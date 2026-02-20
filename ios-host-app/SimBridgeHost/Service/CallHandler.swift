// CallHandler.swift
// SimBridgeHost
//
// Uses CallKit CXCallController for call placement and termination.
// Matches Android CallHandler.kt functionality via iOS CallKit APIs.

import Foundation
import CallKit
import os.log

final class CallHandler {

    private static let logger = Logger(subsystem: "com.simbridge.host", category: "CallHandler")

    private let callController = CXCallController()
    private let sendEvent: (WsMessage) -> Void

    /// Tracks the current active call UUID for hang-up.
    private var activeCallUUID: UUID?

    init(sendEvent: @escaping (WsMessage) -> Void) {
        self.sendEvent = sendEvent
    }

    /// Places an outgoing call to the specified number using CallKit.
    /// The simSlot parameter is accepted for API compatibility but iOS does not
    /// support programmatic SIM selection for calls.
    func makeCall(to: String, simSlot: Int?, reqId: String?) {
        let callUUID = UUID()
        activeCallUUID = callUUID

        let handle = CXHandle(type: .phoneNumber, value: to)
        let startCallAction = CXStartCallAction(call: callUUID, handle: handle)
        startCallAction.isVideo = false

        let transaction = CXTransaction(action: startCallAction)

        callController.request(transaction) { [weak self] error in
            guard let self = self else { return }
            if let error = error {
                Self.logger.error("Failed to place call to \(to): \(error.localizedDescription)")
                self.activeCallUUID = nil
                self.sendEvent(WsMessage(
                    type: "event",
                    event: "CALL_STATE",
                    body: error.localizedDescription,
                    state: "error",
                    reqId: reqId
                ))
            } else {
                Self.logger.info("Call placed to \(to) via SIM slot \(simSlot.map(String.init) ?? "default")")
                self.sendEvent(WsMessage(
                    type: "event",
                    event: "CALL_STATE",
                    state: "dialing",
                    sim: simSlot,
                    reqId: reqId
                ))
            }
        }
    }

    /// Ends the current active call.
    func hangUp(reqId: String?) {
        guard let callUUID = activeCallUUID else {
            Self.logger.warning("No active call to hang up")
            sendEvent(WsMessage(
                type: "event",
                event: "CALL_STATE",
                body: "No active call",
                state: "error",
                reqId: reqId
            ))
            return
        }

        let endCallAction = CXEndCallAction(call: callUUID)
        let transaction = CXTransaction(action: endCallAction)

        callController.request(transaction) { [weak self] error in
            guard let self = self else { return }
            if let error = error {
                Self.logger.error("Failed to hang up: \(error.localizedDescription)")
            } else {
                Self.logger.info("Call ended")
                self.activeCallUUID = nil
                self.sendEvent(WsMessage(
                    type: "event",
                    event: "CALL_STATE",
                    state: "ended",
                    reqId: reqId
                ))
            }
        }
    }

    /// Called by CallManager when call state changes externally.
    func reportCallStateChanged(_ state: String) {
        if state == "ended" {
            activeCallUUID = nil
        }
        sendEvent(WsMessage(
            type: "event",
            event: "CALL_STATE",
            state: state
        ))
    }
}
