// CallManager.swift
// SimBridgeHost
//
// CXProvider + CXProviderDelegate implementation for CallKit integration.
// Matches Android BridgeConnectionService.kt + BridgeConnection.kt functionality.
// Manages the CallKit provider that allows SimBridge to handle calls through
// the native iOS call UI.

import Foundation
import CallKit
import AVFoundation
import os.log

final class CallManager: NSObject, CXProviderDelegate {

    private static let logger = Logger(subsystem: "com.simbridge.host", category: "CallManager")

    static let shared = CallManager()

    private let provider: CXProvider
    private let callController = CXCallController()

    /// Tracks active call UUIDs mapped to their current state.
    private var activeCalls: [UUID: String] = [:]

    /// Callback invoked when call state changes. Matches Android BridgeConnection.onStateChanged.
    var onCallStateChanged: ((UUID, String) -> Void)?

    private override init() {
        let configuration = CXProviderConfiguration()
        configuration.maximumCallsPerCallGroup = 1
        configuration.supportsVideo = false
        configuration.supportedHandleTypes = [.phoneNumber]
        configuration.includesCallsInRecents = true

        provider = CXProvider(configuration: configuration)
        super.init()
        provider.setDelegate(self, queue: nil)
    }

    // MARK: - Public API

    /// Reports an incoming call to CallKit for display in the native call UI.
    func reportIncomingCall(
        uuid: UUID,
        handle: String,
        completion: @escaping (Error?) -> Void
    ) {
        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .phoneNumber, value: handle)
        update.hasVideo = false
        update.supportsHolding = true
        update.supportsGrouping = false
        update.supportsUngrouping = false
        update.supportsDTMF = true

        provider.reportNewIncomingCall(with: uuid, update: update) { [weak self] error in
            if let error = error {
                Self.logger.error("Failed to report incoming call: \(error.localizedDescription)")
            } else {
                self?.activeCalls[uuid] = "ringing"
                Self.logger.info("Incoming call reported: \(handle)")
            }
            completion(error)
        }
    }

    /// Reports that an outgoing call has started connecting.
    func reportOutgoingCallStartedConnecting(uuid: UUID) {
        provider.reportOutgoingCall(with: uuid, startedConnectingAt: Date())
        activeCalls[uuid] = "connecting"
        onCallStateChanged?(uuid, "connecting")
    }

    /// Reports that an outgoing call has connected.
    func reportOutgoingCallConnected(uuid: UUID) {
        provider.reportOutgoingCall(with: uuid, connectedAt: Date())
        activeCalls[uuid] = "active"
        onCallStateChanged?(uuid, "active")
    }

    /// Reports that a call has ended.
    func reportCallEnded(uuid: UUID, reason: CXCallEndedReason) {
        provider.reportCall(with: uuid, endedAt: Date(), reason: reason)
        activeCalls.removeValue(forKey: uuid)
        onCallStateChanged?(uuid, "ended")
    }

    // MARK: - CXProviderDelegate

    func providerDidReset(_ provider: CXProvider) {
        Self.logger.info("Provider did reset")
        activeCalls.removeAll()
    }

    func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        Self.logger.info("Start call action: \(action.handle.value)")

        // Configure audio session for voice chat
        configureAudioSession()

        activeCalls[action.callUUID] = "dialing"
        onCallStateChanged?(action.callUUID, "dialing")

        // Signal that the call has started connecting
        provider.reportOutgoingCall(with: action.callUUID, startedConnectingAt: Date())

        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        Self.logger.info("Answer call action")

        configureAudioSession()

        activeCalls[action.callUUID] = "active"
        onCallStateChanged?(action.callUUID, "active")

        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        Self.logger.info("End call action")

        activeCalls.removeValue(forKey: action.callUUID)
        onCallStateChanged?(action.callUUID, "ended")

        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXSetHeldCallAction) {
        Self.logger.info("Hold call action: held=\(action.isOnHold)")

        let state = action.isOnHold ? "holding" : "active"
        activeCalls[action.callUUID] = state
        onCallStateChanged?(action.callUUID, state)

        action.fulfill()
    }

    func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        Self.logger.info("Mute call action: muted=\(action.isMuted)")
        action.fulfill()
    }

    func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        Self.logger.info("Audio session activated")
        // AudioBridge can start tapping audio here
    }

    func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {
        Self.logger.info("Audio session deactivated")
        // AudioBridge should stop tapping audio here
    }

    // MARK: - Private

    private func configureAudioSession() {
        let audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession.setCategory(
                .playAndRecord,
                mode: .voiceChat,
                options: [.allowBluetooth, .allowBluetoothA2DP]
            )
            try audioSession.setActive(true)
        } catch {
            Self.logger.error("Failed to configure audio session: \(error.localizedDescription)")
        }
    }
}
