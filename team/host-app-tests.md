# Carol — Host App Tests Spec

> Test suite for both Android and iOS Host apps.

---

## Test Strategy

Three layers of testing, each with its own tooling:

| Layer            | Android                    | iOS                        |
|------------------|----------------------------|----------------------------|
| Unit tests       | JUnit 5 + Mockk            | XCTest + Swift mocking     |
| UI tests         | Compose UI Test            | XCUITest                   |
| Integration tests| OkHttp MockWebServer       | URLProtocol mock           |

---

## Unit Tests

### WebSocketManager

| Test                                | What to verify                          |
|-------------------------------------|-----------------------------------------|
| `test_connects_with_correct_url`    | HTTP→WS URL conversion, token appended  |
| `test_reconnect_backoff_sequence`   | Delays: 1, 2, 4, 8, 16, 30, 30s        |
| `test_reconnect_resets_on_success`  | Retry counter resets to 0 after connect |
| `test_intentional_close_no_reconnect`| Calling close() does not trigger retry |
| `test_status_transitions`           | DISCONNECTED→CONNECTING→CONNECTED       |
| `test_ping_sent_on_interval`        | Ping fires every 30s                    |
| `test_message_callback_invoked`     | Incoming text triggers onMessage        |

### CommandHandler

| Test                                | What to verify                          |
|-------------------------------------|-----------------------------------------|
| `test_dispatch_send_sms`            | SEND_SMS cmd routes to SmsHandler       |
| `test_dispatch_make_call`           | MAKE_CALL cmd routes to CallHandler     |
| `test_dispatch_hang_up`             | HANG_UP cmd routes to CallHandler       |
| `test_dispatch_get_sims`            | GET_SIMS cmd routes to SimInfoProvider  |
| `test_unknown_command_ignored`      | Unknown cmd does not crash              |
| `test_missing_fields_returns_error` | Missing "to" field returns error event  |

### SmsHandler

| Test                                | What to verify                          |
|-------------------------------------|-----------------------------------------|
| `test_send_sms_success`             | SmsManager called with correct args     |
| `test_send_sms_sim_selection`       | Correct subscriptionId for SIM slot     |
| `test_send_sms_multipart`           | Long message uses sendMultipartTextMessage |
| `test_send_sms_failure_event`       | Error event sent on SmsManager failure  |

### CallHandler

| Test                                | What to verify                          |
|-------------------------------------|-----------------------------------------|
| `test_make_call_success`            | TelecomManager/CallKit called correctly |
| `test_hang_up_success`              | Call terminated                         |
| `test_call_state_events`            | CALL_STATE events fired (dialing, ended)|
| `test_make_call_permission_error`   | Error event on missing permission       |

### SmsReceiver (Android only)

| Test                                | What to verify                          |
|-------------------------------------|-----------------------------------------|
| `test_incoming_sms_parsed`          | SmsMessage extracted from intent        |
| `test_incoming_sms_event_sent`      | INCOMING_SMS event sent via callback    |
| `test_sim_slot_extracted`           | SIM slot read from intent extras        |

### SimInfoProvider

| Test                                | What to verify                          |
|-------------------------------------|-----------------------------------------|
| `test_returns_active_sims`          | List of SimInfo with slot/carrier       |
| `test_empty_on_no_sims`             | Returns empty list when no SIM          |
| `test_security_exception_handled`   | Returns empty list on permission denied |

### BridgeService

| Test                                | What to verify                          |
|-------------------------------------|-----------------------------------------|
| `test_service_starts_websocket`     | WebSocketManager.connect() called       |
| `test_service_stops_cleanly`        | WebSocket closed, state reset           |
| `test_log_entries_capped_at_100`    | 101st entry evicts the oldest           |
| `test_status_callback_fires`        | onStatusChange called on state change   |

### Models

| Test                                | What to verify                          |
|-------------------------------------|-----------------------------------------|
| `test_ws_message_serialization`     | JSON round-trip with snake_case keys    |
| `test_ws_message_nullable_fields`   | Missing optional fields decode as nil   |

### WebRTC

