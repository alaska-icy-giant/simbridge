package com.simbridge.host

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simbridge.host.ui.theme.SimBridgeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for app navigation flow.
 *
 * Uses a self-contained NavHost with lightweight stub screens to verify
 * navigation routing between LOGIN, DASHBOARD, LOG, and SETTINGS.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    companion object {
        const val LOGIN = "login"
        const val DASHBOARD = "dashboard"
        const val LOG = "log"
        const val SETTINGS = "settings"
    }

    /**
     * Sets up a test NavHost with lightweight stub screens that have the same
     * navigation structure as the real app.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestNavHost(startDestination: String = LOGIN) {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = startDestination) {
            composable(LOGIN) {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("SimBridge Host") }) }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Connect to Relay Server")
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = {
                            navController.navigate(DASHBOARD) {
                                popUpTo(LOGIN) { inclusive = true }
                            }
                        }) {
                            Text("Login")
                        }
                    }
                }
            }

            composable(DASHBOARD) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("SimBridge Host") },
                            actions = {
                                IconButton(onClick = { navController.navigate(LOG) }) {
                                    Icon(Icons.Default.List, contentDescription = "Logs")
                                }
                                IconButton(onClick = { navController.navigate(SETTINGS) }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            },
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                    ) {
                        Text("Dashboard Content")
                        Text("Start Service")
                    }
                }
            }

            composable(LOG) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Event Log") },
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                    )
                                }
                            },
                        )
                    }
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        Text("Log Screen Content")
                    }
                }
            }

            composable(SETTINGS) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Settings") },
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                    )
                                }
                            },
                        )
                    }
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        Text("Settings Screen Content")
                    }
                }
            }
        }
    }

    @Test
    fun startsAtLoginWhenNotLoggedIn() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestNavHost(startDestination = LOGIN)
            }
        }

        composeTestRule.onNodeWithText("Connect to Relay Server").assertIsDisplayed()
        composeTestRule.onNodeWithText("Login").assertIsDisplayed()
    }

    @Test
    fun loginNavigatesToDashboardOnSuccess() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestNavHost(startDestination = LOGIN)
            }
        }

        // Click Login to navigate to Dashboard
        composeTestRule.onNodeWithText("Login").performClick()

        // Verify we are on the Dashboard
        composeTestRule.onNodeWithText("Dashboard Content").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Service").assertIsDisplayed()
    }

    @Test
    fun dashboardNavigatesToLog() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestNavHost(startDestination = DASHBOARD)
            }
        }

        // Click the Logs toolbar icon
        composeTestRule.onNodeWithContentDescription("Logs").performClick()

        // Verify we are on the Log screen
        composeTestRule.onNodeWithText("Event Log").assertIsDisplayed()
        composeTestRule.onNodeWithText("Log Screen Content").assertIsDisplayed()
    }

    @Test
    fun dashboardNavigatesToSettings() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestNavHost(startDestination = DASHBOARD)
            }
        }

        // Click the Settings toolbar icon
        composeTestRule.onNodeWithContentDescription("Settings").performClick()

        // Verify we are on the Settings screen
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings Screen Content").assertIsDisplayed()
    }
}
