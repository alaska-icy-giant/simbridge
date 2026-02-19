package com.simbridge.client

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simbridge.client.ui.screen.SmsScreen
import com.simbridge.client.ui.theme.SimBridgeClientTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setContent() {
        rule.setContent {
            SimBridgeClientTheme {
                SmsScreen(service = null, onBack = {})
            }
        }
    }

    // ── Phone Number Field ──

    @Test
    fun phoneNumberFieldIsVisible() {
        setContent()
        rule.onNodeWithText("Phone Number").assertIsDisplayed()
    }

    // ── Message Body ──

    @Test
    fun messageBodyFieldIsVisible() {
        setContent()
        rule.onNodeWithText("Message").assertIsDisplayed()
    }

    // ── Send Button ──

    @Test
    fun sendButtonIsVisible() {
        setContent()
        rule.onNodeWithContentDescription("Send").assertIsDisplayed()
    }

    @Test
    fun sendButtonDisabledWhenFieldsEmpty() {
        setContent()
        rule.onNodeWithContentDescription("Send").assertIsNotEnabled()
    }

    // ── Title ──

    @Test
    fun titleIsVisible() {
        setContent()
        rule.onNodeWithText("Send SMS").assertIsDisplayed()
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
                SmsScreen(service = null, onBack = { backCalled = true })
            }
        }

        rule.onNodeWithContentDescription("Back").performClick()
        assert(backCalled)
    }

    // ── Recent Messages Header ──

    @Test
    fun recentMessagesHeaderIsVisible() {
        setContent()
        rule.onNodeWithText("Recent Messages").assertIsDisplayed()
    }
}