| Test                                | What to verify                          |
|-------------------------------------|-----------------------------------------|
| `test_peer_connection_created`      | PeerConnectionFactory produces non-null |
| `test_offer_contains_audio`         | SDP offer includes audio m-line         |
| `test_ice_candidate_sent`           | ICE candidate triggers send callback    |
| `test_signaling_offer_handled`      | Incoming offer creates answer           |
| `test_signaling_answer_handled`     | Incoming answer sets remote description |

---

## UI Tests

### Login Screen

| Test                                | What to verify                          |
|-------------------------------------|-----------------------------------------|
| `test_login_fields_visible`         | URL, username, password, button exist   |
| `test_login_button_disabled_empty`  | Button disabled when fields are blank   |
| `test_password_toggle`              | Eye icon toggles password visibility    |
| `test_login_shows_spinner`          | Progress indicator visible during login |
| `test_login_error_displayed`        | Error text shown on failure             |
| `test_login_navigates_to_dashboard` | Successful login goes to Dashboard      |

### Dashboard Screen

| Test                                | What to verify                          |
|-------------------------------------|-----------------------------------------|
| `test_status_card_connected`        | Green card with checkmark when online   |
| `test_status_card_disconnected`     | Red card with cloud-off when offline    |
| `test_start_stop_button_toggles`    | Text and color change on tap            |
| `test_sim_cards_displayed`          | SIM info cards render correctly         |
| `test_empty_sim_state`              | "No SIM cards" message shown            |
| `test_nav_to_log`                   | Log icon navigates to Log screen        |
| `test_nav_to_settings`              | Settings icon navigates to Settings     |

### Log Screen

| Test                                | What to verify                          |
|-------------------------------------|-----------------------------------------|
| `test_log_entries_rendered`         | Entries appear with timestamp/direction |
| `test_log_direction_colors`         | IN = primary, OUT = secondary           |
| `test_empty_log_message`            | Empty state text shown                  |

### Settings Screen

| Test                                | What to verify                          |
|-------------------------------------|-----------------------------------------|
| `test_server_url_displayed`         | Shows current server URL                |
| `test_device_info_displayed`        | Shows device name and ID                |
| `test_logout_confirmation`          | Logout button shows confirmation dialog |
| `test_logout_clears_and_navigates`  | Confirmed logout clears prefs → Login   |

---

## Integration Tests

Use a mock WebSocket server (OkHttp MockWebServer on Android,
URLProtocol-based mock on iOS) or the real SimBridge Docker container.

### Against Mock Server

| Test                                | What to verify                          |
|-------------------------------------|-----------------------------------------|
| `test_login_registers_device`       | POST /auth/login + POST /devices        |
| `test_ws_receives_command`          | Mock server sends SEND_SMS, handler runs|
| `test_ws_sends_event`               | SMS_SENT event reaches mock server      |
| `test_ws_reconnect_after_drop`      | Server closes connection, client retries|

### Against Docker Container

Run the SimBridge container (`docker run -d -p 8100:8100 simbridge`) and
execute tests with `BASE_URL=http://localhost:8100`:

| Test                                | What to verify                          |
|-------------------------------------|-----------------------------------------|
| `test_full_login_flow`              | Register → login → get token            |
| `test_device_registration`          | Create host device                      |
| `test_ws_connect_and_ping`          | WebSocket connects, ping/pong works     |
| `test_receive_command_from_client`  | Client sends SMS command, host receives |

---

## Test Fixtures

Provide reusable helpers:

```
TestFixtures/
├── MockWebSocketServer.swift / .kt    ← Programmable WS mock
├── FakeSmsHandler.swift / .kt         ← Records send calls
├── FakeCallHandler.swift / .kt        ← Records call actions
├── FakeSimInfoProvider.swift / .kt    ← Returns canned SIM data
└── TestApiClient.swift / .kt          ← Stub HTTP responses
```

---

## Running

```bash
# Android
./gradlew :app:test                    # unit tests
./gradlew :app:connectedAndroidTest    # UI tests (emulator required)

# iOS
xcodebuild test -scheme SimBridgeHost -destination 'platform=iOS Simulator,name=iPhone 16'
```

---

## Acceptance Criteria

1. All unit tests pass in CI with no device/simulator required
2. UI tests pass on Android emulator (API 34) and iOS Simulator (iPhone 16)
3. Integration tests pass against SimBridge Docker container
4. Code coverage ≥ 80% on service layer, ≥ 60% on UI layer
