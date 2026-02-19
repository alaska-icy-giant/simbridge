# SimBridge Team

Five team members, each owning a distinct workstream. Everyone follows the
shared [Design System](DESIGN_SYSTEM.md) and the
[End-to-End Testing Guide](E2E_TESTING.md).

---

## 1 — Alice · iOS Host App

**Role:** Build the iOS Host App (Phone A — the phone with SIM cards).

**Responsibility:** Port the Android `host-app/` to a native Swift / SwiftUI
iOS application that performs the same functions: persistent WebSocket
connection, SMS send/receive, call placement, SIM info reporting, and WebRTC
audio bridging.

**Spec:** [ios-host-app.md](ios-host-app.md)

---

## 2 — Bob · iOS Client App

**Role:** Build the iOS Client App (Phone B — the remote control phone).

**Responsibility:** Create a native Swift / SwiftUI iOS application that
connects to SimBridge, pairs with a Host device, and lets the user compose
SMS, initiate calls, and view message history — all executed on the remote
Host phone.

**Spec:** [ios-client-app.md](ios-client-app.md)

---

## 3 — Carol · Host App Tests

**Role:** Test suite for both Android and iOS Host apps.

**Responsibility:** Write unit tests, UI tests, and integration tests that
verify every Host-side feature: WebSocket lifecycle, command dispatch, SMS
send/receive, call handling, SIM info, reconnect logic, and UI state.

**Spec:** [host-app-tests.md](host-app-tests.md)

---

## 4 — Dave · Client App Tests

**Role:** Test suite for the iOS Client App.

**Responsibility:** Write unit tests, UI tests, and integration tests that
verify every Client-side feature: login, device registration, pairing,
SMS composition, call initiation, history display, and offline handling.

**Spec:** [client-app-tests.md](client-app-tests.md)

---

## 5 — Eve · End-to-End System Testing

**Role:** Design and document the full-stack test harness that exercises
Host App ↔ SimBridge ↔ Client App as a single system.

**Responsibility:** Define the test environment (Docker + simulators),
automation tooling, CI pipeline, and write the E2E test scenarios that
cover the complete relay path.

**Spec:** [e2e-testing.md](e2e-testing.md)
