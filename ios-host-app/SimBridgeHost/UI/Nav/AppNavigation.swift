// AppNavigation.swift
// SimBridgeHost
//
// NavigationStack routing matching Android AppNavigation.kt.
// Routes: LOGIN -> DASHBOARD -> LOG / SETTINGS
// If token exists in prefs, starts at DASHBOARD. Otherwise LOGIN.
// Logout clears storage and resets to LOGIN.

import SwiftUI

enum AppRoute: Hashable {
    case login
    case biometric
    case dashboard
    case log
    case settings
}

struct AppNavigation: View {
    @ObservedObject var prefs: Prefs
    @ObservedObject var service: BridgeService

    @State private var navigationPath = NavigationPath()

    private let secureTokenStore = SecureTokenStore()

    var body: some View {
        NavigationStack(path: $navigationPath) {
            // Root view: Biometric, Dashboard, or Login
            Group {
                if prefs.biometricEnabled && secureTokenStore.getToken() != nil {
                    biometricView
                } else if prefs.isLoggedIn {
                    dashboardView
                } else {
                    loginView
                }
            }
            .navigationDestination(for: AppRoute.self) { route in
                switch route {
                case .login:
                    loginView
                case .biometric:
                    biometricView
                case .dashboard:
                    dashboardView
                case .log:
                    LogView(service: service)
                case .settings:
                    SettingsView(
                        prefs: prefs,
                        onLogout: {
                            service.stop()
                            secureTokenStore.clear()
                            prefs.clear()
                            // Reset navigation to root (login)
                            navigationPath = NavigationPath()
                        }
                    )
                }
            }
        }
        .simBridgeTheme()
    }

    // MARK: - View Builders

    private var biometricView: some View {
        BiometricPromptView(
            prefs: prefs,
            secureTokenStore: secureTokenStore,
            onSuccess: {
                navigationPath = NavigationPath()
            },
            onFallbackToLogin: {
                navigationPath = NavigationPath()
                // Force re-render to show login
                prefs.token = ""
                prefs.biometricEnabled = false
            }
        )
    }

    private var loginView: some View {
        LoginView(prefs: prefs) {
            // On login success, reset nav to show dashboard
            navigationPath = NavigationPath()
        }
    }

    private var dashboardView: some View {
        DashboardView(
            service: service,
            onNavigateToLog: {
                navigationPath.append(AppRoute.log)
            },
            onNavigateToSettings: {
                navigationPath.append(AppRoute.settings)
            }
        )
    }
}

// MARK: - Preview

#if DEBUG
struct AppNavigation_Previews: PreviewProvider {
    static var previews: some View {
        AppNavigation(
            prefs: Prefs(),
            service: BridgeService(prefs: Prefs())
        )
    }
}
#endif
