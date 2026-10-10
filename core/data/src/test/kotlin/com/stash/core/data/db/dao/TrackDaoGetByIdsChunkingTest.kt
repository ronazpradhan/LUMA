package com.stash.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.entity.TrackEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end wiring check for the bind-chunked [TrackDao.getByIds]: a
 * library-sized id list (well over the 999 bind cap) must return every row,
 * once, with nothing dropped by the chunk boundaries (#337). Robolectric's
 * SQLite won't throw "too many SQL variables", so this proves completeness of
 * the raw+default wiring; [com.stash.core.data.db.BindChunkingTest] guards the
 * chunk math itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class TrackDaoGetByIdsChunkingTest {

    private lateinit var db: StashDatabase
    private lateinit var dao: TrackDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            StashDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.trackDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun `getByIds returns every row for a list far beyond the bind cap`() = runTest {
        // 1700 tracks > 2 * SQLITE_BIND_LIMIT (800): forces three chunks.
        val ids = (1..1700).map {
            dao.insert(
                TrackEntity(
                    title = "T$it", artist = "a",
                    canonicalTitle = "t$it", canonicalArtist = "a",
                ),
            )
        }

        val result = dao.getByIds(ids)

        assertEquals(ids.size, result.size)
        assertEquals(ids.toSet(), result.map { it.id }.toSet()) // all present, no dupes
    }

    @Test fun `getByIds on empty list returns empty and runs no query`() = runTest {
        assertEquals(emptyList<TrackEntity>(), dao.getByIds(emptyList()))
    }
}
