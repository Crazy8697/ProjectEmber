package com.projectember.mobile.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.projectember.mobile.data.local.dao.ExerciseCategoryDao
import com.projectember.mobile.data.local.dao.ExerciseEntryDao
import com.projectember.mobile.data.local.dao.KetoDao
import com.projectember.mobile.data.local.dao.ManualHealthEntryDao
import com.projectember.mobile.data.local.dao.RecipeDao
import com.projectember.mobile.data.local.dao.StackDefinitionDao
import com.projectember.mobile.data.local.dao.SupplementEntryDao
import com.projectember.mobile.data.local.dao.SyncStatusDao
import com.projectember.mobile.data.local.dao.WeightDao
import com.projectember.mobile.data.local.entities.ExerciseCategory
import com.projectember.mobile.data.local.entities.ExerciseEntry
import com.projectember.mobile.data.local.entities.KetoEntry
import com.projectember.mobile.data.local.entities.ManualHealthEntry
import com.projectember.mobile.data.local.entities.Recipe
import com.projectember.mobile.data.local.entities.StackDefinition
import com.projectember.mobile.data.local.entities.SupplementEntry
import com.projectember.mobile.data.local.entities.SyncStatus
import com.projectember.mobile.data.local.entities.WeightEntry

@Database(
    entities = [KetoEntry::class, Recipe::class, SyncStatus::class,
                ExerciseCategory::class, ExerciseEntry::class,
                WeightEntry::class, ManualHealthEntry::class,
                SupplementEntry::class, StackDefinition::class],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ketoDao(): KetoDao
    abstract fun recipeDao(): RecipeDao
    abstract fun syncStatusDao(): SyncStatusDao
    abstract fun exerciseCategoryDao(): ExerciseCategoryDao
    abstract fun exerciseEntryDao(): ExerciseEntryDao
    abstract fun weightDao(): WeightDao
    abstract fun manualHealthEntryDao(): ManualHealthEntryDao
    abstract fun supplementEntryDao(): SupplementEntryDao
    abstract fun stackDefinitionDao(): StackDefinitionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE keto_entries ADD COLUMN notes TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE keto_entries ADD COLUMN waterMl REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE keto_entries ADD COLUMN sodiumMg REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE keto_entries ADD COLUMN potassiumMg REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE keto_entries ADD COLUMN magnesiumMg REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recipes ADD COLUMN category TEXT NOT NULL DEFAULT 'General'")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recipes ADD COLUMN ingredientsRaw TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recipes ADD COLUMN totalCarbsG REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE recipes ADD COLUMN fiberG REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE recipes ADD COLUMN sodiumMg REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE recipes ADD COLUMN potassiumMg REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE recipes ADD COLUMN magnesiumMg REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE recipes ADD COLUMN waterMl REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `exercise_categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `isBuiltIn` INTEGER NOT NULL
                    )"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `exercise_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `entryDate` TEXT NOT NULL,
                        `entryTime` TEXT NOT NULL,
                        `timestamp` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `subtype` TEXT,
                        `categoryId` INTEGER NOT NULL,
                        `notes` TEXT,
                        `durationMinutes` INTEGER,
                        `caloriesBurned` REAL
                    )"""
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_exercise_entries_entryDate` " +
                        "ON `exercise_entries` (`entryDate`)"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `weight_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `entryDate` TEXT NOT NULL,
                        `weightKg` REAL NOT NULL
                    )"""
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_weight_entries_entryDate` " +
                        "ON `weight_entries` (`entryDate`)"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE keto_entries ADD COLUMN servings REAL NOT NULL DEFAULT 1.0"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE keto_entries ADD COLUMN recipeId INTEGER"
                )
            }
        }

        /**
         * Deduplicate weight entries: keep only the most-recently inserted entry per day
         * (highest `id`). This enforces the one-entry-per-day rule retroactively for any
         * duplicate same-day entries that accumulated before PR29.
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """DELETE FROM weight_entries
                       WHERE id NOT IN (
                           SELECT MAX(id)
                           FROM weight_entries
                           GROUP BY entryDate
                       )"""
                )
            }
        }

        /**
         * Adds `source TEXT` to weight_entries.  Null = manually entered.
         * "health_connect" = imported from Health Connect.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE weight_entries ADD COLUMN source TEXT")
            }
        }

        /**
         * Creates the manual_health_entries table for local-only health metric entries.
         * These are never written back to Health Connect.
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `manual_health_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `metricType` TEXT NOT NULL,
                        `value1` REAL NOT NULL,
                        `value2` REAL,
                        `entryDate` TEXT NOT NULL,
                        `entryTime` TEXT NOT NULL,
                        `source` TEXT NOT NULL
                    )"""
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_manual_health_entries_metricType_entryDate` " +
                        "ON `manual_health_entries` (`metricType`, `entryDate`)"
                )
            }
        }

        /**
         * Creates the supplement_entries table for dedicated supplement logging.
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `supplement_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `dose` TEXT NOT NULL,
                        `unit` TEXT,
                        `entryDate` TEXT NOT NULL,
                        `entryTime` TEXT NOT NULL,
                        `notes` TEXT
                    )"""
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_supplement_entries_entryDate` " +
                        "ON `supplement_entries` (`entryDate`)"
                )
            }
        }

        /**
         * Upgrades supplement tracking to Stacks System:
         *  - Adds stackDefinitionId (FK to stack_definitions.id) to supplement_entries
         *  - Adds ketoEntryId (FK to keto_entries.id) to supplement_entries
         *  - Creates the stack_definitions table (reusable library items)
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns to supplement_entries (existing rows get NULL)
                database.execSQL(
                    "ALTER TABLE supplement_entries ADD COLUMN stackDefinitionId INTEGER"
                )
                database.execSQL(
                    "ALTER TABLE supplement_entries ADD COLUMN ketoEntryId INTEGER"
                )
                // Create the stack_definitions table
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `stack_definitions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `defaultDose` TEXT,
                        `defaultUnit` TEXT,
                        `notes` TEXT,
                        `caloriesKcal` REAL,
                        `proteinG` REAL,
                        `fatG` REAL,
                        `netCarbsG` REAL,
                        `sodiumMg` REAL,
                        `potassiumMg` REAL,
                        `magnesiumMg` REAL
                    )"""
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "project_ember_mobile.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                        MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                        MIGRATION_13_14, MIGRATION_14_15
                    )
                    .build().also { INSTANCE = it }
            }
        }
    }
}
