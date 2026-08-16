package com.streamsync.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.streamsync.android.ui.screens.*

sealed class Screen(val route: String, val title: String, val icon: String) {
    data object Dashboard : Screen("dashboard", "Dashboard", "home")
    data object Devices : Screen("devices", "Devices", "devices")
    data object Transfers : Screen("transfers", "Transfers", "swap_horiz")
    data object Stream : Screen("stream", "Stream", "cast")
    data object Settings : Screen("settings", "Settings", "settings")
    data object Clipboard : Screen("clipboard", "Clipboard", "content_paste")
    data object DeviceDetail : Screen("device/{deviceId}", "Device", "devices") {
        fun createRoute(deviceId: String) = "device/$deviceId"
    }
    data object FilePicker : Screen("file_picker", "Send Files", "file_upload")
    data object StreamPlayer : Screen("stream_player/{streamId}", "Stream Player", "play_circle") {
        fun createRoute(streamId: String) = "stream_player/$streamId"
    }

    companion object {
        val bottomBarRoutes = listOf(
            Dashboard.route, Devices.route, Transfers.route, Stream.route, Settings.route
        )
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }
        composable(Screen.Devices.route) {
            DevicesScreen(navController = navController)
        }
        composable(Screen.Transfers.route) {
            TransfersScreen(navController = navController)
        }
        composable(Screen.Stream.route) {
            StreamScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(Screen.Clipboard.route) {
            ClipboardScreen(navController = navController)
        }
        composable(
            route = Screen.DeviceDetail.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            DeviceDetailScreen(deviceId = deviceId, navController = navController)
        }
        composable(
            route = Screen.StreamPlayer.route,
            arguments = listOf(navArgument("streamId") { type = NavType.StringType })
        ) { backStackEntry ->
            val streamId = backStackEntry.arguments?.getString("streamId") ?: ""
            StreamPlayerScreen(streamId = streamId, navController = navController)
        }
    }
}
