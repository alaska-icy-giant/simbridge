# SimBridge — Platform Challenges & Mitigations

Hard problems that affect a SIM-bridging system on Android and iOS, what the apps do today, what's still missing, and how the Relay Server helps (or should help).

Sections 1–6 cover Android-specific challenges. Section 7 covers iOS platform restrictions.

---

## 1. Android Background Restrictions

### The Problem

Android aggressively kills background processes to save battery. Multiple layers conspire against a long-lived service:

| Restriction | Since | Effect |
|-------------|-------|--------|
| **Doze mode** | Android 6 | Defers network, alarms, jobs when screen off for extended periods |
| **App Standby Buckets** | Android 9 | Limits how often "rare" apps can run jobs or access network |
| **Background execution limits** | Android 8 | Services killed ~1 minute after app leaves foreground |
| **OEM battery killers** | Always | Xiaomi (MIUI), Samsung (Device Care), Huawei (EMUI), OnePlus (Battery Optimization) apply vendor-specific kills on top of stock Android |
| **Phantom process killer** | Android 12+ | Limits child processes and excessive wake-ups |
| **Task killers** | User action | Users swipe-kill the app from Recents |

A WebSocket that dies means the Host can't receive commands until it reconnects — the Client sees "Host offline" with no way to wake it.

### What the Host App Does Today

- **Foreground Service** (`BridgeService.kt`): Runs with `startForeground()` and a persistent notification. This is the single most effective defense — Android treats foreground services as user-visible and avoids killing them.
- **`START_STICKY`**: If the OS does kill the service, `onStartCommand()` returns `START_STICKY` so Android will restart it.
- **Foreground service types**: Declared as `phoneCall|microphone` in the manifest, which gives higher priority than a generic foreground service.
- **Battery optimization UI**: `SettingsScreen.kt` has a button that opens `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` to whitelist the app from Doze.

### What's Missing

| Gap | Severity | Mitigation |
|-----|----------|------------|
| **No `WakeLock`** | High | When the screen is off and the CPU dozes, the WebSocket read loop may stall. Need a `PARTIAL_WAKE_LOCK` held while the service is running. |
| **No network connectivity listener** | Medium | If Wi-Fi drops and mobile data takes over, the WebSocket silently dies. Should use `ConnectivityManager.NetworkCallback` to detect changes and force reconnect immediately instead of waiting for the next ping timeout. |
| **No OEM-specific guidance** | Medium | Xiaomi/Samsung/Huawei each need manual steps (autostart permission, battery saver whitelist). The Settings screen should detect the OEM and show brand-specific instructions. |
| **No `AlarmManager` heartbeat** | Low | A periodic exact alarm (every 5 min) could check if the service is alive and restart it. Survives Doze because `setExactAndAllowWhileIdle()` is permitted for foreground apps. |

### How the Relay Helps

The relay can't force-wake a killed Android process, but it can:

1. **Detect host death** — Track the last WebSocket ping/pong timestamp. If no pong for >60s, mark the host as "offline" and inform the client immediately instead of letting commands time out.
2. **Buffer commands** — When the host is offline, queue commands in the database. On reconnect, replay the queue. Today commands are lost if the host is down.
3. **FCM push wakeup** (future) — Send a high-priority FCM message when a command arrives and the host WebSocket is dead. The FCM message triggers a `FirebaseMessagingService` that restarts `BridgeService`. This is the only reliable way to wake a killed app on modern Android.
4. **Client-visible status** — Surface connection quality to the client (last seen, average uptime, reconnect count) so users know if Phone A is reachable before sending a command.

---

## 2. Call Audio Capture

### The Problem

Android severely restricts access to call audio. This is the hardest technical challenge in the entire system:

| Android Version | Restriction |
|----------------|-------------|
| **Android 9 and below** | `MediaRecorder.AudioSource.VOICE_CALL` works on most devices. Both near-end and far-end audio captured. |
| **Android 10** | `VOICE_CALL` and `VOICE_DOWNLINK` sources blocked for third-party apps. Only system apps or apps with `CAPTURE_AUDIO_OUTPUT` (signature-level) can use them. |
| **Android 11+** | `AccessibilityService`-based workarounds patched. Even root-based approaches become harder with verified boot. |
| **Android 12+** | `VOICE_COMMUNICATION` source works but only captures **microphone audio** (near-end), not the remote party's voice. |

**The core issue**: We need **both sides of the call** — the local mic (near-end) and the remote speaker (far-end) — to bridge a phone call to the Client. Android gives us the mic but blocks programmatic access to the earpiece/speaker audio.

### What the Host App Does Today

- **`AudioBridge.kt`**: Uses `AudioRecord` with `VOICE_COMMUNICATION` source at 16 kHz mono. This captures the local microphone only.
- **`WebRtcManager.kt`**: Creates a local audio track with echo cancellation, noise suppression, and AGC enabled. The track uses `JavaAudioDeviceModule` which reads from the device mic.

### What's Missing (Critical)

