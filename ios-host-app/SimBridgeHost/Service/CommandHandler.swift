// CommandHandler.swift
// SimBridgeHost
//
// Dispatches incoming command messages to the appropriate handler.
// Matches Android CommandHandler.kt dispatch table:
//   SEND_SMS  -> SmsHandler
//   MAKE_CALL -> CallHandler
//   HANG_UP   -> CallHandler
//   GET_SIMS  -> SimInfoProvider

import Foundation
import os.log

final class CommandHandler {

    private static let logger = Logger(subsystem: "com.simbridge.host", category: "CommandHandler")

    private let smsHandler: SmsHandler
    private let callHandler: CallHandler
    private let simInfoProvider: SimInfoProvider
    private let sendEvent: (WsMessage) -> Void
    private let addLog: (LogEntry) -> Void

    init(
        smsHandler: SmsHandler,
        callHandler: CallHandler,
        simInfoProvider: SimInfoProvider,
        sendEvent: @escaping (WsMessage) -> Void,
        addLog: @escaping (LogEntry) -> Void
    ) {
        self.smsHandler = smsHandler
        self.callHandler = callHandler
        self.simInfoProvider = simInfoProvider
        self.sendEvent = sendEvent
        self.addLog = addLog
    }

    /// Dispatches an incoming command message to the appropriate handler.
    func handleCommand(_ message: WsMessage) {
        guard let cmd = message.cmd else { return }
        addLog(LogEntry(direction: "IN", summary: "CMD: \(cmd) \(message.to ?? "")"))
        Self.logger.info("Handling command: \(cmd)")

        switch cmd {
        case "SEND_SMS":
            guard let to = message.to, let body = message.body else {
                sendError(reqId: message.reqId, errorMsg: "SEND_SMS requires 'to' and 'body'")
                return
            }
            smsHandler.sendSms(to: to, body: body, simSlot: message.sim, reqId: message.reqId)

        case "MAKE_CALL":
            guard let to = message.to else {
                sendError(reqId: message.reqId, errorMsg: "MAKE_CALL requires 'to'")
                return
            }
            callHandler.makeCall(to: to, simSlot: message.sim, reqId: message.reqId)

        case "HANG_UP":
            callHandler.hangUp(reqId: message.reqId)

        case "GET_SIMS":
            let sims = simInfoProvider.getActiveSimCards()
            sendEvent(WsMessage(
                type: "event",
                event: "SIM_INFO",
                sims: sims,
                reqId: message.reqId
            ))

        default:
            Self.logger.warning("Unknown command: \(cmd)")
            sendError(reqId: message.reqId, errorMsg: "Unknown command: \(cmd)")
        }
    }

    private func sendError(reqId: String?, errorMsg: String) {
        sendEvent(WsMessage(
            type: "event",
            event: "ERROR",
            body: errorMsg,
            status: "error",
            reqId: reqId
        ))
    }
}
