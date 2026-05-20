package com.openai.codexmobile.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealBridgeDataProviderTest {
    @Test
    fun shouldSuppressStreamFailureOnlyForClientInitiatedShutdown() {
        assertTrue(shouldSuppressStreamFailure("Socket closed", closedByClient = true))
        assertTrue(shouldSuppressStreamFailure(null, closedByClient = true))
        assertFalse(shouldSuppressStreamFailure("Socket closed", closedByClient = false))
    }
}
