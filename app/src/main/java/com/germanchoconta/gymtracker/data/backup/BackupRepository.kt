package com.germanchoconta.gymtracker.data.backup

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import com.germanchoconta.gymtracker.data.local.BackupDao
import com.germanchoconta.gymtracker.data.local.GymTrackerDatabase

class BackupRepository(
    private val database: GymTrackerDatabase,
    private val backupDao: BackupDao,
) {
    suspend fun snapshot(): BackupSnapshot = database.withReadTransaction {
        readSnapshot()
    }

    suspend fun replaceAll(snapshot: BackupSnapshot) {
        val expected = snapshot.normalized()
        database.withWriteTransaction {
            backupDao.deleteWorkoutSets()
            backupDao.deleteWorkoutExercises()
            backupDao.deleteWorkouts()
            backupDao.deleteRoutineExercises()
            backupDao.deleteRoutines()
            backupDao.deleteExerciseMuscles()
            backupDao.deleteMuscles()
            backupDao.deleteExercises()

            backupDao.upsertExercises(expected.exercises)
            backupDao.upsertMuscles(expected.muscles)
            backupDao.upsertExerciseMuscles(expected.exerciseMuscles)
            backupDao.upsertRoutines(expected.routines)
            backupDao.upsertRoutineExercises(expected.routineExercises)
            backupDao.upsertWorkouts(expected.workouts)
            backupDao.upsertWorkoutExercises(expected.workoutExercises)
            backupDao.upsertWorkoutSets(expected.workoutSets)

            check(readSnapshot().normalized() == expected) {
                "Restored Room snapshot did not match the validated backup."
            }
        }
    }

    private suspend fun readSnapshot(): BackupSnapshot = BackupSnapshot(
        exercises = backupDao.getExercises(),
        muscles = backupDao.getMuscles(),
        exerciseMuscles = backupDao.getExerciseMuscles(),
        routines = backupDao.getRoutines(),
        routineExercises = backupDao.getRoutineExercises(),
        workouts = backupDao.getWorkouts(),
        workoutExercises = backupDao.getWorkoutExercises(),
        workoutSets = backupDao.getWorkoutSets(),
    )
}
