// app/src/main/java/com/nativestream/android/ui/viewmodel/CastViewModel.kt
//
// Cast ViewModel
// Bridges CastManager to the player UI. Exposes availability + connected state.

package com.nativestream.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nativestream.android.data.cast.CastManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CastViewModel @Inject constructor(
    private val castManager: CastManager,
) : ViewModel() {

    val isCastAvailable: StateFlow<Boolean> = castManager.isCastAvailable
    val isConnected: StateFlow<Boolean>     = castManager.isConnected

    init {
        viewModelScope.launch { castManager.initialise() }
    }

    fun castStream(streamUrl: String, title: String) =
        castManager.castStream(streamUrl, title)

    fun stopCasting() = castManager.stopCasting()

    override fun onCleared() {
        super.onCleared()
        castManager.release()
    }
}