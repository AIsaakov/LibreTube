package com.github.libretube.player.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlExpiryTest {
    private val base = "https://rr1---sn-example.googlevideo.com/videoplayback"

    @Test
    fun returnsSecondsUntilExpiry() {
        assertEquals(21600L, secondsUntilUrlExpiry("$base?id=abc&expire=1700021600&ei=x", 1700000000))
    }

    @Test
    fun returnsNegativeSecondsForExpiredUrl() {
        assertEquals(-30L, secondsUntilUrlExpiry("$base?expire=1700000000", 1700000030))
    }

    @Test
    fun returnsNullWithoutExpireParameter() {
        assertNull(secondsUntilUrlExpiry("$base?id=abc&ei=x", 1700000000))
    }

    @Test
    fun returnsNullForNonNumericExpireParameter() {
        assertNull(secondsUntilUrlExpiry("$base?expire=soon", 1700000000))
    }

    @Test
    fun returnsNullForInvalidUrl() {
        assertNull(secondsUntilUrlExpiry("not a url", 1700000000))
    }
}
