package com.luma.core.data.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.luma.core.data.db.LumaDatabase
import com.luma.core.data.db.entity.ArtistProfileCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room-backed tests for [com.luma.core.data.db.dao.ArtistProfileCacheDao].
 *
 * Uses Robolectric + an in-memory Room database (no Android device or
 * emulator required). Verifies the basic upsert/get contract plus the
 * [com.luma.core.data.db.dao.ArtistProfileCacheDao.evictOldest] query
 * that keeps the N newest rows by `fetched_at` — the disk-tier cap that
 * pairs with the 20-entry memory LRU in [ArtistCache].
 */
@RunWith(RobolectricTestRunner::class)
class ArtistProfileCacheDaoTest {

    private lateinit var db: LumaDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LumaDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() { db.close() }

    @Test
    fun `upsert then get returns entity`() = runTest {
        val dao = db.artistProfileCacheDao()
        val entity = ArtistProfileCacheEntity("UCabc", "{}", 1_700_000_000_000L)
        dao.upsert(entity)
        assertEquals(entity, dao.get("UCabc"))
    }

    @Test
    fun `evictOldest keeps 20 newest entries`() = runTest {
        val dao = db.artistProfileCacheDao()
        repeat(30) { i -> dao.upsert(ArtistProfileCacheEntity("UC$i", "{}", i.toLong())) }
        dao.evictOldest(keep = 20)
        assertNull(dao.get("UC0"))
        assertEquals("UC29", dao.get("UC29")?.artistId)
    }
}
