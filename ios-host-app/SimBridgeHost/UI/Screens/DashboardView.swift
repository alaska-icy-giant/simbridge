// DashboardView.swift
// SimBridgeHost
//
// Dashboard screen matching DESIGN_SYSTEM.md Dashboard spec.
// TopAppBar: app title + log icon + settings icon.
// Scrollable column: StatusCard -> Start/Stop button -> SIM list.
// Start button: primary colors. Stop button: error colors.
// Empty SIM state: surfaceVariant card with guidance text.

import SwiftUI

struct DashboardView: View {
    @ObservedObject var service: BridgeService
    let onNavigateToLog: () -> Void
    let onNavigateToSettings: () -> Void

    @Environment(\.simBridgeColors) private var colors

    @State private var sims: [SimInfo] = []

    var body: some View {
        ScrollView {
            VStack(spacing: SimBridgeSpacing.itemGap) {
                // Connection status card
                StatusCardView(status: service.connectionStatus)

                // Start/Stop button
                Button {
                    if service.isRunning {
                        service.stop()
                    } else {
                        service.start()
                    }
                } label: {
                    Text(service.isRunning ? "Stop Service" : "Start Service")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .tint(service.isRunning ? colors.error : colors.primary)

                // SIM info section
                if !sims.isEmpty {
                    VStack(alignment: .leading, spacing: SimBridgeSpacing.spacerSmall) {
                        Text("SIM Cards")
                            .font(.headline)
                            .foregroundColor(colors.onSurface)
                            .padding(.top, SimBridgeSpacing.spacerSmall)

                        ForEach(sims) { sim in
                            SimCardView(sim: sim)
                        }
                    }
                } else {
                    // Empty SIM state
                    VStack(spacing: SimBridgeSpacing.spacerSmall) {
                        Image(systemName: "simcard.fill")
                            .font(.system(size: 32))
                            .foregroundColor(colors.onSurfaceVariant)

                        Text("No SIM cards detected. On iOS, only carrier name is available (no phone numbers or slot info).")
                            .font(.body)
                            .foregroundColor(colors.onSurfaceVariant)
                            .multilineTextAlignment(.center)
                    }
                    .padding(SimBridgeSpacing.cardPaddingInfo)
                    .frame(maxWidth: .infinity)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(colors.surfaceVariant.opacity(0.3))
                    )
                }
            }
            .padding(SimBridgeSpacing.screenPadding)
        }
        .navigationTitle("SimBridge Host")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                Button(action: onNavigateToLog) {
                    Image(systemName: "list.bullet")
                }
                Button(action: onNavigateToSettings) {
                    Image(systemName: "gearshape.fill")
                }
            }
        }
        .onAppear {
            loadSimCards()
        }
        .onChange(of: service.isRunning) { _ in
            loadSimCards()
        }
    }

    private func loadSimCards() {
        sims = service.getSimCards()
    }
}

// MARK: - Preview

#if DEBUG
struct DashboardView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationStack {
            DashboardView(
                service: BridgeService(prefs: Prefs()),
                onNavigateToLog: {},
                onNavigateToSettings: {}
            )
            .simBridgeTheme()
        }
    }
}
#endif
