import Foundation
import Combine

// MARK: - WebSocketManagerProtocol

/// Protocol for the WebSocket manager so we can substitute a mock.
protocol WebSocketManagerProtocol: AnyObject {
    var connectionStatus: ConnectionStatus { get }
    var connectionStatusPublisher: Published<ConnectionStatus>.Publisher { get }
    var onMessage: ((WsMessage) -> Void)? { get set }

    func connect(deviceId: Int, token: String)
    func disconnect()
    func send(_ message: Data) async throws
}

// MARK: - MockWebSocketManager

/// Mock that records sent messages and allows simulating incoming events.
final class MockWebSocketManager: ObservableObject, WebSocketManagerProtocol {

    @Published var connectionStatus: ConnectionStatus = .disconnected
    var connectionStatusPublisher: Published<ConnectionStatus>.Publisher { $connectionStatus }

    var onMessage: ((WsMessage) -> Void)?

    // MARK: - Recording

    struct ConnectCall {
        let deviceId: Int
        let token: String
    }

    private(set) var connectCalls: [ConnectCall] = []
    private(set) var disconnectCallCount: Int = 0
    private(set) var sentMessages: [Data] = []

    // MARK: - WebSocketManagerProtocol

    func connect(deviceId: Int, token: String) {
        connectCalls.append(ConnectCall(deviceId: deviceId, token: token))
        connectionStatus = .connected
    }

    func disconnect() {
        disconnectCallCount += 1
        connectionStatus = .disconnected
    }

    func send(_ message: Data) async throws {
        sentMessages.append(message)
    }

    // MARK: - Simulation helpers

    /// Simulate an incoming WebSocket message.
    func simulateIncoming(_ message: WsMessage) {
        onMessage?(message)
    }

    /// Simulate an incoming raw JSON string, decoding it to WsMessage.
    func simulateIncomingJSON(_ json: String) {
        guard let data = json.data(using: .utf8),
              let message = try? JSONDecoder().decode(WsMessage.self, from: data) else {
            return
        }
        onMessage?(message)
    }

    /// Simulate a connection status change.
    func simulateStatusChange(_ status: ConnectionStatus) {
        connectionStatus = status
    }

    func reset() {
        connectCalls = []
        disconnectCallCount = 0
        sentMessages = []
        connectionStatus = .disconnected
        onMessage = nil
    }
}
