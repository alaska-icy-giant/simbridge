// SimBridgeTheme.swift
// Shared design system colors, spacing, and ViewModifiers for SimBridge Client.
// Matches DESIGN_SYSTEM.md exactly.

import SwiftUI

// MARK: - Color Palette

enum SimBridgeTheme {

    // Primary
    static let primaryColor = Color("Primary", bundle: nil)
    static let onPrimaryColor = Color("OnPrimary", bundle: nil)
    static let primaryContainerColor = Color("PrimaryContainer", bundle: nil)

    // Secondary
    static let secondaryColor = Color("Secondary", bundle: nil)
    static let onSecondaryColor = Color("OnSecondary", bundle: nil)

    // Error
    static let errorColor = Color("Error", bundle: nil)

    // Surface
    static let surfaceColor = Color("Surface", bundle: nil)
    static let onSurfaceColor = Color("OnSurface", bundle: nil)

    // Fallback colors using hex values from DESIGN_SYSTEM.md
    static let primaryLight = Color(hex: 0x1976D2)
    static let primaryDark = Color(hex: 0x90CAF9)
    static let onPrimaryLight = Color.white
    static let onPrimaryDark = Color(hex: 0x003258)
    static let primaryContainerLight = Color(hex: 0xBBDEFB)
    static let primaryContainerDark = Color(hex: 0x00497D)
    static let secondaryLight = Color(hex: 0x43A047)
    static let secondaryDark = Color(hex: 0x81C784)
    static let errorLight = Color(hex: 0xD32F2F)
    static let errorDark = Color(hex: 0xEF9A9A)
    static let surfaceLight = Color(hex: 0xFFFBFE)
    static let surfaceDark = Color(hex: 0x1C1B1F)
    static let onSurfaceLight = Color(hex: 0x1C1B1F)
    static let onSurfaceDark = Color(hex: 0xE6E1E5)

    // Tertiary/Amber for "connecting" status
    static let tertiaryLight = Color(hex: 0xFFA000)
    static let tertiaryDark = Color(hex: 0xFFD54F)

    // MARK: - Spacing

    static let screenPadding: CGFloat = 16
    static let loginPadding: CGFloat = 24
    static let cardPaddingStatus: CGFloat = 20
    static let cardPaddingInfo: CGFloat = 16
    static let itemGap: CGFloat = 16
    static let spacerSmall: CGFloat = 8
    static let spacerMedium: CGFloat = 12
    static let spacerLarge: CGFloat = 32
    static let iconSizeStatus: CGFloat = 32
    static let iconTextGap: CGFloat = 12
}

// MARK: - Color Extension

extension Color {
    init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: alpha
        )
    }
}

// MARK: - Adaptive Colors

extension View {
    func adaptivePrimary() -> Color {
        // Use environment-aware approach; fallback to light
        return SimBridgeTheme.primaryLight
    }
}

struct AdaptiveColors {
    let colorScheme: ColorScheme

    var primary: Color {
        colorScheme == .dark ? SimBridgeTheme.primaryDark : SimBridgeTheme.primaryLight
    }

    var onPrimary: Color {
        colorScheme == .dark ? SimBridgeTheme.onPrimaryDark : SimBridgeTheme.onPrimaryLight
    }

    var primaryContainer: Color {
        colorScheme == .dark ? SimBridgeTheme.primaryContainerDark : SimBridgeTheme.primaryContainerLight
    }

    var secondary: Color {
        colorScheme == .dark ? SimBridgeTheme.secondaryDark : SimBridgeTheme.secondaryLight
    }

    var error: Color {
        colorScheme == .dark ? SimBridgeTheme.errorDark : SimBridgeTheme.errorLight
    }

    var surface: Color {
        colorScheme == .dark ? SimBridgeTheme.surfaceDark : SimBridgeTheme.surfaceLight
    }

    var onSurface: Color {
        colorScheme == .dark ? SimBridgeTheme.onSurfaceDark : SimBridgeTheme.onSurfaceLight
    }

    var tertiary: Color {
        colorScheme == .dark ? SimBridgeTheme.tertiaryDark : SimBridgeTheme.tertiaryLight
    }

    func statusColor(for status: ConnectionStatus) -> Color {
        switch status {
        case .connected: return primary
        case .connecting: return tertiary
        case .disconnected: return error
        }
    }

    func statusIcon(for status: ConnectionStatus) -> String {
        switch status {
        case .connected: return "checkmark.circle.fill"
        case .connecting: return "cloud.fill"
        case .disconnected: return "cloud.slash.fill"
        }
    }

    func statusLabel(for status: ConnectionStatus) -> String {
        switch status {
        case .connected: return "Connected"
        case .connecting: return "Connecting..."
        case .disconnected: return "Disconnected"
        }
    }
}

// MARK: - View Modifiers

struct PrimaryButtonStyle: ViewModifier {
    let colors: AdaptiveColors

    func body(content: Content) -> some View {
        content
            .font(.headline)
            .foregroundColor(colors.onPrimary)
            .frame(maxWidth: .infinity)
            .padding()
            .background(colors.primary)
            .cornerRadius(12)
    }
}

struct ErrorButtonStyle: ViewModifier {
    let colors: AdaptiveColors

    func body(content: Content) -> some View {
        content
            .font(.headline)
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding()
            .background(colors.error)
            .cornerRadius(12)
    }
}

struct CardStyle: ViewModifier {
    let colors: AdaptiveColors

    func body(content: Content) -> some View {
        content
            .padding(SimBridgeTheme.cardPaddingInfo)
            .background(colors.surface)
            .cornerRadius(12)
            .shadow(color: .black.opacity(0.05), radius: 2, x: 0, y: 1)
    }
}

extension View {
    func primaryButton(colors: AdaptiveColors) -> some View {
        modifier(PrimaryButtonStyle(colors: colors))
    }

    func errorButton(colors: AdaptiveColors) -> some View {
        modifier(ErrorButtonStyle(colors: colors))
    }

    func cardStyle(colors: AdaptiveColors) -> some View {
        modifier(CardStyle(colors: colors))
    }
}
