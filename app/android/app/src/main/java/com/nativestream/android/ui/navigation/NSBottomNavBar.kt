// app/src/main/java/com/nativestream/android/ui/navigation/NSBottomNavBar.kt
//
// NS-008: Bottom Navigation Bar
// 3-tab bar: Now · Browse · Settings.
// Active tab shown with accent colour + pill indicator, matching designs.

package com.nativestream.android.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nativestream.android.R
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSDimens
import com.nativestream.android.ui.theme.NSType
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color

private val NAV_BAR_HEIGHT     = 56.dp
private val NAV_PILL_WIDTH     = 24.dp
private val NAV_PILL_HEIGHT    = 3.dp
private val NAV_ICON_SIZE      = 20.dp
private val NAV_PILL_RADIUS    = 2.dp

@Composable
fun NSBottomNavBar(
    navController: NavController,
    destinations: List<AppDestination>,
    onDestinationSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(NAV_BAR_HEIGHT)
            .background(NSColors.surface)
    ) {
        // Top border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(NSColors.border)
                .align(Alignment.TopCenter)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            destinations.forEach { destination ->
                val isActive = currentRoute == destination.route
                NavItem(
                    destination = destination,
                    isActive    = isActive,
                    onClick     = { onDestinationSelected(destination) },
                    modifier    = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: AppDestination,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconColor  = if (isActive) NSColors.accent  else NSColors.text3
    val labelColor = if (isActive) NSColors.accent  else NSColors.text3

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Active pill indicator
        if (isActive) {
            Box(
                modifier = Modifier
                    .width(NAV_PILL_WIDTH)
                    .height(NAV_PILL_HEIGHT)
                    .clip(RoundedCornerShape(NAV_PILL_RADIUS))
                    .background(NSColors.accent)
            )
            Spacer(modifier = Modifier.height(2.dp))
        } else {
            // Reserve same space so icons stay vertically aligned
            Spacer(modifier = Modifier.height(NAV_PILL_HEIGHT + 2.dp))
        }

        Icon(
            imageVector         = destination.icon(),
            contentDescription  = destination.label(),
            tint                = iconColor,
            modifier            = Modifier.size(NAV_ICON_SIZE),
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text  = destination.label(),
            style = NSType.caption(),
            color = labelColor,
        )
    }
}

// ── Destination metadata ──────────────────────────────────────────────────────

private fun AppDestination.label(): String = when (this) {
    AppDestination.Now      -> "Now"
    AppDestination.Browse   -> "Browse"
    AppDestination.Settings -> "Settings"
}

@Composable
private fun AppDestination.icon(): ImageVector = when (this) {
    AppDestination.Now      -> ImageVector.vectorResource(R.drawable.ic_nav_now)
    AppDestination.Browse   -> ImageVector.vectorResource(R.drawable.ic_nav_browse)
    AppDestination.Settings -> ImageVector.vectorResource(R.drawable.ic_nav_settings)
}