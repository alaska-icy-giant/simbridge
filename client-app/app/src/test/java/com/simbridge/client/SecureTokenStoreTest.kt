package com.simbridge.client

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.simbridge.client.data.SecureTokenStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecureTokenStoreTest {

    private lateinit var store: SecureTokenStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = SecureTokenStore(context)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun `getToken returns null when no token saved`() {
        assertNull(store.getToken())
    }

    @Test
    fun `saveToken and getToken round-trip`() {
        val token = "jwt.token.value"
        store.saveToken(token)
        assertEquals(token, store.getToken())
    }

    @Test
    fun `clear removes saved token`() {
        store.saveToken("some-token")
        store.clear()
        assertNull(store.getToken())
    }

    @Test
    fun `saveToken overwrites previous value`() {
        store.saveToken("first-token")
        store.saveToken("second-token")
        assertEquals("second-token", store.getToken())
    }
}
