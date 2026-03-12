package com.projectember.mobile.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.projectember.mobile.data.local.dao.KetoDao
import com.projectember.mobile.data.local.dao.RecipeDao
import com.projectember.mobile.data.local.dao.SyncStatusDao
import com.projectember.mobile.data.local.entities.KetoEntry
import com.projectember.mobile.data.local.entities.Recipe
import com.projectember.mobile.data.local.entities.SyncStatus

@Database(
    entities = [KetoEntry::class, Recipe::class, SyncStatus::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ketoDao(): KetoDao
    abstract fun recipeDao(): RecipeDao
    abstract fun syncStatusDao(): SyncStatusDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "project_ember_mobile.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
