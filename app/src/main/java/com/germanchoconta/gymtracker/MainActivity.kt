package com.germanchoconta.gymtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.germanchoconta.gymtracker.data.local.ExerciseRepository
import com.germanchoconta.gymtracker.data.local.GymTrackerDatabase
import com.germanchoconta.gymtracker.data.local.RoutineRepository
import com.germanchoconta.gymtracker.data.local.WorkoutRepository
import com.germanchoconta.gymtracker.ui.GymTrackerApp
import com.germanchoconta.gymtracker.ui.theme.GymTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = GymTrackerDatabase.build(applicationContext)
        val exerciseRepository = ExerciseRepository(database.exerciseDao(), database.muscleDao())
        val routineRepository = RoutineRepository(database.routineDao())
        val workoutRepository = WorkoutRepository(
            workoutDao = database.workoutDao(),
            routineDao = database.routineDao(),
            exerciseDao = database.exerciseDao(),
        )

        setContent {
            GymTrackerTheme {
                GymTrackerApp(
                    exerciseRepository = exerciseRepository,
                    routineRepository = routineRepository,
                    workoutRepository = workoutRepository,
                )
            }
        }
    }
}
