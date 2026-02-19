# Bob — iOS Client App Spec

> Build a native iOS Client App (Phone B — the remote control) in
> Swift 5.10+ / SwiftUI / iOS 16+.

There is no existing Android client app — this is a new build. The client
interacts with SimBridge via the same REST + WebSocket API that the
`test_container.py` integration tests exercise.

---

## What the Client App Does

The Client App is the **remote control**. The user opens it on Phone B to:

1. Log in to the SimBridge relay server
2. Register this phone as a "client" device
3. Pair with a Host device (Phone A) using a 6-digit code
4. Compose and send SMS through the Host phone's SIM
5. Initiate and manage calls through the Host phone
6. View message/call history
7. Receive real-time events (incoming SMS, call state) via WebSocket

---

## Project Structure

```
ios-client-app/
├── SimBridgeClient.xcodeproj
├── SimBridgeClient/
│   ├── App/
│   │   ├── SimBridgeClientApp.swift        ← @main entry
│   │   └── AppState.swift                  ← ObservableObject shared state
│   ├── Data/
│   │   ├── Models.swift                    ← Shared Codable models
│   │   ├── ApiClient.swift                 ← REST client (login, devices, pair, sms, call, history)
│   │   ├── Prefs.swift                     ← UserDefaults wrapper
│   │   └── SecureTokenStore.swift          ← Keychain wrapper for biometric token
│   ├── Service/
│   │   ├── WebSocketManager.swift          ← WS connection to relay
│   │   └── EventHandler.swift              ← Process incoming events from Host
│   ├── UI/
│   │   ├── Theme/
│   │   │   └── SimBridgeTheme.swift        ← Same theme as Host app
│   │   ├── Components/
│   │   │   ├── StatusCardView.swift        ← Connection status card
│   │   │   ├── SimCardView.swift           ← Remote SIM info display
│   │   │   └── MessageBubble.swift         ← SMS conversation bubble
│   │   └── Screens/
│   │       ├── LoginView.swift             ← Login screen + biometric offer
│   │       ├── BiometricPromptView.swift   ← Biometric unlock screen
│   │       ├── PairView.swift              ← Enter 6-digit pairing code
│   │       ├── DashboardView.swift         ← Status + paired host info
│   │       ├── ComposeView.swift           ← Compose SMS
│   │       ├── DialerView.swift            ← Dial a phone number
│   │       ├── HistoryView.swift           ← Message/call history
│   │       └── SettingsView.swift          ← Server, device, biometric toggle, logout
│   └── Resources/
│       └── Assets.xcassets
└── SimBridgeClientTests/                   ← Dave's test target
```

---

## Navigation

```
LOGIN ──► PAIR ──► DASHBOARD
                      ├──► COMPOSE (SMS)
                      ├──► DIALER (Call)
                      ├──► HISTORY
                      └──► SETTINGS ──(logout)──► LOGIN
```

- Start at LOGIN if no token
- After login, if no pairing exists, go to PAIR
- After pairing confirmed, go to DASHBOARD
- DASHBOARD is the main hub with tab or button access to other screens

---

## API Endpoints Used

| Method | Endpoint         | Purpose                      |
|--------|------------------|------------------------------|
| POST   | `/auth/register` | Create account               |
| POST   | `/auth/login`    | Get JWT token                |
| POST   | `/devices`       | Register as `type: "client"` |
| GET    | `/devices`       | List user's devices          |
| POST   | `/pair/confirm`  | Confirm pairing with code    |
| POST   | `/sms`           | Send SMS via paired Host     |
| POST   | `/call`          | Make call via paired Host    |
| GET    | `/sims`          | Get Host's SIM info          |
| GET    | `/history`       | Get message/call logs        |
| WS     | `/ws/client/{id}`| Real-time event stream       |

---

## Screen Specs

### LoginView
Identical layout to the Host app login screen (see DESIGN_SYSTEM.md):
- Server URL field
- Username field
- Password field with visibility toggle
- Login button with inline spinner
- Error display
- Additionally: "Create Account" link that calls `/auth/register` first

### PairView
- Text: "Enter the 6-digit code shown on the Host device"
- 6-digit code input (large, centered, spaced)
- "Pair" button
- Success → navigate to Dashboard
- Error → show message (expired, invalid)

### DashboardView
- StatusCard showing WebSocket connection status
- Paired Host device card (name, online/offline status)
- Remote SIM cards list (fetched via GET /sims when host is online)
- Action buttons:
  - "Send SMS" → ComposeView
  - "Make Call" → DialerView
- Toolbar: History + Settings icons
- Real-time event feed (last 5 events: incoming SMS, call state changes)

