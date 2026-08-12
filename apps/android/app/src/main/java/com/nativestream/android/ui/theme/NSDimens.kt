// app/src/main/java/com/nativestream/android/ui/theme/NSDimens.kt
//
// NS Design System — Spacing, radius, and component sizing tokens
// Every dp value multiplies by NSScale.current — no hardcoded numbers in view code.
//
// Usage (inside Compose):
//   val dimens = NSDimens.current
//   Modifier.padding(dimens.spacing.lg)

package com.nativestream.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Token data classes ────────────────────────────────────────────────────────

data class NSSpacingTokens(
    val xxs:  Dp,
    val xs:   Dp,
    val sm:   Dp,
    val md:   Dp,
    val lg:   Dp,
    val xl:   Dp,
    val xxl:  Dp,
    val xxxl: Dp,
)

data class NSRadiusTokens(
    val sm:   Dp,
    val md:   Dp,
    val lg:   Dp,
    val xl:   Dp,
    val pill: Dp,
)

data class NSCardSizeTokens(
    val minWidth:    Dp,
    val cardHeight:  Dp,
    val logoHeight:  Dp,
)

data class NSRailTokens(
    val width:    Dp,
    val iconSize: Dp,
)

data class NSChipTokens(
    val height:           Dp,
    val horizontalPadding: Dp,
)

data class NSBadgeTokens(
    val height:        Dp,
    val dotSize:       Dp,
    val healthDotSize: Dp,
)

data class NSIconButtonTokens(
    val small: Dp,
    val medium:Dp,
    val large: Dp,
)

data class NSToggleTokens(
    val trackWidth:  Dp,
    val trackHeight: Dp,
    val thumbSize:   Dp,
)

data class NSPlayerTokens(
    val sidebarWidth:         Dp,
    val teamBadgeSize:        Dp,
    val teamBadgeRadius:      Dp,
    val teamEmojiSize:        Dp,
    val teamNameMaxWidth:     Dp,
    val controlPrimary:       Dp,
    val controlSecondary:     Dp,
    val controlRadiusPrimary: Dp,
    val controlRadiusSecondary: Dp,
    val errorIconSize:        Dp,
    val errorPadding:         Dp,
    val menuHeight:           Dp,
    val controlIconSmall:     Dp,
    val controlIconLarge:     Dp,
)

data class NSMatchTokens(
    val heroArtHeight:  Dp,
    val smallArtHeight: Dp,
    val heroBadgeSize:  Dp,
    val smallBadgeSize: Dp,
)

data class NSChannelTokens(
    val logoSquareSmall:  Dp,   // player sidebar
    val logoSquareMedium: Dp,   // rows and lists
    val progressHeight:   Dp,   // intentionally not scaled (per Swift spec)
)

data class NSSettingsTokens(
    val sidebarWidth:  Dp,
    val navItemHeight: Dp,
    val navIconSize:   Dp,
)

data class NSScheduleTokens(
    val chipScrollMaxWidth: Dp,
    val emptyEmojiSize:     Dp,
    val timeColumnWidth:    Dp,
    val teamBadgeSize:      Dp,
    val microLabelSize:     Dp,
)

data class NSUpcomingTokens(
    val badgeSize: Dp,
)

data class NSHelpersTokens(
    val addButtonHeight: Dp,
)

// ── Root dimens object ────────────────────────────────────────────────────────

data class NSDimensTokens(
    val spacing:    NSSpacingTokens,
    val radius:     NSRadiusTokens,
    val cardSize:   NSCardSizeTokens,
    val rail:       NSRailTokens,
    val chip:       NSChipTokens,
    val badge:      NSBadgeTokens,
    val iconButton: NSIconButtonTokens,
    val toggle:     NSToggleTokens,
    val player:     NSPlayerTokens,
    val match:      NSMatchTokens,
    val channel:    NSChannelTokens,
    val settings:   NSSettingsTokens,
    val schedule:   NSScheduleTokens,
    val upcoming:   NSUpcomingTokens,
    val helpers:    NSHelpersTokens,
)

