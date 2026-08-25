package com.germanchoconta.gymtracker.data.local

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

object PreviousReferenceModes {
    const val ANY_WORKOUT = "ANY_WORKOUT"
    const val SAME_ROUTINE = "SAME_ROUTINE"
}

object SetTypes {
    const val WARMUP = "WARMUP"
    const val WORK = "WORK"
    const val DROP = "DROP"
    const val FAILURE = "FAILURE"
}

object MuscleRoles {
    const val PRIMARY = "PRIMARY"
    const val SECONDARY = "SECONDARY"
}

@Entity(
    tableName = "exercise",
    indices = [Index(value = ["name"])],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val equipment: String? = null,
    val unilateral: Boolean = false,
    val notes: String? = null,
    val archived: Boolean = false,
    val defaultRepMin: Int? = null,
    val defaultRepMax: Int? = null,
    val defaultTargetRirTenths: Int? = null,
    val defaultRestSeconds: Int? = null,
    val defaultLoadIncrementGrams: Long? = null,
)

@Entity(
    tableName = "muscle",
    indices = [Index(value = ["name"], unique = true)],
)
data class MuscleEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "exercise_muscle",
    primaryKeys = ["exerciseId", "muscleId"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MuscleEntity::class,
            parentColumns = ["id"],
            childColumns = ["muscleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["exerciseId"]), Index(value = ["muscleId"])],
)
data class ExerciseMuscleEntity(
    val exerciseId: String,
    val muscleId: String,
    val role: String,
)

@Entity(
    tableName = "routine",
    indices = [Index(value = ["position"])],
)
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val position: Int,
    val notes: String? = null,
    val archived: Boolean = false,
)

@Entity(
    tableName = "routine_exercise",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["routineId"]),
        Index(value = ["exerciseId"]),
        Index(value = ["routineId", "position"], unique = true),
    ],
)
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val exerciseId: String,
    val position: Int,
    val targetSetCount: Int,
    val repMin: Int,
    val repMax: Int,
    val targetRirTenths: Int? = null,
    val restSeconds: Int,
    val loadIncrementGrams: Long,
    val previousReferenceMode: String = PreviousReferenceModes.ANY_WORKOUT,
)

@Entity(
    tableName = "workout",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["routineId"]),
        Index(value = ["startedAt"]),
    ],
)
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val routineId: String? = null,
    val title: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val notes: String? = null,
)

@Entity(
    tableName = "workout_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = RoutineExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineExerciseId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["workoutId"]),
        Index(value = ["exerciseId"]),
        Index(value = ["routineExerciseId"]),
        Index(value = ["workoutId", "position"], unique = true),
    ],
)
data class WorkoutExerciseEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val exerciseId: String,
    val routineExerciseId: String? = null,
    val position: Int,
    val notes: String? = null,
)

@Entity(
    tableName = "workout_set",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["workoutExerciseId"]),
        Index(value = ["workoutExerciseId", "position"], unique = true),
    ],
)
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    val workoutExerciseId: String,
    val position: Int,
    val type: String,
    val loadGrams: Long,
    val reps: Int,
    val rirTenths: Int? = null,
    val completedAt: Long? = null,
)
