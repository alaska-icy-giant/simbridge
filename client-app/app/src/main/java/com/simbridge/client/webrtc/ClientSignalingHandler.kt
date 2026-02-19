package com.simbridge.client.webrtc

import android.util.Log
import com.simbridge.client.data.WsMessage
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

/**
 * Client-side signaling. The Client typically initiates the WebRTC session
 * (sends offer) when a call is active, and the Host responds with an answer.
 */
class ClientSignalingHandler(
    private val webRtcManager: ClientWebRtcManager,
    private val sendMessage: (WsMessage) -> Unit,
) {
    companion object {
        private const val TAG = "ClientSignaling"
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
     * Call this when the client wants to start the audio bridge for an active call.
     */
    fun initiateAudioSession(callReqId: String?) {
        webRtcManager.createPeerConnection()
        webRtcManager.createOffer { offer ->
            if (offer != null) {
                sendMessage(
                    WsMessage(
                        type = "webrtc",
                        action = "offer",
                        sdp = offer.description,
                        reqId = callReqId,
                    )
                )
            } else {
                Log.e(TAG, "Failed to create SDP offer")
                sendMessage(
                    WsMessage(
                        type = "webrtc",
                        action = "error",
                        body = "Failed to create SDP offer",
                        reqId = callReqId,
                    )
                )
            }
        }
    }

    fun handleSignaling(message: WsMessage) {
        when (message.action) {
            "offer" -> handleOffer(message)
            "answer" -> handleAnswer(message)
            "ice" -> handleIce(message)
            else -> Log.w(TAG, "Unknown action: ${message.action}")
        }
    }

    private fun handleOffer(message: WsMessage) {
        val sdp = message.sdp ?: return
        Log.i(TAG, "Received offer")
        webRtcManager.createPeerConnection()
        webRtcManager.setRemoteDescription(
            SessionDescription(SessionDescription.Type.OFFER, sdp)
        ) {
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
                } else {
                    Log.e(TAG, "Failed to create SDP answer")
                    sendMessage(
                        WsMessage(
                            type = "webrtc",
                            action = "error",
                            body = "Failed to create SDP answer",
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
        webRtcManager.setRemoteDescription(
            SessionDescription(SessionDescription.Type.ANSWER, sdp)
        )
    }

    private fun handleIce(message: WsMessage) {
        val c = message.candidate ?: return
        val mid = message.sdpMid ?: return
        val idx = message.sdpMLineIndex ?: return
        webRtcManager.addIceCandidate(IceCandidate(mid, idx, c))
    }
}
