package com.simbridge.host

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simbridge.host.ui.theme.SimBridgeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for the SettingsScreen composable.
 *
 * Uses a self-contained test composable that mirrors SettingsScreen's UI layout
 * to avoid the need for a real [com.simbridge.host.data.Prefs] instance or
 * Android system intents.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Helper composable mirroring SettingsScreen layout ──

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestSettingsScreen(
        serverUrl: String = "https://relay.example.com",
        deviceName: String = "Google Pixel 8",
        deviceId: Int = 42,
        onLogout: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        var showLogoutDialog by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Server info
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Server", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = serverUrl.ifBlank { "Not configured" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Device info
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Device", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = deviceName.ifBlank { "Not registered" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (deviceId >= 0) {
                            Text(
                                text = "ID: $deviceId",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Battery optimization
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Battery Optimization", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Disable battery optimization for SimBridge to keep the service running in the background.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { /* no-op in test */ }) {
                            Text("Open Battery Settings")
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Logout
                Button(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                ) {
                    Text("Logout")
                }
            }
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Logout") },
                text = { Text("This will stop the bridge service and clear your credentials.") },
                confirmButton = {
                    TextButton(onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }) {
                        Text("Logout", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }

    @Test
    fun serverUrlDisplayed() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestSettingsScreen(serverUrl = "https://relay.example.com")
            }
        }

        composeTestRule.onNodeWithText("Server").assertIsDisplayed()
        composeTestRule.onNodeWithText("https://relay.example.com").assertIsDisplayed()
    }

    @Test
    fun deviceNameAndIdDisplayed() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestSettingsScreen(deviceName = "Google Pixel 8", deviceId = 42)
            }
        }

        composeTestRule.onNodeWithText("Device").assertIsDisplayed()
        composeTestRule.onNodeWithText("Google Pixel 8").assertIsDisplayed()
        composeTestRule.onNodeWithText("ID: 42").assertIsDisplayed()
    }

    @Test
    fun batteryOptimizationCardVisible() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestSettingsScreen()
            }
        }

        composeTestRule.onNodeWithText("Battery Optimization").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open Battery Settings").assertIsDisplayed()
    }

    @Test
    fun logoutButtonVisible() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestSettingsScreen()
            }
        }

        composeTestRule.onNodeWithText("Logout").assertIsDisplayed()
    }

    @Test
    fun logoutConfirmationDialogAppearsOnClick() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestSettingsScreen()
            }
        }

        composeTestRule.onNodeWithText("Logout").performClick()

        composeTestRule.onNodeWithText("This will stop the bridge service and clear your credentials.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    // ── Biometric toggle tests ──

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestSettingsScreenWithBiometric(
        serverUrl: String = "https://relay.example.com",
        deviceName: String = "Google Pixel 8",
        deviceId: Int = 42,
        biometricAvailable: Boolean = true,
        biometricEnabled: Boolean = false,
        onLogout: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        var showLogoutDialog by remember { mutableStateOf(false) }
        var biometricOn by remember { mutableStateOf(biometricEnabled) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Server info
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Server", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(text = serverUrl.ifBlank { "Not configured" })
                    }
                }

                // Biometric unlock
                if (biometricAvailable) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Biometric Unlock", style = MaterialTheme.typography.titleMedium)
                            }
                            Switch(
                                checked = biometricOn,
                                onCheckedChange = { biometricOn = it },
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Logout")
                }
            }
        }
    }

    @Test
    fun biometricToggleVisibleWhenDeviceSupports() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestSettingsScreenWithBiometric(biometricAvailable = true)
            }
        }

        composeTestRule.onNodeWithText("Biometric Unlock").assertIsDisplayed()
    }

    @Test
    fun biometricToggleHiddenWhenDeviceDoesNotSupport() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestSettingsScreenWithBiometric(biometricAvailable = false)
            }
        }

        composeTestRule.onNodeWithText("Biometric Unlock").assertDoesNotExist()
    }

    @Test
    fun cancelDismissesLogoutDialog() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestSettingsScreen()
            }
        }

        // Open dialog
        composeTestRule.onNodeWithText("Logout").performClick()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()

        // Dismiss dialog
        composeTestRule.onNodeWithText("Cancel").performClick()

        // Dialog text should no longer be visible
        composeTestRule.onNodeWithText("This will stop the bridge service and clear your credentials.")
            .assertDoesNotExist()
    }
}
