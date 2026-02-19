// DialerView.swift
// Phone number field + SIM selector + Call/Hang Up button + call state display.

import SwiftUI

struct DialerView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.colorScheme) private var colorScheme

    @State private var phoneNumber: String = ""
    @State private var selectedSim: Int = 1
    @State private var isLoading: Bool = false
    @State private var errorMessage: String = ""

    private var colors: AdaptiveColors {
        AdaptiveColors(colorScheme: colorScheme)
    }

    private var isCallActive: Bool {
        let state = appState.callState.lowercased()
        return state == "dialing" || state == "active"
    }

    var body: some View {
        VStack(spacing: SimBridgeTheme.itemGap) {

            Spacer().frame(height: SimBridgeTheme.spacerLarge)

            // Phone number display
            Text(phoneNumber.isEmpty ? "Enter Number" : phoneNumber)
                .font(.system(size: 32, weight: .light, design: .monospaced))
                .foregroundColor(phoneNumber.isEmpty ? colors.onSurface.opacity(0.3) : colors.onSurface)
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.horizontal)

            // Phone number input
            TextField("Phone number", text: $phoneNumber)
                .textFieldStyle(.roundedBorder)
                .keyboardType(.phonePad)
                .padding(.horizontal, SimBridgeTheme.screenPadding)

            // SIM selector
            VStack(alignment: .leading, spacing: 6) {
                Text("SIM Card")
                    .font(.footnote)
                    .foregroundColor(colors.onSurface.opacity(0.6))

                Picker("SIM", selection: $selectedSim) {
                    if appState.sims.isEmpty {
                        Text("SIM 1").tag(1)
                        Text("SIM 2").tag(2)
                    } else {
                        ForEach(appState.sims) { sim in
                            Text("SIM \(sim.slot) - \(sim.carrier)")
                                .tag(sim.slot)
                        }
                    }
                }
                .pickerStyle(.segmented)
            }
            .padding(.horizontal, SimBridgeTheme.screenPadding)

            // Call state card
            if !appState.callState.isEmpty {
                callStateCard
            }

            // Error message
            if !errorMessage.isEmpty {
                Text(errorMessage)
                    .font(.footnote)
                    .foregroundColor(colors.error)
                    .padding(.horizontal)
            }

            Spacer()

            // Call / Hang Up buttons
            VStack(spacing: SimBridgeTheme.spacerMedium) {
                if isCallActive {
                    // Hang Up button
                    Button {
                        hangUp()
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "phone.down.fill")
                            Text("Hang Up")
                        }
                        .errorButton(colors: colors)
                    }
                    .padding(.horizontal, SimBridgeTheme.screenPadding)
                } else {
                    // Call button
                    Button {
                        makeCall()
                    } label: {
                        HStack(spacing: 8) {
                            if isLoading {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: colors.onPrimary))
                            }
                            Image(systemName: "phone.fill")
                            Text("Call")
                        }
                        .primaryButton(colors: colors)
                    }
                    .disabled(isLoading || phoneNumber.isEmpty)
                    .opacity((isLoading || phoneNumber.isEmpty) ? 0.6 : 1.0)
                    .padding(.horizontal, SimBridgeTheme.screenPadding)
                }
            }

            Spacer().frame(height: SimBridgeTheme.spacerLarge)
        }
        .background(colors.surface.ignoresSafeArea())
        .navigationTitle("Dialer")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Call State Card

    private var callStateCard: some View {
        let state = appState.callState.lowercased()
        let icon: String
        let color: Color
        let label: String

        switch state {
        case "dialing":
            icon = "phone.arrow.up.right.fill"
            color = colors.primary
            label = "Dialing..."
        case "active":
            icon = "phone.fill"
            color = colors.secondary
            label = "Call Active"
        case "ended":
            icon = "phone.down.fill"
            color = colors.error
            label = "Call Ended"
        default:
            icon = "phone.fill"
            color = colors.onSurface.opacity(0.5)
            label = appState.callState.capitalized
        }

        return HStack(spacing: SimBridgeTheme.iconTextGap) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(color)

            Text(label)
                .font(.title3)
                .fontWeight(.semibold)
                .foregroundColor(color)

            Spacer()
        }
        .padding(SimBridgeTheme.cardPaddingStatus)
        .background(color.opacity(0.1))
        .cornerRadius(12)
        .padding(.horizontal, SimBridgeTheme.screenPadding)
    }

    // MARK: - Actions

    private func makeCall() {
        errorMessage = ""
        isLoading = true

        let command = CallCommand(
            toDeviceId: appState.pairedHostId,
            sim: selectedSim,
            to: phoneNumber
        )

        Task {
            do {
                _ = try await ApiClient.shared.makeCall(command)
                appState.callState = "dialing"
            } catch {
                errorMessage = error.localizedDescription
            }
            isLoading = false
        }
    }

    private func hangUp() {
        // Send hang up command via WebSocket
        // The server does not currently have a /hangup REST endpoint,
        // so we send via WS or just clear the local state.
        appState.callState = "ended"

        // Clear after a delay
        Task {
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            await MainActor.run {
                if appState.callState == "ended" {
                    appState.callState = ""
                }
            }
        }
    }
}

#if DEBUG
struct DialerView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationStack {
            DialerView()
                .environmentObject(AppState())
        }
    }
}
#endif
