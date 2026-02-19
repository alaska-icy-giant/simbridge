// SettingsView.swift
// SimBridgeHost
//
// Settings screen matching DESIGN_SYSTEM.md Settings Screen spec.
// Info cards for server URL, device name/ID.
// Background App Refresh row (iOS equivalent of battery optimization).
// iOS Limitations section documenting platform differences.
// Destructive logout button with confirmation alert.

import SwiftUI
import LocalAuthentication

struct SettingsView: View {
    @ObservedObject var prefs: Prefs
    let onLogout: () -> Void

    @Environment(\.simBridgeColors) private var colors
    @Environment(\.openURL) private var openURL

    @State private var showLogoutAlert = false
    @State private var biometricEnabled: Bool = false

    private let secureTokenStore = SecureTokenStore()

    private var canUseBiometric: Bool {
        let context = LAContext()
        return context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: SimBridgeSpacing.itemGap) {
                // Server info card
                InfoCard(title: "Server", colors: colors) {
                    Text(prefs.serverUrl.isEmpty ? "Not configured" : prefs.serverUrl)
                        .font(.body)
                        .foregroundColor(colors.onSurfaceVariant)
                }

                // Device info card
                InfoCard(title: "Device", colors: colors) {
                    Text(prefs.deviceName.isEmpty ? "Not registered" : prefs.deviceName)
                        .font(.body)
                        .foregroundColor(colors.onSurfaceVariant)

                    if prefs.deviceId >= 0 {
                        Text("ID: \(prefs.deviceId)")
                            .font(.footnote)
                            .foregroundColor(colors.onSurfaceVariant)
                    }
                }

                // Background App Refresh card
                InfoCard(title: "Background App Refresh", colors: colors) {
                    Text("Enable Background App Refresh in iOS Settings to keep SimBridge connected when the app is in the background.")
                        .font(.body)
                        .foregroundColor(colors.onSurfaceVariant)

                    Button("Open Settings") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            openURL(url)
                        }
                    }
                    .font(.body)
                    .padding(.top, 4)
                }

                // Biometric Unlock
                if canUseBiometric {
                    InfoCard(title: "Biometric Unlock", colors: colors) {
                        Toggle(isOn: $biometricEnabled) {
                            Text("Use fingerprint or face recognition to unlock the app.")
                                .font(.body)
                                .foregroundColor(colors.onSurfaceVariant)
                        }
                        .onChange(of: biometricEnabled) { enabled in
                            if enabled {
                                secureTokenStore.saveToken(prefs.token)
                                prefs.biometricEnabled = true
                            } else {
                                secureTokenStore.clear()
                                prefs.biometricEnabled = false
                            }
                        }
                    }
                }

                // iOS Limitations card
                InfoCard(title: "iOS Limitations", colors: colors) {
                    VStack(alignment: .leading, spacing: SimBridgeSpacing.spacerSmall) {
                        LimitationRow(
                            feature: "Background SMS send",
                            detail: "Not possible on App Store builds. MFMessageComposeViewController requires user interaction."
                        )
                        LimitationRow(
                            feature: "Incoming SMS intercept",
                            detail: "Not possible on App Store builds. No public API for reading incoming SMS."
                        )
                        LimitationRow(
                            feature: "SIM slot / phone number",
                            detail: "Only carrier name available via CTTelephonyNetworkInfo. No slot numbers or phone numbers."
                        )
                        LimitationRow(
                            feature: "Persistent background",
                            detail: "Limited. Use BGTaskScheduler + VoIP push for background connectivity."
                        )
                        LimitationRow(
                            feature: "Call placement",
                            detail: "Uses CallKit CXStartCallAction. Functionally equivalent to Android."
                        )
                    }
                }

                Spacer()
                    .frame(height: SimBridgeSpacing.itemGap)

                // Logout button
                Button {
                    showLogoutAlert = true
                } label: {
                    Text("Logout")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .tint(colors.error)
            }
            .padding(SimBridgeSpacing.screenPadding)
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            biometricEnabled = prefs.biometricEnabled
        }
        .alert("Logout", isPresented: $showLogoutAlert) {
            Button("Cancel", role: .cancel) {}
            Button("Logout", role: .destructive) {
                onLogout()
            }
        } message: {
            Text("This will stop the bridge service and clear your credentials.")
        }
    }
}

// MARK: - Info Card

private struct InfoCard<Content: View>: View {
    let title: String
    let colors: SimBridgeColors
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.headline)
                .foregroundColor(colors.onSurface)

            content
        }
        .padding(SimBridgeSpacing.cardPaddingInfo)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(colors.surfaceVariant.opacity(0.2))
        )
    }
}

// MARK: - Limitation Row

private struct LimitationRow: View {
    let feature: String
    let detail: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(feature)
                .font(.footnote.weight(.semibold))
            Text(detail)
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding(.vertical, 2)
    }
}

// MARK: - Preview

#if DEBUG
struct SettingsView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationStack {
            SettingsView(prefs: Prefs(), onLogout: {})
                .simBridgeTheme()
        }
    }
}
#endif
