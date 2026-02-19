// ComposeView.swift
// Compose and send SMS through the paired Host phone's SIM.

import SwiftUI

struct ComposeView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss

    @State private var toNumber: String = ""
    @State private var messageBody: String = ""
    @State private var selectedSim: Int = 1
    @State private var isLoading: Bool = false
    @State private var errorMessage: String = ""
    @State private var successMessage: String = ""

    private var colors: AdaptiveColors {
        AdaptiveColors(colorScheme: colorScheme)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: SimBridgeTheme.itemGap) {

                // To field
                VStack(alignment: .leading, spacing: 6) {
                    Text("To")
                        .font(.footnote)
                        .foregroundColor(colors.onSurface.opacity(0.6))
                    TextField("Phone number", text: $toNumber)
                        .textFieldStyle(.roundedBorder)
                        .keyboardType(.phonePad)
                }

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

                // Message body
                VStack(alignment: .leading, spacing: 6) {
                    Text("Message")
                        .font(.footnote)
                        .foregroundColor(colors.onSurface.opacity(0.6))

                    TextEditor(text: $messageBody)
                        .frame(minHeight: 120)
                        .padding(8)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                        )
                        .font(.body)

                    Text("\(messageBody.count)/1600")
                        .font(.caption)
                        .foregroundColor(
                            messageBody.count > 1600
                                ? colors.error
                                : colors.onSurface.opacity(0.4)
                        )
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }

                // Error message
                if !errorMessage.isEmpty {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundColor(colors.error)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                // Success message
                if !successMessage.isEmpty {
                    HStack {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(colors.secondary)
                        Text(successMessage)
                            .font(.footnote)
                            .foregroundColor(colors.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                // Send button
                Button {
                    sendSms()
                } label: {
                    HStack(spacing: 8) {
                        if isLoading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: colors.onPrimary))
                        }
                        Image(systemName: "paperplane.fill")
                        Text("Send")
                    }
                    .primaryButton(colors: colors)
                }
                .disabled(isLoading || toNumber.isEmpty || messageBody.isEmpty || messageBody.count > 1600)
                .opacity((isLoading || toNumber.isEmpty || messageBody.isEmpty) ? 0.6 : 1.0)

                Spacer()
            }
            .padding(SimBridgeTheme.screenPadding)
        }
        .background(colors.surface.ignoresSafeArea())
        .navigationTitle("Compose SMS")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Actions

    private func sendSms() {
        errorMessage = ""
        successMessage = ""
        isLoading = true

        let command = SmsCommand(
            toDeviceId: appState.pairedHostId,
            sim: selectedSim,
            to: toNumber,
            body: messageBody
        )

        Task {
            do {
                let response = try await ApiClient.shared.sendSms(command)
                successMessage = "SMS sent successfully (ID: \(response.reqId ?? "n/a"))"
                // Clear the form
                messageBody = ""
            } catch {
                errorMessage = error.localizedDescription
            }
            isLoading = false
        }
    }
}

#if DEBUG
struct ComposeView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationStack {
            ComposeView()
                .environmentObject(AppState())
        }
    }
}
#endif
