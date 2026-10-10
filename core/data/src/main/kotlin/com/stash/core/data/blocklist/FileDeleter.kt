package com.stash.core.data.blocklist

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indirection over `File.delete` so [BlocklistGuard] is unit-testable
 * without touching the real filesystem. Best-effort: a missing file or
 * a delete failure is silently swallowed because the row deletes still
 * proceed and an orphaned file is harmless.
 */
@Singleton
class FileDeleter @Inject constructor() {
    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }
}
