package com.github.libretube.ui.interfaces

interface PlayerGestureOptions {

    fun onSingleTap(areControlsLocked: Boolean)

    fun onDoubleTapCenterScreen()

    fun onDoubleTapLeftScreen()

    fun onDoubleTapRightScreen()

    fun onSwipeLeftScreen(distanceY: Float, positionY: Float)

    fun onSwipeRightScreen(distanceY: Float, positionY: Float)

    fun onSwipeCenterScreen(distanceY: Float, positionY: Float)

    fun onSwipeEnd()

    fun onZoom()

    fun onMinimize()

    fun onScaleBegin()

    /**
     * Called for each step of a pinch gesture, with the focus point between the fingers in view
     * coordinates.
     */
    fun onScale(factor: Float, focusX: Float, focusY: Float)

    fun onScaleEnd()

    /**
     * Moves the zoomed video by the given distance in pixels.
     *
     * @return whether the video is zoomed and was moved, in which case the movement must not be
     * used for any other gesture
     */
    fun onPan(dx: Float, dy: Float): Boolean

    fun onFullscreenChange(isFullscreen: Boolean)

    fun onLongPress()

    fun onLongPressEnd()

    /**
     *  Returns a pair of the width and height of the view this listener is used for
     *  These measures change when the screen orientation changes or fullscreen is entered, thus
     *  needs to be refreshed manually all the time when needed.
     */
    fun getViewMeasures(): Pair<Int, Int>
}
