// app/src/main/java/com/nativestream/android/ui/theme/NSGradients.kt
//
// NS Design System — Gradient tokens (AND-002)
// Mirrors NS gradient definitions from DesignSystem.swift exactly.
//
// Usage:  Box(modifier = Modifier.background(NSGradients.playerBottom))

package com.nativestream.android.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object NSGradients {

    // ── Live card — very subtle red tint top-left → surface2 ─────────────────
    val liveCard: Brush get() = Brush.linearGradient(
        colors = listOf(
            NSColors.live.copy(alpha = 0.024f),
            NSColors.surface2,
        ),
        start = Offset(0f, 0f),
        end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    // ── Active / playing card — accent glow top-left → surface2 ──────────────
    val activeCard: Brush get() = Brush.linearGradient(
        colors = listOf(
            NSColors.accentGlow,
            NSColors.surface2,
        ),
        start = Offset(0f, 0f),
        end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    // ── UCL card — dark navy gradient ─────────────────────────────────────────
    val uclCard: Brush get() = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1E3A5F),
            Color(0xFF162D4A),
        ),
        start = Offset(0f, 0f),
        end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    // ── Player overlays — vertical, top-down and bottom-up ───────────────────
    val playerTop: Brush get() = Brush.verticalGradient(
        colors = listOf(
            Color.Black.copy(alpha = 0.69f),
            Color.Transparent,
        ),
    )

    val playerBottom: Brush get() = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.82f),
        ),
    )
}