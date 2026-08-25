package com.germanchoconta.gymtracker.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface BackupDao {
    @Query("SELECT * FROM exercise ORDER BY id")
    suspend fun getExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM muscle ORDER BY id")
    suspend fun getMuscles(): List<MuscleEntity>

    @Query("SELECT * FROM exercise_muscle ORDER BY exerciseId, muscleId")
    suspend fun getExerciseMuscles(): List<ExerciseMuscleEntity>

    @Query("SELECT * FROM routine ORDER BY id")
    suspend fun getRoutines(): List<RoutineEntity>

    @Query("SELECT * FROM routine_exercise ORDER BY id")
    suspend fun getRoutineExercises(): List<RoutineExerciseEntity>

    @Query("SELECT * FROM workout ORDER BY id")
    suspend fun getWorkouts(): List<WorkoutEntity>

    @Query("SELECT * FROM workout_exercise ORDER BY id")
    suspend fun getWorkoutExercises(): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_set ORDER BY id")
    suspend fun getWorkoutSets(): List<WorkoutSetEntity>

    @Query("DELETE FROM workout_set")
    suspend fun deleteWorkoutSets()

    @Query("DELETE FROM workout_exercise")
    suspend fun deleteWorkoutExercises()

    @Query("DELETE FROM workout")
    suspend fun deleteWorkouts()

    @Query("DELETE FROM routine_exercise")
    suspend fun deleteRoutineExercises()

    @Query("DELETE FROM routine")
    suspend fun deleteRoutines()

    @Query("DELETE FROM exercise_muscle")
    suspend fun deleteExerciseMuscles()

    @Query("DELETE FROM muscle")
    suspend fun deleteMuscles()

    @Query("DELETE FROM exercise")
    suspend fun deleteExercises()

    @Upsert
    suspend fun upsertExercises(items: List<ExerciseEntity>)

    @Upsert
    suspend fun upsertMuscles(items: List<MuscleEntity>)

    @Upsert
    suspend fun upsertExerciseMuscles(items: List<ExerciseMuscleEntity>)

    @Upsert
    suspend fun upsertRoutines(items: List<RoutineEntity>)

    @Upsert
    suspend fun upsertRoutineExercises(items: List<RoutineExerciseEntity>)

    @Upsert
    suspend fun upsertWorkouts(items: List<WorkoutEntity>)

    @Upsert
    suspend fun upsertWorkoutExercises(items: List<WorkoutExerciseEntity>)

    @Upsert
    suspend fun upsertWorkoutSets(items: List<WorkoutSetEntity>)
}
