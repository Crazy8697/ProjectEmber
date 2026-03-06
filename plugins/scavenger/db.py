import os
import json
import sqlite3
from pathlib import Path
from contextlib import contextmanager
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

DEFAULT_DB_PATH = os.environ.get("SCAVENGER_DB_PATH", "app/data/scavenger.sqlite")


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def _ensure_parent_dir(db_path: str) -> None:
    Path(db_path).expanduser().resolve().parent.mkdir(parents=True, exist_ok=True)


@contextmanager
def connect(db_path: str = DEFAULT_DB_PATH):
    _ensure_parent_dir(db_path)
    con = sqlite3.connect(db_path)
    try:
        con.row_factory = sqlite3.Row
        con.execute("PRAGMA journal_mode=WAL;")
        con.execute("PRAGMA foreign_keys=ON;")
        yield con
        con.commit()
    except Exception:
        con.rollback()
        raise
    finally:
        con.close()


def init_db(db_path: str = DEFAULT_DB_PATH) -> None:
    with connect(db_path) as con:
        con.executescript(
            """
            CREATE TABLE IF NOT EXISTS part_enrich_cache (
                mpn TEXT PRIMARY KEY,
                payload_json TEXT NOT NULL,
                confidence REAL,
                source TEXT,
                updated_at TEXT NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_part_enrich_cache_updated
            ON part_enrich_cache(updated_at);

            CREATE TABLE IF NOT EXISTS web_search_cache (
                query TEXT NOT NULL,
                rank INTEGER NOT NULL,
                url TEXT NOT NULL,
                title TEXT,
                snippet TEXT,
                fetched_at TEXT NOT NULL,
                PRIMARY KEY (query, rank)
            );

            CREATE INDEX IF NOT EXISTS idx_web_search_cache_query
            ON web_search_cache(query);

            CREATE INDEX IF NOT EXISTS idx_web_search_cache_fetched
            ON web_search_cache(fetched_at);
            """
        )


def get_part_cache(mpn: str, db_path: str = DEFAULT_DB_PATH):
    mpn = (mpn or "").strip()
    if not mpn:
        return None

    with connect(db_path) as con:
        row = con.execute(
            "SELECT payload_json FROM part_enrich_cache WHERE mpn = ?",
            (mpn,),
        ).fetchone()

    if not row:
        return None

    try:
        return json.loads(row["payload_json"])
    except Exception:
        return None


def upsert_part_cache(mpn: str, payload: dict, confidence: float = None, source: str = None, db_path: str = DEFAULT_DB_PATH) -> None:
    mpn = (mpn or "").strip()
    if not mpn:
        return

    payload_json = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    updated_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat()

    with connect(db_path) as con:
        con.execute(
            """
            INSERT INTO part_enrich_cache (mpn, payload_json, confidence, source, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(mpn) DO UPDATE SET
                payload_json=excluded.payload_json,
                confidence=excluded.confidence,
                source=excluded.source,
                updated_at=excluded.updated_at
            """,
            (mpn, payload_json, confidence, source, updated_at),
        )


def delete_part_cache(mpn: str, db_path: str = DEFAULT_DB_PATH) -> int:
    mpn = (mpn or "").strip()
    if not mpn:
        return 0
    with connect(db_path) as con:
        cur = con.execute("DELETE FROM part_enrich_cache WHERE mpn = ?", (mpn,))
        return int(cur.rowcount or 0)


def list_part_cache(limit: int = 50, db_path: str = DEFAULT_DB_PATH) -> List[Dict[str, Any]]:
    lim = max(1, min(int(limit or 50), 500))
    with connect(db_path) as con:
        rows = con.execute(
            """
            SELECT mpn, confidence, source, updated_at
            FROM part_enrich_cache
            ORDER BY updated_at DESC
            LIMIT ?
            """,
            (lim,),
        ).fetchall()
    return [
        {"mpn": r["mpn"], "confidence": r["confidence"], "source": r["source"], "updated_at": r["updated_at"]}
        for r in rows
    ]


def get_search_cache(query: str, db_path: str = DEFAULT_DB_PATH):
    query = (query or "").strip()
    if not query:
        return []

    with connect(db_path) as con:
        rows = con.execute(
            """
            SELECT rank, url, title, snippet
            FROM web_search_cache
            WHERE query = ?
            ORDER BY rank ASC
            """,
            (query,),
        ).fetchall()

    return [{"rank": r["rank"], "url": r["url"], "title": r["title"], "snippet": r["snippet"]} for r in rows]


def put_search_cache(query: str, results: list, db_path: str = DEFAULT_DB_PATH) -> None:
    query = (query or "").strip()
    if not query:
        return

    fetched_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat()

    with connect(db_path) as con:
        con.execute("DELETE FROM web_search_cache WHERE query = ?", (query,))
        for i, item in enumerate(results or []):
            rank = i + 1
            con.execute(
                """
                INSERT INTO web_search_cache (query, rank, url, title, snippet, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (
                    query,
                    rank,
                    (item.get("url") or "").strip(),
                    (item.get("title") or "").strip() or None,
                    (item.get("snippet") or "").strip() or None,
                    fetched_at,
                ),
            )


def delete_search_cache(query: str, db_path: str = DEFAULT_DB_PATH) -> int:
    query = (query or "").strip()
    if not query:
        return 0
    with connect(db_path) as con:
        cur = con.execute("DELETE FROM web_search_cache WHERE query = ?", (query,))
        return int(cur.rowcount or 0)


def list_search_cache(limit: int = 50, db_path: str = DEFAULT_DB_PATH) -> List[Dict[str, Any]]:
    lim = max(1, min(int(limit or 50), 500))
    with connect(db_path) as con:
        rows = con.execute(
            """
            SELECT query, MAX(fetched_at) AS fetched_at, COUNT(*) AS n
            FROM web_search_cache
            GROUP BY query
            ORDER BY fetched_at DESC
            LIMIT ?
            """,
            (lim,),
        ).fetchall()
    return [{"query": r["query"], "fetched_at": r["fetched_at"], "n": r["n"]} for r in rows]


def db_info(db_path: str = DEFAULT_DB_PATH) -> Dict[str, Any]:
    path = str(Path(db_path).expanduser().resolve())
    info: Dict[str, Any] = {"db_path": path}
    with connect(db_path) as con:
        try:
            info["db_size_bytes"] = Path(path).stat().st_size
        except Exception:
            info["db_size_bytes"] = None

        counts = {}
        for t in ("part_enrich_cache", "web_search_cache"):
            try:
                c = con.execute(f"SELECT COUNT(*) AS n FROM {t}").fetchone()
                counts[t] = int(c["n"]) if c else 0
            except Exception:
                counts[t] = None
        info["counts"] = counts
    return info
