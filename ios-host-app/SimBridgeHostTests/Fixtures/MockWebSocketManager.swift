import Foundation
@testable import SimBridgeHost

// MARK: - WebSocketManagerProtocol

/// Protocol extracted from WebSocketManager for testability.
protocol WebSocketManagerProtocol: AnyObject {
    var connectionStatus: ConnectionStatus { get }
    var onMessage: ((WsMessage) -> Void)? { get set }
    var onStatusChange: ((ConnectionStatus) -> Void)? { get set }

    func connect()
    func disconnect()
    func send(_ message: WsMessage)
}

// MARK: - MockWebSocketManager

/// Recording mock for WebSocketManager. Captures all method calls for assertion.
final class MockWebSocketManager: WebSocketManagerProtocol {

    // MARK: - State

    private(set) var connectionStatus: ConnectionStatus = .disconnected

    var onMessage: ((WsMessage) -> Void)?
    var onStatusChange: ((ConnectionStatus) -> Void)?

    // MARK: - Call Records

    private(set) var connectCallCount = 0
    private(set) var disconnectCallCount = 0
    private(set) var sentMessages: [WsMessage] = []

    // MARK: - Configurable Behavior

    var shouldAutoConnect = true

    // MARK: - Protocol Methods

    func connect() {
        connectCallCount += 1
        if shouldAutoConnect {
            connectionStatus = .connecting
            onStatusChange?(.connecting)
            connectionStatus = .connected
            onStatusChange?(.connected)
        }
    }

    func disconnect() {
        disconnectCallCount += 1
        connectionStatus = .disconnected
        onStatusChange?(.disconnected)
    }

    func send(_ message: WsMessage) {
        sentMessages.append(message)
    }

    // MARK: - Test Helpers

    /// Simulates receiving a message from the server.
    func simulateIncomingMessage(_ message: WsMessage) {
        onMessage?(message)
    }

    /// Simulates a connection drop.
    func simulateConnectionDrop() {
        connectionStatus = .disconnected
        onStatusChange?(.disconnected)
    }

    func reset() {
        connectCallCount = 0
        disconnectCallCount = 0
        sentMessages.removeAll()
        connectionStatus = .disconnected
    }
}
