package com.stash.core.model

import kotlinx.serialization.Serializable

/**
 * Records what happened during a single step of the sync pipeline.
 * A list of these is JSON-serialized into the sync_history diagnostics column.
 */
@Serializable
data class SyncStepResult(
    val service: String,
    val step: String,
    val status: StepStatus,
    val itemCount: Int = 0,
    val errorMessage: String? = null,
    val httpCode: Int? = null,
)

/**
 * [SKIPPED] means the user switched that step off — it is NOT evidence of
 * anything working or failing. Distinct from [EMPTY] (the call ran and returned
 * nothing, which can be a real bug) so a diagnostics bundle doesn't send anyone
 * hunting an API that was simply never called.
 */
@Serializable
enum class StepStatus { SUCCESS, EMPTY, ERROR, SKIPPED }
