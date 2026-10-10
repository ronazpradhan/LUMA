package com.stash.core.data.db

/**
 * Max keys in one SQLite `IN (...)` clause. Android <= 11 caps a statement at
 * 999 bind variables; 800 leaves headroom for other params in the same query.
 *
 * Passing a library-sized id list (>999 tracks) to an unchunked `IN (:list)`
 * throws `SQLiteException: too many SQL variables` and kills sync / mix
 * refresh / playback resume — this is the recurring crash of issue #337, whose
 * first fix (#358) chunked only one DAO and left the class live elsewhere.
 * Any DAO method whose bound list can reach library size MUST route through
 * [chunkedForBind] / [chunkedForBindWrite].
 */
const val SQLITE_BIND_LIMIT = 800

/**
 * Runs [query] over this list in chunks of at most [limit] and concatenates the
 * per-chunk results, so callers can pass an arbitrarily large id list without
 * blowing SQLite's bind-variable cap.
 *
 * Correct ONLY for queries that return one row per input id (`GROUP BY id`,
 * `DISTINCT id`, `... id IN (:ids)`): the input is split into disjoint chunks,
 * so per-id rows never duplicate or drop across chunks, and order follows the
 * input order. Do NOT use for a single global aggregate (one `SUM`/`COUNT` over
 * the whole set) — chunking would return one partial row per chunk.
 *
 * Empty input runs no query.
 */
suspend fun <T, R> List<T>.chunkedForBind(
    limit: Int = SQLITE_BIND_LIMIT,
    query: suspend (List<T>) -> List<R>,
): List<R> = when {
    isEmpty() -> emptyList()
    size <= limit -> query(this)
    else -> chunked(limit).flatMap { query(it) }
}

/**
 * Bind-safe variant for write statements (UPDATE/DELETE returning no rows):
 * runs [block] once per chunk of at most [limit] ids. Each chunk is its own
 * autocommit statement — callers needing all-or-nothing must wrap the whole
 * call in a transaction.
 *
 * Empty input runs no statement.
 */
suspend fun <T> List<T>.chunkedForBindWrite(
    limit: Int = SQLITE_BIND_LIMIT,
    block: suspend (List<T>) -> Unit,
) {
    if (isEmpty()) return
    if (size <= limit) {
        block(this)
    } else {
        chunked(limit).forEach { block(it) }
    }
}
