// AppNavigation.swift
// NavigationStack routing: LOGIN -> PAIR -> DASHBOARD -> (COMPOSE | DIALER | HISTORY | SETTINGS)

import SwiftUI

struct AppNavigation: View {
    @EnvironmentObject private var appState: AppState

    private let secureTokenStore = SecureTokenStore()

    var body: some View {
        NavigationStack {
            Group {
                if Prefs.biometricEnabled && secureTokenStore.getToken() != nil && !appState.isLoggedIn {
                    BiometricPromptView()
                } else if !appState.isLoggedIn {
                    LoginView()
                } else if !appState.isPaired {
                    PairView()
                } else {
                    DashboardView()
                }
            }
            .navigationDestination(for: AppScreen.self) { screen in
                switch screen {
                case .compose:
                    ComposeView()
                case .dialer:
                    DialerView()
                case .history:
                    HistoryView()
                case .settings:
                    SettingsView()
                default:
                    EmptyView()
                }
            }
        }
    }
}

#if DEBUG
struct AppNavigation_Previews: PreviewProvider {
    static var previews: some View {
        AppNavigation()
            .environmentObject(AppState())
    }
}
#endif
