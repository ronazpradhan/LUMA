package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.PlaylistEntity
import com.stash.core.data.db.entity.PlaylistTrackCrossRef
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.model.MusicSource
import com.stash.core.model.PlaylistType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end cover for "Undo last sync": capture a restore point, apply the two
 * destructive things a sync actually does, then undo and prove the library is
 * back.
 *
 * The scenario is the reported bug — an ACCUMULATE sync that hid YouTube
 * playlists and cleared membership — so these tests fail if undo stops covering
 * the case it was built for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SyncUndoDaoTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: SyncUndoDao
    private lateinit var playlistDao: PlaylistDao

    private val syncId = 42L

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.syncUndoDao()
        playlistDao = db.playlistDao()
    }

    @After fun tearDown() { db.close() }

    private suspend fun seedTrack(id: Long): Long =
        db.trackDao().insert(
            TrackEntity(id = id, title = "Track $id", artist = "Artist", album = "Album")
        )

    private suspend fun seedPlaylist(name: String, source: MusicSource, sourceId: String): Long =
        playlistDao.insert(
            PlaylistEntity(
                name = name,
                source = source,
                sourceId = sourceId,
                type = PlaylistType.CUSTOM,
                isActive = true,
                syncEnabled = true,
            )
        )

    private suspend fun link(playlistId: Long, trackId: Long, pos: Int, local: Boolean = false) =
        playlistDao.insertCrossRef(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = trackId,
                position = pos,
                locallyAdded = local,
            )
        )


    /** Track ids currently linked to [playlistId], in stored position order. */
    private suspend fun trackIdsIn(playlistId: Long): List<Long> =
        playlistDao.getCrossRefsForPlaylist(playlistId)
            .sortedBy { it.position }
            .map { it.trackId }

    @Test
    fun `undo restores a playlist that a sync hid`() = runTest {
        val pid = seedPlaylist("srab", MusicSource.YOUTUBE, "yt-srab")
        dao.capture(syncId, nowEpochMs = 1_000L)

        // What the sync did: soft-hide it.
        playlistDao.deactivateMissingForSource(MusicSource.YOUTUBE, currentSourceIds = listOf("something-else"))
        assertThat(playlistDao.getById(pid)!!.isActive).isFalse()

        dao.restore(syncId)

        assertThat(playlistDao.getById(pid)!!.isActive).isTrue()
    }

    @Test
    fun `undo refills a playlist that REFRESH emptied, preserving order`() = runTest {
        val pid = seedPlaylist("Mix", MusicSource.YOUTUBE, "yt-mix")
        val t1 = seedTrack(1); val t2 = seedTrack(2); val t3 = seedTrack(3)
        link(pid, t1, 0); link(pid, t2, 1); link(pid, t3, 2)

        dao.capture(syncId, nowEpochMs = 1_000L)

        // What REFRESH did: wipe the sync-added membership.
        playlistDao.clearSyncedPlaylistTracks(pid)
        assertThat(trackIdsIn(pid)).isEmpty()

        val restored = dao.restore(syncId)

        assertThat(restored).isEqualTo(3)
        assertThat(trackIdsIn(pid)).containsExactly(t1, t2, t3).inOrder()
    }

    @Test
    fun `undo keeps tracks the user added after the sync`() = runTest {
        val pid = seedPlaylist("Mix", MusicSource.YOUTUBE, "yt-mix")
        val synced = seedTrack(1)
        link(pid, synced, 0)
        dao.capture(syncId, nowEpochMs = 1_000L)

        playlistDao.clearSyncedPlaylistTracks(pid)
        // The user hand-added a track AFTER the sync — undo must not destroy it.
        val mine = seedTrack(99)
        link(pid, mine, 5, local = true)

        dao.restore(syncId)

        assertThat(trackIdsIn(pid)).containsExactly(synced, mine)
    }

    @Test
    fun `a point is consumed so the same undo cannot be applied twice`() = runTest {
        seedPlaylist("P", MusicSource.YOUTUBE, "yt-p")
        dao.capture(syncId, nowEpochMs = 1_000L)
        assertThat(dao.latestPointNow()).isNotNull()

        dao.restore(syncId)

        assertThat(dao.latestPointNow()).isNull()
    }

    @Test
    fun `only the newest points are kept`() = runTest {
        seedPlaylist("P", MusicSource.YOUTUBE, "yt-p")
        repeat(5) { i -> dao.capture(syncId = i.toLong(), nowEpochMs = (i * 1_000).toLong()) }

        // Newest MAX_UNDO_POINTS survive; the oldest are pruned.
        assertThat(dao.latestPointNow()!!.syncId).isEqualTo(4L)
        assertThat(dao.pointIdsBeyond(SyncUndoDao.MAX_UNDO_POINTS)).isEmpty()
    }

    @Test
    fun `a point records what it captured`() = runTest {
        val pid = seedPlaylist("P", MusicSource.YOUTUBE, "yt-p")
        link(pid, seedTrack(1), 0); link(pid, seedTrack(2), 1)

        dao.capture(syncId, nowEpochMs = 7_000L)

        val point = dao.latestPointNow()!!
        assertThat(point.syncId).isEqualTo(syncId)
        assertThat(point.createdAtEpochMs).isEqualTo(7_000L)
        assertThat(point.playlistCount).isEqualTo(1)
        assertThat(point.membershipCount).isEqualTo(2)
    }
}
