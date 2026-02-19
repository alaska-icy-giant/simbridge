# SimBridge — Known Issues

Comprehensive audit of the relay server, host app, and client app. Issues are grouped by severity, then by component.

---

## Critical

### R-01: Default JWT secret allows full authentication bypass
**Component**: Relay server — `auth.py:5`
```python
JWT_SECRET = os.getenv("JWT_SECRET", "change-me")
```
If `JWT_SECRET` is not set in the environment, the hardcoded `"change-me"` default is used. Any attacker can forge valid JWT tokens for any user.

**Impact**: Complete auth bypass. Attacker can impersonate any user, pair devices, send SMS/call commands.

**Fix**: Remove the default; crash on startup if not configured.

---

### R-02: Race condition in WebSocket connection registry
**Component**: Relay server — `main.py:33`
```python
connections: dict[int, WebSocket] = {}
```
Global mutable dict accessed from multiple async tasks without locking. If the same device connects twice simultaneously, the first connection's reference is overwritten. Messages route to the wrong socket, and cleanup may orphan entries.

**Impact**: Messages delivered to wrong device; zombie connections marked online after disconnect.

**Fix**: Add `asyncio.Lock` around all `connections` dict operations. Reject duplicate device connections.

---

### R-03: Cross-user device pairing — missing user_id check
**Component**: Relay server — `main.py:189-220` (`/pair/confirm`)

The pairing code stores `user_id`, but `confirm_pairing()` does not verify that the confirming user matches the code's `user_id`. User B can pair their client to User A's host if they obtain the 6-digit code.

**Impact**: Unauthorized cross-user device access. User B can send SMS and make calls through User A's phone.

**Fix**: Add `if pc.user_id != user_id: raise HTTPException(403)` before creating the pairing.

---

### R-04: Race condition in pairing confirmation
**Component**: Relay server — `main.py:189-220`

Between checking `PairingCode.used == False` and setting `used = True`, two concurrent requests can both read the code as unused, creating duplicate pairings.

**Impact**: Same pairing code used multiple times; duplicate pairing records.

**Fix**: Use `SELECT FOR UPDATE` or add a unique constraint on `(host_device_id, client_device_id)` in the Pairing table.

---

### H-01: WebSocket URL missing device ID path segment
**Component**: Host app — `WebSocketManager.kt:71-75`
```kotlin
val wsUrl = serverUrl
    .replace("https://", "wss://")
    .replace("http://", "ws://")
    .trimEnd('/') + "/ws?token=$token"
```
The relay expects `/ws/host/{device_id}?token=JWT`, but the host connects to `/ws?token=JWT`. The relay cannot identify which host device this is — the connection will be rejected or misrouted.

**Impact**: Host cannot connect to relay. All functionality broken.

**Fix**: Change to `+ "/ws/host/${prefs.deviceId}?token=$token"`.

---

### H-02: AudioBridge is disconnected from WebRTC pipeline
**Component**: Host app — `AudioBridge.kt:65-73`

The capture thread reads audio data into a buffer and discards it. The comment claims WebRTC's `JavaAudioDeviceModule` automatically picks up `VOICE_COMMUNICATION` audio, but `AudioBridge` is never called from anywhere — `startCapture()` is never invoked.

Meanwhile, `WebRtcManager` creates its own audio track via `JavaAudioDeviceModule`, which captures mic audio independently. The two systems are not connected.

**Impact**: Call audio bridging does not work. Calls are silent.

**Fix**: Either remove `AudioBridge` entirely (rely on `JavaAudioDeviceModule` for mic capture) or integrate it with the WebRTC audio track. The former is simpler and sufficient for mic-only capture.

---

## High

### R-05: Weak pairing code RNG
**Component**: Relay server — `main.py:160`
```python
def _generate_code() -> str:
    return "".join(random.choices(string.digits, k=6))
```
Uses Python's `random` module (not cryptographically secure). Only 1M possible codes, predictable seed.

**Impact**: Brute-force pairing code in seconds. Combined with R-03, attackers can pair to any host.

**Fix**: Use `secrets.choice()`. Add rate limiting on `/pair/confirm` (e.g., 5 attempts/minute).

---

### R-06: No rate limiting on auth endpoints
**Component**: Relay server — `main.py:95-108`

