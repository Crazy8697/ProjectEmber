package com.projectember.mobile.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.projectember.mobile.data.local.dao.KetoDao
import com.projectember.mobile.data.local.dao.RecipeDao
import com.projectember.mobile.data.local.dao.SyncStatusDao
import com.projectember.mobile.data.local.entities.KetoEntry
import com.projectember.mobile.data.local.entities.Recipe
import com.projectember.mobile.data.local.entities.SyncStatus

@Database(
    entities = [KetoEntry::class, Recipe::class, SyncStatus::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ketoDao(): KetoDao
    abstract fun recipeDao(): RecipeDao
    abstract fun syncStatusDao(): SyncStatusDao

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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "project_ember_mobile.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
