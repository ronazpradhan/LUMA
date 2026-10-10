package com.stash.feature.home.banner

import com.stash.data.download.backfill.MetadataBackfillState
import com.stash.data.download.backfill.MetadataBackfillState.BackfillSnapshot
import com.stash.data.download.backfill.MetadataBackfillState.State
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataBackfillBannerStateTest {

    @Test fun `IDLE maps to Hidden`() {
        val state = metadataBackfillBannerStateFor(snapshot(state = State.IDLE))
        assertEquals(MetadataBackfillBannerState.Hidden, state)
    }

    @Test fun `RUNNING with zero total maps to Hidden`() {
        // No library = no banner; do not render "Re-tagging library 0/0"
        val state = metadataBackfillBannerStateFor(snapshot(state = State.RUNNING, total = 0))
        assertEquals(MetadataBackfillBannerState.Hidden, state)
    }

    @Test fun `RUNNING with non-zero total maps to Running`() {
        val state = metadataBackfillBannerStateFor(snapshot(state = State.RUNNING, processed = 23, total = 50))
        assertEquals(MetadataBackfillBannerState.Running(processed = 23, total = 50), state)
    }

    @Test fun `FINISHED maps to Finished`() {
        val state = metadataBackfillBannerStateFor(
            snapshot(state = State.FINISHED, processed = 50, total = 50, safSkipped = 3)
        )
        assertEquals(MetadataBackfillBannerState.Finished(total = 50, safSkipped = 3), state)
    }

    private fun snapshot(
        state: State = State.IDLE,
        processed: Int = 0,
        total: Int = 0,
        safSkipped: Int = 0,
    ) = BackfillSnapshot(state, processed, total, safSkipped, finishedAt = null)
}
