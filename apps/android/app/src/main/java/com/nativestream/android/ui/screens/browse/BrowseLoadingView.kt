package com.nativestream.android.ui.screens.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSType

@Composable
fun BrowseLoadingView() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Text(text = "Loading channels…", style = NSType.caption(), color = NSColors.text3)
    }
}