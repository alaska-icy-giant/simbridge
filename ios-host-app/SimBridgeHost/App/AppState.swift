// AppState.swift
// SimBridgeHost
//
// ObservableObject with published connection status, logs, and service reference.
// Acts as the app-wide shared state container, matching the role of Android's
// MainActivity state management combined with the service binding pattern.

import Foundation
import Combine

@MainActor
final class AppState: ObservableObject {

    // MARK: - Published State

    @Published var connectionStatus: ConnectionStatus = .disconnected
    @Published var logs: [LogEntry] = []
    @Published var isServiceRunning: Bool = false

    // MARK: - Dependencies

    let prefs: Prefs
    let service: BridgeService

    private var cancellables = Set<AnyCancellable>()

    init() {
        let prefs = Prefs()
        self.prefs = prefs
        self.service = BridgeService(prefs: prefs)

        observeService()
    }

    /// For dependency injection (e.g., testing).
    init(prefs: Prefs, service: BridgeService) {
        self.prefs = prefs
        self.service = service

        observeService()
    }

    // MARK: - Service Observation

    private func observeService() {
        // Forward connection status from service
        service.$connectionStatus
            .receive(on: DispatchQueue.main)
            .assign(to: &$connectionStatus)

        // Forward logs from service
        service.$logs
            .receive(on: DispatchQueue.main)
            .assign(to: &$logs)

        // Forward running state from service
        service.$isRunning
            .receive(on: DispatchQueue.main)
            .assign(to: &$isServiceRunning)
    }

    // MARK: - Actions

    func startService() {
        service.start()
    }

    func stopService() {
        service.stop()
    }

    func logout() {
        service.stop()
        prefs.clear()
    }

    /// Auto-start the service if the user is already logged in.
    func autoStartIfNeeded() {
        if prefs.isLoggedIn {
            service.start()
        }
    }
}
