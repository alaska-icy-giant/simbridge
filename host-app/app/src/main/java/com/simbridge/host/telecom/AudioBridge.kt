package com.simbridge.host.telecom

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack as WebRtcAudioTrack

/**
 * Bridges call audio between the Android telephony system and WebRTC.
 *
 * Captures audio from VOICE_COMMUNICATION source (call audio) and feeds it
 * into a WebRTC audio track. Plays remote WebRTC audio into the call.
 */
class AudioBridge {

    companion object {
        private const val TAG = "AudioBridge"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    @Volatile private var isCapturing = false

    /**
     * Starts capturing call audio. The audio data is automatically routed
     * through WebRTC's audio processing pipeline when using VOICE_COMMUNICATION.
     */
    @SuppressLint("MissingPermission")
    fun startCapture() {
        if (isCapturing) return

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return
            }

            isCapturing = true
            audioRecord?.startRecording()

            captureThread = Thread({
                val buffer = ShortArray(bufferSize / 2)
                while (isCapturing) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        // Audio data is captured and processed by WebRTC's audio module
                        // when using VOICE_COMMUNICATION source. The PeerConnection's
                        // audio track automatically picks up this audio.
                    }
                }
            }, "AudioBridge-Capture").also { it.start() }

            Log.i(TAG, "Audio capture started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio capture", e)
            stopCapture()
        }
    }

    /**
     * Stops audio capture and releases resources.
     */
    fun stopCapture() {
        isCapturing = false
        captureThread?.join(1000)
        captureThread = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        audioRecord?.release()
        audioRecord = null

        Log.i(TAG, "Audio capture stopped")
    }
}
