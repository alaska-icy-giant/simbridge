package com.simbridge.client

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simbridge.client.ui.screen.LogScreen
import com.simbridge.client.ui.theme.SimBridgeClientTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogScreenTest {

    @get:Rule
    val rule = createComposeRule()

    // ── Empty State ──

    @Test
    fun emptyStateShowsNoEventsMessage() {
        rule.setContent {
            SimBridgeClientTheme {
                LogScreen(service = null, onBack = {})
            }
        }
        rule.onNodeWithText("No events yet.").assertIsDisplayed()
    }

    // ── Title ──

    @Test
    fun titleIsVisible() {
        rule.setContent {
            SimBridgeClientTheme {
                LogScreen(service = null, onBack = {})
            }
        }
        rule.onNodeWithText("Event Log").assertIsDisplayed()
    }

    // ── Back Button ──

    @Test
    fun backButtonIsVisible() {
        rule.setContent {
            SimBridgeClientTheme {
                LogScreen(service = null, onBack = {})
            }
        }
        rule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun backButtonNavigates() {
        var backCalled = false
        rule.setContent {
            SimBridgeClientTheme {
                LogScreen(service = null, onBack = { backCalled = true })
            }
        }

        rule.onNodeWithContentDescription("Back").performClick()
        assert(backCalled)
    }
}
