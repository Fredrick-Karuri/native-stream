// app/src/main/java/com/nativestream/android/ui/screens/onboarding/SplashScreen.kt

package com.nativestream.android.ui.screens.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import kotlinx.coroutines.delay

private const val SPLASH_DURATION_MS = 2_000L
private const val FADE_IN_MS         = 400

@Composable
fun SplashScreen(
    onComplete: () -> Unit,
    onStartDiscovery: () -> Unit,
) {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        onStartDiscovery()
        alpha.animateTo(1f, animationSpec = tween(FADE_IN_MS))
        delay(SPLASH_DURATION_MS - FADE_IN_MS)
        onComplete()
    }

    val dimens = NSDimens.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(NSColors.bg)
            .alpha(alpha.value),
    ) {
        Text(text = "▶", style = NSType.display(), color = NSColors.accent)
        Spacer(modifier = Modifier.height(dimens.spacing.md))
        Text(text = "NativeStream", style = NSType.heading(), color = NSColors.text)
        Spacer(modifier = Modifier.height(dimens.spacing.sm))
        Text(
            text  = "Your live TV. On every screen.",
            style = NSType.body(),
            color = NSColors.text3,
        )
    }
}