package com.germanchoconta.gymtracker.data.backup

import com.germanchoconta.gymtracker.data.local.MuscleRoles
import com.germanchoconta.gymtracker.data.local.PreviousReferenceModes
import com.germanchoconta.gymtracker.data.local.SetTypes

object BackupValidator {
    private const val MAX_REPS = 1_000
    private const val MAX_RIR_TENTHS = 100
    private const val MAX_SET_COUNT = 1_000
    private const val MAX_REST_SECONDS = 86_400

    fun validate(decoded: DecodedBackup): BackupPreview {
        val metadata = decoded.metadata
        val snapshot = decoded.snapshot

        requireValid(metadata.generatedAtEpochMillis >= 0L, "La fecha de generación es inválida.")
        requireValid(metadata.appVersion.isNotBlank(), "La versión de la app está vacía.")
        requireValid(
            metadata.appVersion.length <= BackupFormat.MAX_NAME_LENGTH,
            "La versión de la app es demasiado larga.",
        )
        requireValid(
            metadata.databaseSchemaVersion in 1..BackupFormat.DATABASE_SCHEMA_VERSION,
            "El schema de base de datos del backup es incompatible.",
        )

        requireCount("ejercicios", snapshot.exercises.size, BackupFormat.MAX_EXERCISES)
        requireCount("músculos", snapshot.muscles.size, BackupFormat.MAX_MUSCLES)
        requireCount("relaciones ejercicio-músculo", snapshot.exerciseMuscles.size, BackupFormat.MAX_EXERCISE_MUSCLES)
        requireCount("rutinas", snapshot.routines.size, BackupFormat.MAX_ROUTINES)
        requireCount("ejercicios de rutina", snapshot.routineExercises.size, BackupFormat.MAX_ROUTINE_EXERCISES)
        requireCount("workouts", snapshot.workouts.size, BackupFormat.MAX_WORKOUTS)
        requireCount("ejercicios de workout", snapshot.workoutExercises.size, BackupFormat.MAX_WORKOUT_EXERCISES)
        requireCount("sets", snapshot.workoutSets.size, BackupFormat.MAX_WORKOUT_SETS)

        ensureUniqueIds("ejercicio", snapshot.exercises.map { it.id })
        ensureUniqueIds("músculo", snapshot.muscles.map { it.id })
        ensureUniqueIds("rutina", snapshot.routines.map { it.id })
        ensureUniqueIds("ejercicio de rutina", snapshot.routineExercises.map { it.id })
        ensureUniqueIds("workout", snapshot.workouts.map { it.id })
        ensureUniqueIds("ejercicio de workout", snapshot.workoutExercises.map { it.id })
        ensureUniqueIds("set", snapshot.workoutSets.map { it.id })

        val exerciseIds = snapshot.exercises.mapTo(hashSetOf()) { it.id }
        val muscleIds = snapshot.muscles.mapTo(hashSetOf()) { it.id }
        val routineIds = snapshot.routines.mapTo(hashSetOf()) { it.id }
        val routineExerciseIds = snapshot.routineExercises.mapTo(hashSetOf()) { it.id }
        val workoutIds = snapshot.workouts.mapTo(hashSetOf()) { it.id }
        val workoutExerciseIds = snapshot.workoutExercises.mapTo(hashSetOf()) { it.id }

        snapshot.exercises.forEach { exercise ->
            requireId(exercise.id, "exercise.id")
            requireName(exercise.name, "exercise.name")
            exercise.equipment?.let { requireName(it, "exercise.equipment", allowBlank = true) }
            exercise.notes?.let { requireNotes(it, "exercise.notes") }
            exercise.defaultRepMin?.let { requireValid(it in 1..MAX_REPS, "defaultRepMin fuera de rango.") }
            exercise.defaultRepMax?.let { requireValid(it in 1..MAX_REPS, "defaultRepMax fuera de rango.") }
            if (exercise.defaultRepMin != null && exercise.defaultRepMax != null) {
                requireValid(exercise.defaultRepMin <= exercise.defaultRepMax, "Rango de reps por defecto inválido.")
            }
            exercise.defaultTargetRirTenths?.let {
                requireValid(it in 0..MAX_RIR_TENTHS, "defaultTargetRirTenths fuera de rango.")
            }
            exercise.defaultRestSeconds?.let {
                requireValid(it in 0..MAX_REST_SECONDS, "defaultRestSeconds fuera de rango.")
            }
            exercise.defaultLoadIncrementGrams?.let {
                requireValid(it >= 0L, "defaultLoadIncrementGrams no puede ser negativo.")
            }
        }

        val muscleNames = hashSetOf<String>()
        snapshot.muscles.forEach { muscle ->
            requireId(muscle.id, "muscle.id")
            requireName(muscle.name, "muscle.name")
            requireValid(muscleNames.add(muscle.name), "Hay nombres de músculo duplicados.")
        }

        val exerciseMuscleKeys = hashSetOf<Pair<String, String>>()
        snapshot.exerciseMuscles.forEach { link ->
            requireId(link.exerciseId, "exerciseMuscle.exerciseId")
            requireId(link.muscleId, "exerciseMuscle.muscleId")
            requireValid(link.exerciseId in exerciseIds, "Relación a ejercicio inexistente.")
            requireValid(link.muscleId in muscleIds, "Relación a músculo inexistente.")
            requireValid(
                exerciseMuscleKeys.add(link.exerciseId to link.muscleId),
                "Hay relaciones ejercicio-músculo duplicadas.",
            )
            requireValid(
                link.role == MuscleRoles.PRIMARY || link.role == MuscleRoles.SECONDARY,
                "Rol de músculo desconocido: ${link.role}.",
            )
        }

        snapshot.routines.forEach { routine ->
            requireId(routine.id, "routine.id")
            requireName(routine.name, "routine.name")
            requireValid(routine.position >= 0, "routine.position no puede ser negativo.")
            routine.notes?.let { requireNotes(it, "routine.notes") }
        }

        val routinePositions = hashSetOf<Pair<String, Int>>()
        snapshot.routineExercises.forEach { item ->
            requireId(item.id, "routineExercise.id")
            requireId(item.routineId, "routineExercise.routineId")
            requireId(item.exerciseId, "routineExercise.exerciseId")
            requireValid(item.routineId in routineIds, "Ejercicio de rutina apunta a una rutina inexistente.")
            requireValid(item.exerciseId in exerciseIds, "Ejercicio de rutina apunta a un ejercicio inexistente.")
            requireValid(item.position >= 0, "routineExercise.position no puede ser negativo.")
            requireValid(
                routinePositions.add(item.routineId to item.position),
                "Hay posiciones duplicadas dentro de una rutina.",
            )
            requireValid(item.targetSetCount in 1..MAX_SET_COUNT, "targetSetCount fuera de rango.")
            requireValid(item.repMin in 1..MAX_REPS, "repMin fuera de rango.")
            requireValid(item.repMax in 1..MAX_REPS, "repMax fuera de rango.")
            requireValid(item.repMin <= item.repMax, "Rango de reps de rutina inválido.")
            item.targetRirTenths?.let { requireValid(it in 0..MAX_RIR_TENTHS, "targetRirTenths fuera de rango.") }
            requireValid(item.restSeconds in 0..MAX_REST_SECONDS, "restSeconds fuera de rango.")
            requireValid(item.loadIncrementGrams >= 0L, "loadIncrementGrams no puede ser negativo.")
            requireValid(isPreviousMode(item.previousReferenceMode), "Modo PREVIOUS desconocido.")
        }

        val activeWorkouts = snapshot.workouts.count { it.finishedAt == null }
        requireValid(activeWorkouts <= 1, "El backup contiene más de un workout activo.")
        snapshot.workouts.forEach { workout ->
            requireId(workout.id, "workout.id")
            workout.routineId?.let {
                requireId(it, "workout.routineId")
                requireValid(it in routineIds, "Workout apunta a una rutina inexistente.")
            }
            requireName(workout.title, "workout.title")
            requireValid(workout.startedAt >= 0L, "workout.startedAt no puede ser negativo.")
            workout.finishedAt?.let {
                requireValid(it >= workout.startedAt, "Workout termina antes de comenzar.")
            }
            workout.notes?.let { requireNotes(it, "workout.notes") }
            workout.restTimerEndsAt?.let {
                requireValid(it >= workout.startedAt, "Rest timer anterior al inicio del workout.")
            }
            workout.restTimerWorkoutExerciseId?.let { requireId(it, "workout.restTimerWorkoutExerciseId") }
        }

        val workoutPositions = hashSetOf<Pair<String, Int>>()
        val workoutById = snapshot.workouts.associateBy { it.id }
        val workoutExerciseById = snapshot.workoutExercises.associateBy { it.id }
        snapshot.workoutExercises.forEach { item ->
            requireId(item.id, "workoutExercise.id")
            requireId(item.workoutId, "workoutExercise.workoutId")
            requireId(item.exerciseId, "workoutExercise.exerciseId")
            requireValid(item.workoutId in workoutIds, "Ejercicio de workout apunta a un workout inexistente.")
            requireValid(item.exerciseId in exerciseIds, "Ejercicio de workout apunta a un ejercicio inexistente.")
            item.routineExerciseId?.let {
                requireId(it, "workoutExercise.routineExerciseId")
                requireValid(it in routineExerciseIds, "Snapshot apunta a un ejercicio de rutina inexistente.")
            }
            requireValid(item.position >= 0, "workoutExercise.position no puede ser negativo.")
            requireValid(
                workoutPositions.add(item.workoutId to item.position),
                "Hay posiciones de ejercicio duplicadas dentro de un workout.",
            )
            item.notes?.let { requireNotes(it, "workoutExercise.notes") }
            item.targetSetCount?.let { requireValid(it in 1..MAX_SET_COUNT, "Snapshot targetSetCount fuera de rango.") }
            item.repMin?.let { requireValid(it in 1..MAX_REPS, "Snapshot repMin fuera de rango.") }
            item.repMax?.let { requireValid(it in 1..MAX_REPS, "Snapshot repMax fuera de rango.") }
            if (item.repMin != null && item.repMax != null) {
                requireValid(item.repMin <= item.repMax, "Snapshot de rango de reps inválido.")
            }
            item.targetRirTenths?.let {
                requireValid(it in 0..MAX_RIR_TENTHS, "Snapshot targetRirTenths fuera de rango.")
            }
            item.restSeconds?.let { requireValid(it in 0..MAX_REST_SECONDS, "Snapshot restSeconds fuera de rango.") }
            item.loadIncrementGrams?.let {
                requireValid(it >= 0L, "Snapshot loadIncrementGrams no puede ser negativo.")
            }
            item.previousReferenceMode?.let {
                requireValid(isPreviousMode(it), "Snapshot PREVIOUS desconocido.")
            }
        }

        snapshot.workouts.forEach { workout ->
            workout.restTimerWorkoutExerciseId?.let { ownerId ->
                val owner = workoutExerciseById[ownerId]
                requireValid(owner != null, "Rest timer apunta a un ejercicio de workout inexistente.")
                requireValid(owner?.workoutId == workout.id, "Rest timer apunta a otro workout.")
            }
        }

        val setPositions = hashSetOf<Pair<String, Int>>()
        snapshot.workoutSets.forEach { set ->
            requireId(set.id, "workoutSet.id")
            requireId(set.workoutExerciseId, "workoutSet.workoutExerciseId")
            requireValid(
                set.workoutExerciseId in workoutExerciseIds,
                "Set apunta a un ejercicio de workout inexistente.",
            )
            requireValid(set.position >= 0, "workoutSet.position no puede ser negativo.")
            requireValid(
                setPositions.add(set.workoutExerciseId to set.position),
                "Hay posiciones de set duplicadas dentro de un ejercicio de workout.",
            )
            requireValid(set.type in SetTypes.all, "Tipo de set desconocido: ${set.type}.")
            requireValid(set.loadGrams >= 0L, "loadGrams no puede ser negativo.")
            requireValid(set.reps in 0..MAX_REPS, "reps fuera de rango.")
            set.rirTenths?.let { requireValid(it in 0..MAX_RIR_TENTHS, "rirTenths fuera de rango.") }
            set.completedAt?.let { completedAt ->
                requireValid(completedAt >= 0L, "completedAt no puede ser negativo.")
                requireValid(set.reps > 0, "Un set completado debe tener reps positivas.")
                val workoutExercise = workoutExerciseById.getValue(set.workoutExerciseId)
                val workout = workoutById.getValue(workoutExercise.workoutId)
                requireValid(completedAt >= workout.startedAt, "Set completado antes de iniciar el workout.")
                workout.finishedAt?.let { finishedAt ->
                    requireValid(completedAt <= finishedAt, "Set completado después de terminar el workout.")
                }
            }
        }

        return BackupPreview(
            metadata = metadata,
            exerciseCount = snapshot.exercises.size,
            routineCount = snapshot.routines.size,
            workoutCount = snapshot.workouts.size,
            setCount = snapshot.workoutSets.size,
            earliestWorkoutStartedAt = snapshot.workouts.minOfOrNull { it.startedAt },
            latestWorkoutStartedAt = snapshot.workouts.maxOfOrNull { it.startedAt },
            hasActiveWorkout = activeWorkouts == 1,
        )
    }

