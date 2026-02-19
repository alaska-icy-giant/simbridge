# SimBridge Client App — Detailed Design

## Overview

The Client App runs on **Phone B** (the phone without SIM cards, or a secondary device). It connects to the SimBridge relay server and sends telephony commands — SMS, calls — to the paired Host app running on Phone A. It also receives incoming SMS/call notifications and plays bridged call audio via WebRTC.

```
┌──────────────────┐       WebSocket        ┌──────────────┐       WebSocket        ┌──────────────┐
│   Client App     │◄──────────────────────►│ Relay Server │◄──────────────────────►│   Host App   │
│   (Phone B)      │  sends commands         │              │  forwards to host      │   (Phone A)  │
│                  │  receives events         │              │  returns events         │   SIM 1 + 2  │
│  No SIM cards    │  WebRTC audio (P2P)  ←──────────────────────────────────────────►│              │
└──────────────────┘                         └──────────────┘                         └──────────────┘
```

**Platform**: Android (minSdk 26, targetSdk 35)
**UI**: Jetpack Compose + Material 3
**Language**: Kotlin 2.0
**Location**: `/opt/ws/simbridge/client-app/`

---

## Architecture

### Layer Diagram

```
┌────────────────────────────────────────────────┐
│                   UI Layer                      │
│  LoginScreen · PairScreen · DashboardScreen    │
│  SmsScreen · DialerScreen · LogScreen          │
│  SettingsScreen · StatusCard · SimSelector      │
├────────────────────────────────────────────────┤
│                Service Layer                    │
│  ClientService (foreground service, hub)       │
│  WebSocketManager · EventHandler               │
│  CommandSender · NotificationHelper            │
├────────────────────────────────────────────────┤
│                WebRTC Layer                     │
│  ClientWebRtcManager · ClientSignalingHandler  │
├────────────────────────────────────────────────┤
│                 Data Layer                      │
│  ApiClient (REST) · Prefs · Models             │
└────────────────────────────────────────────────┘
```

### Host App vs Client App

| Aspect | Host App (Phone A) | Client App (Phone B) |
|--------|-------------------|---------------------|
| **SIM cards** | Has physical SIMs | No SIMs needed |
| **WebSocket path** | `/ws/host/{device_id}` | `/ws/client/{device_id}` |
| **Message direction** | Receives commands, sends events | Sends commands, receives events |
| **Permissions** | 17 (SMS, calls, phone state, audio) | 3 (internet, audio, notifications) |
| **Telephony** | Executes SMS/calls via Android APIs | Sends command messages only |
| **WebRTC role** | Captures call audio, sends to client | Receives call audio, plays to speaker |
| **Notifications** | Service status only | Service status + incoming SMS/call alerts |
| **Pairing flow** | Generates 6-digit code | Enters code to pair |

---

## Project Structure

```
client-app/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/simbridge/client/
│           ├── SimBridgeClientApp.kt       # Application, notification channels
│           ├── MainActivity.kt             # Single activity, service binding
│           ├── data/
│           │   ├── Models.kt               # WsMessage, SimInfo, API models, UI state
│           │   ├── Prefs.kt                # SharedPreferences (token, deviceId, pairedHostId)
│           │   └── ApiClient.kt            # REST client (auth, devices, pair, SMS, call)
│           ├── service/
│           │   ├── ClientService.kt        # Foreground service — lifecycle hub
│           │   ├── WebSocketManager.kt     # OkHttp WebSocket + auto-reconnect
│           │   ├── EventHandler.kt         # Dispatches incoming events from Host
│           │   ├── CommandSender.kt        # Builds & sends commands to Host
│           │   └── NotificationHelper.kt   # Incoming SMS/call notifications
│           ├── webrtc/
│           │   ├── ClientWebRtcManager.kt  # PeerConnection for receiving call audio
│           │   └── ClientSignalingHandler.kt # SDP offer/answer, ICE via WS
│           └── ui/
│               ├── theme/Theme.kt
│               ├── nav/AppNavigation.kt    # 7 routes
│               ├── screen/
│               │   ├── LoginScreen.kt      # Server URL + credentials
│               │   ├── PairScreen.kt       # 6-digit pairing code entry
│               │   ├── DashboardScreen.kt  # Status, SIM info, SMS/Call buttons
│               │   ├── SmsScreen.kt        # Compose & send SMS, message history
│               │   ├── DialerScreen.kt     # Phone number input, call controls
│               │   ├── LogScreen.kt        # WS event log
│               │   └── SettingsScreen.kt   # Server/device/pairing info, logout
│               └── component/
│                   ├── StatusCard.kt       # Connection status indicator
│                   └── SimSelector.kt      # SIM slot picker (host's SIMs)
└── .gitignore
```

