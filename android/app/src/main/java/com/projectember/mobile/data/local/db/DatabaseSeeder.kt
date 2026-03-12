package com.projectember.mobile.data.local.db

import com.projectember.mobile.data.local.entities.KetoEntry
import com.projectember.mobile.data.local.entities.Recipe
import com.projectember.mobile.data.local.entities.SyncStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DatabaseSeeder {
    suspend fun seed(database: AppDatabase) {
        val ketoDao = database.ketoDao()
        val recipeDao = database.recipeDao()
        val syncDao = database.syncStatusDao()

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        if (ketoDao.count() == 0) {
            ketoDao.insertAll(
                listOf(
                    KetoEntry(
                        label = "Coffee with cream",
                        eventType = "drink",
                        calories = 120.0,
                        proteinG = 1.0,
                        fatG = 12.0,
                        netCarbsG = 2.0,
                        entryDate = today.format(dateFormatter),
                        eventTimestamp = "${today.format(dateFormatter)} 08:15"
                    ),
                    KetoEntry(
                        label = "Big Boy Salad",
                        eventType = "meal",
                        calories = 640.0,
                        proteinG = 52.0,
                        fatG = 44.0,
                        netCarbsG = 7.0,
                        entryDate = today.format(dateFormatter),
                        eventTimestamp = "${today.format(dateFormatter)} 12:30"
                    ),
                    KetoEntry(
                        label = "Tangy Beef Bowl",
                        eventType = "meal",
                        calories = 780.0,
                        proteinG = 58.0,
                        fatG = 56.0,
                        netCarbsG = 8.0,
                        entryDate = today.format(dateFormatter),
                        eventTimestamp = "${today.format(dateFormatter)} 18:10"
                    ),
                    KetoEntry(
                        label = "Electrolyte water",
                        eventType = "drink",
                        calories = 0.0,
                        proteinG = 0.0,
                        fatG = 0.0,
                        netCarbsG = 0.0,
                        entryDate = yesterday.format(dateFormatter),
                        eventTimestamp = "${yesterday.format(dateFormatter)} 09:00"
                    ),
                    KetoEntry(
                        label = "Egg, Broccoli & Cheddar Skillet",
                        eventType = "meal",
                        calories = 430.0,
                        proteinG = 26.0,
                        fatG = 32.0,
                        netCarbsG = 5.0,
                        entryDate = yesterday.format(dateFormatter),
                        eventTimestamp = "${yesterday.format(dateFormatter)} 13:05"
                    ),
                    KetoEntry(
                        label = "Cheesy Beef & Mushroom Skillet",
                        eventType = "meal",
                        calories = 690.0,
                        proteinG = 55.0,
                        fatG = 48.0,
                        netCarbsG = 6.0,
                        entryDate = yesterday.format(dateFormatter),
                        eventTimestamp = "${yesterday.format(dateFormatter)} 19:20"
                    )
                )
            )
        }

        if (recipeDao.count() == 0) {
            recipeDao.insertAll(
                listOf(
                    Recipe(
                        name = "Big Boy Salad",
                        description = "Egg-heavy keto salad with deli chicken and cheese.",
                        calories = 640.0,
                        proteinG = 52.0,
                        fatG = 44.0,
                        netCarbsG = 7.0,
                        servings = 1.0,
                        ketoNotes = "Solid default lunch. Easy to log and easy to repeat."
                    ),
                    Recipe(
                        name = "Tangy Beef Bowl",
                        description = "Ground beef, greens, broccoli, butter, and mozzarella.",
                        calories = 780.0,
                        proteinG = 58.0,
                        fatG = 56.0,
                        netCarbsG = 8.0,
                        servings = 1.0,
                        ketoNotes = "Good dinner anchor. Protein-heavy and still low carb."
                    ),
                    Recipe(
                        name = "Cheesy Beef & Mushroom Skillet",
                        description = "Beef, mushrooms, cheddar, bouillon, mustard, and olive brine.",
                        calories = 690.0,
                        proteinG = 55.0,
                        fatG = 48.0,
                        netCarbsG = 6.0,
                        servings = 1.0,
                        ketoNotes = "High satiety. Good for reheating too."
                    ),
                    Recipe(
                        name = "Egg, Broccoli & Cheddar Skillet",
                        description = "Simple skillet with eggs, broccoli, butter, and cheddar.",
                        calories = 430.0,
                        proteinG = 26.0,
                        fatG = 32.0,
                        netCarbsG = 5.0,
                        servings = 1.0,
                        ketoNotes = "Fast, cheap, and idiot-resistant."
                    ),
                    Recipe(
                        name = "Olive Brine Aioli",
                        description = "Egg, oil, olive brine, lemon, and seasonings.",
                        calories = 160.0,
                        proteinG = 1.0,
                        fatG = 17.0,
                        netCarbsG = 1.0,
                        servings = 2.0,
                        ketoNotes = "Use as a sauce booster, not a meal."
                    ),
                    Recipe(
                        name = "Lazy Keto Almost-Kimchi Bowl",
                        description = "Quick cabbage bowl with vinegar, sesame oil, and seaweed.",
                        calories = 90.0,
                        proteinG = 2.0,
                        fatG = 5.0,
                        netCarbsG = 4.0,
                        servings = 1.0,
                        ketoNotes = "Low effort side dish with decent bite."
                    )
                )
            )
        }

        syncDao.upsert(
            SyncStatus(
                id = 1,
                lastSyncTime = null,
                status = "idle",
                message = "Ready for manual sync",
                updatedAt = LocalDateTime.now().format(dateTimeFormatter)
            )
        )
    }
}
