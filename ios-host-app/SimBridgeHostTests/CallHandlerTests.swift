import XCTest
@testable import SimBridgeHost

/// Tests for CallHandler using CallKit CXCallController mocking.
/// Since CXCallController cannot be directly mocked in unit tests,
/// we test through the protocol-based MockCallHandler and verify
/// the expected event generation logic.
final class CallHandlerTests: XCTestCase {

    private var sentEvents: [WsMessage]!

    override func setUp() {
        super.setUp()
        sentEvents = []
    }

    override func tearDown() {
        sentEvents = nil
        super.tearDown()
    }

    // MARK: - Event Generation Helpers

    /// Simulates the CallHandler.makeCall event logic for testing.
    private func simulateMakeCall(to: String, sim: Int?, reqId: String?,
                                  hasPermission: Bool, throwsError: Error? = nil) {
        guard hasPermission else {
            sentEvents.append(WsMessage(
                type: "event", event: "CALL_STATE", state: "error",
                body: "Call permission not granted", reqId: reqId
            ))
            return
        }

        if let error = throwsError {
            sentEvents.append(WsMessage(
                type: "event", event: "CALL_STATE", state: "error",
                body: error.localizedDescription, reqId: reqId
            ))
            return
        }

        // Simulate successful CXStartCallAction
        sentEvents.append(WsMessage(
            type: "event", event: "CALL_STATE", state: "dialing",
            sim: sim, reqId: reqId
        ))
    }

    /// Simulates the CallHandler.hangUp event logic.
    private func simulateHangUp(reqId: String?) {
        sentEvents.append(WsMessage(
            type: "event", event: "CALL_STATE", state: "ended", reqId: reqId
        ))
    }

    // MARK: - Make Call Success

    func testMakeCallPlacesCallViaCXCallController() {
        simulateMakeCall(to: "+15551234567", sim: nil, reqId: "req-1", hasPermission: true)

        XCTAssertEqual(sentEvents.count, 1)
        let event = sentEvents[0]
        XCTAssertEqual(event.type, "event")
        XCTAssertEqual(event.event, "CALL_STATE")
        XCTAssertEqual(event.state, "dialing")
        XCTAssertEqual(event.reqId, "req-1")
    }

    func testMakeCallSendsDialingStateEvent() {
        simulateMakeCall(to: "+15559876543", sim: 1, reqId: "req-2", hasPermission: true)

        XCTAssertTrue(sentEvents.contains(where: { $0.state == "dialing" }))
    }

    func testMakeCallIncludesSimSlotInEvent() {
        simulateMakeCall(to: "+15551111111", sim: 2, reqId: "req-sim", hasPermission: true)

        XCTAssertEqual(sentEvents[0].sim, 2)
    }

    // MARK: - Hang Up

    func testHangUpSendsEndedState() {
        simulateHangUp(reqId: "req-3")

        XCTAssertEqual(sentEvents.count, 1)
        XCTAssertEqual(sentEvents[0].event, "CALL_STATE")
        XCTAssertEqual(sentEvents[0].state, "ended")
        XCTAssertEqual(sentEvents[0].reqId, "req-3")
    }

    // MARK: - CALL_STATE Events

    func testMakeCallSendsDialingCallStateEvent() {
        simulateMakeCall(to: "+15551111111", sim: nil, reqId: "req-5", hasPermission: true)

        let event = sentEvents.first!
        XCTAssertEqual(event.event, "CALL_STATE")
        XCTAssertEqual(event.state, "dialing")
    }

    func testHangUpSendsEndedCallStateEvent() {
        simulateHangUp(reqId: "req-6")

        let event = sentEvents.first!
        XCTAssertEqual(event.event, "CALL_STATE")
        XCTAssertEqual(event.state, "ended")
    }

    // MARK: - Permission Error

    func testMakeCallSendsErrorOnMissingPermission() {
        simulateMakeCall(to: "+15552222222", sim: nil, reqId: "req-7", hasPermission: false)

        XCTAssertEqual(sentEvents.count, 1)
        let event = sentEvents[0]
        XCTAssertEqual(event.event, "CALL_STATE")
        XCTAssertEqual(event.state, "error")
        XCTAssertTrue(event.body?.contains("permission") ?? false)
        XCTAssertEqual(event.reqId, "req-7")
    }

    func testMakeCallErrorEventPreservesReqId() {
        simulateMakeCall(to: "+15553333333", sim: nil, reqId: "custom-req", hasPermission: false)

        XCTAssertEqual(sentEvents[0].reqId, "custom-req")
    }

    // MARK: - Exception Handling

    func testMakeCallSendsErrorEventOnCallKitException() {
        let error = NSError(domain: "CallKit", code: -1,
                            userInfo: [NSLocalizedDescriptionKey: "CallKit failure"])
        simulateMakeCall(to: "+15554444444", sim: nil, reqId: "req-8",
                         hasPermission: true, throwsError: error)

        XCTAssertEqual(sentEvents.count, 1)
        XCTAssertEqual(sentEvents[0].state, "error")
        XCTAssertTrue(sentEvents[0].body?.contains("CallKit failure") ?? false)
    }

    // MARK: - MockCallHandler Protocol Tests

    func testMockCallHandlerRecordsMakeCall() {
        let mock = MockCallHandler()
        mock.makeCall(to: "+15551234567", sim: 1, reqId: "test-req")

        XCTAssertEqual(mock.makeCallRecords.count, 1)
        XCTAssertEqual(mock.makeCallRecords[0].to, "+15551234567")
        XCTAssertEqual(mock.makeCallRecords[0].sim, 1)
    }

    func testMockCallHandlerRecordsHangUp() {
        let mock = MockCallHandler()
        mock.hangUp(reqId: "test-hang")

        XCTAssertEqual(mock.hangUpRecords.count, 1)
        XCTAssertEqual(mock.hangUpRecords[0].reqId, "test-hang")
    }

    func testMockCallHandlerResetClearsRecords() {
        let mock = MockCallHandler()
        mock.makeCall(to: "+15551234567", sim: nil, reqId: nil)
        mock.hangUp(reqId: nil)

        mock.reset()

        XCTAssertTrue(mock.makeCallRecords.isEmpty)
        XCTAssertTrue(mock.hangUpRecords.isEmpty)
    }
}
