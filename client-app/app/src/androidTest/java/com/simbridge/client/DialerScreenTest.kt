package com.simbridge.client

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simbridge.client.ui.screen.DialerScreen
import com.simbridge.client.ui.theme.SimBridgeClientTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DialerScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setContent() {
        rule.setContent {
            SimBridgeClientTheme {
                DialerScreen(service = null, onBack = {})
            }
        }
    }

    // ── Phone Number Field ──

    @Test
    fun phoneNumberFieldIsVisible() {
        setContent()
        rule.onNodeWithText("Phone Number").assertIsDisplayed()
    }

    // ── Call Button (FAB) ──

    @Test
    fun callButtonIsVisible() {
        setContent()
        rule.onNodeWithContentDescription("Call").assertIsDisplayed()
    }

    // ── Title ──

    @Test
    fun titleIsVisible() {
        setContent()
        rule.onNodeWithText("Phone").assertIsDisplayed()
    }

    // ── Back Button ──

    @Test
    fun backButtonIsVisible() {
        setContent()
        rule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun backButtonNavigates() {
        var backCalled = false
        rule.setContent {
            SimBridgeClientTheme {
                DialerScreen(service = null, onBack = { backCalled = true })
            }
        }

        rule.onNodeWithContentDescription("Back").performClick()
        assert(backCalled)
    }

    // ── Phone Number Input ──

    @Test
    fun phoneNumberFieldAcceptsInput() {
        setContent()
        rule.onNodeWithText("Phone Number").performTextInput("+15551234567")
        rule.onNodeWithText("+15551234567").assertIsDisplayed()
    }

    // ── Invalid Phone Number Format ──

    @Test
    fun invalidPhoneNumberShowsError() {
        setContent()
        rule.onNodeWithText("Phone Number").performTextInput("abc")
        rule.onNodeWithText("Invalid phone number format").assertIsDisplayed()
    }
}
