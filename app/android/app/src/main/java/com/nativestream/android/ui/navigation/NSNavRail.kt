package com.nativestream.android.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nativestream.android.ui.theme.NSColors
import com.nativestream.android.ui.theme.NSType
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Television
import com.adamglin.phosphoricons.regular.GridFour
import com.adamglin.phosphoricons.regular.GearSix

private val RAIL_WIDTH      = 80.dp
private val RAIL_ICON_SIZE  = 22.dp
private val RAIL_PILL_W     = 3.dp
private val RAIL_PILL_H     = 24.dp
private val RAIL_PILL_R     = 2.dp

@Composable
fun NSNavRail(
    navController: NavController,
    destinations: List<AppDestination>,
    onDestinationSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Row(modifier = modifier) {
        Column(
            modifier = Modifier
                .width(RAIL_WIDTH)
                .fillMaxHeight()
                .background(NSColors.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            destinations.forEach { destination ->
                RailItem(
                    destination = destination,
                    isActive    = currentRoute == destination.route,
                    onClick     = { onDestinationSelected(destination) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        // Right border
        Box(
            modifier = Modifier
                .width(0.5.dp)
                .fillMaxHeight()
                .background(NSColors.border)
        )
    }
}

@Composable
private fun RailItem(
    destination: AppDestination,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val iconColor  = if (isActive) NSColors.accent else NSColors.text3
    val labelColor = if (isActive) NSColors.accent else NSColors.text3

    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Active pill
        if (isActive) {
            Box(
                modifier = Modifier
                    .width(RAIL_PILL_W)
                    .height(RAIL_PILL_H)
                    .clip(RoundedCornerShape(RAIL_PILL_R))
                    .background(NSColors.accent)
            )
            Spacer(modifier = Modifier.width(6.dp))
        } else {
            Spacer(modifier = Modifier.width(RAIL_PILL_W + 6.dp))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector        = destination.railIcon(),
                contentDescription = destination.railLabel(),
                tint               = iconColor,
                modifier           = Modifier.size(RAIL_ICON_SIZE),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text  = destination.railLabel(),
                style = NSType.caption(),
                color = labelColor,
            )
        }
    }
}

private fun AppDestination.railLabel(): String = when (this) {
    AppDestination.Now      -> "Now"
    AppDestination.Browse   -> "Browse"
    AppDestination.Settings -> "Settings"
}

@Composable
private fun AppDestination.railIcon(): ImageVector = when (this) {
    AppDestination.Now      -> PhosphorIcons.Regular.Television
    AppDestination.Browse   -> PhosphorIcons.Regular.GridFour
    AppDestination.Settings -> PhosphorIcons.Regular.GearSix
}