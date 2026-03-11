from __future__ import annotations

import sqlite3
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

ALLOWED_EVENT_TYPES = {"meal", "drink", "exercise", "supplement", "custom"}

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


def init_db() -> None:
    schema = SCHEMA_PATH.read_text(encoding="utf-8")
    recipes_schema = RECIPES_SCHEMA_PATH.read_text(encoding="utf-8")
    with get_connection() as conn:
        conn.executescript(schema)
        conn.executescript(recipes_schema)
        seed_defaults(conn)
        conn.commit()


def get_targets() -> dict:
    with get_connection() as conn:
        row = conn.execute("SELECT * FROM targets WHERE id = 1").fetchone()
        if row is None:
            raise RuntimeError("No targets row found in targets table.")
        return dict(row)


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
