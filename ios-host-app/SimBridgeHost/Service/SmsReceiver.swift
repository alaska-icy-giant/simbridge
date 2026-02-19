// SmsReceiver.swift
// SimBridgeHost
//
// iOS SMS Interception — PLATFORM LIMITATION DOCUMENTATION
//
// On Android, incoming SMS messages are intercepted via a BroadcastReceiver registered
// for Telephony.Sms.Intents.SMS_RECEIVED_ACTION. This allows the host app to
// automatically forward incoming SMS to the relay server.
//
// On iOS, this is NOT POSSIBLE for App Store builds:
// - iOS does not provide any public API for intercepting incoming SMS messages.
// - The Messages framework only allows sending, not receiving/reading SMS.
// - The IdentityLookup framework (ILMessageFilterExtension) can filter/report
//   unknown senders but does NOT provide access to message content for forwarding.
//
// For reference only (NOT suitable for App Store):
// - Jailbroken devices can use the private CTMessageCenter API to intercept SMS.
// - MDM-enrolled enterprise devices may have limited access via supervised profiles.
//
// This file provides a stub interface matching the Android SmsReceiver pattern
// so that if a future iOS capability becomes available, the integration point is ready.

import Foundation
import os.log

final class SmsReceiver {

    private static let logger = Logger(subsystem: "com.simbridge.host", category: "SmsReceiver")

    /// Callback invoked when an SMS is received. On iOS App Store builds, this will
    /// never be called since incoming SMS interception is not available.
    var onSmsReceived: ((WsMessage) -> Void)?

    init() {
        Self.logger.info("SmsReceiver initialized (iOS: incoming SMS interception not available)")
    }

    /// Attempts to start listening for incoming SMS.
    /// On iOS, this is a no-op with a warning log.
    func startListening() {
        Self.logger.warning("""
            iOS Limitation: Cannot intercept incoming SMS.
            - App Store builds: No API available.
            - Enterprise/MDM: May have limited access via supervised profiles.
            - Jailbroken: Private CTMessageCenter API (unsupported, not recommended).
            Incoming SMS relay is not available on this platform.
            """)
    }

    /// Stops listening for incoming SMS. No-op on iOS.
    func stopListening() {
        // No-op on iOS
    }

    // MARK: - Private API Reference (DO NOT USE IN PRODUCTION)
    //
    // The following documents the private API for reference only. Using private APIs
    // will result in App Store rejection.
    //
    // CTMessageCenter:
    //   let center = CTMessageCenter.sharedMessageCenter()
    //   center.addObserver(forName: "kCTMessageReceivedNotification") { notification in
    //       // Extract SMS content from notification userInfo
    //   }
    //
    // This requires the CoreTelephony private headers and entitlements that are only
    // available on jailbroken devices or via enterprise MDM.
}
