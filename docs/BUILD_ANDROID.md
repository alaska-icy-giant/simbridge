# Building & Testing Android Apps

Instructions for building and testing the SimBridge Host App and Client App.

## Prerequisites

### Required

| Tool | Version | Purpose |
|------|---------|---------|
| **JDK** | **21 (recommended)** | Kotlin/Android compilation — only version where everything works |
| **Android SDK** | Platform 35 | Target SDK for both apps |
| **Android Build Tools** | 35.0.0 | APK packaging |

### Optional

| Tool | Version | Purpose |
|------|---------|---------|
| **Android Studio** | Ladybug 2024.2+ | IDE with integrated emulator |
| **Android Emulator** | API 34+ | Running instrumented (UI) tests |

### Java Version Compatibility

**JDK 21 is recommended.** It is the current LTS release and the only version where all tasks work:

| Task | Java 17 | Java 21 (recommended) | Java 25 |
|------|---------|----------------------|---------|
| Debug build | Works | Works | Works |
| Release build | Works | Works | Works |
| Unit tests | Works | Works | Works* |
| Lint | Works | Works | **Crashes** |
| Instrumented tests | Works | Works | Works |

\* Java 25 requires `net.bytebuddy.experimental=true` (already configured in `build.gradle.kts`).

To install JDK 21 via sdkman: `sdk install java 21-tem && sdk use java 21-tem`

---

## Environment Setup

### Option A: Android Studio (recommended)

1. Install [Android Studio](https://developer.android.com/studio)
2. Open `host-app/` or `client-app/` as a project
3. Android Studio auto-installs the SDK, build tools, and Gradle wrapper

### Option B: Command Line

```bash
# 1. Install JDK 21 (recommended)
#    macOS: brew install openjdk@21
#    Ubuntu: sudo apt install openjdk-21-jdk
#    Or use sdkman: sdk install java 21-tem && sdk default java 21-tem

# 2. Install Android SDK command-line tools
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -o tools.zip
unzip -qo tools.zip && mv cmdline-tools latest && rm tools.zip

# 3. Install required SDK components
export ANDROID_HOME=~/android-sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
    "platforms;android-35" \
    "build-tools;35.0.0" \
    "platform-tools"

# 4. Set ANDROID_HOME (add to ~/.bashrc or ~/.zshrc)
export ANDROID_HOME=~/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

---

## Building

Both apps include a Gradle wrapper (`./gradlew`) — no global Gradle install needed.

### Debug Build

```bash
# Host App
cd host-app
./gradlew :app:assembleDebug

# Client App
cd client-app
./gradlew :app:assembleDebug
```

### Release Build

```bash
# Host App
cd host-app
./gradlew :app:assembleRelease

# Client App
cd client-app
./gradlew :app:assembleRelease
```

> **Note**: Release APKs are unsigned. To install on a device, either sign them
> with a keystore or use debug builds instead.

### APK Output Locations

| Build | Path |
|-------|------|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Release | `app/build/outputs/apk/release/app-release-unsigned.apk` |

---

## Testing

### Unit Tests

Run JVM-based unit tests (no device/emulator needed):

```bash
# Host App (85 tests, all pass)
cd host-app
./gradlew :app:testDebugUnitTest

# Client App
cd client-app
./gradlew :app:testDebugUnitTest
```

Test reports: `app/build/reports/tests/testDebugUnitTest/index.html`

### Instrumented Tests (UI Tests)

Requires a running emulator or connected device:

```bash
# Start an emulator first (if using command line)
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "system-images;android-34;google_apis;x86_64"
$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd -n test -k "system-images;android-34;google_apis;x86_64"
$ANDROID_HOME/emulator/emulator -avd test -no-window &

# Wait for boot
adb wait-for-device
adb shell getprop sys.boot_completed  # wait until returns "1"

# Host App
cd host-app
./gradlew :app:connectedDebugAndroidTest

# Client App
cd client-app
./gradlew :app:connectedDebugAndroidTest
```

Test reports: `app/build/reports/androidTests/connected/index.html`

Instrumented tests include:
- `LoginScreenTest` — login form fields, button states, password toggle
- `SettingsScreenTest` — server info, device info, biometric toggle, logout dialog
- `SecureTokenStoreTest` — encrypted token save/get/clear round-trip
- `NavigationTest`, `DashboardScreenTest`, `LogScreenTest`, etc.

### Lint

```bash
# Requires JDK 21 or 17 (crashes on Java 25)
# If using Java 25, override JAVA_HOME for lint only:
#   JAVA_HOME=~/.sdkman/candidates/java/21-tem ./gradlew :app:lint

cd host-app
./gradlew :app:lint

cd client-app
./gradlew :app:lint
```

Lint report: `app/build/reports/lint-results-debug.html`

---

## Quick Reference

| Task | Command |
|------|---------|
| Build debug | `./gradlew :app:assembleDebug` |
| Build release | `./gradlew :app:assembleRelease` |
| Unit tests | `./gradlew :app:testDebugUnitTest` |
| Instrumented tests | `./gradlew :app:connectedDebugAndroidTest` |
| Lint | `./gradlew :app:lint` |
| Clean | `./gradlew clean` |
| List all tasks | `./gradlew tasks --all` |

---

## CI/CD

GitHub Actions workflows are in `.github/workflows/`:

- **`android.yml`** — Builds both apps, runs unit tests, runs instrumented tests on emulator (PRs only)
- **`e2e.yml`** — Backend Python tests (unrelated to Android builds)
