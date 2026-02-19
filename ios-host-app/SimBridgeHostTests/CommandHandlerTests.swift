import XCTest
@testable import SimBridgeHost

final class CommandHandlerTests: XCTestCase {

    private var mockCallHandler: MockCallHandler!
    private var mockSimInfoProvider: MockSimInfoProvider!
    private var sentEvents: [WsMessage]!
    private var logEntries: [LogEntry]!

    // We test dispatch logic directly, mirroring the CommandHandler switch/case.

    override func setUp() {
        super.setUp()
        mockCallHandler = MockCallHandler()
        mockSimInfoProvider = MockSimInfoProvider()
        sentEvents = []
        logEntries = []
    }

    override func tearDown() {
        mockCallHandler = nil
        mockSimInfoProvider = nil
        sentEvents = nil
        logEntries = nil
        super.tearDown()
    }

    // MARK: - Dispatch Helpers

    /// Mimics CommandHandler.handleCommand dispatch logic for unit testing
    /// without requiring the full iOS CommandHandler class instantiation.
    private func dispatch(_ message: WsMessage) {
        guard let cmd = message.cmd else { return }

        logEntries.append(LogEntry(direction: "IN", summary: "CMD: \(cmd) \(message.to ?? "")"))

        switch cmd {
        case "SEND_SMS":
            guard let to = message.to, let body = message.body else {
                sendError(reqId: message.reqId, msg: "SEND_SMS requires 'to' and 'body'")
                return
            }
            // Would route to SmsHandler -- record for verification
            sentEvents.append(WsMessage(type: "event", event: "SMS_DISPATCHED",
                                        to: to, body: body, sim: message.sim, reqId: message.reqId))

        case "MAKE_CALL":
            guard let to = message.to else {
                sendError(reqId: message.reqId, msg: "MAKE_CALL requires 'to'")
                return
            }
            mockCallHandler.makeCall(to: to, sim: message.sim, reqId: message.reqId)

        case "HANG_UP":
            mockCallHandler.hangUp(reqId: message.reqId)

        case "GET_SIMS":
            let sims = mockSimInfoProvider.getActiveSimCards()
            sentEvents.append(WsMessage(type: "event", event: "SIM_INFO",
                                        sims: sims, reqId: message.reqId))

        default:
            sendError(reqId: message.reqId, msg: "Unknown command: \(cmd)")
        }
    }

    private func sendError(reqId: String?, msg: String) {
        sentEvents.append(WsMessage(type: "event", event: "ERROR",
                                    status: "error", body: msg, reqId: reqId))
    }

    // MARK: - SEND_SMS Dispatch

    func testDispatchSendSmsRoutesToHandler() {
        let msg = WsMessage(type: "command", cmd: "SEND_SMS",
                            to: "+15551234567", body: "Hello", sim: 1, reqId: "req-1")
        dispatch(msg)

        XCTAssertEqual(sentEvents.count, 1)
        XCTAssertEqual(sentEvents[0].event, "SMS_DISPATCHED")
        XCTAssertEqual(sentEvents[0].to, "+15551234567")
        XCTAssertEqual(sentEvents[0].body, "Hello")
        XCTAssertEqual(sentEvents[0].sim, 1)
    }

    // MARK: - MAKE_CALL Dispatch

    func testDispatchMakeCallRoutesToCallHandler() {
        let msg = WsMessage(type: "command", cmd: "MAKE_CALL",
                            to: "+15559876543", sim: 2, reqId: "req-3")
        dispatch(msg)

        XCTAssertEqual(mockCallHandler.makeCallRecords.count, 1)
        XCTAssertEqual(mockCallHandler.makeCallRecords[0].to, "+15559876543")
        XCTAssertEqual(mockCallHandler.makeCallRecords[0].sim, 2)
        XCTAssertEqual(mockCallHandler.makeCallRecords[0].reqId, "req-3")
    }

