package com.github.libretube.player.parser

import misc.Common.FormatId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import video_streaming.FormatInitializationMetadataOuterClass.FormatInitializationMetadata
import video_streaming.MediaHeaderOuterClass.MediaHeader

class SabrSegmentStoreTest {
    private val store = SabrSegmentStore(VIDEO_ID)

    private fun initializeFormat(durationMs: Long) = store.processFormatInitializationMetadata(
        FormatInitializationMetadata.newBuilder()
            .setVideoId(VIDEO_ID)
            .setFormatId(FormatId.newBuilder().setItag(ITAG))
            .setEndTimeMs(durationMs)
            .setEndSegmentNumber(100)
            .build().toByteArray()
    )

    /** Receives a segment whose header announces [contentLength] bytes, but [data] holds the bytes. */
    private fun receiveSegment(sequenceNumber: Long, isInitSeg: Boolean, contentLength: Long, data: ByteArray) {
        val headerId = sequenceNumber.toInt()
        store.processMediaHeader(
            MediaHeader.newBuilder()
                .setHeaderId(headerId)
                .setVideoId(VIDEO_ID)
                .setItag(ITAG)
                .setFormatId(FormatId.newBuilder().setItag(ITAG))
                .setIsInitSeg(isInitSeg)
                .setSequenceNumber(sequenceNumber)
                .setDurationMs(5000)
                .setContentLength(contentLength)
                .build().toByteArray()
        )
        store.processMedia(byteArrayOf(headerId.toByte()) + data)
        store.processMediaEnd(byteArrayOf(headerId.toByte()))
    }

    @Test
    fun formatIsForgottenWhenItsInitSegmentHasTheWrongLength() {
        initializeFormat(durationMs = 0)

        assertThrows(Exception::class.java) {
            receiveSegment(0, isInitSeg = true, contentLength = 10, data = ByteArray(5))
        }

        assertNull(store.initializedFormats[ITAG])
    }

    @Test
    fun formatIsInitializedAgainAfterItsInitSegmentHadTheWrongLength() {
        initializeFormat(durationMs = 0)
        assertThrows(Exception::class.java) {
            receiveSegment(0, isInitSeg = true, contentLength = 10, data = ByteArray(5))
        }

        initializeFormat(durationMs = 600_000)
        receiveSegment(0, isInitSeg = true, contentLength = 10, data = ByteArray(10))

        val format = store.initializedFormats[ITAG]!!
        assertEquals(600_000L, format.duration)
        assertNotNull(format.initSegment)
    }

    @Test
    fun formatIsKeptWhenALaterSegmentHasTheWrongLength() {
        initializeFormat(durationMs = 600_000)
        receiveSegment(0, isInitSeg = true, contentLength = 10, data = ByteArray(10))

        assertThrows(Exception::class.java) {
            receiveSegment(1, isInitSeg = false, contentLength = 10, data = ByteArray(5))
        }

        val format = store.initializedFormats[ITAG]!!
        assertNotNull(format.initSegment)
        assertNull(format.getSegment(1))
    }

    companion object {
        private const val VIDEO_ID = "eRsGyueVLvQ"
        private const val ITAG = 299
    }
}
