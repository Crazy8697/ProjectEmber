"""
nutrition_db.py — Project Ember

Nutrition and tracking database interface.
All DB access goes through this module — no raw SQL elsewhere.

DB file lives in Eira's private storage:
    /home/eira/.local/share/projectember/nutrition.db

Tables:
    foods       — food items with macro data
    food_log    — daily food/macro entries
    water_log   — daily water intake
    weight_log  — weight entries over time
"""

from __future__ import annotations

import logging
import os
import sqlite3
from contextlib import contextmanager
from datetime import date, datetime
from pathlib import Path
from typing import Any, Dict, List, Optional

log = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# DB path — respects env override for testing
# ---------------------------------------------------------------------------
DB_PATH = Path(
    os.environ.get(
        "EMBER_NUTRITION_DB",
        "/home/eira/.local/share/projectember/nutrition.db",
    )
)


# ---------------------------------------------------------------------------
# Connection
# ---------------------------------------------------------------------------

@contextmanager
def _conn():
    """Yield a sqlite3 connection with row_factory set."""
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(DB_PATH)
    con.row_factory = sqlite3.Row
    con.execute("PRAGMA journal_mode=WAL")
    con.execute("PRAGMA foreign_keys=ON")
    try:
        yield con
        con.commit()
    except Exception:
        con.rollback()
        raise
    finally:
        con.close()


# ---------------------------------------------------------------------------
# Schema bootstrap
# ---------------------------------------------------------------------------

def init_db() -> None:
    """Create tables if they don't exist. Safe to call multiple times."""
    with _conn() as con:
        con.executescript("""
            CREATE TABLE IF NOT EXISTS foods (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                name        TEXT    NOT NULL,
                brand       TEXT    DEFAULT '',
                serving_g   REAL    NOT NULL DEFAULT 100,
                calories    REAL    NOT NULL DEFAULT 0,
                fat_g       REAL    NOT NULL DEFAULT 0,
                protein_g   REAL    NOT NULL DEFAULT 0,
                carbs_g     REAL    NOT NULL DEFAULT 0,
                fiber_g     REAL    NOT NULL DEFAULT 0,
                sugar_alc_g REAL    NOT NULL DEFAULT 0,
                source      TEXT    DEFAULT 'user',
                created_at  TEXT    DEFAULT (datetime('now'))
            );

            CREATE TABLE IF NOT EXISTS food_log (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                log_date    TEXT    NOT NULL,
                food_id     INTEGER REFERENCES foods(id),
                food_name   TEXT    NOT NULL,
                serving_g   REAL    NOT NULL DEFAULT 100,
                calories    REAL    NOT NULL DEFAULT 0,
                fat_g       REAL    NOT NULL DEFAULT 0,
                protein_g   REAL    NOT NULL DEFAULT 0,
                net_carbs_g REAL    NOT NULL DEFAULT 0,
                meal        TEXT    DEFAULT 'unspecified',
                logged_at   TEXT    DEFAULT (datetime('now'))
            );

            CREATE TABLE IF NOT EXISTS water_log (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                log_date    TEXT    NOT NULL,
                amount_oz   REAL    NOT NULL,
                logged_at   TEXT    DEFAULT (datetime('now'))
            );

            CREATE TABLE IF NOT EXISTS weight_log (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                log_date    TEXT    NOT NULL UNIQUE,
                weight_lbs  REAL    NOT NULL,
                note        TEXT    DEFAULT '',
                logged_at   TEXT    DEFAULT (datetime('now'))
            );

            CREATE INDEX IF NOT EXISTS idx_food_log_date   ON food_log(log_date);
            CREATE INDEX IF NOT EXISTS idx_water_log_date  ON water_log(log_date);
            CREATE INDEX IF NOT EXISTS idx_weight_log_date ON weight_log(log_date);
            CREATE INDEX IF NOT EXISTS idx_foods_name      ON foods(name COLLATE NOCASE);
        """)
    log.info(f"[NutritionDB] Initialised at {DB_PATH}")


# ---------------------------------------------------------------------------
# Foods — search and add
# ---------------------------------------------------------------------------

def search_foods(query: str, limit: int = 10) -> List[Dict[str, Any]]:
    """Search foods table by name. Returns list of dicts."""
    with _conn() as con:
        rows = con.execute(
            """
            SELECT id, name, brand, serving_g, calories,
                   fat_g, protein_g, carbs_g, fiber_g, sugar_alc_g, source
            FROM foods
            WHERE name LIKE ?
            ORDER BY source DESC, name ASC
            LIMIT ?
            """,
            (f"%{query}%", limit),
        ).fetchall()
    return [dict(r) for r in rows]


