# Building & Testing iOS Apps

Instructions for building and testing the SimBridge iOS Host App and Client App.

## Prerequisites

### Required

| Tool | Version | Purpose |
|------|---------|---------|
| **macOS** | 13 (Ventura) or later | Build host OS |
| **Xcode** | 16.0+ | iOS SDK, simulator, and build tools |
| **Swift** | 5.10+ | Included with Xcode |

### Optional

| Tool | Version | Purpose |
|------|---------|---------|
| **Physical iOS device** | iOS 16+ | Required for telephony, CallKit, biometric testing |
| **Apple Developer account** | Any tier | Required for code signing and on-device testing |
| **xcbeautify** | Latest | Prettier xcodebuild output (`brew install xcbeautify`) |

---

## Project Structure

```
ios-host-app/
├── Package.swift                  ← SPM manifest (GoogleSignIn dependency)
├── Info.plist                     ← Permissions, background modes, Google Sign-In
├── SimBridgeHost/                 ← Source code (38 Swift files)
│   ├── App/
│   ├── Data/
│   ├── Service/
│   ├── Telecom/
│   ├── WebRTC/
│   └── UI/
└── SimBridgeHostTests/            ← Unit tests

ios-client-app/
├── Package.swift                  ← SPM manifest (GoogleSignIn dependency)
├── SimBridgeClient/               ← Source code (41 Swift files)
│   ├── App/
│   ├── Data/
│   ├── Service/
│   └── UI/
└── SimBridgeClientTests/          ← Unit tests
```

---

## Building

### Option A: Xcode (recommended)

This is the primary build method, especially for on-device testing.

#### Host App

1. Open Xcode
2. File → Open → select `ios-host-app/`
3. Xcode resolves SPM dependencies automatically (GoogleSignIn)
4. Select a simulator or connected device
5. Product → Build (`Cmd+B`)

#### Client App

1. File → Open → select `ios-client-app/`
2. Same steps as above

#### Xcode Project Setup (first time)

If Xcode doesn't recognize the directory as a project, create one:

1. File → New → Project → iOS → App
2. Product Name: `SimBridgeHost` (or `SimBridgeClient`)
3. Organization Identifier: `com.simbridge`
4. Interface: SwiftUI, Language: Swift
5. Minimum deployment target: iOS 16.0
6. Replace generated source files with the contents of `SimBridgeHost/` (or `SimBridgeClient/`)
7. File → Add Packages → `https://github.com/google/GoogleSignIn-iOS`

**Host App additional setup:**

8. Add frameworks (Build Phases → Link Binary With Libraries):
   - `CallKit.framework`
   - `AVFoundation.framework`
   - `CoreTelephony.framework`
   - `MessageUI.framework`
   - `LocalAuthentication.framework`
   - `Security.framework`
9. Signing & Capabilities → add:
   - Background Modes: Voice over IP, Background fetch, Audio
   - Push Notifications
10. Copy `Info.plist` entries from `ios-host-app/Info.plist`
11. Replace `YOUR_GOOGLE_CLIENT_ID` in Info.plist with your actual Google OAuth client ID

**Client App additional setup:**

8. Add frameworks:
   - `LocalAuthentication.framework`
   - `Security.framework`
9. Signing & Capabilities → add:
   - Background Modes: Background fetch

### Option B: Command Line (xcodebuild)

For CI or headless builds. Requires Xcode to be installed.

```bash
# Select Xcode version (if multiple installed)
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer

# Host App — build for simulator
cd ios-host-app
xcodebuild build \
    -scheme SimBridgeHost \
    -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest' \
    CODE_SIGNING_ALLOWED=NO

# Client App — build for simulator
cd ios-client-app
xcodebuild build \
    -scheme SimBridgeClient \
    -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest' \
    CODE_SIGNING_ALLOWED=NO
```

### Option C: Swift Package Manager (library target only)

SPM can build the library targets but **not** the full app (which requires an Xcode project for signing, Info.plist, entitlements, etc.).

```bash
# Resolve dependencies
cd ios-host-app
swift package resolve

cd ios-client-app
swift package resolve
```

