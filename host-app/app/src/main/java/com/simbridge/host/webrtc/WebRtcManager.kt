package com.simbridge.host.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.webrtc.*

/**
 * Manages the WebRTC PeerConnection lifecycle for call audio bridging.
 */
class WebRtcManager(private val context: Context) {

    companion object {
        private const val TAG = "WebRtcManager"
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null

    var onIceCandidate: ((IceCandidate) -> Unit)? = null
    var onIceConnectionChange: ((PeerConnection.IceConnectionState) -> Unit)? = null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    init {
        initFactory()
    }

    private fun initFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(
                JavaAudioDeviceModule.builder(context)
                    .setUseHardwareAcousticEchoCanceler(true)
                    .setUseHardwareNoiseSuppressor(true)
                    .createAudioDeviceModule()
            )
            .createPeerConnectionFactory()
    }

    /**
     * Creates a new PeerConnection with audio only (no video).
     * H-11: Sets audio mode to MODE_IN_COMMUNICATION for optimal voice routing.
     */
    fun createPeerConnection() {
        val factory = peerConnectionFactory ?: return

        // H-11: Configure audio mode for voice communication
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "Signaling state: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.i(TAG, "ICE connection state: $state")
                state?.let { onIceConnectionChange?.invoke(it) }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "ICE gathering state: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { onIceCandidate?.invoke(it) }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {}

            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onDataChannel(channel: DataChannel?) {}

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "Renegotiation needed")
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}

            override fun onTrack(transceiver: RtpTransceiver?) {
                Log.i(TAG, "Remote track received: ${transceiver?.mediaType}")
            }
        }) ?: return

        // Add local audio track
        addLocalAudioTrack()
        Log.i(TAG, "PeerConnection created")
    }

    private fun addLocalAudioTrack() {
        val factory = peerConnectionFactory ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("autoGainControl", "true"))
        }
        audioSource = factory.createAudioSource(constraints)
        localAudioTrack = factory.createAudioTrack("audio0", audioSource)
        localAudioTrack?.setEnabled(true)
        peerConnection?.addTrack(localAudioTrack, listOf("stream0"))
    }

    /**
     * Creates an SDP offer (host initiates the call audio stream).
     */
    fun createOffer(callback: (SessionDescription?) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(LoggingSdpObserver("setLocalDesc(offer)") {
                    callback(sdp)
                }, sdp)
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Create offer failed: $error")
                callback(null)
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    /**
     * Creates an SDP answer in response to a remote offer.
     */
    fun createAnswer(callback: (SessionDescription?) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(LoggingSdpObserver("setLocalDesc(answer)") {
                    callback(sdp)
                }, sdp)
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Create answer failed: $error")
                callback(null)
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    fun setRemoteDescription(sdp: SessionDescription, callback: (() -> Unit)? = null) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.i(TAG, "Remote description set")
                callback?.invoke()
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Set remote description failed: $error")
            }
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        }, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun dispose() {
        localAudioTrack?.dispose()
        audioSource?.dispose()
        peerConnection?.dispose()
        peerConnectionFactory?.dispose()
        localAudioTrack = null
        audioSource = null
        peerConnection = null
        peerConnectionFactory = null
        resetAudioMode()
        Log.i(TAG, "WebRTC disposed")
    }

    fun closePeerConnection() {
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        localAudioTrack?.dispose()
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null
        resetAudioMode()
    }

    private fun resetAudioMode() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.mode = AudioManager.MODE_NORMAL
    }

    private class LoggingSdpObserver(
        private val label: String,
        private val onSuccess: (() -> Unit)? = null,
    ) : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetSuccess() {
            Log.d(TAG, "$label succeeded")
            onSuccess?.invoke()
        }
        override fun onSetFailure(error: String?) {
            Log.e(TAG, "$label failed: $error")
        }

        companion object {
            private const val TAG = "WebRtcManager"
        }
    }
}
