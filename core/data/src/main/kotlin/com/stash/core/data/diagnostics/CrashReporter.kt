package com.stash.core.data.diagnostics

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs a global [Thread.UncaughtExceptionHandler] that writes the
 * crash to a file via [CrashFileStore], then chains through to whatever
 * handler the platform installed before us (so the OS still records the
 * crash and the process exits cleanly).
 *
 * Privacy: zero network, zero auto-upload. Files live in `cacheDir/crashes/`
 * until the user explicitly shares one through Settings.
 *
 * Idempotent: a second [install] call is a no-op.
 */
@Singleton
class CrashReporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crashFileStore: CrashFileStore,
) {

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Write first, but never let a writer failure prevent the
            // platform handler from running — that would orphan the crash
            // from the OS log and skip the process-exit path.
            runCatching { crashFileStore.writeCrash(thread, throwable) }
                .onFailure { e -> Log.w(TAG, "crash write failed", e) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG = "CrashReporter"
    }
}
