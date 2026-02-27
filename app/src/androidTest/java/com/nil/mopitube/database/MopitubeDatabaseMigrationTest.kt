package com.nil.mopitube.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Tests for database migrations to ensure user data is preserved across schema changes.
 * This prevents the critical issue where users lose their liked tracks and play history.
 */
@RunWith(AndroidJUnit4::class)
class MopitubeDatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MopitubeDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate2To3_preservesExistingData() {
        // Create database at version 2
        helper.createDatabase(TEST_DB, 2).apply {
            // Insert test data into version 2 schema
            execSQL("INSERT INTO artwork_cache VALUES ('test_key', 'http://example.com/image.jpg')")
            execSQL("INSERT INTO liked_tracks VALUES ('spotify:track:test123')")
            close()
        }

        // Run migration to version 3
        helper.runMigrationsAndValidate(TEST_DB, 3, true)

        // Verify data is preserved
        helper.runMigrationsAndValidate(TEST_DB, 3, true).apply {
            // Check that old data still exists
            query("SELECT * FROM artwork_cache WHERE cacheKey = 'test_key'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(cursor.getColumnIndexOrThrow("imageUrl")))
                    .isEqualTo("http://example.com/image.jpg")
            }

            query("SELECT * FROM liked_tracks WHERE uri = 'spotify:track:test123'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
            }

            // Verify new table exists
            query("SELECT name FROM sqlite_master WHERE type='table' AND name='play_history'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
            }
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4_preservesExistingData() {
        // Create database at version 3
        helper.createDatabase(TEST_DB, 3).apply {
            // Insert test data
            execSQL("INSERT INTO liked_tracks VALUES ('spotify:track:test456')")
            execSQL("INSERT INTO play_history VALUES (1, 'spotify:track:test456', 1234567890)")
            close()
        }

        // Run migration to version 4
        helper.runMigrationsAndValidate(TEST_DB, 4, true)

        // Verify data is preserved and new table exists
        helper.runMigrationsAndValidate(TEST_DB, 4, true).apply {
            // Check that old data still exists
            query("SELECT * FROM liked_tracks WHERE uri = 'spotify:track:test456'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
            }

            query("SELECT * FROM play_history WHERE uri = 'spotify:track:test456'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")))
                    .isEqualTo(1234567890)
            }

            // Verify new tracks table exists with correct schema
            query("SELECT name FROM sqlite_master WHERE type='table' AND name='tracks'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
            }
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll_preservesUserData() {
        // Create database at version 2
        helper.createDatabase(TEST_DB, 2).apply {
            // Insert critical user data
            execSQL("INSERT INTO liked_tracks VALUES ('spotify:track:favorite1')")
            execSQL("INSERT INTO liked_tracks VALUES ('spotify:track:favorite2')")
            execSQL("INSERT INTO artwork_cache VALUES ('album123', 'http://example.com/art.jpg')")
            close()
        }

        // Migrate through all versions
        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true)

        // Verify all user data survived migration
        db.apply {
            query("SELECT COUNT(*) FROM liked_tracks").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(2)
            }

            query("SELECT * FROM liked_tracks WHERE uri = 'spotify:track:favorite1'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
            }

            query("SELECT * FROM artwork_cache WHERE cacheKey = 'album123'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
            }
            close()
        }
    }

    @Test
    fun allMigrations_canBeApplied() {
        // Create the database with version 1 and apply all migrations
        helper.createDatabase(TEST_DB, 1).close()

        // Open latest version - should apply all migrations successfully
        Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MopitubeDatabase::class.java,
            TEST_DB
        ).addMigrations(
            MopitubeDatabase.Companion::class.java.getDeclaredField("MIGRATION_1_2").get(null) as androidx.room.migration.Migration,
            MopitubeDatabase.Companion::class.java.getDeclaredField("MIGRATION_2_3").get(null) as androidx.room.migration.Migration,
            MopitubeDatabase.Companion::class.java.getDeclaredField("MIGRATION_3_4").get(null) as androidx.room.migration.Migration
        ).build().apply {
            openHelper.writableDatabase.close()
        }
    }
}