def get_food(food_id: int) -> Optional[Dict[str, Any]]:
    """Fetch a single food by id."""
    with _conn() as con:
        row = con.execute(
            "SELECT * FROM foods WHERE id = ?", (food_id,)
        ).fetchone()
    return dict(row) if row else None


def add_food(
    name: str,
    serving_g: float,
    calories: float,
    fat_g: float,
    protein_g: float,
    carbs_g: float,
    fiber_g: float,
    sugar_alc_g: float = 0.0,
    brand: str = "",
    source: str = "user",
) -> int:
    """Insert a new food. Returns new row id."""
    with _conn() as con:
        cur = con.execute(
            """
            INSERT INTO foods
                (name, brand, serving_g, calories, fat_g, protein_g,
                 carbs_g, fiber_g, sugar_alc_g, source)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (name, brand, serving_g, calories, fat_g, protein_g,
             carbs_g, fiber_g, sugar_alc_g, source),
        )
    log.info(f"[NutritionDB] Added food: {name} (id={cur.lastrowid})")
    return cur.lastrowid


def net_carbs(carbs_g: float, fiber_g: float, sugar_alc_g: float = 0.0) -> float:
    """Calculate net carbs. Always non-negative."""
    return max(0.0, round(carbs_g - fiber_g - sugar_alc_g, 2))


# ---------------------------------------------------------------------------
# Food log
# ---------------------------------------------------------------------------

def log_food(
    food_name: str,
    calories: float,
    fat_g: float,
    protein_g: float,
    net_carbs_g: float,
    serving_g: float = 100.0,
    food_id: Optional[int] = None,
    meal: str = "unspecified",
    log_date: Optional[str] = None,
) -> int:
    """Log a food entry. log_date defaults to today (YYYY-MM-DD)."""
    log_date = log_date or date.today().isoformat()
    with _conn() as con:
        cur = con.execute(
            """
            INSERT INTO food_log
                (log_date, food_id, food_name, serving_g, calories,
                 fat_g, protein_g, net_carbs_g, meal)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (log_date, food_id, food_name, serving_g, calories,
             fat_g, protein_g, net_carbs_g, meal),
        )
    return cur.lastrowid


def get_food_log(log_date: Optional[str] = None) -> List[Dict[str, Any]]:
    """Return all food log entries for a date (default today)."""
    log_date = log_date or date.today().isoformat()
    with _conn() as con:
        rows = con.execute(
            "SELECT * FROM food_log WHERE log_date = ? ORDER BY logged_at",
            (log_date,),
        ).fetchall()
    return [dict(r) for r in rows]


def get_daily_macros(log_date: Optional[str] = None) -> Dict[str, float]:
    """Return summed macros for a date."""
    log_date = log_date or date.today().isoformat()
    with _conn() as con:
        row = con.execute(
            """
            SELECT
                COALESCE(SUM(calories), 0)    AS calories,
                COALESCE(SUM(fat_g), 0)       AS fat_g,
                COALESCE(SUM(protein_g), 0)   AS protein_g,
                COALESCE(SUM(net_carbs_g), 0) AS net_carbs_g
            FROM food_log
            WHERE log_date = ?
            """,
            (log_date,),
        ).fetchone()
    return dict(row) if row else {"calories": 0, "fat_g": 0, "protein_g": 0, "net_carbs_g": 0}


def get_macro_range(start: str, end: str) -> List[Dict[str, Any]]:
    """Return daily macro totals for a date range (YYYY-MM-DD inclusive)."""
    with _conn() as con:
        rows = con.execute(
            """
            SELECT
                log_date,
                COALESCE(SUM(calories), 0)    AS calories,
                COALESCE(SUM(fat_g), 0)       AS fat_g,
                COALESCE(SUM(protein_g), 0)   AS protein_g,
                COALESCE(SUM(net_carbs_g), 0) AS net_carbs_g
            FROM food_log
            WHERE log_date BETWEEN ? AND ?
            GROUP BY log_date
            ORDER BY log_date
            """,
            (start, end),
        ).fetchall()
    return [dict(r) for r in rows]


# ---------------------------------------------------------------------------
# Water log
# ---------------------------------------------------------------------------

