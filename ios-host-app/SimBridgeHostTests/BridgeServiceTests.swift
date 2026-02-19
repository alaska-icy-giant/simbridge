import XCTest
@testable import SimBridgeHost

/// Tests for BridgeService coordinator lifecycle.
final class BridgeServiceTests: XCTestCase {

    // MARK: - Log Management Tests

    private var logs: [LogEntry]!
    private let maxLogEntries = 100

    override func setUp() {
        super.setUp()
        logs = []
    }

    override func tearDown() {
        logs = nil
        super.tearDown()
    }

    private func addLog(_ entry: LogEntry) {
        logs.insert(entry, at: 0)
        if logs.count > maxLogEntries {
            logs.removeLast()
        }
    }

    // MARK: - Service Starts WebSocket

    func testServiceStartsWebSocket() {
        let mockWs = MockWebSocketManager()
        mockWs.connect()

        XCTAssertEqual(mockWs.connectCallCount, 1)
        XCTAssertEqual(mockWs.connectionStatus, .connected)
    }

    // MARK: - Service Stops Cleanly

    func testServiceStopsCleanly() {
        let mockWs = MockWebSocketManager()
        mockWs.connect()
        XCTAssertEqual(mockWs.connectionStatus, .connected)

        mockWs.disconnect()

        XCTAssertEqual(mockWs.disconnectCallCount, 1)
        XCTAssertEqual(mockWs.connectionStatus, .disconnected)
    }

    func testServiceStopResetsState() {
        let mockWs = MockWebSocketManager()
        mockWs.connect()
        mockWs.send(WsMessage(type: "event", event: "test"))

        mockWs.disconnect()

        XCTAssertEqual(mockWs.connectionStatus, .disconnected)
    }

    // MARK: - Log Entries Capped at 100

    func testLogEntriesCappedAt100() {
        for i in 0..<101 {
            addLog(LogEntry(direction: "IN", summary: "Entry \(i)"))
        }

        XCTAssertEqual(logs.count, 100)
    }

    func test101stEntryEvictsTheOldest() {
        for i in 0..<100 {
            addLog(LogEntry(direction: "IN", summary: "Entry \(i)"))
        }
        XCTAssertEqual(logs.last?.summary, "Entry 0") // oldest at end

        addLog(LogEntry(direction: "IN", summary: "Entry 100"))

        XCTAssertEqual(logs.count, 100)
        XCTAssertEqual(logs.first?.summary, "Entry 100") // newest at front
        // Entry 0 (oldest) should have been evicted
        XCTAssertFalse(logs.contains(where: { $0.summary == "Entry 0" }))
    }

    func testNewestLogEntryIsAlwaysAtIndex0() {
        addLog(LogEntry(direction: "OUT", summary: "First"))
        addLog(LogEntry(direction: "IN", summary: "Second"))

        XCTAssertEqual(logs[0].summary, "Second")
        XCTAssertEqual(logs[1].summary, "First")
    }

    // MARK: - Status Callback Fires

    func testStatusCallbackFiresOnStateChange() {
        let mockWs = MockWebSocketManager()
        var receivedStatuses: [ConnectionStatus] = []
        mockWs.onStatusChange = { status in
            receivedStatuses.append(status)
        }

        mockWs.connect()   // triggers .connecting, .connected
        mockWs.disconnect() // triggers .disconnected

        XCTAssertEqual(receivedStatuses, [.connecting, .connected, .disconnected])
    }

    func testServiceStatusStartsAsDisconnected() {
        let mockWs = MockWebSocketManager()
        XCTAssertEqual(mockWs.connectionStatus, .disconnected)
    }

    // MARK: - Reconnect Lifecycle

    func testReconnectCallsDisconnectThenConnect() {
        let mockWs = MockWebSocketManager()
        mockWs.connect()

        // Simulate reconnect: disconnect then connect
        mockWs.disconnect()
        mockWs.connect()

        XCTAssertEqual(mockWs.connectCallCount, 2)
        XCTAssertEqual(mockWs.disconnectCallCount, 1)
        XCTAssertEqual(mockWs.connectionStatus, .connected)
    }

    // MARK: - Log Entry Properties

    func testLogEntryHasDirectionAndSummary() {
        let entry = LogEntry(direction: "IN", summary: "Test message")

        XCTAssertEqual(entry.direction, "IN")
        XCTAssertEqual(entry.summary, "Test message")
    }

    func testLogDirectionsAreInOrOut() {
        let inEntry = LogEntry(direction: "IN", summary: "Incoming")
        let outEntry = LogEntry(direction: "OUT", summary: "Outgoing")

        XCTAssertEqual(inEntry.direction, "IN")
        XCTAssertEqual(outEntry.direction, "OUT")
    }
}
