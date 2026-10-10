package com.stash.feature.library

import com.stash.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pre-flight math for the batch "Upgrade to FLAC" confirm dialog (spec §3). */
class FlacUpgradeEstimateTest {

    private fun track(
        id: Long = 1L,
        durationMs: Long = 240_000L,
        isDownloaded: Boolean = true,
        fileFormat: String = "opus",
    ) = Track(
        id = id, title = "t$id", artist = "a",
        durationMs = durationMs, isDownloaded = isDownloaded, fileFormat = fileFormat,
    )

    @Test
    fun `estimate is duration at 1000kbps and formats to GB`() {
        // 3 tracks × 4 min = 720s → 720 × 125_000 B = 90 MB
        val tracks = List(3) { track(id = it.toLong()) }
        val bytes = estimateFlacBytes(tracks)
        assertEquals(90_000_000L, bytes)
        assertEquals("0.1 GB", formatGb(bytes))
    }

    @Test
    fun `eligible filters non-downloaded and already-lossless`() {
        val keep = track(id = 1L)
        val tracks = listOf(
            keep,
            track(id = 2L, fileFormat = "flac"),
            track(id = 3L, fileFormat = "wav"),
            track(id = 4L, isDownloaded = false),
        )
        assertEquals(listOf(keep), eligibleForFlacUpgrade(tracks))
    }
}
