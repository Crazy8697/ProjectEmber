from __future__ import annotations

import sqlite3
from datetime import date as _date, timedelta as _timedelta
from pathlib import Path

PLUGIN_DIR = Path(__file__).resolve().parent
DB_PATH = PLUGIN_DIR / "keto.db"
SCHEMA_PATH = PLUGIN_DIR / "schema.sql"
RECIPES_SCHEMA_PATH = PLUGIN_DIR / "recipes_schema.sql"

DEFAULT_TARGETS = {
    "id": 1,
    "calories_target": 2000,
    "protein_target_g": 140,
    "fat_target_g": 140,
    "net_carbs_target_g": 20,
    "water_target_ml": 3000,
    "sodium_target_mg": 4000,
    "potassium_target_mg": 3500,
    "magnesium_target_mg": 400,
    "expected_events_per_day": 4,
}

# Starter recipes seeded the first time the recipes table is empty.
# These are general starter ideas, not dietary recommendations.
STARTER_RECIPES = [
    # Simple meals
    {
        "name": "Scrambled Eggs (2 eggs)",
        "description": "Basic scrambled eggs in butter.",
        "keto_notes": "Simple, fast, near-zero carb. Starter meal option.",
        "servings": 1,
        "calories": 180, "protein_g": 12, "fat_g": 14, "net_carbs_g": 1,
        "water_ml": 0, "sodium_mg": 180, "potassium_mg": 130, "magnesium_mg": 10,
    },
    {
        "name": "Ground Beef Bowl (4 oz)",
        "description": "Plain ground beef seasoned with salt and pepper.",
        "keto_notes": "Near-zero carb. Good protein and fat base.",
        "servings": 1,
        "calories": 280, "protein_g": 19, "fat_g": 22, "net_carbs_g": 0,
        "water_ml": 0, "sodium_mg": 260, "potassium_mg": 270, "magnesium_mg": 18,
    },
    {
        "name": "Rotisserie Chicken Thigh",
        "description": "One chicken thigh, skin on.",
        "keto_notes": "Easy protein. Near-zero carb.",
        "servings": 1,
        "calories": 210, "protein_g": 22, "fat_g": 13, "net_carbs_g": 0,
        "water_ml": 0, "sodium_mg": 230, "potassium_mg": 200, "magnesium_mg": 22,
    },
    {
        "name": "Eggs & Bacon (2 eggs + 2 strips)",
        "description": "Two fried eggs and two strips of bacon.",
        "keto_notes": "Classic keto-friendly starter meal.",
        "servings": 1,
        "calories": 300, "protein_g": 20, "fat_g": 24, "net_carbs_g": 1,
        "water_ml": 0, "sodium_mg": 560, "potassium_mg": 190, "magnesium_mg": 15,
    },
    # Simple drinks
    {
        "name": "Water (500 mL)",
        "description": "Plain water.",
        "keto_notes": "Log water separately to track daily hydration.",
        "servings": 1,
        "calories": 0, "protein_g": 0, "fat_g": 0, "net_carbs_g": 0,
        "water_ml": 500, "sodium_mg": 0, "potassium_mg": 0, "magnesium_mg": 0,
    },
    {
        "name": "Black Coffee",
        "description": "Plain black coffee, no sugar or milk.",
        "keto_notes": "Zero carb. Counts toward daily water intake.",
        "servings": 1,
        "calories": 2, "protein_g": 0, "fat_g": 0, "net_carbs_g": 0,
        "water_ml": 240, "sodium_mg": 5, "potassium_mg": 116, "magnesium_mg": 7,
    },
    {
        "name": "Broth / Electrolyte Drink",
        "description": "Warm broth or a simple homemade electrolyte drink (water, salt, a pinch of potassium).",
        "keto_notes": "Early keto support option. Provides sodium and potassium.",
        "servings": 1,
        "calories": 15, "protein_g": 1, "fat_g": 0.5, "net_carbs_g": 0.5,
        "water_ml": 250, "sodium_mg": 600, "potassium_mg": 300, "magnesium_mg": 10,
    },
    # Near-zero-carb snacks
    {
        "name": "Pickles (3 spears)",
        "description": "Dill pickle spears.",
        "keto_notes": "Near-zero carb starter snack. High sodium.",
        "servings": 1,
        "calories": 5, "protein_g": 0.3, "fat_g": 0, "net_carbs_g": 0.5,
        "water_ml": 0, "sodium_mg": 700, "potassium_mg": 50, "magnesium_mg": 5,
    },
    {
        "name": "Pickled Jalapeños (4 rings)",
        "description": "Sliced pickled jalapeño rings.",
        "keto_notes": "Near-zero carb snack option. Adds flavor without carbs.",
        "servings": 1,
        "calories": 5, "protein_g": 0.2, "fat_g": 0, "net_carbs_g": 0.5,
        "water_ml": 0, "sodium_mg": 400, "potassium_mg": 40, "magnesium_mg": 3,
    },
    {
        "name": "Roasted Seaweed Snack (1 pack)",
        "description": "One snack-size pack of roasted seaweed sheets.",
        "keto_notes": "Near-zero carb starter snack option.",
        "servings": 1,
        "calories": 25, "protein_g": 0.5, "fat_g": 1.5, "net_carbs_g": 0.5,
        "water_ml": 0, "sodium_mg": 100, "potassium_mg": 60, "magnesium_mg": 15,
    },
    {
        "name": "String Cheese (1 stick)",
        "description": "One mozzarella string cheese stick.",
        "keto_notes": "Low-carb snack with protein and fat.",
        "servings": 1,
        "calories": 80, "protein_g": 6, "fat_g": 5, "net_carbs_g": 1,
        "water_ml": 0, "sodium_mg": 200, "potassium_mg": 30, "magnesium_mg": 6,
    },
    {
        "name": "Hard-Boiled Egg",
        "description": "One hard-boiled egg.",
        "keto_notes": "Portable near-zero-carb snack.",
        "servings": 1,
        "calories": 70, "protein_g": 6, "fat_g": 5, "net_carbs_g": 0.5,
        "water_ml": 0, "sodium_mg": 60, "potassium_mg": 65, "magnesium_mg": 5,
    },
    # Craving-control / rough-day options
    {
        "name": "Pork Rinds (1 oz / 28g)",
        "description": "Plain or salted pork rinds.",
        "keto_notes": "Zero-carb craving-control snack. High in fat and protein.",
        "servings": 1,
        "calories": 150, "protein_g": 17, "fat_g": 9, "net_carbs_g": 0,
        "water_ml": 0, "sodium_mg": 380, "potassium_mg": 20, "magnesium_mg": 5,
    },
    {
        "name": "Pepperoni Slices (10 slices)",
        "description": "Standard sliced pepperoni.",
        "keto_notes": "Near-zero-carb craving-control snack.",
        "servings": 1,
        "calories": 90, "protein_g": 5, "fat_g": 8, "net_carbs_g": 0.5,
        "water_ml": 0, "sodium_mg": 460, "potassium_mg": 60, "magnesium_mg": 5,
    },
    {
        "name": "Cheddar Cheese (1 oz)",
        "description": "One ounce of cheddar cheese.",
        "keto_notes": "Low-carb, high-fat craving-control option.",
        "servings": 1,
        "calories": 115, "protein_g": 7, "fat_g": 9, "net_carbs_g": 0.5,
        "water_ml": 0, "sodium_mg": 180, "potassium_mg": 30, "magnesium_mg": 7,
    },
]