---

## Testing

### Unit Tests via Xcode

1. Open project in Xcode
2. Product → Test (`Cmd+U`)
3. Or run a specific test: click the diamond icon next to the test function

### Unit Tests via Command Line

```bash
# Host App
cd ios-host-app
xcodebuild test \
    -scheme SimBridgeHost \
    -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest' \
    -resultBundlePath TestResults.xcresult \
    CODE_SIGNING_ALLOWED=NO

# Client App
cd ios-client-app
xcodebuild test \
    -scheme SimBridgeClient \
    -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest' \
    -resultBundlePath TestResults.xcresult \
    CODE_SIGNING_ALLOWED=NO
```

With `xcbeautify` for cleaner output:

```bash
xcodebuild test \
    -scheme SimBridgeHost \
    -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest' \
    CODE_SIGNING_ALLOWED=NO \
    2>&1 | xcbeautify
```

### Test Coverage

| App | Test Files | Coverage Areas |
|-----|-----------|----------------|
| **Host** | `BridgeServiceTests`, `CallHandlerTests`, `CommandHandlerTests`, `ModelsTests`, `SimInfoProviderTests`, `WebRtcManagerTests`, `WebSocketManagerTests`, `SecureTokenStoreTests` | Service layer, data models, WebSocket, WebRTC, secure storage |
| **Client** | `ApiClientTests`, `EventHandlerTests`, `ModelsTests`, `PrefsTests`, `WebSocketManagerTests`, `SecureTokenStoreTests`, `LoginViewTests`, `SettingsViewTests`, + UI tests | API client, event handling, preferences, secure storage, UI |

### Test Results

Results are saved to `TestResults.xcresult`. View in Xcode:
- Window → Organizer → select the result bundle
- Or: `xcrun xcresulttool get --path TestResults.xcresult`

---

## Running on Device

### Simulator

Most features work on the simulator except:
- Biometric unlock (no Face ID/Touch ID hardware; can simulate via Features → Face ID → Enrolled)
- CallKit incoming call UI
- Real telephony (SMS, calls)
- Push notifications (APNs)

```bash
# List available simulators
xcrun simctl list devices available

# Boot a specific simulator
xcrun simctl boot "iPhone 16"
```

### Physical Device

Required for:
- Biometric authentication (Face ID / Touch ID)
- CallKit integration
- Real network conditions testing
- Push notification testing

Setup:
1. Connect device via USB or enable wireless debugging
2. In Xcode: select the device from the destination picker
3. Trust the developer certificate on the device (Settings → General → VPN & Device Management)
4. Product → Run (`Cmd+R`)

---

## Google Sign-In Configuration

Both apps use Google Sign-In. To enable it:

