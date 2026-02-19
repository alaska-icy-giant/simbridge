// WebRtcManager.swift
// SimBridgeHost
//
// Manages the WebRTC PeerConnection lifecycle for call audio bridging.
// Mirrors Android WebRtcManager.kt using the GoogleWebRTC iOS framework.
//
// Requires: WebRTC.framework via Swift Package Manager or CocoaPods.
// SPM: https://github.com/nicolo-ribaudo/webrtc-swiftpm or Google's official package.
//
// NOTE: The WebRTC import will fail without the framework added to the project.
// In a non-WebRTC build, this file can be compiled by setting the WEBRTC_AVAILABLE flag.

import Foundation
import os.log

// Forward-declare WebRTC types to allow compilation without the framework.
// When WebRTC.framework is linked, replace these with: import WebRTC
#if canImport(WebRTC)
import WebRTC
#endif

final class WebRtcManager {

    private static let logger = Logger(subsystem: "com.simbridge.host", category: "WebRtcManager")

    #if canImport(WebRTC)

    private var peerConnectionFactory: RTCPeerConnectionFactory?
    private var peerConnection: RTCPeerConnection?
    private var localAudioTrack: RTCAudioTrack?
    private var audioSource: RTCAudioSource?

    var onIceCandidate: ((RTCIceCandidate) -> Void)?
    var onIceConnectionChange: ((RTCIceConnectionState) -> Void)?

    private let iceServers = [
        RTCIceServer(urlStrings: ["stun:stun.l.google.com:19302"])
    ]

    init() {
        initFactory()
    }

    private func initFactory() {
        RTCInitializeSSL()

        let encoderFactory = RTCDefaultVideoEncoderFactory()
        let decoderFactory = RTCDefaultVideoDecoderFactory()

        peerConnectionFactory = RTCPeerConnectionFactory(
            encoderFactory: encoderFactory,
            decoderFactory: decoderFactory
        )
        Self.logger.info("PeerConnectionFactory initialized")
    }

    /// Creates a new PeerConnection with audio only (no video).
    func createPeerConnection() {
        guard let factory = peerConnectionFactory else { return }

        let config = RTCConfiguration()
        config.iceServers = iceServers
        config.sdpSemantics = .unifiedPlan
        config.continualGatheringPolicy = .gatherContinually

        let constraints = RTCMediaConstraints(
            mandatoryConstraints: nil,
            optionalConstraints: nil
        )

        let delegate = PeerConnectionDelegate(manager: self)
        peerConnection = factory.peerConnection(
            with: config,
            constraints: constraints,
            delegate: delegate
        )

        addLocalAudioTrack()
        Self.logger.info("PeerConnection created")
    }

    private func addLocalAudioTrack() {
        guard let factory = peerConnectionFactory else { return }

        let audioConstraints = RTCMediaConstraints(
            mandatoryConstraints: [
                "echoCancellation": "true",
                "noiseSuppression": "true",
                "autoGainControl": "true"
            ],
            optionalConstraints: nil
        )

        audioSource = factory.audioSource(with: audioConstraints)
        guard let source = audioSource else { return }

        localAudioTrack = factory.audioTrack(with: source, trackId: "audio0")
        localAudioTrack?.isEnabled = true

        if let track = localAudioTrack {
            peerConnection?.add(track, streamIds: ["stream0"])
        }
    }

    /// Creates an SDP offer (host initiates the call audio stream).
    func createOffer(completion: @escaping (RTCSessionDescription?) -> Void) {
        let constraints = RTCMediaConstraints(
            mandatoryConstraints: [
                "OfferToReceiveAudio": "true",
                "OfferToReceiveVideo": "false"
            ],
            optionalConstraints: nil
        )

        peerConnection?.offer(for: constraints) { [weak self] sdp, error in
            guard let self = self, let sdp = sdp else {
                Self.logger.error("Create offer failed: \(error?.localizedDescription ?? "unknown")")
                completion(nil)
                return
            }

            self.peerConnection?.setLocalDescription(sdp) { error in
                if let error = error {
                    Self.logger.error("Set local description (offer) failed: \(error.localizedDescription)")
                    completion(nil)
                } else {
                    Self.logger.debug("Local description (offer) set")
                    completion(sdp)
                }
            }
        }
    }

