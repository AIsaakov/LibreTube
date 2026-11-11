package com.github.libretube.helpers

import com.github.libretube.api.obj.StreamItem
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.extensions.toID

/**
 * Videos the user hid from the subscription feed.
 *
 * Hiding a video leaves the watch history untouched, unlike marking it as watched.
 * The IDs are kept in the preferences rather than the database, so the fork needs no
 * database migration that could clash with upstream's.
 */
object HiddenFeedVideosHelper {
    private val hiddenIds: Set<String>
        get() = PreferenceHelper.getStringSet(PreferenceKeys.HIDDEN_FEED_VIDEOS, emptySet())

    fun hide(videoId: String) {
        PreferenceHelper.putStringSet(PreferenceKeys.HIDDEN_FEED_VIDEOS, hiddenIds + videoId)
    }

    fun unhide(videoId: String) {
        PreferenceHelper.putStringSet(PreferenceKeys.HIDDEN_FEED_VIDEOS, hiddenIds - videoId)
    }

    fun filterHidden(streams: List<StreamItem>): List<StreamItem> =
        filterHidden(streams, hiddenIds)

    fun filterHidden(streams: List<StreamItem>, hiddenIds: Set<String>): List<StreamItem> {
        if (hiddenIds.isEmpty()) return streams
        return streams.filterNot { it.url.orEmpty().toID() in hiddenIds }
    }
}
