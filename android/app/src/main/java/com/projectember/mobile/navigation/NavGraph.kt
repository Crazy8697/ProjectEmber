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
import com.projectember.mobile.ui.screens.AddKetoEntryScreen
import com.projectember.mobile.ui.screens.AddKetoEntryViewModelFactory
import com.projectember.mobile.ui.screens.AddKetoEntryViewModel
import com.projectember.mobile.ui.screens.EiraScreen
import com.projectember.mobile.ui.screens.HomeScreen
import com.projectember.mobile.ui.screens.HomeViewModel
import com.projectember.mobile.ui.screens.HomeViewModelFactory
import com.projectember.mobile.ui.screens.KetoScreen
import com.projectember.mobile.ui.screens.KetoViewModel
import com.projectember.mobile.ui.screens.KetoViewModelFactory
import com.projectember.mobile.ui.screens.RecipesScreen
import com.projectember.mobile.ui.screens.RecipesViewModel
import com.projectember.mobile.ui.screens.RecipesViewModelFactory

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
                factory = HomeViewModelFactory(app.syncRepository, app.syncManager)
            )
            HomeScreen(
                viewModel = viewModel,
                onNavigateToKeto = { navController.navigate(Screen.Keto.route) },
                onNavigateToRecipes = { navController.navigate(Screen.Recipes.route) },
                onNavigateToEira = { navController.navigate(Screen.Eira.route) }
            )
        }

        composable(Screen.Keto.route) {
            val viewModel: KetoViewModel = viewModel(
                factory = KetoViewModelFactory(app.ketoRepository)
            )
            KetoScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddEntry = { navController.navigate(Screen.KetoAddEntry.route) },
                onNavigateToEditEntry = { entryId ->
                    navController.navigate(Screen.KetoEditEntry.createRoute(entryId))
                }
            )
        }

        composable(Screen.KetoAddEntry.route) {
            val viewModel: AddKetoEntryViewModel = viewModel(
                factory = AddKetoEntryViewModelFactory(app.ketoRepository)
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
                factory = AddKetoEntryViewModelFactory(app.ketoRepository, entryId)
            )
            AddKetoEntryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Recipes.route) {
            val viewModel: RecipesViewModel = viewModel(
                factory = RecipesViewModelFactory(app.recipeRepository)
            )
            RecipesScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Eira.route) {
            EiraScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
