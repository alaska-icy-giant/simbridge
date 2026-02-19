# SimBridge Host App — Detailed Design

## Overview

The Host App runs on **Phone A** (the phone with physical SIM cards). It maintains a persistent WebSocket connection to the SimBridge relay server and executes telephony commands — sending SMS, placing calls, and bridging call audio — on behalf of a remote Client app running on Phone B.

```
┌──────────┐      WebSocket       ┌──────────────┐      WebSocket       ┌──────────┐
│ Client   │◄────────────────────►│ Relay Server │◄────────────────────►│ Host App │
│ (Phone B)│      (commands)      │              │      (commands)      │ (Phone A)│
└──────────┘                      └──────────────┘                      └──────────┘
                                                                            │
                                                                   ┌────────┴────────┐
                                                                   │  SIM 1  │ SIM 2 │
                                                                   │  (CHT)  │ (TWM) │
                                                                   └─────────┴───────┘
```

**Platform**: Android (minSdk 26, targetSdk 35)
**UI**: Jetpack Compose + Material 3
**Language**: Kotlin 2.0
**Location**: `/opt/ws/simbridge/host-app/`

---

## Architecture

### Layer Diagram

```
┌─────────────────────────────────────────────────┐
│                    UI Layer                       │
│  LoginScreen · DashboardScreen · LogScreen       │
│  SettingsScreen · StatusCard · SimCard            │
├─────────────────────────────────────────────────┤
│                 Service Layer                     │
│  BridgeService (foreground service, lifecycle)   │
│  WebSocketManager · CommandHandler               │
│  SmsHandler · CallHandler · SimInfoProvider      │
│  SmsReceiver (BroadcastReceiver)                 │
├─────────────────────────────────────────────────┤
│                Telecom Layer                      │
│  BridgeConnectionService · BridgeConnection      │
│  AudioBridge (AudioRecord ↔ WebRTC)              │
├─────────────────────────────────────────────────┤
│                 WebRTC Layer                      │
│  WebRtcManager · SignalingHandler                │
├─────────────────────────────────────────────────┤
│                  Data Layer                       │
│  ApiClient (OkHttp REST) · Prefs · Models        │
└─────────────────────────────────────────────────┘
```

### Key Design Decisions

1. **Foreground Service** — Android kills background services aggressively. `BridgeService` runs as a foreground service with a persistent notification so the OS keeps the WebSocket alive.

2. **Single WebSocket** — All communication (commands, events, WebRTC signaling) flows through one WebSocket connection. This avoids managing multiple connections and simplifies reconnect logic.

3. **No FCM** — v1 relies entirely on the foreground service staying alive. FCM push wakeup is a future enhancement.

4. **Bind + Start pattern** — The Activity both starts the service (so it survives Activity destruction) and binds to it (for live UI updates). Unbinding in `onStop` doesn't kill the service because `startForegroundService()` was called.

---

## File-by-File Design

### Data Layer (`data/`)

#### `Models.kt`
All data classes shared across the app. A single `WsMessage` class handles all WebSocket message types (command, event, webrtc) using nullable fields rather than a sealed class hierarchy — this simplifies Gson serialization.

```kotlin
data class WsMessage(
    val type: String,       // "command" | "event" | "webrtc"
    val cmd: String?,       // SEND_SMS, MAKE_CALL, HANG_UP, GET_SIMS
    val event: String?,     // SMS_SENT, INCOMING_SMS, CALL_STATE, SIM_INFO
    val action: String?,    // offer, answer, ice
    // ... other fields
)
```

#### `ApiClient.kt`
Synchronous OkHttp-based REST client. Called from coroutines with `Dispatchers.IO`. Three endpoints:
- `POST /auth/login` → JWT token
- `POST /devices` → register this phone as a "host" device
- `POST /pair` → pair with a client using a pairing code

Returns `Result<T>` for clean error handling in the UI layer.

#### `Prefs.kt`
Thin SharedPreferences wrapper storing: `serverUrl`, `token`, `deviceId`, `deviceName`, `biometricEnabled`. The `isLoggedIn` computed property checks both token and serverUrl are non-blank.

#### `SecureTokenStore.kt`
Uses `EncryptedSharedPreferences` (from `androidx.security.crypto`) to store the JWT token in Android Keystore-backed encrypted storage. Released only after biometric verification succeeds. Methods: `saveToken(token)`, `getToken(): String?`, `clear()`.

