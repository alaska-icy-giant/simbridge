// BiometricPromptView.swift
// SimBridgeClient
//
// Shown when biometric unlock is enabled. Triggers Face ID / Touch ID
// and navigates to dashboard on success or falls back to login.

import SwiftUI
import LocalAuthentication

struct BiometricPromptView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.colorScheme) private var colorScheme

    private let secureTokenStore = SecureTokenStore()

    @State private var errorMessage: String?

    private var colors: AdaptiveColors {
        AdaptiveColors(colorScheme: colorScheme)
    }

    var body: some View {
        VStack(spacing: SimBridgeTheme.itemGap) {
            Spacer()

            Text("Biometric Unlock")
                .font(.title2.weight(.semibold))
                .foregroundColor(colors.onSurface)

            Text("Waiting for biometric authentication\u{2026}")
                .font(.body)
                .foregroundColor(colors.onSurfaceVariant)

            if let errorMessage = errorMessage {
                Text(errorMessage)
                    .font(.footnote)
                    .foregroundColor(colors.error)
            }

            Spacer()
                .frame(height: SimBridgeTheme.spacerLarge)

            Button {
                fallbackToLogin()
            } label: {
                Text("Use password instead")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(colors.primary, lineWidth: 1)
                    )
                    .foregroundColor(colors.primary)
            }

            Spacer()
        }
        .padding(SimBridgeTheme.screenPadding)
        .background(colors.surface.ignoresSafeArea())
        .onAppear {
            authenticate()
        }
    }

    private func authenticate() {
        let context = LAContext()
        var error: NSError?

        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            fallbackToLogin()
            return
        }

        context.evaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            localizedReason: "Unlock SimBridge"
        ) { success, authError in
            Task { @MainActor in
                if success {
                    if let token = secureTokenStore.getToken() {
                        Prefs.token = token
                        appState.token = token
                        appState.isLoggedIn = true
                        Prefs.isLoggedIn = true
                    } else {
                        Prefs.biometricEnabled = false
                        secureTokenStore.clear()
                        fallbackToLogin()
                    }
                } else if let authError = authError as? LAError,
                          authError.code == .userCancel || authError.code == .userFallback {
                    fallbackToLogin()
                } else {
                    errorMessage = authError?.localizedDescription ?? "Authentication failed"
                }
            }
        }
    }

    private func fallbackToLogin() {
        Prefs.biometricEnabled = false
        appState.isLoggedIn = false
        Prefs.isLoggedIn = false
    }
}
