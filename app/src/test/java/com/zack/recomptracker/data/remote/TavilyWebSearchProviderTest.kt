package com.zack.recomptracker.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class TavilyWebSearchProviderTest {

    @Test
    fun `returns null when no key is configured`() = runTest {
        val provider = TavilyWebSearchProvider(keyProvider = { "" })
        assertNull(provider.search("big mac calories"))
    }

    @Test
    fun `returns null for a blank query`() = runTest {
        val provider = TavilyWebSearchProvider(keyProvider = { "tvly-key" })
        assertNull(provider.search("   "))
    }
}