---

### Service Layer (`service/`)

#### `BridgeService.kt` — The Hub

The foreground service is the central lifecycle owner. It:

1. Creates and owns `WebSocketManager`, `SmsHandler`, `CallHandler`, `CommandHandler`, `WebRtcManager`, `SignalingHandler`
2. Starts as a foreground service with a notification showing connection status
3. Registers/unregisters the `SmsReceiver` broadcast receiver
4. Exposes observable state (`connectionStatus`, `logs`) for the UI via callbacks
5. Maintains a capped log buffer (100 entries) of recent commands and events

**Lifecycle**:
```
onCreate()  → instantiate all managers
onStartCommand() → startForeground(), register SMS receiver, connect WS
onBind()    → return LocalBinder for Activity binding
onDestroy() → disconnect WS, dispose WebRTC, unregister receiver
```

#### `WebSocketManager.kt` — Connection Management

OkHttp WebSocket with automatic reconnection:

- **Connection URL**: Converts `https://` to `wss://`, appends `?token=JWT`
- **Ping**: OkHttp's built-in ping interval (30s)
- **Reconnect**: Exponential backoff: 1s → 2s → 4s → 8s → 16s → 30s (capped). Resets on successful connection.
- **Intentional close**: `disconnect()` sets a flag to suppress reconnection
- **Thread safety**: Uses `AtomicInteger` for retry count, `@Volatile` for the close flag
- **Message handling**: Deserializes JSON via Gson, dispatches to callback

```
State machine:
  DISCONNECTED → connect() → CONNECTING → onOpen → CONNECTED
  CONNECTED → onFailure/onClosed → DISCONNECTED → scheduleReconnect → CONNECTING → ...
  CONNECTED → disconnect() → DISCONNECTED (no reconnect)
```

#### `CommandHandler.kt` — Command Dispatch

Routes incoming commands to the appropriate handler:

| Command | Handler | Action |
|---------|---------|--------|
| `SEND_SMS` | `SmsHandler.sendSms()` | Send SMS via SmsManager |
| `MAKE_CALL` | `CallHandler.makeCall()` | Place call via TelecomManager |
| `HANG_UP` | `CallHandler.hangUp()` | End active call |
| `GET_SIMS` | `SimInfoProvider.getActiveSimCards()` | Return SIM info |

Validates required fields (e.g., `to` and `body` for SEND_SMS) before dispatching. Logs every command.

#### `SmsHandler.kt` — SMS Sending

- Uses `SmsManager.sendTextMessage()` for single-part SMS
- Uses `SmsManager.sendMultipartTextMessage()` for long messages (auto-split via `divideMessage()`)
- SIM selection: looks up `SubscriptionInfo` for the requested slot via `SimInfoProvider`, then gets the slot-specific `SmsManager` via `getSmsManagerForSubscriptionId()`
- Reports `SMS_SENT` event with status `ok` or `error`

#### `SmsReceiver.kt` — Incoming SMS

A `BroadcastReceiver` for `SMS_RECEIVED_ACTION`:
- Extracts `SmsMessage[]` from the intent
- Groups multi-part messages by sender (concatenates body parts)
- Reads SIM slot from intent extras (`slot` extra, 0-indexed → 1-indexed)
- Forwards as `INCOMING_SMS` event via callback to `BridgeService`

Registered both statically (AndroidManifest, priority 999) and dynamically (in BridgeService). The dynamic registration ensures the callback is wired; the static declaration ensures the receiver exists even if the service restarts.

#### `CallHandler.kt` — Outgoing Calls

- Uses `TelecomManager.placeCall(uri, extras)` with a `PhoneAccountHandle` for SIM selection
- Reports `CALL_STATE` events: `dialing`, `error`
- `hangUp()` uses `TelecomManager.endCall()` (deprecated but functional)
- SIM selection: maps 1-indexed slot to `callCapablePhoneAccounts` list index

#### `SimInfoProvider.kt` — SIM Card Info

