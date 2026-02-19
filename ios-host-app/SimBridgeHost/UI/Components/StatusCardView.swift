// StatusCardView.swift
// SimBridgeHost
//
// Connection status card (green/amber/red) matching DESIGN_SYSTEM.md StatusCard spec.
// Full-width card with status color at 10% alpha background.
// Content: centered row -- icon (32pt) + 12pt gap + label (title2 semibold).

import SwiftUI

struct StatusCardView: View {
    let status: ConnectionStatus

    @Environment(\.simBridgeColors) private var colors

    private var statusIcon: String {
        switch status {
        case .connected: return "checkmark.circle.fill"
        case .connecting: return "cloud.fill"
        case .disconnected: return "cloud.slash.fill"
        }
    }

    private var statusLabel: String {
        switch status {
        case .connected: return "Connected"
        case .connecting: return "Connecting..."
        case .disconnected: return "Offline"
        }
    }

    private var statusColor: Color {
        switch status {
        case .connected: return colors.primary
        case .connecting: return colors.tertiary
        case .disconnected: return colors.error
        }
    }

    var body: some View {
        HStack(spacing: SimBridgeSpacing.iconTextGap) {
            Image(systemName: statusIcon)
                .font(.system(size: SimBridgeSpacing.iconSizeStatus))
                .foregroundColor(statusColor)

            Text(statusLabel)
                .font(.title2.weight(.semibold))
                .foregroundColor(statusColor)
        }
        .frame(maxWidth: .infinity)
        .padding(SimBridgeSpacing.cardPaddingStatus)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(statusColor.opacity(0.1))
        )
    }
}

// MARK: - Preview

#if DEBUG
struct StatusCardView_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 16) {
            StatusCardView(status: .connected)
            StatusCardView(status: .connecting)
            StatusCardView(status: .disconnected)
        }
        .padding()
        .simBridgeTheme()
    }
}
#endif