| Gap | Severity | Notes |
|-----|----------|-------|
| **`AudioBridge` is disconnected** | Critical | `startCapture()` is never called from anywhere. The capture thread reads audio into a buffer and discards it. The captured data is never fed into the WebRTC audio track. |
| **Far-end audio not captured** | Critical | `VOICE_COMMUNICATION` only gets the mic. The remote party's voice (what comes out of the earpiece) is not accessible on Android 10+. |
| **No speakerphone routing** | High | Switching to speakerphone and capturing via `VOICE_COMMUNICATION` can sometimes pick up both sides (mic + speaker bleed), but this is device-dependent and low quality. |

### Realistic Approaches (Ordered by Feasibility)

#### Approach A: Speakerphone + Acoustic Capture (Works Today)
Force the call to speakerphone mode via `AudioManager.setSpeakerphoneOn(true)`. The microphone picks up both the user's voice and the speaker output (far-end). WebRTC's echo cancellation handles the feedback loop.

- **Pros**: Works on all Android versions, no special permissions.
- **Cons**: Audio quality is poor. Background noise. Privacy concern (everyone nearby hears the call). Echo cancellation is imperfect.
- **Best for**: Proof-of-concept, unattended phones in a quiet location.

#### Approach B: ConnectionService Audio Routing (Best for Android 12+)
Register a `ConnectionService` and `PhoneAccount` (already done in `BridgeConnectionService.kt`). When the system routes a call through our `ConnectionService`, we gain access to the `Connection`'s audio stream. Use `Connection.setAudioRoute()` to route audio through our pipeline.

- **Pros**: Legitimate API, works with dual SIM.
- **Cons**: Only works for calls **we initiate or answer** through our PhoneAccount. Doesn't intercept calls placed via the normal dialer. Requires the user to set SimBridge as the default calling app or use a self-managed `PhoneAccount`.
- **Implementation**: The `BridgeConnection.kt` skeleton exists but doesn't wire audio. Need to intercept `onCallAudioStateChanged()` and route the audio stream.

#### Approach C: Opting-In via Companion Device (Android 13+)
Use the `CompanionDeviceManager` API to register as a companion app. Companion apps get elevated background permissions and some audio access.

- **Pros**: Official API path.
- **Cons**: Limited audio capabilities, requires user opt-in, not available on all devices.

#### Approach D: Root / System App (Nuclear Option)
As a system app (installed in `/system/priv-app/`), the app can use `CAPTURE_AUDIO_OUTPUT` permission and access `VOICE_CALL` / `VOICE_DOWNLINK` audio sources directly.

- **Pros**: Full bidirectional call audio on any Android version.
- **Cons**: Requires rooted device or custom ROM. Not viable for general users.
- **Best for**: Dedicated bridge phones that the user controls.

### How the Relay Helps

The relay is mostly a bystander here — audio capture is an on-device problem. But it can:

1. **Negotiate audio quality** — Include codec preferences and bitrate caps in the WebRTC signaling. The relay can inject `a=fmtp` parameters to prefer Opus at lower bitrates, reducing the bandwidth needed for degraded speakerphone audio.
2. **Signal audio mode** — The relay can pass an `audio_mode` field in call commands (`speakerphone`, `earpiece`, `bluetooth`) so the host knows how to configure `AudioManager` before capturing.
3. **Provide TURN relay for audio** — If direct P2P fails, the relay's TURN server ensures audio still flows, just with higher latency (see §6).

---

## 3. Dual SIM Fragmentation

### The Problem

Android's dual SIM support (`SubscriptionManager`) is a minefield of OEM inconsistency:

| Issue | Affected Devices | Impact |
|-------|-----------------|--------|
| **Slot index ≠ subscription order** | Samsung, some Xiaomi | `callCapablePhoneAccounts[0]` might be SIM 2 |
| **eSIM vs physical SIM** | Pixel, iPhone (future), Samsung | eSIM has a slot index but no physical tray slot |
| **Hot-swap SIM** | All | Removing a SIM changes subscription list at runtime |
| **Carrier-locked SIM selection** | Some carriers | Carrier apps override `SmsManager` SIM selection |
| **`getPhoneNumber()` returns empty** | Most carriers | Phone number is rarely stored on the SIM; MSISDN field is optional |
| **`READ_PHONE_NUMBERS` vs `READ_PHONE_STATE`** | Android 12+ | Different permissions for number vs. state |
| **Subscription ID changes** | After SIM swap/reboot | `subscriptionId` is not stable across reboots on some OEMs |

### What the Host App Does Today

- **`SimInfoProvider.kt`**: Reads `SubscriptionManager.activeSubscriptionInfoList`, converts 0-indexed `simSlotIndex` to 1-indexed for user-facing display.
- **`SmsHandler.kt`**: Uses `SmsManager.getSmsManagerForSubscriptionId()` to send SMS from a specific SIM.
- **`CallHandler.kt`**: Uses `callCapablePhoneAccounts.getOrNull(simSlot - 1)` to select a SIM for outgoing calls.

### What's Missing

