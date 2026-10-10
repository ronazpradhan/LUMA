package com.stash.core.data.sync.workers

import com.stash.core.model.StepStatus
import com.stash.core.model.SyncStepResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "did this sync produce anything?" vote, which decides whether a run is
 * marked FAILED.
 *
 * It gained a SKIPPED status when auto-mix discovery became switchable
 * (#335/#344), and that is a genuinely dangerous place to add an enum value: if
 * a skipped step counted as a non-error, "everything failed" becomes
 * unreachable and a completely broken sync would report success just because
 * the user had one step turned off.
 */
class AllAttemptedStepsFailedTest {

    private fun step(status: StepStatus, step: String = "getDailyMixes") =
        SyncStepResult(service = "SPOTIFY", step = step, status = status)

    @Test fun `all errors is a failure`() {
        assertTrue(
            allAttemptedStepsFailed(listOf(step(StepStatus.ERROR), step(StepStatus.ERROR, "getLikedSongs"))),
        )
    }

    @Test fun `a skipped step does not rescue a run where everything else errored`() {
        // The regression this function exists to prevent.
        assertTrue(
            allAttemptedStepsFailed(
                listOf(
                    step(StepStatus.SKIPPED),
                    step(StepStatus.ERROR, "getLikedSongs"),
                    step(StepStatus.ERROR, "getUserPlaylists"),
                ),
            ),
        )
    }

    @Test fun `one success is enough to not be a failure`() {
        assertFalse(
            allAttemptedStepsFailed(listOf(step(StepStatus.ERROR), step(StepStatus.SUCCESS, "getLikedSongs"))),
        )
    }

    @Test fun `EMPTY is not an error — the call ran and returned nothing`() {
        assertFalse(allAttemptedStepsFailed(listOf(step(StepStatus.EMPTY))))
    }

    @Test fun `a run of only skipped steps is not a failure`() {
        // Nothing was attempted, so there is nothing to have failed.
        assertFalse(allAttemptedStepsFailed(listOf(step(StepStatus.SKIPPED))))
    }

    @Test fun `no diagnostics at all is not a failure`() {
        assertFalse(allAttemptedStepsFailed(emptyList()))
    }
}
