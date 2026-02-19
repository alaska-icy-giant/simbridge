// SimBridgeTheme.swift
// SimBridgeHost
//
// Color assets matching DESIGN_SYSTEM.md with dark mode support.
// Provides reusable ViewModifiers for status colors and consistent styling.

import SwiftUI

// MARK: - Color Palette

struct SimBridgeColors {
    let primary: Color
    let onPrimary: Color
    let primaryContainer: Color
    let secondary: Color
    let onSecondary: Color
    let error: Color
    let surface: Color
    let onSurface: Color
    let onSurfaceVariant: Color
    let surfaceVariant: Color
    let tertiary: Color  // Amber for connecting status

    static let light = SimBridgeColors(
        primary: Color(hex: 0x1976D2),
        onPrimary: Color.white,
        primaryContainer: Color(hex: 0xBBDEFB),
        secondary: Color(hex: 0x43A047),
        onSecondary: Color.white,
        error: Color(hex: 0xD32F2F),
        surface: Color(hex: 0xFFFBFE),
        onSurface: Color(hex: 0x1C1B1F),
        onSurfaceVariant: Color(hex: 0x49454F),
        surfaceVariant: Color(hex: 0xE7E0EC),
        tertiary: Color(hex: 0xF9A825)  // Amber
    )

    static let dark = SimBridgeColors(
        primary: Color(hex: 0x90CAF9),
        onPrimary: Color(hex: 0x003258),
        primaryContainer: Color(hex: 0x00497D),
        secondary: Color(hex: 0x81C784),
        onSecondary: Color(hex: 0x003910),
        error: Color(hex: 0xEF9A9A),
        surface: Color(hex: 0x1C1B1F),
        onSurface: Color(hex: 0xE6E1E5),
        onSurfaceVariant: Color(hex: 0xCAC4D0),
        surfaceVariant: Color(hex: 0x49454F),
        tertiary: Color(hex: 0xFFD54F)  // Amber (dark)
    )
}

// MARK: - Environment Key

private struct SimBridgeColorsKey: EnvironmentKey {
    static let defaultValue: SimBridgeColors = .light
}

extension EnvironmentValues {
    var simBridgeColors: SimBridgeColors {
        get { self[SimBridgeColorsKey.self] }
        set { self[SimBridgeColorsKey.self] = newValue }
    }
}

// MARK: - Theme View Modifier

struct SimBridgeTheme: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        let colors = colorScheme == .dark ? SimBridgeColors.dark : SimBridgeColors.light
        content
            .environment(\.simBridgeColors, colors)
            .tint(colors.primary)
    }
}

extension View {
    func simBridgeTheme() -> some View {
        modifier(SimBridgeTheme())
    }
}

// MARK: - Status Color Modifier

struct StatusColorModifier: ViewModifier {
    let status: ConnectionStatus
    @Environment(\.simBridgeColors) private var colors

    var statusColor: Color {
        switch status {
        case .connected: return colors.primary
        case .connecting: return colors.tertiary
        case .disconnected: return colors.error
        }
    }

    var statusBackgroundColor: Color {
        statusColor.opacity(0.1)
    }

    func body(content: Content) -> some View {
        content.foregroundColor(statusColor)
    }
}

extension View {
    func statusColor(for status: ConnectionStatus) -> some View {
        modifier(StatusColorModifier(status: status))
    }
}

// MARK: - Log Direction Color Modifier

struct LogDirectionColorModifier: ViewModifier {
    let direction: String
    @Environment(\.simBridgeColors) private var colors

    var directionColor: Color {
        direction == "IN" ? colors.primary : colors.secondary
    }

    func body(content: Content) -> some View {
        content.foregroundColor(directionColor)
    }
}

extension View {
    func logDirectionColor(for direction: String) -> some View {
        modifier(LogDirectionColorModifier(direction: direction))
    }
}

// MARK: - Card Style Modifier

struct SimBridgeCardModifier: ViewModifier {
    @Environment(\.simBridgeColors) private var colors

    func body(content: Content) -> some View {
        content
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .shadow(color: .black.opacity(0.08), radius: 2, x: 0, y: 1)
    }
}

extension View {
    func simBridgeCard() -> some View {
        modifier(SimBridgeCardModifier())
    }
}

// MARK: - Spacing Constants

enum SimBridgeSpacing {
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

// MARK: - Color Hex Extension

extension Color {
    init(hex: UInt32, opacity: Double = 1.0) {
        let red = Double((hex >> 16) & 0xFF) / 255.0
        let green = Double((hex >> 8) & 0xFF) / 255.0
        let blue = Double(hex & 0xFF) / 255.0
        self.init(red: red, green: green, blue: blue, opacity: opacity)
    }
}