STARTER_RECIPE_NAMES = {recipe["name"] for recipe in STARTER_RECIPES}


ALLOWED_EVENT_TYPES = {"meal", "snack", "drink", "exercise", "supplement", "custom"}

TOTAL_FIELDS = (
    "calories",
    "protein_g",
    "fat_g",
    "net_carbs_g",
    "water_ml",
    "sodium_mg",
    "potassium_mg",
    "magnesium_mg",
)


def get_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def seed_defaults(conn: sqlite3.Connection) -> None:
    conn.execute(
        """
        INSERT OR IGNORE INTO targets (
            id,
            calories_target,
            protein_target_g,
            fat_target_g,
            net_carbs_target_g,
            water_target_ml,
            sodium_target_mg,
            potassium_target_mg,
            magnesium_target_mg,
            expected_events_per_day
        )
        VALUES (
            :id,
            :calories_target,
            :protein_target_g,
            :fat_target_g,
            :net_carbs_target_g,
            :water_target_ml,
            :sodium_target_mg,
            :potassium_target_mg,
            :magnesium_target_mg,
            :expected_events_per_day
        )
        """,
        DEFAULT_TARGETS,
    )


def ensure_recipe_support_tables(conn: sqlite3.Connection) -> None:
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS starter_recipe_tombstones (
            name TEXT PRIMARY KEY,
            deleted_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """
    )


def init_db() -> None:
    schema = SCHEMA_PATH.read_text(encoding="utf-8")
    recipes_schema = RECIPES_SCHEMA_PATH.read_text(encoding="utf-8")
    with get_connection() as conn:
        conn.executescript(schema)
        conn.executescript(recipes_schema)
        ensure_recipe_support_tables(conn)
        seed_defaults(conn)
        seed_starter_recipes(conn)
        conn.commit()


def get_targets() -> dict:
    with get_connection() as conn:
        row = conn.execute("SELECT * FROM targets WHERE id = 1").fetchone()
        if row is None:
            raise RuntimeError("No targets row found in targets table.")
        return dict(row)


def update_targets(
    *,
    calories_target: float,
    protein_target_g: float,
    fat_target_g: float,
    net_carbs_target_g: float,
    water_target_ml: float,
    sodium_target_mg: float,
    potassium_target_mg: float,
    magnesium_target_mg: float,
    expected_events_per_day: float = 4,
) -> None:
    with get_connection() as conn:
        conn.execute(
            """
            UPDATE targets
            SET
                calories_target = ?,
                protein_target_g = ?,
                fat_target_g = ?,
                net_carbs_target_g = ?,
                water_target_ml = ?,
                sodium_target_mg = ?,
                potassium_target_mg = ?,
                magnesium_target_mg = ?,
                expected_events_per_day = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = 1
            """,
            (
                calories_target,
                protein_target_g,
                fat_target_g,
                net_carbs_target_g,
                water_target_ml,
                sodium_target_mg,
                potassium_target_mg,
                magnesium_target_mg,
                expected_events_per_day,
            ),
        )
        conn.commit()


def seed_starter_recipes(conn: sqlite3.Connection) -> None:
    """Ensure starter recipes exist unless the user explicitly deleted them."""
    ensure_recipe_support_tables(conn)

    existing_names = {
        row[0]
        for row in conn.execute("SELECT name FROM recipes").fetchall()
    }
    tombstoned_names = {
        row[0]
        for row in conn.execute("SELECT name FROM starter_recipe_tombstones").fetchall()
    }

    for recipe in STARTER_RECIPES:
        name = recipe["name"]
        if name in existing_names or name in tombstoned_names:
            continue

        conn.execute(
            """
            INSERT INTO recipes (
                name, description, keto_notes, servings,
                calories, protein_g, fat_g, net_carbs_g,
                water_ml, sodium_mg, potassium_mg, magnesium_mg
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                name,
                recipe.get("description", ""),
                recipe.get("keto_notes", ""),
                recipe.get("servings", 1),
                recipe.get("calories", 0),
                recipe.get("protein_g", 0),
                recipe.get("fat_g", 0),
                recipe.get("net_carbs_g", 0),
                recipe.get("water_ml", 0),
                recipe.get("sodium_mg", 0),
                recipe.get("potassium_mg", 0),
                recipe.get("magnesium_mg", 0),
            ),
        )


