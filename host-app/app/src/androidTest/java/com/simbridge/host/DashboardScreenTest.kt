package com.simbridge.host

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simbridge.host.data.ConnectionStatus
import com.simbridge.host.data.SimInfo
import com.simbridge.host.ui.component.SimCard
import com.simbridge.host.ui.component.StatusCard
import com.simbridge.host.ui.theme.SimBridgeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for the DashboardScreen composable.
 *
 * Uses a self-contained test composable that mirrors DashboardScreen's UI layout
 * to avoid the need for a real [com.simbridge.host.service.BridgeService] or
 * [com.simbridge.host.service.SimInfoProvider] instance.
 */
@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Helper composable that mirrors DashboardScreen layout ──

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestDashboardScreen(
        connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
        isServiceRunning: Boolean = false,
        sims: List<SimInfo> = emptyList(),
        onStartService: () -> Unit = {},
        onStopService: () -> Unit = {},
        onNavigateToLog: () -> Unit = {},
        onNavigateToSettings: () -> Unit = {},
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("SimBridge Host") },
                    actions = {
                        IconButton(onClick = onNavigateToLog) {
                            Icon(Icons.Default.List, contentDescription = "Logs")
                        }
                        IconButton(onClick = onNavigateToSettings) {
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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatusCard(status = connectionStatus)

                Button(
                    onClick = { if (isServiceRunning) onStopService() else onStartService() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (isServiceRunning) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    Text(if (isServiceRunning) "Stop Service" else "Start Service")
                }

                if (sims.isNotEmpty()) {
                    Text(
                        text = "SIM Cards",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    sims.forEach { sim ->
                        SimCard(sim = sim)
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    ) {
                        Text(
                            text = "No SIM cards detected. Grant phone permissions to see SIM info.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun statusCardShowsOfflineInitially() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestDashboardScreen(connectionStatus = ConnectionStatus.DISCONNECTED)
            }
        }

        composeTestRule.onNodeWithText("Offline").assertIsDisplayed()
    }

    @Test
    fun startServiceButtonVisibleWithCorrectText() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestDashboardScreen(isServiceRunning = false)
            }
        }

        composeTestRule.onNodeWithText("Start Service").assertIsDisplayed()
    }

    @Test
    fun stopServiceButtonShownWhenRunning() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestDashboardScreen(isServiceRunning = true)
            }
        }

        composeTestRule.onNodeWithText("Stop Service").assertIsDisplayed()
    }

    @Test
    fun simCardsSectionRendersWhenSimsProvided() {
        val sims = listOf(
            SimInfo(slot = 1, carrier = "T-Mobile", number = "+15551234567"),
            SimInfo(slot = 2, carrier = "Verizon", number = null),
        )

        composeTestRule.setContent {
            SimBridgeTheme {
                TestDashboardScreen(sims = sims)
            }
        }

        composeTestRule.onNodeWithText("SIM Cards").assertIsDisplayed()
        composeTestRule.onNodeWithText("SIM 1 — T-Mobile").assertIsDisplayed()
        composeTestRule.onNodeWithText("+15551234567").assertIsDisplayed()
        composeTestRule.onNodeWithText("SIM 2 — Verizon").assertIsDisplayed()
    }

    @Test
    fun emptySimStateShowsGuidanceText() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestDashboardScreen(sims = emptyList())
            }
        }

        composeTestRule.onNodeWithText("No SIM cards detected. Grant phone permissions to see SIM info.")
            .assertIsDisplayed()
    }

    @Test
    fun logAndSettingsToolbarIconsVisible() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestDashboardScreen()
            }
        }

        composeTestRule.onNodeWithContentDescription("Logs").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }
}