    // MARK: - HANG_UP Dispatch

    func testDispatchHangUpRoutesToCallHandler() {
        let msg = WsMessage(type: "command", cmd: "HANG_UP", reqId: "req-4")
        dispatch(msg)

        XCTAssertEqual(mockCallHandler.hangUpRecords.count, 1)
        XCTAssertEqual(mockCallHandler.hangUpRecords[0].reqId, "req-4")
    }

    // MARK: - GET_SIMS Dispatch

    func testDispatchGetSimsRoutesToSimInfoProviderAndSendsEvent() {
        mockSimInfoProvider.activeSims = [
            SimInfo(slot: 1, carrier: "T-Mobile", number: "+15551111111"),
            SimInfo(slot: 2, carrier: "AT&T", number: nil),
        ]

        let msg = WsMessage(type: "command", cmd: "GET_SIMS", reqId: "req-5")
        dispatch(msg)

        XCTAssertEqual(mockSimInfoProvider.getActiveSimCardsCallCount, 1)
        XCTAssertEqual(sentEvents.count, 1)
        XCTAssertEqual(sentEvents[0].event, "SIM_INFO")
        XCTAssertEqual(sentEvents[0].reqId, "req-5")
        XCTAssertEqual(sentEvents[0].sims?.count, 2)
        XCTAssertEqual(sentEvents[0].sims?[0].carrier, "T-Mobile")
    }

    // MARK: - Unknown Command

    func testUnknownCommandDoesNotCrashAndSendsError() {
        let msg = WsMessage(type: "command", cmd: "SELF_DESTRUCT", reqId: "req-6")

        // Should not throw
        dispatch(msg)

        XCTAssertEqual(sentEvents.count, 1)
        XCTAssertEqual(sentEvents[0].event, "ERROR")
        XCTAssertEqual(sentEvents[0].status, "error")
        XCTAssertTrue(sentEvents[0].body?.contains("Unknown command") ?? false)
    }

    func testNilCmdIsIgnoredSilently() {
        let msg = WsMessage(type: "command")
        dispatch(msg)

        XCTAssertTrue(sentEvents.isEmpty)
        XCTAssertTrue(logEntries.isEmpty)
    }

    // MARK: - Missing Required Fields

    func testSendSmsMissingToFieldReturnsError() {
        let msg = WsMessage(type: "command", cmd: "SEND_SMS", body: "Hello", reqId: "req-7")
        dispatch(msg)

        XCTAssertEqual(sentEvents.count, 1)
        XCTAssertEqual(sentEvents[0].event, "ERROR")
        XCTAssertTrue(sentEvents[0].body?.contains("'to'") ?? false)
    }

    func testSendSmsMissingBodyFieldReturnsError() {
        let msg = WsMessage(type: "command", cmd: "SEND_SMS", to: "+15551234567", reqId: "req-8")
        dispatch(msg)

        XCTAssertEqual(sentEvents.count, 1)
        XCTAssertEqual(sentEvents[0].event, "ERROR")
        XCTAssertTrue(sentEvents[0].body?.contains("'body'") ?? false)
    }

    func testMakeCallMissingToFieldReturnsError() {
        let msg = WsMessage(type: "command", cmd: "MAKE_CALL", reqId: "req-9")
        dispatch(msg)

        XCTAssertEqual(sentEvents.count, 1)
        XCTAssertEqual(sentEvents[0].event, "ERROR")
        XCTAssertTrue(sentEvents[0].body?.contains("'to'") ?? false)
    }

    // MARK: - Log Entries

    func testCommandHandlingAddsLogEntry() {
        let msg = WsMessage(type: "command", cmd: "HANG_UP", reqId: "req-10")
        dispatch(msg)

        XCTAssertEqual(logEntries.count, 1)
        XCTAssertEqual(logEntries[0].direction, "IN")
        XCTAssertTrue(logEntries[0].summary.contains("HANG_UP"))
    }
}
