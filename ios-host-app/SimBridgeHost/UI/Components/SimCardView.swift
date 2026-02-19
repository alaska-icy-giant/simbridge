// SimCardView.swift
// SimBridgeHost
//
// SIM info display card matching DESIGN_SYSTEM.md SimCard spec.
// Full-width card on surfaceVariant.
// Row: SIM icon + column (slot header + carrier + number).
// 16pt internal padding.

import SwiftUI

struct SimCardView: View {
    let sim: SimInfo

    @Environment(\.simBridgeColors) private var colors

    var body: some View {
        HStack(spacing: SimBridgeSpacing.iconTextGap) {
            Image(systemName: "simcard.fill")
                .font(.system(size: 28))
                .foregroundColor(colors.primary)

            VStack(alignment: .leading, spacing: 2) {
                Text("SIM \(sim.slot) \u{2014} \(sim.carrier)")
                    .font(.headline)
                    .foregroundColor(colors.onSurface)

                if let number = sim.number, !number.isEmpty {
                    Text(number)
                        .font(.body)
                        .foregroundColor(colors.onSurfaceVariant)
                } else {
                    Text("Phone number not available")
                        .font(.caption)
                        .foregroundColor(colors.onSurfaceVariant)
                }
            }

            Spacer()
        }
        .padding(SimBridgeSpacing.cardPaddingInfo)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(colors.surfaceVariant.opacity(0.3))
        )
    }
}

// MARK: - Preview

#if DEBUG
struct SimCardView_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 16) {
            SimCardView(sim: SimInfo(slot: 1, carrier: "T-Mobile", number: "+1 555-0100"))
            SimCardView(sim: SimInfo(slot: 2, carrier: "AT&T", number: nil))
        }
        .padding()
        .simBridgeTheme()
    }
}
#endif