### ComposeView
- "To" phone number field with phone keyboard
- SIM selector (SIM 1 / SIM 2, from GET /sims data)
- Message body text area
- "Send" button → POST /sms
- Success/error feedback
- Sent message appears in history

### DialerView
- Phone number field or dialer pad
- SIM selector
- "Call" button → POST /call
- Active call card showing state (dialing, active, ended)
- "Hang Up" button → future POST /hangup or WS command

### HistoryView
- List of MessageLog entries from GET /history
- Each entry: timestamp, direction icon, type badge (SMS/Call), summary
- Pull-to-refresh
- Filter by device (if multiple hosts paired)
- Tap entry to see full payload

### SettingsView
Same layout as Host app settings:
- Server URL, device info
- Paired Host info
- Logout with confirmation

---

## Service Layer

### WebSocketManager.swift
Same implementation as Host app WebSocket manager:
- Connect to `/ws/client/{deviceId}?token=<jwt>`
- Exponential backoff reconnect
- 30-second ping interval
- Publish `connectionStatus` as `@Published`

### EventHandler.swift
Process incoming WebSocket messages from the paired Host:

| Event Type     | Action                                  |
|----------------|-----------------------------------------|
| `INCOMING_SMS` | Show local notification + add to history|
| `CALL_STATE`   | Update call UI (dialing/active/ended)   |
| `SMS_SENT`     | Update compose screen (sent/failed)     |
| `SIM_INFO`     | Update SIM list on Dashboard            |
| `ERROR`        | Show error alert                        |

Use `UNUserNotificationCenter` for local notifications when the app is
backgrounded.

---

## Data Layer

### Models.swift
Reuse the same `Codable` structs from the Host app models, plus add:

```swift
struct SmsCommand: Codable {
    let toDeviceId: Int      // "to_device_id"
    let sim: Int
    let to: String
    let body: String
}

struct CallCommand: Codable {
    let toDeviceId: Int
    let sim: Int
    let to: String
}

struct HistoryEntry: Codable {
    let id: Int
    let fromDeviceId: Int
    let toDeviceId: Int
    let msgType: String
    let payload: [String: AnyCodable]
    let createdAt: String?
}

struct PairConfirm: Codable {
    let code: String
    let clientDeviceId: Int   // "client_device_id"
}
```

### ApiClient.swift
Full REST client using URLSession async/await:

```swift
func register(serverUrl:username:password:) async throws -> RegisterResponse
func login(serverUrl:username:password:) async throws -> LoginResponse
func registerDevice(name:) async throws -> DeviceResponse
func listDevices() async throws -> [DeviceResponse]
func confirmPair(code:clientDeviceId:) async throws -> PairResponse
func sendSms(_:) async throws -> RelayResponse
func makeCall(_:) async throws -> RelayResponse
func getSims(hostDeviceId:) async throws -> RelayResponse
func getHistory(deviceId:limit:) async throws -> [HistoryEntry]
```

### Prefs.swift
Same as Host app: `serverUrl`, `token`, `deviceId`, `deviceName`, plus:
- `pairedHostId: Int`
- `pairedHostName: String`
- `biometricEnabled: Bool`

### SecureTokenStore.swift
Keychain wrapper using the `Security` framework with
`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`. Methods: `saveToken(_:)`,
`getToken() -> String?`, `clear()`. Used for biometric unlock feature.

---

## UI / Theme

Use the **exact same** `SimBridgeTheme.swift` and color palette from the
Host app. Both apps must look like they belong to the same product family.
See [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md).

The only visual differences:
- App name: "SimBridge Client" (vs "SimBridge Host")
- Client has Compose/Dialer/History screens (Host does not)
- Client does not have a Start/Stop Service button
- Client shows "Paired Host: <name>" on Dashboard

---

## Dependencies (Swift Package Manager)

| Package | Purpose |
|---------|---------|
| *None*  | Use Foundation URLSession for HTTP + WebSocket |
| *None*  | Use Foundation Codable for JSON |

No third-party dependencies required for the Client app.

---

## Acceptance Criteria

1. Login, register, device creation work against live SimBridge
2. Pairing with 6-digit code succeeds
3. Compose SMS sends via REST and Host receives command over WebSocket
4. Make Call sends via REST and Host receives command
5. History screen shows all relayed messages
6. WebSocket receives real-time events (incoming SMS, call state)
7. Local notifications fire for incoming SMS when app is backgrounded
8. All screens match DESIGN_SYSTEM.md
9. Dark mode works correctly
10. Dave's test suite passes (see client-app-tests.md)