- Reads `SubscriptionManager.activeSubscriptionInfoList`
- Maps each `SubscriptionInfo` to `SimInfo(slot, carrier, number)`
- Slot numbers are 1-indexed (Android's `simSlotIndex` is 0-indexed)
- Phone number retrieval handles the API change in Android 13+ (`getPhoneNumber()` vs deprecated `number`)

---

### Telecom Layer (`telecom/`)

#### `BridgeConnectionService.kt`

Android's `ConnectionService` for managing call connections. Declared in the manifest with `BIND_TELECOM_CONNECTION_SERVICE` permission. Creates `BridgeConnection` instances for outgoing and incoming calls.

#### `BridgeConnection.kt`

Extends `android.telecom.Connection`. Reports lifecycle events:
- `onAnswer()` → `setActive()` → callback `"active"`
- `onDisconnect()` → `setDisconnected(LOCAL)` → callback `"ended"`
- `onReject()` → `setDisconnected(REJECTED)` → callback `"ended"`

#### `AudioBridge.kt`

Captures call audio using `AudioRecord` with `VOICE_COMMUNICATION` source. This audio source captures the near-end audio during a phone call. The captured audio is routed through WebRTC's audio processing pipeline (echo cancellation, noise suppression) when a `PeerConnection` is active.

---

### WebRTC Layer (`webrtc/`)

#### `WebRtcManager.kt`

Manages the WebRTC `PeerConnection` for audio-only communication:

- **Factory**: Initializes `PeerConnectionFactory` with `JavaAudioDeviceModule` (hardware AEC + NS)
- **ICE**: Uses Google's public STUN server (`stun.l.google.com:19302`)
- **Audio**: Creates a local audio track with echo cancellation, noise suppression, and AGC enabled
- **SDP**: Provides `createOffer()` and `createAnswer()` with `OfferToReceiveAudio=true`, `OfferToReceiveVideo=false`
- **Cleanup**: `dispose()` releases all WebRTC resources; `closePeerConnection()` closes just the current session

#### `SignalingHandler.kt`

Bridges WebRTC signaling through the existing WebSocket (no separate signaling server needed):

- **Offer received** → creates PeerConnection, sets remote SDP, creates answer, sends answer via WS
- **Answer received** → sets remote SDP on existing PeerConnection
- **ICE candidate received** → adds to PeerConnection
- **ICE candidate generated** → sends via WS as `{"type":"webrtc","action":"ice",...}`

---

### UI Layer (`ui/`)

Single-Activity architecture with Jetpack Compose Navigation.

#### Navigation Flow

```
BiometricPromptScreen ──success──► DashboardScreen
        │ (fallback)                    │
        ▼                               ├──► LogScreen
LoginScreen ──login──► DashboardScreen  │
                            │           ├──► SettingsScreen
                            │                    │
                            │                (logout)
                            │                    │
                            ◄────────────────────┘
```

Start destination logic:
- If `biometricEnabled && secureToken exists` → `BIOMETRIC`
- Else if already logged in → `DASHBOARD`
- Otherwise → `LOGIN`

#### `LoginScreen`

- Three fields: server URL, username, password
- Password toggle visibility
- On login success: saves token to Prefs, registers device (fire-and-forget)
- If device supports biometric and biometric is not yet enabled: shows "Enable biometric unlock?" dialog
- If user accepts: saves token to `SecureTokenStore`, sets `prefs.biometricEnabled = true`
- Navigates to dashboard
- Error display for failed login attempts

#### `BiometricPromptScreen`

- Shown when `prefs.biometricEnabled && SecureTokenStore.getToken() != null`
- Uses `BiometricPrompt` from `androidx.biometric` with `BIOMETRIC_STRONG` authenticator
- On success: retrieves token from secure storage, sets `prefs.token`, navigates to dashboard
- On failure/cancel or "Use password" button: navigates to LoginScreen

#### `DashboardScreen`

- **StatusCard**: Large colored indicator — green "Connected", amber "Connecting...", red "Offline"
- **Start/Stop button**: Toggles `BridgeService` foreground service
- **SIM Cards section**: Lists detected SIM cards with carrier name and phone number
- **Toolbar**: Log and Settings navigation icons
- Observes `BridgeService.connectionStatus` via `DisposableEffect`

#### `LogScreen`

- Scrollable list of `LogEntry` items (timestamp, direction IN/OUT, summary)
- Monospace font, color-coded direction (blue for IN, green for OUT)
- Updates in real-time via `BridgeService.onLogEntry` callback

#### `SettingsScreen`

- Server URL display (read-only)
- Device name and ID display
- Biometric Unlock toggle (only visible if device supports biometric via `BiometricManager.canAuthenticate()`). Enabling saves the current token to `SecureTokenStore`; disabling clears it.
- Battery optimization shortcut — opens Android's "ignore battery optimization" settings for the app
- Logout button with confirmation dialog (clears Prefs + SecureTokenStore, stops service, navigates to login)

#### `Theme.kt`

Material 3 theme with:
- Dynamic color on Android 12+ (Material You)
- Fallback light/dark color schemes for older devices
- System dark mode detection

---

## WebSocket Protocol

### Commands (Relay → Host)

| Command | Fields | Response Event |
|---------|--------|----------------|
| `SEND_SMS` | `sim`, `to`, `body`, `req_id` | `SMS_SENT` (status: ok/error) |
| `MAKE_CALL` | `sim`, `to`, `req_id` | `CALL_STATE` (state: dialing/error) |
| `HANG_UP` | `req_id` | `CALL_STATE` (state: ended) |
| `GET_SIMS` | `req_id` | `SIM_INFO` (sims array) |

### Events (Host → Relay)

| Event | Fields | Trigger |
|-------|--------|---------|
| `SMS_SENT` | `status`, `req_id` | After SEND_SMS command |
| `INCOMING_SMS` | `sim`, `from`, `body` | SMS received on phone |
| `INCOMING_CALL` | `sim`, `from` | Phone call received |
| `CALL_STATE` | `state`, `sim`, `req_id` | Call state change |
| `SIM_INFO` | `sims[]` | After GET_SIMS command |
| `ERROR` | `body`, `req_id` | Invalid command |

### WebRTC Signaling (Bidirectional)

| Action | Fields | Direction |
|--------|--------|-----------|
| `offer` | `sdp`, `req_id` | Client → Host (usually) |
| `answer` | `sdp`, `req_id` | Host → Client |
| `ice` | `candidate`, `sdpMid`, `sdpMLineIndex`, `req_id` | Both |

---

## Android Permissions

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | WebSocket + REST API |
| `CALL_PHONE` | Place outgoing calls |
| `READ_PHONE_STATE` | Read SIM info |
| `READ_PHONE_NUMBERS` | Read phone numbers |
| `ANSWER_PHONE_CALLS` | End calls (hangUp) |
| `MANAGE_OWN_CALLS` | ConnectionService |
| `READ_CALL_LOG` | Call history access |
| `SEND_SMS` | Send SMS messages |
| `RECEIVE_SMS` | Receive incoming SMS |
| `RECORD_AUDIO` | WebRTC audio capture |
| `MODIFY_AUDIO_SETTINGS` | Audio routing |
| `FOREGROUND_SERVICE` | Keep service alive |
| `FOREGROUND_SERVICE_PHONE_CALL` | Call-type foreground service |
| `FOREGROUND_SERVICE_MICROPHONE` | Mic-type foreground service |
| `POST_NOTIFICATIONS` | Notification (Android 13+) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Battery optimization dialog |

Permissions are requested at Activity launch. The service gracefully handles missing permissions (e.g., returns empty SIM list if `READ_PHONE_STATE` is denied).

---

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Compose BOM | 2024.12.01 | UI framework |
| Material 3 | (from BOM) | Design system |
| Navigation Compose | 2.8.5 | Screen navigation |
| Lifecycle ViewModel Compose | 2.8.7 | ViewModel integration |
| OkHttp | 4.12.0 | WebSocket + REST HTTP |
| Gson | 2.11.0 | JSON serialization |
| stream-webrtc-android | 1.2.2 | Google's libwebrtc for Android |
| Core KTX | 1.15.0 | Kotlin extensions |
| Biometric | 1.1.0 | Fingerprint / Face unlock |
| Security Crypto | 1.0.0 | EncryptedSharedPreferences |

---

## v1 Limitations

- **No FCM push wakeup** — relies entirely on foreground service
- **No call recording** — audio is bridged live only
- **No USSD forwarding**
- **No message buffering** — commands lost if WebSocket disconnects mid-delivery
- **No offline queue** — SMS/call commands require active connection
- **Single active call** — no conference/multi-call support
