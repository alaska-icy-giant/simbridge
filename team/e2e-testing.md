# Eve — End-to-End System Testing Spec

> Design and document the test system that exercises
> Host App ↔ SimBridge ↔ Client App as one integrated system.

---

## System Under Test

```
┌──────────────┐          ┌───────────────┐          ┌──────────────┐
│  Host App    │◄── WS ──►│   SimBridge    │◄── WS ──►│  Client App  │
│  (Phone A)   │          │  (Docker)      │          │  (Phone B)   │
│              │          │                │◄── REST ──│              │
│  SIM / Call  │          │  SQLite + JWT  │          │  UI / Remote │
└──────────────┘          └───────────────┘          └──────────────┘
```

The E2E tests prove that a command typed on the Client reaches the Host
and that events from the Host reach the Client — with the real relay
server in the middle.

---

## Test Environment

### Components

| Component     | How it runs                                   |
|---------------|-----------------------------------------------|
| SimBridge     | Docker container (`simbridge` image, port 8100) |
| Host stub     | Python script using `websockets` library       |
| Client stub   | Python script using `httpx` + `websockets`     |
| Test runner   | pytest (same repo, `test_e2e.py`)              |

The Host and Client stubs are lightweight Python scripts that mimic the
real apps' WebSocket and REST behavior. This eliminates the need for
physical devices or emulators in CI.

For device-level testing (real iOS/Android apps), use the manual
procedure in the "Device Testing" section below.

### Environment Diagram

```
┌─────────────────────────────────────────────────┐
│                   CI Runner                      │
│                                                  │
│  ┌────────────┐   ┌───────────┐   ┌──────────┐ │
│  │ pytest     │   │ SimBridge │   │ (stubs   ││
│  │ test_e2e   │──►│ container │◄──│  inside  ││
│  │            │   │ :8100     │   │  pytest) ││
│  └────────────┘   └───────────┘   └──────────┘ │
└─────────────────────────────────────────────────┘
```

### Setup

```bash
# 1. Start SimBridge
docker rm -f simbridge 2>/dev/null
docker run -d --name simbridge \
  -p 8100:8100 \
  -e JWT_SECRET="e2e-test-secret-32chars-minimum!!" \
  simbridge

# 2. Wait for ready
until curl -sf http://localhost:8100/docs > /dev/null; do sleep 1; done

# 3. Run E2E tests
pytest test_e2e.py -v

# 4. Teardown
docker rm -f simbridge
```

---

## Test Scenarios

### Scenario 1: Registration & Login

```
Client Stub                    SimBridge
    │                              │
    ├── POST /auth/register ──────►│  create user
    ├── POST /auth/login ─────────►│  return JWT
    ├── POST /devices (client) ───►│  create client device
    │                              │
Host Stub                          │
    ├── POST /auth/login ─────────►│  return JWT
    ├── POST /devices (host) ─────►│  create host device
```

**Assertions:**
- Both register/login succeed (200)
- Both devices created with correct types
- Tokens are valid JWTs

### Scenario 2: Pairing

```
Host Stub                      SimBridge              Client Stub
    │                              │                      │
    ├── POST /pair ───────────────►│  return 6-digit code  │
    │                              │                      │
    │                              │◄── POST /pair/confirm─┤
    │                              │    return paired      │
```

**Assertions:**
- Pairing code is 6 digits
- Confirm returns `status: "paired"`
- Repeated confirm returns `status: "already_paired"`

### Scenario 3: SMS Relay (REST path)

```
Client Stub                    SimBridge              Host Stub (WS)
    │                              │                      │
    │                              │◄──── WS connected ───┤
    ├── POST /sms ────────────────►│                      │
    │   {to_device_id, sim,        │── WS: SEND_SMS ─────►│
    │    to, body}                 │                      │
    │◄── {status: sent} ──────────┤                      │
```

**Assertions:**
- POST /sms returns 200 with `status: "sent"` and `req_id`
- Host WebSocket receives message with `cmd: "SEND_SMS"`, correct `to` and `body`
- GET /history shows the relayed message

### Scenario 4: Call Relay (REST path)

Same pattern as SMS with `POST /call` and `cmd: "MAKE_CALL"`.

### Scenario 5: WebSocket Relay (bidirectional)

```
Host Stub (WS)                 SimBridge              Client Stub (WS)
    │                              │                      │
    │◄──── WS connected ──────────┤──── WS connected ───►│
    │                              │                      │
    │                              │◄── {type: command} ──┤ client sends
    │◄── {type: command,           │                      │
    │     from_device_id: C} ──────┤                      │
    │                              │                      │
    ├── {type: event,              │                      │
    │    to_device_id: C} ────────►│── {type: event,      │
    │                              │   from_device_id: H}►│ client receives
```

**Assertions:**
- Client→Host: message arrives with correct `from_device_id`
- Host→Client: event arrives with correct `from_device_id`
- Both messages logged in GET /history

### Scenario 6: Host Offline

```
Client Stub                    SimBridge
    │                              │  (no host WS)
    ├── POST /sms ────────────────►│
    │◄── 503 Host offline ────────┤
```

**Assertions:**
- POST /sms returns 503 when host has no WebSocket connection

### Scenario 7: Target Offline (WebSocket)

```
Client Stub (WS)               SimBridge
    │                              │  (no host WS)
    ├── {type: command} ──────────►│
    │◄── {error: target_offline} ──┤
```

**Assertions:**
- Client receives `target_offline` error over WebSocket

### Scenario 8: Reconnection

