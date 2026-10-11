package com.github.libretube.ui.controllers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoZoomTransformTest {
    // a 16:9 video fitted on a 20:9 screen: 1800x1012.5 inside 2250x1012.5
    private fun pillarboxed() = VideoZoomTransform().apply {
        setSizes(1800f, 1012.5f, 2250f, 1012.5f)
    }

    private fun VideoZoomTransform.pinch(vararg factors: Float, focusX: Float = 1125f, focusY: Float = 506.25f) {
        beginGesture()
        factors.forEach { scaleBy(it, focusX, focusY) }
    }

    @Test
    fun fillScaleCoversTheViewport() {
        assertEquals(1.25f, pillarboxed().fillScale, DELTA)

        // a 16:9 video fitted on a 4:3 screen: 1440x810 inside 1440x1080
        val letterboxed = VideoZoomTransform().apply { setSizes(1440f, 810f, 1440f, 1080f) }
        assertEquals(1080f / 810f, letterboxed.fillScale, DELTA)
    }

    @Test
    fun fillScaleIsOneWhenTheFrameAlreadyCoversTheViewport() {
        val zoomMode = VideoZoomTransform().apply { setSizes(2250f, 1265.6f, 2250f, 1012.5f) }
        assertEquals(1f, zoomMode.fillScale, DELTA)
    }

    @Test
    fun firstPinchStopsAtFillScale() {
        val transform = pillarboxed()
        transform.pinch(1.2f, 1.2f, 1.2f, 1.2f)
        assertEquals(transform.fillScale, transform.scale, DELTA)
    }

    @Test
    fun isAtFillOnlyAtTheFillPoint() {
        val transform = pillarboxed()
        assertFalse(transform.isAtFill)

        transform.pinch(1.1f)
        assertFalse(transform.isAtFill)

        transform.pinch(2f)
        assertTrue(transform.isAtFill)

        transform.pinch(2f)
        assertFalse(transform.isAtFill)
    }

    @Test
    fun secondPinchZoomsOnToMaxScale() {
        val transform = pillarboxed()
        transform.pinch(2f)
        transform.pinch(2f)
        assertEquals(2.5f, transform.scale, DELTA)

        transform.pinch(10f)
        assertEquals(VideoZoomTransform.MAX_SCALE, transform.scale, DELTA)
    }

    @Test
    fun pinchingOutFromBeyondFillStopsAtFillScale() {
        val transform = pillarboxed()
        transform.pinch(2f)
        transform.pinch(4f)

        transform.pinch(0.1f)
        assertEquals(transform.fillScale, transform.scale, DELTA)

        transform.pinch(0.1f)
        assertEquals(1f, transform.scale, DELTA)
        assertFalse(transform.isZoomed)
    }

    @Test
    fun pinchingOutBelowFillReturnsToOne() {
        val transform = pillarboxed()
        transform.pinch(1.1f)
        assertTrue(transform.isZoomed)

        transform.pinch(0.5f)
        assertEquals(1f, transform.scale, DELTA)
    }

    @Test
    fun focusPointStaysUnderTheFingers() {
        val transform = pillarboxed()
        transform.pinch(2f)
        transform.beginGesture()

        // a point 300px right of and 100px above the viewport's centre
        val focusX = 1125f + 300f
        val focusY = 506.25f - 100f
        val contentXBefore = (300f - transform.translationX) / transform.scale
        val contentYBefore = (-100f - transform.translationY) / transform.scale

        transform.scaleBy(1.5f, focusX, focusY)

        val contentXAfter = (300f - transform.translationX) / transform.scale
        val contentYAfter = (-100f - transform.translationY) / transform.scale
        assertEquals(contentXBefore, contentXAfter, DELTA)
        assertEquals(contentYBefore, contentYAfter, DELTA)
    }

    @Test
    fun panStopsAtTheVideoEdges() {
        val transform = pillarboxed()
        transform.pinch(2f)
        transform.pinch(2f)
        // 2.5x: the video is 4500x2531.25 in a 2250x1012.5 viewport
        transform.panBy(10_000f, 10_000f)
        assertEquals(1125f, transform.translationX, DELTA)
        assertEquals(759.375f, transform.translationY, DELTA)

        transform.panBy(-20_000f, -20_000f)
        assertEquals(-1125f, transform.translationX, DELTA)
        assertEquals(-759.375f, transform.translationY, DELTA)
    }

    @Test
    fun axisNarrowerThanTheViewportStaysCentred() {
        val transform = pillarboxed()
        // 1.1x: 1980px wide in a 2250px viewport, but taller than the viewport
        transform.pinch(1.1f)
        transform.panBy(500f, 500f)
        assertEquals(0f, transform.translationX, DELTA)
        assertEquals(50.625f, transform.translationY, DELTA)
    }

    @Test
    fun resetReturnsToTheFittedVideo() {
        val transform = pillarboxed()
        transform.pinch(2f)
        transform.pinch(2f)
        transform.panBy(300f, 300f)

        transform.reset()
        assertEquals(1f, transform.scale, DELTA)
        assertEquals(0f, transform.translationX, DELTA)
        assertEquals(0f, transform.translationY, DELTA)
    }

    private companion object {
        const val DELTA = 0.01f
    }
}