---

## File-by-File Design

### Data Layer (`data/`)

#### `Models.kt`

Shared data classes. The `WsMessage` class is identical to the Host's — both apps speak the same protocol. Additional client-specific models:

- `PairConfirmRequest/Response` — for the pairing flow
- `SmsRequest`, `CallRequest` — REST fallback command bodies
- `HistoryEntry` — message log entries from relay
- `SmsEntry` — local SMS history item (timestamp, direction, address, body)
- `CallState` enum — `IDLE`, `DIALING`, `RINGING`, `ACTIVE`
- `SmsConversation` — grouped messages by address

#### `Prefs.kt`

Extends the Host's Prefs with pairing state:

| Key | Type | Purpose |
|-----|------|---------|
| `serverUrl` | String | Relay server URL |
| `token` | String | JWT from `/auth/login` |
| `deviceId` | Int | This client's device ID |
| `deviceName` | String | Device display name |
| `pairedHostId` | Int | Paired host's device ID |
| `pairedHostName` | String | Paired host's display name |

`isPaired` returns `true` when `pairedHostId >= 0`.

#### `ApiClient.kt`

Full REST client covering all relay endpoints. Key difference from Host's ApiClient:

- `register()` — POST `/auth/register` (create account)
- `confirmPairing()` — POST `/pair/confirm` with `{code, client_device_id}`
- `sendSms()` / `makeCall()` — REST fallback commands (POST `/sms`, `/call`)
- `getSims()` — GET `/sims?host_device_id=N`
- `getHistory()` — GET `/history?limit=50`
- `listDevices()` — GET `/devices` (to fetch host device name after pairing)

All authenticated endpoints include `Authorization: Bearer {token}` header.

---

### Service Layer (`service/`)

#### `ClientService.kt` — The Hub

Mirror of the Host's `BridgeService`, but for the client side. Key differences:

- **No telephony** — doesn't touch SMS/call Android APIs
- **Owns `CommandSender`** — exposed publicly so UI screens can send commands
- **SMS history buffer** — maintains in-memory list of sent/received SMS (max 200)
- **Call state tracking** — `callState` and `callNumber` for active call UI

**Event dispatch flow**:
```
WebSocket message
    → handleWsMessage()
        → type "connected" → auto-request GET_SIMS
        → type "pong" → ignore
        → type "event" → EventHandler.handleEvent()
        → type "webrtc" → ClientSignalingHandler.handleSignaling()
        → error field → EventHandler.handleEvent() (relay error)
```

**Observable state** (for UI binding):
- `connectionStatus` — via `onStatusChange` callback
- `hostSims` — via `onSimsUpdated` callback
- `callState` / `callNumber` — via `onCallStateChange` callback
- `smsHistory` — via `onSmsReceived` callback
- `logs` — via `onLogEntry` callback

#### `WebSocketManager.kt`

Identical to Host's implementation except:
- Connects to `/ws/client/{device_id}?token=JWT` (not `/ws/host/`)
- Same exponential backoff reconnection (1s → 30s)
- Same OkHttp ping interval (30s)
- Includes `sendRaw(json)` for pre-serialized messages

#### `EventHandler.kt`

Dispatches incoming events from the Host to callbacks:

| Event | Handler | UI Effect |
|-------|---------|-----------|
| `SMS_SENT` | `onSmsSent` | Update SMS status (ok/error) |
| `INCOMING_SMS` | `onSmsReceived` | Add to history, show notification |
| `INCOMING_CALL` | `onIncomingCall` | Show call notification, update call state |
| `CALL_STATE` | `onCallState` | Update dialer UI (dialing/active/ended) |
| `SIM_INFO` | `onSimInfo` | Update SIM selector in dashboard/SMS/dialer |
| `ERROR` | `onError` | Log the error |
| Relay errors (`target_offline`, etc.) | `onError` | Log the relay error |

#### `CommandSender.kt`

