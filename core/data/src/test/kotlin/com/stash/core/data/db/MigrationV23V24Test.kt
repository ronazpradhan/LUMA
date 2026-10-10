package com.stash.core.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
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
 * v0.9.25: Verifies migration v23 -> v24 adds three nullable
 * loudness-metadata columns (`loudness_lufs`, `true_peak_dbfs`,
 * `loudness_measured_at`) to the `tracks` table without disturbing
 * existing rows. Pure ALTER TABLE additive — no data backfill.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MigrationV23V24Test {

    private val DB_NAME = "migration-v23v24-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StashDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `migration v23 to v24 adds loudness columns and existing rows have NULL values`() {
        // 1. Open at v23 and write a track row with every NOT NULL column populated.
        helper.createDatabase(DB_NAME, 23).use { db ->
            db.insertTrackV23(id = 1L)
        }

        // 2. Run migration to v24 with validation. This fails fast if the
        // migration DDL doesn't match the v24 schema JSON Room generated
        // for the updated entity.
        val migrated = helper.runMigrationsAndValidate(
            DB_NAME, 24, true, StashDatabase.MIGRATION_23_24,
        )

        // 3. The three new columns exist and the existing row has NULL for each.
        migrated.query(
            "SELECT loudness_lufs, true_peak_dbfs, loudness_measured_at " +
                "FROM tracks WHERE id = 1"
        ).use { c ->
            assertEquals(1, c.count)
            assertTrue(c.moveToFirst())
            assertTrue("loudness_lufs should be NULL", c.isNull(0))
            assertTrue("true_peak_dbfs should be NULL", c.isNull(1))
            assertTrue("loudness_measured_at should be NULL", c.isNull(2))
        }

        // 4. New rows can store non-NULL values in the new columns.
        val cv = ContentValues().apply {
            put("loudness_lufs", -14.2f)
            put("true_peak_dbfs", -1.0f)
            put("loudness_measured_at", 1_715_000_000_000L)
        }
        migrated.update("tracks", SQLiteDatabase.CONFLICT_REPLACE, cv, "id = 1", null)

        migrated.query(
            "SELECT loudness_lufs, true_peak_dbfs, loudness_measured_at " +
                "FROM tracks WHERE id = 1"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(-14.2f, c.getFloat(0), 0.001f)
            assertEquals(-1.0f, c.getFloat(1), 0.001f)
            assertEquals(1_715_000_000_000L, c.getLong(2))
        }
    }

    /**
     * Insert a minimally-populated v23 tracks row. Every NOT NULL column
     * from `core/data/schemas/.../23.json` is populated. Nullable columns
     * are left to default to NULL. The exact non-key values don't matter
     * for this test — we only care that the row survives the migration.
     */
    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertTrackV23(id: Long) {
        val cv = ContentValues().apply {
            put("id", id)
            put("title", "Test Track $id")
            put("artist", "Test Artist")
            put("album", "")
            put("duration_ms", 0L)
            put("file_format", "opus")
            put("quality_kbps", 0)
            put("file_size_bytes", 0L)
            put("source", "SPOTIFY")
            put("date_added", 0L)
            put("play_count", 0)
            put("is_downloaded", 0)
            put("canonical_title", "test track $id")
            put("canonical_artist", "test artist")
            put("match_confidence", 0f)
            put("match_dismissed", 0)
            put("match_flagged", 0)
            put("lastfm_user_loved", 0)
        }
        insert("tracks", SQLiteDatabase.CONFLICT_REPLACE, cv)
    }
}
