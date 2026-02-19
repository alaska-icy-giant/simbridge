package com.simbridge.client.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.simbridge.client.data.Prefs
import com.simbridge.client.data.SecureTokenStore
import com.simbridge.client.service.ClientService
import com.simbridge.client.ui.screen.*

object Routes {
    const val LOGIN = "login"
    const val BIOMETRIC = "biometric"
    const val PAIR = "pair"
    const val DASHBOARD = "dashboard"
    const val SMS = "sms"
    const val DIALER = "dialer"
    const val LOG = "log"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    prefs: Prefs,
    service: ClientService?,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
) {
    val context = LocalContext.current
    val secureTokenStore = remember { SecureTokenStore(context) }

    val startDestination = when {
        prefs.biometricEnabled && secureTokenStore.getToken() != null -> Routes.BIOMETRIC
        !prefs.isLoggedIn -> Routes.LOGIN
        !prefs.isPaired -> Routes.PAIR
        else -> Routes.DASHBOARD
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(prefs = prefs, onLoginSuccess = {
                val dest = if (prefs.isPaired) Routes.DASHBOARD else Routes.PAIR
                navController.navigate(dest) { popUpTo(Routes.LOGIN) { inclusive = true } }
            })
        }

        composable(Routes.BIOMETRIC) {
            BiometricPromptScreen(
                prefs = prefs,
                secureTokenStore = secureTokenStore,
                onSuccess = {
                    val dest = if (prefs.isPaired) Routes.DASHBOARD else Routes.PAIR
                    navController.navigate(dest) {
                        popUpTo(Routes.BIOMETRIC) { inclusive = true }
                    }
                },
                onFallbackToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.BIOMETRIC) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.PAIR) {
            PairScreen(prefs = prefs, onPaired = {
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.PAIR) { inclusive = true }
                }
            })
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                service = service,
                onStartService = onStartService,
                onStopService = onStopService,
                onNavigateToSms = { navController.navigate(Routes.SMS) },
                onNavigateToDialer = { navController.navigate(Routes.DIALER) },
                onNavigateToLog = { navController.navigate(Routes.LOG) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SMS) {
            SmsScreen(service = service, onBack = { navController.popBackStack() })
        }

        composable(Routes.DIALER) {
            DialerScreen(service = service, onBack = { navController.popBackStack() })
        }

        composable(Routes.LOG) {
            LogScreen(service = service, onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                prefs = prefs,
                onLogout = {
                    secureTokenStore.clear()
                    prefs.clear()
                    onStopService()
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
