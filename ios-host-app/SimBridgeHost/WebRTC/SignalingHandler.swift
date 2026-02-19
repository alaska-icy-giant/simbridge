// SignalingHandler.swift
// SimBridgeHost
//
// Handles WebRTC signaling messages (offer/answer/ICE) received through the WebSocket.
// Mirrors Android SignalingHandler.kt.

import Foundation
import os.log

#if canImport(WebRTC)
import WebRTC
#endif

final class SignalingHandler {

    private static let logger = Logger(subsystem: "com.simbridge.host", category: "SignalingHandler")

    private let webRtcManager: WebRtcManager
    private let sendMessage: (WsMessage) -> Void

    init(webRtcManager: WebRtcManager, sendMessage: @escaping (WsMessage) -> Void) {
        self.webRtcManager = webRtcManager
        self.sendMessage = sendMessage

        #if canImport(WebRTC)
        // Forward ICE candidates over WebSocket
        webRtcManager.onIceCandidate = { [weak self] candidate in
            self?.sendMessage(WsMessage(
                type: "webrtc",
                action: "ice",
                candidate: candidate.sdp,
                sdpMid: candidate.sdpMid,
                sdpMLineIndex: Int(candidate.sdpMLineIndex)
            ))
        }
        #endif
    }

    /// Handles an incoming WebRTC signaling message.
    func handleSignaling(_ message: WsMessage) {
        guard let action = message.action else {
            Self.logger.warning("WebRTC message missing action field")
            return
        }

        switch action {
        case "offer":
            handleOffer(message)
        case "answer":
            handleAnswer(message)
        case "ice":
            handleIceCandidate(message)
        default:
            Self.logger.warning("Unknown WebRTC action: \(action)")
        }
    }

    // MARK: - Private

    private func handleOffer(_ message: WsMessage) {
        #if canImport(WebRTC)
        guard let sdpString = message.sdp else { return }
        Self.logger.info("Received offer")

        webRtcManager.createPeerConnection()

        let remoteSdp = RTCSessionDescription(type: .offer, sdp: sdpString)
        webRtcManager.setRemoteDescription(remoteSdp) { [weak self] in
            self?.webRtcManager.createAnswer { answer in
                guard let answer = answer else { return }
                self?.sendMessage(WsMessage(
                    type: "webrtc",
                    action: "answer",
                    sdp: answer.sdp,
                    reqId: message.reqId
                ))
            }
        }
        #else
        Self.logger.error("WebRTC not available: cannot handle offer")
        #endif
    }

    private func handleAnswer(_ message: WsMessage) {
        #if canImport(WebRTC)
        guard let sdpString = message.sdp else { return }
        Self.logger.info("Received answer")

        let remoteSdp = RTCSessionDescription(type: .answer, sdp: sdpString)
        webRtcManager.setRemoteDescription(remoteSdp)
        #else
        Self.logger.error("WebRTC not available: cannot handle answer")
        #endif
    }

    private func handleIceCandidate(_ message: WsMessage) {
        #if canImport(WebRTC)
        guard let candidateSdp = message.candidate,
              let sdpMid = message.sdpMid,
              let sdpMLineIndex = message.sdpMLineIndex else {
            return
        }

        let candidate = RTCIceCandidate(
            sdp: candidateSdp,
            sdpMLineIndex: Int32(sdpMLineIndex),
            sdpMid: sdpMid
        )
        webRtcManager.addIceCandidate(candidate)
        #else
        Self.logger.error("WebRTC not available: cannot handle ICE candidate")
        #endif
    }
}
