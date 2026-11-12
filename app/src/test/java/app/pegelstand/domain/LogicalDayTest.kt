package app.pegelstand.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class LogicalDayTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun at(dateTime: String, zone: ZoneId = berlin): Instant =
        LocalDateTime.parse(dateTime).atZone(zone).toInstant()

    @Test
    fun `03_59 belongs to the previous day, 04_00 starts the new day`() {
        assertEquals(LocalDate.of(2026, 6, 9), logicalDay(at("2026-06-10T03:59:59.999"), berlin))
        assertEquals(LocalDate.of(2026, 6, 10), logicalDay(at("2026-06-10T04:00:00"), berlin))
    }

    @Test
    fun `glass after midnight counts for the evening before`() {
        assertEquals(LocalDate.of(2026, 6, 9), logicalDay(at("2026-06-10T01:30"), berlin))
        assertEquals(LocalDate.of(2026, 6, 9), logicalDay(at("2026-06-09T23:59"), berlin))
    }

    @Test
    fun `spring forward day starts at 04_00 local, not four elapsed hours after midnight`() {
        // 2026-03-29: 02:00 CET jumps to 03:00 CEST in Berlin.
        assertEquals(LocalDate.of(2026, 3, 28), logicalDay(at("2026-03-29T03:59"), berlin))
        assertEquals(LocalDate.of(2026, 3, 29), logicalDay(at("2026-03-29T04:00"), berlin))
        assertEquals(LocalDate.of(2026, 3, 29), logicalDay(at("2026-03-29T04:30"), berlin))
        assertEquals(at("2026-03-29T04:00"), LogicalDay.startOf(LocalDate.of(2026, 3, 29), berlin))
    }

    @Test
    fun `fall back day keeps both 02_xx hours in the previous day and starts at 04_00 local`() {
        // 2026-10-25: 03:00 CEST falls back to 02:00 CET; 02:30 happens twice.
        val firstHalfPast2 = Instant.parse("2026-10-25T00:30:00Z") // 02:30 CEST
        val secondHalfPast2 = Instant.parse("2026-10-25T01:30:00Z") // 02:30 CET
        assertEquals(LocalDate.of(2026, 10, 24), logicalDay(firstHalfPast2, berlin))
        assertEquals(LocalDate.of(2026, 10, 24), logicalDay(secondHalfPast2, berlin))
        assertEquals(LocalDate.of(2026, 10, 24), logicalDay(at("2026-10-25T03:59"), berlin))
        assertEquals(LocalDate.of(2026, 10, 25), logicalDay(at("2026-10-25T04:00"), berlin))
    }

    @Test
    fun `logical day of 25 hour and 23 hour days`() {
        val fallBack = LocalDate.of(2026, 10, 24)
        val springForward = LocalDate.of(2026, 3, 28)
        assertEquals(25L * 3600, (LogicalDay.startOf(fallBack.plusDays(1), berlin).epochSecond - LogicalDay.startOf(fallBack, berlin).epochSecond))
        assertEquals(23L * 3600, (LogicalDay.startOf(springForward.plusDays(1), berlin).epochSecond - LogicalDay.startOf(springForward, berlin).epochSecond))
    }

    @Test
    fun `next start is the following 04_00, also from just before the boundary`() {
        assertEquals(at("2026-06-10T04:00"), LogicalDay.nextStart(at("2026-06-10T03:59"), berlin))
        assertEquals(at("2026-06-11T04:00"), LogicalDay.nextStart(at("2026-06-10T04:00"), berlin))
        assertEquals(at("2026-06-11T04:00"), LogicalDay.nextStart(at("2026-06-10T23:00"), berlin))
    }

    @Test
    fun `same instant falls on different logical days in different zones`() {
        val instant = Instant.parse("2026-06-10T03:00:00Z")
        assertEquals(LocalDate.of(2026, 6, 9), logicalDay(instant, ZoneOffset.UTC))
        assertEquals(LocalDate.of(2026, 6, 10), logicalDay(instant, ZoneOffset.ofHours(2)))
    }
}