No throttling on `/auth/login` or `/auth/register`. Enables credential stuffing, brute-force attacks, and account enumeration.

**Fix**: Add `slowapi` rate limiter: 5 login attempts/minute per IP.

---

### R-07: Incomplete device ownership check in `_get_paired_host`
**Component**: Relay server — `main.py:262-268`

Verifies pairing exists but does not check that both devices belong to the requesting user. If a cross-user pairing exists (via R-03), commands are allowed.

**Fix**: Add `Device.user_id == user_id` filter when querying the host device.

---

### R-08: No message history retention limit
**Component**: Relay server — `models.py`, `main.py:407-430`

`MessageLog` table grows indefinitely. No cleanup, no TTL, no row limit.

**Impact**: Database bloat, degraded query performance over months.

**Fix**: Add periodic cleanup (delete rows older than 90 days) or use a trigger.

---

### H-03: Thread-unsafe callbacks in BridgeService
**Component**: Host app — `BridgeService.kt:50-51`
```kotlin
var onStatusChange: ((ConnectionStatus) -> Unit)? = null
var onLogEntry: ((LogEntry) -> Unit)? = null
```
Callbacks are set from the main thread (UI) and invoked from the WebSocket background thread. No synchronization. Setting the callback to `null` in `onDispose` races with the background thread calling `?.invoke()`.

**Impact**: Silent state update failures; potential `NullPointerException` on race.

**Fix**: Use a synchronized listener list, or post callbacks to the main thread via `Handler(Looper.getMainLooper())`.

---

### H-04: `logs.toList()` not synchronized
**Component**: Host app — `BridgeService.kt:54`
```kotlin
val logs: List<LogEntry> get() = _logs.toList()
```
`addLog()` uses `synchronized(_logs)`, but the getter does not. Concurrent iteration and modification causes `ConcurrentModificationException`.

**Fix**: Wrap in `synchronized(_logs) { _logs.toList() }`.

---

### H-05: PhoneAccount-to-SIM mapping uses wrong index
**Component**: Host app — `CallHandler.kt:120`
```kotlin
return accounts.getOrNull(simSlot - 1)
```
Assumes `callCapablePhoneAccounts` list is ordered by SIM slot. On Samsung and Xiaomi devices, this is often wrong.

**Impact**: Calls placed from wrong SIM. On some devices, call fails entirely.

**Fix**: Match `PhoneAccountHandle.id` against `subscriptionId.toString()` instead of using index.

---

### H-06: ICE candidate sdpMLineIndex=0 treated as null
**Component**: Host app — `SignalingHandler.kt:79`
```kotlin
val sdpMLineIndex = message.sdpMLineIndex ?: return
```
In Kotlin, `Int? = 0` is not null, so `?: return` does NOT trigger for 0. **Correction**: This is actually fine in Kotlin — `0 ?: return` evaluates to `0`, not the `return` branch. The `?:` operator only triggers on null, not on 0.

**Status**: Not a bug. Removed from issue list.

---

### H-07: WebRTC setLocalDescription errors silently ignored
**Component**: Host app — `WebRtcManager.kt:124-127`
```kotlin
peerConnection?.setLocalDescription(NoOpSdpObserver(), sdp)
callback(sdp)
```
`NoOpSdpObserver` swallows all errors. If `setLocalDescription` fails, the SDP is still sent to the peer, causing a mysterious connection failure.

**Fix**: Use a real observer that logs errors and calls `callback(null)` on failure.

---

### C-01: PeerConnection resource leak on repeated calls
**Component**: Client app — `ClientWebRtcManager.kt:45-75`
```kotlin
fun createPeerConnection() {
    // No check if peerConnection already exists
    peerConnection = f.createPeerConnection(config, observer)
    addLocalAudioTrack()
}
```
If `handleOffer()` and `initiateAudioSession()` are both called, `createPeerConnection()` runs twice. The first connection and audio track are leaked.

**Fix**: Call `closePeerConnection()` before creating a new one, or guard with a null check.

---

### C-02: Silent SDP creation failures
**Component**: Client app — `ClientSignalingHandler.kt:37-50`
```kotlin
webRtcManager.createOffer { offer ->
    if (offer != null) { sendMessage(...) }
    // else: no error notification — call hangs silently
}
```
Same issue in `handleOffer()` — if answer creation fails, no error is sent to the Host.

