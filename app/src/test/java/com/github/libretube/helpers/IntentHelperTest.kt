package com.github.libretube.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class IntentHelperTest {
    @Test
    fun youTubeWatchUrlIncludesThePosition() {
        assertEquals(
            "https://www.youtube.com/watch?v=eRsGyueVLvQ&t=125",
            IntentHelper.getYouTubeWatchUrl("eRsGyueVLvQ", 125)
        )
    }

    @Test
    fun youTubeWatchUrlOmitsAMissingPosition() {
        assertEquals(
            "https://www.youtube.com/watch?v=eRsGyueVLvQ",
            IntentHelper.getYouTubeWatchUrl("eRsGyueVLvQ", null)
        )
    }
}
