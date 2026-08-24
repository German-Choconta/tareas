package com.germanchoconta.gymtracker.data.local

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
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
}
