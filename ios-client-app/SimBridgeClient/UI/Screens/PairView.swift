// PairView.swift
// 6-digit pairing code input screen for linking with a Host device.

import SwiftUI

struct PairView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.colorScheme) private var colorScheme

    @State private var code: String = ""
    @State private var isLoading: Bool = false
    @State private var errorMessage: String = ""

    private var colors: AdaptiveColors {
        AdaptiveColors(colorScheme: colorScheme)
    }

    var body: some View {
        VStack(spacing: SimBridgeTheme.itemGap) {
            Spacer()

            Image(systemName: "link.circle.fill")
                .font(.system(size: 64))
                .foregroundColor(colors.primary)

            Text("Pair with Host Device")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(colors.onSurface)

            Text("Enter the 6-digit code shown on the Host device")
                .font(.body)
                .foregroundColor(colors.onSurface.opacity(0.7))
                .multilineTextAlignment(.center)
                .padding(.horizontal)

            Spacer().frame(height: SimBridgeTheme.spacerMedium)

            // 6-digit code input
            HStack(spacing: 12) {
                ForEach(0..<6, id: \.self) { index in
                    let char = codeCharacter(at: index)
                    Text(char)
                        .font(.system(size: 32, weight: .bold, design: .monospaced))
                        .foregroundColor(colors.onSurface)
                        .frame(width: 44, height: 56)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .fill(colors.surface)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(
                                    index < code.count ? colors.primary : colors.onSurface.opacity(0.2),
                                    lineWidth: 2
                                )
                        )
                }
            }

            // Hidden text field for actual input
            TextField("", text: $code)
                .keyboardType(.numberPad)
                .frame(width: 1, height: 1)
                .opacity(0.01)
                .onChange(of: code) { newValue in
                    // Limit to 6 digits only
                    let filtered = String(newValue.filter { $0.isNumber }.prefix(6))
                    if filtered != newValue {
                        code = filtered
                    }
                }

            // Tap the digits area to focus the hidden field
            Text("Tap here to enter code")
                .font(.footnote)
                .foregroundColor(colors.primary)

            // Error message
            if !errorMessage.isEmpty {
                Text(errorMessage)
                    .font(.footnote)
                    .foregroundColor(colors.error)
                    .multilineTextAlignment(.center)
            }

            Spacer().frame(height: SimBridgeTheme.spacerMedium)

            // Pair button
            Button {
                performPairing()
            } label: {
                HStack(spacing: 8) {
                    if isLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: colors.onPrimary))
                    }
                    Text("Pair")
                }
                .primaryButton(colors: colors)
            }
            .disabled(code.count != 6 || isLoading)
            .opacity((code.count != 6 || isLoading) ? 0.6 : 1.0)
            .padding(.horizontal, SimBridgeTheme.loginPadding)

            Spacer()
        }
        .padding(SimBridgeTheme.screenPadding)
        .background(colors.surface.ignoresSafeArea())
    }

    // MARK: - Helpers

    private func codeCharacter(at index: Int) -> String {
        let chars = Array(code)
        if index < chars.count {
            return String(chars[index])
        }
        return ""
    }

    // MARK: - Actions

    private func performPairing() {
        errorMessage = ""
        isLoading = true

        Task {
            do {
                let response = try await ApiClient.shared.confirmPair(
                    code: code,
                    clientDeviceId: appState.deviceId
                )

                // Find the host device name from the devices list
                var hostName = "Host Device"
                if let hostId = response.hostDeviceId {
                    let devices = try? await ApiClient.shared.listDevices()
                    if let host = devices?.first(where: { $0.id == hostId }) {
                        hostName = host.name
                    }
                    appState.completePairing(hostDeviceId: hostId, hostName: hostName)
                } else {
                    // already_paired case - we need to find the host
                    let devices = try await ApiClient.shared.listDevices()
                    if let host = devices.first(where: { $0.type == "host" }) {
                        appState.completePairing(hostDeviceId: host.id, hostName: host.name)
                    } else {
                        appState.isPaired = true
                        Prefs.isPaired = true
                    }
                }
            } catch {
                errorMessage = error.localizedDescription
            }
            isLoading = false
        }
    }
}

#if DEBUG
struct PairView_Previews: PreviewProvider {
    static var previews: some View {
        PairView()
            .environmentObject(AppState())
    }
}
#endif
