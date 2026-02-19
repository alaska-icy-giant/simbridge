# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SimBridge is a SIM-bridging platform with three components:

1. **Relay Server** (this directory) — Python/FastAPI WebSocket message broker that routes commands and events between Host and Client apps. Handles auth, device pairing, command queuing, and message logging.
2. **Host App** (`host-app/`) — Android app (Kotlin/Compose) that runs on Phone A (with SIM cards). Executes SMS/call commands, bridges call audio via WebRTC.
3. **Client App** (`client-app/`) — Android app (Kotlin/Compose) that runs on Phone B (no SIMs). Sends commands, receives events, plays call audio.

## Running the Server

```bash
pip install -r requirements.txt
cp .env.example .env   # set JWT_SECRET to a random 32+ char string (REQUIRED)
uvicorn main:app --reload --port 8100
```

API docs: http://localhost:8100/docs

**IMPORTANT**: `JWT_SECRET` environment variable is **required**. The server crashes on startup if it's not set. There is no default value.

## Testing

```bash
# All relay server tests (40 tests)
pytest test_auth.py test_endpoints.py test_websocket.py -v

# Single test
pytest -k "test_confirm_pairing"
```

Test infrastructure:
- `conftest.py` — sets `JWT_SECRET=test-secret-for-pytest`, in-memory SQLite, clears rate limiter between tests
- `test_auth.py` — password hashing, JWT create/decode, Google token verification (8 tests)
- `test_endpoints.py` — REST API: register, login, devices, pairing, SMS/call relay, history, Google auth (27 tests)
- `test_websocket.py` — WS connect, ping/pong, message routing, offline errors (7 tests)

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `JWT_SECRET` | **Yes** | — | HMAC key for JWT tokens. Crashes on startup if missing. |
| `DB_PATH` | No | `simbridge.db` | SQLite database file path |
| `LOG_RETENTION_DAYS` | No | `90` | Auto-delete message logs older than N days |
| `GOOGLE_CLIENT_ID` | No | — | Google OAuth client ID. Google auth disabled if not set. |

## Architecture

### Files

- **`main.py`** — FastAPI app: REST endpoints, WebSocket handlers, connection management, command queuing, rate limiting, message type validation, server heartbeat, offline notifications
- **`models.py`** — SQLAlchemy ORM: `User` (with optional `email`/`google_id`), `Device`, `PairingCode`, `Pairing` (unique constraint), `MessageLog`, `PendingCommand`
- **`auth.py`** — JWT create/verify (HS256, 24h), bcrypt password hashing, Google ID token verification
- **`conftest.py`** — Pytest fixtures: in-memory DB, test client, auth headers, paired devices

### Key Design Patterns

- **JWT auth**: Bearer token on REST; `?token=` query param on WebSocket
- **Connection registry**: `connections: dict[int, WebSocket]` protected by `asyncio.Lock`. Duplicate connections replaced (old one closed with 1008). Online status computed from dict, not persisted.
- **Rate limiting**: Per-username, 5 attempts / 60 seconds on `/auth/login` and `/pair/confirm`. State in `_auth_attempts` dict (cleared in tests).
- **Pairing codes**: 6-digit, cryptographically secure (`secrets.choice`), 10-min expiry. Old unused codes expired on new generation. Cross-user pairing blocked (`user_id` check).
- **Device ownership**: All commands verify both host and client belong to the requesting user.
- **Message type validation**: WebSocket messages must have `type` in `{ping, command, event, webrtc}`.
- **Fresh DB sessions**: `_ws_loop` creates a new `SessionLocal()` per message to avoid stale-session issues.
- **Command queuing**: When host is offline, commands stored in `PendingCommand` table (returns `"status":"queued"`). Delivered on host reconnect.
- **Server heartbeat**: Server sends `{"type":"ping"}` every 30s to detect dead connections.
- **Offline notifications**: When a device disconnects, `DEVICE_OFFLINE` event sent to all paired devices.
- **Log retention**: Message logs older than 90 days auto-deleted on startup.
- **Paginated history**: `/history` returns `{items, total, offset, limit}`.
- **Google OAuth**: Optional. `POST /auth/google` verifies Google ID token, auto-creates user or links by email.

### Database Schema

