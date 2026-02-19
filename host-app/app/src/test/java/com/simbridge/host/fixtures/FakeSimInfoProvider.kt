package com.simbridge.host.fixtures

import com.simbridge.host.data.SimInfo

/**
 * Recording fake for SimInfoProvider. Returns canned SIM data for tests.
 */
class FakeSimInfoProvider {

    var activeSims: List<SimInfo> = emptyList()
    var shouldThrowSecurityException = false

    fun getActiveSimCards(): List<SimInfo> {
        if (shouldThrowSecurityException) throw SecurityException("Permission denied")
        return activeSims
    }
}
