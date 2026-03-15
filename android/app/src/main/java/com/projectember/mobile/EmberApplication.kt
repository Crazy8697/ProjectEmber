package com.projectember.mobile

import android.app.Application
import com.projectember.mobile.data.backup.BackupManager
import com.projectember.mobile.data.local.db.AppDatabase
import com.projectember.mobile.data.local.db.DatabaseSeeder
import com.projectember.mobile.data.local.KetoTargetsStore
import com.projectember.mobile.data.local.WeightStore
import com.projectember.mobile.data.repository.ExerciseCategoryRepository
import com.projectember.mobile.data.repository.ExerciseRepository
import com.projectember.mobile.data.repository.KetoRepository
import com.projectember.mobile.data.repository.RecipeRepository
import com.projectember.mobile.data.repository.SyncRepository
import com.projectember.mobile.data.repository.WeightRepository
import com.projectember.mobile.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EmberApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.getInstance(this) }

    val ketoRepository by lazy { KetoRepository(database.ketoDao()) }
    val ketoTargetsStore by lazy { KetoTargetsStore(this) }
    val weightStore by lazy { WeightStore(this) }
    val weightRepository by lazy { WeightRepository(database.weightDao()) }
    val recipeRepository by lazy { RecipeRepository(database.recipeDao()) }
    val syncRepository by lazy { SyncRepository(database.syncStatusDao()) }
    val syncManager by lazy { SyncManager(syncRepository) }
    val exerciseCategoryRepository by lazy { ExerciseCategoryRepository(database.exerciseCategoryDao()) }
    val exerciseRepository by lazy { ExerciseRepository(database.exerciseEntryDao()) }

    val backupManager by lazy {
        BackupManager(
            context = applicationContext,
            database = database,
            ketoRepository = ketoRepository,
            recipeRepository = recipeRepository,
            exerciseRepository = exerciseRepository,
            exerciseCategoryRepository = exerciseCategoryRepository,
            weightRepository = weightRepository,
            ketoTargetsStore = ketoTargetsStore,
            appVersion = BuildConfig.VERSION_NAME
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            DatabaseSeeder.seed(database)
        }
    }
}
