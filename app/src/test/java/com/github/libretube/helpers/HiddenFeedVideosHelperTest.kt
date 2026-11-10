package com.github.libretube.helpers

import com.github.libretube.api.obj.StreamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HiddenFeedVideosHelperTest {
    private val first = StreamItem(url = "/watch?v=aaaaaaaaaaa")
    private val second = StreamItem(url = "/watch?v=bbbbbbbbbbb")
    private val short = StreamItem(url = "/shorts/ccccccccccc", isShort = true)
    private val feed = listOf(first, second, short)

    @Test
    fun keepsTheFeedWhenNothingIsHidden() {
        assertSame(feed, HiddenFeedVideosHelper.filterHidden(feed, emptySet()))
    }

    @Test
    fun dropsHiddenVideosAndKeepsTheOthersInOrder() {
        assertEquals(
            listOf(first, short),
            HiddenFeedVideosHelper.filterHidden(feed, setOf("bbbbbbbbbbb"))
        )
    }

    @Test
    fun matchesTheIdOfAShort() {
        assertEquals(
            listOf(first, second),
            HiddenFeedVideosHelper.filterHidden(feed, setOf("ccccccccccc"))
        )
    }

    @Test
    fun ignoresHiddenIdsThatAreNotInTheFeed() {
        assertEquals(feed, HiddenFeedVideosHelper.filterHidden(feed, setOf("zzzzzzzzzzz")))
    }
}
