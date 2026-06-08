// app/src/main/java/com/nativestream/android/ui/viewmodel/FavouritesViewModel.kt
//
// Favourites Manager (Android)
// Persists starred channel IDs in DataStore as a Set<String>.
// Exposed as StateFlow<Set<String>>. toggle() writes immediately.

package com.nativestream.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nativestream.android.domain.model.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

private val FAVOURITE_IDS_KEY = stringSetPreferencesKey("favourite_channel_ids")

@HiltViewModel
class FavouritesViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _favouriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favouriteIds: StateFlow<Set<String>> = _favouriteIds.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data
                .map { prefs -> prefs[FAVOURITE_IDS_KEY] ?: emptySet() }
                .collect { ids -> _favouriteIds.value = ids }
        }
    }

    fun toggle(channel: Channel) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                val current = prefs[FAVOURITE_IDS_KEY] ?: emptySet()
                prefs[FAVOURITE_IDS_KEY] = if (current.contains(channel.id)) {
                    current - channel.id
                } else {
                    current + channel.id
                }
            }
        }
    }

    fun isFavourite(channel: Channel): Boolean = _favouriteIds.value.contains(channel.id)
}