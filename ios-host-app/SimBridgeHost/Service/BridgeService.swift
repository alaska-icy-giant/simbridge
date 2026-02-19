// BridgeService.swift
// SimBridgeHost
//
// Central coordinator that owns WebSocketManager, command handlers, and publishes
// observable state. Matches Android BridgeService.kt (without the foreground service
// aspects, which are not directly available on iOS).

import Foundation
import Combine
import os.log

@MainActor
final class BridgeService: ObservableObject {

    private static let logger = Logger(subsystem: "com.simbridge.host", category: "BridgeService")
    private static let maxLogEntries = 100

    // MARK: - Published State

    @Published private(set) var connectionStatus: ConnectionStatus = .disconnected
    @Published private(set) var logs: [LogEntry] = []
    @Published private(set) var isRunning = false

    // MARK: - Dependencies

    private let prefs: Prefs
    private var wsManager: WebSocketManager?
    private var commandHandler: CommandHandler?
    private var smsHandler: SmsHandler?
    private var callHandler: CallHandler?
    private var simInfoProvider: SimInfoProvider?
    private var webRtcManager: WebRtcManager?
    private var signalingHandler: SignalingHandler?
    private var smsReceiver: SmsReceiver?

    private var cancellables = Set<AnyCancellable>()

    init(prefs: Prefs) {
        self.prefs = prefs
    }

    // MARK: - Lifecycle

    func start() {
        guard !isRunning else { return }
        Self.logger.info("Service starting")

        // SIM info
        let simInfo = SimInfoProvider()
        simInfoProvider = simInfo

        // WebSocket
        let ws = WebSocketManager(prefs: prefs, onMessage: { [weak self] message in
            self?.handleWsMessage(message)
        })
        wsManager = ws

        // Observe connection status
        ws.$connectionStatus
            .receive(on: DispatchQueue.main)
            .sink { [weak self] status in
                self?.connectionStatus = status
            }
            .store(in: &cancellables)

        // SMS handler
        let sms = SmsHandler(simInfoProvider: simInfo, sendEvent: { [weak self] event in
            self?.sendEvent(event)
        })
        smsHandler = sms

        // Call handler
        let call = CallHandler(sendEvent: { [weak self] event in
            self?.sendEvent(event)
        })
        callHandler = call

        // Command dispatcher
        commandHandler = CommandHandler(
            smsHandler: sms,
            callHandler: call,
            simInfoProvider: simInfo,
            sendEvent: { [weak self] event in self?.sendEvent(event) },
            addLog: { [weak self] entry in self?.addLog(entry) }
        )

        // WebRTC
        let rtc = WebRtcManager()
        webRtcManager = rtc
        signalingHandler = SignalingHandler(webRtcManager: rtc, sendMessage: { [weak self] msg in
            self?.wsManager?.send(msg)
        })

        // SMS receiver (documents iOS limitation)
        let receiver = SmsReceiver()
        receiver.onSmsReceived = { [weak self] event in
            self?.addLog(LogEntry(direction: "IN", summary: "SMS from \(event.from ?? "unknown")"))
            self?.sendEvent(event)
        }
        smsReceiver = receiver

        // Connect
        ws.connect()
        isRunning = true
    }

    func stop() {
        guard isRunning else { return }
        Self.logger.info("Service stopping")

        wsManager?.disconnect()
        webRtcManager?.dispose()
        cancellables.removeAll()

        wsManager = nil
        commandHandler = nil
        smsHandler = nil
        callHandler = nil
        simInfoProvider = nil
        webRtcManager = nil
        signalingHandler = nil
        smsReceiver = nil

        connectionStatus = .disconnected
        isRunning = false
    }

    func reconnect() {
        wsManager?.disconnect()
        wsManager?.connect()
    }

    // MARK: - Message Handling

    private func handleWsMessage(_ message: WsMessage) {
        switch message.type {
        case "command":
            commandHandler?.handleCommand(message)
        case "webrtc":
            signalingHandler?.handleSignaling(message)
        default:
            Self.logger.warning("Unknown message type: \(message.type)")
        }
    }

    private func sendEvent(_ event: WsMessage) {
        addLog(LogEntry(direction: "OUT", summary: "\(event.event ?? "") \(event.status ?? "")"))
        wsManager?.send(event)
    }

    func addLog(_ entry: LogEntry) {
        logs.insert(entry, at: 0)
        if logs.count > Self.maxLogEntries {
            logs.removeLast()
        }
    }

    /// Returns current SIM info for external consumers (e.g., DashboardView).
    func getSimCards() -> [SimInfo] {
        return simInfoProvider?.getActiveSimCards() ?? []
    }
}
