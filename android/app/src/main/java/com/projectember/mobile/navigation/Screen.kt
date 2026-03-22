package com.projectember.mobile.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Keto : Screen("keto")
    data object KetoAddEntry : Screen("keto_add_entry")
    data object KetoEditEntry : Screen("keto_edit_entry/{entryId}") {
        fun createRoute(entryId: Int) = "keto_edit_entry/$entryId"
    }
    data object KetoTrends : Screen("keto_trends?metric={metric}") {
        fun createRoute(metric: String = "") =
            if (metric.isBlank()) "keto_trends" else "keto_trends?metric=$metric"
    }
    data object Recipes : Screen("recipes")
    data object RecipeAdd : Screen("recipe_add")
    data object RecipeEdit : Screen("recipe_edit/{recipeId}") {
        fun createRoute(recipeId: Int) = "recipe_edit/$recipeId"
    }
    data object Eira : Screen("eira")
    data object KetoTargets : Screen("keto_targets")
    data object KetoSettings : Screen("keto_settings")
    data object Settings : Screen("settings")
    data object Exercise : Screen("exercise")
    data object ExerciseAddEntry : Screen("exercise_add_entry?initialDate={initialDate}") {
        fun createRoute(initialDate: String = "") =
            if (initialDate.isBlank()) "exercise_add_entry" else "exercise_add_entry?initialDate=$initialDate"
    }
    data object ExerciseEditEntry : Screen("exercise_edit_entry/{entryId}") {
        fun createRoute(entryId: Int) = "exercise_edit_entry/$entryId"
    }
    data object RecipeNerdMode : Screen("recipe_nerd_mode")
    data object KetoNerdMode : Screen("keto_nerd_mode")
    data object WeightHistory : Screen("weight_history")
    data object Health : Screen("health")
    data object HealthMetricTrends : Screen("health_metric_trends/{metric}") {
        fun createRoute(metric: String) = "health_metric_trends/$metric"
    }
    data object Supplements : Screen("supplements")
    data object StackDefinitionAdd : Screen("stack_definition_add")
    data object StackDefinitionEdit : Screen("stack_definition_edit/{definitionId}") {
        fun createRoute(definitionId: Int) = "stack_definition_edit/$definitionId"
    }
    data object JsonImport : Screen("json_import")
}
