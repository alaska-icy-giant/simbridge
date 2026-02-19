// SettingsView.swift
// Server info + device info + paired host info + logout with confirmation.

import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.colorScheme) private var colorScheme

    @State private var showLogoutConfirmation: Bool = false

    private var colors: AdaptiveColors {
        AdaptiveColors(colorScheme: colorScheme)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: SimBridgeTheme.itemGap) {

                // Server Info Card
                settingsCard(title: "Server") {
                    infoRow(label: "URL", value: Prefs.serverUrl)
                    infoRow(label: "Status", value: appState.connectionStatus.rawValue.capitalized)
                }

                // Device Info Card
                settingsCard(title: "This Device") {
                    infoRow(label: "Name", value: appState.deviceName)
                    infoRow(label: "Device ID", value: "\(appState.deviceId)")
                    infoRow(label: "Type", value: "Client")
                }

                // Paired Host Info Card
                settingsCard(title: "Paired Host") {
                    infoRow(
                        label: "Name",
                        value: appState.pairedHostName.isEmpty ? "Not paired" : appState.pairedHostName
                    )
                    infoRow(label: "Host ID", value: "\(appState.pairedHostId)")
                    infoRow(label: "Online", value: appState.isHostOnline ? "Yes" : "No")
                }

                // Background App Refresh
                settingsCard(title: "Background") {
                    HStack {
                        Image(systemName: "arrow.clockwise.circle.fill")
                            .foregroundColor(colors.primary)
                        Text("Background App Refresh")
                            .font(.body)
                            .foregroundColor(colors.onSurface)
                        Spacer()
                        Text("System Settings")
                            .font(.footnote)
                            .foregroundColor(colors.onSurface.opacity(0.4))
                    }
                }

                Spacer().frame(height: SimBridgeTheme.spacerMedium)

                // Logout Button
                Button {
                    showLogoutConfirmation = true
                } label: {
                    HStack {
                        Image(systemName: "rectangle.portrait.and.arrow.right")
                        Text("Logout")
                    }
                    .errorButton(colors: colors)
                }

                Spacer()
            }
            .padding(SimBridgeTheme.screenPadding)
        }
        .background(colors.surface.ignoresSafeArea())
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Logout", isPresented: $showLogoutConfirmation) {
            Button("Cancel", role: .cancel) {}
            Button("Logout", role: .destructive) {
                appState.logout()
            }
        } message: {
            Text("Are you sure you want to logout? This will disconnect from the relay server and clear all local data.")
        }
    }

    // MARK: - Components

    private func settingsCard(
        title: String,
        @ViewBuilder content: () -> some View
    ) -> some View {
        VStack(alignment: .leading, spacing: SimBridgeTheme.spacerSmall) {
            Text(title)
                .font(.headline)
                .foregroundColor(colors.onSurface)

            VStack(spacing: SimBridgeTheme.spacerSmall) {
                content()
            }
            .cardStyle(colors: colors)
        }
    }

    private func infoRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.body)
                .foregroundColor(colors.onSurface.opacity(0.6))
            Spacer()
            Text(value.isEmpty ? "-" : value)
                .font(.body)
                .foregroundColor(colors.onSurface)
                .lineLimit(1)
                .truncationMode(.middle)
        }
    }
}

#if DEBUG
struct SettingsView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationStack {
            SettingsView()
                .environmentObject(AppState())
        }
    }
}
#endif
