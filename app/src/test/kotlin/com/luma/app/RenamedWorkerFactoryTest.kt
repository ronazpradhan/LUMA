package com.luma.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RenamedWorkerFactoryTest {

    private val old = "com." + "stash."

    @Test fun `old package and class prefix map to the new names`() {
        assertEquals(
            "com.luma.core.data.sync.workers.LumaMixRefreshWorker",
            RenamedWorkerFactory.currentName(old + "core.data.sync.workers.St" + "ashMixRefreshWorker"),
        )
        assertEquals(
            "com.luma.core.data.sync.workers.DiffWorker",
            RenamedWorkerFactory.currentName(old + "core.data.sync.workers.DiffWorker"),
        )
    }

    @Test fun `current and foreign names pass through`() {
        assertEquals("com.luma.x.FooWorker", RenamedWorkerFactory.currentName("com.luma.x.FooWorker"))
        assertEquals("androidx.work.impl.workers.ConstraintTrackingWorker", RenamedWorkerFactory.currentName("androidx.work.impl.workers.ConstraintTrackingWorker"))
    }
}
