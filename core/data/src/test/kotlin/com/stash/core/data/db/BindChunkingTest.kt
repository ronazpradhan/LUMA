package com.stash.core.data.db

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-logic guard for the bind-chunking primitive that all library-sized
 * IN() DAO methods route through (issue #337 class). Robolectric's SQLite
 * does NOT enforce the 999-variable cap, so this — not a DB test — is what
 * fails if the chunking logic regresses.
 */
class BindChunkingTest {

    @Test fun `empty input runs no query`() = runTest {
        var calls = 0
        val out = emptyList<Long>().chunkedForBind(limit = 800) { calls++; it }
        assertEquals(0, calls)
        assertEquals(emptyList<Long>(), out)
    }

    @Test fun `at-limit list is a single passthrough call`() = runTest {
        val ids = (1..800L).toList()
        val chunks = mutableListOf<Int>()
        val out = ids.chunkedForBind(limit = 800) { chunks.add(it.size); it }
        assertEquals(listOf(800), chunks)
        assertEquals(ids, out)
    }

    @Test fun `over-limit list splits into chunks and concatenates in order`() = runTest {
        val ids = (1..1500L).toList()
        val chunks = mutableListOf<Int>()
        val out = ids.chunkedForBind(limit = 800) { chunk -> chunks.add(chunk.size); chunk }
        assertEquals(listOf(800, 700), chunks) // two queries, none over the cap
        assertEquals(ids, out)                  // order preserved, nothing dropped
    }

    @Test fun `limit-plus-one splits into two`() = runTest {
        val ids = (1..801L).toList()
        val chunks = mutableListOf<Int>()
        ids.chunkedForBind(limit = 800) { chunk -> chunks.add(chunk.size); chunk }
        assertEquals(listOf(800, 1), chunks)
    }

    @Test fun `write variant runs one statement per chunk and skips empty`() = runTest {
        var emptyCalls = 0
        emptyList<Long>().chunkedForBindWrite(limit = 800) { emptyCalls++ }
        assertEquals(0, emptyCalls)

        val seen = mutableListOf<Int>()
        (1..1700L).toList().chunkedForBindWrite(limit = 800) { seen.add(it.size) }
        assertEquals(listOf(800, 800, 100), seen)
    }
}
