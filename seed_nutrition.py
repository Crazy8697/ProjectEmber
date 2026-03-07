"""
seed_nutrition.py — Project Ember

Seeds the nutrition DB with common keto-friendly foods using USDA FoodData
Central values. Run once to populate. Safe to re-run (skips duplicates by name).

Usage:
    sudo -u eira python3 /home/eira/ProjectEmber/seed_nutrition.py

Or with custom DB path:
    EMBER_NUTRITION_DB=/path/to/nutrition.db python3 seed_nutrition.py
"""

import logging
import sys
import os

# Allow running from project root
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nutrition_db as db

logging.basicConfig(level=logging.INFO, format="%(message)s")
log = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Seed data — USDA FoodData Central values
# Format: (name, serving_g, calories, fat_g, protein_g, carbs_g, fiber_g, sugar_alc_g, brand, source)
# ---------------------------------------------------------------------------
SEED_FOODS = [
    # Proteins
    ("Bacon cooked",                28,   137, 10.5,  9.7,  0.1, 0.0, 0.0, "", "usda"),
    ("Beef ground 80/20 cooked",    85,   215, 13.0, 22.0,  0.0, 0.0, 0.0, "", "usda"),
    ("Beef ribeye steak cooked",    85,   221, 15.0, 21.0,  0.0, 0.0, 0.0, "", "usda"),
    ("Chicken breast cooked",       85,   140,  3.1, 26.0,  0.0, 0.0, 0.0, "", "usda"),
    ("Chicken thigh cooked",        85,   179,  9.5, 23.0,  0.0, 0.0, 0.0, "", "usda"),
    ("Egg whole large",             50,    72,  5.0,  6.3,  0.4, 0.0, 0.0, "", "usda"),
    ("Egg yolk large",              17,    55,  4.5,  2.7,  0.6, 0.0, 0.0, "", "usda"),
    ("Pork belly cooked",           85,   400, 35.0, 20.0,  0.0, 0.0, 0.0, "", "usda"),
    ("Pork chop cooked",            85,   187,  9.0, 25.0,  0.0, 0.0, 0.0, "", "usda"),
    ("Salmon Atlantic cooked",      85,   175, 10.5, 18.8,  0.0, 0.0, 0.0, "", "usda"),
    ("Sardines in oil drained",     85,   191, 10.5, 22.7,  0.0, 0.0, 0.0, "", "usda"),
    ("Shrimp cooked",               85,    84,  0.9, 17.8,  0.0, 0.0, 0.0, "", "usda"),
    ("Tuna canned in water",        85,    73,  0.5, 16.5,  0.0, 0.0, 0.0, "", "usda"),
    ("Turkey ground cooked",        85,   200, 11.2, 23.0,  0.0, 0.0, 0.0, "", "usda"),

    # Dairy and eggs
    ("Butter unsalted",             14,   102, 11.5,  0.1,  0.0, 0.0, 0.0, "", "usda"),
    ("Cheddar cheese",              28,   113,  9.3,  7.0,  0.4, 0.0, 0.0, "", "usda"),
    ("Cream cheese full fat",       28,    99,  9.8,  1.8,  1.3, 0.0, 0.0, "", "usda"),
    ("Ghee",                        14,   130, 14.5,  0.0,  0.0, 0.0, 0.0, "", "usda"),
    ("Greek yogurt full fat",      100,    97,  5.0,  9.0,  3.6, 0.0, 0.0, "", "usda"),
    ("Heavy whipping cream",        15,    52,  5.5,  0.4,  0.4, 0.0, 0.0, "", "usda"),
    ("Mozzarella whole milk",       28,    85,  6.3,  6.3,  0.6, 0.0, 0.0, "", "usda"),
    ("Parmesan grated",             28,   110,  7.3, 10.1,  0.9, 0.0, 0.0, "", "usda"),
    ("Sour cream full fat",         30,    57,  5.5,  0.7,  1.2, 0.0, 0.0, "", "usda"),

    # Fats and oils
    ("Avocado oil",                 14,   124, 14.0,  0.0,  0.0, 0.0, 0.0, "", "usda"),
    ("Coconut oil",                 14,   121, 13.5,  0.0,  0.0, 0.0, 0.0, "", "usda"),
    ("MCT oil",                     15,   130, 14.0,  0.0,  0.0, 0.0, 0.0, "", "usda"),
    ("Olive oil extra virgin",      14,   119, 13.5,  0.0,  0.0, 0.0, 0.0, "", "usda"),

    # Vegetables (low carb)
    ("Asparagus cooked",            90,    20,  0.2,  2.2,  3.7, 1.8, 0.0, "", "usda"),
    ("Avocado raw",                100,   160, 14.7,  2.0,  8.5, 6.7, 0.0, "", "usda"),
    ("Broccoli raw",                91,    31,  0.3,  2.6,  6.0, 2.4, 0.0, "", "usda"),
    ("Brussels sprouts cooked",     78,    28,  0.4,  2.0,  5.5, 2.0, 0.0, "", "usda"),
    ("Cabbage raw",                 89,    22,  0.1,  1.1,  5.2, 2.2, 0.0, "", "usda"),
    ("Cauliflower raw",            100,    25,  0.3,  1.9,  5.0, 2.0, 0.0, "", "usda"),
    ("Celery raw",                 110,    18,  0.2,  0.8,  3.5, 1.8, 0.0, "", "usda"),
    ("Cucumber raw",               119,    16,  0.1,  0.7,  3.8, 0.5, 0.0, "", "usda"),
    ("Garlic raw",                   3,     4,  0.0,  0.2,  1.0, 0.1, 0.0, "", "usda"),
    ("Green beans cooked",         125,    44,  0.4,  2.4,  9.9, 4.0, 0.0, "", "usda"),
    ("Kale raw",                    67,    33,  0.5,  2.9,  6.7, 1.3, 0.0, "", "usda"),
    ("Lettuce romaine raw",         72,    12,  0.2,  0.9,  2.4, 1.0, 0.0, "", "usda"),
    ("Mushrooms raw",               96,    21,  0.3,  3.0,  3.3, 1.0, 0.0, "", "usda"),
    ("Onion raw",                   28,    11,  0.0,  0.3,  2.7, 0.3, 0.0, "", "usda"),
    ("Peppers bell green raw",     149,    31,  0.3,  1.3,  7.6, 2.5, 0.0, "", "usda"),
    ("Peppers bell red raw",       149,    46,  0.4,  1.5, 10.5, 2.0, 0.0, "", "usda"),
    ("Radish raw",                  58,     9,  0.1,  0.4,  2.0, 0.9, 0.0, "", "usda"),
    ("Spinach raw",                 30,     7,  0.1,  0.9,  1.1, 0.7, 0.0, "", "usda"),
    ("Zucchini raw",               124,    21,  0.4,  1.5,  3.9, 1.2, 0.0, "", "usda"),

    # Nuts and seeds
    ("Almonds raw",                 28,   164, 14.2,  6.0,  6.1, 3.5, 0.0, "", "usda"),
    ("Brazil nuts",                 28,   187, 19.0,  4.1,  3.5, 2.1, 0.0, "", "usda"),
    ("Chia seeds",                  28,   138,  8.7,  4.7, 12.3, 9.8, 0.0, "", "usda"),
    ("Flaxseed ground",             14,    75,  5.9,  2.6,  5.2, 3.8, 0.0, "", "usda"),
    ("Macadamia nuts",              28,   204, 21.5,  2.2,  3.9, 2.4, 0.0, "", "usda"),
    ("Pecans",                      28,   196, 20.4,  2.6,  3.9, 2.7, 0.0, "", "usda"),
    ("Pumpkin seeds",               28,   158, 13.9,  8.5,  3.0, 1.1, 0.0, "", "usda"),
    ("Sunflower seeds",             28,   165, 14.4,  5.5,  6.8, 3.0, 0.0, "", "usda"),
    ("Walnuts",                     28,   185, 18.5,  4.3,  3.9, 1.9, 0.0, "", "usda"),

    # Condiments and sauces
    ("Mayonnaise full fat",         15,   100, 11.0,  0.1,  0.1, 0.0, 0.0, "", "usda"),
    ("Mustard yellow",               5,     3,  0.2,  0.2,  0.3, 0.1, 0.0, "", "usda"),
    ("Hot sauce",                    5,     1,  0.0,  0.1,  0.1, 0.0, 0.0, "", "usda"),
    ("Soy sauce",                   15,     8,  0.1,  1.3,  0.8, 0.0, 0.0, "", "usda"),

    # Sweeteners (keto-friendly)
    ("Erythritol",                  4,     0,  0.0,  0.0,  4.0, 0.0, 4.0, "", "usda"),
    ("Stevia powder",               1,     0,  0.0,  0.0,  1.0, 0.0, 0.0, "", "usda"),
    ("Monk fruit sweetener",        4,     0,  0.0,  0.0,  4.0, 0.0, 4.0, "", "usda"),
]


def seed() -> None:
    db.init_db()

    existing = {r["name"].lower() for r in db.search_foods("", limit=10000)}
    added = 0
    skipped = 0

    for row in SEED_FOODS:
        name = row[0]
        if name.lower() in existing:
            skipped += 1
            continue
        db.add_food(*row)
        added += 1

    log.info(f"Seeding complete: {added} added, {skipped} skipped (already existed).")
    log.info(f"DB location: {db.DB_PATH}")


if __name__ == "__main__":
    seed()
