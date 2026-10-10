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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MigrationV20V21Test {
    private val DB_NAME = "migration-v20v21-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StashDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `migration v20 to v21 adds new columns and creates skip events table`() {
        helper.createDatabase(DB_NAME, 20).close()

        val db = helper.runMigrationsAndValidate(DB_NAME, 21, true, StashDatabase.MIGRATION_20_21)

        // tracks columns
        db.query("PRAGMA table_info(tracks)").use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) names += c.getString(1)
            assertTrue("mbid present", names.contains("mbid"))
            assertTrue("lastfm_user_playcount present", names.contains("lastfm_user_playcount"))
            assertTrue("lastfm_listeners present", names.contains("lastfm_listeners"))
            assertTrue("lastfm_user_loved present", names.contains("lastfm_user_loved"))
        }
        // stash_mix_recipes has seed_strategy
        db.query("PRAGMA table_info(stash_mix_recipes)").use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) names += c.getString(1)
            assertTrue("seed_strategy present", names.contains("seed_strategy"))
        }
        // track_skip_events table exists
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='track_skip_events'").use { c ->
            assertEquals(1, c.count)
        }
    }
}
