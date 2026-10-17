package com.luma.core.data.sync

import com.google.common.truth.Truth.assertThat
import com.luma.core.data.db.entity.SyncHistoryEntity
import com.luma.core.model.SyncDisplayStatus
import com.luma.core.model.SyncState
import org.junit.Test

class SyncDisplayStatusMapperTest {

    @Test
    fun `cancelled state maps to Cancelled display status`() {
        val entity = SyncHistoryEntity(
            status = SyncState.CANCELLED,
            errorMessage = "Cancelled",
            tracksDownloaded = 3,
        )
        assertThat(entity.toDisplayStatus()).isEqualTo(SyncDisplayStatus.Cancelled)
    }
}