**Impact**: Call appears connected but has no audio. No user feedback.

**Fix**: Send a `{"type":"webrtc","action":"error","reason":"..."}` message on failure.

---

## Medium

### R-09: No unique constraint on Pairing table
**Component**: Relay server — `models.py:60-75`

Same `(host_device_id, client_device_id)` pair can be inserted multiple times.

**Fix**: Add `UniqueConstraint('host_device_id', 'client_device_id')`.

---

### R-10: Device online status stale after server restart
**Component**: Relay server — `main.py:33, 363-374`

`Device.is_online = True` is persisted to SQLite, but `connections` dict is in-memory. After server restart, all devices show online in the DB but the connections dict is empty.

**Fix**: Calculate online status dynamically from `connections` dict; don't persist it.

---

### R-11: No DB exception handling in WebSocket message relay
**Component**: Relay server — `main.py:341-343`
```python
log = MessageLog(...)
db.add(log)
db.commit()  # Can raise
```
If `db.commit()` fails (constraint violation, DB locked), the exception crashes the WebSocket loop.

**Fix**: Wrap in try/except with `db.rollback()`.

---

### R-12: Unlimited pairing code generation
**Component**: Relay server — `main.py:163-170`

Multiple active pairing codes can exist for the same host simultaneously. No cleanup of expired codes.

**Fix**: Limit to one active code per host. Expire previous codes on new generation.

---

### R-13: No message type validation
**Component**: Relay server — `main.py:334`

Unknown message types (e.g., `{"type":"malicious"}`) are silently relayed to the target device without validation.

**Fix**: Whitelist allowed message types: `command`, `event`, `webrtc`, `ping`.

---

### R-14: No input validation on SMS/call commands
**Component**: Relay server — `main.py:290-305`

No validation of `sim` (should be 1 or 2), `to` (phone number format), or `body` (length limit).

**Fix**: Add Pydantic validators with field constraints.

---

### R-15: No command offline queueing
**Component**: Relay server — `main.py:276-283`

Commands fail immediately with HTTP 503 when host is offline. No retry or queue option.

**Fix**: Add optional `PendingCommand` table; deliver queued commands when host reconnects.

---

### R-16: SQLAlchemy session shared across WebSocket lifetime
**Component**: Relay server — `main.py:365-379`

A single `SessionLocal()` instance is used for the entire WebSocket connection lifetime. Long-lived sessions can hold stale data and conflict with concurrent operations.

**Fix**: Create a fresh session per message or use scoped sessions.

---

### H-08: Service binding with flags=0 in onStart
**Component**: Host app — `MainActivity.kt:69-74`
```kotlin
bindService(intent, serviceConnection, 0)  // No BIND_AUTO_CREATE
```
If the service isn't already running, `onServiceConnected()` is never called. The UI shows `service = null` even though the user expects it running.

**Fix**: Use `Context.BIND_AUTO_CREATE`, or only bind if the service was explicitly started.

---

### H-09: Unsafe binder cast in ServiceConnection
**Component**: Host app — `MainActivity.kt:31-32`
```kotlin
bridgeService = (binder as BridgeService.LocalBinder).service
```
If `binder` is null, this crashes. Use `as?` safe cast.

---

### H-10: SmsHandler silently falls back to default SIM
**Component**: Host app — `SmsHandler.kt:68-79`

If the requested SIM slot doesn't exist, falls back to default SIM without notifying the client. SMS may be sent from the wrong number.

**Fix**: Return an error event if the requested SIM slot is not found.

---

### H-11: Missing audio focus and audio mode configuration
**Component**: Host app — `AudioBridge.kt`

Does not call `AudioManager.setMode(MODE_IN_COMMUNICATION)` or request audio focus before capturing. Audio routing may be incorrect, and other apps' audio may interfere.

**Fix**: Request `AUDIOFOCUS_GAIN_TRANSIENT` and set mode before capture.

---

### H-12: WebSocket reconnect future race condition
**Component**: Host app — `WebSocketManager.kt:39, 84-88`