| Gap | Severity | Notes |
|-----|----------|-------|
| **Fragile SIM-to-PhoneAccount mapping** | High | `callCapablePhoneAccounts[slot-1]` assumes the list is ordered by slot. On Samsung devices, this is often wrong. Need to match by `subscriptionId` instead. |
| **No subscription change listener** | Medium | If a SIM is removed or swapped, the cached SIM list goes stale. Should register `SubscriptionManager.OnSubscriptionsChangedListener` to auto-refresh. |
| **No carrier-specific workarounds** | Medium | Some carriers (e.g., China Mobile) require specific APN settings for SMS. Some Samsung models need `Intent.ACTION_SENDTO` instead of `SmsManager` for dual SIM. |
| **No eSIM awareness** | Low | eSIM slots report as `simSlotIndex = 0` on many devices, colliding with physical SIM 1. Should use `subscriptionId` as the canonical identifier, not slot index. |
| **Phone number often empty** | Low | `getPhoneNumber()` returns empty for most carriers. The `SIM_INFO` event should flag this so the Client UI doesn't show blank numbers. |

### Correct SIM Selection Algorithm

```
Current (fragile):
  slot 1 → callCapablePhoneAccounts[0]
  slot 2 → callCapablePhoneAccounts[1]

Correct:
  1. Get SubscriptionInfo for requested slot via simSlotIndex
  2. Get subscriptionId from SubscriptionInfo
  3. For SMS: SmsManager.getSmsManagerForSubscriptionId(subId) ← already correct
  4. For calls: Iterate callCapablePhoneAccounts, match by comparing
     PhoneAccountHandle.id with subscriptionId.toString()
```

### How the Relay Helps

1. **Canonical SIM identifiers** — The relay should store SIM cards by `subscriptionId` (stable per SIM) rather than slot index (fragile). When the host reports `SIM_INFO`, the relay maps `subscriptionId` → friendly name and exposes that to the client.
2. **SIM change notifications** — When the host detects a subscription change, it sends an updated `SIM_INFO` event. The relay pushes this to the client so the UI always shows current SIM state.
3. **Fallback routing** — If a command specifies `"sim": 1` but slot 1 is empty, the relay can reject with a clear error ("SIM 1 not available, current SIMs: [2: CHT]") instead of letting the host silently fall back to the default SIM.

---

## 4. Voice Latency

### The Problem

The audio path for a bridged call has multiple latency sources:

```
Remote caller ──► Cell tower ──► Phone A earpiece ──► Mic capture ──► WebRTC encode
    ──► Network (STUN/TURN) ──► WebRTC decode ──► Phone B speaker ──► User's ear

Total: ~300–800ms one-way (vs. ~150ms for a normal cell call)
```

| Latency Source | Typical | Notes |
|---------------|---------|-------|
| Cellular network | 50–150ms | Unavoidable, depends on carrier |
| `AudioRecord` buffer | 20–60ms | Buffer size determines capture latency |
| WebRTC encode (Opus) | 20ms | Opus default frame size |
| Network transit (P2P) | 10–50ms | If STUN succeeds, direct connection |
| Network transit (TURN) | 50–200ms | If TURN relay needed, adds hop |
| WebRTC jitter buffer | 20–80ms | Adapts to network jitter |
| `AudioTrack` playout | 20–40ms | Playback buffer |
| **Total one-way** | **190–600ms** | Varies wildly |

Above ~400ms one-way, conversation becomes awkward (users talk over each other). Above ~800ms, it's unusable.

### What the Host App Does Today

- **`WebRtcManager.kt`**: Enables hardware echo cancellation and noise suppression via `JavaAudioDeviceModule`. Uses Opus codec (WebRTC default) which is low-latency.
- **`AudioBridge.kt`**: Uses 16 kHz sample rate with minimum buffer size, which is reasonable for voice.

### What's Missing

| Gap | Severity | Notes |
|-----|----------|-------|
| **No audio session configuration** | High | Should set `AudioManager.MODE_IN_COMMUNICATION` and request `STREAM_VOICE_CALL` for lowest-latency audio path. |
| **No OpenSL ES / AAudio** | Medium | `JavaAudioDeviceModule` uses Java `AudioRecord`/`AudioTrack` which have higher latency than native audio. WebRTC supports `OpenSLES` audio device module for lower latency on Android. |
| **No jitter buffer tuning** | Medium | WebRTC's adaptive jitter buffer defaults may be too conservative for a local/Wi-Fi scenario. Can reduce with `googHighpassFilter` and `googAutoGainControl2` SDP parameters. |
| **No audio stats monitoring** | Low | Should periodically query `PeerConnection.getStats()` to monitor round-trip time, jitter, packet loss, and alert if latency exceeds thresholds. |

### Optimizations to Implement

1. **Use `MODE_IN_COMMUNICATION`** before starting WebRTC:
   ```kotlin
   audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
   audioManager.isSpeakerphoneOn = false // or true for speakerphone capture
   ```

2. **Prefer OpenSL ES** in `PeerConnectionFactory`:
   ```kotlin
   // Replace JavaAudioDeviceModule with:
   PeerConnectionFactory.builder()
       .setAudioDeviceModule(
           OpenSLESAudioDeviceModule.builder(context).createAudioDeviceModule()
       )
   ```

3. **Reduce `AudioRecord` buffer** to minimum viable size (1–2 frames).

4. **Set Opus to low-delay mode** via SDP munging:
   ```
   a=fmtp:111 minptime=10;useinbandfec=0
   ```