    /// Creates an SDP answer in response to a remote offer.
    func createAnswer(completion: @escaping (RTCSessionDescription?) -> Void) {
        let constraints = RTCMediaConstraints(
            mandatoryConstraints: [
                "OfferToReceiveAudio": "true",
                "OfferToReceiveVideo": "false"
            ],
            optionalConstraints: nil
        )

        peerConnection?.answer(for: constraints) { [weak self] sdp, error in
            guard let self = self, let sdp = sdp else {
                Self.logger.error("Create answer failed: \(error?.localizedDescription ?? "unknown")")
                completion(nil)
                return
            }

            self.peerConnection?.setLocalDescription(sdp) { error in
                if let error = error {
                    Self.logger.error("Set local description (answer) failed: \(error.localizedDescription)")
                    completion(nil)
                } else {
                    Self.logger.debug("Local description (answer) set")
                    completion(sdp)
                }
            }
        }
    }

    /// Sets the remote session description.
    func setRemoteDescription(_ sdp: RTCSessionDescription, completion: (() -> Void)? = nil) {
        peerConnection?.setRemoteDescription(sdp) { error in
            if let error = error {
                Self.logger.error("Set remote description failed: \(error.localizedDescription)")
            } else {
                Self.logger.info("Remote description set")
                completion?()
            }
        }
    }

    /// Adds a remote ICE candidate.
    func addIceCandidate(_ candidate: RTCIceCandidate) {
        peerConnection?.add(candidate) { error in
            if let error = error {
                Self.logger.error("Add ICE candidate failed: \(error.localizedDescription)")
            }
        }
    }

    /// Closes and cleans up the current peer connection.
    func closePeerConnection() {
        peerConnection?.close()
        localAudioTrack = nil
        audioSource = nil
        peerConnection = nil
    }

    /// Disposes all WebRTC resources.
    func dispose() {
        closePeerConnection()
        peerConnectionFactory = nil
        RTCCleanupSSL()
        Self.logger.info("WebRTC disposed")
    }

    // MARK: - PeerConnection Delegate

    private class PeerConnectionDelegate: NSObject, RTCPeerConnectionDelegate {
        weak var manager: WebRtcManager?

        init(manager: WebRtcManager) {
            self.manager = manager
        }

        func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {
            WebRtcManager.logger.debug("Signaling state: \(stateChanged.rawValue)")
        }

        func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {
            WebRtcManager.logger.info("ICE connection state: \(newState.rawValue)")
            manager?.onIceConnectionChange?(newState)
        }

        func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {
            WebRtcManager.logger.debug("ICE gathering state: \(newState.rawValue)")
        }

        func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
            manager?.onIceCandidate?(candidate)
        }

        func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}

        func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {}

        func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {
            WebRtcManager.logger.debug("Renegotiation needed")
        }

        func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {}

        func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {}

        func peerConnection(_ peerConnection: RTCPeerConnection, didAdd rtpReceiver: RTCRtpReceiver, streams mediaStreams: [RTCMediaStream]) {
            WebRtcManager.logger.info("Remote track received")
        }
    }

    #else

    // MARK: - Stub when WebRTC framework is not available

    var onIceCandidate: ((Any) -> Void)?
    var onIceConnectionChange: ((Any) -> Void)?

    init() {
        Self.logger.warning("WebRTC framework not available. WebRTC features are disabled.")
    }

    func createPeerConnection() {
        Self.logger.error("WebRTC not available: cannot create peer connection")
    }

    func createOffer(completion: @escaping (Any?) -> Void) {
        completion(nil)
    }

    func createAnswer(completion: @escaping (Any?) -> Void) {
        completion(nil)
    }

    func setRemoteDescription(_ sdp: Any, completion: (() -> Void)? = nil) {}
    func addIceCandidate(_ candidate: Any) {}
    func closePeerConnection() {}
    func dispose() {}

    #endif
}
