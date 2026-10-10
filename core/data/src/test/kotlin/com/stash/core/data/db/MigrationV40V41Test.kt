package com.stash.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies migration v40 -> v41: rows poisoned by the loudness parser bug
 * (every on-device measurement stored the ebur128 gating floor, exactly
 * -70.0 LUFS) are reset to "never measured" so the backfill worker
 * re-measures them, while legitimate measurements and deliberate failure
 * sentinels (NULL lufs + non-NULL stamp) survive untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MigrationV40V41Test {

    private val DB_NAME = "migration-v40v41-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StashDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private fun insertTrack(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: Long,
        lufs: Double?,
        peak: Double?,
        measuredAt: Long?,
    ) {
        db.execSQL(
            """
            INSERT INTO tracks (
                id, title, artist, album, duration_ms, file_format,
                quality_kbps, file_size_bytes, source, date_added, play_count,
                is_downloaded, canonical_title, canonical_artist,
                match_confidence, match_dismissed,
                loudness_lufs, true_peak_dbfs, loudness_measured_at
            ) VALUES (
                $id, 't$id', 'a', 'al', 1000, 'flac',
                1000, 1, 'YOUTUBE', 0, 0,
                1, 't$id', 'a',
                0, 0,
                ${lufs ?: "NULL"}, ${peak ?: "NULL"}, ${measuredAt ?: "NULL"}
            )
            """.trimIndent(),
        )
    }

    @Test
    fun `poisoned floor rows are unstamped, real and sentinel rows survive`() {
        helper.createDatabase(DB_NAME, 40).use { db ->
            // The bug's signature: the gating floor with a real-looking peak.
            insertTrack(db, id = 1, lufs = -70.0, peak = 0.3, measuredAt = 111)
            // A legitimate measurement (made off-device / post-fix).
            insertTrack(db, id = 2, lufs = -9.8, peak = 0.3, measuredAt = 222)
            // A deliberate failure sentinel: broken file, don't retry.
            insertTrack(db, id = 3, lufs = null, peak = null, measuredAt = 333)
            // Never measured at all.
            insertTrack(db, id = 4, lufs = null, peak = null, measuredAt = null)
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME, 41, true, StashDatabase.MIGRATION_40_41,
        )

        migrated.query(
            "SELECT id, loudness_lufs, true_peak_dbfs, loudness_measured_at FROM tracks ORDER BY id",
        ).use { c ->
            assertTrue(c.moveToNext())
            assertEquals(1, c.getLong(0))
            assertTrue("poisoned lufs must be NULLed", c.isNull(1))
            assertTrue("poisoned peak must be NULLed", c.isNull(2))
            assertTrue("poisoned stamp must be NULLed so backfill retries", c.isNull(3))

            assertTrue(c.moveToNext())
            assertEquals(2, c.getLong(0))
            assertEquals(-9.8, c.getDouble(1), 0.001)
            assertEquals(222, c.getLong(3))

            assertTrue(c.moveToNext())
            assertEquals(3, c.getLong(0))
            assertTrue(c.isNull(1))
            assertEquals("failure sentinel must survive", 333, c.getLong(3))

            assertTrue(c.moveToNext())
            assertEquals(4, c.getLong(0))
            assertTrue(c.isNull(3))
        }
    }
}
