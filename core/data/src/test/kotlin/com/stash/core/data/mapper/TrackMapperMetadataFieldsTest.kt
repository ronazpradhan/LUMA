package com.stash.core.data.mapper

import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v0.9.35: pins the contract that the mapper threads both new metadata
 * fields end-to-end.
 *
 * - `albumArtist` has lived on [TrackEntity] since v0.9.26 but was never
 *   surfaced through the domain `Track` layer — Phase 2 of the embedding
 *   work closes that gap so the tagging pipeline can read it from the
 *   domain object.
 * - `metadataEmbeddedAt` is the v0.9.35 idempotency signal for the tag +
 *   art backfill worker; the mapper must round-trip both NULL (never
 *   tagged) and a non-null timestamp (successful tagging pass).
 *
 * A future edit that drops either field from the mapper fails loudly
 * here instead of silently shipping a backfill worker that re-tags every
 * file on every launch (non-null stamp lost) or never advances past the
 * first row (mapper read drops the success stamp).
 */
class TrackMapperMetadataFieldsTest {

    @Test
    fun `entity to domain propagates albumArtist + metadataEmbeddedAt`() {
        val entity = TrackEntity(
            title = "Title",
            artist = "Artist",
            albumArtist = "Drake",
            metadataEmbeddedAt = 1716000000000L,
        )

        val track = entity.toDomain()

        assertEquals("Drake", track.albumArtist)
        assertEquals(1716000000000L, track.metadataEmbeddedAt)
    }

    @Test
    fun `domain to entity propagates albumArtist + metadataEmbeddedAt`() {
        val track = Track(
            id = 1,
            title = "Title",
            artist = "Artist",
            albumArtist = "Drake",
            metadataEmbeddedAt = 1716000000000L,
        )

        val entity = track.toEntity()

        assertEquals("Drake", entity.albumArtist)
        assertEquals(1716000000000L, entity.metadataEmbeddedAt)
    }

    @Test
    fun `metadataEmbeddedAt null round-trips as null`() {
        val entity = TrackEntity(
            title = "Legacy Row",
            artist = "Artist",
            metadataEmbeddedAt = null,
        )

        assertNull(entity.toDomain().metadataEmbeddedAt)
        assertNull(entity.toDomain().toEntity().metadataEmbeddedAt)
    }

    @Test
    fun `metadataEmbeddedAt zero sentinel round-trips as zero`() {
        // 0L is the "backfill tried and gave up irrecoverably" sentinel
        // (file missing on disk, ffmpeg error, SAF row we can't operate
        // on in place). It MUST be distinguishable from NULL so the
        // worker's `WHERE metadata_embedded_at IS NULL` predicate filters
        // it out — re-attempting would just loop.
        val entity = TrackEntity(
            title = "Failed Row",
            artist = "Artist",
            metadataEmbeddedAt = 0L,
        )

        assertEquals(0L, entity.toDomain().metadataEmbeddedAt)
        assertEquals(0L, entity.toDomain().toEntity().metadataEmbeddedAt)
    }
}
