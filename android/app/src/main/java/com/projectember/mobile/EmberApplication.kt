package com.projectember.mobile

import android.app.Application
import com.projectember.mobile.data.backup.BackupManager
import com.projectember.mobile.data.backup.NightlyBackupEngine
import com.projectember.mobile.data.backup.NightlyBackupScheduler
import com.projectember.mobile.data.backup.NightlyBackupStore
import com.projectember.mobile.data.keto.KetoImportManager
import com.projectember.mobile.data.recipe.RecipeImportExportManager
import com.projectember.mobile.data.local.db.AppDatabase
import com.projectember.mobile.data.local.db.DatabaseSeeder
import com.projectember.mobile.data.local.HealthMetricPreferencesStore
import com.projectember.mobile.data.local.KetoTargetsStore
import com.projectember.mobile.data.local.ThemePreferencesStore
import com.projectember.mobile.data.local.UnitsPreferencesStore
import com.projectember.mobile.data.local.DailyRhythmStore
import com.projectember.mobile.data.local.MealTimingStore
import com.projectember.mobile.data.local.entities.WeightEntry
import com.projectember.mobile.data.repository.ExerciseCategoryRepository
import com.projectember.mobile.data.repository.ExerciseRepository
import com.projectember.mobile.data.repository.KetoRepository
import com.projectember.mobile.data.repository.ManualHealthEntryRepository
import com.projectember.mobile.data.repository.IngredientRepository
import com.projectember.mobile.data.repository.RecipeRepository
import com.projectember.mobile.data.repository.StackDefinitionRepository
import com.projectember.mobile.data.repository.SupplementRepository
import com.projectember.mobile.data.repository.SyncRepository
import com.projectember.mobile.data.repository.WeightRepository
import com.projectember.mobile.sync.SyncManager
import com.projectember.mobile.sync.HealthConnectManager
import com.projectember.mobile.sync.HealthSyncReceiver
import com.projectember.mobile.widget.WidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EmberApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.getInstance(this) }

    val ketoRepository by lazy { KetoRepository(database.ketoDao()) }
    val ketoTargetsStore by lazy { KetoTargetsStore(this) }
    val weightRepository by lazy { WeightRepository(database.weightDao()) }
    val recipeRepository by lazy { RecipeRepository(database.recipeDao()) }
    val ingredientRepository by lazy { IngredientRepository(database.ingredientDao()) }
    val syncRepository by lazy { SyncRepository(database.syncStatusDao()) }
    val syncManager by lazy { SyncManager(syncRepository) }
    val healthConnectManager by lazy {
        HealthConnectManager(
            context = applicationContext,
            syncRepository = syncRepository,
            weightRepository = weightRepository,
            exerciseRepository = exerciseRepository,
            exerciseCategoryRepository = exerciseCategoryRepository
        )
    }
    val exerciseCategoryRepository by lazy { ExerciseCategoryRepository(database.exerciseCategoryDao()) }
    val exerciseRepository by lazy { ExerciseRepository(database.exerciseEntryDao()) }
    val manualHealthEntryRepository by lazy { ManualHealthEntryRepository(database.manualHealthEntryDao()) }
    val supplementRepository by lazy { SupplementRepository(database.supplementEntryDao()) }
    val stackDefinitionRepository by lazy { StackDefinitionRepository(database.stackDefinitionDao()) }
    val themePreferencesStore by lazy { ThemePreferencesStore(this) }
    val unitsPreferencesStore by lazy { UnitsPreferencesStore(this) }
    val dailyRhythmStore by lazy { DailyRhythmStore(this) }
    val mealTimingStore by lazy { MealTimingStore(this) }
    val calorieAllocationStore by lazy { com.projectember.mobile.data.local.CalorieAllocationStore(this) }
    val recipeCategoryStore by lazy { com.projectember.mobile.data.local.RecipeCategoryStore(this) }
    val healthMetricPreferencesStore by lazy { HealthMetricPreferencesStore(this) }

    val backupManager by lazy {
        BackupManager(
            context = applicationContext,
            database = database,
            ketoRepository = ketoRepository,
            recipeRepository = recipeRepository,
            exerciseRepository = exerciseRepository,
            exerciseCategoryRepository = exerciseCategoryRepository,
            weightRepository = weightRepository,
            supplementRepository = supplementRepository,
            stackDefinitionRepository = stackDefinitionRepository,
            manualHealthEntryRepository = manualHealthEntryRepository,
            ketoTargetsStore = ketoTargetsStore,
            themePreferencesStore = themePreferencesStore,
            unitsPreferencesStore = unitsPreferencesStore,
            dailyRhythmStore = dailyRhythmStore,
            mealTimingStore = mealTimingStore,
            healthMetricPreferencesStore = healthMetricPreferencesStore,
            appVersion = BuildConfig.VERSION_NAME
        )
    }

    val nightlyBackupStore by lazy { NightlyBackupStore(this) }

    val nightlyBackupEngine by lazy {
        NightlyBackupEngine(
            context = applicationContext,
            backupManager = backupManager,
            store = nightlyBackupStore
        )
    }

    val recipeImportExportManager by lazy {
        RecipeImportExportManager(
            context = applicationContext,
            recipeRepository = recipeRepository,
            appVersion = BuildConfig.VERSION_NAME
        )
    }

    val ketoImportManager by lazy {
        KetoImportManager(
            context = applicationContext,
            ketoRepository = ketoRepository
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            DatabaseSeeder.seed(database)
            // Repair any pre-existing keto entries whose recipeId no longer points
            // to a recipe in the database.  This is idempotent and a no-op once
            // all dangling references have been cleared.
            ketoRepository.clearDanglingRecipeReferences()
            // One-time migration: if the legacy WeightStore (SharedPreferences) has a
            // saved weight entry AND the Room weight_entries table is empty, migrate
            // that single entry into Room so no historical data is lost.
            migrateWeightStoreIfNeeded()
            // Schedule daily lightweight Health Connect sync (around 10:00 local)
            HealthSyncReceiver.scheduleDaily(applicationContext)
            // Schedule periodic widget updates for day rollover
            WidgetUpdateWorker.schedule(applicationContext)
            // Schedule nightly master backup at 23:59 local time
            NightlyBackupScheduler.schedule(applicationContext)
        }
    }

    /**
     * Reads the legacy WeightStore SharedPreferences (key "weight_log") and inserts the
     * stored entry into Room if Room currently has no weight entries.  This is safe to
     * call repeatedly — it is a no-op once Room has any entries.
     */
    private suspend fun migrateWeightStoreIfNeeded() {
        if (weightRepository.count() > 0) return  // Room already has data — nothing to do
        val prefs = getSharedPreferences("weight_log", MODE_PRIVATE)
        val bits = prefs.getLong("weight_kg_bits", 0L)
        val date = prefs.getString("weight_date", "") ?: ""
        if (bits == 0L || date.isBlank()) return  // No legacy data
        val kg = Double.fromBits(bits)
        if (kg <= 0) return
        weightRepository.insert(WeightEntry(entryDate = date, weightKg = kg))
    }
}
