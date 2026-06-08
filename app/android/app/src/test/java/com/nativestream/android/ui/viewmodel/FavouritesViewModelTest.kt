// app/src/test/java/com/nativestream/android/ui/viewmodel/FavouritesViewModelTest.kt
//
// AND-T018 — FavouritesViewModel: persistence
// Type: Integration (Robolectric) — DataStore requires an Android Context.

package com.nativestream.android.ui.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nativestream.android.domain.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FavouritesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var viewModel: FavouritesViewModel

    private val channelA = Channel.create(
        tvgId     = "bbc.one",
        name      = "BBC One",
        streamUrl = "http://stream.example.com/bbc1.m3u8",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        viewModel = FavouritesViewModel(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `T018 - toggle adds channel id to favouriteIds`() = runTest {
        viewModel.toggle(channelA)
        advanceUntilIdle()
        assertTrue(viewModel.favouriteIds.value.contains(channelA.id))
    }

    @Test
    fun `T018 - second toggle removes channel id`() = runTest {
        viewModel.toggle(channelA)
        advanceUntilIdle()
        viewModel.toggle(channelA)
        advanceUntilIdle()
        assertFalse(viewModel.favouriteIds.value.contains(channelA.id))
    }

    @Test
    fun `T018 - isFavourite reflects current state`() = runTest {
        assertFalse(viewModel.isFavourite(channelA))
        viewModel.toggle(channelA)
        advanceUntilIdle()
        assertTrue(viewModel.isFavourite(channelA))
    }

    @Test
    fun `T018 - ids survive ViewModel recreation via DataStore`() = runTest {
        viewModel.toggle(channelA)
        advanceUntilIdle()

        // Recreate ViewModel — DataStore read happens on init
        val recreated = FavouritesViewModel(context)
        advanceUntilIdle()

        assertTrue(
            "Favourite should persist across ViewModel recreation",
            recreated.favouriteIds.value.contains(channelA.id),
        )
    }
}