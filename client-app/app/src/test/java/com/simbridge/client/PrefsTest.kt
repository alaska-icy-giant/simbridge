package com.simbridge.client

import android.content.Context
import android.content.SharedPreferences
import com.simbridge.client.data.Prefs
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PrefsTest {

    private val context = mockk<Context>()
    private val sharedPrefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val store = mutableMapOf<String, Any?>()

    private lateinit var prefs: Prefs

    @BeforeEach
    fun setUp() {
        store.clear()

        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.edit() } returns editor

        // String get/put
        every { sharedPrefs.getString(any(), any()) } answers {
            store[firstArg()] as? String ?: secondArg()
        }
        every { editor.putString(any(), any()) } answers {
            store[firstArg()] = secondArg<String?>()
            editor
        }

        // Int get/put
        every { sharedPrefs.getInt(any(), any()) } answers {
            store[firstArg()] as? Int ?: secondArg()
        }
        every { editor.putInt(any(), any()) } answers {
            store[firstArg()] = secondArg<Int>()
            editor
        }

        every { editor.clear() } answers {
            store.clear()
            editor
        }
        every { editor.apply() } just Runs

        prefs = Prefs(context)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ── serverUrl ──

    @Test
    fun `test serverUrl defaults to empty string`() {
        assertEquals("", prefs.serverUrl)
    }

    @Test
    fun `test serverUrl saves and reads`() {
        prefs.serverUrl = "https://example.com"
        assertEquals("https://example.com", prefs.serverUrl)
    }

    @Test
    fun `test serverUrl trims trailing slash on save`() {
        prefs.serverUrl = "https://example.com/"
        assertEquals("https://example.com", prefs.serverUrl)
    }

    // ── token ──

    @Test
    fun `test token defaults to empty string`() {
        assertEquals("", prefs.token)
    }

    @Test
    fun `test token saves and reads`() {
        prefs.token = "jwt-token-abc"
        assertEquals("jwt-token-abc", prefs.token)
    }

    // ── deviceId ──

    @Test
    fun `test deviceId defaults to -1`() {
        assertEquals(-1, prefs.deviceId)
    }

    @Test
    fun `test deviceId saves and reads`() {
        prefs.deviceId = 42
        assertEquals(42, prefs.deviceId)
    }

    // ── deviceName ──

    @Test
    fun `test deviceName defaults to empty string`() {
        assertEquals("", prefs.deviceName)
    }

    @Test
    fun `test deviceName saves and reads`() {
        prefs.deviceName = "Samsung Galaxy S24"
        assertEquals("Samsung Galaxy S24", prefs.deviceName)
    }

    // ── pairedHostId ──

    @Test
    fun `test pairedHostId defaults to -1`() {
        assertEquals(-1, prefs.pairedHostId)
    }

    @Test
    fun `test pairedHostId saves and reads`() {
        prefs.pairedHostId = 7
        assertEquals(7, prefs.pairedHostId)
    }

    // ── pairedHostName ──

    @Test
    fun `test pairedHostName defaults to empty string`() {
        assertEquals("", prefs.pairedHostName)
    }

    @Test
    fun `test pairedHostName saves and reads`() {
        prefs.pairedHostName = "Host Phone A"
        assertEquals("Host Phone A", prefs.pairedHostName)
    }

    // ── isLoggedIn ──

    @Test
    fun `test isLoggedIn true when token serverUrl and deviceId set`() {
        prefs.token = "some-token"
        prefs.serverUrl = "https://example.com"
        prefs.deviceId = 1

        assertTrue(prefs.isLoggedIn)
    }

    @Test
    fun `test isLoggedIn false when token is blank`() {
        prefs.serverUrl = "https://example.com"
        prefs.deviceId = 1

        assertFalse(prefs.isLoggedIn)
    }

    @Test
    fun `test isLoggedIn false when serverUrl is blank`() {
        prefs.token = "some-token"
        prefs.deviceId = 1

        assertFalse(prefs.isLoggedIn)
    }

    @Test
    fun `test isLoggedIn false when deviceId is -1`() {
        prefs.token = "some-token"
        prefs.serverUrl = "https://example.com"

        assertFalse(prefs.isLoggedIn)
    }

    @Test
    fun `test isLoggedIn false when all empty`() {
        assertFalse(prefs.isLoggedIn)
    }

    // ── isPaired ──

    @Test
    fun `test isPaired true when pairedHostId is 0`() {
        prefs.pairedHostId = 0
        assertTrue(prefs.isPaired)
    }

    @Test
    fun `test isPaired true when pairedHostId is positive`() {
        prefs.pairedHostId = 5
        assertTrue(prefs.isPaired)
    }

    @Test
    fun `test isPaired false when pairedHostId is -1 (default)`() {
        assertFalse(prefs.isPaired)
    }

    // ── clear ──

    @Test
    fun `test clear removes all keys`() {
        prefs.token = "tok"
        prefs.serverUrl = "https://example.com"
        prefs.deviceId = 10
        prefs.deviceName = "Phone"
        prefs.pairedHostId = 5
        prefs.pairedHostName = "Host"

        prefs.clear()

        assertEquals("", prefs.serverUrl)
        assertEquals("", prefs.token)
        assertEquals(-1, prefs.deviceId)
        assertEquals("", prefs.deviceName)
        assertEquals(-1, prefs.pairedHostId)
        assertEquals("", prefs.pairedHostName)
    }

    @Test
    fun `test clear makes isLoggedIn false`() {
        prefs.token = "tok"
        prefs.serverUrl = "https://example.com"
        prefs.deviceId = 10
        assertTrue(prefs.isLoggedIn)

        prefs.clear()

        assertFalse(prefs.isLoggedIn)
    }

    @Test
    fun `test clear makes isPaired false`() {
        prefs.pairedHostId = 5
        assertTrue(prefs.isPaired)

        prefs.clear()

        assertFalse(prefs.isPaired)
    }
}
