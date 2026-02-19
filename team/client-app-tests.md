# Dave — Client App Tests Spec

> Test suite for the iOS Client App.

---

## Test Strategy

| Layer            | Tooling                                  |
|------------------|------------------------------------------|
| Unit tests       | XCTest + Swift protocol-based mocking    |
| UI tests         | XCUITest                                 |
| Integration tests| URLProtocol mock or live Docker container |

---

## Unit Tests

### ApiClient

| Test                                  | What to verify                        |
|---------------------------------------|---------------------------------------|
| `test_login_sends_correct_body`       | POST /auth/login with username+password |
| `test_login_parses_token`             | Response token stored correctly       |
| `test_register_sends_correct_body`    | POST /auth/register with credentials  |
| `test_register_device_as_client`      | POST /devices with type="client"      |
| `test_confirm_pair_sends_code`        | POST /pair/confirm with code + deviceId |
| `test_send_sms_correct_payload`       | POST /sms with to_device_id, sim, to, body |
| `test_make_call_correct_payload`      | POST /call with to_device_id, sim, to |
| `test_get_sims_query_param`           | GET /sims?host_device_id=N            |
| `test_get_history_default_limit`      | GET /history returns list             |
| `test_get_history_with_device_filter` | GET /history?device_id=N              |
| `test_auth_header_set`               | Authorization: Bearer token on all requests |
| `test_http_error_throws`             | Non-2xx status throws descriptive error |
| `test_network_error_throws`          | URLSession failure throws             |

### WebSocketManager

| Test                                  | What to verify                        |
|---------------------------------------|---------------------------------------|
| `test_connects_to_client_endpoint`    | URL is /ws/client/{id}?token=...      |
| `test_reconnect_backoff`             | Delays: 1, 2, 4, 8, 16, 30, 30s      |
| `test_reconnect_resets_after_connect` | Counter resets to 0                   |
| `test_close_stops_reconnect`         | Intentional close does not retry      |
| `test_status_published`              | @Published connectionStatus updates   |
| `test_incoming_message_decoded`      | JSON text → WsMessage struct          |
| `test_malformed_json_handled`        | Bad JSON does not crash               |

### EventHandler

| Test                                  | What to verify                        |
|---------------------------------------|---------------------------------------|
| `test_incoming_sms_notification`      | INCOMING_SMS triggers local notification |
| `test_sms_sent_updates_state`        | SMS_SENT event updates compose UI state |
| `test_call_state_updates`            | CALL_STATE events update dialer UI    |
| `test_sim_info_updates_dashboard`    | SIM_INFO event updates SIM list       |
| `test_error_event_shows_alert`       | ERROR event presents alert            |
| `test_unknown_event_ignored`         | Unrecognized type does not crash      |

### Models

| Test                                  | What to verify                        |
|---------------------------------------|---------------------------------------|
| `test_sms_command_encodes`           | SmsCommand → JSON with snake_case keys |
| `test_call_command_encodes`          | CallCommand → JSON with snake_case keys |
| `test_pair_confirm_encodes`          | PairConfirm → JSON correctly          |
| `test_history_entry_decodes`         | JSON → HistoryEntry with all fields   |
| `test_ws_message_decodes`            | JSON → WsMessage with optional fields |
| `test_connection_status_enum`        | All 3 cases exist                     |

### SecureTokenStore

| Test                                  | What to verify                        |
|---------------------------------------|---------------------------------------|
| `test_getToken_returns_nil_empty`    | No token saved returns nil            |
| `test_saveToken_getToken_roundtrip`  | Save then get returns same value      |
| `test_clear_removes_token`           | clear() then getToken() returns nil   |
| `test_saveToken_overwrites`          | Second save replaces first value      |

### Prefs

| Test                                  | What to verify                        |
|---------------------------------------|---------------------------------------|
| `test_save_and_read_token`           | Written value readable                |
| `test_is_logged_in_true`             | Returns true when token is set        |
| `test_is_logged_in_false`            | Returns false when token is empty     |
| `test_clear_removes_all`            | clear() removes token, deviceId, etc  |
| `test_paired_host_id_persists`       | pairedHostId survives app restart     |

---

## UI Tests

### LoginView

| Test                                  | What to verify                        |
|---------------------------------------|---------------------------------------|
| `test_all_fields_present`            | Server URL, username, password visible |
| `test_login_button_disabled_empty`   | Button disabled when fields empty     |
| `test_password_visibility_toggle`    | Eye icon toggles                      |
| `test_login_shows_loading`           | Spinner appears during login          |
| `test_login_error_shown`            | Error message displays on failure     |
| `test_create_account_link`          | "Create Account" triggers registration |
| `test_successful_login_navigates`    | Goes to Pair or Dashboard             |
| `test_biometric_offer_after_login`   | Biometric offer shown after login (device-dependent) |

### PairView

