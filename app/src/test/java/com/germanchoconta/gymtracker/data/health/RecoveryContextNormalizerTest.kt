package com.germanchoconta.gymtracker.data.health

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryContextNormalizerTest {
    @Test
    fun readWindowUsesCalendarBoundariesAcrossDst() {
        val zone = ZoneId.of("America/New_York")

        val spring = RecoveryReadWindow.forDay(LocalDate.of(2026, 3, 8), zone)
        val fall = RecoveryReadWindow.forDay(LocalDate.of(2026, 11, 1), zone)

        assertEquals(23L, Duration.between(spring.dayStart, spring.nextDayStart).toHours())
        assertEquals(25L, Duration.between(fall.dayStart, fall.nextDayStart).toHours())
        assertEquals(LocalDate.of(2026, 3, 7).atStartOfDay(zone).toInstant(), spring.sleepReadStart)
    }

    @Test
    fun sleepCrossingMidnightBelongsToItsRecordedEndDayAndStagesAreClipped() {
        val fallbackZone = ZoneId.of("America/Bogota")
        val day = LocalDate.of(2026, 8, 25)
        val start = Instant.parse("2026-08-25T02:00:00Z")
        val end = Instant.parse("2026-08-25T08:00:00Z")
        val raw = RawRecoveryRecords(
            sleepSessions = listOf(
                RawSleepSession(
                    sourcePackage = "synthetic.sleep.source",
                    startTime = start,
                    endTime = end,
                    startZoneOffset = ZoneOffset.ofHours(-4),
                    endZoneOffset = ZoneOffset.ofHours(-4),
                    stages = listOf(
                        RawSleepStage(
                            startTime = start.minusSeconds(600),
                            endTime = start.plusSeconds(3_600),
                            type = RawSleepStageType.LIGHT,
                        ),
                        RawSleepStage(
                            startTime = start.plusSeconds(3_600),
                            endTime = start.plusSeconds(10_800),
                            type = RawSleepStageType.DEEP,
                        ),
                        RawSleepStage(
                            startTime = start.plusSeconds(10_800),
                            endTime = end.plusSeconds(600),
                            type = RawSleepStageType.REM,
                        ),
                    ),
                ),
            ),
        )

        val context = RecoveryContextNormalizer.normalize(raw, day, fallbackZone)

        assertEquals(1, context.sleepSessions.size)
        val sleep = context.sleepSessions.single()
        assertEquals(Duration.ofHours(6), sleep.duration)
        assertEquals(Duration.ofHours(1), sleep.stages.light)
        assertEquals(Duration.ofHours(2), sleep.stages.deep)
        assertEquals(Duration.ofHours(3), sleep.stages.rem)
    }

    @Test
    fun explicitSleepEndOffsetWinsOverCurrentTravelZone() {
        val instant = Instant.parse("2026-08-25T04:30:00Z")
        val session = RawSleepSession(
            sourcePackage = "synthetic.travel.source",
            startTime = instant.minusSeconds(7_200),
            endTime = instant,
            startZoneOffset = ZoneOffset.ofHours(-5),
            endZoneOffset = ZoneOffset.ofHours(-5),
            stages = emptyList(),
        )

        val bogotaDay = RecoveryContextNormalizer.normalize(
            RawRecoveryRecords(sleepSessions = listOf(session)),
            LocalDate.of(2026, 8, 24),
            ZoneId.of("Asia/Tokyo"),
        )

        assertEquals(1, bogotaDay.sleepSessions.size)
    }

    @Test
    fun multipleOriginsStaySeparateAndLatestInstantRecordWinsPerOrigin() {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 8, 25)
        val raw = RawRecoveryRecords(
            sleepSessions = listOf(
                sleep("synthetic.source.a", "2026-08-25T00:00:00Z", "2026-08-25T06:00:00Z"),
                sleep("synthetic.source.b", "2026-08-25T01:00:00Z", "2026-08-25T07:00:00Z"),
            ),
            restingHeartRates = listOf(
                RawRestingHeartRate("synthetic.source.a", Instant.parse("2026-08-25T05:00:00Z"), 61),
                RawRestingHeartRate("synthetic.source.a", Instant.parse("2026-08-25T08:00:00Z"), 58),
                RawRestingHeartRate("synthetic.source.b", Instant.parse("2026-08-25T07:00:00Z"), 63),
            ),
            hrvRmssd = listOf(
                RawHrvRmssd("synthetic.source.a", Instant.parse("2026-08-25T04:00:00Z"), 42.5),
                RawHrvRmssd("synthetic.source.a", Instant.parse("2026-08-25T09:00:00Z"), 45.0),
                RawHrvRmssd("synthetic.source.b", Instant.parse("2026-08-25T06:00:00Z"), 39.0),
            ),
        )

        val context = RecoveryContextNormalizer.normalize(raw, day, zone)

        assertEquals(2, context.sleepSessions.size)
        assertEquals(listOf("synthetic.source.a", "synthetic.source.b"), context.restingHeartRates.map { it.sourcePackage })
        assertEquals(58L, context.restingHeartRates.first { it.sourcePackage == "synthetic.source.a" }.beatsPerMinute)
        assertEquals(45.0, context.hrvRmssd.first { it.sourcePackage == "synthetic.source.a" }.milliseconds, 0.0)
        assertTrue(context.sleepSessions.map { it.sourcePackage }.containsAll(listOf("synthetic.source.a", "synthetic.source.b")))
    }

    private fun sleep(source: String, start: String, end: String) = RawSleepSession(
        sourcePackage = source,
        startTime = Instant.parse(start),
        endTime = Instant.parse(end),
        startZoneOffset = ZoneOffset.UTC,
        endZoneOffset = ZoneOffset.UTC,
        stages = emptyList(),
    )
}
