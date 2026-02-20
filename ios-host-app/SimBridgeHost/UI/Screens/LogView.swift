// LogView.swift
// SimBridgeHost
//
// Scrollable log entries matching DESIGN_SYSTEM.md Log Screen spec.
// Each row: HH:mm:ss  [IN/OUT]  summary
// Monospace font. IN = primary color, OUT = secondary color.
// Newest entries first.

import SwiftUI

struct LogView: View {
    @ObservedObject var service: BridgeService

    @Environment(\.simBridgeColors) private var colors

    fileprivate static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        return formatter
    }()

    var body: some View {
        Group {
            if service.logs.isEmpty {
                VStack(spacing: SimBridgeSpacing.itemGap) {
                    Spacer()
                    Image(systemName: "list.bullet")
                        .font(.system(size: 48))
                        .foregroundColor(colors.onSurfaceVariant.opacity(0.5))
                    Text("No events yet. Commands and events will appear here.")
                        .font(.body)
                        .foregroundColor(colors.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                    Spacer()
                }
                .padding(SimBridgeSpacing.spacerLarge)
            } else {
                List {
                    ForEach(service.logs) { entry in
                        LogEntryRow(entry: entry, colors: colors)
                            .listRowInsets(EdgeInsets(
                                top: 2,
                                leading: SimBridgeSpacing.screenPadding,
                                bottom: 2,
                                trailing: SimBridgeSpacing.screenPadding
                            ))
                            .listRowSeparator(.hidden)
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("Event Log")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Log Entry Row

private struct LogEntryRow: View {
    let entry: LogEntry
    let colors: SimBridgeColors

    private var directionColor: Color {
        entry.direction == "IN" ? colors.primary : colors.secondary
    }

    var body: some View {
        HStack(spacing: SimBridgeSpacing.spacerSmall) {
            Text(LogView.timeFormatter.string(from: entry.timestamp))
                .font(.caption.monospaced())
                .foregroundColor(colors.onSurfaceVariant)

            Text(entry.direction)
                .font(.caption.monospaced().weight(.bold))
                .foregroundColor(directionColor)
                .frame(width: 30, alignment: .leading)

            Text(entry.summary)
                .font(.caption.monospaced())
                .foregroundColor(colors.onSurface)
                .lineLimit(2)

            Spacer()
        }
    }
}

// MARK: - Preview

#if DEBUG
struct LogView_Previews: PreviewProvider {
    static var previews: some View {
        let service = BridgeService(prefs: Prefs())
        // Add some sample log entries
        service.addLog(LogEntry(direction: "IN", summary: "CMD: SEND_SMS +1555012345"))
        service.addLog(LogEntry(direction: "OUT", summary: "SMS_SENT ok"))
        service.addLog(LogEntry(direction: "IN", summary: "CMD: GET_SIMS"))
        service.addLog(LogEntry(direction: "OUT", summary: "SIM_INFO"))

        return NavigationStack {
            LogView(service: service)
                .simBridgeTheme()
        }
    }
}
#endif
