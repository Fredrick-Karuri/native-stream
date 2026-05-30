// app/src/main/java/com/nativestream/android/ui/viewmodel/FavouritesViewModel.kt
//
// NS-028: Favourites ViewModel (stub)
// Exposes starred channel IDs as StateFlow. Full DataStore persistence in AND-028.

package com.nativestream.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.nativestream.android.domain.model.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class FavouritesViewModel @Inject constructor() : ViewModel() {

    private val _favouriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favouriteIds: StateFlow<Set<String>> = _favouriteIds.asStateFlow()

    fun toggle(channel: Channel) {
        _favouriteIds.value = _favouriteIds.value.toMutableSet().apply {
            if (!add(channel.id)) remove(channel.id)
        }
    }

    fun isFavourite(channel: Channel): Boolean = _favouriteIds.value.contains(channel.id)
}