### How the Relay Helps

1. **Geographic proximity** — Deploy the relay server close to both phones. If Phone A is in Taiwan and Phone B is in Japan, a relay in Tokyo or Taipei minimizes the network hop. The relay doesn't touch audio data (that's P2P via WebRTC), but it routes signaling faster.
2. **TURN server co-location** — Run the TURN server on the same machine as the relay. If P2P fails and audio routes through TURN, having TURN on a low-latency server is critical.
3. **Codec negotiation** — The relay can inject SDP parameters during signaling to enforce Opus narrowband (8 kHz) when network conditions are poor, trading quality for latency.
4. **Latency monitoring** — The relay can track WebRTC stats reported by both endpoints and alert if round-trip time exceeds a threshold.

---

## 5. Carrier Restrictions

### The Problem

Carriers impose restrictions that can silently break SMS sending and call placement:

| Restriction | Carriers | Impact |
|------------|----------|--------|
| **SMS rate limiting** | Most carriers | Sending >5–10 SMS/minute triggers throttling or blocking |
| **Premium SMS blocking** | T-Mobile, some EU carriers | Programmatic SMS to short codes or international numbers blocked |
| **A2P filtering** | All major carriers | Carriers detect "application-to-person" patterns and may block messages that look automated |
| **Caller ID spoofing prevention** | Verizon, AT&T | Call placed via `TelecomManager` shows the SIM's real number; can't spoof |
| **VoLTE-only networks** | Newer carriers | Some carriers require VoLTE for calls; `TelecomManager.placeCall()` works but behavior varies |
| **International SMS blocking** | Prepaid SIMs, some carriers | Prepaid plans may block international SMS by default |
| **SIM toolkit interference** | Some carriers in Asia | Carrier's SIM toolkit app intercepts SMS intents before our app |
| **Dual SIM SMS routing** | Some carriers | Carrier app forces all SMS through SIM 1 regardless of app's selection |

### What the Host App Does Today

- **`SmsHandler.kt`**: Uses standard `SmsManager.sendTextMessage()` with a `PendingIntent` for delivery status. Falls back to default SIM if the requested slot isn't found.
- **`CallHandler.kt`**: Uses `TelecomManager.placeCall()` which goes through the normal dialer path — most carrier restrictions don't apply since it looks like a normal user-initiated call.

### What's Missing

| Gap | Severity | Notes |
|-----|----------|-------|
| **No SMS rate limiting** | High | If the Client sends 50 SMS commands in a minute, the Host will try to send all 50 and get blocked by the carrier. Need a per-SIM rate limiter (e.g., max 5/min with queue). |
| **No delivery receipt tracking** | Medium | The `PendingIntent` for SMS sent status is created but the result is never processed. Should register a `BroadcastReceiver` for `SMS_SENT` action to report actual delivery status (sent, failed, no service). |
| **No international SMS detection** | Low | Should warn if the target number is international and the SIM is prepaid. |
| **No SMS encoding awareness** | Low | Unicode messages (Chinese, emoji) use UCS-2 encoding, limiting each segment to 70 chars instead of 160. `divideMessage()` handles this, but the Client should know the effective segment count. |

### Mitigations

1. **Rate limiter in `SmsHandler`**: Token bucket per SIM slot, 5 SMS/minute default, configurable.
2. **Delivery receipt receiver**: Register for `SmsHandler.ACTION_SMS_SENT`, decode result code (`RESULT_OK`, `RESULT_ERROR_GENERIC_FAILURE`, `RESULT_ERROR_NO_SERVICE`), send as WS event.
3. **Carrier error mapping**: Translate Android error codes to human-readable messages:
   ```
   RESULT_ERROR_GENERIC_FAILURE → "SMS failed (carrier rejected)"
   RESULT_ERROR_NO_SERVICE → "No cellular service on SIM {slot}"
   RESULT_ERROR_NULL_PDU → "Invalid message format"
   RESULT_ERROR_RADIO_OFF → "Airplane mode or radio off"
   ```

### How the Relay Helps

1. **Server-side rate limiting** — The relay should enforce SMS rate limits **before** forwarding commands to the host. This protects the host's SIM from carrier throttling even if the Client app has a bug. Configurable per-user or per-device.
   ```python
   # In relay POST /sms handler:
   if rate_limiter.is_limited(device_id, "sms"):
       raise HTTPException(429, "SMS rate limit exceeded (max 5/min)")
   ```
2. **Command queuing** — When rate-limited, queue the command and send it when the window opens. Return `202 Accepted` with an ETA to the client.
3. **Carrier profile database** — The relay can maintain a database of known carrier restrictions (e.g., "CHT prepaid: no international SMS") and pre-reject impossible commands with a clear error.
4. **Message logging & audit** — Already implemented (`message_logs` table). This provides evidence if a carrier disputes automated SMS usage.
5. **Retry logic** — If the host reports `SMS_SENT` with status `error`, the relay can auto-retry once after a delay, or escalate to the Client.

---

## 6. NAT Traversal Failures

### The Problem

WebRTC requires a direct network path between the two peers (Host and Client) for audio. NAT (Network Address Translation) blocks this in many real-world scenarios:

| NAT Type | % of Networks | P2P Success | Notes |
|----------|--------------|-------------|-------|
| **Full Cone** | ~15% | High | STUN works reliably |
| **Restricted Cone** | ~30% | Medium | STUN usually works |
| **Port Restricted Cone** | ~35% | Low | STUN sometimes works |
| **Symmetric NAT** | ~20% | Fails | STUN cannot punch through; TURN required |
| **Carrier-grade NAT (CGNAT)** | Growing | Fails | Mobile carriers increasingly use CGNAT; STUN fails |
| **Corporate firewall** | Common | Fails | UDP blocked entirely; need TURN over TCP/443 |

**STUN** (Session Traversal Utilities for NAT) discovers the public IP:port but can't punch through symmetric NAT. **TURN** (Traversal Using Relays around NAT) relays all media through a server — always works but adds latency.

### What the Host App Does Today

- **`WebRtcManager.kt`**: Configures a single STUN server: `stun:stun.l.google.com:19302`
- **ICE gathering**: Uses `GATHER_CONTINUALLY` policy for ongoing candidate discovery.
- **`SignalingHandler.kt`**: Exchanges ICE candidates bidirectionally through the WebSocket.

### What's Missing (Critical)

| Gap | Severity | Notes |
|-----|----------|-------|
| **No TURN server** | Critical | Without TURN, ~20–55% of connections will fail (symmetric NAT + CGNAT + firewalls). This is the #1 reason WebRTC calls fail in production. |
| **No TURN-over-TCP** | Critical | Corporate networks block UDP entirely. Need TURN on TCP port 443 as ultimate fallback. |
| **No ICE restart** | High | If the network changes (Wi-Fi → mobile data), ICE candidates become invalid. Need to detect this and trigger ICE restart. |
| **No candidate pair monitoring** | Medium | Should log which candidate pair was selected (host/srflx/relay) and report to relay for diagnostics. |
| **Single STUN server** | Low | Google's STUN server is reliable but adds a dependency. Should have 2–3 STUN servers for redundancy. |

### TURN Server Architecture

```
                     ┌──────────────────┐
Phone A (Host)       │   Relay Server   │       Phone B (Client)
     │               │                  │              │
     │◄─── STUN ────►│  STUN: 3478/UDP  │◄─── STUN ──►│
     │               │  TURN: 3478/UDP  │              │
     │◄─── TURN ────►│  TURN: 443/TCP   │◄─── TURN ──►│
     │               │                  │              │
     │    (if P2P     │  (media relay    │   (if P2P    │
     │     fails)     │   fallback)      │    fails)    │
     └───────────────►└──────────────────┘◄─────────────┘
                         coturn server
```

### How the Relay Helps (Essential)

The relay server is **the** solution to NAT traversal. Specifically:

1. **Run a TURN server** — Deploy `coturn` alongside the relay server. This is not optional for production — it's the only way to guarantee connectivity.
   ```bash
   # coturn config (/etc/turnserver.conf)
   listening-port=3478
   tls-listening-port=443
   realm=simbridge.example.com
   use-auth-secret
   static-auth-secret=<shared-secret-with-relay>
   ```

2. **Dynamic TURN credentials** — The relay generates short-lived TURN credentials (HMAC-based, 1-hour expiry) and sends them to both Host and Client during WebRTC setup:
   ```json
   {
     "type": "webrtc",
     "action": "config",
     "ice_servers": [
       {"urls": "stun:relay.example.com:3478"},
       {
         "urls": ["turn:relay.example.com:3478?transport=udp",
                  "turn:relay.example.com:443?transport=tcp"],
         "username": "1708300000:device_1",
         "credential": "hmac-sha1-derived-password"
       }
     ]
   }
   ```

3. **ICE server injection** — The host and client should **not** hardcode ICE servers. Instead, the relay provides them during signaling. This allows:
   - Rotating TURN credentials without app updates
   - Adding geographic TURN servers based on device location
   - Falling back to different TURN providers if the primary is down

4. **Connection quality monitoring** — The relay can require both endpoints to report their selected ICE candidate type:
   - `host` = direct LAN connection (best)
   - `srflx` = STUN-punched connection (good)
   - `relay` = TURN-relayed connection (works but higher latency)

   If both endpoints report `relay`, the relay knows audio latency will be high and can warn the user.

5. **TURN-over-TCP on port 443** — The ultimate fallback. Even the most restrictive corporate firewalls allow TCP 443 (HTTPS port). Running TURN on 443/TCP ensures connectivity in every network environment, at the cost of higher latency due to TCP head-of-line blocking.

6. **Multiple STUN/TURN servers** — For global deployments, run TURN servers in multiple regions. The relay picks the closest TURN server to each device based on IP geolocation:
   ```
   Host in Taiwan  → TURN server in Tokyo
   Client in US    → TURN server in Oregon
   ```

---

## 7. iOS Platform Restrictions

Unlike the six Android challenges above — which are hard but solvable — several iOS restrictions are **platform-enforced and have no workaround** on App Store builds. These fundamentally limit what the iOS Host App can do and, to a lesser extent, what the iOS Client App can do in the background.

