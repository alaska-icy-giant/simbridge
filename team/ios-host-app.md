# Alice — iOS Host App Spec

> Port the Android `host-app/` to native iOS (Swift 5.10+ / SwiftUI / iOS 16+).

---

## Reference Implementation

The Android source lives in `host-app/app/src/main/java/com/simbridge/host/`.
Every feature in the Android app must have a 1:1 equivalent in the iOS app.
Read each Android file and translate to its iOS counterpart per the mapping
below.

---

## Project Structure

```
ios-host-app/
├── SimBridgeHost.xcodeproj
├── SimBridgeHost/
│   ├── App/
│   │   ├── SimBridgeHostApp.swift          ← @main entry, app lifecycle
│   │   └── AppState.swift                  ← ObservableObject shared state
│   ├── Data/
│   │   ├── Models.swift                    ← ↔ data/Models.kt
│   │   ├── ApiClient.swift                 ← ↔ data/ApiClient.kt
│   │   ├── Prefs.swift                     ← ↔ data/Prefs.kt (UserDefaults)
│   │   └── SecureTokenStore.swift          ← ↔ data/SecureTokenStore.kt (Keychain)
│   ├── Service/
│   │   ├── BridgeService.swift             ← ↔ service/BridgeService.kt
│   │   ├── WebSocketManager.swift          ← ↔ service/WebSocketManager.kt
│   │   ├── CommandHandler.swift            ← ↔ service/CommandHandler.kt
│   │   ├── SmsHandler.swift                ← ↔ service/SmsHandler.kt
│   │   ├── CallHandler.swift               ← ↔ service/CallHandler.kt
│   │   ├── SmsReceiver.swift               ← ↔ service/SmsReceiver.kt
│   │   └── SimInfoProvider.swift           ← ↔ service/SimInfoProvider.kt
│   ├── Telecom/
│   │   ├── CallManager.swift               ← ↔ telecom/BridgeConnectionService.kt
│   │   └── AudioBridge.swift               ← ↔ telecom/AudioBridge.kt
│   ├── WebRTC/
│   │   ├── WebRtcManager.swift             ← ↔ webrtc/WebRtcManager.kt
│   │   └── SignalingHandler.swift           ← ↔ webrtc/SignalingHandler.kt
│   ├── UI/
│   │   ├── Theme/
│   │   │   └── SimBridgeTheme.swift        ← ↔ ui/theme/Theme.kt
│   │   ├── Components/
│   │   │   ├── StatusCardView.swift        ← ↔ ui/component/StatusCard.kt
│   │   │   └── SimCardView.swift           ← ↔ ui/component/SimCard.kt
│   │   └── Screens/
│   │       ├── LoginView.swift             ← ↔ ui/screen/LoginScreen.kt
│   │       ├── BiometricPromptView.swift   ← ↔ ui/screen/BiometricPromptScreen.kt
│   │       ├── DashboardView.swift         ← ↔ ui/screen/DashboardScreen.kt
│   │       ├── LogView.swift               ← ↔ ui/screen/LogScreen.kt
│   │       └── SettingsView.swift          ← ↔ ui/screen/SettingsScreen.kt
│   └── Resources/
│       └── Assets.xcassets                 ← App icons, color assets
└── SimBridgeHostTests/                     ← Carol's test target
```

---

## Android → iOS Mapping

| Android                         | iOS Equivalent                           |
|---------------------------------|------------------------------------------|
| Jetpack Compose                 | SwiftUI                                  |
| Material 3 `MaterialTheme`      | Custom `SimBridgeTheme` environment key  |
| `SharedPreferences`             | `@AppStorage` / `UserDefaults`           |
| OkHttp `WebSocket`              | `URLSessionWebSocketTask`                |
| Gson `@SerializedName`          | `Codable` with `CodingKeys`             |
| `BroadcastReceiver` (SMS)       | No direct equivalent — use MessageUI or private API (see notes) |
| `ForegroundService`             | `BGTaskScheduler` + background URLSession |
| `TelecomManager.placeCall`      | `CXCallController` (CallKit)             |
| `ConnectionService`             | `CXProvider` (CallKit)                   |
| `SmsManager.sendTextMessage`    | `MFMessageComposeViewController` (user-triggered only) |
| `SubscriptionManager`           | `CTTelephonyNetworkInfo`                 |
| `AudioRecord`                   | `AVAudioEngine`                          |
| WebRTC `PeerConnectionFactory`  | WebRTC.framework (same C++ core)         |
| `rememberCoroutineScope`        | Swift structured concurrency (`Task {}`) |
| `mutableStateOf`                | `@State` / `@Published`                  |

---

## Layer-by-Layer Requirements

### Data Layer

**Models.swift** — Translate every data class from `Models.kt` to a
`Codable` struct. Use `CodingKeys` enum where the JSON key differs from
the Swift property name (e.g. `req_id` → `reqId`, `from_device_id` →
`fromDeviceId`). Keep the `ConnectionStatus` enum and `LogEntry` struct.

**ApiClient.swift** — Use `URLSession` with `async/await`. Implement:
- `login(serverUrl:username:password:) async throws -> LoginResponse`
- `registerDevice(name:) async throws -> DeviceResponse`
- `pair(code:) async throws -> PairResponse`

Set `Authorization: Bearer <token>` header. Timeout: 15 seconds.

