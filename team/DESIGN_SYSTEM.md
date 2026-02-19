# SimBridge Design System

Cross-platform design specification for iOS and Android apps. Both the Host
and Client apps **must** follow these rules to maintain visual consistency.

---

## 1. Color Palette

All colors are specified as hex. Use the platform's dynamic/system color
APIs when available (Material You on Android 12+, iOS 15+ tintColor), but
fall back to these exact values on older OS versions.

| Role             | Light          | Dark           |
|------------------|----------------|----------------|
| Primary          | `#1976D2`      | `#90CAF9`      |
| On Primary       | `#FFFFFF`      | `#003258`      |
| Primary Container| `#BBDEFB`      | `#00497D`      |
| Secondary        | `#43A047`      | `#81C784`      |
| On Secondary     | `#FFFFFF`      | `#003910`      |
| Error            | `#D32F2F`      | `#EF9A9A`      |
| Surface          | `#FFFBFE`      | `#1C1B1F`      |
| On Surface       | `#1C1B1F`      | `#E6E1E5`      |

### Status Colors

| Status         | Light Icon/Text | Card Background |
|----------------|-----------------|-----------------|
| Connected      | Primary         | Primary @ 10%   |
| Connecting     | Tertiary/Amber  | Tertiary @ 10%  |
| Disconnected   | Error           | Error @ 10%     |

### Log Direction Colors

| Direction | Color   |
|-----------|---------|
| IN        | Primary |
| OUT       | Secondary (green) |

---

## 2. Typography

| Platform | System         |
|----------|----------------|
| Android  | Material 3 default type scale |
| iOS      | SF Pro (Dynamic Type) |

### Mapping

| Usage               | Android Token          | iOS Equivalent              |
|---------------------|------------------------|-----------------------------|
| Screen title        | `titleLarge`           | `.title` / Navigation title |
| Section header      | `titleMedium`          | `.headline`                 |
| Body text           | `bodyMedium`           | `.body`                     |
| Small/error text    | `bodySmall`            | `.footnote`                 |
| Log entry text      | `bodySmall` monospace  | `.caption` monospace        |
| Status card label   | `titleLarge`           | `.title2` semibold          |

---

## 3. Iconography

Use platform-native filled icon sets. Map icons consistently:

| Meaning         | Android (Material)      | iOS (SF Symbols)           |
|-----------------|-------------------------|----------------------------|
| Connected       | `Icons.Filled.CheckCircle` | `checkmark.circle.fill` |
| Connecting      | `Icons.Filled.Cloud`    | `cloud.fill`               |
| Disconnected    | `Icons.Filled.CloudOff` | `cloud.slash.fill`         |
| SIM card        | `Icons.Filled.SimCard`  | `simcard.fill`             |
| Logs            | `Icons.Filled.List`     | `list.bullet`              |
| Settings        | `Icons.Filled.Settings` | `gearshape.fill`           |
| Visibility on   | `Icons.Filled.Visibility` | `eye.fill`               |
| Visibility off  | `Icons.Filled.VisibilityOff` | `eye.slash.fill`      |
| Back            | `Icons.AutoMirrored.Filled.ArrowBack` | `chevron.left` |

---

## 4. Spacing & Layout

| Token         | Value  |
|---------------|--------|
| Screen padding| 16 dp / 16 pt |
| Login padding | 24 dp / 24 pt |
| Card padding  | 20 dp / 20 pt (status), 16 dp / 16 pt (info) |
| Item gap      | 16 dp / 16 pt |
| Spacer small  | 8 dp / 8 pt   |
| Spacer medium | 12 dp / 12 pt |
| Spacer large  | 32 dp / 32 pt |
| Icon size (status) | 32 dp / 32 pt |
| Icon-text gap | 12 dp / 12 pt |

---

## 5. Component Specs

### StatusCard
- Full-width card
- Background: status color at 10% alpha
- Content: centered row — icon (32pt) + 12pt gap + label (titleLarge)
- Internal padding: 20pt all sides

### SimCard
- Full-width card on surfaceVariant
- Row: SIM icon + column (slot header + carrier + number)
- 16pt internal padding

### Login Form
- Centered column
- Headline: "Connect to Relay Server"
- OutlinedTextField for Server URL, Username, Password
- Password field has trailing eye icon toggle
- Full-width primary button with inline spinner when loading
- Error text in `error` color below fields

### Dashboard
- TopAppBar: app title + log icon + settings icon
- Scrollable column: StatusCard → Start/Stop button → SIM list
- Start button: primary colors
- Stop button: error colors
- Empty SIM state: surfaceVariant card with guidance text

### Log Screen
- Scrollable list, newest first
- Each row: `HH:mm:ss  [IN/OUT]  summary`
- Monospace font
- IN = primary color, OUT = secondary color

### Settings Screen
- Info cards for server URL, device name/ID
- Biometric Unlock toggle row (only visible if device supports biometric)
  - Switch/Toggle to enable or disable
  - Enabling saves JWT to platform-secure storage (Android Keystore / iOS Keychain)
  - Disabling clears the secure storage
- Battery optimization row (Android) / Background App Refresh (iOS)
- Destructive logout button with confirmation alert (also clears biometric state)

---

## 6. Navigation Structure

Both apps share the same navigation graph:

```
BIOMETRIC ──(success)──► DASHBOARD
    │ (fallback)            ├──► LOG
    ▼                       └──► SETTINGS ──(logout)──► LOGIN
LOGIN ──(success)──► DASHBOARD
```

- If `biometricEnabled` and secure token exists, start at BIOMETRIC
- Else if `token` exists in local storage, start at DASHBOARD
- Otherwise start at LOGIN
- Biometric success retrieves token from secure storage → DASHBOARD
- Biometric failure/cancel falls back to LOGIN
- Logout clears storage (including secure biometric storage) and resets to LOGIN

---

## 7. Dark Mode

- Both apps must support system dark mode
- Use the dark palette above as fallback
- On Android 12+: use Material You dynamic colors
- On iOS 15+: respect system appearance, use Asset Catalog colors

---

## 8. Platform-Specific Notes

### iOS
- Use SwiftUI with `NavigationStack`
- Use `@AppStorage` / `UserDefaults` for prefs (equivalent to SharedPreferences)
- Use `URLSessionWebSocketTask` for WebSocket (equivalent to OkHttp WebSocket)
- Use `Codable` structs (equivalent to Gson data classes)
- Notifications via `UNUserNotificationCenter`
- Background execution via BGTaskScheduler + URLSession background config

### Android (existing)
- Jetpack Compose + Material 3
- SharedPreferences via Prefs wrapper
- OkHttp WebSocket with exponential backoff
- Foreground Service for persistent connection
- Gson for JSON serialization
