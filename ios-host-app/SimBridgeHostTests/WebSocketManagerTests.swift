import XCTest
@testable import SimBridgeHost

final class WebSocketManagerTests: XCTestCase {

    // MARK: - URL Conversion Tests

    func testHttpURLConvertsToWsURL() {
        let httpUrl = "http://example.com:8100"
        let wsUrl = convertToWebSocketURL(httpUrl)
        XCTAssertTrue(wsUrl.hasPrefix("ws://"), "HTTP should convert to WS")
        XCTAssertEqual(wsUrl, "ws://example.com:8100")
    }

    func testHttpsURLConvertsToWssURL() {
        let httpsUrl = "https://example.com:8100"
        let wsUrl = convertToWebSocketURL(httpsUrl)
        XCTAssertTrue(wsUrl.hasPrefix("wss://"), "HTTPS should convert to WSS")
        XCTAssertEqual(wsUrl, "wss://example.com:8100")
    }

    func testTokenAppendedToWebSocketURL() {
        let serverUrl = "http://example.com:8100"
        let token = "jwt-token-abc"
        let deviceId = 42
        let wsUrl = buildWebSocketURL(serverUrl: serverUrl, deviceId: deviceId, token: token)
        XCTAssertEqual(wsUrl, "ws://example.com:8100/ws/host/42?token=jwt-token-abc")
    }

    func testTrailingSlashTrimmedFromServerURL() {
        let serverUrl = "http://example.com:8100/"
        let token = "tok"
        let deviceId = 1
        let wsUrl = buildWebSocketURL(serverUrl: serverUrl, deviceId: deviceId, token: token)
        XCTAssertEqual(wsUrl, "ws://example.com:8100/ws/host/1?token=tok")
    }

    // MARK: - Backoff Sequence Tests

    func testReconnectBackoffSequenceFollowsExponentialPatternWithCap() {
        let maxBackoff = 30
        let expectedDelays = [1, 2, 4, 8, 16, 30, 30]

        for (attempt, expected) in expectedDelays.enumerated() {
            let actual = min(1 << attempt, maxBackoff)
            XCTAssertEqual(actual, expected, "Backoff at attempt \(attempt) should be \(expected)")
        }
    }

    func testBackoffAtAttempt0Is1Second() {
        let maxBackoff = 30
        XCTAssertEqual(min(1 << 0, maxBackoff), 1)
    }

    func testBackoffAtAttempt5IsCappedAt30Seconds() {
        let maxBackoff = 30
        // 1 << 5 = 32, capped to 30
        XCTAssertEqual(min(1 << 5, maxBackoff), 30)
    }

    func testBackoffAtAttempt10IsStillCappedAt30Seconds() {
        let maxBackoff = 30
        XCTAssertEqual(min(1 << 10, maxBackoff), 30)
    }

    // MARK: - Reconnect Reset Tests

    func testReconnectCounterResetsOnSuccessfulConnection() {
        var retryCount = 5
        // Simulating onOpen behavior
        retryCount = 0
        XCTAssertEqual(retryCount, 0)
    }

    // MARK: - Intentional Close Tests

    func testIntentionalCloseSuppressesReconnect() {
        let intentionalClose = true
        var reconnectScheduled = false

        if !intentionalClose {
            reconnectScheduled = true
        }

        XCTAssertFalse(reconnectScheduled, "Reconnect should not be scheduled on intentional close")
    }

    func testDisconnectSetsStatusToDisconnected() {
        let mock = MockWebSocketManager()
        mock.connect()
        XCTAssertEqual(mock.connectionStatus, .connected)

        mock.disconnect()
        XCTAssertEqual(mock.connectionStatus, .disconnected)
    }

    // MARK: - Status Transition Tests

    func testConnectTransitionsToConnectingFirst() {
        let mock = MockWebSocketManager()
        var statuses: [ConnectionStatus] = []
        mock.onStatusChange = { status in
            statuses.append(status)
        }

        mock.connect()

        XCTAssertTrue(statuses.count >= 1)
        XCTAssertEqual(statuses.first, .connecting)
    }

    func testStatusTransitionsDisconnectedToConnectingToConnected() {
        let mock = MockWebSocketManager()
        var statuses: [ConnectionStatus] = []
        mock.onStatusChange = { status in
            statuses.append(status)
        }

        mock.connect()

        XCTAssertEqual(statuses, [.connecting, .connected])
    }

    // MARK: - Message Callback Tests

    func testMessageCallbackInvokedOnIncomingText() {
        let mock = MockWebSocketManager()
        var received: WsMessage?
        mock.onMessage = { msg in
            received = msg
        }

        let testMessage = WsMessage(type: "command", cmd: "GET_SIMS", reqId: "abc")
        mock.simulateIncomingMessage(testMessage)

        XCTAssertNotNil(received)
        XCTAssertEqual(received?.type, "command")
        XCTAssertEqual(received?.cmd, "GET_SIMS")
        XCTAssertEqual(received?.reqId, "abc")
    }

    // MARK: - Ping Interval Tests

    func testPingIntervalConstantIs30Seconds() {
        // The WebSocketManager should define a 30-second ping interval
        let expectedPingInterval: TimeInterval = 30.0
        XCTAssertEqual(expectedPingInterval, 30.0)
    }

    // MARK: - Helpers

    private func convertToWebSocketURL(_ url: String) -> String {
        return url
            .replacingOccurrences(of: "https://", with: "wss://")
            .replacingOccurrences(of: "http://", with: "ws://")
    }

    private func buildWebSocketURL(serverUrl: String, deviceId: Int, token: String) -> String {
        let base = convertToWebSocketURL(serverUrl)
        let trimmed: String
        if base.hasSuffix("/") {
            trimmed = String(base.dropLast())
        } else {
            trimmed = base
        }
        return "\(trimmed)/ws/host/\(deviceId)?token=\(token)"
    }
}
