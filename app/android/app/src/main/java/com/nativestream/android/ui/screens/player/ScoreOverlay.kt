// app/src/main/java/com/nativestream/android/ui/screens/player/ScoreOverlay.kt
//
//  Score Overlay
// Shown in the player when EPG programme title contains " vs ".

package com.nativestream.android.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.nativestream.android.domain.model.Programme
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType

private const val VS_SEPARATOR = " vs "
private val SCORE_REGEX        = Regex("""(\d+)\s*[–\-]\s*(\d+)""")
private val MINUTE_REGEX       = Regex("""(\d+)'""")

@Composable
fun ScoreOverlay(programme: Programme, modifier: Modifier = Modifier) {
    val dimens   = NSDimens.current
    val parts    = programme.title.split(VS_SEPARATOR, ignoreCase = true)
    val home     = parts.getOrNull(0)?.trim() ?: return
    val away     = parts.getOrNull(1)?.substringBefore(" — ")?.trim() ?: return
    val scoreMatch  = SCORE_REGEX.find(programme.title)
    val minuteMatch = MINUTE_REGEX.find(programme.title)
    val competition = programme.title.substringAfter(" — ", "").trim()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        modifier = modifier,
    ) {
        if (competition.isNotEmpty()) {
            Text(
                text  = competition.uppercase(),
                style = NSType.monoSmall(),
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text      = home,
                style     = NSType.bodyMedium(),
                color     = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.End,
                modifier  = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(dimens.spacing.lg))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text  = scoreMatch?.let { "${it.groupValues[1]} – ${it.groupValues[2]}" } ?: "vs",
                    style = NSType.scoreXL(),
                    color = Color.White,
                )
                minuteMatch?.let {
                    Text(text = it.value, style = NSType.caption(), color = NSColors.live)
                }
            }
            Spacer(modifier = Modifier.width(dimens.spacing.lg))
            Text(
                text     = away,
                style    = NSType.bodyMedium(),
                color    = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Start,
                modifier  = Modifier.weight(1f),
            )
        }
    }
}