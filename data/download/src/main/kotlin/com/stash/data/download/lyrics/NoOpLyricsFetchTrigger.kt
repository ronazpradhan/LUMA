package com.stash.data.download.lyrics

/**
 * No-op fallback for unit-test fixtures that don't want to take a
 * `mockk<LyricsFetchTrigger>()` dependency or pull in the `:data:lyrics`
 * module to construct a real binding. Production builds always use the
 * Hilt-provided `LyricsFetchTriggerModule` binding in `:app` — this class
 * is intentionally not `@Inject`-able and lives outside the DI graph.
 *
 * Kept `internal` to discourage accidental production use; tests that
 * need a non-mock instance can shadow the visibility via a same-package
 * test source.
 */
internal class NoOpLyricsFetchTrigger : LyricsFetchTrigger {
    override fun enqueueFor(trackId: Long) = Unit
}
