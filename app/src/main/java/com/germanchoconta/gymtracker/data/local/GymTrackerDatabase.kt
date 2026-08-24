package com.germanchoconta.gymtracker.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

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
    version = 1,
    exportSchema = true,
)
abstract class GymTrackerDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun muscleDao(): MuscleDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val DATABASE_NAME = "gymtracker.db"

        fun build(context: Context): GymTrackerDatabase =
            Room.databaseBuilder<GymTrackerDatabase>(context.applicationContext, DATABASE_NAME)
                .setDriver(BundledSQLiteDriver())
                .build()
    }
}

class ExerciseRepository(
    private val exerciseDao: ExerciseDao,
    private val muscleDao: MuscleDao,
) {
    fun observeActive() = exerciseDao.observeActive()
    fun observeMuscles(exerciseId: String) = muscleDao.observeForExercise(exerciseId)
    suspend fun getById(id: String) = exerciseDao.getById(id)
    suspend fun save(exercise: ExerciseEntity) = exerciseDao.upsert(exercise)
    suspend fun saveMuscle(muscle: MuscleEntity) = muscleDao.upsert(muscle)
    suspend fun linkMuscle(link: ExerciseMuscleEntity) = muscleDao.upsertLink(link)
    suspend fun archive(id: String) = exerciseDao.archive(id)
}

class RoutineRepository(private val routineDao: RoutineDao) {
    fun observeActive() = routineDao.observeActive()
    fun observeExercises(routineId: String) = routineDao.observeExercises(routineId)
    suspend fun save(routine: RoutineEntity) = routineDao.upsert(routine)
    suspend fun saveExercise(routineExercise: RoutineExerciseEntity) =
        routineDao.upsertExercise(routineExercise)
    suspend fun archive(id: String) = routineDao.archive(id)
}

class WorkoutRepository(private val workoutDao: WorkoutDao) {
    suspend fun save(workout: WorkoutEntity) = workoutDao.upsert(workout)
    suspend fun saveExercise(workoutExercise: WorkoutExerciseEntity) =
        workoutDao.upsertExercise(workoutExercise)
    suspend fun saveSet(workoutSet: WorkoutSetEntity) = workoutDao.upsertSet(workoutSet)
    suspend fun getWorkout(id: String) = workoutDao.getWorkout(id)
    suspend fun getExercises(workoutId: String) = workoutDao.getExercises(workoutId)
    suspend fun getSets(workoutExerciseId: String) = workoutDao.getSets(workoutExerciseId)
    fun observeExerciseHistory(exerciseId: String) = workoutDao.observeExerciseHistory(exerciseId)

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
}
