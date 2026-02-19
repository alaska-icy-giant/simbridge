// AppState.swift
// Observable shared state for the SimBridge Client app.

import Foundation
import SwiftUI

/// Represents the current screen in the navigation flow.
enum AppScreen: Hashable {
    case login
    case pair
    case dashboard
    case compose
    case dialer
    case history
    case settings
}

/// Represents a real-time event displayed in the dashboard feed.
struct EventFeedItem: Identifiable {
    let id = UUID()
    let timestamp: Date
    let icon: String        // SF Symbol name
    let title: String
    let detail: String
    let color: Color
}

@MainActor
final class AppState: ObservableObject {

    // MARK: - Auth State
    @Published var isLoggedIn: Bool = Prefs.isLoggedIn
    @Published var isPaired: Bool = Prefs.isPaired
    @Published var token: String = Prefs.token

    // MARK: - Connection
    @Published var connectionStatus: ConnectionStatus = .disconnected
    @Published var isHostOnline: Bool = false

    // MARK: - Device / Pairing
    @Published var deviceId: Int = Prefs.deviceId
    @Published var deviceName: String = Prefs.deviceName
    @Published var pairedHostId: Int = Prefs.pairedHostId
    @Published var pairedHostName: String = Prefs.pairedHostName

    // MARK: - SIM Info
    @Published var sims: [SimInfo] = []

    // MARK: - Call State
    @Published var callState: String = ""  // "dialing", "active", "ended", ""

    // MARK: - Event Feed
    @Published var eventFeed: [EventFeedItem] = []

    // MARK: - Errors / Alerts
    @Published var alertMessage: String = ""
    @Published var showAlert: Bool = false

    // MARK: - Services
    var webSocketManager: WebSocketManager?
    var eventHandler: EventHandler?

    // MARK: - Methods

    func completeLogin(token: String, serverUrl: String) {
        Prefs.token = token
        Prefs.serverUrl = serverUrl
        self.token = token
        self.isLoggedIn = true
        Prefs.isLoggedIn = true
    }

    func setDevice(id: Int, name: String) {
        Prefs.deviceId = id
        Prefs.deviceName = name
        self.deviceId = id
        self.deviceName = name
    }

    func completePairing(hostDeviceId: Int, hostName: String) {
        Prefs.pairedHostId = hostDeviceId
        Prefs.pairedHostName = hostName
        self.pairedHostId = hostDeviceId
        self.pairedHostName = hostName
        self.isPaired = true
        Prefs.isPaired = true
    }

    func connectWebSocket() {
        guard deviceId > 0, !token.isEmpty else { return }
        let serverUrl = Prefs.serverUrl
        guard !serverUrl.isEmpty else { return }

        let manager = WebSocketManager(
            serverUrl: serverUrl,
            deviceId: deviceId,
            token: token
        )

        let handler = EventHandler(appState: self)
        manager.onMessage = { [weak handler] message in
            handler?.handle(message: message)
        }
        manager.onStatusChange = { [weak self] status in
            Task { @MainActor in
                self?.connectionStatus = status
            }
        }

        self.webSocketManager = manager
        self.eventHandler = handler
        manager.connect()
    }

    func disconnectWebSocket() {
        webSocketManager?.disconnect()
        webSocketManager = nil
        eventHandler = nil
        connectionStatus = .disconnected
    }

    func addEvent(icon: String, title: String, detail: String, color: Color) {
        let item = EventFeedItem(
            timestamp: Date(),
            icon: icon,
            title: title,
            detail: detail,
            color: color
        )
        eventFeed.insert(item, at: 0)
        // Keep only last 5 events
        if eventFeed.count > 5 {
            eventFeed = Array(eventFeed.prefix(5))
        }
    }

    func showError(_ message: String) {
        alertMessage = message
        showAlert = true
    }

    func logout() {
        disconnectWebSocket()
        SecureTokenStore().clear()
        Prefs.clear()
        isLoggedIn = false
        isPaired = false
        token = ""
        deviceId = 0
        deviceName = ""
        pairedHostId = 0
        pairedHostName = ""
        sims = []
        callState = ""
        eventFeed = []
        connectionStatus = .disconnected
    }
}
