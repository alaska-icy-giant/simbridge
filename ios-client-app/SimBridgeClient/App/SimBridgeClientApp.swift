// SimBridgeClientApp.swift
// SimBridge Client — iOS Remote Control App
// Entry point for the SwiftUI application.

import SwiftUI
import UserNotifications
import GoogleSignIn

@main
struct SimBridgeClientApp: App {
    @StateObject private var appState = AppState()

    init() {
        requestNotificationPermission()
    }

    var body: some Scene {
        WindowGroup {
            AppNavigation()
                .environmentObject(appState)
                .preferredColorScheme(nil) // respect system setting
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }

    private func requestNotificationPermission() {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if let error = error {
                print("Notification permission error: \(error.localizedDescription)")
            }
        }
    }
}
