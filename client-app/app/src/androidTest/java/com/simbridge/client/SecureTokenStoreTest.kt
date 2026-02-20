package com.simbridge.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simbridge.client.data.SecureTokenStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureTokenStoreTest {

    private lateinit var store: SecureTokenStore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        store = SecureTokenStore(context)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun getToken_returnsNull_whenNoTokenSaved() {
        assertNull(store.getToken())
    }

    @Test
    fun saveToken_and_getToken_roundTrip() {
        val token = "jwt.token.value"
        store.saveToken(token)
        assertEquals(token, store.getToken())
    }

    @Test
    fun clear_removesSavedToken() {
        store.saveToken("some-token")
        store.clear()
        assertNull(store.getToken())
    }

    @Test
    fun saveToken_overwritesPreviousValue() {
        store.saveToken("first-token")
        store.saveToken("second-token")
        assertEquals("second-token", store.getToken())
    }
}
