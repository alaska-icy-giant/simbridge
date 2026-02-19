package com.simbridge.client

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simbridge.client.data.Prefs
import com.simbridge.client.ui.screen.SettingsScreen
import com.simbridge.client.ui.theme.SimBridgeClientTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = Prefs(context)
        prefs.clear()
    }

    // ── Server Info ──

    @Test
    fun serverSectionIsVisible() {
        rule.setContent {
            SimBridgeClientTheme {
                SettingsScreen(prefs = prefs, onLogout = {}, onBack = {})
            }
        }
        rule.onNodeWithText("Server").assertIsDisplayed()
    }

    @Test
    fun serverShowsNotConfiguredWhenBlank() {
        rule.setContent {
            SimBridgeClientTheme {
                SettingsScreen(prefs = prefs, onLogout = {}, onBack = {})
            }
        }
        rule.onNodeWithText("Not configured").assertIsDisplayed()
    }

    @Test
    fun serverShowsUrlWhenConfigured() {
        prefs.serverUrl = "https://relay.example.com"
        rule.setContent {
            SimBridgeClientTheme {
                SettingsScreen(prefs = prefs, onLogout = {}, onBack = {})
            }
        }
        rule.onNodeWithText("https://relay.example.com").assertIsDisplayed()
    }

    // ── Device Info ──

    @Test
    fun deviceSectionIsVisible() {
        rule.setContent {
            SimBridgeClientTheme {
                SettingsScreen(prefs = prefs, onLogout = {}, onBack = {})
            }
        }
        rule.onNodeWithText("This Device (Client)").assertIsDisplayed()
    }

    @Test
    fun deviceShowsNotRegisteredWhenBlank() {
        rule.setContent {
            SimBridgeClientTheme {
                SettingsScreen(prefs = prefs, onLogout = {}, onBack = {})
            }
        }
        rule.onNodeWithText("Not registered").assertIsDisplayed()
    }

    @Test
    fun deviceShowsNameWhenConfigured() {
        prefs.deviceName = "Samsung Galaxy S24"
        prefs.deviceId = 5
        rule.setContent {
            SimBridgeClientTheme {
                SettingsScreen(prefs = prefs, onLogout = {}, onBack = {})
            }
        }
        rule.onNodeWithText("Samsung Galaxy S24").assertIsDisplayed()
        rule.onNodeWithText("ID: 5").assertIsDisplayed()
    }

    // ── Paired Host ──

    @Test
    fun pairedHostSectionIsVisible() {
        rule.setContent {
            SimBridgeClientTheme {
                SettingsScreen(prefs = prefs, onLogout = {}, onBack = {})
            }
        }
        rule.onNodeWithText("Paired Host").assertIsDisplayed()
    }

    @Test
    fun pairedHostShowsNotPairedWhenDefault() {
        rule.setContent {
            SimBridgeClientTheme {
                SettingsScreen(prefs = prefs, onLogout = {}, onBack = {})
            }
        }
        rule.onNodeWithText("Not paired").assertIsDisplayed()
    }

    @Test
    fun pairedHostShowsNameWhenPaired() {
        prefs.pairedHostId = 3
        prefs.pairedHostName = "Host Phone A"
        rule.setContent {
            SimBridgeClientTheme {
                SettingsScreen(prefs = prefs, onLogout = {}, onBack = {})
            }
        }
        rule.onNodeWithText("Host Phone A").assertIsDisplayed()
        rule.onNodeWithText("ID: 3").assertIsDisplayed()
    }

    // ── Logout Button ──

    @Test
    fun logoutButtonIsVisible() {
        rule.setContent {
            SimBridgeClientTheme {
                SettingsScreen(prefs = prefs, onLogout = {}, onBack = {})
            }
        }
        rule.onNodeWithText("Logout").assertIsDisplayed()
    }

    // ── Logout Dialog ──

    @Test
    fun logoutDialogAppearsOnClick() {
        rule.setContent {
            SimBridgeClientTheme {
                SettingsScreen(prefs = prefs, onLogout = {}, onBack = {})
            }
        }

        rule.onNodeWithText("Logout").performClick()
        rule.onNodeWithText("This will stop the service and clear all credentials and pairing.")
            .assertIsDisplayed()
    }

    @Test
    fun logoutDialogHasCancelButton() {
        rule.setContent {
            SimBridgeClientTheme {
                SettingsScreen(prefs = prefs, onLogout = {}, onBack = {})
            }
        }

        rule.onNodeWithText("Logout").performClick()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun logoutDialogCancelDismisses() {
        rule.setContent {
            SimBridgeClientTheme {
                SettingsScreen(prefs = prefs, onLogout = {}, onBack = {})
            }
        }

        rule.onNodeWithText("Logout").performClick()
        rule.onNodeWithText("Cancel").performClick()
        // Dialog text should no longer be visible
        rule.onNodeWithText("This will stop the service and clear all credentials and pairing.")
            .assertDoesNotExist()
    }

    // ── Back Button ──

    @Test
    fun backButtonIsVisible() {
        rule.setContent {
            SimBridgeClientTheme {
                SettingsScreen(prefs = prefs, onLogout = {}, onBack = {})
            }
        }
        rule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    // ── Title ──

    @Test
    fun titleIsVisible() {
        rule.setContent {
            SimBridgeClientTheme {
                SettingsScreen(prefs = prefs, onLogout = {}, onBack = {})
            }
        }
        rule.onNodeWithText("Settings").assertIsDisplayed()
    }
}
