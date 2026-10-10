package com.stash.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMetadataFieldsTest {

    @Test fun `albumArtist defaults to empty string`() {
        val track = stubTrack()
        assertEquals("", track.albumArtist)
    }

    @Test fun `albumArtist preserves explicit value`() {
        val track = stubTrack(albumArtist = "Drake")
        assertEquals("Drake", track.albumArtist)
    }

    @Test fun `metadataEmbeddedAt defaults to null`() {
        val track = stubTrack()
        assertNull(track.metadataEmbeddedAt)
    }

    @Test fun `metadataEmbeddedAt preserves explicit value`() {
        val track = stubTrack(metadataEmbeddedAt = 1716000000000L)
        assertEquals(1716000000000L, track.metadataEmbeddedAt)
    }

    private fun stubTrack(
        albumArtist: String = "",
        metadataEmbeddedAt: Long? = null,
    ) = Track(
        id = 0L,
        title = "",
        artist = "",
        albumArtist = albumArtist,
        metadataEmbeddedAt = metadataEmbeddedAt,
    )
}
