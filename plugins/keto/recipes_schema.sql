CREATE TABLE IF NOT EXISTS recipes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    description TEXT,
    servings REAL DEFAULT 1,

    calories REAL DEFAULT 0,
    protein_g REAL DEFAULT 0,
    fat_g REAL DEFAULT 0,
    net_carbs_g REAL DEFAULT 0,

    water_ml REAL DEFAULT 0,
    sodium_mg REAL DEFAULT 0,
    potassium_mg REAL DEFAULT 0,
    magnesium_mg REAL DEFAULT 0,

    keto_notes TEXT,

    created_at TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recipe_ingredients (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recipe_id INTEGER NOT NULL,

    ingredient TEXT NOT NULL,
    amount TEXT,

    calories REAL DEFAULT 0,
    protein_g REAL DEFAULT 0,
    fat_g REAL DEFAULT 0,
    net_carbs_g REAL DEFAULT 0,

    FOREIGN KEY(recipe_id) REFERENCES recipes(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_recipe
ON recipe_ingredients(recipe_id);
