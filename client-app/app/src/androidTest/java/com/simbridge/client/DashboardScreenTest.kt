package com.simbridge.client

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simbridge.client.ui.screen.DashboardScreen
import com.simbridge.client.ui.theme.SimBridgeClientTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setContent(service: Nothing? = null) {
        rule.setContent {
            SimBridgeClientTheme {
                DashboardScreen(
                    service = service,
                    onStartService = {},
                    onStopService = {},
                    onNavigateToSms = {},
                    onNavigateToDialer = {},
                    onNavigateToLog = {},
                    onNavigateToSettings = {},
                )
            }
        }
    }

    // ── StatusCard Shown ──

    @Test
    fun statusCardShowsOfflineWhenNoService() {
        setContent(service = null)
        rule.onNodeWithText("Offline").assertIsDisplayed()
    }

    // ── Start / Stop Button ──

    @Test
    fun startButtonShownWhenServiceNotRunning() {
        setContent(service = null)
        rule.onNodeWithText("Start Service").assertIsDisplayed()
    }

    @Test
    fun titleIsVisible() {
        setContent()
        rule.onNodeWithText("SimBridge Client").assertIsDisplayed()
    }

    // ── Action Buttons ──

    @Test
    fun smsButtonIsVisible() {
        setContent()
        rule.onNodeWithText("SMS").assertIsDisplayed()
    }

    @Test
    fun callButtonIsVisible() {
        setContent()
        rule.onNodeWithText("Call").assertIsDisplayed()
    }

    // ── Navigation Icons ──

    @Test
    fun logsButtonIsVisible() {
        setContent()
        rule.onNodeWithContentDescription("Logs").assertIsDisplayed()
    }

    @Test
    fun settingsButtonIsVisible() {
        setContent()
        rule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    // ── Start Button Click ──

    @Test
    fun startButtonCanBeClicked() {
        var startCalled = false
        rule.setContent {
            SimBridgeClientTheme {
                DashboardScreen(
                    service = null,
                    onStartService = { startCalled = true },
                    onStopService = {},
                    onNavigateToSms = {},
                    onNavigateToDialer = {},
                    onNavigateToLog = {},
                    onNavigateToSettings = {},
                )
            }
        }

        rule.onNodeWithText("Start Service").performClick()
        assert(startCalled)
    }

    // ── Navigation Button Clicks ──

    @Test
    fun smsButtonNavigates() {
        var navigated = false
        rule.setContent {
            SimBridgeClientTheme {
                DashboardScreen(
                    service = null,
                    onStartService = {},
                    onStopService = {},
                    onNavigateToSms = { navigated = true },
                    onNavigateToDialer = {},
                    onNavigateToLog = {},
                    onNavigateToSettings = {},
                )
            }
        }

        rule.onNodeWithText("SMS").performClick()
        assert(navigated)
    }

    @Test
    fun callButtonNavigates() {
        var navigated = false
        rule.setContent {
            SimBridgeClientTheme {
                DashboardScreen(
                    service = null,
                    onStartService = {},
                    onStopService = {},
                    onNavigateToSms = {},
                    onNavigateToDialer = { navigated = true },
                    onNavigateToLog = {},
                    onNavigateToSettings = {},
                )
            }
        }

        rule.onNodeWithText("Call").performClick()
        assert(navigated)
    }
}
