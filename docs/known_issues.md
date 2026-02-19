# SimBridge — Known Issues

Comprehensive audit of the relay server, host app, and client app. All 40 issues have been resolved.

**Status**: All fixed as of `9dbc89b` (critical/high) and `9f12585` (medium/low).

---

## Critical — 6 fixed

### R-01: Default JWT secret allows full authentication bypass ✅
**Component**: Relay server — `auth.py`

Default `"change-me"` JWT secret allowed token forgery.

**Fixed in** `9dbc89b`: Removed default; app crashes on startup if `JWT_SECRET` env var is not set.

---

### R-02: Race condition in WebSocket connection registry ✅
**Component**: Relay server — `main.py`

Global `connections` dict accessed without locking; duplicate connections overwrite each other.

**Fixed in** `9dbc89b`: Added `asyncio.Lock` (`_conn_lock`) around all dict operations. Old duplicate connections closed before replacement. Cleanup only removes if `connections[id] is ws`.

---

### R-03: Cross-user device pairing — missing user_id check ✅
**Component**: Relay server — `main.py` (`/pair/confirm`)

Pairing code's `user_id` was not checked, allowing User B to pair with User A's host.

**Fixed in** `9dbc89b`: Added `if pc.user_id != user_id: raise HTTPException(403)`.

---

### R-04: Race condition in pairing confirmation ✅
**Component**: Relay server — `main.py`, `models.py`

Concurrent requests could both use the same pairing code, creating duplicate pairings.

**Fixed in** `9dbc89b`: Added `UniqueConstraint("host_device_id", "client_device_id")` on Pairing table. Old unused codes expired on new code generation.

---

### H-01: WebSocket URL missing device ID path segment ✅
**Component**: Host app — `WebSocketManager.kt`

Connected to `/ws?token=` instead of `/ws/host/{device_id}?token=`. Relay couldn't identify the device.

**Fixed in** `9dbc89b`: URL now includes `/ws/host/${prefs.deviceId}?token=$token`.

---

### H-02: AudioBridge is disconnected from WebRTC pipeline ✅
**Component**: Host app — `AudioBridge.kt`

Capture thread read audio and discarded it. Never called from anywhere.

**Fixed in** `9dbc89b`: Deleted `AudioBridge.kt`. `JavaAudioDeviceModule` in `WebRtcManager` handles mic capture.

---

## High — 11 fixed

### R-05: Weak pairing code RNG ✅
**Component**: Relay server — `main.py`

Used `random.choices()` (not cryptographically secure).

**Fixed in** `9dbc89b`: Switched to `secrets.choice()`. Added rate limiting on `/pair/confirm`.

---

### R-06: No rate limiting on auth endpoints ✅
**Component**: Relay server — `main.py`

No throttling on `/auth/login` or `/auth/register`.

**Fixed in** `9dbc89b`: Added `_check_rate_limit()` — 5 attempts per 60 seconds per username. Applied to login and pairing confirmation.

---

### R-07: Incomplete device ownership check in `_get_paired_host` ✅
**Component**: Relay server — `main.py`

Didn't verify the host device belongs to the requesting user.

**Fixed in** `9dbc89b`: Added `Device.user_id == user_id` filter in `_get_paired_host`.

---

### R-08: No message history retention limit ✅
**Component**: Relay server — `main.py`

`MessageLog` table grows indefinitely with no cleanup.

**Fixed in** `9dbc89b`: Added startup cleanup deleting logs older than 90 days (configurable via `LOG_RETENTION_DAYS`).

---

### H-03: Thread-unsafe callbacks in BridgeService ✅
**Component**: Host app — `BridgeService.kt`

Callbacks invoked from WebSocket thread, set from main thread with no synchronization.

**Fixed in** `9dbc89b`: All callbacks posted to main thread via `Handler(Looper.getMainLooper())`.

---

### H-04: `logs.toList()` not synchronized ✅
**Component**: Host app — `BridgeService.kt`

Getter called without synchronization while background thread modifies the list.

**Fixed in** `9dbc89b`: Wrapped in `synchronized(_logs) { _logs.toList() }`.

---

### H-05: PhoneAccount-to-SIM mapping uses wrong index ✅
**Component**: Host app — `CallHandler.kt`

Used `accounts.getOrNull(simSlot - 1)` which doesn't match SIM slots on Samsung/Xiaomi.