def add_event(
    *,
    event_timestamp: str,
    event_date: str,
    event_type: str,
    label: str,
    calories: float = 0,
    protein_g: float = 0,
    fat_g: float = 0,
    net_carbs_g: float = 0,
    water_ml: float = 0,
    sodium_mg: float = 0,
    potassium_mg: float = 0,
    magnesium_mg: float = 0,
    source: str = "manual",
    source_id: str | None = None,
    notes: str | None = None,
) -> int:
    if event_type not in ALLOWED_EVENT_TYPES:
        raise ValueError(f"Invalid event_type: {event_type}")

    with get_connection() as conn:
        cur = conn.execute(
            """
            INSERT INTO events (
                event_timestamp,
                event_date,
                event_type,
                label,
                calories,
                protein_g,
                fat_g,
                net_carbs_g,
                water_ml,
                sodium_mg,
                potassium_mg,
                magnesium_mg,
                source,
                source_id,
                notes
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                event_timestamp,
                event_date,
                event_type,
                label,
                calories,
                protein_g,
                fat_g,
                net_carbs_g,
                water_ml,
                sodium_mg,
                potassium_mg,
                magnesium_mg,
                source,
                source_id,
                notes,
            ),
        )
        conn.commit()
        return int(cur.lastrowid)


def get_event_by_id(event_id: int) -> dict | None:
    with get_connection() as conn:
        row = conn.execute(
            """
            SELECT *
            FROM events
            WHERE id = ?
            """,
            (event_id,),
        ).fetchone()
        return dict(row) if row is not None else None


def update_event(
    *,
    event_id: int,
    event_timestamp: str,
    event_date: str,
    event_type: str,
    label: str,
    calories: float = 0,
    protein_g: float = 0,
    fat_g: float = 0,
    net_carbs_g: float = 0,
    water_ml: float = 0,
    sodium_mg: float = 0,
    potassium_mg: float = 0,
    magnesium_mg: float = 0,
    source: str = "manual",
    source_id: str | None = None,
    notes: str | None = None,
) -> bool:
    if event_type not in ALLOWED_EVENT_TYPES:
        raise ValueError(f"Invalid event_type: {event_type}")

    with get_connection() as conn:
        cur = conn.execute(
            """
            UPDATE events
            SET
                event_timestamp = ?,
                event_date = ?,
                event_type = ?,
                label = ?,
                calories = ?,
                protein_g = ?,
                fat_g = ?,
                net_carbs_g = ?,
                water_ml = ?,
                sodium_mg = ?,
                potassium_mg = ?,
                magnesium_mg = ?,
                source = ?,
                source_id = ?,
                notes = ?
            WHERE id = ?
            """,
            (
                event_timestamp,
                event_date,
                event_type,
                label,
                calories,
                protein_g,
                fat_g,
                net_carbs_g,
                water_ml,
                sodium_mg,
                potassium_mg,
                magnesium_mg,
                source,
                source_id,
                notes,
                event_id,
            ),
        )
        conn.commit()
        return cur.rowcount > 0


def delete_event(event_id: int) -> bool:
    with get_connection() as conn:
        cur = conn.execute(
            """
            DELETE FROM events
            WHERE id = ?
            """,
            (event_id,),
        )
        conn.commit()
        return cur.rowcount > 0


def list_events_for_date(event_date: str) -> list[dict]:
    with get_connection() as conn:
        rows = conn.execute(
            """
            SELECT *
            FROM events
            WHERE event_date = ?
            ORDER BY event_timestamp ASC, id ASC
            """,
            (event_date,),
        ).fetchall()
        return [dict(row) for row in rows]


def get_daily_totals(event_date: str) -> dict:
    with get_connection() as conn:
        row = conn.execute(
            """
            SELECT
                COALESCE(SUM(calories), 0) AS calories,
                COALESCE(SUM(protein_g), 0) AS protein_g,
                COALESCE(SUM(fat_g), 0) AS fat_g,
                COALESCE(SUM(net_carbs_g), 0) AS net_carbs_g,
                COALESCE(SUM(water_ml), 0) AS water_ml,
                COALESCE(SUM(sodium_mg), 0) AS sodium_mg,
                COALESCE(SUM(potassium_mg), 0) AS potassium_mg,
                COALESCE(SUM(magnesium_mg), 0) AS magnesium_mg
            FROM events
            WHERE event_date = ?
            """,
            (event_date,),
        ).fetchone()
        return dict(row) if row is not None else {field: 0 for field in TOTAL_FIELDS}


def get_daily_differences(event_date: str) -> dict:
    targets = get_targets()
    totals = get_daily_totals(event_date)

    return {
        "calories": totals["calories"] - targets["calories_target"],
        "protein_g": totals["protein_g"] - targets["protein_target_g"],
        "fat_g": totals["fat_g"] - targets["fat_target_g"],
        "net_carbs_g": totals["net_carbs_g"] - targets["net_carbs_target_g"],
        "water_ml": totals["water_ml"] - targets["water_target_ml"],
        "sodium_mg": totals["sodium_mg"] - targets["sodium_target_mg"],
        "potassium_mg": totals["potassium_mg"] - targets["potassium_target_mg"],
        "magnesium_mg": totals["magnesium_mg"] - targets["magnesium_target_mg"],
    }


def get_day_summary(event_date: str) -> dict:
    return {
        "date": event_date,
        "targets": get_targets(),
        "totals": get_daily_totals(event_date),
        "differences": get_daily_differences(event_date),
        "events": list_events_for_date(event_date),
    }


# -----------------------------
# Recipe helpers
# -----------------------------

def list_recipes() -> list[dict]:
    with get_connection() as conn:
        rows = conn.execute(
            """
            SELECT *
            FROM recipes
            ORDER BY name COLLATE NOCASE ASC, id ASC
            """
        ).fetchall()
        return [dict(row) for row in rows]


def get_recipe_by_id(recipe_id: int) -> dict | None:
    with get_connection() as conn:
        row = conn.execute(
            """
            SELECT *
            FROM recipes
            WHERE id = ?
            """,
            (recipe_id,),
        ).fetchone()

        if row is None:
            return None

        recipe = dict(row)
        recipe["ingredients"] = list_recipe_ingredients(recipe_id)
        return recipe


def list_recipe_ingredients(recipe_id: int) -> list[dict]:
    with get_connection() as conn:
        rows = conn.execute(
            """
            SELECT *
            FROM recipe_ingredients
            WHERE recipe_id = ?
            ORDER BY id ASC
            """,
            (recipe_id,),
        ).fetchall()
        return [dict(row) for row in rows]


def create_recipe(
    *,
    name: str,
    description: str | None = None,
    keto_notes: str | None = None,
    servings: float = 1,
    calories: float = 0,
    protein_g: float = 0,
    fat_g: float = 0,
    net_carbs_g: float = 0,
    water_ml: float = 0,
    sodium_mg: float = 0,
    potassium_mg: float = 0,
    magnesium_mg: float = 0,
    ingredients: list[dict] | None = None,
) -> int:
    with get_connection() as conn:
        cur = conn.execute(
            """
            INSERT INTO recipes (
                name,
                description,
                keto_notes,
                servings,
                calories,
                protein_g,
                fat_g,
                net_carbs_g,
                water_ml,
                sodium_mg,
                potassium_mg,
                magnesium_mg
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                name.strip(),
                (description or "").strip(),
                (keto_notes or "").strip(),
                servings,
                calories,
                protein_g,
                fat_g,
                net_carbs_g,
                water_ml,
                sodium_mg,
                potassium_mg,
                magnesium_mg,
            ),
        )
        recipe_id = int(cur.lastrowid)
        _replace_recipe_ingredients(conn, recipe_id, ingredients or [])
        conn.commit()
        return recipe_id


