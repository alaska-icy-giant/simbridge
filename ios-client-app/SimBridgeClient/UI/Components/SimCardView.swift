// SimCardView.swift
// Remote SIM info card displaying carrier and number for a SIM slot.

import SwiftUI

struct SimCardView: View {
    let sim: SimInfo
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AdaptiveColors {
        AdaptiveColors(colorScheme: colorScheme)
    }

    var body: some View {
        HStack(spacing: SimBridgeTheme.iconTextGap) {
            Image(systemName: "simcard.fill")
                .font(.title2)
                .foregroundColor(colors.primary)

            VStack(alignment: .leading, spacing: 4) {
                Text("SIM \(sim.slot)")
                    .font(.headline)
                    .foregroundColor(colors.onSurface)

                if !sim.carrier.isEmpty {
                    Text(sim.carrier)
                        .font(.body)
                        .foregroundColor(colors.onSurface.opacity(0.7))
                }

                if !sim.number.isEmpty {
                    Text(sim.number)
                        .font(.footnote)
                        .foregroundColor(colors.onSurface.opacity(0.5))
                }
            }

            Spacer()
        }
        .padding(SimBridgeTheme.cardPaddingInfo)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.surface)
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.05), radius: 2, x: 0, y: 1)
    }
}

#if DEBUG
struct SimCardView_Previews: PreviewProvider {
    static var previews: some View {
        SimCardView(sim: SimInfo(slot: 1, carrier: "T-Mobile", number: "+1234567890"))
            .padding()
            .previewLayout(.sizeThatFits)
    }
}
#endif
