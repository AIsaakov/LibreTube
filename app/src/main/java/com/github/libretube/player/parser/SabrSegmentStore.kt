package com.github.libretube.player.parser

import android.util.Log
import misc.Common.FormatId
import video_streaming.BufferedRangeOuterClass.BufferedRange
import video_streaming.FormatInitializationMetadataOuterClass.FormatInitializationMetadata
import video_streaming.MediaHeaderOuterClass.MediaHeader
import video_streaming.UmpPartId.UMPPartId

/**
 * An initialized format within a video stream.
 */
internal data class InitializedFormat(
    /** Identifier of the format. */
    val id: FormatId,
    /** Segments that have been downloaded for this format. */
    val downloadedSegments: MutableMap<Long, Segment> = mutableMapOf(),
    /** Segments that have been downloaded for this format. */
    val bufferedSegments: MutableMap<Long, Segment> = mutableMapOf(),
    /** Sequence number of the last segment in the format. */
    val endSegmentNumber: Long,
    /** Initial segment containing metadata about the stream,
     *  such as the position of the other segments.
     **/
    var initSegment: Segment? = null,
    /** Duration of the format in milliseconds. */
    val duration: Long,
) {
    /** Returns a list of all downloaded segments for the format. */
    fun getSegment(sequenceNumber: Long): Segment? {
        val segment = downloadedSegments.remove(sequenceNumber)
            ?: initSegment?.takeIf { it.sequenceNumber == sequenceNumber }
            ?: return null
        // mark retrieved segment as buffered
        bufferedSegments[sequenceNumber] = segment.copy(data = mutableListOf())
        return segment
    }

    /** Returns a list of all downloaded segments for the format. */
    fun buildBufferedRanges(): List<BufferedRange> =
        bufferedSegments.entries.union(downloadedSegments.entries).sortedBy { it.key }
        .fold(mutableListOf<MutableList<Pair<Long, Segment>>>()) { acc, (id, segment) ->
            val previousId = acc.lastOrNull()?.lastOrNull()?.first
            if (previousId?.plus(1) != id) {
                //we found a discontinuity, create a new partition
                acc.add(mutableListOf())
            }
            acc.lastOrNull()!!.add(Pair(id, segment))
            acc
        }.map { partition ->
            val duration = partition.sumOf { it.second.duration }
            val (firstId, firstSegment) = partition.first()
            BufferedRange.newBuilder().setFormatId(id).setStartTimeMs(firstSegment.header.startMs)
                .setDurationMs(duration).setStartSegmentIndex(firstId.toInt())
                .setEndSegmentIndex(partition.last().first.toInt()).build()
        }

    /**
     * Whether the format has non-retrieved data.
     */
    fun hasSegment(segmentNumber: Long): Boolean =
        downloadedSegments.containsKey(segmentNumber) || initSegment?.sequenceNumber == segmentNumber
}

/**
 * Formats and media segments received from a SABR stream.
 *
 * Assembles segments from the media parts of UMP responses and stores them per format.
 */
internal class SabrSegmentStore(
    /** Unique identifier for the SABR stream resource. */
    private val videoId: String,
) {
    /**
     * Initialized formats.
     *
     * A format is initialized when the stream sends a [UMPPartId.FORMAT_INITIALIZATION_METADATA] part,
     * containing the metadata of the format.
     *
     * Each format is identified by its `itag`.
     */
    val initializedFormats = mutableMapOf<Int, InitializedFormat>()

    /**
     * Partial segments that are being processed.
     *
     * Segments are stored here temporarily until they are fully processed.
     */
    private val partialSegments = mutableMapOf<Int, Segment>()

    /** Handles a [UMPPartId.MEDIA_HEADER] part. */
    fun processMediaHeader(data: ByteArray) {
        val header = MediaHeader.parseFrom(data)
        val videoId = header.videoId
        val headerId = header.headerId
        val sequenceNumber = header.sequenceNumber
        val duration = if (header.hasDurationMs()) header.durationMs else {
            ((header.timeRange.durationTicks.toDouble() / header.timeRange.timescale.toDouble()) * 1000).toLong()
        }

        if (videoId != this.videoId) {
            Log.e(TAG, "processPart: Received unexpected media header for $videoId")
            throw Exception("Header mismatch")
        }

        val format = initializedFormats[header.formatId.itag]!!

        if (format.downloadedSegments.containsKey(sequenceNumber)) {
            Log.w(TAG, "processPart: Segment $sequenceNumber is already downloaded. Ignoring.")
            return
        }

        Log.v(TAG, "processPart: Enqueuing partial segment $headerId")
        partialSegments[headerId] = Segment(
            header = header,
            sequenceNumber = sequenceNumber,
            data = mutableListOf(),
            duration = duration
        )
    }

    /** Handles a [UMPPartId.MEDIA] part. */
    fun processMedia(data: ByteArray) {
        val parser = UmpParser(data)
        val headerId = parser.readVarint()?.toInt()!!

        // repeated segment are skipped, when their header is found and their not added
        // to the partial segment queue
        val segment = partialSegments[headerId] ?: return
        //TODO: decompress gzipped data (not send for the WEB client)
        segment.data.add(parser.data())
    }

    /** Handles a [UMPPartId.MEDIA_END] part. */
    fun processMediaEnd(data: ByteArray) {
        val parser = UmpParser(data)
        val headerId = parser.readVarint()?.toInt()!!
        val segment = partialSegments.remove(headerId) ?: return
        Log.v(TAG, "processPart: Dequeuing partial segment $headerId")

        val segmentLength = segment.length()
        if (segmentLength != segment.header.contentLength.toInt()) {
            Log.w(
                TAG,
                "processPart: Content length mismatch for segment $headerId: expected ${segment.header.contentLength}, got $segmentLength"
            )
            // A format without an init segment can't be played, and while it stays initialized,
            // requests tell the server it's already selected and keep its metadata from the failed
            // response. Forget it, so that the next request starts the format from scratch.
            if (initializedFormats[segment.header.itag]?.initSegment == null) {
                initializedFormats.remove(segment.header.itag)
            }
            throw Exception("Content length mismatch")
        }

        val format = initializedFormats[segment.header.itag]!!
        format.downloadedSegments[segment.sequenceNumber] = segment

        if (segment.header.isInitSeg) {
            format.initSegment = segment
        }
    }

    /** Handles a [UMPPartId.FORMAT_INITIALIZATION_METADATA] part. */
    fun processFormatInitializationMetadata(data: ByteArray) {
        val metadata = FormatInitializationMetadata.parseFrom(data)

        val duration = metadata.endTimeMs
        val endSegmentNumber = metadata.endSegmentNumber
        val formatId = metadata.formatId
        val itag = formatId.itag

        if (initializedFormats.containsKey(itag)) {
            Log.w(TAG, "processPart: Skipping already initialized format `$itag`")
            return
        }

        val format = InitializedFormat(
            id = formatId,
            endSegmentNumber = endSegmentNumber,
            duration = duration
        )
        initializedFormats[itag] = format
    }

    companion object {
        private const val TAG = "SabrStream"
    }
}