**Fixed in** `9dbc89b`: Now matches `PhoneAccountHandle.id` against `subscriptionId.toString()` with index-based fallback.

---

### H-07: WebRTC setLocalDescription errors silently ignored ✅
**Component**: Host app — `WebRtcManager.kt`

`NoOpSdpObserver` swallowed all errors from `setLocalDescription`.

**Fixed in** `9dbc89b`: Replaced with `LoggingSdpObserver` that logs errors and chains the callback on success.

---

### H-12: WebSocket reconnect future race condition ✅
**Component**: Host + Client — `WebSocketManager.kt`

`reconnectFuture` accessed from multiple threads without synchronization.

**Fixed in** `9dbc89b`: `synchronized(this)` block around `reconnectFuture` in both `disconnect()` and `scheduleReconnect()`.

---

### C-01: PeerConnection resource leak on repeated calls ✅
**Component**: Client app — `ClientWebRtcManager.kt`

`createPeerConnection()` called twice leaks the first connection.

**Fixed in** `9dbc89b`: Calls `closePeerConnection()` before creating a new one.

---

### C-02: Silent SDP creation failures ✅
**Component**: Client app — `ClientSignalingHandler.kt`

Failed SDP offer/answer creation caused silent call hangups.

**Fixed in** `9dbc89b`: Sends `{"type":"webrtc","action":"error","body":"..."}` on failure.

---

## Medium — 17 fixed

### R-09: No unique constraint on Pairing table ✅
**Component**: Relay server — `models.py`

**Fixed in** `9dbc89b`: Added `UniqueConstraint("host_device_id", "client_device_id")`.

---

### R-10: Device online status stale after server restart ✅
**Component**: Relay server — `main.py`

`Device.is_online` persisted to DB but `connections` dict is in-memory.

**Fixed in** `9f12585`: Removed `is_online` persistence from WS handlers. `list_devices` already computed status from `connections` dict.

---

### R-11: No DB exception handling in WebSocket message relay ✅
**Component**: Relay server — `main.py`

`db.commit()` failure crashed the WebSocket loop.

**Fixed in** `9dbc89b`: Wrapped in try/except with `db.rollback()`.

---

### R-12: Unlimited pairing code generation ✅
**Component**: Relay server — `main.py`

Multiple active codes per host with no cleanup.

**Fixed in** `9dbc89b`: Previous unused codes expired on new generation.

---

### R-13: No message type validation ✅
**Component**: Relay server — `main.py`

Unknown WS message types silently relayed.

**Fixed in** `9f12585`: Added `ALLOWED_WS_TYPES = {"ping", "command", "event", "webrtc"}` whitelist. Invalid types rejected with error.

---

### R-14: No input validation on SMS/call commands ✅
**Component**: Relay server — `main.py`

No validation on `sim`, `to`, `body` fields.

**Fixed in** `9dbc89b`: Added Pydantic `Field` validators — `sim` (1-2), `to` (1-30 chars), `body` (1-1600 chars).

---

### R-15: No command offline queueing ✅
**Component**: Relay server — `main.py`, `models.py`

Commands failed with 503 when host offline.

**Fixed in** `9f12585`: Added `PendingCommand` table. Commands queued when host offline (returns `"status": "queued"`). Queued commands delivered on host reconnect.

---

### R-16: SQLAlchemy session shared across WebSocket lifetime ✅
**Component**: Relay server — `main.py`

Single session for entire WS connection could hold stale data.

**Fixed in** `9f12585`: `_ws_loop` creates a fresh `SessionLocal()` per message with try/finally close.

---

### H-08: Service binding with flags=0 in onStart ✅
**Component**: Host app — `MainActivity.kt`

`bindService()` with `0` flags silently fails if service not started.

**Fixed in** `9f12585`: Changed to `Context.BIND_AUTO_CREATE`.

---

### H-09: Unsafe binder cast in ServiceConnection ✅
**Component**: Host app — `MainActivity.kt`

`binder as BridgeService.LocalBinder` crashes if null.

**Fixed in** `9f12585`: Changed to `binder as? BridgeService.LocalBinder` with null check and log.

---

### H-10: SmsHandler silently falls back to default SIM ✅
**Component**: Host app — `SmsHandler.kt`

Missing SIM slot caused silent fallback to default SIM.

**Fixed in** `9f12585`: Returns `SMS_SENT` error event with `"SIM slot N not available"` if slot not found.

