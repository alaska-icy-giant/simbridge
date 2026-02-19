# SimBridge Relay Server

Message relay between Host (Phone A with SIM cards) and Client (Phone B) apps. The relay server is a WebSocket-based message broker that routes commands from Client to Host and events from Host to Client.

## System Architecture

```
┌──────────────┐        ┌──────────────────┐        ┌──────────────┐
│  Host App    │  WSS   │  Relay Server    │  WSS   │  Client App  │
│  (Phone A)   │◄──────►│  (this project)  │◄──────►│  (Phone B)   │
│              │        │                  │        │              │
│  SIM 1, SIM 2│        │  FastAPI + SQLite │        │  Dialer UI   │
│  Android     │        │  JWT auth        │        │  SMS compose  │
│  Telecom API │        │  WebSocket hub   │        │  SIM selector │
└──────────────┘        └──────────────────┘        └──────────────┘
```

## Features

- **Auth**: Username/password + Google OAuth. JWT tokens (HS256, 24h expiry).
- **Rate limiting**: 5 login/pairing attempts per 60 seconds per key.
- **Device pairing**: Cryptographically secure 6-digit codes (10-min expiry, one active per host).
- **Command relay**: SMS, calls, SIM queries routed via WebSocket.
- **Command queuing**: Commands queued in DB when host offline, delivered on reconnect.
- **WebRTC signaling**: SDP offer/answer and ICE candidate relay for call audio bridging.
- **Message type validation**: Only `command`, `event`, `webrtc`, `ping` allowed on WebSocket.
- **Server heartbeat**: Server pings every 30s to detect dead connections.
- **Offline notifications**: `DEVICE_OFFLINE` event sent to paired devices on disconnect.
- **Paginated history**: `/history` with offset/limit, 90-day auto-retention.
- **Connection safety**: `asyncio.Lock` on connection registry, duplicate connections replaced.

## System Requirements

### Runtime
- Python 3.11+
- ~50MB RAM (idle), scales with concurrent WebSocket connections
- Minimal CPU — primarily I/O bound (WebSocket relay)
- SQLite (included with Python, no external DB needed)

### Dependencies
- **FastAPI** — async web framework with native WebSocket support
- **uvicorn** — ASGI server
- **SQLAlchemy** — ORM for SQLite
- **PyJWT** — JWT token creation and verification (HS256)
- **bcrypt** — password hashing
- **python-dotenv** — environment variable loading
- **google-auth** — Google ID token verification (optional)

## Project Structure

```
/opt/ws/simbridge/
├── main.py              # FastAPI app — REST + WebSocket + rate limiting + heartbeat
├── models.py            # SQLAlchemy models (User, Device, PairingCode, Pairing, MessageLog, PendingCommand)
├── auth.py              # JWT, bcrypt, Google token verification
├── conftest.py          # Pytest fixtures (in-memory DB, rate limit cleanup)
├── test_auth.py         # Auth tests (8)
├── test_endpoints.py    # REST API tests (27)
├── test_websocket.py    # WebSocket tests (7)
├── requirements.txt     # Python dependencies
├── .env.example         # Example environment variables
├── host-app/            # Android Host app (Kotlin/Compose)
├── client-app/          # Android Client app (Kotlin/Compose)
├── docs/                # Design docs, known issues, platform challenges
└── README.md            # This file
```

## Development Guide

### Initial Setup

```bash
cd /opt/ws/simbridge
pip install -r requirements.txt
cp .env.example .env
# Edit .env — set JWT_SECRET to a random string (32+ chars, REQUIRED)
```

### Run Development Server

```bash
uvicorn main:app --reload --port 8100
```

- Swagger UI: http://localhost:8100/docs
- ReDoc: http://localhost:8100/redoc

### Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `JWT_SECRET` | **Yes** | — | HMAC key for JWT tokens. Server crashes on startup if missing. |
| `DB_PATH` | No | `simbridge.db` | SQLite database file path |
| `LOG_RETENTION_DAYS` | No | `90` | Auto-delete message logs older than N days on startup |
| `GOOGLE_CLIENT_ID` | No | — | Google OAuth client ID. Google auth endpoint disabled if not set. |

### Running Tests

