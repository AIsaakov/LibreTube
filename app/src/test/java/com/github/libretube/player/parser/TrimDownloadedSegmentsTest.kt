package com.github.libretube.player.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import video_streaming.MediaHeaderOuterClass.MediaHeader

class TrimDownloadedSegmentsTest {
    private fun segment(sequenceNumber: Long, bytes: Int) = Segment(
        header = MediaHeader.getDefaultInstance(),
        sequenceNumber = sequenceNumber,
        data = mutableListOf(ByteArray(bytes)),
        duration = 0,
    )

    private fun segments(vararg sizes: Int): MutableMap<Long, Segment> =
        sizes.withIndex().associateTo(LinkedHashMap()) { (i, size) ->
            i.toLong() to segment(i.toLong(), size)
        }

    @Test
    fun keepsSegmentsWithinLimit() {
        val downloaded = segments(30, 30, 40)
        trimDownloadedSegments(downloaded, maxBytes = 100)
        assertEquals(listOf(0L, 1L, 2L), downloaded.keys.toList())
    }

    @Test
    fun dropsOldestSegmentsUntilWithinLimit() {
        val downloaded = segments(40, 40, 40, 40)
        trimDownloadedSegments(downloaded, maxBytes = 100)
        assertEquals(listOf(2L, 3L), downloaded.keys.toList())
    }

    @Test
    fun keepsLastSegmentEvenIfOverLimit() {
        val downloaded = segments(50, 150)
        trimDownloadedSegments(downloaded, maxBytes = 100)
        assertEquals(listOf(1L), downloaded.keys.toList())
    }

    @Test
    fun dropsInInsertionOrderNotBySequenceNumber() {
        val downloaded: MutableMap<Long, Segment> = linkedMapOf(
            5L to segment(5, 60),
            2L to segment(2, 60),
        )
        trimDownloadedSegments(downloaded, maxBytes = 100)
        assertEquals(listOf(2L), downloaded.keys.toList())
    }

    @Test
    fun leavesEmptyMapUnchanged() {
        val downloaded = mutableMapOf<Long, Segment>()
        trimDownloadedSegments(downloaded, maxBytes = 100)
        assertEquals(0, downloaded.size)
    }
}