```
Host Stub (WS)                 SimBridge
    │                              │
    │◄──── WS connected ──────────┤
    │◄──── server closes WS ──────┤
    │                              │
    │ (wait backoff delay)         │
    │                              │
    │── WS reconnect ────────────►│
    │◄──── WS connected ──────────┤
```

**Assertions:**
- After forced disconnect, stub reconnects within expected backoff window
- `connected` message received again after reconnect

### Scenario 9: Ping / Pong

```
Host Stub (WS)                 SimBridge
    │                              │
    ├── {type: ping} ─────────────►│
    │◄── {type: pong} ────────────┤
```

### Scenario 10: Message History

```
Client Stub                    SimBridge
    │                              │
    │  (after scenarios 3-5)       │
    ├── GET /history ─────────────►│
    │◄── [log entries] ───────────┤
    │                              │
    ├── GET /history?device_id=H ─►│
    │◄── [filtered entries] ──────┤
```

**Assertions:**
- History contains entries from relay scenarios
- Filtering by device_id returns only matching entries
- Each entry has `from_device_id`, `to_device_id`, `msg_type`, `payload`, `created_at`

---

## Test Implementation

The E2E tests live in `test_e2e.py` in the repo root. They build on the
existing `test_container.py` pattern but use two independent user
accounts (host user + client user) to simulate a realistic two-phone
scenario.

```python
# test_e2e.py structure (pseudocode)

@pytest.fixture(scope="module")
def simbridge():
    """Ensure SimBridge container is running."""
    ...

@pytest.fixture(scope="module")
def host_user(simbridge):
    """Register host user, login, create host device, return state."""
    ...

@pytest.fixture(scope="module")
def client_user(simbridge):
    """Register client user, login, create client device, return state."""
    ...

@pytest.fixture(scope="module")
def paired(host_user, client_user):
    """Pair host and client devices, return pairing info."""
    ...

class TestSmsRelay:
    async def test_sms_via_rest(self, paired, host_ws, http_client): ...
    async def test_sms_via_ws(self, paired, host_ws, client_ws): ...

class TestCallRelay:
    async def test_call_via_rest(self, paired, host_ws, http_client): ...

class TestBidirectionalWs:
    async def test_host_to_client(self, paired, host_ws, client_ws): ...
    async def test_client_to_host(self, paired, host_ws, client_ws): ...

class TestOffline:
    async def test_host_offline_rest(self, paired, http_client): ...
    async def test_target_offline_ws(self, paired, client_ws): ...

class TestHistory:
    def test_history_after_relay(self, paired, http_client): ...
```

---

## Device Testing (Manual / Semi-Automated)

For testing with real iOS/Android apps on physical devices or simulators:

### Prerequisites

| Item                  | Details                                     |
|-----------------------|---------------------------------------------|
| SimBridge server      | Running on a reachable host (Docker or bare)|
| Host device/simulator | Android emulator or iOS Simulator           |
| Client device/sim     | Second simulator or physical phone          |
| Network               | All three on same network (or port-forwarded)|

### Manual Test Procedure

```
Step  Action                                  Expected
─────────────────────────────────────────────────────────────────
1     Start SimBridge container                Server on :8100
2     Open Host app, login                     Dashboard shown
3     Tap "Start Service"                      Status: Connected (green)
4     Open Client app, login                   Dashboard or Pair screen
5     On Host: note the pairing code           6-digit code displayed
6     On Client: enter pairing code            "Paired" confirmation
7     On Client: compose SMS to +1234          SMS appears on Host WS log
8     On Client: make call to +1234            Call state changes on Host
9     On Client: view History                  SMS and call entries shown
10    On Host: stop service                    Status: Offline
11    On Client: try SMS                       503 error displayed
12    On Host: restart service                 Reconnects, status green
13    Logout on both                           Back to login screens
```

### Automated Device Tests

Use platform-specific UI test frameworks pointed at the real apps:

| Platform | Framework  | How                                       |
|----------|------------|-------------------------------------------|
| Android  | Espresso   | `connectedAndroidTest` with SimBridge URL  |
| iOS      | XCUITest   | Test plan with `SIMBRIDGE_URL` env var     |

The test code lives in Carol's and Dave's test targets. The E2E aspect
is that both apps run simultaneously against the same SimBridge instance.

---

## CI Pipeline

```yaml
# .github/workflows/e2e.yml (or equivalent)
name: E2E Tests

on: [push, pull_request]

jobs:
  e2e:
    runs-on: ubuntu-latest
    services:
      simbridge:
        image: simbridge
        ports: ["8100:8100"]
        env:
          JWT_SECRET: ci-test-secret-at-least-32-chars!!

    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: "3.13"
      - run: pip install -r requirements.txt
      - run: pytest test_e2e.py test_container.py -v
```

For iOS/Android app tests in CI, add macOS runners with Xcode and
Android SDK respectively. These are slower and typically run on merge
to main, not on every push.

---

## Monitoring & Reporting

- pytest produces JUnit XML: `pytest --junitxml=results.xml`
- CI publishes test results as build artifacts
- Flaky test detection: any test that fails intermittently gets a
  `@pytest.mark.flaky(reruns=2)` marker and a filed issue

---

## Troubleshooting

| Symptom                        | Likely cause                    | Fix                            |
|--------------------------------|---------------------------------|--------------------------------|
| Connection refused :8100       | Container not running           | `docker start simbridge`       |
| 401 on all requests            | JWT_SECRET mismatch             | Use same secret in tests       |
| WebSocket closes immediately   | Invalid token                   | Re-login, get fresh token      |
| test_reconnect flaky           | Backoff timing too tight        | Increase wait tolerance        |
| History empty after relay      | Different user accounts         | Verify device ownership        |