// ── Factory — builds all tokens from a scale multiplier ──────────────────────

private fun buildDimens(scale: Float): NSDimensTokens {
    fun dp(base: Float) = (base * scale).dp
    // progressHeight is explicitly NOT scaled (matches Swift spec comment)
    val unscaledProgressHeight = 2.dp

    return NSDimensTokens(
        spacing = NSSpacingTokens(
            xxs  = dp(2f),
            xs   = dp(4f),
            sm   = dp(8f),
            md   = dp(12f),
            lg   = dp(16f),
            xl   = dp(20f),
            xxl  = dp(28f),
            xxxl = dp(36f),
        ),
        radius = NSRadiusTokens(
            sm   = dp(6f),
            md   = dp(8f),
            lg   = dp(10f),
            xl   = dp(12f),
            pill = dp(20f),
        ),
        cardSize = NSCardSizeTokens(
            minWidth   = dp(220f),
            cardHeight = dp(320f),
            logoHeight = dp(160f),  // cardHeight / 2
        ),
        rail = NSRailTokens(
            width    = dp(52f),
            iconSize = dp(38f),
        ),
        chip = NSChipTokens(
            height            = dp(28f),
            horizontalPadding = dp(10f),
        ),
        badge = NSBadgeTokens(
            height        = dp(24f),
            dotSize       = dp(5f),
            healthDotSize = dp(6f),
        ),
        iconButton = NSIconButtonTokens(
            small = dp(22f),
            medium = dp(27f),
            large = dp(32f),
        ),
        toggle = NSToggleTokens(
            trackWidth  = dp(36f),
            trackHeight = dp(20f),
            thumbSize   = dp(16f),
        ),
        player = NSPlayerTokens(
            sidebarWidth           = dp(230f),
            teamBadgeSize          = dp(52f),
            teamBadgeRadius        = dp(12f),
            teamEmojiSize          = dp(22f),
            teamNameMaxWidth       = dp(120f),
            controlPrimary         = dp(44f),
            controlSecondary       = dp(36f),
            controlRadiusPrimary   = dp(11f),
            controlRadiusSecondary = dp(9f),
            errorIconSize          = dp(36f),
            errorPadding           = dp(32f),
            menuHeight             = dp(32f),
            controlIconSmall       = dp(14f),
            controlIconLarge       = dp(16f),
        ),
        match = NSMatchTokens(
            heroArtHeight  = dp(120f),
            smallArtHeight = dp(72f),
            heroBadgeSize  = dp(40f),
            smallBadgeSize = dp(28f),
        ),
        channel = NSChannelTokens(
            logoSquareSmall  = dp(32f),
            logoSquareMedium = dp(36f),
            progressHeight   = unscaledProgressHeight,
        ),
        settings = NSSettingsTokens(
            sidebarWidth  = dp(200f),
            navItemHeight = dp(34f),
            navIconSize   = dp(16f),
        ),
        schedule = NSScheduleTokens(
            chipScrollMaxWidth = dp(480f),
            emptyEmojiSize     = dp(32f),
            timeColumnWidth    = dp(44f),
            teamBadgeSize      = dp(22f),
            microLabelSize     = dp(7f),
        ),
        upcoming = NSUpcomingTokens(
            badgeSize = dp(20f),
        ),
        helpers = NSHelpersTokens(
            addButtonHeight = dp(40f),
        ),
    )
}

// ── CompositionLocal ──────────────────────────────────────────────────────────

val LocalNSDimens = compositionLocalOf { buildDimens(NS_SCALE_DEFAULT) }

/** Convenience accessor — use NSDimens.current inside any composable. */
object NSDimens {
    val current: NSDimensTokens
        @Composable
        get() = LocalNSDimens.current

    /** Build a token set for a given scale — used by NSTheme to provide values. */
    fun forScale(scale: Float): NSDimensTokens = buildDimens(scale)
}