Builds command messages with auto-generated `req_id` (UUID):

```kotlin
commandSender.sendSms(sim = 1, to = "+886...", body = "Hello")
// → WsMessage(type="command", cmd="SEND_SMS", sim=1, to="+886...", body="Hello", reqId="uuid")

commandSender.makeCall(sim = 2, to = "+886...")
// → WsMessage(type="command", cmd="MAKE_CALL", sim=2, to="+886...", reqId="uuid")

commandSender.hangUp()
// → WsMessage(type="command", cmd="HANG_UP", reqId="uuid")

commandSender.getSims()
// → WsMessage(type="command", cmd="GET_SIMS", reqId="uuid")
```

Each command is logged as an `OUT` entry for the event log screen.

#### `NotificationHelper.kt`

Builds Android notifications for incoming events:

- **Incoming SMS** — expandable BigText notification with sender and body. Taps navigate to SMS screen.
- **Incoming call** — ongoing notification with caller number. Taps navigate to dialer screen. Auto-cancelled when call ends.

Uses `IMPORTANCE_HIGH` channel for calls/SMS (heads-up display) and `IMPORTANCE_LOW` for service status.

---

### WebRTC Layer (`webrtc/`)

#### `ClientWebRtcManager.kt`

Client-side PeerConnection manager. Structurally similar to the Host's `WebRtcManager` but the Client:

1. **Adds a local audio track** — so the user on Phone B can speak into the mic and be heard on the call (via Host)
2. **Receives remote audio** — the Host's call audio arrives as a remote track and plays through the device speaker
3. **Can initiate offers** — the Client starts the WebRTC session when a call becomes active

#### `ClientSignalingHandler.kt`

Handles WebRTC signaling with an additional method:

- `initiateAudioSession(callReqId)` — called when the Client wants to start audio bridging for an active call. Creates PeerConnection, generates SDP offer, sends via WebSocket.
- `handleSignaling(message)` — same as Host: handles offer/answer/ICE messages.

**Typical flow** (Client-initiated):
```
1. Client receives CALL_STATE(state="active")
2. Client calls initiateAudioSession(reqId)
3. Client creates PeerConnection + SDP offer → sends to Host via WS
4. Host receives offer → creates answer → sends back
5. ICE candidates exchanged bidirectionally
6. Audio stream established
7. Client hears call audio, Client's mic audio sent to Host
```

---

### UI Layer (`ui/`)

#### Navigation Flow

```
LoginScreen ──login──► PairScreen ──pair──► DashboardScreen
                                                │
                                          ┌─────┼─────┐
                                          ▼     ▼     ▼
                                       SmsScreen DialerScreen LogScreen
                                                        │
                                                  SettingsScreen
                                                        │
                                                    (logout)
                                                        │
                                                        ▼
                                                   LoginScreen
```

Start destination logic:
- Not logged in → `LoginScreen`
- Logged in but not paired → `PairScreen`
- Logged in and paired → `DashboardScreen`

#### `LoginScreen`

Same as Host — server URL, username, password. On success: saves token, registers device as type `"client"`, navigates to Pair or Dashboard.

#### `PairScreen`

Unique to the Client app:
- Large 6-digit code input field (numeric keyboard)
- Instructions: "Open the Host app on Phone A and generate a pairing code"
- On confirm: calls `POST /pair/confirm` with code + client device ID
- On success: saves `pairedHostId` and `pairedHostName`, navigates to Dashboard

#### `DashboardScreen`

- **Status card** — connection to relay (green/amber/red)
- **Start/Stop service** button
- **Host SIM cards** — fetched via `GET_SIMS` on connect, displayed as cards
- **Action buttons** — "SMS" and "Call" navigate to respective screens

#### `SmsScreen`

- **SIM selector** — horizontal chips showing Host's SIM cards
- **Phone number** input
- **Message body** input with send button
- **Recent messages** — scrollable list of sent/received SMS entries, color-coded (incoming = surface variant, sent = primary container)

#### `DialerScreen`

Two states:
- **Idle**: phone number input + green call FAB
- **In call**: displays number, call state text (Dialing.../Ringing.../In Call), red hang-up FAB

SIM selector at top in both states.

#### `LogScreen`

Same as Host — monospace log entries with timestamp, direction (OUT=blue, IN=green), summary.

#### `SettingsScreen`

