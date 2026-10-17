package com.luma.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.luma.core.data.db.LumaDatabase
import com.luma.core.data.db.entity.PlaylistEntity
import com.luma.core.data.db.entity.SharedMixEntity
import com.luma.core.model.MusicSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SharedMixDaoTest {
    private lateinit var db: LumaDatabase
    private lateinit var dao: SharedMixDao
    private lateinit var playlistDao: PlaylistDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LumaDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.sharedMixDao(); playlistDao = db.playlistDao()
    }
    @After fun tearDown() { db.close() }

    private suspend fun playlist(src: String) = playlistDao.insert(PlaylistEntity(name = src, source = MusicSource.BOTH, sourceId = src))

    @Test fun `queries split owners and followers and deleting the playlist cascades`() = runTest {
        val owned = playlist("custom_a"); val followed = playlist("share:Kx7Qa2pL"); val paused = playlist("custom_b")
        dao.insert(SharedMixEntity(owned, "AAAAAAAA", SharedMixEntity.ROLE_OWNER, editKey = "k", name = "A"))
        dao.insert(SharedMixEntity(followed, "Kx7Qa2pL", SharedMixEntity.ROLE_FOLLOWER, name = "F"))
        dao.insert(SharedMixEntity(paused, "BBBBBBBB", SharedMixEntity.ROLE_OWNER, editKey = "k", name = "B", autoUpdate = false))
        assertEquals(listOf(owned), dao.activeOwnedWithUpdates().map { it.playlistId })
        assertEquals(listOf(followed), dao.activeFollowed().map { it.playlistId })
        assertEquals(followed, dao.byShareId("Kx7Qa2pL")?.playlistId)
        playlistDao.deleteById(followed)
        assertNull(dao.forPlaylist(followed))
    }
}