1. Create a project in [Google Cloud Console](https://console.cloud.google.com/)
2. Create an iOS OAuth 2.0 Client ID
3. Download the `GoogleService-Info.plist`
4. Update `Info.plist`:
   - Replace `YOUR_GOOGLE_CLIENT_ID` with your actual client ID
   - Update `CFBundleURLSchemes` with the reversed client ID

Without Google Sign-In configured, the Google button will fail but username/password login still works.

---

## Known Limitations

| Issue | Impact | Workaround |
|-------|--------|------------|
| **SMS relay not possible on iOS Host** | Cannot send/receive SMS programmatically | Use Android Host for SMS features |
| **Background WebSocket dies ~30s** | App goes offline when backgrounded | APNs push (not yet implemented) |
| **Call audio capture not possible** | Cannot bridge cellular call audio | WebRTC-only calls work |
| **No phone number from SIM** | Client sees carrier name, not number | Display carrier name as SIM label |
| **Keychain tests may fail on CI** | SecureTokenStoreTests need Keychain access | Tests work on macOS, may need entitlements on CI |

See [platform_challenges.md](platform_challenges.md) § 7 for full iOS restriction details.

---

## Quick Reference

| Task | Command |
|------|---------|
| Build (simulator) | `xcodebuild build -scheme <Scheme> -destination 'platform=iOS Simulator,name=iPhone 16'` |
| Run tests | `xcodebuild test -scheme <Scheme> -destination 'platform=iOS Simulator,name=iPhone 16'` |
| Resolve SPM | `swift package resolve` |
| Clean | `xcodebuild clean -scheme <Scheme>` |
| List simulators | `xcrun simctl list devices available` |

Schemes: `SimBridgeHost`, `SimBridgeClient`

---

## CI/CD

GitHub Actions workflow is in `.github/workflows/ios.yml`:

- Runs on `macos-14` with Xcode 16.2
- Builds both apps for iPhone 16 simulator
- Runs unit tests with `CODE_SIGNING_ALLOWED=NO`
- Uploads `.xcresult` bundles as artifacts
- Triggers only on changes to `ios-host-app/` or `ios-client-app/`

### Firebase App Distribution (optional)

On pushes to `master`, the workflow can sign, archive, and distribute IPAs to testers via Firebase App Distribution. **This is optional** — without the Apple signing secrets, these steps are automatically skipped and CI still runs builds and tests normally.

#### Prerequisites

- **Apple Developer Account** ($99/year) — required for code signing
- **Firebase project** with both iOS apps registered

#### Setup steps

1. **Export a distribution certificate (.p12)**
   - Open Keychain Access on your Mac
   - Apple Developer Portal → Certificates → create/download an **Apple Distribution** certificate
   - In Keychain Access, right-click the certificate → Export → save as `.p12`, set a password

2. **Create Ad Hoc provisioning profiles**
   - Apple Developer Portal → Profiles → New → **Ad Hoc**
   - Create one for the host app bundle ID, one for the client app bundle ID
   - Add your test devices by UDID
   - Download both `.mobileprovision` files

3. **Create `ExportOptions.plist` files** (commit to repo)

   `ios-host-app/ExportOptions.plist`:
   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
     "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
   <plist version="1.0">
   <dict>
       <key>method</key>
       <string>ad-hoc</string>
       <key>teamID</key>
       <string>YOUR_TEAM_ID</string>
   </dict>
   </plist>
   ```

   Create the same file at `ios-client-app/ExportOptions.plist`.

4. **Add GitHub Actions secrets**

   Encode the certificate and profiles:
   ```bash
   base64 -i Certificates.p12 | pbcopy                        # → IOS_P12_BASE64
   base64 -i SimBridgeHost_AdHoc.mobileprovision | pbcopy      # → IOS_HOST_PROVISION_PROFILE_BASE64
   base64 -i SimBridgeClient_AdHoc.mobileprovision | pbcopy    # → IOS_CLIENT_PROVISION_PROFILE_BASE64
   ```

   Go to repo Settings → Secrets and variables → Actions → add:

   | Secret | Description |
   |---|---|
   | `IOS_P12_BASE64` | Base64-encoded `.p12` distribution certificate |
   | `IOS_P12_PASSWORD` | Password for the `.p12` file |
   | `IOS_HOST_PROVISION_PROFILE_BASE64` | Base64-encoded Ad Hoc profile for host app |
   | `IOS_CLIENT_PROVISION_PROFILE_BASE64` | Base64-encoded Ad Hoc profile for client app |
   | `FIREBASE_IOS_HOST_APP_ID` | Firebase App ID for iOS host app (e.g. `1:123456:ios:aaa`) |
   | `FIREBASE_IOS_CLIENT_APP_ID` | Firebase App ID for iOS client app |
   | `FIREBASE_SERVICE_ACCOUNT` | Firebase service account JSON (shared with Android) |

   > **Note**: `FIREBASE_SERVICE_ACCOUNT` is per-project, not per-app. If you already set it for Android distribution, reuse the same secret.

#### Without an Apple Developer account

If the 4 Apple signing secrets (`IOS_P12_BASE64`, `IOS_P12_PASSWORD`, `IOS_HOST_PROVISION_PROFILE_BASE64`, `IOS_CLIENT_PROVISION_PROFILE_BASE64`) are not configured, all signing and distribution steps are **automatically skipped**. CI will still:

- Build both apps for simulator
- Run unit tests
- Upload test results

No changes to the workflow are needed — just add the secrets when you're ready.
