// WebSocketManager.swift
// SimBridgeHost
//
// URLSessionWebSocketTask with exponential backoff reconnect (1->2->4->8->16->30s cap),
// ping/pong keepalive, and published connection status. Matches Android WebSocketManager.kt.

import Foundation
import Combine
import os.log

final class WebSocketManager: ObservableObject {

    private static let logger = Logger(subsystem: "com.simbridge.host", category: "WebSocketManager")
    private static let pingIntervalSec: TimeInterval = 30
    private static let maxBackoffSec: Int = 30

    private let prefs: Prefs
    private let onMessage: (WsMessage) -> Void

    @Published private(set) var connectionStatus: ConnectionStatus = .disconnected

    private var webSocketTask: URLSessionWebSocketTask?
    private var pingTask: Task<Void, Never>?
    private var receiveTask: Task<Void, Never>?
    private var reconnectTask: Task<Void, Never>?
    private var retryCount: Int = 0
    private var intentionalClose = false

    private let session: URLSession

    init(prefs: Prefs, onMessage: @escaping (WsMessage) -> Void) {
        self.prefs = prefs
        self.onMessage = onMessage
        let config = URLSessionConfiguration.default
        config.waitsForConnectivity = true
        self.session = URLSession(configuration: config)
    }

    // MARK: - Public

    func connect() {
        intentionalClose = false
        doConnect()
    }

    func disconnect() {
        intentionalClose = true
        reconnectTask?.cancel()
        reconnectTask = nil
        pingTask?.cancel()
        pingTask = nil
        receiveTask?.cancel()
        receiveTask = nil
        webSocketTask?.cancel(with: .normalClosure, reason: "User disconnect".data(using: .utf8))
        webSocketTask = nil
        updateStatus(.disconnected)
    }

    func send(_ message: WsMessage) {
        guard let task = webSocketTask else { return }
        do {
            let data = try JSONEncoder().encode(message)
            let text = String(data: data, encoding: .utf8) ?? ""
            Self.logger.debug("TX: \(text)")
            task.send(.string(text)) { error in
                if let error = error {
                    Self.logger.error("Send failed: \(error.localizedDescription)")
                }
            }
        } catch {
            Self.logger.error("Encode failed: \(error.localizedDescription)")
        }
    }

    // MARK: - Private

    private func doConnect() {
        updateStatus(.connecting)

        let serverUrl = prefs.serverUrl
        let token = prefs.token
        let deviceId = prefs.deviceId

        guard !serverUrl.isEmpty, !token.isEmpty, deviceId >= 0 else {
            Self.logger.warning("No server URL, token, or device ID configured")
            updateStatus(.disconnected)
            return
        }

        // Convert http(s):// to ws(s):// and build the host device endpoint
        let wsUrl = serverUrl
            .replacingOccurrences(of: "https://", with: "wss://")
            .replacingOccurrences(of: "http://", with: "ws://")
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            + "/ws/host/\(deviceId)?token=\(token)"

        guard let url = URL(string: wsUrl) else {
            Self.logger.error("Invalid WebSocket URL: \(wsUrl)")
            updateStatus(.disconnected)
            return
        }

        Self.logger.info("Connecting to \(wsUrl)")
        let task = session.webSocketTask(with: url)
        webSocketTask = task
        task.resume()

        // Start listening for messages
        receiveTask = Task { [weak self] in
            await self?.receiveLoop()
        }

        // Start ping keepalive
        pingTask = Task { [weak self] in
            await self?.pingLoop()
        }

        // The first successful receive indicates connection is open.
        // URLSessionWebSocketTask doesn't have an onOpen callback, so we
        // treat a successful ping as the "connected" signal.
        Task { [weak self] in
            guard let self = self else { return }
            do {
                try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                    task.sendPing { error in
                        if let error = error {
                            continuation.resume(throwing: error)
                        } else {
                            continuation.resume()
                        }
                    }
                }
                self.retryCount = 0
                self.updateStatus(.connected)
            } catch {
                Self.logger.error("Initial ping failed: \(error.localizedDescription)")
                self.handleDisconnect()
            }
        }
    }

    private func receiveLoop() async {
        guard let task = webSocketTask else { return }
        let decoder = JSONDecoder()

        while !Task.isCancelled {
            do {
                let message = try await task.receive()
                switch message {
                case .string(let text):
                    Self.logger.debug("RX: \(text)")
                    if let data = text.data(using: .utf8),
                       let wsMessage = try? decoder.decode(WsMessage.self, from: data) {
                        await MainActor.run {
                            self.onMessage(wsMessage)
                        }
                    } else {
                        Self.logger.error("Failed to parse message: \(text)")
                    }
                case .data(let data):
                    if let wsMessage = try? decoder.decode(WsMessage.self, from: data) {
                        await MainActor.run {
                            self.onMessage(wsMessage)
                        }
                    }
                @unknown default:
                    break
                }
            } catch {
                if !Task.isCancelled && !intentionalClose {
                    Self.logger.error("Receive error: \(error.localizedDescription)")
                    handleDisconnect()
                }
                return
            }
        }
    }

    private func pingLoop() async {
        while !Task.isCancelled {
            try? await Task.sleep(nanoseconds: UInt64(Self.pingIntervalSec * 1_000_000_000))
            guard !Task.isCancelled, let task = webSocketTask else { return }
            task.sendPing { [weak self] error in
                if let error = error {
                    Self.logger.error("Ping failed: \(error.localizedDescription)")
                    self?.handleDisconnect()
                }
            }
        }
    }

    private func handleDisconnect() {
        webSocketTask?.cancel(with: .abnormalClosure, reason: nil)
        webSocketTask = nil
        receiveTask?.cancel()
        pingTask?.cancel()

        guard !intentionalClose else {
            updateStatus(.disconnected)
            return
        }

        scheduleReconnect()
    }

    private func scheduleReconnect() {
        guard !intentionalClose else { return }

        let attempt = retryCount
        retryCount += 1
        let delaySec = min(1 << attempt, Self.maxBackoffSec)

        Self.logger.info("Reconnecting in \(delaySec)s (attempt \(attempt + 1))")
        updateStatus(.connecting)

        reconnectTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(delaySec) * 1_000_000_000)
            guard !Task.isCancelled else { return }
            self?.doConnect()
        }
    }

    private func updateStatus(_ status: ConnectionStatus) {
        Task { @MainActor [weak self] in
            self?.connectionStatus = status
        }
    }
}
