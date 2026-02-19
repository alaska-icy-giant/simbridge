// StatusCardView.swift
// Connection status card component matching DESIGN_SYSTEM.md StatusCard spec.

import SwiftUI

struct StatusCardView: View {
    let status: ConnectionStatus
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AdaptiveColors {
        AdaptiveColors(colorScheme: colorScheme)
    }

    var body: some View {
        HStack(spacing: SimBridgeTheme.iconTextGap) {
            Image(systemName: colors.statusIcon(for: status))
                .font(.system(size: SimBridgeTheme.iconSizeStatus))
                .foregroundColor(colors.statusColor(for: status))

            Text(colors.statusLabel(for: status))
                .font(.title2)
                .fontWeight(.semibold)
                .foregroundColor(colors.statusColor(for: status))

            Spacer()
        }
        .padding(SimBridgeTheme.cardPaddingStatus)
        .frame(maxWidth: .infinity)
        .background(
            colors.statusColor(for: status).opacity(0.1)
        )
        .cornerRadius(12)
    }
}

#if DEBUG
struct StatusCardView_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 16) {
            StatusCardView(status: .connected)
            StatusCardView(status: .connecting)
            StatusCardView(status: .disconnected)
        }
        .padding()
        .previewLayout(.sizeThatFits)
    }
}
#endif
