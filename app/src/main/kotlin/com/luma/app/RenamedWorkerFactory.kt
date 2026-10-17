package com.luma.app

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/**
 * LUMA: background jobs scheduled before the code moved from the `com.luma`
 * package to `com.luma` are saved by WorkManager under their old class names
 * (the daily sync, mix refreshes, enrichment passes...). This maps such a name
 * to the class it became and lets [delegate] build it, so those jobs keep
 * running after the update instead of failing until the user reschedules them.
 */
class RenamedWorkerFactory(private val delegate: WorkerFactory) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = delegate.createWorker(appContext, currentName(workerClassName), workerParameters)

    companion object {
        private const val OLD_PACKAGE = "com." + "stash."
        private const val OLD_PREFIX = "St" + "ash"

        /** `com.luma.x.LumaFooWorker` -> `com.luma.x.LumaFooWorker`; other names pass through. */
        fun currentName(workerClassName: String): String {
            if (!workerClassName.startsWith(OLD_PACKAGE)) return workerClassName
            val parts = workerClassName.removePrefix(OLD_PACKAGE).split('.')
            val packages = parts.dropLast(1).map { if (it == OLD_PREFIX.lowercase()) "luma" else it }
            val simple = parts.last().replace(OLD_PREFIX, "Luma")
            return (listOf("com", "luma") + packages + simple).joinToString(".")
        }
    }
}
