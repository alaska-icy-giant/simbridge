// BiometricPromptView.swift
// SimBridgeHost
//
// Shown when biometric unlock is enabled. Triggers Face ID / Touch ID
// and navigates to dashboard on success or falls back to login.

import SwiftUI
import LocalAuthentication

struct BiometricPromptView: View {
    @ObservedObject var prefs: Prefs
    let secureTokenStore: SecureTokenStore
    let onSuccess: () -> Void
    let onFallbackToLogin: () -> Void

    @Environment(\.simBridgeColors) private var colors

    @State private var errorMessage: String?

    var body: some View {
        VStack(spacing: SimBridgeSpacing.itemGap) {
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
                .frame(height: SimBridgeSpacing.spacerLarge)

            Button {
                onFallbackToLogin()
            } label: {
                Text("Use password instead")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.bordered)

            Spacer()
        }
        .padding(SimBridgeSpacing.screenPadding)
        .onAppear {
            authenticate()
        }
    }

    private func authenticate() {
        let context = LAContext()
        var error: NSError?

        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            onFallbackToLogin()
            return
        }

        context.evaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            localizedReason: "Unlock SimBridge"
        ) { success, authError in
            Task { @MainActor in
                if success {
                    if let token = secureTokenStore.getToken() {
                        prefs.token = token
                        onSuccess()
                    } else {
                        prefs.biometricEnabled = false
                        secureTokenStore.clear()
                        onFallbackToLogin()
                    }
                } else if let authError = authError as? LAError,
                          authError.code == .userCancel || authError.code == .userFallback {
                    onFallbackToLogin()
                } else {
                    errorMessage = authError?.localizedDescription ?? "Authentication failed"
                }
            }
        }
    }
}
