package com.projectember.mobile.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Keto : Screen("keto")
    data object KetoAddEntry : Screen("keto_add_entry")
    data object Recipes : Screen("recipes")
    data object Eira : Screen("eira")
}
