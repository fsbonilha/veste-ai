package com.vesteai.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Migration harness from v1: future migrations add a runMigrationsAndValidate per step. */
@RunWith(RobolectricTestRunner::class)
class VesteDatabaseSchemaTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VesteDatabase::class.java,
    )

    @Test
    fun exportedV1SchemaMatchesEntities() {
        helper.createDatabase(DB_NAME, 1).close()

        helper.runMigrationsAndValidate(DB_NAME, 1, true).close()
    }

    private companion object {
        const val DB_NAME = "schema-test.db"
    }
}