```bash
# All 42 tests
pytest test_auth.py test_endpoints.py test_websocket.py -v

# Specific test file
pytest test_endpoints.py -v

# Single test
pytest -k "test_confirm_pairing"
```

Tests use in-memory SQLite, mock Google auth, and auto-clear rate limit state between tests.

### Quick Smoke Test

```bash
# 1. Register a user
curl -s -X POST http://localhost:8100/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test123"}'

# 2. Login to get JWT token
curl -s -X POST http://localhost:8100/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test123"}'
export TOKEN=eyJ...

# 3. Register devices
curl -s -X POST http://localhost:8100/devices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Phone A","type":"host"}'

curl -s -X POST http://localhost:8100/devices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Phone B","type":"client"}'

# 4. Generate pairing code
curl -s -X POST "http://localhost:8100/pair?host_device_id=1" \
  -H "Authorization: Bearer $TOKEN"

# 5. Confirm pairing
curl -s -X POST http://localhost:8100/pair/confirm \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"code":"<CODE>","client_device_id":2}'

# 6. Send SMS (queued — host not connected)
curl -s -X POST http://localhost:8100/sms \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"to_device_id":1,"sim":1,"to":"+886912345678","body":"Hello"}'
# -> {"status":"queued","req_id":"..."}
```

### Testing WebSocket Connections

```bash
# Connect as host device
websocat "ws://localhost:8100/ws/host/1?token=$TOKEN"

# In another terminal, connect as client
websocat "ws://localhost:8100/ws/client/2?token=$TOKEN"

# From client, send a command:
{"type":"command","cmd":"GET_SIMS","req_id":"test-1"}

# From host, send an event:
{"type":"event","event":"SIM_INFO","sims":[{"slot":1,"carrier":"CHT"}],"req_id":"test-1"}
```

## API Reference

### Authentication

| Endpoint | Method | Auth | Body | Response |
|---|---|---|---|---|
| `/auth/register` | POST | No | `{"username","password"}` | `{"id","username"}` |
| `/auth/login` | POST | No | `{"username","password"}` | `{"token","user_id"}` |
| `/auth/google` | POST | No | `{"id_token"}` | `{"token","user_id"}` |

Login is rate-limited: 5 attempts per 60 seconds per username.

### Devices

| Endpoint | Method | Auth | Body/Params | Response |
|---|---|---|---|---|
| `POST /devices` | POST | JWT | `{"name","type":"host\|client"}` | `{"id","name","type","is_online"}` |
| `GET /devices` | GET | JWT | — | `[{"id","name","type","is_online","last_seen"}]` |

`is_online` is computed from the in-memory connection registry (not persisted to DB).

### Pairing

| Endpoint | Method | Auth | Body/Params | Response |
|---|---|---|---|---|
| `POST /pair` | POST | JWT | `?host_device_id=N` | `{"code","expires_in_seconds"}` |
| `POST /pair/confirm` | POST | JWT | `{"code","client_device_id"}` | `{"status","pairing_id","host_device_id"}` |

- Pairing codes are 6-digit, cryptographically secure, valid for 10 minutes.
- Only one active code per host (previous codes expired on new generation).
- Cross-user pairing blocked: code's `user_id` must match confirming user.
- Rate-limited: 5 attempts per 60 seconds.
- `Pairing` table has unique constraint on `(host_device_id, client_device_id)`.

### Commands (REST fallback)

Commands relay to the Host via WebSocket. If the Host is offline, commands are **queued** (returned as `"status":"queued"`).

| Endpoint | Method | Auth | Body | Response |
|---|---|---|---|---|
| `POST /sms` | POST | JWT | `{"to_device_id","sim"(1-2),"to","body"}` | `{"status":"sent\|queued","req_id"}` |
| `POST /call` | POST | JWT | `{"to_device_id","sim"(1-2),"to"}` | `{"status":"sent\|queued","req_id"}` |
| `GET /sims` | GET | JWT | `?host_device_id=N` | `{"status":"sent\|queued","req_id"}` |

Input validation: `sim` must be 1-2, `to` max 30 chars, `body` max 1600 chars.

### History

| Endpoint | Method | Auth | Params | Response |
|---|---|---|---|---|
| `GET /history` | GET | JWT | `?device_id=N&limit=50&offset=0` | `{"items":[...],"total","offset","limit"}` |

