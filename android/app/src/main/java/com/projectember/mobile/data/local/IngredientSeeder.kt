package com.projectember.mobile.data.local

import com.projectember.mobile.data.local.dao.IngredientDao
import com.projectember.mobile.data.local.entities.Ingredient

/**
 * Seeds the Ingredient Index with a practical keto-friendly starter set.
 *
 * All nutrition values are per [defaultAmount] grams/ml (typically 100 g).
 * The Recipe Builder scales these proportionally when the user enters a different quantity.
 *
 * Only runs once — no-op if the ingredients table already has rows.
 */
object IngredientSeeder {

    suspend fun seed(dao: IngredientDao) {
        if (dao.count() > 0) return
        dao.insertAll(BUILT_IN_INGREDIENTS)
    }

    private val BUILT_IN_INGREDIENTS = listOf(
        // ── Proteins ────────────────────────────────────────────────────────
        Ingredient(
            name = "Chicken Breast (raw)",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 165.0, proteinG = 31.0, fatG = 3.6,
            netCarbsG = 0.0, totalCarbsG = 0.0, fiberG = 0.0,
            sodiumMg = 74.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Ground Beef 80/20",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 254.0, proteinG = 17.4, fatG = 20.0,
            netCarbsG = 0.0, totalCarbsG = 0.0, fiberG = 0.0,
            sodiumMg = 75.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Salmon (Atlantic)",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 208.0, proteinG = 20.0, fatG = 13.0,
            netCarbsG = 0.0, totalCarbsG = 0.0, fiberG = 0.0,
            sodiumMg = 59.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Whole Egg",
            defaultAmount = 50.0, defaultUnit = "g",
            calories = 72.0, proteinG = 6.3, fatG = 4.8,
            netCarbsG = 0.4, totalCarbsG = 0.4, fiberG = 0.0,
            sodiumMg = 71.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Bacon",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 541.0, proteinG = 37.0, fatG = 42.0,
            netCarbsG = 1.4, totalCarbsG = 1.4, fiberG = 0.0,
            sodiumMg = 1717.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Beef Ribeye Steak",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 291.0, proteinG = 24.0, fatG = 21.0,
            netCarbsG = 0.0, totalCarbsG = 0.0, fiberG = 0.0,
            sodiumMg = 63.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Pork Belly",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 518.0, proteinG = 9.3, fatG = 53.0,
            netCarbsG = 0.0, totalCarbsG = 0.0, fiberG = 0.0,
            sodiumMg = 39.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Shrimp",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 99.0, proteinG = 24.0, fatG = 0.3,
            netCarbsG = 0.2, totalCarbsG = 0.2, fiberG = 0.0,
            sodiumMg = 111.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Tuna (canned, water)",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 116.0, proteinG = 26.0, fatG = 1.0,
            netCarbsG = 0.0, totalCarbsG = 0.0, fiberG = 0.0,
            sodiumMg = 337.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Pork Chops",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 231.0, proteinG = 25.0, fatG = 14.0,
            netCarbsG = 0.0, totalCarbsG = 0.0, fiberG = 0.0,
            sodiumMg = 62.0,
            isBuiltIn = true
        ),
        // ── Dairy & Fats ────────────────────────────────────────────────────
        Ingredient(
            name = "Cheddar Cheese",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 402.0, proteinG = 25.0, fatG = 33.0,
            netCarbsG = 1.3, totalCarbsG = 1.3, fiberG = 0.0,
            sodiumMg = 621.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Mozzarella",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 280.0, proteinG = 28.0, fatG = 17.0,
            netCarbsG = 3.1, totalCarbsG = 3.1, fiberG = 0.0,
            sodiumMg = 627.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Parmesan Cheese",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 431.0, proteinG = 38.0, fatG = 29.0,
            netCarbsG = 3.2, totalCarbsG = 3.2, fiberG = 0.0,
            sodiumMg = 1529.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Cream Cheese",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 342.0, proteinG = 6.0, fatG = 34.0,
            netCarbsG = 4.1, totalCarbsG = 4.1, fiberG = 0.0,
            sodiumMg = 321.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Butter",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 717.0, proteinG = 0.9, fatG = 81.0,
            netCarbsG = 0.1, totalCarbsG = 0.1, fiberG = 0.0,
            sodiumMg = 576.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Heavy Cream",
            defaultAmount = 100.0, defaultUnit = "ml",
            calories = 345.0, proteinG = 2.1, fatG = 36.0,
            netCarbsG = 2.8, totalCarbsG = 2.8, fiberG = 0.0,
            sodiumMg = 38.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Sour Cream",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 198.0, proteinG = 2.4, fatG = 19.0,
            netCarbsG = 4.6, totalCarbsG = 4.6, fiberG = 0.0,
            sodiumMg = 53.0,
            isBuiltIn = true
        ),
        // ── Oils ────────────────────────────────────────────────────────────
        Ingredient(
            name = "Olive Oil",
            defaultAmount = 15.0, defaultUnit = "ml",
            calories = 133.0, proteinG = 0.0, fatG = 15.0,
            netCarbsG = 0.0, totalCarbsG = 0.0, fiberG = 0.0,
            sodiumMg = 0.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Coconut Oil",
            defaultAmount = 15.0, defaultUnit = "ml",
            calories = 130.0, proteinG = 0.0, fatG = 15.0,
            netCarbsG = 0.0, totalCarbsG = 0.0, fiberG = 0.0,
            sodiumMg = 0.0,
            isBuiltIn = true
        ),
        // ── Vegetables ──────────────────────────────────────────────────────
        Ingredient(
            name = "Avocado",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 160.0, proteinG = 2.0, fatG = 15.0,
            netCarbsG = 1.8, totalCarbsG = 8.5, fiberG = 6.7,
            potassiumMg = 485.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Broccoli",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 34.0, proteinG = 2.8, fatG = 0.4,
            netCarbsG = 4.0, totalCarbsG = 6.6, fiberG = 2.6,
            potassiumMg = 316.0, sodiumMg = 33.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Spinach",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 23.0, proteinG = 2.9, fatG = 0.4,
            netCarbsG = 1.4, totalCarbsG = 3.6, fiberG = 2.2,
            potassiumMg = 558.0, sodiumMg = 79.0, magnesiumMg = 79.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Cauliflower",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 25.0, proteinG = 1.9, fatG = 0.3,
            netCarbsG = 3.0, totalCarbsG = 5.0, fiberG = 2.0,
            potassiumMg = 299.0, sodiumMg = 30.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Mushrooms (white)",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 22.0, proteinG = 3.1, fatG = 0.3,
            netCarbsG = 2.3, totalCarbsG = 3.3, fiberG = 1.0,
            potassiumMg = 318.0, sodiumMg = 5.0,
            isBuiltIn = true
        ),
        Ingredient(
            name = "Zucchini",
            defaultAmount = 100.0, defaultUnit = "g",
            calories = 17.0, proteinG = 1.2, fatG = 0.3,
            netCarbsG = 2.1, totalCarbsG = 3.1, fiberG = 1.0,
            potassiumMg = 261.0, sodiumMg = 8.0,
            isBuiltIn = true
        ),
        // ── Nuts & Seeds ────────────────────────────────────────────────────
        Ingredient(
            name = "Almonds",
            defaultAmount = 28.0, defaultUnit = "g",
            calories = 162.0, proteinG = 6.0, fatG = 14.0,
            netCarbsG = 2.6, totalCarbsG = 6.1, fiberG = 3.5,
            potassiumMg = 200.0, magnesiumMg = 76.0,
            isBuiltIn = true
        )
    )
}
