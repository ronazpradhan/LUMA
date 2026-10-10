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
 * v0.9.27: Verifies migration v25 -> v26 adds the two streamability
 * metadata columns (`is_streamable`, `is_streamable_checked_at`) to
 * the `tracks` table without disturbing existing rows. Pure ALTER
 * TABLE additive — no data backfill.
 *
 * `is_streamable` defaults to 0 (Boolean NOT NULL). The companion
 * `is_streamable_checked_at` is nullable with no default — NULL is
 * the tristate sentinel that means "never checked", mirroring the
 * loudness_lufs / loudness_measured_at pattern from v0.9.25.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MigrationV25V26Test {

    private val DB_NAME = "migration-v25v26-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StashDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `migration v25 to v26 adds streamability columns with defaults`() {
        // 1. Open at v25 and seed one minimal track row.
        helper.createDatabase(DB_NAME, 25).use { db ->
            db.insertTrackV25(id = 1L)
        }

        // 2. Run migration to v26 with validation. Fails fast if the
        // migration DDL doesn't match the v26 schema JSON Room will
        // generate for the updated entity.
        val migrated = helper.runMigrationsAndValidate(
            DB_NAME, 26, true, StashDatabase.MIGRATION_25_26,
        )

        // 3. is_streamable defaults to 0, is_streamable_checked_at is NULL.
        migrated.query(
            "SELECT is_streamable, is_streamable_checked_at FROM tracks WHERE id = 1",
        ).use { c ->
            assertEquals(1, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("is_streamable should default to 0", 0, c.getInt(0))
            assertTrue("is_streamable_checked_at should be NULL", c.isNull(1))
        }
    }

    @Test
    fun `migration v25 to v26 round-trips streamable values`() {
        helper.createDatabase(DB_NAME, 25).use { db ->
            db.insertTrackV25(id = 1L)
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME, 26, true, StashDatabase.MIGRATION_25_26,
        )

        val cv = ContentValues().apply {
            put("is_streamable", 1)
            put("is_streamable_checked_at", 1_700_000_000_000L)
        }
        migrated.update("tracks", SQLiteDatabase.CONFLICT_REPLACE, cv, "id = 1", null)

        migrated.query(
            "SELECT is_streamable, is_streamable_checked_at FROM tracks WHERE id = 1",
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
            assertEquals(1_700_000_000_000L, c.getLong(1))
        }
    }

    /**
     * Insert a minimally-populated v25 tracks row. Every NOT NULL column
     * from `core/data/schemas/.../25.json` is populated; nullable columns
     * default to NULL. Exact non-key values don't matter for the test —
     * we only care that the row survives the migration.
     */
    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertTrackV25(id: Long) {
        val cv = ContentValues().apply {
            put("id", id)
            put("title", "Test Track $id")
            put("artist", "Test Artist")
            put("album", "")
            put("album_artist", "")
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
