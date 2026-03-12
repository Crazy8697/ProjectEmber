package com.projectember.mobile.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Keto : Screen("keto")
    data object KetoAddEntry : Screen("keto_add_entry")
    data object KetoEditEntry : Screen("keto_edit_entry/{entryId}") {
        fun createRoute(entryId: Int) = "keto_edit_entry/$entryId"
    }
    data object Recipes : Screen("recipes")
    data object Eira : Screen("eira")
}
