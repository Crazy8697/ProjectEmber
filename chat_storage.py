"""
Chat and Project storage for Project Ember.

Manages:
- Projects (folders for organizing chats)
- Chats (individual conversations within projects)
- Messages (user/assistant exchanges within chats)

Uses SQLite for persistence.
"""

import sqlite3
import json
import time
from pathlib import Path
from typing import Dict, List, Optional, Any

# Storage location
STORAGE_DIR = Path.home() / ".local" / "share" / "projectember" / "chats"
DB_PATH = STORAGE_DIR / "chats.db"

def _ensure_db():
    """Create database and tables if they don't exist."""
    STORAGE_DIR.mkdir(parents=True, exist_ok=True)
    
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    # Projects table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS projects (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            created_at REAL NOT NULL,
            updated_at REAL NOT NULL
        )
    """)
    
    # Chats table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS chats (
            id TEXT PRIMARY KEY,
            project_id TEXT NOT NULL,
            name TEXT NOT NULL,
            created_at REAL NOT NULL,
            updated_at REAL NOT NULL,
            is_archived INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (project_id) REFERENCES projects(id)
        )
    """)

    # Migration: add is_archived if it doesn't exist yet (existing DBs)
    try:
        cursor.execute("ALTER TABLE chats ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")
        conn.commit()
    except sqlite3.OperationalError:
        pass  # Column already exists
    
    # Messages table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS messages (
            id TEXT PRIMARY KEY,
            chat_id TEXT NOT NULL,
            role TEXT NOT NULL,
            content TEXT NOT NULL,
            created_at REAL NOT NULL,
            FOREIGN KEY (chat_id) REFERENCES chats(id)
        )
    """)
    
    conn.commit()
    conn.close()

def _get_connection():
    """Get a database connection."""
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

# ------- Projects -------

def create_project(name: str) -> Dict[str, Any]:
    """Create a new project."""
    _ensure_db()
    import uuid
    
    project_id = str(uuid.uuid4())
    now = time.time()
    
    conn = _get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO projects (id, name, created_at, updated_at) VALUES (?, ?, ?, ?)",
        (project_id, name, now, now)
    )
    conn.commit()
    conn.close()
    
    return {
        "id": project_id,
        "name": name,
        "created_at": now,
        "updated_at": now,
    }

def list_projects() -> List[Dict[str, Any]]:
    """List all projects."""
    _ensure_db()
    
    conn = _get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM projects ORDER BY updated_at DESC")
    rows = cursor.fetchall()
    conn.close()
    
    return [dict(row) for row in rows]

def get_project(project_id: str) -> Optional[Dict[str, Any]]:
    """Get a single project."""
    _ensure_db()
    
    conn = _get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM projects WHERE id = ?", (project_id,))
    row = cursor.fetchone()
    conn.close()
    
    return dict(row) if row else None

def delete_project(project_id: str) -> bool:
    """Delete a project (and all its chats/messages)."""
    _ensure_db()
    
    conn = _get_connection()
    cursor = conn.cursor()
    
    # Delete all messages in chats under this project
    cursor.execute(
        "DELETE FROM messages WHERE chat_id IN (SELECT id FROM chats WHERE project_id = ?)",
        (project_id,)
    )
    # Delete all chats in this project
    cursor.execute("DELETE FROM chats WHERE project_id = ?", (project_id,))
    # Delete the project
    cursor.execute("DELETE FROM projects WHERE id = ?", (project_id,))
    
    conn.commit()
    conn.close()
    
    return True

def rename_project(project_id: str, new_name: str) -> Dict[str, Any]:
    """Rename a project."""
    _ensure_db()
    now = time.time()
    
    conn = _get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "UPDATE projects SET name = ?, updated_at = ? WHERE id = ?",
        (new_name, now, project_id)
    )
    conn.commit()
    
    cursor.execute("SELECT * FROM projects WHERE id = ?", (project_id,))
    row = cursor.fetchone()
    conn.close()
    
    return dict(row) if row else {}

# ------- Chats -------

def create_chat(project_id: str, name: str) -> Dict[str, Any]:
    """Create a new chat in a project."""
    _ensure_db()
    import uuid
    
    chat_id = str(uuid.uuid4())
    now = time.time()
    
    conn = _get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO chats (id, project_id, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
        (chat_id, project_id, name, now, now)
    )
    conn.commit()
    conn.close()
    
    return {
        "id": chat_id,
        "project_id": project_id,
        "name": name,
        "created_at": now,
        "updated_at": now,
    }

def list_chats(project_id: str) -> List[Dict[str, Any]]:
    """List all chats in a project."""
    _ensure_db()
    
    conn = _get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "SELECT * FROM chats WHERE project_id = ? ORDER BY updated_at DESC",
        (project_id,)
    )
    rows = cursor.fetchall()
    conn.close()
    
    return [dict(row) for row in rows]

def get_chat(chat_id: str) -> Optional[Dict[str, Any]]:
    """Get a single chat."""
    _ensure_db()
    
    conn = _get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM chats WHERE id = ?", (chat_id,))
    row = cursor.fetchone()
    conn.close()
    
    return dict(row) if row else None

def delete_chat(chat_id: str) -> bool:
    """Delete a chat (and all its messages)."""
    _ensure_db()
    
    conn = _get_connection()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM messages WHERE chat_id = ?", (chat_id,))
    cursor.execute("DELETE FROM chats WHERE id = ?", (chat_id,))
    conn.commit()
    conn.close()
    
    return True

def rename_chat(chat_id: str, new_name: str) -> Dict[str, Any]:
    """Rename a chat."""
    _ensure_db()
    now = time.time()
    
    conn = _get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "UPDATE chats SET name = ?, updated_at = ? WHERE id = ?",
        (new_name, now, chat_id)
    )
    conn.commit()
    
    # Fetch updated row
    cursor.execute("SELECT * FROM chats WHERE id = ?", (chat_id,))
    row = cursor.fetchone()
    conn.close()
    
    return dict(row) if row else {}

def archive_chat(chat_id: str, new_name: str) -> Dict[str, Any]:
    """Mark a chat as archived and rename it."""
    _ensure_db()
    now = time.time()

    conn = _get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "UPDATE chats SET name = ?, is_archived = 1, updated_at = ? WHERE id = ?",
        (new_name, now, chat_id)
    )
    conn.commit()
    cursor.execute("SELECT * FROM chats WHERE id = ?", (chat_id,))
    row = cursor.fetchone()
    conn.close()
    return dict(row) if row else {}

def has_archived_chats(project_id: str) -> bool:
    """Return True if this project has at least one archived chat."""
    _ensure_db()
    conn = _get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "SELECT COUNT(*) FROM chats WHERE project_id = ? AND is_archived = 1",
        (project_id,)
    )
    count = cursor.fetchone()[0]
    conn.close()
    return count > 0

# ------- Messages -------

def add_message(chat_id: str, role: str, content: str) -> Dict[str, Any]:
    """Add a message (user or assistant) to a chat."""
    _ensure_db()
    import uuid
    
    msg_id = str(uuid.uuid4())
    now = time.time()
    
    conn = _get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO messages (id, chat_id, role, content, created_at) VALUES (?, ?, ?, ?, ?)",
        (msg_id, chat_id, role, content, now)
    )
    
    # Update chat's updated_at
    cursor.execute(
        "UPDATE chats SET updated_at = ? WHERE id = ?",
        (now, chat_id)
    )
    
    conn.commit()
    conn.close()
    
    return {
        "id": msg_id,
        "chat_id": chat_id,
        "role": role,
        "content": content,
        "created_at": now,
    }

def get_chat_history(chat_id: str) -> List[Dict[str, Any]]:
    """Get all messages in a chat (in chronological order)."""
    _ensure_db()
    
    conn = _get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "SELECT * FROM messages WHERE chat_id = ? ORDER BY created_at ASC",
        (chat_id,)
    )
    rows = cursor.fetchall()
    conn.close()
    
    return [dict(row) for row in rows]

def clear_chat_history(chat_id: str) -> bool:
    """Delete all messages in a chat."""
    _ensure_db()
    
    conn = _get_connection()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM messages WHERE chat_id = ?", (chat_id,))
    conn.commit()
    conn.close()
    
    return True
