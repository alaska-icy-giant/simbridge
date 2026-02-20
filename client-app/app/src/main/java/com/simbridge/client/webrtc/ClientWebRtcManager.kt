package com.simbridge.client.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Client-side WebRTC manager. Receives call audio from the Host
 * and plays it through the device speaker/earpiece.
 */
class ClientWebRtcManager(private val context: Context) {

    companion object {
        private const val TAG = "ClientWebRtc"
    }

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null

    var onIceCandidate: ((IceCandidate) -> Unit)? = null
    var onIceConnectionChange: ((PeerConnection.IceConnectionState) -> Unit)? = null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(
                JavaAudioDeviceModule.builder(context)
                    .setUseHardwareAcousticEchoCanceler(true)
                    .setUseHardwareNoiseSuppressor(true)
                    .createAudioDeviceModule()
            )
            .createPeerConnectionFactory()
    }

    fun createPeerConnection() {
        val f = factory ?: return

        // Clean up any existing connection to prevent resource leaks (C-01)
        closePeerConnection()

        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = f.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
                Log.i(TAG, "ICE: $s")
                s?.let { onIceConnectionChange?.invoke(it) }
            }
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(c: IceCandidate?) { c?.let { onIceCandidate?.invoke(it) } }
            override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
            override fun onAddStream(s: MediaStream?) {}
            override fun onRemoveStream(s: MediaStream?) {}
            override fun onDataChannel(d: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver?, streams: Array<out MediaStream>?) {}
            override fun onTrack(t: RtpTransceiver?) {
                Log.i(TAG, "Remote track: ${t?.mediaType}")
            }
        })

        // Add local audio so the client can also speak
        addLocalAudioTrack()
    }

    private fun addLocalAudioTrack() {
        val f = factory ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("autoGainControl", "true"))
        }
        audioSource = f.createAudioSource(constraints)
        localAudioTrack = f.createAudioTrack("client_audio", audioSource)
        localAudioTrack?.setEnabled(true)
        peerConnection?.addTrack(localAudioTrack, listOf("client_stream"))
    }

    /**
     * Client initiates the call audio by creating an SDP offer.
     */
    fun createOffer(callback: (SessionDescription?) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(NoOpSdp(), sdp)
                callback(sdp)
            }
            override fun onCreateFailure(e: String?) { Log.e(TAG, "Offer failed: $e"); callback(null) }
            override fun onSetSuccess() {}
            override fun onSetFailure(e: String?) {}
        }, constraints)
    }

    fun createAnswer(callback: (SessionDescription?) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(NoOpSdp(), sdp)
                callback(sdp)
            }
            override fun onCreateFailure(e: String?) { Log.e(TAG, "Answer failed: $e"); callback(null) }
            override fun onSetSuccess() {}
            override fun onSetFailure(e: String?) {}
        }, constraints)
    }

    fun setRemoteDescription(sdp: SessionDescription, callback: (() -> Unit)? = null) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() { callback?.invoke() }
            override fun onSetFailure(e: String?) { Log.e(TAG, "Remote SDP failed: $e") }
            override fun onCreateSuccess(s: SessionDescription?) {}
            override fun onCreateFailure(e: String?) {}
        }, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun closePeerConnection() {
        localAudioTrack?.dispose()
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
    }

    fun dispose() {
        closePeerConnection()
        factory?.dispose()
        factory = null
    }

    private class NoOpSdp : SdpObserver {
        override fun onCreateSuccess(s: SessionDescription?) {}
        override fun onCreateFailure(e: String?) {}
        override fun onSetSuccess() {}
        override fun onSetFailure(e: String?) {}
    }
}