    private fun isPreviousMode(value: String): Boolean =
        value == PreviousReferenceModes.ANY_WORKOUT || value == PreviousReferenceModes.SAME_ROUTINE

    private fun ensureUniqueIds(label: String, ids: List<String>) {
        val seen = hashSetOf<String>()
        ids.forEach { id ->
            requireValid(seen.add(id), "ID duplicado en $label: $id.")
        }
    }

    private fun requireId(value: String, label: String) {
        requireValid(value.isNotBlank(), "$label no puede estar vacío.")
        requireValid(value.length <= BackupFormat.MAX_ID_LENGTH, "$label es demasiado largo.")
    }

    private fun requireName(value: String, label: String, allowBlank: Boolean = false) {
        if (!allowBlank) requireValid(value.isNotBlank(), "$label no puede estar vacío.")
        requireValid(value.length <= BackupFormat.MAX_NAME_LENGTH, "$label es demasiado largo.")
    }

    private fun requireNotes(value: String, label: String) {
        requireValid(value.length <= BackupFormat.MAX_NOTES_LENGTH, "$label es demasiado largo.")
    }

    private fun requireCount(label: String, count: Int, max: Int) {
        requireValid(count <= max, "El backup contiene demasiados $label ($count > $max).")
    }

    private fun requireValid(condition: Boolean, message: String) {
        if (!condition) throw BackupValidationException(message)
    }
}
