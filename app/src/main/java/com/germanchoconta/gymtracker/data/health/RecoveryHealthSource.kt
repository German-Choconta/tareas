package com.germanchoconta.gymtracker.data.health

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

enum class RecoveryAvailability {
    AVAILABLE,
    PROVIDER_UPDATE_REQUIRED,
    UNAVAILABLE,
}

enum class RecoveryPermission {
    SLEEP,
    RESTING_HEART_RATE,
    HRV_RMSSD,
}

enum class RawSleepStageType {
    UNKNOWN,
    AWAKE,
    SLEEPING,
    LIGHT,
    DEEP,
    REM,
}

data class RawSleepStage(
    val startTime: Instant,
    val endTime: Instant,
    val type: RawSleepStageType,
)

data class RawSleepSession(
    val sourcePackage: String,
    val startTime: Instant,
    val endTime: Instant,
    val startZoneOffset: ZoneOffset?,
    val endZoneOffset: ZoneOffset?,
    val stages: List<RawSleepStage>,
)

data class RawRestingHeartRate(
    val sourcePackage: String,
    val time: Instant,
    val beatsPerMinute: Long,
)

data class RawHrvRmssd(
    val sourcePackage: String,
    val time: Instant,
    val milliseconds: Double,
)

data class RawRecoveryRecords(
    val sleepSessions: List<RawSleepSession> = emptyList(),
    val restingHeartRates: List<RawRestingHeartRate> = emptyList(),
    val hrvRmssd: List<RawHrvRmssd> = emptyList(),
)

data class SleepStageDurations(
    val awake: Duration = Duration.ZERO,
    val sleepingUnspecified: Duration = Duration.ZERO,
    val light: Duration = Duration.ZERO,
    val deep: Duration = Duration.ZERO,
    val rem: Duration = Duration.ZERO,
) {
    val hasKnownStages: Boolean
        get() = awake != Duration.ZERO ||
            sleepingUnspecified != Duration.ZERO ||
            light != Duration.ZERO ||
            deep != Duration.ZERO ||
            rem != Duration.ZERO
}

data class RecoverySleepSession(
    val sourcePackage: String,
    val startTime: Instant,
    val endTime: Instant,
    val duration: Duration,
    val stages: SleepStageDurations,
)

data class RecoveryRestingHeartRate(
    val sourcePackage: String,
    val time: Instant,
    val beatsPerMinute: Long,
)

data class RecoveryHrvRmssd(
    val sourcePackage: String,
    val time: Instant,
    val milliseconds: Double,
)

data class RecoveryContext(
    val day: LocalDate,
    val zoneId: ZoneId,
    val sleepSessions: List<RecoverySleepSession>,
    val restingHeartRates: List<RecoveryRestingHeartRate>,
    val hrvRmssd: List<RecoveryHrvRmssd>,
) {
    val isEmpty: Boolean
        get() = sleepSessions.isEmpty() && restingHeartRates.isEmpty() && hrvRmssd.isEmpty()
}

interface RecoveryHealthSource {
    val requestedPermissionStrings: Set<String>

    fun availability(): RecoveryAvailability

    suspend fun grantedPermissions(): Set<RecoveryPermission>

    suspend fun readRawContext(
        day: LocalDate,
        zoneId: ZoneId,
        grantedPermissions: Set<RecoveryPermission>,
    ): RawRecoveryRecords

    suspend fun disconnect()
}

object RecoveryContextNormalizer {
    fun normalize(
        raw: RawRecoveryRecords,
        day: LocalDate,
        zoneId: ZoneId,
    ): RecoveryContext {
        val sleepSessions = raw.sleepSessions
            .asSequence()
            .filter { sleepEndDate(it, zoneId) == day }
            .filter { it.endTime.isAfter(it.startTime) }
            .map { session ->
                RecoverySleepSession(
                    sourcePackage = session.sourcePackage,
                    startTime = session.startTime,
                    endTime = session.endTime,
                    duration = Duration.between(session.startTime, session.endTime),
                    stages = summarizeStages(session),
                )
            }
            .sortedWith(compareBy<RecoverySleepSession> { it.endTime }.thenBy { it.sourcePackage })
            .toList()

        val restingHeartRates = raw.restingHeartRates
            .asSequence()
            .filter { it.time.atZone(zoneId).toLocalDate() == day }
            .groupBy { it.sourcePackage }
            .values
            .mapNotNull { records -> records.maxByOrNull(RawRestingHeartRate::time) }
            .map {
                RecoveryRestingHeartRate(
                    sourcePackage = it.sourcePackage,
                    time = it.time,
                    beatsPerMinute = it.beatsPerMinute,
                )
            }
            .sortedBy { it.sourcePackage }

        val hrvRmssd = raw.hrvRmssd
            .asSequence()
            .filter { it.time.atZone(zoneId).toLocalDate() == day }
            .groupBy { it.sourcePackage }
            .values
            .mapNotNull { records -> records.maxByOrNull(RawHrvRmssd::time) }
            .map {
                RecoveryHrvRmssd(
                    sourcePackage = it.sourcePackage,
                    time = it.time,
                    milliseconds = it.milliseconds,
                )
            }
            .sortedBy { it.sourcePackage }

        return RecoveryContext(
            day = day,
            zoneId = zoneId,
            sleepSessions = sleepSessions,
            restingHeartRates = restingHeartRates,
            hrvRmssd = hrvRmssd,
        )
    }

    private fun sleepEndDate(session: RawSleepSession, fallbackZone: ZoneId): LocalDate =
        session.endZoneOffset
            ?.let { session.endTime.atOffset(it).toLocalDate() }
            ?: session.endTime.atZone(fallbackZone).toLocalDate()

    private fun summarizeStages(session: RawSleepSession): SleepStageDurations {
        var awake = Duration.ZERO
        var sleeping = Duration.ZERO
        var light = Duration.ZERO
        var deep = Duration.ZERO
        var rem = Duration.ZERO

        session.stages.forEach { stage ->
            val boundedStart = maxOf(stage.startTime, session.startTime)
            val boundedEnd = minOf(stage.endTime, session.endTime)
            if (!boundedEnd.isAfter(boundedStart)) return@forEach
            val duration = Duration.between(boundedStart, boundedEnd)
            when (stage.type) {
                RawSleepStageType.AWAKE -> awake = awake.plus(duration)
                RawSleepStageType.SLEEPING -> sleeping = sleeping.plus(duration)
                RawSleepStageType.LIGHT -> light = light.plus(duration)
                RawSleepStageType.DEEP -> deep = deep.plus(duration)
                RawSleepStageType.REM -> rem = rem.plus(duration)
                RawSleepStageType.UNKNOWN -> Unit
            }
        }

        return SleepStageDurations(
            awake = awake,
            sleepingUnspecified = sleeping,
            light = light,
            deep = deep,
            rem = rem,
        )
    }
}