---

### H-11: Missing audio focus and audio mode configuration ✅
**Component**: Host app — `WebRtcManager.kt`

No `MODE_IN_COMMUNICATION` set before WebRTC audio capture.

**Fixed in** `9f12585`: Sets `AudioManager.MODE_IN_COMMUNICATION` in `createPeerConnection()`. Resets to `MODE_NORMAL` in `closePeerConnection()` and `dispose()`.

---

### H-13: DashboardScreen UI state out of sync with service ✅
**Component**: Host app — `DashboardScreen.kt`

Local `isServiceRunning` variable desynced on rapid double-tap.

**Fixed in** `9f12585`: `isServiceRunning` derived from `service != null` instead of local mutable state.

---

### C-03: Non-atomic notification ID in NotificationHelper ✅
**Component**: Client app — `NotificationHelper.kt`

`nextId++` not atomic; concurrent SMS notifications could overwrite.

**Fixed in** `9f12585`: Changed to `AtomicInteger` with `getAndIncrement()`.

---

### C-04: No phone number validation in DialerScreen ✅
**Component**: Client app — `DialerScreen.kt`

Accepted any string as phone number.

**Fixed in** `9f12585`: Added regex validation `^\+?[\d\s\-()]+$`. Shows error text for invalid input. Call button disabled until valid.

---

### C-05: Thread-unsafe state updates in ClientService ✅
**Component**: Client app — `ClientService.kt`

State fields updated from WS thread, read from main thread.

**Fixed in** `9dbc89b`: Added `@Volatile` to `connectionStatus`, `hostSims`, `callState`, `callNumber`. All callbacks posted to main thread via `Handler`. Lists synchronized.

---

### H-06: ICE candidate sdpMLineIndex=0 treated as null
**Component**: Host app — `SignalingHandler.kt`

**Status**: Not a bug. Kotlin's `?:` only triggers on null, not on 0.

---

## Low — 6 fixed

### R-17: No pagination on `/history` endpoint ✅
**Component**: Relay server — `main.py`

Only `limit` supported, no offset.

**Fixed in** `9f12585`: Added `offset` parameter. Returns `{"items": [...], "total": N, "offset": N, "limit": N}`.

---

### R-18: No device offline notification to paired client ✅
**Component**: Relay server — `main.py`

Paired client not notified when host disconnects.

**Fixed in** `9f12585`: Added `_notify_paired_offline()` — sends `{"type":"event","event":"DEVICE_OFFLINE","device_id":N}` to all paired devices on disconnect.

---

### R-19: No server-side heartbeat ✅
**Component**: Relay server — `main.py`

Client-initiated pings only; dead connections stay "online".

**Fixed in** `9f12585`: Added `_server_heartbeat()` task — sends `{"type":"ping"}` every 30s. Heartbeat cancelled on disconnect.

---

### H-14: BridgeConnectionService has no integration with BridgeService ✅
**Component**: Host app — `BridgeConnectionService.kt`, `BridgeService.kt`

Call state changes not forwarded as WS events.

**Fixed in** `9f12585`: Added `onCallStateEvent` static callback. `BridgeConnectionService` invokes it on connection state changes. `BridgeService.onCreate()` registers to forward as `CALL_STATE` WS events.

---

### H-15: ApiClient has no retry logic ✅
**Component**: Host app — `ApiClient.kt`

Single-attempt HTTP requests fail on transient errors.

**Fixed in** `9f12585`: Added OkHttp interceptor — retries 3 times with 1s/2s/3s backoff.

---

### C-06: Event listener lifecycle leak in SmsScreen/DialerScreen ✅
**Component**: Client app — `SmsScreen.kt`

`DisposableEffect` could clean up wrong service instance on recomposition.

**Fixed in** `9f12585`: Captured `service` in local `val svc` inside `DisposableEffect` block so dispose always targets the correct instance.

---

## Summary

| Severity | Relay | Host | Client | Total | Status |
|----------|-------|------|--------|-------|--------|
| Critical | 4 | 2 | 0 | **6** | All fixed |
| High | 4 | 5 | 2 | **11** | All fixed |
| Medium | 8 | 6 | 3 | **17** | All fixed |
| Low | 3 | 2 | 1 | **6** | All fixed |
| **Total** | **19** | **15** | **6** | **40** | **All fixed** |
