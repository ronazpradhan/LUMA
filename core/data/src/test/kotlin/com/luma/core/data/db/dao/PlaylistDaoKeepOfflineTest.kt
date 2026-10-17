package com.luma.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.luma.core.data.db.LumaDatabase
import com.luma.core.data.db.entity.PlaylistEntity
import com.luma.core.model.MusicSource
import com.luma.core.model.PlaylistType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The playlist page's Download switch (#474): `playlists.keep_offline`. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class PlaylistDaoKeepOfflineTest {

    private lateinit var db: LumaDatabase
    private lateinit var dao: PlaylistDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LumaDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = db.playlistDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun `setKeepOffline round-trips and leaves sync_enabled alone`() = runTest {
        val id = insert("Release Radar", syncEnabled = true)
        assertThat(dao.getById(id)!!.keepOffline).isFalse()

        dao.setKeepOffline(id, true)
        assertThat(dao.getById(id)!!.keepOffline).isTrue()
        assertThat(dao.getById(id)!!.syncEnabled).isTrue()

        dao.setKeepOffline(id, false)
        assertThat(dao.getById(id)!!.keepOffline).isFalse()
    }

    @Test fun `the keep-offline ids are the active playlists with the switch on`() = runTest {
        val kept = insert("Kept")
        val hidden = insert("Kept but hidden", isActive = false)
        insert("Not kept")
        // Synced is not kept: counting it would queue every synced playlist after each sync (#368).
        insert("Synced, not kept", syncEnabled = true)
        dao.setKeepOffline(kept, true)
        dao.setKeepOffline(hidden, true)

        assertThat(dao.getKeepOfflinePlaylistIds()).containsExactly(kept)
    }

    private suspend fun insert(name: String, syncEnabled: Boolean = false, isActive: Boolean = true): Long =
        dao.insert(
            PlaylistEntity(
                name = name,
                source = MusicSource.SPOTIFY,
                sourceId = "spotify:$name",
                type = PlaylistType.DAILY_MIX,
                syncEnabled = syncEnabled,
                isActive = isActive,
            )
        )
}
