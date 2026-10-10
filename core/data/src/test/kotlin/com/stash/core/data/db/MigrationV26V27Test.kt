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
 * v0.9.35: verifies migration v26 -> v27 adds the
 * `metadata_embedded_at` column to the `tracks` table without
 * disturbing existing rows. Pure ALTER TABLE additive — no data
 * backfill.
 *
 * `metadata_embedded_at` is nullable INTEGER with no default. NULL is
 * the tristate sentinel that means "never tagged" — the worker's
 * `WHERE metadata_embedded_at IS NULL` predicate is the entry point
 * for the backfill loop. A non-null non-zero value is the
 * successful-embed wall-clock stamp; 0L is the irrecoverable-failure
 * sentinel (file missing, ffmpeg error, SAF row). Both stamps remove
 * the row from the worker's result set so it terminates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MigrationV26V27Test {

    private val DB_NAME = "migration-v26v27-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StashDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `migration v26 to v27 adds metadata_embedded_at column with NULL default`() {
        // 1. Open at v26 and seed one minimal track row.
        helper.createDatabase(DB_NAME, 26).use { db ->
            db.insertTrackV26(id = 1L)
        }

        // 2. Run migration to v27 with validation. Fails fast if the
        // migration DDL doesn't match the v27 schema JSON Room
        // generates for the updated entity.
        val migrated = helper.runMigrationsAndValidate(
            DB_NAME, 27, true, StashDatabase.MIGRATION_26_27,
        )

        // 3. metadata_embedded_at is NULL on the pre-existing row.
        migrated.query(
            "SELECT metadata_embedded_at FROM tracks WHERE id = 1",
        ).use { c ->
            assertEquals(1, c.count)
            assertTrue(c.moveToFirst())
            assertTrue(
                "metadata_embedded_at should default to NULL for legacy rows",
                c.isNull(0),
            )
        }
    }

    @Test
    fun `migration v26 to v27 round-trips a stamped value`() {
        helper.createDatabase(DB_NAME, 26).use { db ->
            db.insertTrackV26(id = 1L)
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME, 27, true, StashDatabase.MIGRATION_26_27,
        )

        val cv = ContentValues().apply {
            put("metadata_embedded_at", 1_716_000_000_000L)
        }
        migrated.update("tracks", SQLiteDatabase.CONFLICT_REPLACE, cv, "id = 1", null)

        migrated.query(
            "SELECT metadata_embedded_at FROM tracks WHERE id = 1",
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1_716_000_000_000L, c.getLong(0))
        }
    }

    @Test
    fun `migration v26 to v27 round-trips the 0L failure sentinel`() {
        // The 0L sentinel means "backfill tried and gave up
        // irrecoverably" (file missing, ffmpeg error, SAF row). It
        // MUST be distinguishable from NULL so the worker's
        // `metadata_embedded_at IS NULL` predicate filters it out —
        // re-attempting would just loop. SQLite stores 0L the same
        // way it stores any INTEGER; this test pins that the column
        // affinity preserves the value rather than coercing 0 to NULL
        // (which it never does for INTEGER, but make it explicit).
        helper.createDatabase(DB_NAME, 26).use { db ->
            db.insertTrackV26(id = 1L)
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME, 27, true, StashDatabase.MIGRATION_26_27,
        )

        val cv = ContentValues().apply {
            put("metadata_embedded_at", 0L)
        }
        migrated.update("tracks", SQLiteDatabase.CONFLICT_REPLACE, cv, "id = 1", null)

        migrated.query(
            "SELECT metadata_embedded_at FROM tracks WHERE id = 1",
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("0L sentinel should be a non-NULL INTEGER", !c.isNull(0))
            assertEquals(0L, c.getLong(0))
        }
    }

    /**
     * Insert a minimally-populated v26 tracks row. Every NOT NULL column
     * from `core/data/schemas/.../26.json` is populated; nullable columns
     * default to NULL. Exact non-key values don't matter for the test —
     * we only care that the row survives the migration intact.
     */
    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertTrackV26(id: Long) {
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
            put("is_streamable", 0)
        }
        insert("tracks", SQLiteDatabase.CONFLICT_REPLACE, cv)
    }
}