| Test                                  | What to verify                        |
|---------------------------------------|---------------------------------------|
| `test_code_input_visible`            | 6-digit input field present           |
| `test_pair_button_exists`            | "Pair" button visible                 |
| `test_pair_button_disabled_empty`    | Disabled when code is blank           |
| `test_pair_success_navigates`        | Goes to Dashboard on success          |
| `test_pair_error_shown`             | "Invalid or expired" message shown    |
| `test_code_input_numeric_only`       | Only digits accepted                  |

### DashboardView

| Test                                  | What to verify                        |
|---------------------------------------|---------------------------------------|
| `test_status_card_shown`            | StatusCard visible with correct state |
| `test_paired_host_info`             | Host name and online/offline shown    |
| `test_sim_cards_from_host`          | Remote SIM info displayed             |
| `test_send_sms_button`              | Navigates to ComposeView             |
| `test_make_call_button`             | Navigates to DialerView              |
| `test_nav_to_history`               | History icon navigates correctly      |
| `test_nav_to_settings`              | Settings icon navigates correctly     |
| `test_event_feed_updates`           | Incoming events appear in real-time   |

### ComposeView

| Test                                  | What to verify                        |
|---------------------------------------|---------------------------------------|
| `test_to_field_phone_keyboard`       | Phone number field uses number pad    |
| `test_sim_selector_visible`          | SIM 1 / SIM 2 picker shown           |
| `test_message_body_multiline`        | Text area accepts multiple lines      |
| `test_send_button`                   | Sends SMS, shows success feedback     |
| `test_send_disabled_empty`           | Disabled when to or body is empty     |
| `test_send_error_shown`             | Error message on failure              |

### DialerView

| Test                                  | What to verify                        |
|---------------------------------------|---------------------------------------|
| `test_phone_number_field`            | Number input visible                  |
| `test_sim_selector`                  | SIM picker shown                      |
| `test_call_button`                   | Initiates call                        |
| `test_hang_up_button`               | Visible during active call            |
| `test_call_state_display`           | Shows "Dialing...", "Active", "Ended" |

### HistoryView

| Test                                  | What to verify                        |
|---------------------------------------|---------------------------------------|
| `test_entries_listed`                | History entries render with details   |
| `test_pull_to_refresh`              | Refresh re-fetches from API           |
| `test_empty_state`                  | "No history" message shown            |
| `test_entry_details`                | Timestamp, type badge, summary shown  |

### SettingsView

| Test                                  | What to verify                        |
|---------------------------------------|---------------------------------------|
| `test_server_url_shown`             | Displays current server URL           |
| `test_device_info_shown`            | Device name and ID                    |
| `test_paired_host_shown`            | Paired host name                      |
| `test_logout_confirmation`           | Confirmation dialog on logout         |
| `test_logout_navigates_to_login`    | Clears prefs, goes to LoginView       |
| `test_biometric_toggle_presence`     | Toggle present on biometric-capable devices |

---

## Integration Tests

### Against Mock Server (URLProtocol)

| Test                                  | What to verify                        |
|---------------------------------------|---------------------------------------|
| `test_full_login_flow`              | register → login → registerDevice     |
| `test_pair_flow`                    | confirmPair → dashboard               |
| `test_send_sms_flow`               | compose → POST /sms → success         |
| `test_ws_event_received`            | Mock WS sends INCOMING_SMS, handler runs |
| `test_history_fetch`               | GET /history → rendered in HistoryView |

### Against Docker Container

Requires SimBridge running: `docker run -d -p 8100:8100 simbridge`

| Test                                  | What to verify                        |
|---------------------------------------|---------------------------------------|
| `test_register_login_against_server` | Real HTTP register + login            |
| `test_create_client_device`         | POST /devices succeeds                |
| `test_pair_with_host`              | Full pairing flow (needs host device) |
| `test_ws_connect_receive_connected` | WebSocket handshake + connected msg   |
| `test_sms_relay_e2e`              | POST /sms → host WS receives command  |
| `test_history_after_relay`         | GET /history returns the relayed msg  |

---

## Test Fixtures

```
TestFixtures/
├── MockApiClient.swift         ← Protocol-conforming stub
├── MockWebSocketManager.swift  ← Records sent messages, simulates events
├── MockEventHandler.swift      ← Records handled events
├── StubPrefs.swift             ← In-memory prefs (no UserDefaults)
└── SampleData.swift            ← Canned JSON responses
```

---

## Running

```bash
# Unit + UI tests
xcodebuild test \
  -scheme SimBridgeClient \
  -destination 'platform=iOS Simulator,name=iPhone 16'

# Integration tests against container
SIMBRIDGE_URL=http://localhost:8100 xcodebuild test \
  -scheme SimBridgeClient \
  -testPlan IntegrationTests \
  -destination 'platform=iOS Simulator,name=iPhone 16'
```

---

## Acceptance Criteria

1. All unit tests pass with no network dependency
2. UI tests pass on iOS Simulator (iPhone 16, iOS 18)
3. Integration tests pass against SimBridge Docker container
4. Code coverage ≥ 80% on Data + Service layers, ≥ 60% on UI
