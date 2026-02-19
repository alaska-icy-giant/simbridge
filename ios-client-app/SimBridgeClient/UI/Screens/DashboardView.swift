// DashboardView.swift
// Main hub: StatusCard + paired host info + remote SIMs + action buttons + event feed.

import SwiftUI

struct DashboardView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.colorScheme) private var colorScheme

    @State private var isLoadingSims: Bool = false

    private var colors: AdaptiveColors {
        AdaptiveColors(colorScheme: colorScheme)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: SimBridgeTheme.itemGap) {
                // Connection Status Card
                StatusCardView(status: appState.connectionStatus)

                // Paired Host Card
                pairedHostCard

                // SIM Cards
                simCardsSection

                // Action Buttons
                actionButtons

                // Recent Events Feed
                eventFeedSection
            }
            .padding(SimBridgeTheme.screenPadding)
        }
        .background(colors.surface.ignoresSafeArea())
        .navigationTitle("SimBridge Client")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 16) {
                    NavigationLink(value: AppScreen.history) {
                        Image(systemName: "list.bullet")
                            .foregroundColor(colors.primary)
                    }
                    NavigationLink(value: AppScreen.settings) {
                        Image(systemName: "gearshape.fill")
                            .foregroundColor(colors.primary)
                    }
                }
            }
        }
        .onAppear {
            appState.connectWebSocket()
            fetchSims()
        }
        .alert("Error", isPresented: $appState.showAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(appState.alertMessage)
        }
    }

    // MARK: - Paired Host Card

    private var pairedHostCard: some View {
        HStack(spacing: SimBridgeTheme.iconTextGap) {
            Image(systemName: "desktopcomputer")
                .font(.title2)
                .foregroundColor(colors.primary)

            VStack(alignment: .leading, spacing: 4) {
                Text("Paired Host")
                    .font(.footnote)
                    .foregroundColor(colors.onSurface.opacity(0.6))

                Text(appState.pairedHostName.isEmpty ? "Unknown Host" : appState.pairedHostName)
                    .font(.headline)
                    .foregroundColor(colors.onSurface)

                Text(appState.isHostOnline ? "Online" : "Offline")
                    .font(.footnote)
                    .foregroundColor(appState.isHostOnline ? colors.secondary : colors.onSurface.opacity(0.4))
            }

            Spacer()

            Text("ID: \(appState.pairedHostId)")
                .font(.caption)
                .foregroundColor(colors.onSurface.opacity(0.4))
        }
        .cardStyle(colors: colors)
    }

    // MARK: - SIM Cards Section

    private var simCardsSection: some View {
        VStack(alignment: .leading, spacing: SimBridgeTheme.spacerSmall) {
            HStack {
                Text("Remote SIMs")
                    .font(.headline)
                    .foregroundColor(colors.onSurface)

                Spacer()

                if isLoadingSims {
                    ProgressView()
                        .scaleEffect(0.8)
                }

                Button {
                    fetchSims()
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .foregroundColor(colors.primary)
                }
            }

            if appState.sims.isEmpty {
                HStack {
                    Spacer()
                    VStack(spacing: 8) {
                        Image(systemName: "simcard.fill")
                            .font(.title)
                            .foregroundColor(colors.onSurface.opacity(0.3))
                        Text("No SIM info available")
                            .font(.body)
                            .foregroundColor(colors.onSurface.opacity(0.5))
                        Text("SIM data will appear when the Host is online")
                            .font(.footnote)
                            .foregroundColor(colors.onSurface.opacity(0.3))
                    }
                    Spacer()
                }
                .padding(SimBridgeTheme.cardPaddingInfo)
                .background(colors.surface)
                .cornerRadius(12)
            } else {
                ForEach(appState.sims) { sim in
                    SimCardView(sim: sim)
                }
            }
        }
    }

    // MARK: - Action Buttons

    private var actionButtons: some View {
        HStack(spacing: SimBridgeTheme.itemGap) {
            NavigationLink(value: AppScreen.compose) {
                HStack {
                    Image(systemName: "message.fill")
                    Text("Send SMS")
                }
                .primaryButton(colors: colors)
            }

            NavigationLink(value: AppScreen.dialer) {
                HStack {
                    Image(systemName: "phone.fill")
                    Text("Make Call")
                }
                .primaryButton(colors: colors)
            }
        }
    }

    // MARK: - Event Feed

    private var eventFeedSection: some View {
        VStack(alignment: .leading, spacing: SimBridgeTheme.spacerSmall) {
            Text("Recent Events")
                .font(.headline)
                .foregroundColor(colors.onSurface)

            if appState.eventFeed.isEmpty {
                Text("No recent events")
                    .font(.body)
                    .foregroundColor(colors.onSurface.opacity(0.4))
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, SimBridgeTheme.spacerMedium)
            } else {
                ForEach(appState.eventFeed) { event in
                    HStack(spacing: SimBridgeTheme.spacerSmall) {
                        Image(systemName: event.icon)
                            .foregroundColor(event.color)
                            .frame(width: 24)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(event.title)
                                .font(.footnote)
                                .fontWeight(.medium)
                                .foregroundColor(colors.onSurface)

                            Text(event.detail)
                                .font(.caption)
                                .foregroundColor(colors.onSurface.opacity(0.6))
                                .lineLimit(1)
                        }

                        Spacer()

                        Text(formatTime(event.timestamp))
                            .font(.caption2)
                            .foregroundColor(colors.onSurface.opacity(0.4))
                    }
                    .padding(.vertical, 4)
                }
            }
        }
    }

    // MARK: - Actions

    private func fetchSims() {
        guard appState.pairedHostId > 0 else { return }
        isLoadingSims = true

        Task {
            do {
                _ = try await ApiClient.shared.getSims(hostDeviceId: appState.pairedHostId)
                // SIM info will arrive via WebSocket SIM_INFO event
            } catch {
                // Host may be offline — that is expected
                print("Failed to fetch SIMs: \(error.localizedDescription)")
            }
            isLoadingSims = false
        }
    }

    private func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        return formatter.string(from: date)
    }
}

#if DEBUG
struct DashboardView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationStack {
            DashboardView()
                .environmentObject(AppState())
        }
    }
}
#endif
