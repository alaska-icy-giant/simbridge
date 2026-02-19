// EventHandler.swift
// Process incoming WebSocket events from the paired Host device.

import Foundation
import UserNotifications
import SwiftUI

@MainActor
final class EventHandler {

    private weak var appState: AppState?

    init(appState: AppState) {
        self.appState = appState
    }

    func handle(message: WsMessage) {
        Task { @MainActor in
            self.processMessage(message)
        }
    }

    private func processMessage(_ message: WsMessage) {
        guard let appState = appState else { return }

        let type = message.type ?? message.cmd ?? ""

        switch type.uppercased() {
        case "INCOMING_SMS":
            handleIncomingSms(message)

        case "SMS_SENT":
            handleSmsSent(message)

        case "CALL_STATE":
            handleCallState(message)

        case "SIM_INFO":
            handleSimInfo(message)

        case "ERROR":
            handleError(message)

        default:
            // Unknown event type — log it to the feed
            appState.addEvent(
                icon: "info.circle.fill",
                title: "Event",
                detail: type.isEmpty ? "Unknown event" : type,
                color: SimBridgeTheme.primaryColor
            )
        }
    }

    // MARK: - INCOMING_SMS

    private func handleIncomingSms(_ message: WsMessage) {
        guard let appState = appState else { return }

        let from = message.from ?? "Unknown"
        let body = message.body ?? ""

        appState.addEvent(
            icon: "message.fill",
            title: "SMS from \(from)",
            detail: body,
            color: SimBridgeTheme.primaryColor
        )

        // Fire local notification
        sendLocalNotification(
            title: "SMS from \(from)",
            body: body
        )
    }

    // MARK: - SMS_SENT

    private func handleSmsSent(_ message: WsMessage) {
        guard let appState = appState else { return }

        let to = message.to ?? "Unknown"

        appState.addEvent(
            icon: "checkmark.circle.fill",
            title: "SMS Sent",
            detail: "To: \(to)",
            color: SimBridgeTheme.secondaryColor
        )
    }

    // MARK: - CALL_STATE

    private func handleCallState(_ message: WsMessage) {
        guard let appState = appState else { return }

        let state = message.state ?? "unknown"
        appState.callState = state

        let icon: String
        let color: Color
        switch state.lowercased() {
        case "dialing":
            icon = "phone.arrow.up.right.fill"
            color = SimBridgeTheme.primaryColor
        case "active":
            icon = "phone.fill"
            color = SimBridgeTheme.secondaryColor
        case "ended":
            icon = "phone.down.fill"
            color = SimBridgeTheme.errorColor
        default:
            icon = "phone.fill"
            color = SimBridgeTheme.primaryColor
        }

        appState.addEvent(
            icon: icon,
            title: "Call \(state.capitalized)",
            detail: message.to ?? "",
            color: color
        )
    }

    // MARK: - SIM_INFO

    private func handleSimInfo(_ message: WsMessage) {
        guard let appState = appState else { return }

        // SIM info may come as payload or directly in the message
        // Try to decode SIM array from the payload
        if let payload = message.payload,
           case .array(let items) = payload {
            var sims: [SimInfo] = []
            for item in items {
                if case .dictionary(let dict) = item {
                    let slot = dict["slot"]
                    let carrier = dict["carrier"]
                    let number = dict["number"]

                    var slotInt = 0
                    var carrierStr = ""
                    var numberStr = ""

                    if case .int(let v) = slot { slotInt = v }
                    if case .string(let v) = carrier { carrierStr = v }
                    if case .string(let v) = number { numberStr = v }

                    sims.append(SimInfo(slot: slotInt, carrier: carrierStr, number: numberStr))
                }
            }
            if !sims.isEmpty {
                appState.sims = sims
            }
        }

        appState.addEvent(
            icon: "simcard.fill",
            title: "SIM Info Updated",
            detail: "\(appState.sims.count) SIM(s) detected",
            color: SimBridgeTheme.primaryColor
        )
    }

    // MARK: - ERROR

    private func handleError(_ message: WsMessage) {
        guard let appState = appState else { return }

        let errorMsg = message.error ?? "Unknown error"

        appState.addEvent(
            icon: "exclamationmark.triangle.fill",
            title: "Error",
            detail: errorMsg,
            color: SimBridgeTheme.errorColor
        )

        appState.showError(errorMsg)
    }

    // MARK: - Local Notification

    private func sendLocalNotification(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil // deliver immediately
        )

        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("[Notification] Error: \(error.localizedDescription)")
            }
        }
    }
}