Shows server, device, and pairing info. Logout clears all prefs and pairing.

---

## Android Permissions (Minimal)

The Client needs far fewer permissions than the Host:

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | WebSocket + REST API |
| `ACCESS_NETWORK_STATE` | Detect connectivity changes |
| `RECORD_AUDIO` | WebRTC microphone for call audio |
| `MODIFY_AUDIO_SETTINGS` | Audio routing during calls |
| `FOREGROUND_SERVICE` | Keep WebSocket alive |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Audio playback service type |
| `POST_NOTIFICATIONS` | Incoming SMS/call notifications |

No SMS, call, phone state, or SIM permissions needed — all telephony happens on the Host.

---

## Command & Event Flow

### Send SMS (End-to-End)

```
User types message in SmsScreen
    → commandSender.sendSms(sim=1, to="+886...", body="Hello")
    → WebSocket: {"type":"command","cmd":"SEND_SMS","sim":1,"to":"+886...","body":"Hello","req_id":"abc"}
    → Relay routes to paired Host
    → Host executes SmsManager.sendTextMessage()
    → Host sends: {"type":"event","event":"SMS_SENT","status":"ok","req_id":"abc"}
    → Relay routes back to Client
    → EventHandler.handleSmsSent(reqId="abc", status="ok")
    → Log entry added
```

### Receive SMS (End-to-End)

```
SMS arrives on Host's SIM 1
    → SmsReceiver.onReceive() extracts sender + body
    → Host sends: {"type":"event","event":"INCOMING_SMS","sim":1,"from":"+1...","body":"Hi"}
    → Relay routes to paired Client
    → EventHandler.handleSmsReceived()
    → SmsEntry added to smsHistory
    → NotificationHelper.notifyIncomingSms()
    → onSmsReceived callback → SmsScreen updates
```

### Make Call (End-to-End)

```
User enters number in DialerScreen, taps Call
    → commandSender.makeCall(sim=2, to="+886...")
    → Host receives MAKE_CALL, calls TelecomManager.placeCall()
    → Host sends CALL_STATE(state="dialing")
    → Client updates callState → DialerScreen shows "Dialing..."
    → Call connects on Host
    → Host sends CALL_STATE(state="active")
    → Client receives "active", initiates WebRTC audio session
    → SDP offer/answer exchanged, ICE candidates exchanged
    → Audio stream established — Client hears the call
    → User taps Hang Up
    → commandSender.hangUp()
    → Host ends call, sends CALL_STATE(state="ended")
    → Client receives "ended", closes WebRTC, returns to idle dialer
```

---

## Notification Channels

| Channel | ID | Importance | Purpose |
|---------|-----|-----------|---------|
| Service status | `simbridge_client` | Low | Persistent foreground service notification |
| Incoming events | `simbridge_call` | High | SMS and call notifications (heads-up) |

---

## Dependencies

Same as Host App (shared version catalog):

| Library | Version | Purpose |
|---------|---------|---------|
| Compose BOM | 2024.12.01 | UI framework |
| Material 3 | (from BOM) | Design system |
| Navigation Compose | 2.8.5 | Screen navigation |
| OkHttp | 4.12.0 | WebSocket + REST |
| Gson | 2.11.0 | JSON serialization |
| stream-webrtc-android | 1.2.2 | WebRTC audio |

---

## Key Design Decisions

1. **Foreground service for WebSocket** — Same rationale as Host. Ensures the Client receives incoming SMS/call notifications even when the app is backgrounded.

2. **CommandSender as public API** — Exposed from `ClientService` so any UI screen can send commands without knowing about WebSocket internals. Each command auto-generates a `req_id` UUID.

3. **In-memory SMS history** — SMS entries are kept in a capped list (200 entries) in `ClientService`. Not persisted to disk — this is a bridge, not a full SMS app. The relay's `message_logs` table serves as the persistent audit trail.

4. **Auto GET_SIMS on connect** — When the WebSocket connects and receives the `connected` message, the Client immediately requests SIM info. This populates the SIM selector before the user opens SMS or Dialer screens.

5. **PairScreen as mandatory step** — Navigation flow enforces pairing before reaching the Dashboard. Without pairing, the relay can't route messages.

6. **Minimal permissions** — The Client doesn't touch Android telephony APIs. All it needs is internet, audio (for WebRTC), and notifications.