Paginated. Max `limit` is 200. Logs auto-deleted after 90 days.

### WebSocket

| Endpoint | Auth | Purpose |
|---|---|---|
| `WS /ws/host/{device_id}?token=JWT` | JWT | Host persistent connection |
| `WS /ws/client/{device_id}?token=JWT` | JWT | Client persistent connection |

**On connect**: Server sends `{"type":"connected","device_id":N}`, delivers any queued commands (host only), starts server heartbeat (ping every 30s).

**Allowed message types**: `ping`, `command`, `event`, `webrtc`. Invalid types rejected.

**Routing**: Messages auto-forwarded to paired device. If no `to_device_id` specified, server looks up first pairing.

**On disconnect**: `DEVICE_OFFLINE` event sent to all paired devices.

### WebSocket Message Format

**Commands** (Client → Host):
```json
{"type":"command","cmd":"SEND_SMS","sim":1,"to":"+886...","body":"Hi","req_id":"uuid"}
{"type":"command","cmd":"MAKE_CALL","sim":1,"to":"+886...","req_id":"uuid"}
{"type":"command","cmd":"HANG_UP","req_id":"uuid"}
{"type":"command","cmd":"GET_SIMS","req_id":"uuid"}
```

**Events** (Host → Client):
```json
{"type":"event","event":"SMS_SENT","status":"ok","req_id":"uuid"}
{"type":"event","event":"INCOMING_SMS","sim":1,"from":"+1...","body":"Hello"}
{"type":"event","event":"INCOMING_CALL","sim":2,"from":"+1..."}
{"type":"event","event":"SIM_INFO","sims":[{"slot":1,"carrier":"CHT","number":"+886..."}]}
{"type":"event","event":"CALL_STATE","state":"dialing|ringing|active|ended","req_id":"uuid"}
{"type":"event","event":"DEVICE_OFFLINE","device_id":1}
```

**WebRTC signaling** (bidirectional):
```json
{"type":"webrtc","action":"offer","sdp":"...","req_id":"call-uuid"}
{"type":"webrtc","action":"answer","sdp":"...","req_id":"call-uuid"}
{"type":"webrtc","action":"ice","candidate":"...","sdpMid":"0","sdpMLineIndex":0,"req_id":"call-uuid"}
{"type":"webrtc","action":"error","body":"...","req_id":"call-uuid"}
```

**Server messages**:
```json
{"type":"connected","device_id":1}
{"type":"pong"}
{"type":"ping"}
{"error":"target_offline","target_device_id":1,"req_id":"uuid"}
{"error":"no paired host"}
{"error":"invalid JSON"}
{"error":"invalid message type: foo"}
```

## Database Schema

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

## Deployment

### Direct (systemd)

```bash
cd /opt/ws/simbridge
pip install -r requirements.txt
cat > .env << 'EOF'
JWT_SECRET=your-very-long-random-secret-string-here
DB_PATH=/opt/ws/simbridge/simbridge.db
EOF

uvicorn main:app --host 0.0.0.0 --port 8100
```

### Docker

```bash
docker build -t simbridge -f docker/Dockerfile .
docker run -d --name simbridge \
  -p 8100:8100 \
  -e JWT_SECRET=your-secret-here \
  -v simbridge_data:/app/data \
  -e DB_PATH=/app/data/simbridge.db \
  simbridge
```

### Production Checklist

- [ ] Set `JWT_SECRET` to a strong random string (32+ characters)
- [ ] Use TLS (HTTPS/WSS) via reverse proxy (nginx, caddy)
- [ ] Set `DB_PATH` to a persistent volume
- [ ] Set `GOOGLE_CLIENT_ID` if using Google OAuth
- [ ] Back up SQLite database periodically
- [ ] Use `--workers 1` with uvicorn (SQLite single-writer)

## Documentation

| Document | Description |
|---|---|
| `docs/host_app_design.md` | Host app architecture and file-by-file design |
| `docs/client_app_design.md` | Client app architecture, end-to-end message flows |
| `docs/platform_challenges.md` | 6 hard problems: background, audio, dual SIM, latency, carrier, NAT |
| `docs/known_issues.md` | 40 issues audited and resolved across all components |
| `docs/simbridge_hld.md` | High-level system design |
