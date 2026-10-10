package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.TrackEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the v0.9.25 loudness-normalization DAO methods:
 *   - [TrackDao.tracksNeedingLoudness]
 *   - [TrackDao.updateLoudness]
 *   - [TrackDao.markLoudnessFailed]
 *
 * **Physical row states** (after SQLite round-trip):
 *   - `loudnessLufs == null` AND `loudnessMeasuredAt == null` -> not measured (eligible)
 *   - `loudnessLufs == null` AND `loudnessMeasuredAt != null` -> failed sentinel (skip)
 *   - `loudnessLufs` real number AND `loudnessMeasuredAt != null` -> measured (skip)
 *
 * The intent-level `Float.NaN` sentinel for failure passed to
 * [TrackDao.markLoudnessFailed] gets normalised to NULL by Android's
 * `bindDouble` + SQLite REAL storage round-trip — see the KDoc on that
 * method. The physical discriminator the [TrackDao.tracksNeedingLoudness]
 * query relies on is therefore `loudness_measured_at IS NULL`, which
 * correctly excludes both successfully-measured rows and failed rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class TrackDaoLoudnessTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: TrackDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = db.trackDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun tracksNeedingLoudness_returnsRowsWithNullMeasuredAt() = runTest {
        dao.insert(track(id = 1L, filePath = "/a.opus"))
        dao.insert(track(id = 2L, filePath = "/b.opus"))
        dao.insert(track(id = 3L, filePath = "/c.opus"))

        val result = dao.tracksNeedingLoudness(limit = 10)

        assertEquals(setOf(1L, 2L, 3L), result.map { it.id }.toSet())
    }

    @Test fun tracksNeedingLoudness_excludesMeasuredRows() = runTest {
        dao.insert(track(
            id = 1L,
            filePath = "/a.opus",
            loudnessLufs = -14f,
            truePeakDbfs = -0.5f,
            loudnessMeasuredAt = 1000L,
        ))
        dao.insert(track(
            id = 2L,
            filePath = "/b.opus",
            loudnessLufs = -10f,
            truePeakDbfs = -0.2f,
            loudnessMeasuredAt = 1000L,
        ))
        dao.insert(track(id = 3L, filePath = "/c.opus"))

        val result = dao.tracksNeedingLoudness(limit = 10)

        assertEquals(listOf(3L), result.map { it.id })
    }

    @Test fun tracksNeedingLoudness_excludesFailedSentinelRows() = runTest {
        dao.insert(track(
            id = 1L,
            filePath = "/a.opus",
            loudnessLufs = Float.NaN,
            loudnessMeasuredAt = 1000L,
        ))
        dao.insert(track(id = 2L, filePath = "/b.opus"))

        val result = dao.tracksNeedingLoudness(limit = 10)

        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test fun updateLoudness_writesAllThreeColumns() = runTest {
        dao.insert(track(id = 1L, filePath = "/a.opus"))

        dao.updateLoudness(id = 1L, lufs = -14.2f, peak = -0.3f, now = 1234L)

        val row = dao.getById(1L)!!
        assertEquals(-14.2f, row.loudnessLufs!!, 0.0001f)
        assertEquals(-0.3f, row.truePeakDbfs!!, 0.0001f)
        assertEquals(1234L, row.loudnessMeasuredAt)
    }

    /**
     * `markLoudnessFailed` writes `Float.NaN` to `loudness_lufs` as the
     * intent-level failure sentinel. Android/Room's `bindDouble`+SQLite
     * round-trip normalises NaN to NULL on storage, so the readback comes
     * back null — but the wall-clock `loudness_measured_at` IS preserved.
     *
     * That's the actual physical discriminator the query relies on:
     * `tracksNeedingLoudness` filters by `loudness_measured_at IS NULL`,
     * so timestamped-and-null-LUFS rows (failed) are skipped just like
     * timestamped-and-real-LUFS rows (success). The downstream gain
     * computer treats null LUFS as "no gain to apply", matching the
     * never-measured branch — semantically identical from the user's
     * perspective.
     */
    @Test fun markLoudnessFailed_writesNanLufsAndTimestamp() = runTest {
        dao.insert(track(id = 1L, filePath = "/a.opus"))

        dao.markLoudnessFailed(id = 1L, now = 5000L)

        val row = dao.getById(1L)!!
        // SQLite/Android round-trip: NaN-on-bind reads back as null. The
        // failure mark is carried by `loudness_measured_at`, not the LUFS.
        assertNull("loudnessLufs round-trips NaN as null", row.loudnessLufs)
        assertEquals(5000L, row.loudnessMeasuredAt)
        assertNull("truePeakDbfs should remain untouched", row.truePeakDbfs)
    }

    private fun track(
        id: Long,
        filePath: String?,
        loudnessLufs: Float? = null,
        truePeakDbfs: Float? = null,
        loudnessMeasuredAt: Long? = null,
    ) = TrackEntity(
        id = id,
        title = "Title $id",
        artist = "Artist $id",
        canonicalTitle = "title $id",
        canonicalArtist = "artist $id",
        filePath = filePath,
        isDownloaded = filePath != null,
        loudnessLufs = loudnessLufs,
        truePeakDbfs = truePeakDbfs,
        loudnessMeasuredAt = loudnessMeasuredAt,
    )
}
