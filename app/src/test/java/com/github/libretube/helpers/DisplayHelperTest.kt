package com.github.libretube.helpers

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayHelperTest {
    private fun locked(rotation: Int, orientation: Int) =
        DisplayHelper.getLockedOrientation(rotation, orientation)

    @Test
    fun deviceWithPortraitNaturalOrientation() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            locked(Surface.ROTATION_0, Configuration.ORIENTATION_PORTRAIT)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            locked(Surface.ROTATION_90, Configuration.ORIENTATION_LANDSCAPE)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            locked(Surface.ROTATION_180, Configuration.ORIENTATION_PORTRAIT)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            locked(Surface.ROTATION_270, Configuration.ORIENTATION_LANDSCAPE)
        )
    }

    @Test
    fun deviceWithLandscapeNaturalOrientation() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            locked(Surface.ROTATION_0, Configuration.ORIENTATION_LANDSCAPE)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            locked(Surface.ROTATION_90, Configuration.ORIENTATION_PORTRAIT)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            locked(Surface.ROTATION_180, Configuration.ORIENTATION_LANDSCAPE)
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            locked(Surface.ROTATION_270, Configuration.ORIENTATION_PORTRAIT)
        )
    }
}