def update_recipe(
    *,
    recipe_id: int,
    name: str,
    description: str | None = None,
    keto_notes: str | None = None,
    servings: float = 1,
    calories: float = 0,
    protein_g: float = 0,
    fat_g: float = 0,
    net_carbs_g: float = 0,
    water_ml: float = 0,
    sodium_mg: float = 0,
    potassium_mg: float = 0,
    magnesium_mg: float = 0,
    ingredients: list[dict] | None = None,
) -> bool:
    with get_connection() as conn:
        cur = conn.execute(
            """
            UPDATE recipes
            SET
                name = ?,
                description = ?,
                keto_notes = ?,
                servings = ?,
                calories = ?,
                protein_g = ?,
                fat_g = ?,
                net_carbs_g = ?,
                water_ml = ?,
                sodium_mg = ?,
                potassium_mg = ?,
                magnesium_mg = ?
            WHERE id = ?
            """,
            (
                name.strip(),
                (description or "").strip(),
                (keto_notes or "").strip(),
                servings,
                calories,
                protein_g,
                fat_g,
                net_carbs_g,
                water_ml,
                sodium_mg,
                potassium_mg,
                magnesium_mg,
                recipe_id,
            ),
        )
        if cur.rowcount <= 0:
            conn.commit()
            return False

        _replace_recipe_ingredients(conn, recipe_id, ingredients or [])
        conn.commit()
        return True


