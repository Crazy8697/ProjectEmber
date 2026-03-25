package com.projectember.mobile.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.projectember.mobile.EmberApplication
import com.projectember.mobile.data.local.HealthMetric
import com.projectember.mobile.ui.import.JsonImportScreen
import com.projectember.mobile.ui.screens.AddEditExerciseScreen
import com.projectember.mobile.ui.screens.AddEditExerciseViewModel
import com.projectember.mobile.ui.screens.AddEditExerciseViewModelFactory
import com.projectember.mobile.ui.screens.AddEditRecipeScreen
import com.projectember.mobile.ui.screens.AddEditRecipeViewModel
import com.projectember.mobile.ui.screens.AddEditRecipeViewModelFactory
import com.projectember.mobile.ui.screens.AddEditStackDefinitionScreen
import com.projectember.mobile.ui.screens.AddEditStackDefinitionViewModel
import com.projectember.mobile.ui.screens.AddEditStackDefinitionViewModelFactory
import com.projectember.mobile.ui.screens.AddKetoEntryScreen
import com.projectember.mobile.ui.screens.AddKetoEntryViewModel
import com.projectember.mobile.ui.screens.AddKetoEntryViewModelFactory
import com.projectember.mobile.ui.screens.ExerciseScreen
import com.projectember.mobile.ui.screens.ExerciseViewModel
import com.projectember.mobile.ui.screens.ExerciseViewModelFactory
import com.projectember.mobile.ui.screens.HealthMetricTrendsScreen
import com.projectember.mobile.ui.screens.HealthMetricTrendsViewModel
import com.projectember.mobile.ui.screens.HealthMetricTrendsViewModelFactory
import com.projectember.mobile.ui.screens.HealthScreen
import com.projectember.mobile.ui.screens.HealthViewModel
import com.projectember.mobile.ui.screens.HealthViewModelFactory
import com.projectember.mobile.ui.screens.HomeScreen
import com.projectember.mobile.ui.screens.HomeViewModel
import com.projectember.mobile.ui.screens.HomeViewModelFactory
import com.projectember.mobile.ui.screens.KetoNerdModeScreen
import com.projectember.mobile.ui.screens.KetoNerdModeViewModel
import com.projectember.mobile.ui.screens.KetoNerdModeViewModelFactory
import com.projectember.mobile.ui.screens.KetoScreen
import com.projectember.mobile.ui.screens.KetoSettingsScreen
import com.projectember.mobile.ui.screens.KetoTrendsScreen
import com.projectember.mobile.ui.screens.KetoViewModel
import com.projectember.mobile.ui.screens.KetoViewModelFactory
import com.projectember.mobile.ui.screens.BulkCategoryScreen
import com.projectember.mobile.ui.screens.BulkCategoryViewModel
import com.projectember.mobile.ui.screens.BulkCategoryViewModelFactory
import com.projectember.mobile.ui.screens.RecipeNerdModeScreen
import com.projectember.mobile.ui.screens.RecipeNerdModeViewModel
import com.projectember.mobile.ui.screens.RecipeNerdModeViewModelFactory
import com.projectember.mobile.ui.screens.RecipesScreen
import com.projectember.mobile.ui.screens.RecipesViewModel
import com.projectember.mobile.ui.screens.RecipesViewModelFactory
import com.projectember.mobile.ui.screens.SettingsScreen
import com.projectember.mobile.ui.screens.SettingsViewModel
import com.projectember.mobile.ui.screens.SettingsViewModelFactory
import com.projectember.mobile.ui.screens.StacksScreen
import com.projectember.mobile.ui.screens.StacksViewModel
import com.projectember.mobile.ui.screens.StacksViewModelFactory
import com.projectember.mobile.ui.screens.WeightHistoryScreen
import com.projectember.mobile.ui.screens.WeightHistoryViewModel
import com.projectember.mobile.ui.screens.WeightHistoryViewModelFactory

