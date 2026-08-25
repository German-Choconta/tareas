package com.germanchoconta.gymtracker.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

class HealthConnectRecoverySource(
    private val context: Context,
) : RecoveryHealthSource {
    private val permissionStringsByType = mapOf(
        RecoveryPermission.SLEEP to HealthPermission.getReadPermission(SleepSessionRecord::class),
        RecoveryPermission.RESTING_HEART_RATE to HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        RecoveryPermission.HRV_RMSSD to HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
    )

    override val requestedPermissionStrings: Set<String> = permissionStringsByType.values.toSet()

    override fun availability(): RecoveryAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> RecoveryAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> RecoveryAvailability.PROVIDER_UPDATE_REQUIRED
        else -> RecoveryAvailability.UNAVAILABLE
    }

    override suspend fun grantedPermissions(): Set<RecoveryPermission> {
        if (availability() != RecoveryAvailability.AVAILABLE) return emptySet()
        val granted = client().permissionController.getGrantedPermissions()
        return permissionStringsByType
            .filterValues { it in granted }
            .keys
    }

    override suspend fun readRawContext(
        day: LocalDate,
        zoneId: ZoneId,
        grantedPermissions: Set<RecoveryPermission>,
    ): RawRecoveryRecords {
        if (availability() != RecoveryAvailability.AVAILABLE || grantedPermissions.isEmpty()) {
            return RawRecoveryRecords()
        }

        val window = RecoveryReadWindow.forDay(day, zoneId)

        val sleepSessions = if (RecoveryPermission.SLEEP in grantedPermissions) {
            readAll(SleepSessionRecord::class, window.sleepReadStart, window.nextDayStart).map(::mapSleep)
        } else {
            emptyList()
        }

        val restingHeartRates = if (RecoveryPermission.RESTING_HEART_RATE in grantedPermissions) {
            readAll(RestingHeartRateRecord::class, window.dayStart, window.nextDayStart).map {
                RawRestingHeartRate(
                    sourcePackage = it.metadata.dataOrigin.packageName,
                    time = it.time,
                    beatsPerMinute = it.beatsPerMinute,
                )
            }
        } else {
            emptyList()
        }

        val hrvRmssd = if (RecoveryPermission.HRV_RMSSD in grantedPermissions) {
            readAll(HeartRateVariabilityRmssdRecord::class, window.dayStart, window.nextDayStart).map {
                RawHrvRmssd(
                    sourcePackage = it.metadata.dataOrigin.packageName,
                    time = it.time,
                    milliseconds = it.heartRateVariabilityMillis,
                )
            }
        } else {
            emptyList()
        }

        return RawRecoveryRecords(
            sleepSessions = sleepSessions,
            restingHeartRates = restingHeartRates,
            hrvRmssd = hrvRmssd,
        )
    }

    override suspend fun disconnect() {
        if (availability() == RecoveryAvailability.AVAILABLE) {
            client().permissionController.revokeAllPermissions()
        }
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    private suspend fun <T : Record> readAll(
        recordType: KClass<T>,
        startTime: Instant,
        endTime: Instant,
    ): List<T> {
        val records = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client().readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    pageToken = pageToken,
                ),
            )
            records += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return records
    }

    private fun mapSleep(record: SleepSessionRecord): RawSleepSession = RawSleepSession(
        sourcePackage = record.metadata.dataOrigin.packageName,
        startTime = record.startTime,
        endTime = record.endTime,
        startZoneOffset = record.startZoneOffset,
        endZoneOffset = record.endZoneOffset,
        stages = record.stages.map { stage ->
            RawSleepStage(
                startTime = stage.startTime,
                endTime = stage.endTime,
                type = when (stage.stage) {
                    SleepSessionRecord.STAGE_TYPE_AWAKE,
                    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
                    -> RawSleepStageType.AWAKE
                    SleepSessionRecord.STAGE_TYPE_SLEEPING -> RawSleepStageType.SLEEPING
                    SleepSessionRecord.STAGE_TYPE_LIGHT -> RawSleepStageType.LIGHT
                    SleepSessionRecord.STAGE_TYPE_DEEP -> RawSleepStageType.DEEP
                    SleepSessionRecord.STAGE_TYPE_REM -> RawSleepStageType.REM
                    else -> RawSleepStageType.UNKNOWN
                },
            )
        },
    )
}
