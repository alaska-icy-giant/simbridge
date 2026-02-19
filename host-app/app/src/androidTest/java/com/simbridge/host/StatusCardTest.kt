package com.simbridge.host

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simbridge.host.data.ConnectionStatus
import com.simbridge.host.ui.component.StatusCard
import com.simbridge.host.ui.theme.SimBridgeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for the StatusCard composable.
 *
 * Tests each [ConnectionStatus] state to verify the correct label and icon
 * content description are rendered.
 */
@RunWith(AndroidJUnit4::class)
class StatusCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun connectedStateShowsConnectedTextWithCheckIcon() {
        composeTestRule.setContent {
            SimBridgeTheme {
                StatusCard(status = ConnectionStatus.CONNECTED)
            }
        }

        composeTestRule.onNodeWithText("Connected").assertIsDisplayed()
        // The Icon's contentDescription is set to the label ("Connected")
        composeTestRule.onNodeWithContentDescription("Connected").assertIsDisplayed()
    }

    @Test
    fun connectingStateShowsConnectingText() {
        composeTestRule.setContent {
            SimBridgeTheme {
                StatusCard(status = ConnectionStatus.CONNECTING)
            }
        }

        composeTestRule.onNodeWithText("Connecting...").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Connecting...").assertIsDisplayed()
    }

    @Test
    fun disconnectedStateShowsOfflineText() {
        composeTestRule.setContent {
            SimBridgeTheme {
                StatusCard(status = ConnectionStatus.DISCONNECTED)
            }
        }

        composeTestRule.onNodeWithText("Offline").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Offline").assertIsDisplayed()
    }
}
