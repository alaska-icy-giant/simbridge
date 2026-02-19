// SimBridgeHostApp.swift
// SimBridgeHost
//
// @main entry with WindowGroup, app lifecycle.
// Matches Android MainActivity.kt + SimBridgeApp.kt setup.
// Initializes AppState, sets up theme, and configures the navigation root.

import SwiftUI

@main
struct SimBridgeHostApp: App {

    @StateObject private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            AppNavigation(
                prefs: appState.prefs,
                service: appState.service
            )
            .environmentObject(appState)
            .onAppear {
                appState.autoStartIfNeeded()
            }
        }
    }
}
