package com.germanchoconta.gymtracker.data.backup

import java.math.BigDecimal
import java.time.Instant

object WorkoutCsvExporter {
    val headers = listOf(
        "workout_id",
        "workout_started_at_epoch_ms",
        "workout_started_at_utc",
        "workout_finished_at_epoch_ms",
        "workout_finished_at_utc",
        "workout_title_snapshot",
        "routine_id",
        "workout_notes",
        "workout_exercise_id",
        "routine_exercise_id",
        "exercise_id",
        "exercise_name_current",
        "exercise_position",
        "exercise_notes",
        "snapshot_target_set_count",
        "snapshot_rep_min",
        "snapshot_rep_max",
        "snapshot_target_rir_tenths",
        "snapshot_target_rir",
        "snapshot_rest_seconds",
        "snapshot_load_increment_grams",
        "snapshot_previous_reference_mode",
        "set_id",
        "set_position",
        "set_type",
        "set_completed",
        "set_completed_at_epoch_ms",
        "set_completed_at_utc",
        "load_grams",
        "load_kg",
        "reps",
        "rir_tenths",
        "rir",
    )

    fun encode(snapshot: BackupSnapshot): ByteArray {
        val normalized = snapshot.normalized()
        val exerciseNames = normalized.exercises.associate { it.id to it.name }
        val workoutExercises = normalized.workoutExercises.groupBy { it.workoutId }
        val sets = normalized.workoutSets.groupBy { it.workoutExerciseId }
        val out = StringBuilder()
        appendRow(out, headers)

        normalized.workouts
            .sortedWith(compareBy({ it.startedAt }, { it.id }))
            .forEach { workout ->
                workoutExercises[workout.id].orEmpty()
                    .sortedWith(compareBy({ it.position }, { it.id }))
                    .forEach { workoutExercise ->
                        sets[workoutExercise.id].orEmpty()
                            .sortedWith(compareBy({ it.position }, { it.id }))
                            .forEach { set ->
                                appendRow(
                                    out,
                                    listOf(
                                        workout.id,
                                        workout.startedAt.toString(),
                                        Instant.ofEpochMilli(workout.startedAt).toString(),
                                        workout.finishedAt?.toString().orEmpty(),
                                        workout.finishedAt?.let { Instant.ofEpochMilli(it).toString() }.orEmpty(),
                                        workout.title,
                                        workout.routineId.orEmpty(),
                                        workout.notes.orEmpty(),
                                        workoutExercise.id,
                                        workoutExercise.routineExerciseId.orEmpty(),
                                        workoutExercise.exerciseId,
                                        exerciseNames[workoutExercise.exerciseId].orEmpty(),
                                        workoutExercise.position.toString(),
                                        workoutExercise.notes.orEmpty(),
                                        workoutExercise.targetSetCount?.toString().orEmpty(),
                                        workoutExercise.repMin?.toString().orEmpty(),
                                        workoutExercise.repMax?.toString().orEmpty(),
                                        workoutExercise.targetRirTenths?.toString().orEmpty(),
                                        workoutExercise.targetRirTenths?.let(::formatTenths).orEmpty(),
                                        workoutExercise.restSeconds?.toString().orEmpty(),
                                        workoutExercise.loadIncrementGrams?.toString().orEmpty(),
                                        workoutExercise.previousReferenceMode.orEmpty(),
                                        set.id,
                                        set.position.toString(),
                                        set.type,
                                        (set.completedAt != null).toString(),
                                        set.completedAt?.toString().orEmpty(),
                                        set.completedAt?.let { Instant.ofEpochMilli(it).toString() }.orEmpty(),
                                        set.loadGrams.toString(),
                                        formatGramsAsKg(set.loadGrams),
                                        set.reps.toString(),
                                        set.rirTenths?.toString().orEmpty(),
                                        set.rirTenths?.let(::formatTenths).orEmpty(),
                                    ),
                                )
                            }
                    }
            }
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    private fun appendRow(out: StringBuilder, values: List<String>) {
        values.forEachIndexed { index, value ->
            if (index > 0) out.append(',')
            out.append(escape(value))
        }
        out.append("\r\n")
    }

    internal fun escape(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\r' || it == '\n' }) return value
        return buildString(value.length + 2) {
            append('"')
            value.forEach { character ->
                if (character == '"') append("\"\"") else append(character)
            }
            append('"')
        }
    }

    internal fun formatGramsAsKg(grams: Long): String =
        BigDecimal.valueOf(grams).movePointLeft(3).stripTrailingZeros().toPlainString()

    internal fun formatTenths(tenths: Int): String =
        BigDecimal.valueOf(tenths.toLong()).movePointLeft(1).stripTrailingZeros().toPlainString()
}
