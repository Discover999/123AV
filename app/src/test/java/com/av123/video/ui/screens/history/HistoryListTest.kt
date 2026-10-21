package com.av123.video.ui.screens.history

import com.av123.video.data.model.Video
import com.av123.video.data.model.WatchRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class HistoryListTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 22)

    private fun record(
        id: String,
        date: LocalDate,
        positionMs: Long,
        durationMs: Long
    ): WatchRecord = WatchRecord(
        video = Video(id = id, title = id, category = "剧情"),
        positionMs = positionMs,
        durationMs = durationMs,
        watchedAt = date.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
    )

    @Test
    fun `finished requires progress at least 90 percent`() {
        assertTrue(record("a", today, 900L, 1000L).isFinished())
        assertFalse(record("b", today, 899L, 1000L).isFinished())
        assertFalse(record("c", today, 0L, 0L).isFinished())
        assertFalse(record("d", today, 0L, 1000L).isFinished())
    }

    @Test
    fun `filters split unfinished and finished by progress`() {
        val records = listOf(
            record("finished", today, 950L, 1000L),
            record("unfinished", today, 500L, 1000L),
            record("unstarted", today, 0L, 1000L),
            record("unknown", today, 0L, 0L)
        )
        assertEquals(4, filterHistory(records, HistoryFilter.ALL).size)
        assertEquals(
            listOf("unfinished"),
            filterHistory(records, HistoryFilter.UNFINISHED).map { it.video.id }
        )
        assertEquals(
            listOf("finished"),
            filterHistory(records, HistoryFilter.FINISHED).map { it.video.id }
        )
    }

    @Test
    fun `groups keep today yesterday earlier order and record order`() {
        val records = listOf(
            record("today-late", today, 100L, 1000L),
            record("yesterday", today.minusDays(1), 100L, 1000L),
            record("today-early", today, 200L, 1000L),
            record("earlier", today.minusDays(3), 100L, 1000L)
        )
        val sections = groupHistory(records, today, zone)
        assertEquals(
            listOf(HistoryGroup.TODAY, HistoryGroup.YESTERDAY, HistoryGroup.EARLIER),
            sections.map { it.group }
        )
        assertEquals(
            listOf("today-late", "today-early"),
            sections[0].records.map { it.video.id }
        )
        assertEquals(listOf("yesterday"), sections[1].records.map { it.video.id })
        assertEquals(listOf("earlier"), sections[2].records.map { it.video.id })
    }

    @Test
    fun `future timestamps fall into today`() {
        val sections = groupHistory(
            records = listOf(record("future", today.plusDays(1), 0L, 0L)),
            today = today,
            zone = zone
        )
        assertEquals(1, sections.size)
        assertEquals(HistoryGroup.TODAY, sections[0].group)
    }
}