**Prefs.swift** — Wrap `UserDefaults` with computed properties for
`serverUrl`, `token`, `deviceId`, `deviceName`, `biometricEnabled`. Add `var isLoggedIn: Bool`.
Add `func clear()`.

**SecureTokenStore.swift** — Keychain wrapper using the `Security` framework with
`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`. Methods: `saveToken(_:)`,
`getToken() -> String?`, `clear()`. Used for biometric unlock feature.

### Service Layer

**WebSocketManager.swift** — Use `URLSessionWebSocketTask`.
- Convert `http(s)://` to `ws(s)://`
- Append `?token=<jwt>` query parameter
- Implement exponential backoff reconnect: 1→2→4→8→16→30s cap
- 30-second ping interval via `sendPing(pongReceiveHandler:)`
- Publish `connectionStatus` as `@Published` on `ObservableObject`

**CommandHandler.swift** — Same dispatch table:
- `SEND_SMS` → SmsHandler
- `MAKE_CALL` → CallHandler
- `HANG_UP` → CallHandler
- `GET_SIMS` → SimInfoProvider

**SmsHandler.swift** — iOS cannot send SMS programmatically in the
background. Use `MFMessageComposeViewController` for user-initiated sends.
For automated relay, document that this requires an MDM/enterprise
entitlement or a Twilio/Vonage sidecar. Implement the compose path and
stub the automated path with a clear TODO.

**CallHandler.swift** — Use CallKit `CXCallController` to start/end calls.
Register a `CXProvider` for incoming call UI. Use
`CXStartCallAction(call:handle:)` with `CXHandle(type: .phoneNumber)`.

**SmsReceiver.swift** — iOS does not allow intercepting incoming SMS.
Document this limitation. For jailbroken devices or MDM setups, document
the private `CTMessageCenter` API as a reference only. For App Store
builds, incoming SMS relay is not possible.

**SimInfoProvider.swift** — Use `CTTelephonyNetworkInfo().serviceSubscriberCellularProviders`
to list carriers. iOS does not expose SIM slot numbers or phone numbers
directly. Return carrier name and `mobileCountryCode`/`mobileNetworkCode`.
Document the limitation vs Android.

### Telecom Layer

**CallManager.swift** — Implement `CXProviderDelegate`:
- `provider(_:perform startCallAction:)` — initiate outgoing call
- `provider(_:perform endCallAction:)` — terminate call
- Report call events back via callback

**AudioBridge.swift** — Use `AVAudioEngine` to tap call audio.
Configure audio session as `.voiceChat` with `.allowBluetooth` option.

### WebRTC Layer

Use the same Google WebRTC iOS framework (`WebRTC.framework` via CocoaPods
or SPM). Mirror the Android implementation:
- `WebRtcManager.swift` — PeerConnectionFactory, create offer/answer, ICE
- `SignalingHandler.swift` — Handle offer/answer/ice WsMessages

### UI Layer

Follow [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) exactly. Use SwiftUI with
`NavigationStack`. Implement these views matching the Android screens
pixel-for-pixel in layout (adapting to iOS idioms like navigation bars):

- **LoginView** — Server URL + username + password + login button + spinner + biometric offer after login
- **BiometricPromptView** — Face ID / Touch ID prompt using `LAContext`, falls back to login
- **DashboardView** — StatusCard + start/stop button + SIM list
- **LogView** — Scrollable log entries, monospace, color-coded direction
- **SettingsView** — Server info + device info + biometric toggle + battery/background + logout

### Theme

Create `SimBridgeTheme.swift` that provides:
- Color assets matching the palette in DESIGN_SYSTEM.md
- Dark mode support via `@Environment(\.colorScheme)`
- Reusable `ViewModifier`s for status colors

---

## iOS-Specific Limitations (vs Android)

| Feature               | Android                    | iOS                              |
|-----------------------|----------------------------|----------------------------------|
| Background SMS send   | `SmsManager` (automatic)   | Not possible (App Store)         |
| Incoming SMS intercept| `BroadcastReceiver`        | Not possible (App Store)         |
| SIM slot / number     | `SubscriptionManager`      | Carrier name only via `CTTelephonyNetworkInfo` |
| Persistent background | Foreground Service         | Limited — BGTaskScheduler + VOIP push |
| Call placement        | `TelecomManager.placeCall` | CallKit `CXStartCallAction`      |

Document each limitation clearly in the app's Settings screen under an
"iOS Limitations" section so users understand what differs from the Android
Host.

---

## Dependencies (Swift Package Manager)

| Package                | Purpose                     |
|------------------------|-----------------------------|
| WebRTC (GoogleWebRTC)  | Peer-to-peer audio          |
| *None for HTTP/WS*     | Use Foundation URLSession   |
| *None for JSON*        | Use Foundation Codable      |

---

## Acceptance Criteria

1. All four screens render identically to Android (per design system)
2. WebSocket connects, reconnects with backoff, handles ping/pong
3. `GET_SIMS` returns carrier info via CTTelephonyNetworkInfo
4. `MAKE_CALL` / `HANG_UP` work via CallKit
5. `SEND_SMS` opens MFMessageComposeViewController (documented limitation)
6. WebRTC audio call works between two devices
7. Logs display in real-time
8. Dark mode works correctly
9. Carol's test suite passes (see host-app-tests.md)
