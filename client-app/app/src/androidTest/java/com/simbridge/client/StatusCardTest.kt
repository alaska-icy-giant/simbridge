package com.simbridge.client

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simbridge.client.data.ConnectionStatus
import com.simbridge.client.ui.component.StatusCard
import com.simbridge.client.ui.theme.SimBridgeClientTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusCardTest {

    @get:Rule
    val rule = createComposeRule()

    // ── Connected State ──

    @Test
    fun connectedStateShowsConnectedLabel() {
        rule.setContent {
            SimBridgeClientTheme {
                StatusCard(status = ConnectionStatus.CONNECTED)
            }
        }
        rule.onNodeWithText("Connected").assertIsDisplayed()
    }

    // ── Connecting State ──

    @Test
    fun connectingStateShowsConnectingLabel() {
        rule.setContent {
            SimBridgeClientTheme {
                StatusCard(status = ConnectionStatus.CONNECTING)
            }
        }
        rule.onNodeWithText("Connecting...").assertIsDisplayed()
    }

    // ── Disconnected State ──

    @Test
    fun disconnectedStateShowsOfflineLabel() {
        rule.setContent {
            SimBridgeClientTheme {
                StatusCard(status = ConnectionStatus.DISCONNECTED)
            }
        }
        rule.onNodeWithText("Offline").assertIsDisplayed()
    }

    // ── Icons ──

    @Test
    fun connectedStateHasCheckCircleIcon() {
        rule.setContent {
            SimBridgeClientTheme {
                StatusCard(status = ConnectionStatus.CONNECTED)
            }
        }
        // Icon content description matches the label
        rule.onNodeWithContentDescription("Connected").assertIsDisplayed()
    }

    @Test
    fun connectingStateHasCloudIcon() {
        rule.setContent {
            SimBridgeClientTheme {
                StatusCard(status = ConnectionStatus.CONNECTING)
            }
        }
        rule.onNodeWithContentDescription("Connecting...").assertIsDisplayed()
    }

    @Test
    fun disconnectedStateHasCloudOffIcon() {
        rule.setContent {
            SimBridgeClientTheme {
                StatusCard(status = ConnectionStatus.DISCONNECTED)
            }
        }
        rule.onNodeWithContentDescription("Offline").assertIsDisplayed()
    }
}