### 7a. SMS — Cannot Send or Receive Programmatically

#### The Problem

iOS has no equivalent of Android's `SmsManager` or `BroadcastReceiver` for SMS:

| Operation | Android | iOS | Gap |
|-----------|---------|-----|-----|
| **Send SMS automatically** | `SmsManager.sendTextMessage()` — fully background, no UI | `MFMessageComposeViewController` — must present UI, user must tap Send | Cannot relay SMS commands without user interaction |
| **Send SMS in background** | Works via foreground service | **Not possible** — `MFMessageComposeViewController` requires the app to be in the foreground with a visible view controller | Host must be open and user must confirm every message |
| **Intercept incoming SMS** | `BroadcastReceiver` with `SMS_RECEIVED_ACTION` — automatic, real-time | **No public API** — `MessageFilterExtension` only classifies SMS for the system, cannot read content or forward it | Incoming SMS relay is impossible on App Store builds |
| **Read SMS history** | `content://sms` content provider | **Not possible** — no API to read the SMS database | Cannot sync message history |

#### What the iOS Host App Does Today

- **`SmsHandler.swift`**: Presents `MFMessageComposeViewController` for user-initiated sends. This requires the app to be in the foreground and the user to manually tap "Send" in the system compose sheet.
- **`SmsReceiver.swift`**: Documented as non-functional. No incoming SMS interception.

#### Why This Cannot Be Fixed

Apple enforces SMS sandboxing at the OS level. There is no entitlement, MDM profile, or enterprise certificate that grants programmatic SMS access. The only known workarounds are:

1. **Jailbroken devices** — Private `CTMessageCenter` API provides full SMS access. Not viable for general users.
2. **Shortcuts/Automation** — iOS Shortcuts can send messages via the Messages app, but requires user confirmation and cannot be triggered by a WebSocket event.
3. **iMessage-based relay** — A Mac running the Messages app can programmatically send iMessages (not SMS) via AppleScript. This is a different product entirely.

#### Impact

**The iOS Host App cannot serve as an SMS relay.** This is the most significant iOS limitation. Users who need SMS bridging must use an Android Host.

#### How the Relay Helps

The relay can't fix an OS-level restriction, but it can:

1. **Detect the host platform** — If the host device reports `platform: "ios"` during registration, the relay can pre-reject SMS commands with a clear error: `"SMS relay not supported on iOS hosts"`.
2. **Suggest Android Host** — The client UI can show a warning when paired with an iOS host: "SMS features unavailable — pair with an Android host for full functionality."

---

### 7b. Background Execution — App Suspended Within Seconds

#### The Problem

iOS aggressively suspends apps that are not in the foreground. Unlike Android's foreground service (which can run indefinitely with a notification), iOS provides no equivalent for third-party apps:

| Mechanism | Max Background Time | Reliability | Notes |
|-----------|-------------------|-------------|-------|
| **`beginBackgroundTask`** | ~30 seconds (iOS 13+) | Reliable but short | For finishing in-progress work only |
| **`BGAppRefreshTask`** | ~30 seconds, scheduled by OS | Unreliable timing | OS decides when to wake the app; can be hours between wakes |
| **`BGProcessingTask`** | Several minutes | Unreliable timing | Only runs when device is charging and on Wi-Fi |
| **VoIP push (`PushKit`)** | Immediate wake, ~30 seconds | Reliable | Apple **rejects** apps that use VoIP push without actual VoIP calls. App review checks for CallKit integration. |
| **APNs push notification** | Wakes app for ~30 seconds | Reliable | Requires server-side APNs integration. Cannot maintain a WebSocket. |
| **Location updates** | Continuous if `Always` permission granted | Reliable | Apple rejects apps that abuse location for keep-alive. Requires genuine location need. |
| **Audio playback** | Continuous while playing | Reliable | Playing silence is rejected by App Review. |

#### What the iOS Apps Do Today

- **Host App**: Uses `BGTaskScheduler` for background refresh. The WebSocket disconnects within ~30 seconds of backgrounding. The app goes "offline" from the relay's perspective.
- **Client App**: Same limitation. Misses real-time events (incoming SMS, call state changes) when backgrounded.

#### What's Missing

| Gap | Severity | Mitigation |
|-----|----------|------------|
| **No APNs push integration** | Critical | When the relay receives a command for an offline iOS host, it should send an APNs push to wake the app. The app has ~30 seconds to reconnect the WebSocket, process the command, and respond. |
| **No VoIP push for calls** | High | For incoming call relay, VoIP push via `PushKit` is the correct mechanism. It wakes the app instantly and provides enough time to show a CallKit incoming call UI. This is a legitimate VoIP use case that Apple approves. |
| **No `URLSession` background transfer** | Medium | For non-urgent commands, `URLSession` background configurations can download/upload data while the app is suspended. Could be used for queued command delivery. |
| **No connectivity state tracking** | Medium | The apps don't inform the relay whether they're about to go to background. A "going to sleep" message would let the relay queue commands instead of trying (and failing) to deliver via dead WebSocket. |

#### How the Relay Helps (Essential for iOS)

The relay is the **only** solution for iOS background delivery:

1. **APNs push for commands** — When a command arrives and the iOS host/client WebSocket is dead, send an APNs push notification. The app wakes, reconnects the WebSocket, and processes queued commands. Requires:
   - APNs certificate or key configured on the relay server
   - Device push token stored during registration
   - `application(_:didReceiveRemoteNotification:fetchCompletionHandler:)` in the app

2. **VoIP push for calls** — Use `PushKit` to wake the iOS host for incoming call relay. This is the only way to show an incoming call UI when the app is backgrounded. Apple explicitly approves this for VoIP apps.

3. **Command queuing** — Already partially implemented (`PendingCommand` table). For iOS devices, queuing is not optional — it's the primary delivery mechanism since the WebSocket will be dead most of the time the app is backgrounded.

4. **Platform-aware delivery** — The relay should track each device's platform (`android`/`ios`) and use different delivery strategies:
   - Android: Deliver via WebSocket (usually connected) with FCM fallback
   - iOS: Deliver via WebSocket if connected, otherwise queue + APNs push

---

### 7c. Call Audio — Cannot Capture on iOS

#### The Problem

iOS completely sandboxes call audio. There is no API — public or private — to capture the audio of a cellular phone call on a non-jailbroken device:

| Approach | Android | iOS |
|----------|---------|-----|
| **Capture call audio directly** | `VOICE_CALL` source (system apps) or `VOICE_COMMUNICATION` (mic only) | **Not possible** — no audio source for calls |
| **Speakerphone + mic capture** | Works (poor quality) | `AVAudioEngine` cannot tap call audio even on speaker |
| **ConnectionService routing** | Can intercept audio for self-managed calls | CallKit provides call UI but **no audio access** to the cellular call |
| **WebRTC during call** | Can run alongside a cellular call | `AVAudioSession` conflict — iOS switches audio routes when a cellular call starts, disrupting any existing WebRTC session |

#### What the iOS Host App Does Today

- **`CallManager.swift`**: Uses CallKit `CXCallController` to place and end calls. Works correctly for call control.
- **`AudioBridge.swift`**: Uses `AVAudioEngine` for mic capture. Works for WebRTC audio **when no cellular call is active**. During a cellular call, iOS takes exclusive control of the audio session.

#### Why This Cannot Be Fixed

Apple does not expose call audio to third-party apps for privacy reasons. This is enforced at the kernel level. There is no entitlement, MDM profile, or enterprise workaround.

#### Impact

**The iOS Host App cannot bridge call audio.** It can place/end calls (useful as a remote dialer), but the Client cannot hear the conversation. Users who need call audio bridging must use an Android Host.

#### Partial Workaround: WebRTC-Only Calls

The iOS Host *can* make WebRTC-only calls (app-to-app, not cellular). If both parties use SimBridge, the call goes entirely through WebRTC without touching the cellular network, and audio works normally. This is not a SIM-bridge use case, but it's functional.

---

### 7d. SIM Info — Limited Carrier Data

#### The Problem

iOS provides minimal SIM card information compared to Android:

| Data | Android | iOS |
|------|---------|-----|
| **Number of SIM slots** | `SubscriptionManager.activeSubscriptionInfoList.size` | Not exposed. `CTTelephonyNetworkInfo().serviceSubscriberCellularProviders` returns carriers but no slot numbers. |
| **Phone number** | `SubscriptionInfo.number` (often empty) or `getPhoneNumber()` on Android 13+ | **Not available**. No API returns the phone number. |
| **Carrier name** | `SubscriptionInfo.carrierName` | `CTCarrier.carrierName` — works |
| **MCC/MNC** | `SubscriptionInfo.mcc` / `.mnc` | `CTCarrier.mobileCountryCode` / `.mobileNetworkCode` — works |
| **SIM slot index** | `SubscriptionInfo.simSlotIndex` (0-indexed) | Not exposed |
| **Subscription ID** | `SubscriptionInfo.subscriptionId` (stable) | Not exposed |
| **eSIM detection** | `SubscriptionInfo.isEmbedded` | Not exposed |

#### What the iOS Host App Does Today

- **`SimInfoProvider.swift`**: Uses `CTTelephonyNetworkInfo().serviceSubscriberCellularProviders` to list carriers. Returns carrier name and MCC/MNC. No slot numbers or phone numbers.

#### Impact

Low severity. The Client sees carrier names (e.g., "CHT", "T-Mobile") instead of phone numbers. SIM selection uses carrier name as the identifier. This is adequate for most users but less precise than Android's slot-based selection.

---

### 7e. iOS Client App — Background Event Delivery

#### The Problem

The iOS Client App works fully in the foreground but cannot maintain a WebSocket connection in the background. This means:

1. **Incoming SMS notifications are delayed** — The Client only learns about new SMS when the app is reopened or an APNs push wakes it.
2. **Call state changes are missed** — If a call ends while the Client is backgrounded, the UI is stale when the user returns.
3. **Connection status shows "Disconnected"** — The Client correctly shows the relay connection as offline when backgrounded, which may confuse users.

#### What the iOS Client App Does Today