`reconnectFuture` is a non-volatile, non-synchronized `ScheduledFuture`. If `disconnect()` cancels it on one thread while `scheduleReconnect()` assigns it on another, the cancel may miss.

**Fix**: Synchronize access to `reconnectFuture`.

---

### H-13: DashboardScreen UI state out of sync with service
**Component**: Host app — `DashboardScreen.kt:82-90`

Button updates local `isServiceRunning` immediately on click, but actual service start/stop is async. Double-tapping causes UI desync.

**Fix**: Derive button state from `service?.connectionStatus` instead of local variable.

---

### C-03: Non-atomic notification ID in NotificationHelper
**Component**: Client app — `NotificationHelper.kt:18`
```kotlin
private var nextId = 1000
fun notifyIncomingSms(...) { manager.notify(nextId++, ...) }
```
`nextId++` is not atomic. Concurrent SMS notifications can get the same ID; one overwrites the other.

**Fix**: Use `AtomicInteger`.

---

### C-04: No phone number validation in DialerScreen
**Component**: Client app — `DialerScreen.kt:69-72`

Accepts any string as a phone number, including letters, spaces, and special characters.

**Fix**: Validate with a regex (e.g., `^\+?[\d\s\-()]+$`) and show an error for invalid input.

---

### C-05: Thread-unsafe state updates in ClientService
**Component**: Client app — `ClientService.kt:49-54`
```kotlin
var hostSims: List<SimInfo> = emptyList()
var callState: CallState = CallState.IDLE
var callNumber: String? = null
```
Updated from the WebSocket background thread, read from the main thread (Compose). No synchronization.

**Fix**: Use `@Volatile` or `StateFlow` for thread-safe state publication.

---

## Low

### R-17: No pagination on `/history` endpoint
**Component**: Relay server — `main.py:407-430`

Only supports `limit` (max 200), no offset or cursor. Clients cannot page through older history.

---

### R-18: No device offline notification to paired client
**Component**: Relay server

When a host disconnects, the paired client receives no notification. Client discovers the host is offline only when the next command fails.

**Fix**: Send `{"type":"event","event":"device_offline","device_id":N}` to paired clients on host disconnect.

---

### R-19: No server-side heartbeat
**Component**: Relay server — `main.py:331-333`

Ping/pong exists but is client-initiated only. If a client dies without closing the socket, the server never detects it. The device stays marked online indefinitely until the next message attempt fails.

**Fix**: Send server-initiated pings every 30s. Close connections that don't respond within 60s.

---

### H-14: BridgeConnectionService has no integration with BridgeService
**Component**: Host app — `BridgeConnectionService.kt`

The `ConnectionService` creates `BridgeConnection` instances but has no reference to `BridgeService` or `WebSocketManager`. Call state changes in `BridgeConnection` are not forwarded as WebSocket events.

**Impact**: Call audio routing through `ConnectionService` is scaffolded but not functional.

---

### H-15: ApiClient has no retry logic
**Component**: Host app — `ApiClient.kt`

Single-attempt HTTP requests. Transient failures (network blip, server restart) cause immediate login failure.

---

### C-06: Event listener lifecycle leak in SmsScreen/DialerScreen
**Component**: Client app — `SmsScreen.kt:31-40`

When the Compose `service` key changes, `DisposableEffect` cleans up the old listener but the new listener may reference a stale service object.

---

## Summary

| Severity | Relay | Host | Client | Total |
|----------|-------|------|--------|-------|
| Critical | 4 | 2 | 0 | **6** |
| High | 4 | 5 | 2 | **11** |
| Medium | 8 | 6 | 3 | **17** |
| Low | 3 | 2 | 1 | **6** |
| **Total** | **19** | **15** | **6** | **40** |

### Top 5 Actions

1. **R-01 + R-05 + R-06**: Harden auth — remove default JWT secret, use `secrets` for pairing codes, add rate limiting
2. **R-03 + R-07**: Fix cross-user pairing — add `user_id` check in `/pair/confirm` and `_get_paired_host`
3. **H-01**: Fix WebSocket URL — add `/ws/host/{device_id}` path segment (app is non-functional without this)
4. **H-02**: Remove `AudioBridge` or integrate it — call audio doesn't work
5. **R-02 + R-04**: Add locking to `connections` dict and pairing confirmation to prevent race conditions
