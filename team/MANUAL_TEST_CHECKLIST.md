# SimBridge Manual Device Test Checklist

Use this checklist when testing with real iOS/Android apps on physical devices or simulators.
Print it out or copy it into your test tracker. Check off each item as you verify it.

## Prerequisites

| Item | Status |
|------|--------|
| SimBridge server running and reachable on port 8100 | [ ] |
| Host device/simulator available (Android emulator or iOS Simulator) | [ ] |
| Client device/simulator available (second device or phone) | [ ] |
| All devices on the same network (or port-forwarded) | [ ] |
| SimBridge URL configured in both apps | [ ] |

---

## Core Flow (Steps 1-13)

### Server Startup
- [ ] **Step 1** — Start SimBridge container (`docker run ...` or `docker-compose up`)
  - Verify server responds on `:8100`
  - Verify `/docs` endpoint loads in browser

### Host App Login
- [ ] **Step 2** — Open Host app and log in
  - Dashboard/home screen is shown
  - No error messages displayed

### Host Service Start
- [ ] **Step 3** — Tap "Start Service" on Host app
  - Status indicator shows **Connected** (green)
  - WebSocket connection established (check server logs if needed)

### Client App Login
- [ ] **Step 4** — Open Client app and log in
  - Dashboard or Pair screen is shown
  - No error messages displayed

### Pairing Code Generation
- [ ] **Step 5** — On Host: note the pairing code
  - 6-digit code is displayed
  - Code is clearly readable

### Pairing Confirmation
- [ ] **Step 6** — On Client: enter pairing code
  - "Paired" confirmation shown
  - Both apps reflect paired state

### SMS Relay
- [ ] **Step 7** — On Client: compose and send SMS to +1234567890
  - SMS command appears in Host app WS log / notification
  - Host app shows correct recipient number
  - Host app shows correct message body

### Call Relay
- [ ] **Step 8** — On Client: initiate call to +1234567890
  - Call state change reflected on Host app
  - Correct phone number shown on Host

### History View
- [ ] **Step 9** — On Client: view message History
  - SMS entry from Step 7 is visible
  - Call entry from Step 8 is visible
  - Entries show correct timestamps

### Host Offline
- [ ] **Step 10** — On Host: stop service
  - Status indicator shows **Offline**
  - WebSocket disconnected

### Client Error on Offline Host
- [ ] **Step 11** — On Client: try to send SMS while Host is offline
  - 503 / "Host offline" error displayed to user
  - No crash or hang

### Host Reconnection
- [ ] **Step 12** — On Host: restart service
  - WebSocket reconnects automatically
  - Status indicator returns to **Connected** (green)
  - Client can now send commands again

### Logout
- [ ] **Step 13** — Logout on both devices
  - Both apps return to login screens
  - No residual session state

---

## Edge Cases and Additional Checks

### Authentication
- [ ] Login with wrong password shows appropriate error
- [ ] Login with non-existent username shows appropriate error
- [ ] Accessing protected screens without login redirects to login
- [ ] Token expiry after 24h forces re-login (if feasible to test)

### Registration
- [ ] Register with duplicate username shows error
- [ ] Register with empty username/password is rejected

### Pairing
- [ ] Entering wrong 6-digit code shows error, does not pair
- [ ] Entering expired code (wait >10 min) shows error
- [ ] Re-pairing already-paired devices shows "already paired"
- [ ] Pairing code from User A cannot be used by User B

### Device Isolation
- [ ] User A's devices are not visible to User B
- [ ] User B cannot send SMS to User A's host device
- [ ] User B cannot connect WebSocket to User A's device

### SMS Edge Cases
- [ ] SMS with maximum body length (1600 characters) relays correctly
- [ ] SMS with special characters (emoji, unicode) relays correctly
- [ ] SMS with empty body is rejected
- [ ] SMS to non-existent device ID returns error

### Call Edge Cases
- [ ] Call to valid paired host succeeds
- [ ] Call to offline host returns 503
- [ ] Call to non-existent device ID returns error

### WebSocket Behavior
- [ ] Ping message receives pong response
- [ ] Host-to-Client event relay works bidirectionally
- [ ] Client-to-Host command relay works bidirectionally
- [ ] Sending command when target is offline returns `target_offline` error
- [ ] Connecting with invalid/expired token is rejected immediately
- [ ] Connecting wrong device type to endpoint (client to /ws/host) is rejected
- [ ] Duplicate WebSocket connection replaces the old one cleanly

### History
- [ ] History shows all relayed messages for the user
- [ ] History filter by device_id returns only matching entries
- [ ] History entries contain: from_device_id, to_device_id, msg_type, payload, created_at
- [ ] History limit parameter restricts number of returned entries
- [ ] History returns empty list for user with no messages

### Reconnection / Resilience
- [ ] Force-closing Host WebSocket marks device as offline
- [ ] Reconnecting after force-close succeeds without re-login
- [ ] Server handles rapid connect/disconnect cycles without crashing
- [ ] Multiple hosts and clients connected simultaneously route messages to correct pairs

### Rate Limiting
- [ ] Excessive failed login attempts (>5 in 60s) return HTTP 429
- [ ] Rate limit resets after the window expires

---

## Sign-Off

| Field | Value |
|-------|-------|
| Tester name | |
| Date | |
| SimBridge version / commit | |
| Host device/OS | |
| Client device/OS | |
| All checks passed? | [ ] Yes  [ ] No |
| Notes | |
