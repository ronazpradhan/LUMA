package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.db.entity.TrackSkipEventEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class TrackSkipEventDaoBanListTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: TrackSkipEventDao
    private lateinit var trackDao: TrackDao

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = db.trackSkipEventDao()
        trackDao = db.trackDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun `bans canonical key with 3+ early skips inside time window`() = runTest {
        trackDao.insert(track(id = 1L, canonicalArtist = "the strokes", canonicalTitle = "last nite"))

        val now = System.currentTimeMillis()
        repeat(4) { i -> dao.insert(skip(trackId = 1L, positionMs = 1000L, skippedAt = now - i * 1000L)) }

        val result = dao.getEarlySkipBannedCanonicalKeys(
            minSkips = 3,
            sinceMs = now - TimeUnit.DAYS.toMillis(90),
            maxPositionMs = 30_000L,
        )

        assertEquals(listOf("the strokes|last nite"), result)
    }

    @Test fun `excludes canonical keys with skips beyond position cutoff`() = runTest {
        trackDao.insert(track(id = 1L, canonicalArtist = "arctic monkeys", canonicalTitle = "do i wanna know"))

        val now = System.currentTimeMillis()
        repeat(4) { dao.insert(skip(trackId = 1L, positionMs = 90_000L, skippedAt = now)) }

        val result = dao.getEarlySkipBannedCanonicalKeys(
            minSkips = 3,
            sinceMs = now - TimeUnit.DAYS.toMillis(90),
            maxPositionMs = 30_000L,
        )

        assertTrue("expected empty, got $result", result.isEmpty())
    }

    @Test fun `excludes canonical keys below skip count threshold`() = runTest {
        trackDao.insert(track(id = 1L, canonicalArtist = "radiohead", canonicalTitle = "creep"))

        val now = System.currentTimeMillis()
        repeat(2) { dao.insert(skip(trackId = 1L, positionMs = 1000L, skippedAt = now)) }

        val result = dao.getEarlySkipBannedCanonicalKeys(
            minSkips = 3,
            sinceMs = now - TimeUnit.DAYS.toMillis(90),
            maxPositionMs = 30_000L,
        )

        assertTrue("expected empty, got $result", result.isEmpty())
    }

    @Test fun `excludes skips outside time window`() = runTest {
        trackDao.insert(track(id = 1L, canonicalArtist = "blur", canonicalTitle = "song 2"))

        val now = System.currentTimeMillis()
        val veryOld = now - TimeUnit.DAYS.toMillis(100)
        repeat(4) { dao.insert(skip(trackId = 1L, positionMs = 1000L, skippedAt = veryOld)) }

        val result = dao.getEarlySkipBannedCanonicalKeys(
            minSkips = 3,
            sinceMs = now - TimeUnit.DAYS.toMillis(90),
            maxPositionMs = 30_000L,
        )

        assertTrue("expected empty (events outside window), got $result", result.isEmpty())
    }

    @Test fun `bans multiple distinct canonical keys when each has 3+ qualifying skips`() = runTest {
        trackDao.insert(track(id = 1L, canonicalArtist = "a", canonicalTitle = "x"))
        trackDao.insert(track(id = 2L, canonicalArtist = "b", canonicalTitle = "y"))

        val now = System.currentTimeMillis()
        repeat(3) { dao.insert(skip(trackId = 1L, positionMs = 5000L, skippedAt = now)) }
        repeat(3) { dao.insert(skip(trackId = 2L, positionMs = 5000L, skippedAt = now)) }

        val result = dao.getEarlySkipBannedCanonicalKeys(
            minSkips = 3,
            sinceMs = now - TimeUnit.DAYS.toMillis(90),
            maxPositionMs = 30_000L,
        )

        assertEquals(setOf("a|x", "b|y"), result.toSet())
    }

    private fun track(id: Long, canonicalArtist: String, canonicalTitle: String) = TrackEntity(
        id = id,
        title = "Title $id",
        artist = "Artist $id",
        canonicalTitle = canonicalTitle,
        canonicalArtist = canonicalArtist,
        isDownloaded = true,
    )

    private fun skip(trackId: Long, positionMs: Long, skippedAt: Long) = TrackSkipEventEntity(
        trackId = trackId,
        positionMs = positionMs,
        skippedAt = skippedAt,
    )
}