@Composable
fun EmberNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val app = LocalContext.current.applicationContext as EmberApplication

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // Home
        composable(Screen.Home.route) {
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModelFactory(
                    app.syncRepository,
                    app.syncManager,
                    app.ketoRepository,
                    app.ketoTargetsStore,
                    app.calorieAllocationStore,
                    app.weightRepository,
                    app.unitsPreferencesStore,
                    app.exerciseRepository,
                    app.dailyRhythmStore,
                    app.mealTimingStore
                )
            )
            HomeScreen(
                viewModel = viewModel,
                onNavigateToKeto = { navController.navigate(Screen.Keto.route) },
                onNavigateToRecipes = { navController.navigate(Screen.Recipes.route) },
                onNavigateToExercise = { navController.navigate(Screen.Exercise.route) },
                onNavigateToHealth = { navController.navigate(Screen.Health.route) },
                onNavigateToSupplements = { navController.navigate(Screen.Supplements.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToTrends = { navController.navigate(Screen.KetoTrends.createRoute("calories")) }
            )
        }

        // Keto list
        composable(Screen.Keto.route) {
            val viewModel: KetoViewModel = viewModel(
                factory = KetoViewModelFactory(
                    app.ketoRepository,
                    app.ketoTargetsStore,
                    app.calorieAllocationStore,
                    app.weightRepository,
                    app.exerciseRepository,
                    app.exerciseCategoryRepository,
                    app.unitsPreferencesStore,
                    app.dailyRhythmStore,
                    app.mealTimingStore,
                    app.healthMetricPreferencesStore,
                    app.manualHealthEntryRepository
                )
            )
            KetoScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddEntry = { navController.navigate(Screen.KetoAddEntry.route) },
                onNavigateToEditEntry = { entryId -> navController.navigate(Screen.KetoEditEntry.createRoute(entryId)) },
                onNavigateToEditExercise = { entryId -> navController.navigate(Screen.ExerciseEditEntry.createRoute(entryId)) },
                onNavigateToTargets = { navController.navigate(Screen.KetoTargets.route) },
                onNavigateToTrends = { metric -> navController.navigate(Screen.KetoTrends.createRoute(metric)) },
                onNavigateToLogExercise = { date -> navController.navigate(Screen.ExerciseAddEntry.createRoute(date)) },
                onNavigateToWeightHistory = { navController.navigate(Screen.WeightHistory.route) },
            )
        }

        // Add / Edit Keto entry
        composable(Screen.KetoAddEntry.route) {
            val viewModel: AddKetoEntryViewModel = viewModel(
                factory = AddKetoEntryViewModelFactory(
                    app.ketoRepository,
                    unitsPreferencesStore = app.unitsPreferencesStore,
                    recipeRepository = app.recipeRepository,
                    ketoImportManager = app.ketoImportManager
                )
            )
            AddKetoEntryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToRecipes = { navController.navigate(Screen.Recipes.route) }
            )
        }

        composable(
            route = Screen.KetoEditEntry.route,
            arguments = listOf(navArgument("entryId") { type = NavType.IntType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getInt("entryId") ?: return@composable
            val viewModel: AddKetoEntryViewModel = viewModel(
                factory = AddKetoEntryViewModelFactory(
                    app.ketoRepository,
                    entryId,
                    app.unitsPreferencesStore,
                    app.recipeRepository,
                    app.ketoImportManager
                )
            )
            AddKetoEntryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToRecipes = { navController.navigate(Screen.Recipes.route) }
            )
        }

        // Recipes
        composable(Screen.Recipes.route) {
            val viewModel: RecipesViewModel = viewModel(
                factory = RecipesViewModelFactory(
                    app.recipeRepository,
                    app.ketoRepository,
                    app.unitsPreferencesStore
                )
            )
            RecipesScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddRecipe = { navController.navigate(Screen.RecipeAdd.route) },
                onNavigateToEditRecipe = { recipeId -> navController.navigate(Screen.RecipeEdit.createRoute(recipeId)) },
                onNavigateToNerdMode = { navController.navigate(Screen.RecipeNerdMode.route) },
                onNavigateToBulkCategory = { navController.navigate(Screen.RecipeBulkCategory.route) }
            )
        }

        composable(Screen.RecipeBulkCategory.route) {
            val viewModel: BulkCategoryViewModel = viewModel(
                factory = BulkCategoryViewModelFactory(
                    app.recipeRepository,
                    app.recipeCategoryStore
                )
            )
            BulkCategoryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.RecipeNerdMode.route) {
            val viewModel: RecipeNerdModeViewModel = viewModel(
                factory = RecipeNerdModeViewModelFactory(
                    app.recipeImportExportManager,
                    app.recipeRepository,
                    app.recipeCategoryStore
                )
            )
            RecipeNerdModeScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.JsonImport.route + "?domain={domain}",
            arguments = listOf(navArgument("domain") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            JsonImportScreen(
                onNavigateBack = { navController.popBackStack() },
                initialDomain = backStackEntry.arguments?.getString("domain")?.takeIf { s -> s.isNotBlank() }
            )
        }

        composable(Screen.KetoNerdMode.route) {
            val viewModel: KetoNerdModeViewModel = viewModel(
                factory = KetoNerdModeViewModelFactory(app.ketoImportManager)
            )
            KetoNerdModeScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Add / Edit Recipe
        composable(Screen.RecipeAdd.route) {
            val viewModel: AddEditRecipeViewModel = viewModel(
                factory = AddEditRecipeViewModelFactory(
                    app.recipeRepository,
                    unitsPreferencesStore = app.unitsPreferencesStore,
                    recipeCategoryStore = app.recipeCategoryStore,
                    recipeImportExportManager = app.recipeImportExportManager
                )
            )
            AddEditRecipeScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Screen.RecipeEdit.route,
            arguments = listOf(navArgument("recipeId") { type = NavType.IntType })
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getInt("recipeId") ?: return@composable
            val viewModel: AddEditRecipeViewModel = viewModel(
                factory = AddEditRecipeViewModelFactory(
                    app.recipeRepository,
                    recipeId,
                    app.unitsPreferencesStore,
                    app.recipeCategoryStore,
                    app.recipeImportExportManager
                )
            )
            AddEditRecipeScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // Keto settings / trends
        composable(Screen.KetoTargets.route) {
            KetoSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.KetoSettings.route) {
            KetoSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.KetoTrends.route,
            arguments = listOf(navArgument("metric") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            val metric = backStackEntry.arguments?.getString("metric") ?: ""
            val viewModel: KetoViewModel = viewModel(
                factory = KetoViewModelFactory(
                    app.ketoRepository,
                    app.ketoTargetsStore,
                    app.calorieAllocationStore,
                    app.weightRepository,
                    app.exerciseRepository,
                    app.exerciseCategoryRepository,
                    app.unitsPreferencesStore,
                    app.dailyRhythmStore,
                    app.mealTimingStore,
                    app.healthMetricPreferencesStore,
                    app.manualHealthEntryRepository
                )
            )
            KetoTrendsScreen(
                viewModel = viewModel,
                initialMetric = metric,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToJsonImport = { domain ->
                    val route = if (!domain.isNullOrBlank()) {
                        "${Screen.JsonImport.route}?domain=${domain}"
                    } else {
                        Screen.JsonImport.route
                    }
                    navController.navigate(route)
                }
            )
        }

        // Settings
        composable(Screen.Settings.route) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(
                    app.syncRepository,
                    app.ketoRepository,
                    app.recipeRepository,
                    app.healthConnectManager,
                    app.backupManager,
                    app.themePreferencesStore,
                    app.unitsPreferencesStore,
                    app.dailyRhythmStore,
                    app.mealTimingStore,
                    app.healthMetricPreferencesStore,
                    app.nightlyBackupStore,
                    app.nightlyBackupEngine
                )
            )
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Exercise flows
        composable(Screen.Exercise.route) {
            val viewModel: ExerciseViewModel = viewModel(
                factory = ExerciseViewModelFactory(
                    app.exerciseRepository,
                    app.exerciseCategoryRepository,
                    app.healthConnectManager,
                    app.healthMetricPreferencesStore,
                    app.manualHealthEntryRepository
                )
            )
            ExerciseScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddEntry = { date -> navController.navigate(Screen.ExerciseAddEntry.createRoute(date)) },
                onNavigateToEditEntry = { entryId -> navController.navigate(Screen.ExerciseEditEntry.createRoute(entryId)) },
                onNavigateToTrends = { metric -> navController.navigate(Screen.HealthMetricTrends.createRoute(metric.name)) }
            )
        }

        composable(
            route = Screen.ExerciseAddEntry.route,
            arguments = listOf(navArgument("initialDate") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            val initialDate = backStackEntry.arguments?.getString("initialDate")?.takeIf { it.isNotBlank() }
            val viewModel: AddEditExerciseViewModel = viewModel(
                factory = AddEditExerciseViewModelFactory(
                    app.exerciseRepository,
                    app.exerciseCategoryRepository,
                    initialDate = initialDate
                )
            )
            AddEditExerciseScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ExerciseEditEntry.route,
            arguments = listOf(navArgument("entryId") { type = NavType.IntType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getInt("entryId") ?: return@composable
            val viewModel: AddEditExerciseViewModel = viewModel(
                factory = AddEditExerciseViewModelFactory(
                    app.exerciseRepository,
                    app.exerciseCategoryRepository,
                    editEntryId = entryId
                )
            )
            AddEditExerciseScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Weight history
        composable(Screen.WeightHistory.route) {
            val viewModel: WeightHistoryViewModel = viewModel(
                factory = WeightHistoryViewModelFactory(app.weightRepository, app.unitsPreferencesStore)
            )
            WeightHistoryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToJsonImport = { domain ->
                    val route = if (!domain.isNullOrBlank()) {
                        "${Screen.JsonImport.route}?domain=${domain}"
                    } else {
                        Screen.JsonImport.route
                    }
                    navController.navigate(route)
                }
            )
        }

        // Health
        composable(Screen.Health.route) {
            val viewModel: HealthViewModel = viewModel(
                factory = HealthViewModelFactory(
                    app.healthConnectManager,
                    app.healthMetricPreferencesStore,
                    app.manualHealthEntryRepository
                )
            )
            HealthScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTrends = { metric -> navController.navigate(Screen.HealthMetricTrends.createRoute(metric.name)) }
            )
        }

        composable(
            route = Screen.HealthMetricTrends.route,
            arguments = listOf(navArgument("metric") { type = NavType.StringType })
        ) { backStackEntry ->
            val metricName = backStackEntry.arguments?.getString("metric") ?: return@composable
            val metric = runCatching { HealthMetric.valueOf(metricName) }.getOrNull() ?: return@composable
            val viewModel: HealthMetricTrendsViewModel = viewModel(
                key = "trends_$metricName",
                factory = HealthMetricTrendsViewModelFactory(
                    metric,
                    app.manualHealthEntryRepository,
                    app.healthMetricPreferencesStore,
                    app.healthConnectManager
                )
            )
            HealthMetricTrendsScreen(
                metric = metric,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToJsonImport = { domain ->
                    val route = if (!domain.isNullOrBlank()) {
                        "${Screen.JsonImport.route}?domain=${domain}"
                    } else {
                        Screen.JsonImport.route
                    }
                    navController.navigate(route)
                }
            )
        }

        // Supplements / Stacks
        composable(Screen.Supplements.route) {
            val viewModel: StacksViewModel = viewModel(
                factory = StacksViewModelFactory(
                    app.stackDefinitionRepository,
                    app.supplementRepository,
                    app.ketoRepository
                )
            )
            StacksScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddDefinition = { navController.navigate(Screen.StackDefinitionAdd.route) },
                onNavigateToEditDefinition = { definitionId -> navController.navigate(Screen.StackDefinitionEdit.createRoute(definitionId)) }
            )
        }

        composable(Screen.StackDefinitionAdd.route) {
            val viewModel: AddEditStackDefinitionViewModel = viewModel(
                factory = AddEditStackDefinitionViewModelFactory(app.stackDefinitionRepository)
            )
            AddEditStackDefinitionScreen(
                viewModel = viewModel,
                supplementRepository = app.supplementRepository,
                ketoRepository = app.ketoRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.StackDefinitionEdit.route,
            arguments = listOf(navArgument("definitionId") { type = NavType.IntType })
        ) { backStackEntry ->
            val definitionId = backStackEntry.arguments?.getInt("definitionId") ?: return@composable
            val viewModel: AddEditStackDefinitionViewModel = viewModel(
                factory = AddEditStackDefinitionViewModelFactory(
                    app.stackDefinitionRepository,
                    editDefinitionId = definitionId
                )
            )
            AddEditStackDefinitionScreen(
                viewModel = viewModel,
                supplementRepository = app.supplementRepository,
                ketoRepository = app.ketoRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
