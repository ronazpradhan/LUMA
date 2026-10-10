package com.stash.core.data.sync.workers

import androidx.work.NetworkType
import com.stash.core.model.DownloadNetworkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the [constraintsForManualTrigger] helper — drops charging
 * requirement compared to [constraintsFor], otherwise honors the user's
 * [DownloadNetworkMode] preference for cellular gating.
 */
class DownloadConstraintsTest {

    @Test fun `constraintsForManualTrigger ignores mode — always CONNECTED + battery-not-low + no charging`() {
        for (mode in DownloadNetworkMode.values()) {
            val constraints = constraintsForManualTrigger(mode)
            assertEquals("mode=$mode", NetworkType.CONNECTED, constraints.requiredNetworkType)
            assertFalse("mode=$mode", constraints.requiresCharging())
            assertTrue("mode=$mode", constraints.requiresBatteryNotLow())
        }
    }
}
