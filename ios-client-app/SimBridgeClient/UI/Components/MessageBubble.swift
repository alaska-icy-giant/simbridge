// MessageBubble.swift
// SMS conversation bubble with sent/received styling.

import SwiftUI

struct MessageBubble: View {
    let text: String
    let isSent: Bool
    let timestamp: String

    @Environment(\.colorScheme) private var colorScheme

    private var colors: AdaptiveColors {
        AdaptiveColors(colorScheme: colorScheme)
    }

    var body: some View {
        HStack {
            if isSent { Spacer(minLength: 60) }

            VStack(alignment: isSent ? .trailing : .leading, spacing: 4) {
                Text(text)
                    .font(.body)
                    .foregroundColor(isSent ? colors.onPrimary : colors.onSurface)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(
                        isSent
                            ? colors.primary
                            : colors.surface
                    )
                    .cornerRadius(18)
                    .shadow(color: .black.opacity(0.05), radius: 1, x: 0, y: 1)

                if !timestamp.isEmpty {
                    Text(timestamp)
                        .font(.caption2)
                        .foregroundColor(colors.onSurface.opacity(0.4))
                }
            }

            if !isSent { Spacer(minLength: 60) }
        }
    }
}

#if DEBUG
struct MessageBubble_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 12) {
            MessageBubble(text: "Hello, how are you?", isSent: false, timestamp: "10:30 AM")
            MessageBubble(text: "I'm doing great, thanks!", isSent: true, timestamp: "10:31 AM")
            MessageBubble(
                text: "This is a longer message to test wrapping behavior in the bubble component.",
                isSent: false,
                timestamp: "10:32 AM"
            )
        }
        .padding()
        .previewLayout(.sizeThatFits)
    }
}
#endif