```
users                devices              pairings (UNIQUE host+client)
─────                ───────              ────────
id                   id                   id
username (unique)    user_id → users      host_device_id → devices
password_hash (opt)  name                 client_device_id → devices
email (unique, opt)  device_type          created_at
google_id (unique)   device_token
created_at           is_online
                     last_seen
                     created_at

pairing_codes        message_logs         pending_commands
─────────────        ────────────         ────────────────
id                   id                   id
user_id → users      from_device_id →     host_device_id → devices
host_device_id →     to_device_id →       from_device_id → devices
code (6-digit)       msg_type             payload (JSON)
expires_at           payload (JSON)       created_at
used                 created_at           delivered
created_at
```

### WebSocket Protocol

**Allowed message types**: `ping`, `command`, `event`, `webrtc`

**Server → Device**:
```json
{"type":"connected","device_id":1}
{"type":"pong"}
{"type":"ping"}
{"type":"event","event":"DEVICE_OFFLINE","device_id":1}
{"error":"target_offline","target_device_id":1,"req_id":"..."}
{"error":"no paired host"}
{"error":"invalid message type: foo"}
```

**Commands** (Client → Host via relay):
```json
{"type":"command","cmd":"SEND_SMS","sim":1,"to":"+886...","body":"Hi","req_id":"uuid"}
{"type":"command","cmd":"MAKE_CALL","sim":1,"to":"+886...","req_id":"uuid"}
{"type":"command","cmd":"HANG_UP","req_id":"uuid"}
{"type":"command","cmd":"GET_SIMS","req_id":"uuid"}
```

**Events** (Host → Client via relay):
```json
{"type":"event","event":"SMS_SENT","status":"ok","req_id":"uuid"}
{"type":"event","event":"INCOMING_SMS","sim":1,"from":"+1...","body":"Hello"}
{"type":"event","event":"INCOMING_CALL","sim":2,"from":"+1..."}
{"type":"event","event":"SIM_INFO","sims":[{"slot":1,"carrier":"CHT","number":"+886..."}]}
{"type":"event","event":"CALL_STATE","state":"dialing|ringing|active|ended","req_id":"uuid"}
```

**WebRTC signaling** (bidirectional):
```json
{"type":"webrtc","action":"offer","sdp":"...","req_id":"call-uuid"}
{"type":"webrtc","action":"answer","sdp":"...","req_id":"call-uuid"}
{"type":"webrtc","action":"ice","candidate":"...","sdpMid":"0","sdpMLineIndex":0,"req_id":"call-uuid"}
{"type":"webrtc","action":"error","body":"Failed to create SDP offer","req_id":"call-uuid"}
```

## Android Apps

### Host App (`host-app/`)

Kotlin/Compose, minSdk 26, targetSdk 35. Key files:
- `service/WebSocketManager.kt` — connects to `/ws/host/{device_id}?token=JWT` with exponential backoff reconnect
- `service/BridgeService.kt` — foreground service hub, thread-safe callbacks via `Handler(Looper.getMainLooper())`
- `service/CommandHandler.kt` — dispatches `SEND_SMS`, `MAKE_CALL`, `HANG_UP`, `GET_SIMS`
- `webrtc/WebRtcManager.kt` — PeerConnection with `JavaAudioDeviceModule`, sets `MODE_IN_COMMUNICATION`
- `telecom/BridgeConnectionService.kt` — wired to BridgeService via static `onCallStateEvent` callback

### Client App (`client-app/`)

Kotlin/Compose, minSdk 26, targetSdk 35. Key files:
- `service/WebSocketManager.kt` — connects to `/ws/client/{device_id}?token=JWT`
- `service/ClientService.kt` — foreground service, thread-safe state (`@Volatile`, `mainHandler`)
- `service/CommandSender.kt` — builds commands with UUID `req_id`
- `webrtc/ClientSignalingHandler.kt` — initiates audio session, sends error on SDP failure
- `ui/screen/DialerScreen.kt` — phone number regex validation

## Documentation

- `docs/host_app_design.md` — Host app architecture, file-by-file design
- `docs/client_app_design.md` — Client app architecture, end-to-end flows
- `docs/platform_challenges.md` — 6 platform challenges (background, audio, dual SIM, latency, carrier, NAT)
- `docs/known_issues.md` — 40 issues audited and resolved (all ✅)
- `docs/simbridge_hld.md` — High-level system design
