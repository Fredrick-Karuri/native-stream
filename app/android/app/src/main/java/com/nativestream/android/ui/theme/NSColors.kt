// app/src/main/java/com/nativestream/android/ui/theme/NSColors.kt
//
// NS Design System — Colour tokens (AND-002)
// Single source of truth for every colour used in the app.
// Mirrors DesignSystem.swift exactly — never use hardcoded hex values elsewhere.
//
// Usage:  NSColors.bg, NSColors.accent, NSColors.live …

package com.nativestream.android.ui.theme

import androidx.compose.ui.graphics.Color

object NSColors {

    // ── Backgrounds ───────────────────────────────────────────────────────────
    val bg       = Color(0xFF060810)
    val surface  = Color(0xFF0D1120)
    val surface2 = Color(0xFF131826)
    val surface3 = Color(0xFF1A2035)

    // ── Borders ───────────────────────────────────────────────────────────────
    // Opacity values match Swift: 0.043 / 0.086 / 0.141
    val border  = Color.White.copy(alpha = 0.043f)
    val border2 = Color.White.copy(alpha = 0.086f)
    val border3 = Color.White.copy(alpha = 0.141f)

    // ── Text ──────────────────────────────────────────────────────────────────
    val text  = Color(0xFFE8EAF0)
    val text2 = Color(0xFF8890B0)
    val text3 = Color(0xFF444E70)

    // ── Accent — Sky Blue ─────────────────────────────────────────────────────
    val accent       = Color(0xFF0EA5E9)
    val accent2      = Color(0xFF38BDF8)
    val accentGlow   = Color(0xFF0EA5E9).copy(alpha = 0.133f)
    val accentBorder = Color(0xFF0EA5E9).copy(alpha = 0.220f)

    // ── Status ────────────────────────────────────────────────────────────────
    val green = Color(0xFF10B981)
    val live  = Color(0xFFEF4444)   // red — used for LIVE badges
    val amber = Color(0xFFF59E0B)
    val red   = Color(0xFFEF4444)   // alias of live for semantic clarity

    // ── Health score → colour ─────────────────────────────────────────────────
    private const val HEALTH_GOOD_THRESHOLD = 0.70
    private const val HEALTH_WARN_THRESHOLD = 0.40

    fun healthColor(score: Double): Color = when {
        score >= HEALTH_GOOD_THRESHOLD -> green
        score >= HEALTH_WARN_THRESHOLD -> accent
        else                           -> live
    }
}