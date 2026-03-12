package com.projectember.mobile

import android.app.Application
import com.projectember.mobile.data.local.db.AppDatabase
import com.projectember.mobile.data.local.db.DatabaseSeeder
import com.projectember.mobile.data.repository.KetoRepository
import com.projectember.mobile.data.repository.RecipeRepository
import com.projectember.mobile.data.repository.SyncRepository
import com.projectember.mobile.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EmberApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.getInstance(this) }

    val ketoRepository by lazy { KetoRepository(database.ketoDao()) }
    val recipeRepository by lazy { RecipeRepository(database.recipeDao()) }
    val syncRepository by lazy { SyncRepository(database.syncStatusDao()) }
    val syncManager by lazy { SyncManager(syncRepository) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            DatabaseSeeder.seed(database)
        }
    }
}
