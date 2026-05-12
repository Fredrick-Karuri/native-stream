// DesignSystem.swift — UX-001
// Single source of truth for all colours, typography, and spacing.
// Every view uses NS.* — never hardcoded hex values.
//
// Scale: NS.scale is an @AppStorage-backed multiplier (default 1.0).
// All sizing tokens multiply by it. No view code changes needed for scaling.

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

    // ── Global scale ──────────────────────────────────────────────────────────
    // Stored in UserDefaults so it persists across launches.
    // Range: 0.8 – 1.5. All sizing tokens multiply by this value.
    @AppStorage("uiScale") static var scale: Double = 1.5

    // ── Backgrounds ───────────────────────────────────────────────────────────
    static let bg       = Color(hex: "060810")
    static let surface  = Color(hex: "0d1120")
    static let surface2 = Color(hex: "131826")
    static let surface3 = Color(hex: "1a2035")

    // ── Borders ───────────────────────────────────────────────────────────────
    static let border   = Color.white.opacity(0.043)
    static let border2  = Color.white.opacity(0.086)
    static let border3  = Color.white.opacity(0.141)

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
    static let red = Color(hex: "ef4444")

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
    // Base sizes are the design-intent values at scale 1.0.
    // Computed properties re-evaluate whenever NS.scale changes.
    enum Font {
        private static func s(_ base: CGFloat) -> CGFloat { base * NS.scale }

        static var displayXL: SwiftUI.Font { .custom("Syne-ExtraBold",  size: s(36)) }
        static var display:   SwiftUI.Font { .custom("Syne-Bold",       size: s(20)) }
        static var heading:   SwiftUI.Font { .custom("Syne-Bold",       size: s(16)) }
        static var label:     SwiftUI.Font { .custom("Syne-Bold",       size: s(11)) }   // uppercase
        static var cardTitle: SwiftUI.Font { .custom("Syne-Bold",       size: s(13)) }
        static var scoreXL:   SwiftUI.Font { .custom("Syne-ExtraBold",  size: s(60)) }

        static var body:       SwiftUI.Font { .custom("InstrumentSans",          size: s(13)) }
        static var bodyMedium: SwiftUI.Font { .custom("InstrumentSans-Medium",   size: s(13)) }
        static var caption:    SwiftUI.Font { .custom("InstrumentSans",          size: s(11)) }
        static var captionMed: SwiftUI.Font { .custom("InstrumentSans-Medium",   size: s(11)) }

        static var mono:    SwiftUI.Font { .custom("DMMono-Regular", size: s(11)) }
        static var monoSm:  SwiftUI.Font { .custom("DMMono-Regular", size: s(10)) }
        static var monoMed: SwiftUI.Font { .custom("DMMono-Medium",  size: s(11)) }
    }

    // ── Spacing ───────────────────────────────────────────────────────────────
    enum Spacing {
        static var xxs: CGFloat { 2 * NS.scale }
        static var xs:  CGFloat { 4  * NS.scale }
        static var sm:  CGFloat { 8  * NS.scale }
        static var md:  CGFloat { 12 * NS.scale }
        static var lg:  CGFloat { 16 * NS.scale }
        static var xl:  CGFloat { 20 * NS.scale }
        static var xxl: CGFloat { 28 * NS.scale }
        static var xxxl: CGFloat { 36 * NS.scale }
    }

    // ── Radius ────────────────────────────────────────────────────────────────
    enum Radius {
        static var sm:   CGFloat { 6  * NS.scale }
        static var md:   CGFloat { 8  * NS.scale }
        static var lg:   CGFloat { 10 * NS.scale }
        static var xl:   CGFloat { 12 * NS.scale }
        static var pill: CGFloat { 20 * NS.scale }
    }

    // ── Card sizing ───────────────────────────────────────────────────────────
    enum CardSize {
        static var minWidth: CGFloat { 220 * NS.scale }
        static var cardHeight: CGFloat { 320 * NS.scale }
        static var logoHeight: CGFloat { cardHeight / 2 }
    }

    // ── Rail sizing ───────────────────────────────────────────────────────────
    enum Rail {
        static var width:    CGFloat { 52 * NS.scale }
        static var iconSize: CGFloat { 38 * NS.scale }
    }

    // ── Settings sizing ───────────────────────────────────────────────────────
    enum Settings {
        static var sidebarWidth: CGFloat { 200 * NS.scale }
        static var navItemHeight: CGFloat { 34  * NS.scale }
        static var navIconSize:   CGFloat { 16  * NS.scale }
    }

    enum Helpers {
        static var addButtonHeight: CGFloat { 40 * NS.scale }
    }

    enum Help {
        static var sidebarWidth:   CGFloat { 180 * NS.scale }
        static var searchWidth:    CGFloat { 180 * NS.scale }
        static var searchHeight:   CGFloat { 28  * NS.scale }
        static var tabHeight:      CGFloat { 26  * NS.scale }
        static var emptyIconSize:  CGFloat { 28  * NS.scale }
        static var emptyTopPadding: CGFloat { 80 * NS.scale }
        static var inlineIconSize: CGFloat { 12  * NS.scale }
    }

    enum Browser {
        static var searchWidth:    CGFloat { 200 * NS.scale }
        static var emptyEmojiSize: CGFloat { 40  * NS.scale }
    }

    enum Schedule {
        static var chipScrollMaxWidth: CGFloat { 480 * NS.scale }
        static var emptyEmojiSize:     CGFloat { 32  * NS.scale }
        static var timeColumnWidth:    CGFloat { 44  * NS.scale }
        static var teamBadgeSize:      CGFloat { 22  * NS.scale }
        static var microLabelSize:     CGFloat { 7   * NS.scale }
    }

    enum Chip {
        static var height:   CGFloat { 28 * NS.scale }
        static var paddingH: CGFloat { 10 * NS.scale }
    }

    enum Badge {
        static var height:       CGFloat { 24 * NS.scale }
        static var dotSize:      CGFloat { 5  * NS.scale }
        static var healthDotSize: CGFloat { 6 * NS.scale }
    }

    enum IconButton {
        static var sizeSm: CGFloat { 22 * NS.scale }
        static var sizeLg: CGFloat { 32 * NS.scale }
    }

    enum Toggle {
        static var trackW:     CGFloat { 36 * NS.scale }
        static var trackH:     CGFloat { 20 * NS.scale }
        static var thumbSize:  CGFloat { 16 * NS.scale }
    }

    enum Player {
        static var sidebarWidth:       CGFloat { 230 * NS.scale }
        static var teamBadgeSize:      CGFloat { 52  * NS.scale }
        static var teamBadgeRadius:    CGFloat { 12  * NS.scale }
        static var teamEmojiSize:      CGFloat { 22  * NS.scale }
        static var teamNameMaxWidth:   CGFloat { 120 * NS.scale }
        static var ctrlPrimary:        CGFloat { 44  * NS.scale }
        static var ctrlSecondary:      CGFloat { 36  * NS.scale }
        static var ctrlRadiusPrimary:  CGFloat { 11  * NS.scale }
        static var ctrlRadiusSecondary: CGFloat { 9  * NS.scale }
        static var errorIconSize:      CGFloat { 36  * NS.scale }
        static var errorPadding:       CGFloat { 32  * NS.scale }
        static var menuHeight:   CGFloat { 32 * NS.scale }
        static var ctrlIconSm:   CGFloat { 14 * NS.scale }
        static var ctrlIconLg:   CGFloat { 16 * NS.scale }
    }

    // ── Health score → colour ─────────────────────────────────────────────────
    static func healthColour(score: Double) -> Color {
        if score >= 0.7 { return green }
        if score >= 0.4 { return accent }
        return live
    }
}
