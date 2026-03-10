from __future__ import annotations

import sqlite3
from pathlib import Path

PLUGIN_DIR = Path(__file__).resolve().parent
DB_PATH = PLUGIN_DIR / "keto.db"
SCHEMA_PATH = PLUGIN_DIR / "schema.sql"

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
    with get_connection() as conn:
        conn.executescript(schema)
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
        "events": list_events_for_date(event_date),
        "totals": get_daily_totals(event_date),
        "differences": get_daily_differences(event_date),
    }


if __name__ == "__main__":
    init_db()
    print(get_targets())
