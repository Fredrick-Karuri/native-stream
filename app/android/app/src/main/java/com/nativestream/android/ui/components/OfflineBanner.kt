// app/src/main/java/com/nativestream/android/ui/components/OfflineBanner.kt
//
// Slim persistent banner shown at the top of the nav shell when the device
// has no internet. Auto-hides when connectivity is restored.

package com.nativestream.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.WifiSlash
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

@Composable
fun OfflineBanner(
    isOffline: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible  = isOffline,
        enter    = slideInVertically { -it },
        exit     = slideOutVertically { -it },
        modifier = modifier,
    ) {
        val dimens = NSDimens.current
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .background(NSColors.surface3)
                .padding(horizontal = dimens.spacing.md, vertical = dimens.spacing.sm),
        ) {
            Icon(
                imageVector        = PhosphorIcons.Regular.WifiSlash,
                contentDescription = null,
                tint               = NSColors.text3,
                modifier           = Modifier.size(12.dp),
            )
            Text(
                text  = "You're offline",
                style = NSType.caption(),
                color = NSColors.text3,
            )
        }
    }
}