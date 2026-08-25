package com.germanchoconta.gymtracker.data.backup

import com.germanchoconta.gymtracker.data.local.SetTypes
import com.germanchoconta.gymtracker.data.local.WorkoutEntity
import com.germanchoconta.gymtracker.data.local.WorkoutExerciseEntity
import com.germanchoconta.gymtracker.data.local.WorkoutSetEntity
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableBackupCodecTest {
    @Test
    fun encodeDecodeRoundTripPreservesEveryCanonicalValue() {
        val source = SyntheticBackupFixtures.complex().normalized()

        val encoded = PortableBackupCodec.encode(
            snapshot = source,
            generatedAtEpochMillis = 10_000,
            appVersion = "0.1.0-synthetic",
        )
        val decoded = PortableBackupCodec.decode(encoded)
        val preview = BackupValidator.validate(decoded)

        assertEquals(source, decoded.snapshot)
        assertEquals(2, decoded.metadata.databaseSchemaVersion)
        assertEquals(2, preview.exerciseCount)
        assertEquals(1, preview.routineCount)
        assertEquals(2, preview.workoutCount)
        assertEquals(4, preview.setCount)
        assertEquals(1_000L, preview.earliestWorkoutStartedAt)
        assertEquals(5_000L, preview.latestWorkoutStartedAt)
        assertTrue(preview.hasActiveWorkout)
        assertEquals(123_456L, decoded.snapshot.workoutSets.single { it.id.contains("active-complete") }.loadGrams)
        assertEquals(15, decoded.snapshot.workoutSets.single { it.id.contains("history-a") }.rirTenths)
        assertTrue(decoded.snapshot.exercises.single { it.id.endsWith("b") }.archived)
        assertEquals(null, decoded.snapshot.workouts.single { it.id.contains("history") }.routineId)
        assertEquals(
            2,
            decoded.snapshot.workoutExercises.count {
                it.workoutId == "workout-synthetic-history" && it.exerciseId == "exercise-synthetic-b"
            },
        )
    }

    @Test
    fun payloadAndChecksumAreDeterministicForSameSnapshot() {
        val source = SyntheticBackupFixtures.complex()
        val shuffled = source.copy(
            exercises = source.exercises.shuffled(Random(1)),
            muscles = source.muscles.shuffled(Random(2)),
            exerciseMuscles = source.exerciseMuscles.shuffled(Random(3)),
            routines = source.routines.shuffled(Random(4)),
            routineExercises = source.routineExercises.shuffled(Random(5)),
            workouts = source.workouts.shuffled(Random(6)),
            workoutExercises = source.workoutExercises.shuffled(Random(7)),
            workoutSets = source.workoutSets.shuffled(Random(8)),
        )

        assertEquals(
            PortableBackupCodec.canonicalPayload(source),
            PortableBackupCodec.canonicalPayload(shuffled),
        )
        assertTrue(
            PortableBackupCodec.encode(source, 10_000, "synthetic").contentEquals(
                PortableBackupCodec.encode(shuffled, 10_000, "synthetic"),
            ),
        )
    }

    @Test
    fun unknownOrFutureVersionFailsBeforeRestore() {
        val valid = PortableBackupCodec.encode(SyntheticBackupFixtures.complex(), 10_000, "synthetic")
            .toString(Charsets.UTF_8)
        val future = valid.replace("\"formatVersion\":1", "\"formatVersion\":2")

        val error = assertThrows(BackupFormatException::class.java) {
            PortableBackupCodec.decode(future.toByteArray(Charsets.UTF_8))
        }
        assertTrue(error.message.orEmpty().contains("incompatible"))
    }

    @Test
    fun futureDatabaseSchemaFailsBeforeRestore() {
        val valid = PortableBackupCodec.encode(SyntheticBackupFixtures.complex(), 10_000, "synthetic")
            .toString(Charsets.UTF_8)
        val future = valid.replace("\"databaseSchemaVersion\":2", "\"databaseSchemaVersion\":3")

        assertThrows(BackupFormatException::class.java) {
            PortableBackupCodec.decode(future.toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun malformedMissingFieldsAndOverflowFailSafely() {
        assertThrows(BackupFormatException::class.java) {
            PortableBackupCodec.decode("{".toByteArray())
        }

        val valid = PortableBackupCodec.encode(SyntheticBackupFixtures.complex(), 10_000, "synthetic")
            .toString(Charsets.UTF_8)
        val missing = valid.replace("\"format\":\"gymtracker-backup\",", "")
        assertThrows(BackupFormatException::class.java) {
            PortableBackupCodec.decode(missing.toByteArray(Charsets.UTF_8))
        }

        val overflow = valid.replace("\"loadGrams\":42500", "\"loadGrams\":999999999999999999999999999")
        assertThrows(BackupFormatException::class.java) {
            PortableBackupCodec.decode(overflow.toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun checksumMismatchFailsSafely() {
        val valid = PortableBackupCodec.encode(SyntheticBackupFixtures.complex(), 10_000, "synthetic")
            .toString(Charsets.UTF_8)
        val modified = valid.replace("\"loadGrams\":42500", "\"loadGrams\":42501")

        val error = assertThrows(BackupFormatException::class.java) {
            PortableBackupCodec.decode(modified.toByteArray(Charsets.UTF_8))
        }
        assertTrue(error.message.orEmpty().contains("checksum"))
    }

    @Test
    fun duplicateIdsBrokenReferencesEnumsAndRangesAreRejected() {
        val source = SyntheticBackupFixtures.complex().normalized()

        assertThrows(BackupValidationException::class.java) {
            BackupValidator.validate(
                SyntheticBackupFixtures.decoded(
                    source.copy(exercises = source.exercises + source.exercises.first()),
                ),
            )
        }
        assertThrows(BackupValidationException::class.java) {
            BackupValidator.validate(
                SyntheticBackupFixtures.decoded(
                    source.copy(
                        workoutSets = source.workoutSets.mapIndexed { index, set ->
                            if (index == 0) set.copy(workoutExerciseId = "missing-synthetic-parent") else set
                        },
                    ),
                ),
            )
        }
        assertThrows(BackupValidationException::class.java) {
            BackupValidator.validate(
                SyntheticBackupFixtures.decoded(
                    source.copy(
                        workoutSets = source.workoutSets.mapIndexed { index, set ->
                            if (index == 0) set.copy(type = "UNKNOWN_SYNTHETIC_TYPE") else set
                        },
                    ),
                ),
            )
        }
        assertThrows(BackupValidationException::class.java) {
            BackupValidator.validate(
                SyntheticBackupFixtures.decoded(
                    source.copy(
                        workoutSets = source.workoutSets.mapIndexed { index, set ->
                            if (index == 0) set.copy(rirTenths = 101) else set
                        },
                    ),
                ),
            )
        }
    }

    @Test
    fun impossiblePositionsAndTimestampsAreRejected() {
        val source = SyntheticBackupFixtures.complex().normalized()
        val occurrence = source.workoutExercises.first { it.workoutId == "workout-synthetic-history" }
        val sibling = source.workoutExercises.first {
            it.workoutId == occurrence.workoutId && it.id != occurrence.id
        }

        assertThrows(BackupValidationException::class.java) {
            BackupValidator.validate(
                SyntheticBackupFixtures.decoded(
                    source.copy(
                        workoutExercises = source.workoutExercises.map {
                            if (it.id == sibling.id) it.copy(position = occurrence.position) else it
                        },
                    ),
                ),
            )
        }

        val history = source.workouts.first { it.id == "workout-synthetic-history" }
        assertThrows(BackupValidationException::class.java) {
            BackupValidator.validate(
                SyntheticBackupFixtures.decoded(
                    source.copy(
                        workouts = source.workouts.map {
                            if (it.id == history.id) it.copy(finishedAt = it.startedAt - 1) else it
                        },
                    ),
                ),
            )
        }
    }

    @Test
    fun multiYearSyntheticHistoryRemainsUntruncated() {
        val base = SyntheticBackupFixtures.complex().normalized()
        val exerciseId = base.exercises.first().id
        val extraWorkouts = ArrayList<WorkoutEntity>()
        val extraOccurrences = ArrayList<WorkoutExerciseEntity>()
        val extraSets = ArrayList<WorkoutSetEntity>()
        repeat(2_000) { index ->
            val started = 86_400_000L * index + 100_000L
            val workoutId = "workout-multiyear-$index"
            val occurrenceId = "workout-exercise-multiyear-$index"
            extraWorkouts += WorkoutEntity(
                id = workoutId,
                title = "Synthetic year-spanning workout $index",
                startedAt = started,
                finishedAt = started + 10_000L,
            )
            extraOccurrences += WorkoutExerciseEntity(
                id = occurrenceId,
                workoutId = workoutId,
                exerciseId = exerciseId,
                position = 0,
            )
            extraSets += WorkoutSetEntity(
                id = "set-multiyear-$index",
                workoutExerciseId = occurrenceId,
                position = 0,
                type = SetTypes.WORK,
                loadGrams = 50_000L + index,
                reps = 8,
                rirTenths = 10,
                completedAt = started + 5_000L,
            )
        }
        val large = base.copy(
            workouts = base.workouts + extraWorkouts,
            workoutExercises = base.workoutExercises + extraOccurrences,
            workoutSets = base.workoutSets + extraSets,
        )

        val decoded = PortableBackupCodec.decode(
            PortableBackupCodec.encode(large, 10_000, "synthetic-large"),
        )
        val preview = BackupValidator.validate(decoded)

        assertEquals(2_002, preview.workoutCount)
        assertEquals(2_004, preview.setCount)
        assertEquals(large.normalized(), decoded.snapshot)
    }
}
