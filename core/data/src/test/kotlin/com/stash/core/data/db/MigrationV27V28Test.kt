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
 * v0.9.36: verifies migration v27 -> v28 adds the `lyrics_fetched_at`
 * column (additive ALTER) AND creates the new `lyrics` table
 * (FK CASCADE to tracks.id INTEGER, INTEGER PK).
 *
 * `lyrics_fetched_at`: NULL = never tried; 0L = tried-and-failed
 * sentinel; non-zero = success epoch-millis. Mirror of the
 * metadata_embedded_at column's sentinel semantics from v0.9.35.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MigrationV27V28Test {

    private val DB_NAME = "migration-v27v28-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StashDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `migration v27 to v28 adds lyrics_fetched_at column with NULL default`() {
        helper.createDatabase(DB_NAME, 27).use { db ->
            db.insertTrackV27(id = 1L)
        }
        val migrated = helper.runMigrationsAndValidate(
            DB_NAME, 28, true, StashDatabase.MIGRATION_27_28,
        )
        migrated.query("SELECT lyrics_fetched_at FROM tracks WHERE id = 1").use { c ->
            assertEquals(1, c.count)
            assertTrue(c.moveToFirst())
            assertTrue(
                "lyrics_fetched_at should default to NULL for legacy rows",
                c.isNull(0),
            )
        }
    }

    @Test
    fun `migration v27 to v28 creates empty lyrics table`() {
        helper.createDatabase(DB_NAME, 27).use { db -> db.insertTrackV27(id = 1L) }
        val migrated = helper.runMigrationsAndValidate(
            DB_NAME, 28, true, StashDatabase.MIGRATION_27_28,
        )
        migrated.query("SELECT COUNT(*) FROM lyrics").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun `migration v27 to v28 round-trips a stamped lyrics_fetched_at value`() {
        helper.createDatabase(DB_NAME, 27).use { db -> db.insertTrackV27(id = 1L) }
        val migrated = helper.runMigrationsAndValidate(
            DB_NAME, 28, true, StashDatabase.MIGRATION_27_28,
        )
        val cv = ContentValues().apply { put("lyrics_fetched_at", 1_716_000_000_000L) }
        migrated.update("tracks", SQLiteDatabase.CONFLICT_REPLACE, cv, "id = 1", null)
        migrated.query("SELECT lyrics_fetched_at FROM tracks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1_716_000_000_000L, c.getLong(0))
        }
    }

    @Test
    fun `migration v27 to v28 round-trips the 0L failure sentinel`() {
        helper.createDatabase(DB_NAME, 27).use { db -> db.insertTrackV27(id = 1L) }
        val migrated = helper.runMigrationsAndValidate(
            DB_NAME, 28, true, StashDatabase.MIGRATION_27_28,
        )
        val cv = ContentValues().apply { put("lyrics_fetched_at", 0L) }
        migrated.update("tracks", SQLiteDatabase.CONFLICT_REPLACE, cv, "id = 1", null)
        migrated.query("SELECT lyrics_fetched_at FROM tracks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("0L sentinel should be a non-NULL INTEGER", !c.isNull(0))
            assertEquals(0L, c.getLong(0))
        }
    }

    @Test
    fun `lyrics table accepts insert keyed by INTEGER track_id with FK cascade on delete`() {
        helper.createDatabase(DB_NAME, 27).use { db -> db.insertTrackV27(id = 1L) }
        val migrated = helper.runMigrationsAndValidate(
            DB_NAME, 28, true, StashDatabase.MIGRATION_27_28,
        )
        migrated.execSQL("PRAGMA foreign_keys = ON")
        val cv = ContentValues().apply {
            put("track_id", 1L)
            put("plain_text", "hello")
            putNull("synced_lrc")
            put("instrumental", 0)
            putNull("language")
            put("source", "lrclib")
            put("source_lyrics_id", "42")
            put("fetched_at", 1_716_000_000_000L)
        }
        migrated.insert("lyrics", SQLiteDatabase.CONFLICT_REPLACE, cv)
        migrated.query("SELECT COUNT(*) FROM lyrics WHERE track_id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        // FK CASCADE: delete the track, lyrics row vanishes.
        migrated.delete("tracks", "id = 1", null)
        migrated.query("SELECT COUNT(*) FROM lyrics").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }

    /**
     * Insert a minimally-populated v27 tracks row. Mirror of
     * `MigrationV26V27Test.insertTrackV26` but for the v27 schema
     * (already has `metadata_embedded_at` from v0.9.35).
     *
     * IMPORTANT: if 27.json includes additional NOT NULL columns
     * beyond what v26 had + `metadata_embedded_at`, add them here.
     * Diff `26.json` vs `27.json` to find any.
     */
    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertTrackV27(id: Long) {
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
            putNull("metadata_embedded_at")
        }
        insert("tracks", SQLiteDatabase.CONFLICT_REPLACE, cv)
    }
}
