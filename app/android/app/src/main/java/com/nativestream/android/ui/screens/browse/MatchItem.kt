// app/src/main/java/com/nativestream/android/ui/screens/browse/MatchItem.kt
//
// NS-013: Match Item

package com.nativestream.android.ui.screens.browse

import com.nativestream.android.domain.model.Programme
import java.util.UUID

data class MatchItem(
    val id: String             = UUID.randomUUID().toString(),
    val programme: Programme,
    val channelTvgId: String,
    val competition: String,
    val homeTeam: String,
    val awayTeam: String,
    val variant: MatchCardVariant = MatchCardVariant.PLAIN,
)