package com.stash.feature.home.banner

import com.stash.data.download.backfill.MetadataBackfillState.BackfillSnapshot
import com.stash.data.download.backfill.MetadataBackfillState.State

/**
 * Discrete states for the Home "re-tagging library" banner. [Hidden]
 * is the dominant state in the steady-state install — only rendered
 * while the v0.9.35+ backfill worker is actively processing rows, and
 * for a short "Done" pulse after completion.
 */
sealed interface MetadataBackfillBannerState {
    data object Hidden : MetadataBackfillBannerState
    data class Running(val processed: Int, val total: Int) : MetadataBackfillBannerState
    data class Finished(val total: Int, val safSkipped: Int) : MetadataBackfillBannerState
}

/**
 * Pure mapping from MetadataBackfillState.BackfillSnapshot into the
 * banner sealed type. Extracted so HomeViewModel doesn't carry the
 * branching logic inline.
 */
fun metadataBackfillBannerStateFor(snapshot: BackfillSnapshot): MetadataBackfillBannerState =
    when (snapshot.state) {
        State.IDLE -> MetadataBackfillBannerState.Hidden
        State.RUNNING ->
            if (snapshot.total <= 0) MetadataBackfillBannerState.Hidden
            else MetadataBackfillBannerState.Running(snapshot.processed, snapshot.total)
        State.FINISHED ->
            MetadataBackfillBannerState.Finished(snapshot.total, snapshot.safSkipped)
    }
