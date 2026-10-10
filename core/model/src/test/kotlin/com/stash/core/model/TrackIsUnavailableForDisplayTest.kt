package com.stash.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the 3-condition predicate that gates the greyed-out row treatment
 * in library views (Task 18).
 *
 * A row is "unavailable for display" iff:
 *   - it is NOT downloaded locally, AND
 *   - it is NOT marked streamable, AND
 *   - the streamability check has already run (`isStreamableCheckedAt != null`).
 *
 * The fourth state — not-yet-checked (checkedAt == null) — renders
 * normally because the DAO predicate filters those rows out of library
 * views, and on the rare chance one leaks through we'd rather show it
 * normally than grey-with-no-action.
 */
class TrackIsUnavailableForDisplayTest {

    @Test
    fun `downloaded track is never unavailable`() {
        // Even if Kennyy says no, a downloaded file plays locally.
        val track = stubTrack(
            isDownloaded = true,
            isStreamable = false,
            isStreamableCheckedAt = 1_700_000_000_000L,
        )
        assertFalse(track.isUnavailableForDisplay)
    }

    @Test
    fun `downloaded and streamable is not unavailable`() {
        val track = stubTrack(
            isDownloaded = true,
            isStreamable = true,
            isStreamableCheckedAt = 1_700_000_000_000L,
        )
        assertFalse(track.isUnavailableForDisplay)
    }

    @Test
    fun `streamable-only track is not unavailable`() {
        // Task 11 routes the tap to playFromStream.
        val track = stubTrack(
            isDownloaded = false,
            isStreamable = true,
            isStreamableCheckedAt = 1_700_000_000_000L,
        )
        assertFalse(track.isUnavailableForDisplay)
    }

    @Test
    fun `checked-and-unresolvable track is unavailable`() {
        // The state Task 18 cares about: synced metadata, Kennyy checked,
        // Kennyy doesn't have it. Row should grey out, no tap action.
        val track = stubTrack(
            isDownloaded = false,
            isStreamable = false,
            isStreamableCheckedAt = 1_700_000_000_000L,
        )
        assertTrue(track.isUnavailableForDisplay)
    }

    @Test
    fun `not-yet-checked track is not unavailable`() {
        // The DAO filters these out of library queries, but defend the
        // predicate: if one leaks through, render normally (the worker
        // will resolve it soon).
        val track = stubTrack(
            isDownloaded = false,
            isStreamable = false,
            isStreamableCheckedAt = null,
        )
        assertFalse(track.isUnavailableForDisplay)
    }

    @Test
    fun `checked timestamp of zero still counts as checked`() {
        // Sentinel paranoia: 0L is a valid epoch-millis (1970-01-01) but
        // non-null, so the predicate must treat it as "checked". The
        // worker writes `System.currentTimeMillis()` so 0L never occurs
        // in practice, but the predicate must not silently swallow it.
        val track = stubTrack(
            isDownloaded = false,
            isStreamable = false,
            isStreamableCheckedAt = 0L,
        )
        assertTrue(track.isUnavailableForDisplay)
    }

    private fun stubTrack(
        isDownloaded: Boolean,
        isStreamable: Boolean,
        isStreamableCheckedAt: Long?,
    ): Track = Track(
        title = "Stub",
        artist = "Stub Artist",
        isDownloaded = isDownloaded,
        isStreamable = isStreamable,
        isStreamableCheckedAt = isStreamableCheckedAt,
    )
}
