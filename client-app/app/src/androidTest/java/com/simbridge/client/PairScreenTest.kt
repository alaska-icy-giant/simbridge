package com.simbridge.client

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simbridge.client.data.Prefs
import com.simbridge.client.ui.screen.PairScreen
import com.simbridge.client.ui.theme.SimBridgeClientTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PairScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = Prefs(context)
        prefs.clear()
        prefs.token = "test-token"
        prefs.serverUrl = "https://example.com"
        prefs.deviceId = 1
    }

    private fun setContent() {
        rule.setContent {
            SimBridgeClientTheme {
                PairScreen(prefs = prefs, onPaired = {})
            }
        }
    }

    // ── Code Input Visible ──

    @Test
    fun codeInputFieldIsVisible() {
        setContent()
        rule.onNodeWithText("6-digit code").assertIsDisplayed()
    }

    @Test
    fun titleIsVisible() {
        setContent()
        rule.onNodeWithText("Enter Pairing Code").assertIsDisplayed()
    }

    @Test
    fun instructionTextIsVisible() {
        setContent()
        rule.onNodeWithText("Open the Host app on Phone A and generate a pairing code from the dashboard.")
            .assertIsDisplayed()
    }

    @Test
    fun pairButtonIsVisible() {
        setContent()
        rule.onNodeWithText("Pair").assertIsDisplayed()
    }

    // ── Button Disabled with Short Code ──

    @Test
    fun pairButtonDisabledWhenCodeEmpty() {
        setContent()
        rule.onNodeWithText("Pair").assertIsNotEnabled()
    }

    @Test
    fun pairButtonDisabledWithPartialCode() {
        setContent()
        rule.onNodeWithText("6-digit code").performTextInput("123")
        rule.onNodeWithText("Pair").assertIsNotEnabled()
    }

    @Test
    fun pairButtonDisabledWith5Digits() {
        setContent()
        rule.onNodeWithText("6-digit code").performTextInput("12345")
        rule.onNodeWithText("Pair").assertIsNotEnabled()
    }

    @Test
    fun pairButtonEnabledWith6Digits() {
        setContent()
        rule.onNodeWithText("6-digit code").performTextInput("123456")
        rule.onNodeWithText("Pair").assertIsEnabled()
    }

    // ── Digit-Only Filter ──

    @Test
    fun codeFieldFiltersNonDigitCharacters() {
        setContent()
        rule.onNodeWithText("6-digit code").performTextInput("12ab34")
        // Only digits should remain: "1234"
        rule.onNodeWithText("1234").assertIsDisplayed()
    }

    @Test
    fun codeFieldLimitedTo6Characters() {
        setContent()
        rule.onNodeWithText("6-digit code").performTextInput("1234567890")
        // Only first 6 digits should be accepted
        rule.onNodeWithText("123456").assertIsDisplayed()
    }
}
