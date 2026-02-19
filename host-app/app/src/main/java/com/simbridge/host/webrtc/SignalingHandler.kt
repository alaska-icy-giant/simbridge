package com.simbridge.host.webrtc

import android.util.Log
import com.simbridge.host.data.WsMessage
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

/**
 * Handles WebRTC signaling messages (offer/answer/ICE) received through the WebSocket.
 */
class SignalingHandler(
    private val webRtcManager: WebRtcManager,
    private val sendMessage: (WsMessage) -> Unit,
) {

    companion object {
        private const val TAG = "SignalingHandler"
    }

    init {
        webRtcManager.onIceCandidate = { candidate ->
            sendMessage(
                WsMessage(
                    type = "webrtc",
                    action = "ice",
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex,
                )
            )
        }
    }

    /**
     * Handles an incoming WebRTC signaling message.
     */
    fun handleSignaling(message: WsMessage) {
        when (message.action) {
            "offer" -> handleOffer(message)
            "answer" -> handleAnswer(message)
            "ice" -> handleIceCandidate(message)
            else -> Log.w(TAG, "Unknown WebRTC action: ${message.action}")
        }
    }

    private fun handleOffer(message: WsMessage) {
        val sdp = message.sdp ?: return
        Log.i(TAG, "Received offer")

        webRtcManager.createPeerConnection()
        val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdp)
        webRtcManager.setRemoteDescription(remoteSdp) {
            webRtcManager.createAnswer { answer ->
                if (answer != null) {
                    sendMessage(
                        WsMessage(
                            type = "webrtc",
                            action = "answer",
                            sdp = answer.description,
                            reqId = message.reqId,
                        )
                    )
                }
            }
        }
    }

    private fun handleAnswer(message: WsMessage) {
        val sdp = message.sdp ?: return
        Log.i(TAG, "Received answer")
        val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        webRtcManager.setRemoteDescription(remoteSdp)
    }

    private fun handleIceCandidate(message: WsMessage) {
        val candidateSdp = message.candidate ?: return
        val sdpMid = message.sdpMid ?: return
        val sdpMLineIndex = message.sdpMLineIndex ?: return
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateSdp)
        webRtcManager.addIceCandidate(candidate)
    }
}
