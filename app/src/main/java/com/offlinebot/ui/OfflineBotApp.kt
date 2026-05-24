package com.offlinebot.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.offlinebot.ui.buckets.BucketsScreen
import com.offlinebot.ui.chat.ChatScreen
import com.offlinebot.ui.home.HomeScreen
import com.offlinebot.ui.models.ModelsScreen
import com.offlinebot.ui.settings.SettingsScreen
import com.offlinebot.ui.automation.AutomationScreen
import com.offlinebot.ui.status.SystemStatusScreen
import com.offlinebot.ui.timeline.TimelineScreen

private enum class AppDestination(val route: String, val label: String) {
    Home("home", "Home"),
    Timeline("timeline", "Timeline"),
    Chat("chat", "Chat"),
    Status("status", "Status"),
    Automation("automation", "Auto"),
    Models("models", "Models"),
    Settings("settings", "Settings")
}

@Composable
fun OfflineBotApp() {
    val navController = rememberNavController()
    val destinations = AppDestination.entries
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: AppDestination.Home.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                launchSingleTop = true
                                popUpTo(AppDestination.Home.route)
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (destination) {
                                    AppDestination.Home -> Icons.Outlined.Home
                                    AppDestination.Timeline -> Icons.Outlined.Timeline
                                    AppDestination.Chat -> Icons.AutoMirrored.Outlined.Chat
                                    AppDestination.Status -> Icons.Outlined.MonitorHeart
                                    AppDestination.Automation -> Icons.Outlined.AutoAwesome
                                    AppDestination.Models -> Icons.Outlined.SmartToy
                                    AppDestination.Settings -> Icons.Outlined.Settings
                                },
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route
        ) {
            composable(AppDestination.Home.route) { HomeScreen(padding) }
            composable(AppDestination.Timeline.route) { TimelineScreen(padding) }
            composable(AppDestination.Chat.route) { ChatScreen(padding) }
            composable(AppDestination.Status.route) { SystemStatusScreen(padding) }
            composable(AppDestination.Automation.route) { AutomationScreen(padding) }
            composable(AppDestination.Models.route) { ModelsScreen(padding) }
            composable(AppDestination.Settings.route) { SettingsScreen(padding) }
        }
    }
}
