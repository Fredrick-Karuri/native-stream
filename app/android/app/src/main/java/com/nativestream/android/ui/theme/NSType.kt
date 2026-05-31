// app/src/main/java/com/nativestream/android/ui/theme/NSType.kt
//
// NS Design System — Typography tokens
//
// Fonts: Syne (display/headings), Instrument Sans (body), DM Mono (mono)
// loaded at runtime via androidx.compose.ui:ui-text-google-fonts.
//
// Scale: NSScale.current multiplier applied to every base size,
// matching the Mac @AppStorage("uiScale") behaviour.

package com.nativestream.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.nativestream.android.R

// ── Google Fonts provider ─────────────────────────────────────────────────────

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs,
)

private fun googleFont(name: String) = GoogleFont(name)

// ── Font families ─────────────────────────────────────────────────────────────

val SyneFontFamily = FontFamily(
    Font(googleFont("Syne"), googleFontProvider, weight = FontWeight.Bold),
    Font(googleFont("Syne"), googleFontProvider, weight = FontWeight.ExtraBold),
)

val InstrumentSansFontFamily = FontFamily(
    Font(googleFont("Instrument Sans"), googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont("Instrument Sans"), googleFontProvider, weight = FontWeight.Medium),
)

val DmMonoFontFamily = FontFamily(
    Font(googleFont("DM Mono"), googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont("DM Mono"), googleFontProvider, weight = FontWeight.Medium),
)

// ── Base sizes (design-intent at scale 1.0) ───────────────────────────────────

private object BaseSize {
    const val DISPLAY_XL: Float = 36f
    const val DISPLAY:    Float = 20f
    const val HEADING:    Float = 16f
    const val LABEL:      Float = 11f
    const val CARD_TITLE: Float = 13f
    const val SCORE_XL:   Float = 60f

    const val BODY:    Float = 13f
    const val CAPTION: Float = 11f

    const val MONO:    Float = 11f
    const val MONO_SM: Float = 10f
}

// ── Scaled text styles ────────────────────────────────────────────────────────
// Each style is a @Composable so it reads the current scale from composition.

object NSType {

    // Display / Headings (Syne)
    @Composable fun displayXL() = TextStyle(
        fontFamily = SyneFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize   = (BaseSize.DISPLAY_XL * NSScale.current).sp,
    )

    @Composable fun display() = TextStyle(
        fontFamily = SyneFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = (BaseSize.DISPLAY * NSScale.current).sp,
    )

    @Composable fun heading() = TextStyle(
        fontFamily = SyneFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = (BaseSize.HEADING * NSScale.current).sp,
    )

    /** Uppercase label — caller responsible for letterSpacing / textTransform. */
    @Composable fun label() = TextStyle(
        fontFamily = SyneFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = (BaseSize.LABEL * NSScale.current).sp,
    )

    @Composable fun cardTitle() = TextStyle(
        fontFamily = SyneFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = (BaseSize.CARD_TITLE * NSScale.current).sp,
    )

    @Composable fun scoreXL() = TextStyle(
        fontFamily = SyneFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize   = (BaseSize.SCORE_XL * NSScale.current).sp,
    )

    // Body (Instrument Sans)
    @Composable fun body() = TextStyle(
        fontFamily = InstrumentSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = (BaseSize.BODY * NSScale.current).sp,
    )

    @Composable fun bodyMedium() = TextStyle(
        fontFamily = InstrumentSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = (BaseSize.BODY * NSScale.current).sp,
    )

    @Composable fun caption() = TextStyle(
        fontFamily = InstrumentSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = (BaseSize.CAPTION * NSScale.current).sp,
    )

    @Composable fun captionMedium() = TextStyle(
        fontFamily = InstrumentSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = (BaseSize.CAPTION * NSScale.current).sp,
    )

    // Mono (DM Mono)
    @Composable fun mono() = TextStyle(
        fontFamily = DmMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = (BaseSize.MONO * NSScale.current).sp,
    )

    @Composable fun monoSmall() = TextStyle(
        fontFamily = DmMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = (BaseSize.MONO_SM * NSScale.current).sp,
    )

    @Composable fun monoMedium() = TextStyle(
        fontFamily = DmMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = (BaseSize.MONO * NSScale.current).sp,
    )
}