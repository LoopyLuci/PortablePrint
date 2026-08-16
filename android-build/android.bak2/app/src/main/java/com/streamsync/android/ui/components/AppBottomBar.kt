package com.streamsync.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.streamsync.android.ui.navigation.Screen

data class BottomNavItem(
    val screen: Screen,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, Icons.Filled.Home, Icons.Outlined.Home, "Home"),
    BottomNavItem(Screen.Devices, Icons.Filled.Devices, Icons.Outlined.Devices, "Devices"),
    BottomNavItem(Screen.Transfers, Icons.Filled.SwapHoriz, Icons.Outlined.SwapHoriz, "Transfers"),
    BottomNavItem(Screen.Stream, Icons.Filled.Cast, Icons.Outlined.Cast, "Stream"),
    BottomNavItem(Screen.Settings, Icons.Filled.Settings, Icons.Outlined.Settings, "Settings"),
)

@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.screen.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.screen.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            )
        }
    }
}
