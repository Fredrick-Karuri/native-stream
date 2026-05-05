// DesignSystem.swift — UX-001
// Single source of truth for all colours, typography, and spacing.
// Every view uses NS.* — never hardcoded hex values.

import SwiftUI

// MARK: - Colour hex helper

extension Color {
    init(hex: String) {
        let h = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: h).scanHexInt64(&int)
        let r, g, b: UInt64
        switch h.count {
        case 6:
            (r, g, b) = ((int >> 16) & 0xFF, (int >> 8) & 0xFF, int & 0xFF)
        default:
            (r, g, b) = (0, 0, 0)
        }
        self.init(red: Double(r) / 255, green: Double(g) / 255, blue: Double(b) / 255)
    }
}

// MARK: - NS Design System

enum NS {

    // ── Backgrounds ───────────────────────────────────────────────────────────
    static let bg       = Color(hex: "060810")
    static let surface  = Color(hex: "0d1120")
    static let surface2 = Color(hex: "131826")
    static let surface3 = Color(hex: "1a2035")

    // ── Borders ───────────────────────────────────────────────────────────────
    static let border   = Color.white.opacity(0.043)   // #ffffff0b
    static let border2  = Color.white.opacity(0.086)   // #ffffff16
    static let border3  = Color.white.opacity(0.141)   // #ffffff24

    // ── Text ──────────────────────────────────────────────────────────────────
    static let text     = Color(hex: "e8eaf0")
    static let text2    = Color(hex: "8890b0")
    static let text3    = Color(hex: "444e70")

    // ── Accent — Sky Blue ─────────────────────────────────────────────────────
    static let accent       = Color(hex: "0ea5e9")
    static let accent2      = Color(hex: "38bdf8")
    static let accentGlow   = Color(hex: "0ea5e9").opacity(0.133)
    static let accentBorder = Color(hex: "0ea5e9").opacity(0.220)

    // ── Status ────────────────────────────────────────────────────────────────
    static let green  = Color(hex: "10b981")
    static let live   = Color(hex: "ef4444")
    static let amber  = Color(hex: "f59e0b")

    // ── Gradients ─────────────────────────────────────────────────────────────
    static let liveCardGradient = LinearGradient(
        colors: [Color(hex: "ef4444").opacity(0.024), surface2],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )
    static let activeCardGradient = LinearGradient(
        colors: [accentGlow, surface2],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )
    static let uclCardGradient = LinearGradient(
        colors: [Color(hex: "1e3a5f"), Color(hex: "162d4a")],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )
    static let playerTopGradient = LinearGradient(
        colors: [Color.black.opacity(0.69), Color.clear],
        startPoint: .top, endPoint: .bottom
    )
    static let playerBottomGradient = LinearGradient(
        colors: [Color.black.opacity(0.82), Color.clear],
        startPoint: .bottom, endPoint: .top
    )

    // ── Typography ────────────────────────────────────────────────────────────
    enum Font {
        static let displayXL  = SwiftUI.Font.custom("Syne-ExtraBold",  size: 36)
        static let display     = SwiftUI.Font.custom("Syne-Bold",       size: 20)
        static let heading     = SwiftUI.Font.custom("Syne-Bold",       size: 16)
        static let label       = SwiftUI.Font.custom("Syne-Bold",       size: 11)  // uppercase
        static let cardTitle   = SwiftUI.Font.custom("Syne-Bold",       size: 13)
        static let scoreXL     = SwiftUI.Font.custom("Syne-ExtraBold",  size: 60)
        static let body        = SwiftUI.Font.custom("InstrumentSans",  size: 13)
        static let bodyMedium  = SwiftUI.Font.custom("InstrumentSans-Medium", size: 13)
        static let caption     = SwiftUI.Font.custom("InstrumentSans",  size: 11)
        static let captionMed  = SwiftUI.Font.custom("InstrumentSans-Medium", size: 11)
        static let mono        = SwiftUI.Font.custom("DMMono-Regular",  size: 11)
        static let monoSm      = SwiftUI.Font.custom("DMMono-Regular",  size: 10)
        static let monoMed     = SwiftUI.Font.custom("DMMono-Medium",   size: 11)
    }

    // ── Spacing ───────────────────────────────────────────────────────────────
    enum Spacing {
        static let xs: CGFloat  = 4
        static let sm: CGFloat  = 8
        static let md: CGFloat  = 12
        static let lg: CGFloat  = 16
        static let xl: CGFloat  = 20
        static let xxl: CGFloat = 28
    }

    // ── Radius ────────────────────────────────────────────────────────────────
    enum Radius {
        static let sm: CGFloat  = 6
        static let md: CGFloat  = 8
        static let lg: CGFloat  = 10
        static let xl: CGFloat  = 12
        static let pill: CGFloat = 20
    }

    // ── Health score → colour ─────────────────────────────────────────────────
    static func healthColour(score: Double) -> Color {
        if score >= 0.7 { return green }
        if score >= 0.4 { return accent }
        return live
    }
}
