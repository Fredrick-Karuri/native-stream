// app/src/androidTest/java/com/nativestream/android/AddChannelSheetTest.kt
//
// AddChannelSheet: validation

package com.nativestream.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.nativestream.android.ui.screens.browse.AddChannelSheet
import com.nativestream.android.ui.viewmodel.ChannelManagerViewModel
import com.nativestream.android.ui.viewmodel.ChannelLoadingViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class AddChannelSheetTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createComposeRule()

    private lateinit var channelManagerViewModel: ChannelManagerViewModel
    private lateinit var loadingViewModel: ChannelLoadingViewModel
    private var dismissed = false

    @Before
    fun setUp() {
        hiltRule.inject()
        channelManagerViewModel = mockk(relaxed = true)
        loadingViewModel = mockk(relaxed = true)
        dismissed               = false

        every { channelManagerViewModel.isLoading } returns MutableStateFlow(false)
        every { channelManagerViewModel.error     } returns MutableStateFlow(null)
    }

    private fun setContent() {
        composeRule.setContent {
            AddChannelSheet(
                onDone                  = { dismissed = true },
                loadingViewModel        = loadingViewModel,
                channelManagerViewModel = channelManagerViewModel,
            )
        }
    }

    @Test
    fun addChannelButton_disabledWhenNameOrUrlEmpty() {
        setContent()
        // Both fields empty — button should be disabled
        composeRule.onNodeWithText("Add Channel").assertIsNotEnabled()
    }

    @Test
    fun addChannelButton_disabledWhenOnlyNameProvided() {
        setContent()
        composeRule.onNodeWithText("e.g. NBC").performTextInput("My Channel")
        composeRule.onNodeWithText("Add Channel").assertIsNotEnabled()
    }

    @Test
    fun validSubmission_callsAddChannelWithCorrectArgs() {
        setContent()
        composeRule.onNodeWithText("e.g. NBC").performTextInput("BBC One")
        composeRule.onNodeWithText("https://…").performTextInput("http://stream.example.com/bbc1.m3u8")
        composeRule.onNodeWithText("Add Channel").performClick()

        verify {
            channelManagerViewModel.addChannel(
                name       = "BBC One",
                streamUrl  = "http://stream.example.com/bbc1.m3u8",
                groupTitle = any(),
                tvgId      = any(),
                logoUrl    = any(),
                keywords   = any(),
                onSuccess  = any(),
            )
        }
    }

    @Test
    fun serverError_showsInlineErrorText() {
        every { channelManagerViewModel.error } returns MutableStateFlow("Channel already exists")
        setContent()
        composeRule.onNodeWithText("Channel already exists").assertIsDisplayed()
    }

    @Test
    fun cancelButton_dismissesSheet_withoutApiCall() {
        setContent()
        composeRule.onNodeWithText("Cancel").performClick()
        assert(dismissed)
        verify(exactly = 0) { channelManagerViewModel.addChannel(any(), any(), any(), any(), any(), any(), any()) }
    }
}