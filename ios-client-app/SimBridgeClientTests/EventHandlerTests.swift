import XCTest

// MARK: - EventHandler under test

/// Production-like EventHandler that processes incoming WebSocket messages.
final class EventHandler: EventHandlerProtocol {

    /// Tracks whether a local notification was requested.
    private(set) var pendingNotifications: [(title: String, body: String)] = []

    /// Current SMS send status (updated on SMS_SENT).
    @Published var smsSendStatus: String?

    /// Current call state (updated on CALL_STATE).
    @Published var callState: String?

    /// SIM list from the host (updated on SIM_INFO).
    @Published var sims: [SimInfo] = []

    /// Most recent error message (updated on ERROR).
    @Published var errorAlert: String?

    func handle(_ message: WsMessage) {
        guard let type = message.type else { return }

        switch type {
        case "INCOMING_SMS":
            let from = message.from ?? "Unknown"
            let body = message.body ?? "New message"
            scheduleLocalNotification(title: "SMS from \(from)", body: body)

        case "SMS_SENT":
            smsSendStatus = message.status ?? "sent"

        case "CALL_STATE":
            callState = message.state

        case "SIM_INFO":
            if let simList = message.sims {
                sims = simList
            }

        case "ERROR":
            errorAlert = message.message ?? "Unknown error"

        default:
            // Unknown event — silently ignore
            break
        }
    }

    private func scheduleLocalNotification(title: String, body: String) {
        pendingNotifications.append((title: title, body: body))
        // In production this would use UNUserNotificationCenter.
    }

    func reset() {
        pendingNotifications = []
        smsSendStatus = nil
        callState = nil
        sims = []
        errorAlert = nil
    }
}

// MARK: - Tests

final class EventHandlerTests: XCTestCase {

    var sut: EventHandler!

    override func setUp() {
        super.setUp()
        sut = EventHandler()
    }

    override func tearDown() {
        sut = nil
        super.tearDown()
    }

    // MARK: - INCOMING_SMS

    func test_incoming_sms_triggers_notification() throws {
        let data = SampleData.wsIncomingSmsJSON.data(using: .utf8)!
        let message = try JSONDecoder().decode(WsMessage.self, from: data)

        sut.handle(message)

        XCTAssertEqual(sut.pendingNotifications.count, 1)
        XCTAssertEqual(sut.pendingNotifications[0].title, "SMS from +1234567890")
        XCTAssertEqual(sut.pendingNotifications[0].body, "Test message")
    }

    // MARK: - SMS_SENT

    func test_sms_sent_updates_state() throws {
        let data = SampleData.wsSmsSentJSON.data(using: .utf8)!
        let message = try JSONDecoder().decode(WsMessage.self, from: data)

        sut.handle(message)

        XCTAssertEqual(sut.smsSendStatus, "delivered")
    }

    // MARK: - CALL_STATE

    func test_call_state_updates_dialer() throws {
        let data = SampleData.wsCallStateJSON.data(using: .utf8)!
        let message = try JSONDecoder().decode(WsMessage.self, from: data)

        sut.handle(message)

        XCTAssertEqual(sut.callState, "active")
    }

    func test_call_state_transitions() throws {
        let states = ["dialing", "active", "ended"]

        for state in states {
            let json = """
            {"type": "CALL_STATE", "state": "\(state)", "to": "+1234567890", "sim": 1}
            """
            let data = json.data(using: .utf8)!
            let message = try JSONDecoder().decode(WsMessage.self, from: data)

            sut.handle(message)

            XCTAssertEqual(sut.callState, state, "Expected callState to be \(state)")
        }
    }

    // MARK: - SIM_INFO

    func test_sim_info_updates_dashboard() throws {
        let data = SampleData.wsSimInfoJSON.data(using: .utf8)!
        let message = try JSONDecoder().decode(WsMessage.self, from: data)

        sut.handle(message)

        XCTAssertEqual(sut.sims.count, 2)
        XCTAssertEqual(sut.sims[0].slot, 1)
        XCTAssertEqual(sut.sims[0].carrier, "T-Mobile")
        XCTAssertEqual(sut.sims[0].number, "+1111111111")
        XCTAssertEqual(sut.sims[1].slot, 2)
        XCTAssertEqual(sut.sims[1].carrier, "AT&T")
    }

    // MARK: - ERROR

    func test_error_event_shows_alert() throws {
        let data = SampleData.wsErrorJSON.data(using: .utf8)!
        let message = try JSONDecoder().decode(WsMessage.self, from: data)

        sut.handle(message)

        XCTAssertEqual(sut.errorAlert, "Host device is offline")
    }

    // MARK: - Unknown event

    func test_unknown_event_ignored() throws {
        let data = SampleData.wsUnknownEventJSON.data(using: .utf8)!
        let message = try JSONDecoder().decode(WsMessage.self, from: data)

        // Should not crash
        sut.handle(message)

        XCTAssertNil(sut.smsSendStatus)
        XCTAssertNil(sut.callState)
        XCTAssertNil(sut.errorAlert)
        XCTAssertTrue(sut.sims.isEmpty)
        XCTAssertTrue(sut.pendingNotifications.isEmpty)
    }
}
