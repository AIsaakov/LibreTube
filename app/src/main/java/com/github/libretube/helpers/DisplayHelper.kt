package com.github.libretube.helpers

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.Surface
import androidx.core.content.ContextCompat

object DisplayHelper {
    /**
     * Detect whether the device supports HDR as the ExoPlayer doesn't handle it properly
     * Returns false below SDK 24
     */
    fun supportsHdr(context: Context) = ContextCompat.getDisplayOrDefault(context).isHdr

    /**
     * The fixed screen orientation that keeps the display as it is now, e.g. reverse landscape
     * rather than landscape when the device is turned the other way.
     *
     * @param rotation the display's rotation, one of the [Surface] `ROTATION_*` values
     * @param orientation the current orientation, one of the [Configuration] `ORIENTATION_*` values
     */
    fun getLockedOrientation(rotation: Int, orientation: Int): Int {
        // the rotation is relative to the device's natural orientation, which is landscape on
        // most tablets, so the current orientation decides between landscape and portrait
        val isReversed = rotation == Surface.ROTATION_180 || rotation == Surface.ROTATION_270

        return if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (isReversed) ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            if (isReversed) ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    fun getLockedOrientation(context: Context) = getLockedOrientation(
        ContextCompat.getDisplayOrDefault(context).rotation,
        context.resources.configuration.orientation
    )
}
