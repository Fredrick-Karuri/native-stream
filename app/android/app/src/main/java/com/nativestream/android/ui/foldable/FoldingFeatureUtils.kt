// app/src/main/java/com/nativestream/android/ui/foldable/FoldingFeatureUtils.kt

package com.nativestream.android.ui.foldable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map

data class FoldPosture(
    val isTableTop: Boolean = false,
    val isBook:     Boolean = false,
    val hingeBounds: androidx.compose.ui.geometry.Rect? = null,
)

@Composable
fun rememberFoldPosture(): FoldPosture {
    val context = LocalContext.current
    val windowLayoutInfo by WindowInfoTracker
        .getOrCreate(context)
        .windowLayoutInfo(context as android.app.Activity)
        .map { info ->
            val fold = info.displayFeatures
                .filterIsInstance<FoldingFeature>()
                .firstOrNull()

            if (fold == null) return@map FoldPosture()

            val bounds = fold.bounds
            FoldPosture(
                isTableTop  = fold.state == FoldingFeature.State.HALF_OPENED &&
                        fold.orientation == FoldingFeature.Orientation.HORIZONTAL,
                isBook      = fold.state == FoldingFeature.State.HALF_OPENED &&
                        fold.orientation == FoldingFeature.Orientation.VERTICAL,
                hingeBounds = androidx.compose.ui.geometry.Rect(
                    left   = bounds.left.toFloat(),
                    top    = bounds.top.toFloat(),
                    right  = bounds.right.toFloat(),
                    bottom = bounds.bottom.toFloat(),
                ),
            )
        }
        .collectAsState(initial = FoldPosture())

    return windowLayoutInfo
}