// HistoryView.swift
// Message/call history list with pull-to-refresh, type badges, and timestamps.

import SwiftUI

struct HistoryView: View {
    @EnvironmentObject private var appState: AppState
    @Environment(\.colorScheme) private var colorScheme

    @State private var entries: [HistoryEntry] = []
    @State private var isLoading: Bool = false
    @State private var errorMessage: String = ""
    @State private var selectedEntry: HistoryEntry?

    private var colors: AdaptiveColors {
        AdaptiveColors(colorScheme: colorScheme)
    }

    var body: some View {
        Group {
            if isLoading && entries.isEmpty {
                VStack {
                    Spacer()
                    ProgressView("Loading history...")
                    Spacer()
                }
            } else if entries.isEmpty && !isLoading {
                VStack(spacing: SimBridgeTheme.itemGap) {
                    Spacer()
                    Image(systemName: "clock.arrow.circlepath")
                        .font(.system(size: 48))
                        .foregroundColor(colors.onSurface.opacity(0.3))
                    Text("No history yet")
                        .font(.headline)
                        .foregroundColor(colors.onSurface.opacity(0.5))
                    Text("Messages and calls will appear here")
                        .font(.body)
                        .foregroundColor(colors.onSurface.opacity(0.3))
                    Spacer()
                }
            } else {
                List {
                    ForEach(entries) { entry in
                        Button {
                            selectedEntry = entry
                        } label: {
                            historyRow(entry)
                        }
                        .listRowBackground(colors.surface)
                    }
                }
                .listStyle(.plain)
                .refreshable {
                    await loadHistory()
                }
            }
        }
        .background(colors.surface.ignoresSafeArea())
        .navigationTitle("History")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            Task { await loadHistory() }
        }
        .sheet(item: $selectedEntry) { entry in
            historyDetailSheet(entry)
        }
    }

    // MARK: - History Row

    private func historyRow(_ entry: HistoryEntry) -> some View {
        HStack(spacing: SimBridgeTheme.spacerSmall) {
            // Direction icon
            directionIcon(entry)

            // Type badge
            typeBadge(entry)

            // Summary
            VStack(alignment: .leading, spacing: 2) {
                Text(entrySummary(entry))
                    .font(.body)
                    .foregroundColor(colors.onSurface)
                    .lineLimit(1)

                if let createdAt = entry.createdAt {
                    Text(formatTimestamp(createdAt))
                        .font(.caption)
                        .foregroundColor(colors.onSurface.opacity(0.4))
                }
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundColor(colors.onSurface.opacity(0.3))
        }
        .padding(.vertical, 4)
    }

    private func directionIcon(_ entry: HistoryEntry) -> some View {
        let isOutgoing = entry.fromDeviceId == appState.deviceId
        return Image(systemName: isOutgoing ? "arrow.up.right" : "arrow.down.left")
            .font(.caption)
            .fontWeight(.bold)
            .foregroundColor(isOutgoing ? colors.secondary : colors.primary)
            .frame(width: 24, height: 24)
    }

    private func typeBadge(_ entry: HistoryEntry) -> some View {
        let msgType = entry.msgType.uppercased()
        let icon: String
        let label: String

        if msgType.contains("COMMAND") || msgType.contains("SMS") {
            icon = "message.fill"
            label = "SMS"
        } else if msgType.contains("CALL") {
            icon = "phone.fill"
            label = "CALL"
        } else {
            icon = "doc.fill"
            label = msgType.prefix(4).uppercased()
        }

        return HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.caption2)
            Text(label)
                .font(.caption2)
                .fontWeight(.semibold)
        }
        .foregroundColor(colors.primary)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(colors.primary.opacity(0.1))
        .cornerRadius(6)
    }

    // MARK: - Detail Sheet

    private func historyDetailSheet(_ entry: HistoryEntry) -> some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: SimBridgeTheme.itemGap) {
                    infoRow("ID", value: "\(entry.id)")
                    infoRow("Type", value: entry.msgType)
                    infoRow("From Device", value: "\(entry.fromDeviceId)")
                    infoRow("To Device", value: "\(entry.toDeviceId)")
                    if let ts = entry.createdAt {
                        infoRow("Timestamp", value: ts)
                    }

                    Divider()

                    Text("Payload")
                        .font(.headline)
                        .foregroundColor(colors.onSurface)

                    Text(payloadString(entry.payload))
                        .font(.system(.caption, design: .monospaced))
                        .foregroundColor(colors.onSurface.opacity(0.8))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(colors.onSurface.opacity(0.05))
                        .cornerRadius(8)
                }
                .padding(SimBridgeTheme.screenPadding)
            }
            .navigationTitle("Log Detail")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        selectedEntry = nil
                    }
                }
            }
        }
    }

    private func infoRow(_ label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.footnote)
                .foregroundColor(colors.onSurface.opacity(0.6))
            Spacer()
            Text(value)
                .font(.footnote)
                .foregroundColor(colors.onSurface)
        }
    }

    // MARK: - Helpers

    private func entrySummary(_ entry: HistoryEntry) -> String {
        return entry.payload.displaySummary
    }

    private func formatTimestamp(_ iso: String) -> String {
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = isoFormatter.date(from: iso) {
            let displayFormatter = DateFormatter()
            displayFormatter.dateFormat = "MMM d, HH:mm:ss"
            return displayFormatter.string(from: date)
        }
        // Try without fractional seconds
        isoFormatter.formatOptions = [.withInternetDateTime]
        if let date = isoFormatter.date(from: iso) {
            let displayFormatter = DateFormatter()
            displayFormatter.dateFormat = "MMM d, HH:mm:ss"
            return displayFormatter.string(from: date)
        }
        return iso
    }

    private func payloadString(_ payload: AnyCodableValue) -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        if let data = try? encoder.encode(payload),
           let string = String(data: data, encoding: .utf8) {
            return string
        }
        return "Unable to display payload"
    }

    // MARK: - Data Loading

    private func loadHistory() async {
        isLoading = true
        errorMessage = ""

        do {
            entries = try await ApiClient.shared.getHistory(limit: 50)
        } catch {
            errorMessage = error.localizedDescription
        }

        isLoading = false
    }
}

#if DEBUG
struct HistoryView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationStack {
            HistoryView()
                .environmentObject(AppState())
        }
    }
}
#endif
