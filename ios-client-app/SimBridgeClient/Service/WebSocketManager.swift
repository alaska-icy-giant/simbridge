// WebSocketManager.swift
// URLSessionWebSocketTask-based WebSocket manager with exponential backoff reconnect.

import Foundation

final class WebSocketManager: NSObject {

    // MARK: - Configuration

    private let serverUrl: String
    private let deviceId: Int
    private let token: String

    // MARK: - Reconnection

    private let maxBackoff: TimeInterval = 30
    private let backoffBase: TimeInterval = 1
    private var reconnectAttempt: Int = 0
    private var shouldReconnect: Bool = false

    // MARK: - Ping

    private let pingInterval: TimeInterval = 30
    private var pingTimer: Timer?

    // MARK: - Connection

    private var webSocketTask: URLSessionWebSocketTask?
    private var session: URLSession?
    private var currentStatus: ConnectionStatus = .disconnected

    // MARK: - Callbacks

    var onMessage: ((WsMessage) -> Void)?
    var onStatusChange: ((ConnectionStatus) -> Void)?

    // MARK: - Init

    init(serverUrl: String, deviceId: Int, token: String) {
        self.serverUrl = serverUrl
        self.deviceId = deviceId
        self.token = token
        super.init()
    }

    // MARK: - Public

    func connect() {
        shouldReconnect = true
        reconnectAttempt = 0
        establishConnection()
    }

    func disconnect() {
        shouldReconnect = false
        stopPingTimer()
        webSocketTask?.cancel(with: .goingAway, reason: nil)
        webSocketTask = nil
        session?.invalidateAndCancel()
        session = nil
        updateStatus(.disconnected)
    }

    func send(_ message: WsMessage) {
        guard let task = webSocketTask else { return }
        do {
            let data = try JSONEncoder().encode(message)
            let string = String(data: data, encoding: .utf8) ?? "{}"
            task.send(.string(string)) { error in
                if let error = error {
                    print("[WS] Send error: \(error.localizedDescription)")
                }
            }
        } catch {
            print("[WS] Encode error: \(error.localizedDescription)")
        }
    }

    // MARK: - Private

    private func establishConnection() {
        updateStatus(.connecting)

        // Build WebSocket URL: ws(s)://host/ws/client/{id}?token=jwt
        var wsUrl = serverUrl
            .replacingOccurrences(of: "https://", with: "wss://")
            .replacingOccurrences(of: "http://", with: "ws://")
        if wsUrl.hasSuffix("/") {
            wsUrl = String(wsUrl.dropLast())
        }
        wsUrl += "/ws/client/\(deviceId)?token=\(token)"

        guard let url = URL(string: wsUrl) else {
            print("[WS] Invalid URL: \(wsUrl)")
            updateStatus(.disconnected)
            return
        }

        let config = URLSessionConfiguration.default
        session = URLSession(configuration: config, delegate: self, delegateQueue: .main)
        webSocketTask = session?.webSocketTask(with: url)
        webSocketTask?.resume()

        startReceiving()
    }

    private func startReceiving() {
        webSocketTask?.receive { [weak self] result in
            guard let self = self else { return }

            switch result {
            case .success(let message):
                self.handleReceived(message)
                self.startReceiving() // continue listening

            case .failure(let error):
                print("[WS] Receive error: \(error.localizedDescription)")
                self.handleDisconnect()
            }
        }
    }

    private func handleReceived(_ message: URLSessionWebSocketTask.Message) {
        switch message {
        case .string(let text):
            guard let data = text.data(using: .utf8) else { return }
            do {
                let wsMessage = try JSONDecoder().decode(WsMessage.self, from: data)

                // Handle "connected" message from server
                if wsMessage.type == "connected" {
                    reconnectAttempt = 0
                    updateStatus(.connected)
                    startPingTimer()
                    return
                }

                // Handle pong
                if wsMessage.type == "pong" {
                    return
                }

                onMessage?(wsMessage)
            } catch {
                print("[WS] Decode error: \(error.localizedDescription) for: \(text)")
            }

        case .data(let data):
            do {
                let wsMessage = try JSONDecoder().decode(WsMessage.self, from: data)
                onMessage?(wsMessage)
            } catch {
                print("[WS] Decode binary error: \(error.localizedDescription)")
            }

        @unknown default:
            break
        }
    }

    private func handleDisconnect() {
        stopPingTimer()
        updateStatus(.disconnected)
        scheduleReconnect()
    }

    private func scheduleReconnect() {
        guard shouldReconnect else { return }

        let delay = min(maxBackoff, backoffBase * pow(2.0, Double(reconnectAttempt)))
        reconnectAttempt += 1

        print("[WS] Reconnecting in \(delay)s (attempt \(reconnectAttempt))")
        updateStatus(.connecting)

        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
            guard let self = self, self.shouldReconnect else { return }
            self.webSocketTask?.cancel(with: .goingAway, reason: nil)
            self.webSocketTask = nil
            self.session?.invalidateAndCancel()
            self.session = nil
            self.establishConnection()
        }
    }

    // MARK: - Ping

    private func startPingTimer() {
        stopPingTimer()
        pingTimer = Timer.scheduledTimer(withTimeInterval: pingInterval, repeats: true) { [weak self] _ in
            self?.sendPing()
        }
    }

    private func stopPingTimer() {
        pingTimer?.invalidate()
        pingTimer = nil
    }

    private func sendPing() {
        guard let task = webSocketTask else { return }
        let pingMessage = "{\"type\":\"ping\"}"
        task.send(.string(pingMessage)) { error in
            if let error = error {
                print("[WS] Ping error: \(error.localizedDescription)")
            }
        }
    }

    // MARK: - Status

    private func updateStatus(_ status: ConnectionStatus) {
        guard currentStatus != status else { return }
        currentStatus = status
        onStatusChange?(status)
    }
}

// MARK: - URLSessionWebSocketDelegate

extension WebSocketManager: URLSessionWebSocketDelegate {

    func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didOpenWithProtocol protocol: String?
    ) {
        print("[WS] Connection opened")
    }

    func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didCloseWith closeCode: URLSessionWebSocketTask.CloseCode,
        reason: Data?
    ) {
        print("[WS] Connection closed: \(closeCode)")
        handleDisconnect()
    }
}