def log_water(amount_oz: float, log_date: Optional[str] = None) -> int:
    """Log water intake in oz. log_date defaults to today."""
    log_date = log_date or date.today().isoformat()
    with _conn() as con:
        cur = con.execute(
            "INSERT INTO water_log (log_date, amount_oz) VALUES (?, ?)",
            (log_date, amount_oz),
        )
    return cur.lastrowid


def get_daily_water(log_date: Optional[str] = None) -> float:
    """Return total water in oz for a date."""
    log_date = log_date or date.today().isoformat()
    with _conn() as con:
        row = con.execute(
            "SELECT COALESCE(SUM(amount_oz), 0) AS total FROM water_log WHERE log_date = ?",
            (log_date,),
        ).fetchone()
    return float(row["total"]) if row else 0.0


def get_water_range(start: str, end: str) -> List[Dict[str, Any]]:
    """Return daily water totals for a date range."""
    with _conn() as con:
        rows = con.execute(
            """
            SELECT log_date, COALESCE(SUM(amount_oz), 0) AS total_oz
            FROM water_log
            WHERE log_date BETWEEN ? AND ?
            GROUP BY log_date
            ORDER BY log_date
            """,
            (start, end),
        ).fetchall()
    return [dict(r) for r in rows]


# ---------------------------------------------------------------------------
# Weight log
# ---------------------------------------------------------------------------

def log_weight(weight_lbs: float, note: str = "", log_date: Optional[str] = None) -> int:
    """Log weight. One entry per date — upserts if date already exists."""
    log_date = log_date or date.today().isoformat()
    with _conn() as con:
        cur = con.execute(
            """
            INSERT INTO weight_log (log_date, weight_lbs, note)
            VALUES (?, ?, ?)
            ON CONFLICT(log_date) DO UPDATE SET
                weight_lbs = excluded.weight_lbs,
                note       = excluded.note,
                logged_at  = datetime('now')
            """,
            (log_date, weight_lbs, note),
        )
    return cur.lastrowid


def get_weight_range(start: str, end: str) -> List[Dict[str, Any]]:
    """Return weight entries for a date range."""
    with _conn() as con:
        rows = con.execute(
            """
            SELECT log_date, weight_lbs, note
            FROM weight_log
            WHERE log_date BETWEEN ? AND ?
            ORDER BY log_date
            """,
            (start, end),
        ).fetchall()
    return [dict(r) for r in rows]


def get_latest_weight() -> Optional[Dict[str, Any]]:
    """Return the most recent weight entry."""
    with _conn() as con:
        row = con.execute(
            "SELECT log_date, weight_lbs, note FROM weight_log ORDER BY log_date DESC LIMIT 1"
        ).fetchone()
    return dict(row) if row else None


# ---------------------------------------------------------------------------
# Submind-facing query interface
# ---------------------------------------------------------------------------

def submind_lookup(food_name: str) -> str:
    """
    Called by keto_submind to look up a food by name.
    Returns a plain-text result EAI can include in a brief.
    """
    results = search_foods(food_name, limit=5)
    if not results:
        return f"No food found matching '{food_name}' in the local database."

    lines = [f"Database results for '{food_name}':"]
    for r in results:
        nc = net_carbs(r["carbs_g"], r["fiber_g"], r["sugar_alc_g"])
        lines.append(
            f"  {r['name']}"
            + (f" ({r['brand']})" if r["brand"] else "")
            + f" — per {r['serving_g']}g: "
            + f"{r['calories']} kcal | fat {r['fat_g']}g | "
            + f"protein {r['protein_g']}g | net carbs {nc}g"
            + f" [source: {r['source']}]"
        )
    return "\n".join(lines)


def submind_daily_summary(log_date: Optional[str] = None) -> str:
    """
    Called by keto_submind to get today's macro + water summary.
    Returns plain text for EAI brief injection.
    """
    log_date = log_date or date.today().isoformat()
    macros = get_daily_macros(log_date)
    water = get_daily_water(log_date)
    weight = get_latest_weight()

    lines = [f"Daily summary for {log_date}:"]
    lines.append(f"  Calories:   {macros['calories']:.0f} kcal")
    lines.append(f"  Fat:        {macros['fat_g']:.1f}g")
    lines.append(f"  Protein:    {macros['protein_g']:.1f}g")
    lines.append(f"  Net carbs:  {macros['net_carbs_g']:.1f}g")
    lines.append(f"  Water:      {water:.1f} oz")
    if weight:
        lines.append(f"  Weight:     {weight['weight_lbs']} lbs (as of {weight['log_date']})")
    else:
        lines.append("  Weight:     not logged yet")
    return "\n".join(lines)
