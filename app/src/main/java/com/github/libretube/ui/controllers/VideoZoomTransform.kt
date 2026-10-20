package com.github.libretube.ui.controllers

import kotlin.math.abs
import kotlin.math.max

/**
 * The scale and translation of a video frame that is centred in a viewport and scaled around its
 * own centre.
 *
 * A single pinch gesture can't cross [fillScale], the scale at which the video covers the whole
 * viewport: zooming from the fitted size stops there, and a new gesture is needed to zoom on to
 * [maxScale]. The same applies when zooming back out.
 *
 * Translations are offsets of the frame's centre from the viewport's centre, in viewport pixels.
 */
class VideoZoomTransform {
    var scale = 1f
        private set
    var translationX = 0f
        private set
    var translationY = 0f
        private set

    private var frameWidth = 0f
    private var frameHeight = 0f
    private var viewportWidth = 0f
    private var viewportHeight = 0f

    private var gestureStartScale = 1f
    private var gestureRange: ClosedFloatingPointRange<Float>? = null

    val isZoomed get() = scale > 1f + EPSILON

    val fillScale: Float
        get() {
            if (frameWidth <= 0f || frameHeight <= 0f) return 1f
            return max(1f, max(viewportWidth / frameWidth, viewportHeight / frameHeight))
        }

    val maxScale get() = max(MAX_SCALE, fillScale)

    val isAtFill get() = abs(scale - fillScale) <= EPSILON

    /**
     * Sets the sizes the zoom and pan limits depend on. When the sizes change while zoomed, as on
     * a screen rotation, the zoom is kept as [restore] describes.
     */
    fun setSizes(frameWidth: Float, frameHeight: Float, viewportWidth: Float, viewportHeight: Float) {
        val state = save()
        this.frameWidth = frameWidth
        this.frameHeight = frameHeight
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
        restore(state)
    }

    /**
     * The zoom in a form that doesn't depend on the sizes, so that it can be applied to a frame
     * and a viewport of other sizes.
     *
     * @param centreX the point of the video at the viewport's centre, as a fraction of the frame's
     * width from the frame's centre
     * @param centreY the same as [centreX], as a fraction of the frame's height
     */
    data class State(
        val scale: Float,
        val centreX: Float,
        val centreY: Float,
        val isAtFill: Boolean,
    )

    fun save() = State(
        scale = scale,
        centreX = if (frameWidth > 0f) -translationX / (scale * frameWidth) else 0f,
        centreY = if (frameHeight > 0f) -translationY / (scale * frameHeight) else 0f,
        isAtFill = isZoomed && isAtFill,
    )

    /**
     * Applies a zoom saved with [save]: the scale is kept and the saved point of the video is moved
     * to the viewport's centre, as far as the pan limits allow. A video zoomed to fill is zoomed to
     * fill the current viewport instead of keeping its scale.
     */
    fun restore(state: State) {
        scale = if (state.isAtFill) fillScale else state.scale.coerceIn(1f, maxScale)
        translationX = -state.centreX * scale * frameWidth
        translationY = -state.centreY * scale * frameHeight
        clampTranslation()
    }

    fun beginGesture() {
        gestureStartScale = scale
        gestureRange = null
    }

    /**
     * Multiplies the scale by [factor], keeping the point at ([focusX], [focusY]) in viewport
     * coordinates under the fingers.
     */
    fun scaleBy(factor: Float, focusX: Float, focusY: Float) {
        if (factor == 1f) return
        val range = gestureRange ?: gestureRangeFor(factor).also { gestureRange = it }

        val newScale = (scale * factor).coerceIn(range)
        val ratio = newScale / scale
        // the focus point relative to the viewport's centre, which is where the frame's centre
        // sits without any translation
        val focusOffsetX = focusX - viewportWidth / 2
        val focusOffsetY = focusY - viewportHeight / 2
        translationX = focusOffsetX - (focusOffsetX - translationX) * ratio
        translationY = focusOffsetY - (focusOffsetY - translationY) * ratio
        scale = newScale

        clampTranslation()
    }

    fun panBy(dx: Float, dy: Float) {
        translationX += dx
        translationY += dy
        clampTranslation()
    }

    fun reset() {
        scale = 1f
        translationX = 0f
        translationY = 0f
        gestureRange = null
    }

    private fun gestureRangeFor(firstFactor: Float): ClosedFloatingPointRange<Float> {
        val fill = fillScale
        val startsAtFill = abs(gestureStartScale - fill) <= EPSILON
        val beyondFill = if (startsAtFill) firstFactor > 1f else gestureStartScale > fill

        return if (beyondFill) fill..maxScale else 1f..fill
    }

    private fun clampTranslation() {
        // the frame's edges may not move inside the viewport; when the scaled frame is smaller
        // than the viewport on an axis, that axis stays centred
        val maxX = max(0f, (frameWidth * scale - viewportWidth) / 2)
        val maxY = max(0f, (frameHeight * scale - viewportHeight) / 2)
        translationX = translationX.coerceIn(-maxX, maxX)
        translationY = translationY.coerceIn(-maxY, maxY)
    }

    companion object {
        const val MAX_SCALE = 10f
        private const val EPSILON = 0.001f
    }
}
