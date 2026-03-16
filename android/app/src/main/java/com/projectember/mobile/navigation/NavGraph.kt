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
import com.projectember.mobile.ui.screens.AddEditExerciseScreen
import com.projectember.mobile.ui.screens.AddEditExerciseViewModelFactory
import com.projectember.mobile.ui.screens.AddEditExerciseViewModel
import com.projectember.mobile.ui.screens.AddEditRecipeScreen
import com.projectember.mobile.ui.screens.AddEditRecipeViewModelFactory
import com.projectember.mobile.ui.screens.AddEditRecipeViewModel
import com.projectember.mobile.ui.screens.AddKetoEntryScreen
import com.projectember.mobile.ui.screens.AddKetoEntryViewModelFactory
import com.projectember.mobile.ui.screens.AddKetoEntryViewModel
import com.projectember.mobile.ui.screens.EiraScreen
import com.projectember.mobile.ui.screens.ExerciseScreen
import com.projectember.mobile.ui.screens.ExerciseViewModel
import com.projectember.mobile.ui.screens.ExerciseViewModelFactory
import com.projectember.mobile.ui.screens.HomeScreen
import com.projectember.mobile.ui.screens.HomeViewModel
import com.projectember.mobile.ui.screens.HomeViewModelFactory
import com.projectember.mobile.ui.screens.KetoScreen
import com.projectember.mobile.ui.screens.KetoTrendsScreen
import com.projectember.mobile.ui.screens.KetoViewModel
import com.projectember.mobile.ui.screens.KetoViewModelFactory
import com.projectember.mobile.ui.screens.KetoTargetsScreen
import com.projectember.mobile.ui.screens.KetoTargetsViewModel
import com.projectember.mobile.ui.screens.KetoTargetsViewModelFactory
import com.projectember.mobile.ui.screens.RecipesScreen
import com.projectember.mobile.ui.screens.RecipesViewModel
import com.projectember.mobile.ui.screens.RecipesViewModelFactory
import com.projectember.mobile.ui.screens.SettingsScreen
import com.projectember.mobile.ui.screens.SettingsViewModel
import com.projectember.mobile.ui.screens.SettingsViewModelFactory
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
        composable(Screen.Home.route) {
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModelFactory(
                    app.syncRepository,
                    app.syncManager,
                    app.ketoRepository,
                    app.ketoTargetsStore,
                    app.weightRepository,
                    app.unitsPreferencesStore,
                    app.exerciseRepository
                )
            )
            HomeScreen(
                viewModel = viewModel,
                onNavigateToKeto = { navController.navigate(Screen.Keto.route) },
                onNavigateToRecipes = { navController.navigate(Screen.Recipes.route) },
                onNavigateToExercise = { navController.navigate(Screen.Exercise.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToTrends = { navController.navigate(Screen.KetoTrends.createRoute("calories")) }
            )
        }

        composable(Screen.Keto.route) {
            val viewModel: KetoViewModel = viewModel(
                factory = KetoViewModelFactory(
                    app.ketoRepository, app.ketoTargetsStore,
                    app.weightRepository, app.exerciseRepository,
                    app.exerciseCategoryRepository, app.unitsPreferencesStore
                )
            )
            KetoScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddEntry = { navController.navigate(Screen.KetoAddEntry.route) },
                onNavigateToEditEntry = { entryId ->
                    navController.navigate(Screen.KetoEditEntry.createRoute(entryId))
                },
                onNavigateToEditExercise = { entryId ->
                    navController.navigate(Screen.ExerciseEditEntry.createRoute(entryId))
                },
                onNavigateToTargets = { navController.navigate(Screen.KetoTargets.route) },
                onNavigateToTrends = { metric -> navController.navigate(Screen.KetoTrends.createRoute(metric)) },
                onNavigateToLogExercise = { date ->
                    navController.navigate(Screen.ExerciseAddEntry.createRoute(date))
                },
                onNavigateToWeightHistory = { navController.navigate(Screen.WeightHistory.route) }
            )
        }

        composable(Screen.KetoAddEntry.route) {
            val viewModel: AddKetoEntryViewModel = viewModel(
                factory = AddKetoEntryViewModelFactory(
                    app.ketoRepository,
                    unitsPreferencesStore = app.unitsPreferencesStore
                )
            )
            AddKetoEntryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.KetoEditEntry.route,
            arguments = listOf(navArgument("entryId") { type = NavType.IntType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getInt("entryId")
            if (entryId == null) {
                navController.popBackStack()
                return@composable
            }
            val viewModel: AddKetoEntryViewModel = viewModel(
                factory = AddKetoEntryViewModelFactory(
                    app.ketoRepository,
                    entryId,
                    app.unitsPreferencesStore
                )
            )
            AddKetoEntryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

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
                onNavigateToEditRecipe = { recipeId ->
                    navController.navigate(Screen.RecipeEdit.createRoute(recipeId))
                }
            )
        }

        composable(Screen.RecipeAdd.route) {
            val viewModel: AddEditRecipeViewModel = viewModel(
                factory = AddEditRecipeViewModelFactory(
                    app.recipeRepository,
                    unitsPreferencesStore = app.unitsPreferencesStore
                )
            )
            AddEditRecipeScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.RecipeEdit.route,
            arguments = listOf(navArgument("recipeId") { type = NavType.IntType })
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getInt("recipeId")
            if (recipeId == null) {
                navController.popBackStack()
                return@composable
            }
            val viewModel: AddEditRecipeViewModel = viewModel(
                factory = AddEditRecipeViewModelFactory(
                    app.recipeRepository,
                    recipeId,
                    app.unitsPreferencesStore
                )
            )
            AddEditRecipeScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Eira.route) {
            EiraScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.KetoTargets.route) {
            val viewModel: KetoTargetsViewModel = viewModel(
                factory = KetoTargetsViewModelFactory(
                    app.ketoTargetsStore,
                    app.unitsPreferencesStore
                )
            )
            KetoTargetsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.KetoTrends.route,
            arguments = listOf(navArgument("metric") {
                type = NavType.StringType
                defaultValue = ""
            })
        ) { backStackEntry ->
            val metric = backStackEntry.arguments?.getString("metric") ?: ""
            val viewModel: KetoViewModel = viewModel(
                factory = KetoViewModelFactory(
                    app.ketoRepository, app.ketoTargetsStore,
                    app.weightRepository, app.exerciseRepository,
                    app.exerciseCategoryRepository, app.unitsPreferencesStore
                )
            )
            KetoTrendsScreen(
                viewModel = viewModel,
                initialMetric = metric,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(
                    app.syncRepository,
                    app.healthConnectManager,
                    app.backupManager,
                    app.themePreferencesStore,
                    app.unitsPreferencesStore
                )
            )
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Exercise ──────────────────────────────────────────────────────────
        composable(Screen.Exercise.route) {
            val viewModel: ExerciseViewModel = viewModel(
                factory = ExerciseViewModelFactory(
                    app.exerciseRepository,
                    app.exerciseCategoryRepository
                )
            )
            ExerciseScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddEntry = { date ->
                    navController.navigate(Screen.ExerciseAddEntry.createRoute(date))
                },
                onNavigateToEditEntry = { entryId ->
                    navController.navigate(Screen.ExerciseEditEntry.createRoute(entryId))
                }
            )
        }

        composable(
            route = Screen.ExerciseAddEntry.route,
            arguments = listOf(navArgument("initialDate") {
                type = NavType.StringType
                defaultValue = ""
            })
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
            val entryId = backStackEntry.arguments?.getInt("entryId")
            if (entryId == null) {
                navController.popBackStack()
                return@composable
            }
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

        composable(Screen.WeightHistory.route) {
            val viewModel: WeightHistoryViewModel = viewModel(
                factory = WeightHistoryViewModelFactory(
                    app.weightRepository,
                    app.unitsPreferencesStore
                )
            )
            WeightHistoryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
