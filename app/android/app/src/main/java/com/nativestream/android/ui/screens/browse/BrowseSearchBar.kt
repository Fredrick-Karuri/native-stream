package com.nativestream.android.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nativestream.android.ui.components.IconButton
import com.nativestream.android.ui.components.NSTextField
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens


@Composable
fun BrowseSearchBar(
    searchText: String,
    onSearchChange: (String) -> Unit,
    onSearchClose: () -> Unit,
) {
    val dimens = NSDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(NSColors.surface)
            .padding(horizontal = dimens.spacing.lg, vertical = dimens.spacing.sm),
    ) {
        NSTextField(
            value         = searchText,
            onValueChange = onSearchChange,
            placeholder   = "Search channels…",
            modifier      = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(dimens.spacing.sm))
        IconButton(
            icon               = Icons.Default.Close,
            contentDescription = "Close search",
            onClick            = onSearchClose,
        )
    }
}
