package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.PlaylistEntity
import com.stash.core.data.db.entity.PlaylistTrackCrossRef
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.model.MusicSource
import com.stash.core.model.PlaylistType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Pins the assumption the "mixes are opt-in" change rests on (#368).
 *
 * `defaultSyncEnabled` used to auto-enable a discovered DAILY_MIX in Online mode
 * so it would "surface immediately with no download". Making discovery opt-in is
 * only safe because [PlaylistDao.getAllVisible] already surfaces a
 * `sync_enabled = 0` playlist through its streamable escape hatch — the
 * auto-enable was redundant for surfacing and load-bearing only for the download
 * harm.
 *
 * If this test ever goes red, that change silently HIDES mixes instead of merely
 * un-downloading them, and it must be revisited rather than patched around.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class PlaylistDaoMixVisibilityTest {

    private lateinit var db: StashDatabase
    private lateinit var playlistDao: PlaylistDao
    private var mixId: Long = 0L

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        playlistDao = db.playlistDao()

        // A discovered algorithmic mix as it will exist after the change: opt-in
        // (sync_enabled = false), active, holding stream-only tracks.
        mixId = playlistDao.insert(
            PlaylistEntity(
                name = "Daily Mix 1",
                source = MusicSource.SPOTIFY,
                sourceId = "spotify:playlist:dm1",
                type = PlaylistType.DAILY_MIX,
                syncEnabled = false,
                isActive = true,
            )
        )

        val trackId = db.trackDao().insert(
            TrackEntity(
                title = "Borderline",
                artist = "Tame Impala",
                canonicalTitle = "borderline",
                canonicalArtist = "tame impala",
                isDownloaded = false,
                isStreamable = true,
            )
        )
        playlistDao.insertCrossRef(
            PlaylistTrackCrossRef(
                playlistId = mixId,
                trackId = trackId,
                position = 0,
                addedAt = Instant.parse("2026-07-01T00:00:00Z"),
                removedAt = null,
            )
        )
    }

    @After fun tearDown() { db.close() }

    @Test fun `sync-disabled daily mix with streamable tracks is visible online`() = runTest {
        val visible = playlistDao.getAllVisible(includeStreamable = true).first().map { it.id }
        assertThat(visible).contains(mixId)
    }

    /** Offline, with nothing downloaded, hiding it is correct — it can't play. */
    @Test fun `sync-disabled daily mix with nothing downloaded is hidden offline`() = runTest {
        val visible = playlistDao.getAllVisible(includeStreamable = false).first().map { it.id }
        assertThat(visible).doesNotContain(mixId)
    }
}