def delete_recipe(recipe_id: int) -> bool:
    with get_connection() as conn:
        ensure_recipe_support_tables(conn)

        row = conn.execute(
            """
            SELECT name
            FROM recipes
            WHERE id = ?
            """,
            (recipe_id,),
        ).fetchone()

        if row is None:
            conn.commit()
            return False

        recipe_name = row[0]

        if recipe_name in STARTER_RECIPE_NAMES:
            conn.execute(
                """
                INSERT OR IGNORE INTO starter_recipe_tombstones (name)
                VALUES (?)
                """,
                (recipe_name,),
            )

        cur = conn.execute(
            """
            DELETE FROM recipes
            WHERE id = ?
            """,
            (recipe_id,),
        )
        conn.commit()
        return cur.rowcount > 0


def _replace_recipe_ingredients(conn: sqlite3.Connection, recipe_id: int, ingredients: list[dict]) -> None:
    conn.execute(
        """
        DELETE FROM recipe_ingredients
        WHERE recipe_id = ?
        """,
        (recipe_id,),
    )

    for item in ingredients:
        ingredient = str(item.get("ingredient", "")).strip()
        amount = str(item.get("amount", "")).strip()
        if not ingredient:
            continue

        conn.execute(
            """
            INSERT INTO recipe_ingredients (
                recipe_id,
                ingredient,
                amount,
                calories,
                protein_g,
                fat_g,
                net_carbs_g
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (
                recipe_id,
                ingredient,
                amount,
                float(item.get("calories", 0) or 0),
                float(item.get("protein_g", 0) or 0),
                float(item.get("fat_g", 0) or 0),
                float(item.get("net_carbs_g", 0) or 0),
            ),
        )


def recipe_to_event_payload(recipe: dict, *, event_date: str, event_timestamp: str) -> dict:
    return {
        "event_timestamp": event_timestamp,
        "event_date": event_date,
        "event_type": "meal",
        "label": recipe.get("name", "").strip(),
        "calories": float(recipe.get("calories", 0) or 0),
        "protein_g": float(recipe.get("protein_g", 0) or 0),
        "fat_g": float(recipe.get("fat_g", 0) or 0),
        "net_carbs_g": float(recipe.get("net_carbs_g", 0) or 0),
        "water_ml": float(recipe.get("water_ml", 0) or 0),
        "sodium_mg": float(recipe.get("sodium_mg", 0) or 0),
        "potassium_mg": float(recipe.get("potassium_mg", 0) or 0),
        "magnesium_mg": float(recipe.get("magnesium_mg", 0) or 0),
        "source": "recipe",
        "source_id": str(recipe.get("id", "")),
        "notes": None,
    }


# -----------------------------
# Chart / Trend data helpers
# -----------------------------

_CHART_FIELDS = ("calories", "protein_g", "fat_g", "net_carbs_g", "water_ml")


def get_chart_daily(date_from: str, date_to: str) -> list[dict]:
    """Daily aggregated totals for dates that have events in [date_from, date_to]."""
    with get_connection() as conn:
        rows = conn.execute(
            """
            SELECT
                event_date                    AS date,
                COALESCE(SUM(calories), 0)    AS calories,
                COALESCE(SUM(protein_g), 0)   AS protein_g,
                COALESCE(SUM(fat_g), 0)       AS fat_g,
                COALESCE(SUM(net_carbs_g), 0) AS net_carbs_g,
                COALESCE(SUM(water_ml), 0)    AS water_ml
            FROM events
            WHERE event_date >= ? AND event_date <= ?
            GROUP BY event_date
            ORDER BY event_date ASC
            """,
            (date_from, date_to),
        ).fetchall()
        return [dict(row) for row in rows]


def get_chart_per_meal(date_from: str, date_to: str) -> list[dict]:
    """Individual ingestive event rows in [date_from, date_to] for per-meal chart mode."""
    with get_connection() as conn:
        rows = conn.execute(
            """
            SELECT
                event_date                    AS date,
                event_timestamp,
                event_type,
                label,
                COALESCE(calories, 0)         AS calories,
                COALESCE(protein_g, 0)        AS protein_g,
                COALESCE(fat_g, 0)            AS fat_g,
                COALESCE(net_carbs_g, 0)      AS net_carbs_g,
                COALESCE(water_ml, 0)         AS water_ml
            FROM events
            WHERE event_date >= ? AND event_date <= ?
              AND event_type IN ('meal', 'snack', 'drink')
            ORDER BY event_timestamp ASC, id ASC
            """,
            (date_from, date_to),
        ).fetchall()
        return [dict(row) for row in rows]


def compute_rolling_avg(
    daily_rows: list[dict],
    window: int,
    date_from: str,
    date_to: str,
) -> list[dict]:
    """
    Compute a rolling average over *window* days for every date in
    [date_from, date_to].  Missing days contribute 0 to each window bucket.
    Returns one entry per date in the requested range.
    """
    data_by_date: dict[str, dict] = {row["date"]: row for row in daily_rows}
    start = _date.fromisoformat(date_from)
    end = _date.fromisoformat(date_to)
    window_start = start - _timedelta(days=window - 1)

    # Dense chronological list from window_start to date_to
    all_dates: list[_date] = []
    d = window_start
    while d <= end:
        all_dates.append(d)
        d += _timedelta(days=1)

    result: list[dict] = []
    for i, d in enumerate(all_dates):
        if d < start:
            continue
        window_slice = all_dates[max(0, i - window + 1) : i + 1]
        avg_row: dict = {"date": d.isoformat()}
        for field in _CHART_FIELDS:
            total = sum(
                data_by_date.get(wd.isoformat(), {}).get(field, 0.0)
                for wd in window_slice
            )
            avg_row[field] = round(total / len(window_slice), 2)
        result.append(avg_row)

    return result
