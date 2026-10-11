package com.github.libretube.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekIncrementTest {
    @Test
    fun parsesStoredSecondsToMillis() {
        assertEquals(10_000L, parseSeekIncrementMillis(DEFAULT_SEEK_INCREMENT))
        assertEquals(5_000L, parseSeekIncrementMillis("5.0"))
        assertEquals(60_000L, parseSeekIncrementMillis("60.0"))
        assertEquals(30_000L, parseSeekIncrementMillis("30"))
    }

    @Test
    fun roundsFractionalSecondsToWholeSeconds() {
        assertEquals(8_000L, parseSeekIncrementMillis("7.5"))
        assertEquals(7_000L, parseSeekIncrementMillis("7.4"))
    }
}
