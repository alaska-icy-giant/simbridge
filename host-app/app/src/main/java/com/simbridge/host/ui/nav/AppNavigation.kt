package com.simbridge.host.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.simbridge.host.data.Prefs
import com.simbridge.host.service.BridgeService
import com.simbridge.host.ui.screen.DashboardScreen
import com.simbridge.host.ui.screen.LogScreen
import com.simbridge.host.ui.screen.LoginScreen
import com.simbridge.host.ui.screen.SettingsScreen

object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val LOG = "log"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    prefs: Prefs,
    service: BridgeService?,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
) {
    val startDestination = if (prefs.isLoggedIn) Routes.DASHBOARD else Routes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                prefs = prefs,
                onLoginSuccess = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                service = service,
                onStartService = onStartService,
                onStopService = onStopService,
                onNavigateToLog = { navController.navigate(Routes.LOG) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.LOG) {
            LogScreen(
                service = service,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                prefs = prefs,
                onLogout = {
                    prefs.clear()
                    onStopService()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
