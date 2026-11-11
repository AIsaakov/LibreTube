package com.github.libretube.ui.controllers

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Applies free pinch zoom and panning to the player's content frame.
 *
 * The whole frame is transformed because a SurfaceView's video is clipped to the bounds of its
 * parent, so scaling only the video surface would keep the video inside the area it had before
 * zooming. The frame also holds views that must keep their size and position, such as the
 * subtitles, and those get the inverse transform.
 *
 * @param viewport the view the video is shown in, whose bounds the zoomed video may not leave
 * @param frame the content frame, centred in [viewport]
 * @param fixedViews children of [frame] that stay as they are while the video is zoomed
 */
class PinchZoomController(
    private val viewport: View,
    private val frame: View,
    private val fixedViews: () -> List<View>,
) {
    private val transform = VideoZoomTransform()
    private var resetAnimator: ValueAnimator? = null

    /**
     * The zoom saved by [rememberAndReset], until [restoreRemembered] or [reset].
     */
    private var remembered: VideoZoomTransform.State? = null

    /**
     * The zoom [restoreRemembered] applies once the frame has been laid out at its new size.
     */
    private var pendingRestore: VideoZoomTransform.State? = null

    init {
        // keeps the zoom when the frame or the viewport is resized, as on a screen rotation. The
        // viewport gets its new size before it lays out the frame, so both sizes are current here.
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val restore = pendingRestore
            if (restore == null && !transform.isZoomed) return@addOnLayoutChangeListener
            updateSizes()
            restore?.let { transform.restore(it) }
            pendingRestore = null
            apply()
        }
    }

    val isZoomed get() = transform.isZoomed

    val scale get() = transform.scale

    /**
     * Whether the video exactly covers the viewport, the point where a pinch gesture stops.
     */
    val isAtFill get() = transform.isAtFill

    fun onScaleBegin() {
        resetAnimator?.cancel()
        updateSizes()
        transform.beginGesture()
    }

    fun onScale(factor: Float, focusX: Float, focusY: Float) {
        transform.scaleBy(factor, focusX, focusY)
        apply()
    }

    fun onPan(dx: Float, dy: Float) {
        updateSizes()
        transform.panBy(dx, dy)
        apply()
    }

    /**
     * Resets the zoom, but keeps it for [restoreRemembered].
     */
    fun rememberAndReset(animate: Boolean) {
        val state = transform.save().takeIf { transform.isZoomed }
        reset(animate)
        remembered = state
    }

    /**
     * Applies the zoom saved by [rememberAndReset] once the frame has been laid out at the size it
     * changes to, since the zoom is relative to the frame's size.
     */
    fun restoreRemembered() {
        pendingRestore = remembered ?: return
        remembered = null
        resetAnimator?.cancel()
        frame.requestLayout()
    }

    fun reset(animate: Boolean) {
        resetAnimator?.cancel()
        remembered = null
        pendingRestore = null
        val startScale = frame.scaleX
        val startX = frame.translationX
        val startY = frame.translationY
        transform.reset()
        val isTransformed = startScale != 1f || startX != 0f || startY != 0f
        if (!animate || !isTransformed) {
            apply()
            return
        }

        resetAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            interpolator = AccelerateDecelerateInterpolator()
            duration = RESET_ANIMATION_DURATION
            addUpdateListener {
                val remaining = it.animatedValue as Float
                setTransform(
                    1f + (startScale - 1f) * remaining,
                    startX * remaining,
                    startY * remaining
                )
            }
            start()
        }
    }

    private fun updateSizes() {
        transform.setSizes(
            frame.width.toFloat(),
            frame.height.toFloat(),
            viewport.width.toFloat(),
            viewport.height.toFloat()
        )
    }

    private fun apply() {
        setTransform(transform.scale, transform.translationX, transform.translationY)
    }

    /**
     * Transforms the frame and gives [fixedViews] the inverse: each one is scaled by the inverse
     * around the frame's centre and moved back by the frame's translation.
     */
    private fun setTransform(scale: Float, translationX: Float, translationY: Float) {
        frame.pivotX = frame.width / 2f
        frame.pivotY = frame.height / 2f
        frame.scaleX = scale
        frame.scaleY = scale
        frame.translationX = translationX
        frame.translationY = translationY

        fixedViews().forEach { view ->
            view.pivotX = frame.width / 2f - view.left
            view.pivotY = frame.height / 2f - view.top
            view.scaleX = 1f / scale
            view.scaleY = 1f / scale
            view.translationX = -translationX / scale
            view.translationY = -translationY / scale
        }
    }

    companion object {
        private const val RESET_ANIMATION_DURATION = 200L
    }
}
