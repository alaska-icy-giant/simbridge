// SmsHandler.swift
// SimBridgeHost
//
// iOS SMS sending via MFMessageComposeViewController.
//
// IMPORTANT iOS LIMITATION:
// Unlike Android's SmsManager which can send SMS programmatically in the background,
// iOS requires user interaction via MFMessageComposeViewController. This means:
// - SMS cannot be sent automatically in response to a WebSocket command.
// - The user must confirm each SMS send via the system compose sheet.
// - For automated/background SMS relay, an enterprise MDM entitlement or a third-party
//   service (Twilio, Vonage) sidecar is required.
//
// This file implements the compose path and stubs the automated path.

import Foundation
import MessageUI
import os.log

final class SmsHandler: NSObject {

    private static let logger = Logger(subsystem: "com.simbridge.host", category: "SmsHandler")

    private let simInfoProvider: SimInfoProvider
    private let sendEvent: (WsMessage) -> Void

    /// Set this from the UI layer to present MFMessageComposeViewController.
    /// The closure receives (recipients, body) and should present the compose view.
    var presentComposer: (([String], String) -> Void)?

    init(simInfoProvider: SimInfoProvider, sendEvent: @escaping (WsMessage) -> Void) {
        self.simInfoProvider = simInfoProvider
        self.sendEvent = sendEvent
        super.init()
    }

    /// Sends an SMS to the specified number.
    /// On iOS, this can only present MFMessageComposeViewController (user-triggered).
    /// The simSlot parameter is accepted for API compatibility but ignored on iOS
    /// since iOS does not allow programmatic SIM selection for SMS.
    func sendSms(to: String, body: String, simSlot: Int?, reqId: String?) {
        guard MFMessageComposeViewController.canSendText() else {
            Self.logger.error("Device cannot send SMS")
            sendEvent(WsMessage(
                type: "event",
                event: "SMS_SENT",
                body: "Device cannot send SMS (no SMS capability or simulator)",
                status: "error",
                reqId: reqId
            ))
            return
        }

        // Attempt to present the compose UI if a presenter is available
        if let presentComposer = presentComposer {
            Self.logger.info("Presenting SMS composer for \(to)")
            presentComposer([to], body)
            // Report as pending since user must confirm
            sendEvent(WsMessage(
                type: "event",
                event: "SMS_SENT",
                body: "SMS compose sheet presented. Awaiting user confirmation.",
                status: "pending",
                reqId: reqId
            ))
        } else {
            // TODO: For automated SMS relay without user interaction, integrate with
            // a server-side SMS gateway (Twilio, Vonage, etc.) or use enterprise
            // MDM entitlements that grant access to the private SMS framework.
            Self.logger.warning("No SMS composer presenter available. Automated SMS not supported on iOS.")
            sendEvent(WsMessage(
                type: "event",
                event: "SMS_SENT",
                body: "iOS limitation: Cannot send SMS without user interaction. Present the app to send.",
                status: "error",
                reqId: reqId
            ))
        }
    }

    /// Call this when MFMessageComposeViewController completes.
    func handleComposeResult(_ result: MessageComposeResult, reqId: String?) {
        switch result {
        case .sent:
            Self.logger.info("SMS sent successfully via compose sheet")
            sendEvent(WsMessage(
                type: "event",
                event: "SMS_SENT",
                status: "ok",
                reqId: reqId
            ))
        case .cancelled:
            Self.logger.info("SMS compose cancelled by user")
            sendEvent(WsMessage(
                type: "event",
                event: "SMS_SENT",
                body: "User cancelled SMS",
                status: "cancelled",
                reqId: reqId
            ))
        case .failed:
            Self.logger.error("SMS compose failed")
            sendEvent(WsMessage(
                type: "event",
                event: "SMS_SENT",
                body: "SMS send failed",
                status: "error",
                reqId: reqId
            ))
        @unknown default:
            break
        }
    }
}
