package com.stash.core.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A live, human-readable account of what a sync is actually doing.
 *
 * The progress counter alone ("fetched 52… 53… 54") tells the user a sync is
 * moving but not what it FOUND — so when a sync goes wrong there is nothing to
 * look at, and diagnosing it means reading logcat. This records the same moments
 * in the user's own terms ("Liked Songs — 402 tracks"), which is both the live
 * feed and the thing they can copy into a bug report.
 *
 * Deliberately in-memory and bounded: it's a window onto the run in progress,
 * not an audit trail, so it costs nothing when nobody is watching and cannot
 * grow without limit on a long sync.
 */
@Singleton
class SyncLog @Inject constructor() {

    enum class Level { INFO, SUCCESS, WARN, ERROR }

    data class Line(
        val atEpochMs: Long,
        val level: Level,
        val text: String,
    )

    private val _lines = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = _lines.asStateFlow()

    /** Injectable clock so tests get deterministic timestamps. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    /**
     * Start a fresh run. The previous run's lines stay readable until a new sync
     * begins — a log that erased itself the moment the sync ended would be
     * useless for the case it exists for, which is reading it afterwards.
     */
    fun beginRun(label: String) {
        _lines.value = emptyList()
        add(Level.INFO, label)
    }

    fun info(text: String) = add(Level.INFO, text)
    fun success(text: String) = add(Level.SUCCESS, text)
    fun warn(text: String) = add(Level.WARN, text)
    fun error(text: String) = add(Level.ERROR, text)

    private fun add(level: Level, text: String) {
        val line = Line(atEpochMs = clock(), level = level, text = text)
        // Keep the newest MAX_LINES. Dropping from the front means a long sync
        // shows its recent activity rather than stalling on ancient history.
        _lines.value = (_lines.value + line).takeLast(MAX_LINES)
    }

    /** The whole log as plain text — what a user pastes into a bug report. */
    fun asPlainText(): String = _lines.value.joinToString("\n") { line ->
        val mark = when (line.level) {
            Level.INFO -> " "
            Level.SUCCESS -> "+"
            Level.WARN -> "!"
            Level.ERROR -> "x"
        }
        "$mark ${line.text}"
    }

    fun clear() { _lines.value = emptyList() }

    private companion object {
        /**
         * Enough to cover a large library's sync while staying trivially small in
         * memory (a few hundred short strings).
         */
        const val MAX_LINES = 400
    }
}
