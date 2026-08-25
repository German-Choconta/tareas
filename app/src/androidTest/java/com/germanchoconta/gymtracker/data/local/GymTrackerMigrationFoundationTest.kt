package com.germanchoconta.gymtracker.data.local

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GymTrackerMigrationFoundationTest {
    @Test
    fun exportedSchemaV1CanCreateDatabase() = runTest {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = instrumentation.targetContext.getDatabasePath("gymtracker-migration-v1.db")
        file.delete()

        val helper = MigrationTestHelper(
            instrumentation = instrumentation,
            file = file,
            driver = BundledSQLiteDriver(),
            databaseClass = GymTrackerDatabase::class,
        )

        val connection = helper.createDatabase(1)
        connection.close()
        file.delete()
    }

    @Test
    fun migration1To2PreservesLegacyWorkoutAndAddsNullableSnapshotState() = runTest {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = instrumentation.targetContext.getDatabasePath("gymtracker-migration-1-2.db")
        file.delete()
        val helper = MigrationTestHelper(
            instrumentation = instrumentation,
            file = file,
            driver = BundledSQLiteDriver(),
            databaseClass = GymTrackerDatabase::class,
        )

        val v1 = helper.createDatabase(1)
        v1.execSQL(
            "INSERT INTO exercise (id, name, unilateral, archived) " +
                "VALUES ('synthetic-migration-exercise', 'Synthetic Migration Exercise', 0, 0)",
        )
        v1.execSQL(
            "INSERT INTO routine (id, name, position, archived) " +
                "VALUES ('synthetic-migration-routine', 'Synthetic Migration Routine', 0, 0)",
        )
        v1.execSQL(
            "INSERT INTO routine_exercise " +
                "(id, routineId, exerciseId, position, targetSetCount, repMin, repMax, targetRirTenths, restSeconds, loadIncrementGrams, previousReferenceMode) " +
                "VALUES ('synthetic-migration-template', 'synthetic-migration-routine', 'synthetic-migration-exercise', 0, 2, 8, 12, 20, 90, 2500, 'ANY_WORKOUT')",
        )
        v1.execSQL(
            "INSERT INTO workout (id, routineId, title, startedAt, finishedAt, notes) " +
                "VALUES ('synthetic-migration-workout', 'synthetic-migration-routine', 'Synthetic Migration Workout', 1000, 2000, 'Synthetic note')",
        )
        v1.execSQL(
            "INSERT INTO workout_exercise (id, workoutId, exerciseId, routineExerciseId, position, notes) " +
                "VALUES ('synthetic-migration-we', 'synthetic-migration-workout', 'synthetic-migration-exercise', 'synthetic-migration-template', 0, 'Synthetic exercise note')",
        )
        v1.execSQL(
            "INSERT INTO workout_set (id, workoutExerciseId, position, type, loadGrams, reps, rirTenths, completedAt) " +
                "VALUES ('synthetic-migration-set', 'synthetic-migration-we', 0, 'WORK', 42500, 10, 15, 1500)",
        )
        v1.close()

        val migrated = helper.runMigrationsAndValidate(2, listOf(GymTrackerDatabase.MIGRATION_1_2))
        migrated.prepare(
            "SELECT title, restTimerEndsAt, restTimerWorkoutExerciseId FROM workout " +
                "WHERE id = 'synthetic-migration-workout'",
        ).use { statement ->
            assertTrue(statement.step())
            assertEquals("Synthetic Migration Workout", statement.getText(0))
            assertTrue(statement.isNull(1))
            assertTrue(statement.isNull(2))
        }
        migrated.prepare(
            "SELECT notes, targetSetCount, repMin, repMax, targetRirTenths, restSeconds, loadIncrementGrams, previousReferenceMode " +
                "FROM workout_exercise WHERE id = 'synthetic-migration-we'",
        ).use { statement ->
            assertTrue(statement.step())
            assertEquals("Synthetic exercise note", statement.getText(0))
            for (index in 1..7) assertTrue(statement.isNull(index))
        }
        migrated.prepare(
            "SELECT loadGrams, reps, rirTenths, completedAt FROM workout_set " +
                "WHERE id = 'synthetic-migration-set'",
        ).use { statement ->
            assertTrue(statement.step())
            assertEquals(42_500L, statement.getLong(0))
            assertEquals(10L, statement.getLong(1))
            assertEquals(15L, statement.getLong(2))
            assertEquals(1_500L, statement.getLong(3))
        }
        migrated.close()
        file.delete()
    }
}
