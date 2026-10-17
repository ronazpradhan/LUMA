package com.luma.feature.library

import com.google.common.truth.Truth.assertThat
import com.luma.core.model.Track
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HistoryGroupingTest {

    private val today = LocalDate.of(2026, 10, 17) // a Saturday
    private fun playedOn(day: LocalDate, id: Long) = Track(
        id = id, title = "t$id", artist = "a",
        lastPlayed = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
    )

    @Test fun `groups newest first into today, yesterday, weekdays and earlier`() {
        val tracks = listOf(
            playedOn(today, 1), playedOn(today, 2),
            playedOn(today.minusDays(1), 3),
            playedOn(today.minusDays(3), 4),
            playedOn(today.minusDays(30), 5),
        )
        val days = HistoryViewModel.groupByDay(tracks, today, ZoneOffset.UTC)
        assertThat(days.map { it.label }).containsExactly("Today", "Yesterday", "Wednesday", "Earlier").inOrder()
        assertThat(days.first().tracks.map { it.id }).containsExactly(1L, 2L).inOrder()
    }
}
