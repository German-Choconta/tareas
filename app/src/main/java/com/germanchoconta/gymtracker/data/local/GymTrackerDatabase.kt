package com.germanchoconta.gymtracker.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.util.UUID

@Database(
    entities = [
        ExerciseEntity::class,
        MuscleEntity::class,
        ExerciseMuscleEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class GymTrackerDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun muscleDao(): MuscleDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val DATABASE_NAME = "gymtracker.db"

        val MIGRATION_1_2 = Migration(1, 2) { connection ->
            connection.execSQL("ALTER TABLE workout ADD COLUMN restTimerEndsAt INTEGER")
            connection.execSQL("ALTER TABLE workout ADD COLUMN restTimerWorkoutExerciseId TEXT")
            connection.execSQL("ALTER TABLE workout_exercise ADD COLUMN targetSetCount INTEGER")
            connection.execSQL("ALTER TABLE workout_exercise ADD COLUMN repMin INTEGER")
            connection.execSQL("ALTER TABLE workout_exercise ADD COLUMN repMax INTEGER")
            connection.execSQL("ALTER TABLE workout_exercise ADD COLUMN targetRirTenths INTEGER")
            connection.execSQL("ALTER TABLE workout_exercise ADD COLUMN restSeconds INTEGER")
            connection.execSQL("ALTER TABLE workout_exercise ADD COLUMN loadIncrementGrams INTEGER")
            connection.execSQL("ALTER TABLE workout_exercise ADD COLUMN previousReferenceMode TEXT")
        }

        @Volatile
        private var instance: GymTrackerDatabase? = null

        fun build(context: Context): GymTrackerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder<GymTrackerDatabase>(
                    context.applicationContext,
                    DATABASE_NAME,
                )
                    .setDriver(BundledSQLiteDriver())
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}

class ExerciseRepository(
    private val exerciseDao: ExerciseDao,
    private val muscleDao: MuscleDao,
) {
    fun observeActive() = exerciseDao.observeActive()
    fun observeMuscles(exerciseId: String) = muscleDao.observeForExercise(exerciseId)
    fun observeAllMuscles() = muscleDao.observeAll()
    suspend fun getActive() = exerciseDao.getActive()
    suspend fun getById(id: String) = exerciseDao.getById(id)
    suspend fun getAssignments(exerciseId: String) = muscleDao.getAssignments(exerciseId)
    suspend fun save(exercise: ExerciseEntity) = exerciseDao.upsert(exercise)
    suspend fun saveMuscle(muscle: MuscleEntity) = muscleDao.upsert(muscle)
    suspend fun linkMuscle(link: ExerciseMuscleEntity) = muscleDao.upsertLink(link)

    suspend fun saveWithMuscles(
        exercise: ExerciseEntity,
        muscles: List<MuscleEntity>,
        links: List<ExerciseMuscleEntity>,
    ) {
        muscles.forEach { muscleDao.upsert(it) }
        exerciseDao.upsert(exercise)
        muscleDao.replaceLinks(exercise.id, links)
    }

    suspend fun archive(id: String) = exerciseDao.archive(id)
}

class RoutineRepository(private val routineDao: RoutineDao) {
    fun observeActive() = routineDao.observeActive()
    fun observeExercises(routineId: String) = routineDao.observeExercises(routineId)
    suspend fun getById(id: String) = routineDao.getById(id)
    suspend fun getExercises(routineId: String) = routineDao.getExercises(routineId)
    suspend fun save(routine: RoutineEntity) = routineDao.upsert(routine)
    suspend fun saveExercise(routineExercise: RoutineExerciseEntity) =
        routineDao.upsertExercise(routineExercise)

    suspend fun saveWithExercises(
        routine: RoutineEntity,
        exercises: List<RoutineExerciseEntity>,
    ) = routineDao.saveWithExercises(routine, exercises)

    suspend fun archive(id: String) = routineDao.archive(id)
}

data class WorkoutAggregate(
    val workout: WorkoutEntity,
    val exercises: List<WorkoutExerciseWithSets>,
)

data class WorkoutExerciseWithSets(
    val exercise: WorkoutExerciseEntity,
    val sets: List<WorkoutSetEntity>,
)

class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
) {
    suspend fun startFromRoutine(routineId: String, startedAt: Long): WorkoutEntity? {
        workoutDao.getActiveWorkout()?.let { return it }
        val routine = routineDao.getById(routineId)?.takeUnless { it.archived } ?: return null
        val template = routineDao.getExercises(routineId)
        if (template.isEmpty()) return null
        val workout = WorkoutEntity(
            id = UUID.randomUUID().toString(),
            routineId = routine.id,
            title = routine.name,
            startedAt = startedAt,
        )
        val workoutExercises = template.map { item ->
            WorkoutExerciseEntity(
                id = UUID.randomUUID().toString(),
                workoutId = workout.id,
                exerciseId = item.exerciseId,
                routineExerciseId = item.id,
                position = item.position,
                targetSetCount = item.targetSetCount,
                repMin = item.repMin,
                repMax = item.repMax,
                targetRirTenths = item.targetRirTenths,
                restSeconds = item.restSeconds,
                loadIncrementGrams = item.loadIncrementGrams,
                previousReferenceMode = item.previousReferenceMode,
            )
        }
        val sets = workoutExercises.flatMap { workoutExercise ->
            List(requireNotNull(workoutExercise.targetSetCount)) { position ->
                WorkoutSetEntity(
                    id = UUID.randomUUID().toString(),
                    workoutExerciseId = workoutExercise.id,
                    position = position,
                    type = SetTypes.WORK,
                    loadGrams = 0,
                    reps = 0,
                )
            }
        }
        workoutDao.insertWorkoutAggregate(workout, workoutExercises, sets)
        return workout
    }

    suspend fun getActiveWorkout() = workoutDao.getActiveWorkout()
    suspend fun getWorkout(id: String) = workoutDao.getWorkout(id)
    suspend fun getWorkoutExercise(id: String) = workoutDao.getWorkoutExercise(id)
    suspend fun getExercises(workoutId: String) = workoutDao.getExercises(workoutId)
    suspend fun getSets(workoutExerciseId: String) = workoutDao.getSets(workoutExerciseId)
    suspend fun getCompletedSets(workoutExerciseId: String) = workoutDao.getCompletedSets(workoutExerciseId)
    fun observeExerciseHistory(exerciseId: String) = workoutDao.observeExerciseHistory(exerciseId)

    suspend fun getAggregate(workoutId: String): WorkoutAggregate? {
        val workout = workoutDao.getWorkout(workoutId) ?: return null
        val exercises = workoutDao.getExercises(workoutId).map { item ->
            WorkoutExerciseWithSets(item, workoutDao.getSets(item.id))
        }
        return WorkoutAggregate(workout, exercises)
    }

    suspend fun updateSetLoad(setId: String, loadGrams: Long): Boolean =
        loadGrams >= 0L && workoutDao.updateSetLoad(setId, loadGrams) > 0

    suspend fun updateSetReps(setId: String, reps: Int): Boolean =
        reps in 0..1000 && workoutDao.updateSetReps(setId, reps) > 0

    suspend fun updateSetRir(setId: String, rirTenths: Int?): Boolean =
        (rirTenths == null || rirTenths in 0..100) && workoutDao.updateSetRir(setId, rirTenths) > 0

    suspend fun updateSetType(setId: String, type: String): Boolean =
        type in SetTypes.all && workoutDao.updateSetType(setId, type) > 0

    suspend fun updateSet(
        setId: String,
        loadGrams: Long,
        reps: Int,
        rirTenths: Int?,
        type: String,
    ): Boolean {
        if (loadGrams < 0L || reps !in 0..1000 || rirTenths?.let { it !in 0..100 } == true || type !in SetTypes.all) {
            return false
        }
        return updateSetLoad(setId, loadGrams) &&
            updateSetReps(setId, reps) &&
            updateSetRir(setId, rirTenths) &&
            updateSetType(setId, type)
    }

    suspend fun setCompleted(setId: String, completedAt: Long?): Boolean {
        val existing = workoutDao.getSet(setId) ?: return false
        val workoutExercise = workoutDao.getWorkoutExercise(existing.workoutExerciseId) ?: return false
        val workout = workoutDao.getWorkout(workoutExercise.workoutId) ?: return false
        if (workout.finishedAt != null) return false
        if (completedAt != null && existing.reps <= 0) return false
        if (workoutDao.updateSetCompletedAt(setId, completedAt) == 0) return false
        if (completedAt != null) {
            val restSeconds = workoutExercise.restSeconds ?: 0
            if (restSeconds > 0) {
                workoutDao.setRestTimer(
                    workout.id,
                    workoutExercise.id,
                    completedAt + restSeconds * 1_000L,
                )
            }
        }
        return true
    }

    suspend fun addSet(workoutExerciseId: String): WorkoutSetEntity? {
        val workoutExercise = workoutDao.getWorkoutExercise(workoutExerciseId) ?: return null
        val workout = workoutDao.getWorkout(workoutExercise.workoutId) ?: return null
        if (workout.finishedAt != null) return null
        val sets = workoutDao.getSets(workoutExerciseId)
        val set = WorkoutSetEntity(
            id = UUID.randomUUID().toString(),
            workoutExerciseId = workoutExerciseId,
            position = (sets.maxOfOrNull { it.position } ?: -1) + 1,
            type = SetTypes.WORK,
            loadGrams = 0,
            reps = 0,
        )
        workoutDao.upsertSet(set)
        return set
    }

    suspend fun removeSet(setId: String, allowCompleted: Boolean = false): Boolean {
        val set = workoutDao.getSet(setId) ?: return false
        if (set.completedAt != null && !allowCompleted) return false
        val workoutExercise = workoutDao.getWorkoutExercise(set.workoutExerciseId) ?: return false
        val workout = workoutDao.getWorkout(workoutExercise.workoutId) ?: return false
        if (workout.finishedAt != null) return false
        workoutDao.deleteSetAndCompact(workoutExercise.id, set.id)
        return true
    }

    suspend fun addExercise(workoutId: String, exerciseId: String): WorkoutExerciseEntity? {
        val workout = workoutDao.getWorkout(workoutId) ?: return null
        if (workout.finishedAt != null) return null
        val exercise = exerciseDao.getById(exerciseId)?.takeUnless { it.archived } ?: return null
        val current = workoutDao.getExercises(workoutId)
        val targetSetCount = 3
        val workoutExercise = WorkoutExerciseEntity(
            id = UUID.randomUUID().toString(),
            workoutId = workoutId,
            exerciseId = exercise.id,
            routineExerciseId = null,
            position = (current.maxOfOrNull { it.position } ?: -1) + 1,
            targetSetCount = targetSetCount,
            repMin = exercise.defaultRepMin ?: 8,
            repMax = exercise.defaultRepMax ?: 12,
            targetRirTenths = exercise.defaultTargetRirTenths ?: 20,
            restSeconds = exercise.defaultRestSeconds ?: 120,
            loadIncrementGrams = exercise.defaultLoadIncrementGrams ?: 2_500,
            previousReferenceMode = PreviousReferenceModes.ANY_WORKOUT,
        )
        workoutDao.upsertExercise(workoutExercise)
        repeat(targetSetCount) { position ->
            workoutDao.upsertSet(
                WorkoutSetEntity(
                    id = UUID.randomUUID().toString(),
                    workoutExerciseId = workoutExercise.id,
                    position = position,
                    type = SetTypes.WORK,
                    loadGrams = 0,
                    reps = 0,
                ),
            )
        }
        return workoutExercise
    }

    suspend fun replaceExercise(workoutExerciseId: String, exerciseId: String): Boolean {
        val workoutExercise = workoutDao.getWorkoutExercise(workoutExerciseId) ?: return false
        val workout = workoutDao.getWorkout(workoutExercise.workoutId) ?: return false
        if (workout.finishedAt != null) return false
        if (workoutDao.getCompletedSets(workoutExerciseId).isNotEmpty()) return false
        val replacement = exerciseDao.getById(exerciseId)?.takeUnless { it.archived } ?: return false
        workoutDao.upsertExercise(
            workoutExercise.copy(
                exerciseId = replacement.id,
                routineExerciseId = null,
            ),
        )
        return true
    }

    suspend fun removeExercise(workoutExerciseId: String, allowCompleted: Boolean = false): Boolean {
        val workoutExercise = workoutDao.getWorkoutExercise(workoutExerciseId) ?: return false
        val workout = workoutDao.getWorkout(workoutExercise.workoutId) ?: return false
        if (workout.finishedAt != null) return false
        if (workoutDao.getCompletedSets(workoutExerciseId).isNotEmpty() && !allowCompleted) return false
        workoutDao.deleteWorkoutExerciseAndCompact(workout.id, workoutExerciseId)
        return true
    }

    suspend fun updateWorkoutNotes(workoutId: String, notes: String?) =
        workoutDao.updateWorkoutNotes(workoutId, notes?.trim()?.ifEmpty { null })

    suspend fun updateWorkoutExerciseNotes(workoutExerciseId: String, notes: String?): Boolean {
        val workoutExercise = workoutDao.getWorkoutExercise(workoutExerciseId) ?: return false
        val workout = workoutDao.getWorkout(workoutExercise.workoutId) ?: return false
        if (workout.finishedAt != null) return false
        workoutDao.updateWorkoutExerciseNotes(workoutExerciseId, notes?.trim()?.ifEmpty { null })
        return true
    }

    suspend fun setRestTimer(workoutId: String, workoutExerciseId: String?, endsAt: Long?) =
        workoutDao.setRestTimer(workoutId, workoutExerciseId, endsAt)

    suspend fun finishWorkout(workoutId: String, finishedAt: Long): Boolean {
        val workout = workoutDao.getWorkout(workoutId) ?: return false
        if (workout.finishedAt != null || finishedAt < workout.startedAt) return false
        workoutDao.finishWorkout(workoutId, finishedAt)
        return true
    }

    suspend fun previousWorkout(
        exerciseId: String,
        referenceMode: String,
        routineId: String?,
        beforeStartedAt: Long = Long.MAX_VALUE,
    ): PreviousWorkoutRow? = when (referenceMode) {
        PreviousReferenceModes.SAME_ROUTINE -> {
            if (routineId == null) null
            else workoutDao.previousSameRoutine(exerciseId, routineId, beforeStartedAt)
        }
        else -> workoutDao.previousAnyWorkout(exerciseId, beforeStartedAt)
    }

    suspend fun previousCompletedSets(
        exerciseId: String,
        referenceMode: String,
        routineId: String?,
        beforeStartedAt: Long,
    ): List<WorkoutSetEntity> {
        val previous = previousWorkout(exerciseId, referenceMode, routineId, beforeStartedAt) ?: return emptyList()
        return workoutDao.getCompletedSets(previous.workoutExerciseId)
    }
}