- WebSocket connects on app foreground, disconnects ~30 seconds after backgrounding.
- No APNs push integration for event delivery.
- `BGTaskScheduler` registered but provides infrequent background refresh.

#### What's Missing

| Gap | Severity | Mitigation |
|-----|----------|------------|
| **No APNs push for events** | High | Relay should send push notifications for incoming SMS and call events when the iOS client WebSocket is dead. The notification content can include the event payload. |
| **No background WebSocket reconnect** | Medium | On `BGAppRefreshTask` wake, reconnect the WebSocket, drain queued events, and disconnect. |
| **No Notification Service Extension** | Low | An `UNNotificationServiceExtension` can modify push content before display — e.g., decrypt an encrypted SMS body or fetch additional data from the relay. |

---

### iOS Summary Matrix

| Restriction | Host App | Client App | Severity | Workaround |
|-------------|----------|------------|----------|------------|
| **No programmatic SMS** | Cannot send or receive SMS automatically | N/A (Client doesn't send SMS directly) | **Critical** | None on App Store. Use Android Host. |
| **No incoming SMS intercept** | Cannot forward incoming SMS to Client | Misses incoming SMS events when backgrounded | **Critical** | None on App Store. APNs push helps Client. |
| **App suspended in background** | WebSocket dies; host goes offline | WebSocket dies; misses events | **Critical** | APNs push + VoIP push + command queuing |
| **No call audio capture** | Cannot bridge call audio | Cannot hear remote call | **High** | None. WebRTC-only calls work. |
| **Limited SIM info** | Carrier name only, no phone number or slot | Displays carrier names instead of numbers | **Low** | Acceptable UX with carrier-name labels |

### Recommended Configuration

| Setup | SMS Relay | Call Relay | Call Audio | Background | Rating |
|-------|-----------|------------|------------|------------|--------|
| **Android Host + Android Client** | Full | Full | Partial (speakerphone) | Reliable (foreground service) | Best |
| **Android Host + iOS Client** | Full | Full | Partial | Client limited when backgrounded | Good |
| **iOS Host + Android Client** | **Broken** | Control only | **No audio** | Host limited when backgrounded | Poor |
| **iOS Host + iOS Client** | **Broken** | Control only | **No audio** | Both limited when backgrounded | Not recommended |

### Top 3 Actions for iOS Production Readiness

1. **Implement APNs push delivery** in the relay — This is the single most impactful improvement for iOS. Without push, iOS devices are offline whenever backgrounded, which is most of the time.
2. **Add platform detection to the relay** — Store `platform: "ios" | "android"` on device registration. Use platform-aware delivery (WebSocket for Android, push + queue for iOS). Pre-reject impossible commands (SMS to iOS host) with clear errors.
3. **Implement VoIP push for call relay** — Use `PushKit` to wake the iOS host for incoming call notifications. This is the only Apple-approved mechanism for real-time call delivery to a backgrounded app.

---

## Summary Matrix

### Android Challenges

| Challenge | Host App Status | Relay Status | Priority |
|-----------|----------------|--------------|----------|
| **Background restrictions** | Foreground service works; missing WakeLock and connectivity listener | No FCM wakeup, no command buffering | High |
| **Call audio capture** | AudioBridge exists but is disconnected; only captures mic, not far-end | Passive (audio is P2P) | Critical |
| **Dual SIM fragmentation** | Basic slot→subscription mapping; fragile PhoneAccount selection | No SIM identity tracking | Medium |
| **Voice latency** | WebRTC with Opus + HW AEC; missing audio mode config and OpenSL ES | No TURN co-location, no codec negotiation | Medium |
| **Carrier restrictions** | No rate limiting, no delivery receipts | No server-side rate limiting | High |
| **NAT traversal** | STUN only (Google), no TURN | No TURN server, no credential provisioning | Critical |

### iOS Challenges

| Challenge | Host App | Client App | Relay Status | Priority |
|-----------|----------|------------|--------------|----------|
| **No programmatic SMS** | Cannot send/receive | N/A | No platform detection, no pre-rejection | Critical |
| **App suspended in background** | WebSocket dies ~30s after backgrounding | Same | No APNs push, no VoIP push | Critical |
| **No call audio capture** | Cannot bridge call audio | Cannot hear remote call | Passive | High |
| **Limited SIM info** | Carrier name only | Displays carrier names | No platform-aware SIM handling | Low |
| **Background event delivery** | N/A | Misses events when backgrounded | No push notification for events | High |

### Top 5 Actions for Production Readiness

1. **Deploy a TURN server** alongside the relay and provision dynamic credentials via signaling. Without this, ~30–50% of WebRTC calls will fail.
2. **Implement APNs push delivery** in the relay for iOS devices. Without push, iOS apps are offline whenever backgrounded.
3. **Fix the Android audio bridge** — Either wire `AudioBridge` into the WebRTC track, or (more practically) use `ConnectionService` audio routing with speakerphone fallback.
4. **Add platform detection and platform-aware delivery** — Store device platform on registration. Use WebSocket + FCM for Android, push + queue for iOS. Pre-reject impossible commands (SMS to iOS host) with clear errors.
5. **Add server-side rate limiting and command buffering** in the relay to protect against carrier throttling and host downtime.
