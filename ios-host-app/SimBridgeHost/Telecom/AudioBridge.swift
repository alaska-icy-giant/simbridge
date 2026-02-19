// AudioBridge.swift
// SimBridgeHost
//
// AVAudioEngine-based audio tap for capturing and injecting call audio.
// Matches Android AudioBridge.kt / telecom audio handling.
// Configures the audio session as .voiceChat with .allowBluetooth.

import Foundation
import AVFoundation
import os.log

final class AudioBridge {

    private static let logger = Logger(subsystem: "com.simbridge.host", category: "AudioBridge")

    private let audioEngine = AVAudioEngine()
    private var isRunning = false

    /// Called with captured audio buffer data from the microphone.
    /// Use this to feed audio into WebRTC or other audio pipelines.
    var onAudioCaptured: ((AVAudioPCMBuffer, AVAudioTime) -> Void)?

    /// Called when remote audio should be played back.
    private var playerNode: AVAudioPlayerNode?

    // MARK: - Audio Session Configuration

    /// Configures the AVAudioSession for voice chat with Bluetooth support.
    func configureAudioSession() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(
            .playAndRecord,
            mode: .voiceChat,
            options: [.allowBluetooth, .allowBluetoothA2DP, .defaultToSpeaker]
        )
        try session.setPreferredSampleRate(48000)
        try session.setPreferredIOBufferDuration(0.02) // 20ms buffer
        try session.setActive(true)
        Self.logger.info("Audio session configured for voice chat")
    }

    // MARK: - Audio Engine

    /// Starts the audio engine and installs a tap on the input node (microphone).
    func startCapture() throws {
        guard !isRunning else {
            Self.logger.warning("Audio capture already running")
            return
        }

        try configureAudioSession()

        let inputNode = audioEngine.inputNode
        let inputFormat = inputNode.outputFormat(forBus: 0)

        guard inputFormat.sampleRate > 0, inputFormat.channelCount > 0 else {
            throw AudioBridgeError.invalidAudioFormat
        }

        Self.logger.info("Input format: \(inputFormat.sampleRate)Hz, \(inputFormat.channelCount)ch")

        // Install a tap on the input node to capture microphone audio
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: inputFormat) { [weak self] buffer, time in
            self?.onAudioCaptured?(buffer, time)
        }

        // Set up player node for remote audio playback
        let player = AVAudioPlayerNode()
        playerNode = player
        audioEngine.attach(player)

        let outputFormat = audioEngine.outputNode.inputFormat(forBus: 0)
        audioEngine.connect(player, to: audioEngine.mainMixerNode, format: outputFormat)

        try audioEngine.start()
        player.play()
        isRunning = true

        Self.logger.info("Audio capture started")
    }

    /// Stops the audio engine and removes the input tap.
    func stopCapture() {
        guard isRunning else { return }

        audioEngine.inputNode.removeTap(onBus: 0)

        if let player = playerNode {
            player.stop()
            audioEngine.detach(player)
            playerNode = nil
        }

        audioEngine.stop()
        isRunning = false

        // Deactivate audio session
        do {
            try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        } catch {
            Self.logger.error("Failed to deactivate audio session: \(error.localizedDescription)")
        }

        Self.logger.info("Audio capture stopped")
    }

    /// Schedules a PCM buffer for playback through the speaker/earpiece.
    /// Use this to play remote audio received via WebRTC.
    func playRemoteAudio(buffer: AVAudioPCMBuffer) {
        guard isRunning, let player = playerNode else { return }
        player.scheduleBuffer(buffer, completionHandler: nil)
    }

    /// Returns the current input audio format.
    var inputFormat: AVAudioFormat? {
        let format = audioEngine.inputNode.outputFormat(forBus: 0)
        guard format.sampleRate > 0 else { return nil }
        return format
    }

    /// Returns the current output audio format.
    var outputFormat: AVAudioFormat? {
        let format = audioEngine.outputNode.inputFormat(forBus: 0)
        guard format.sampleRate > 0 else { return nil }
        return format
    }
}

// MARK: - Errors

enum AudioBridgeError: LocalizedError {
    case invalidAudioFormat

    var errorDescription: String? {
        switch self {
        case .invalidAudioFormat:
            return "Invalid audio format: sample rate or channel count is zero"
        }
    }
}
