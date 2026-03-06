import os
from tools_registry import tools_by_group
import sys
import json
import sqlite3
import time
import signal
import subprocess
import socket
from pathlib import Path
from typing import Any, Dict, List, Optional

from flask import Flask, request, jsonify, render_template, redirect
from werkzeug.serving import run_simple

APP_DIR = Path(__file__).resolve().parent
def _load_dotenv(path: Path) -> None:
    """Minimal .env loader (KEY=VALUE). No dependencies, no drama."""
    try:
        if not path.exists():
            return
        for raw in path.read_text().splitlines():
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if "=" not in line:
                continue
            k, v = line.split("=", 1)
            k = k.strip()
            v = v.strip().strip('"').strip("'")
            if not k:
                continue
            # Load into process env (override empty / missing only)
            if os.environ.get(k, "") == "":
                os.environ[k] = v
    except Exception:
        # Don't brick startup because a .env has a bad day
        return

# Load ProjectEmber .env early so mounted apps (Scavenger) inherit keys
_load_dotenv(APP_DIR / ".env")

# ----------------------------
# Ember (main) Flask app
# ----------------------------
ember = Flask(__name__, static_folder="static", template_folder="templates")

# Register the scavenger inventory blueprint
from plugins.scavenger import init_scavenger
init_scavenger(ember)

# ----------------------------

# ----------------------------
# Memory persistence
# ----------------------------
MEM_DIR = Path(os.environ.get("EIRA_MEMORY_DIR", "/home/eira/.local/share/projectember/memory"))
MEM_PATH = MEM_DIR / "memory.json"

def _load_memory() -> Dict[str, str]:
    try:
        if MEM_PATH.exists():
            return json.loads(MEM_PATH.read_text())
    except Exception:
        pass
    return {}

def _save_memory(mem: Dict[str, str]) -> None:
    MEM_DIR.mkdir(parents=True, exist_ok=True)
    MEM_PATH.write_text(json.dumps(mem, indent=2, sort_keys=True))

def _memory_block() -> str:
    mem = _load_memory()
    if not mem:
        return ""
    lines = ["Known user facts (persistent memory):"]
    for k in sorted(mem.keys()):
        v = (mem.get(k) or "").strip().replace("\n", " ")
        if not v:
            continue
        lines.append(f"- {k}: {v}")
    return "\n".join(lines).strip()

# ----------------------------
# Model backend config
# ----------------------------
LLAMA_SERVER_URL = os.environ.get("LLAMA_SERVER_URL", "http://127.0.0.1:8080")
LLAMA_TIMEOUT_S = float(os.environ.get("LLAMA_TIMEOUT_S", "30"))
DEFAULT_MODE = os.environ.get("EMBER_MODE", "medium").lower()

MODE_PRESETS = {
    "quick":  {"temperature": 0.15, "n_predict": 160, "top_p": 0.9},
    "medium": {"temperature": 0.25, "n_predict": 320, "top_p": 0.95},
    "deep":   {"temperature": 0.35, "n_predict": 640, "top_p": 0.95},
}

def _mode_settings(mode: str) -> Dict[str, Any]:
    m = (mode or DEFAULT_MODE).lower()
    return MODE_PRESETS.get(m, MODE_PRESETS["medium"])

def _llama_completion(prompt: str, mode: str) -> str:
    """Call the local LLM server (OpenAI-compatible /v1/completions)."""
    import requests

    s = _mode_settings(mode)
    payload = {
        "model": "local",
        "prompt": prompt,
        "max_tokens": int(s["n_predict"]),
        "temperature": float(s["temperature"]),
        "top_p": float(s["top_p"]),
        "stream": False,
        "stop": ["User:", "EAI:", "\nUser:", "\nEAI:", "Human:", "\nHuman:"],
    }

    r = requests.post(f"{LLAMA_SERVER_URL}/v1/completions", json=payload, timeout=LLAMA_TIMEOUT_S)
    r.raise_for_status()
    data = r.json()
    return (data.get("choices", [{}])[0].get("text") or "").strip()

def _build_prompt(query: str, history: List[Dict[str, str]], system_prompt: str) -> str:
    lines: List[str] = []
    sys_parts = [system_prompt.strip()]
    mem_block = _memory_block()
    if mem_block:
        sys_parts.append("")
        sys_parts.append(mem_block)
    sys_text = "\n".join([p for p in sys_parts if p is not None]).strip()

    if sys_text:
        lines.append(sys_text)
        lines.append("")

    for m in history[-20:]:
        role = m.get("role", "")
        content = (m.get("content") or "").strip()
        if not content:
            continue
        if role == "user":
            lines.append(f"User: {content}")
        else:
            lines.append(f"Assistant: {content}")
    lines.append(f"User: {query.strip()}")
    lines.append("Assistant:")
    return "\n".join(lines)

# ----------------------------
# "Wire into system" helpers (time + process control)
# ----------------------------
@ember.get("/api/time")
def api_time():
    # This is the *server's* clock (your machine). No hallucinations.
    return jsonify({"epoch": int(time.time()), "iso": time.strftime("%Y-%m-%d %H:%M:%S %z")})

PID_DIR = Path(os.environ.get("EMBER_PID_DIR", Path.home() / ".cache" / "projectember")).resolve()
PID_DIR.mkdir(parents=True, exist_ok=True)
LLAMA_PIDFILE = PID_DIR / "llama_server.pid"

def _pid_alive(pid: int) -> bool:
    try:
        os.kill(pid, 0)
        return True
    except Exception:
        return False

def _read_pidfile(p: Path) -> Optional[int]:
    try:
        if p.exists():
            pid = int(p.read_text().strip())
            if pid > 0:
                return pid
    except Exception:
        pass
    return None

def _kill_pid(pid: int, timeout_s: float = 3.0) -> bool:
    try:
        os.kill(pid, signal.SIGTERM)
    except ProcessLookupError:
        return True
    except Exception:
        return False

    start = time.time()
    while time.time() - start < timeout_s:
        if not _pid_alive(pid):
            return True
        time.sleep(0.1)

    try:
        os.kill(pid, signal.SIGKILL)
        return True
    except Exception:
        return False

def _default_model_path() -> Optional[str]:
    # Try common location in this repo.
    candidate = APP_DIR / "ai_models" / "mistral7b" / "Mistral-7B-Instruct-v0.3-Q4_K_M.gguf"
    if candidate.exists():
        return str(candidate)
    return None

@ember.get("/api/llama/status")
def api_llama_status():
    pid = _read_pidfile(LLAMA_PIDFILE)
    running = bool(pid and _pid_alive(pid))
    return jsonify({
        "running": running,
        "pid": pid if running else None,
        "url": LLAMA_SERVER_URL,
    })

@ember.post("/api/llama/start")
def api_llama_start():
    # If already running, no drama.
    pid = _read_pidfile(LLAMA_PIDFILE)
    if pid and _pid_alive(pid):
        return jsonify({"ok": True, "already_running": True, "pid": pid})

    model = (request.get_json(silent=True) or {}).get("model") or os.environ.get("LLAMA_MODEL") or _default_model_path()
    if not model:
        return jsonify({"ok": False, "error": "No model path provided. Set LLAMA_MODEL env var or pass JSON {\"model\": \"/path/model.gguf\"}."}), 400

    host = os.environ.get("LLAMA_HOST", "127.0.0.1")
    port = int(os.environ.get("LLAMA_PORT", "8080"))

    cmd = [
        sys.executable, "-m", "llama_cpp.server",
        "--model", model,
        "--host", host,
        "--port", str(port),
    ]

    # Detached-ish: keep stdout/stderr in a log so you can debug without crying.
    log_path = PID_DIR / "llama_server.log"
    logf = open(log_path, "a", buffering=1)
    proc = subprocess.Popen(cmd, stdout=logf, stderr=logf, cwd=str(APP_DIR))
    LLAMA_PIDFILE.write_text(str(proc.pid))

    return jsonify({"ok": True, "pid": proc.pid, "model": model, "log": str(log_path)})

@ember.post("/api/llama/stop")
def api_llama_stop():
    pid = _read_pidfile(LLAMA_PIDFILE)
    if not pid:
        return jsonify({"ok": True, "already_stopped": True})
    if not _pid_alive(pid):
        try:
            LLAMA_PIDFILE.unlink(missing_ok=True)  # py3.8+ in practice
        except Exception:
            pass
        return jsonify({"ok": True, "already_stopped": True})

    ok = _kill_pid(pid)
    if ok:
        try:
            LLAMA_PIDFILE.unlink(missing_ok=True)
        except Exception:
            pass
    return jsonify({"ok": ok, "pid": pid})


# ----------------------------
# Admin API
# ----------------------------
def _tcp_check(host: str, port: int, timeout: float = 0.5) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except Exception:
        return False

def _parse_host_port(url: str) -> (str, int):
    # very small parser for http://host:port
    try:
        u = url.strip()
        if "://" in u:
            u = u.split("://", 1)[1]
        u = u.split("/", 1)[0]
        if ":" in u:
            h, p = u.rsplit(":", 1)
            return h, int(p)
        return u, 80
    except Exception:
        return "127.0.0.1", 8080

@ember.get("/api/admin/status")
def api_admin_status():
    # Note: do NOT return secret values. Only presence flags.
    ember_host = os.environ.get("EMBER_HOST", "0.0.0.0")
    ember_port = int(os.environ.get("EMBER_PORT", "5000"))

    llama_host, llama_port = _parse_host_port(LLAMA_SERVER_URL)

    llama_pid = _read_pidfile(LLAMA_PIDFILE)
    llama_alive = bool(llama_pid and _pid_alive(llama_pid))

    scavenger_dir = SCAVENGER_DIR
    scavenger_exists = Path(scavenger_dir).exists()
    scavenger_app_path = Path(scavenger_dir) / "app" / "app.py"
    scavenger_app_exists = scavenger_app_path.exists()

    return jsonify({
        "ok": True,
        "ember": {
            "pid": os.getpid(),
            "host": ember_host,
            "port": ember_port,
            "listening": _tcp_check(ember_host, ember_port),
        },
        "llama": {
            "server_url": LLAMA_SERVER_URL,
            "host": llama_host,
            "port": llama_port,
            "listening": _tcp_check(llama_host, llama_port),
            "pidfile": str(LLAMA_PIDFILE),
            "pid": llama_pid,
            "alive": llama_alive,
            "log": str(PID_DIR / "llama_server.log"),
        },
        "inventory": {
            "mount_path": SCAVENGER_MOUNT_PATH,
            "scavenger_dir": scavenger_dir,
            "dir_exists": scavenger_exists,
            "app_py_exists": scavenger_app_exists,
        },
        "env_flags": {
            "BRAVE_ENABLED": bool(os.environ.get("BRAVE_ENABLED")),
            "BRAVE_API_KEY": bool(os.environ.get("BRAVE_API_KEY")),
            "OPENAI_API_KEY": bool(os.environ.get("OPENAI_API_KEY")),
            "OPENAI_MODEL": bool(os.environ.get("OPENAI_MODEL")),
        },
    })

# ----------------------------
# Cache Inspector API (Scavenger SQLite)
# ----------------------------
SCAVENGER_DB_PATH = Path(os.environ.get("SCAVENGER_DB_PATH", str(APP_DIR / "app" / "data" / "scavenger.sqlite"))).resolve()

def _db_connect():
    conn = sqlite3.connect(str(SCAVENGER_DB_PATH))
    conn.row_factory = sqlite3.Row
    return conn

@ember.get("/api/cache/part/search")
def api_cache_part_search():
    mpn = (request.args.get("mpn") or "").strip()
    like = (request.args.get("like") or "").strip() in ("1", "true", "yes", "on")
    include_payload = (request.args.get("payload") or "").strip() in ("1", "true", "yes", "on")
    try:
        limit = int(request.args.get("limit") or "50")
    except Exception:
        limit = 50
    limit = max(1, min(limit, 200))

    if not SCAVENGER_DB_PATH.exists():
        return jsonify({"ok": False, "error": f"DB not found: {SCAVENGER_DB_PATH}"}), 404

    q = "SELECT mpn, confidence, source, updated_at, payload_json FROM part_enrich_cache"
    params = []
    if mpn:
        if like:
            q += " WHERE mpn LIKE ?"
            params.append(f"%{mpn}%")
        else:
            q += " WHERE mpn = ?"
            params.append(mpn)
    q += " ORDER BY updated_at DESC LIMIT ?"
    params.append(limit)

    rows = []
    with _db_connect() as conn:
        for r in conn.execute(q, params):
            payload = r["payload_json"] or ""
            rows.append({
                "mpn": r["mpn"],
                "confidence": r["confidence"],
                "source": r["source"],
                "updated_at": r["updated_at"],
                "payload_len": len(payload),
                "payload_preview": payload[:400] + ("…" if len(payload) > 400 else ""),
                **({"payload_json": payload} if include_payload else {}),
            })

    return jsonify({"ok": True, "db_path": str(SCAVENGER_DB_PATH), "count": len(rows), "rows": rows})

@ember.post("/api/cache/part/delete")
def api_cache_part_delete():
    data = request.get_json(force=True, silent=True) or {}
    mpn = (data.get("mpn") or "").strip()
    if not mpn:
        return jsonify({"ok": False, "error": "Missing mpn"}), 400
    if not SCAVENGER_DB_PATH.exists():
        return jsonify({"ok": False, "error": f"DB not found: {SCAVENGER_DB_PATH}"}), 404

    with _db_connect() as conn:
        cur = conn.execute("DELETE FROM part_enrich_cache WHERE mpn = ?", (mpn,))
        conn.commit()
        deleted = cur.rowcount

    return jsonify({"ok": True, "mpn": mpn, "deleted": deleted})

@ember.get("/api/cache/web/search")
def api_cache_web_search():
    query = (request.args.get("query") or "").strip()
    like = (request.args.get("like") or "").strip() in ("1", "true", "yes", "on")
    try:
        limit = int(request.args.get("limit") or "50")
    except Exception:
        limit = 50
    limit = max(1, min(limit, 300))

    if not SCAVENGER_DB_PATH.exists():
        return jsonify({"ok": False, "error": f"DB not found: {SCAVENGER_DB_PATH}"}), 404

    q = "SELECT query, rank, url, title, snippet, fetched_at FROM web_search_cache"
    params = []
    if query:
        if like:
            q += " WHERE query LIKE ?"
            params.append(f"%{query}%")
        else:
            q += " WHERE query = ?"
            params.append(query)
    q += " ORDER BY fetched_at DESC, query ASC, rank ASC LIMIT ?"
    params.append(limit)

    rows = []
    with _db_connect() as conn:
        for r in conn.execute(q, params):
            rows.append({
                "query": r["query"],
                "rank": r["rank"],
                "url": r["url"],
                "title": r["title"],
                "snippet": r["snippet"],
                "fetched_at": r["fetched_at"],
            })

    return jsonify({"ok": True, "db_path": str(SCAVENGER_DB_PATH), "count": len(rows), "rows": rows})

@ember.post("/api/cache/web/delete")
def api_cache_web_delete():
    data = request.get_json(force=True, silent=True) or {}
    query = (data.get("query") or "").strip()
    rank = data.get("rank", None)
    delete_all = bool(data.get("all_for_query"))
    if not query:
        return jsonify({"ok": False, "error": "Missing query"}), 400
    if not SCAVENGER_DB_PATH.exists():
        return jsonify({"ok": False, "error": f"DB not found: {SCAVENGER_DB_PATH}"}), 404

    with _db_connect() as conn:
        if delete_all:
            cur = conn.execute("DELETE FROM web_search_cache WHERE query = ?", (query,))
        else:
            if rank is None:
                return jsonify({"ok": False, "error": "Missing rank (or set all_for_query=true)"}), 400
            try:
                rank_i = int(rank)
            except Exception:
                return jsonify({"ok": False, "error": "rank must be an integer"}), 400
            cur = conn.execute("DELETE FROM web_search_cache WHERE query = ? AND rank = ?", (query, rank_i))
        conn.commit()
        deleted = cur.rowcount

    return jsonify({"ok": True, "query": query, "rank": rank, "all_for_query": delete_all, "deleted": deleted})


# ----------------------------
# Scavenger Bins + Categories (Admin CRUD)
# ----------------------------
SCAVENGER_DB_PATH = Path(os.environ.get("SCAVENGER_DB_PATH", APP_DIR / "app" / "data" / "scavenger.sqlite")).resolve()

def _now_iso() -> str:
    # ISO-ish, sortable, local time
    return time.strftime("%Y-%m-%dT%H:%M:%S")

def _scav_db_connect():
    import sqlite3
    conn = sqlite3.connect(str(SCAVENGER_DB_PATH))
    conn.row_factory = sqlite3.Row
    try:
        conn.execute("PRAGMA foreign_keys=ON;")
    except Exception:
        pass
    return conn

@ember.get("/api/bins/list")
def api_bins_list():
    q = (request.args.get("q") or "").strip()
    like = (request.args.get("like") or "").strip() in ("1", "true", "yes", "on")
    limit = int(request.args.get("limit") or "100")
    limit = max(1, min(limit, 500))

    sql = "SELECT id, code, name, description, created_at, updated_at FROM bins"
    params = []
    if q:
        if like:
            sql += " WHERE code LIKE ? OR name LIKE ? OR description LIKE ?"
            qq = f"%{q}%"
            params += [qq, qq, qq]
        else:
            sql += " WHERE code = ? OR name = ?"
            params += [q, q]
    sql += " ORDER BY code ASC LIMIT ?"
    params.append(limit)

    try:
        conn = _scav_db_connect()
        rows = [dict(r) for r in conn.execute(sql, params).fetchall()]
        conn.close()
        return jsonify({"ok": True, "rows": rows})
    except Exception as e:
        return jsonify({"ok": False, "error": f"{type(e).__name__}: {e}"}), 500

@ember.post("/api/bins/upsert")
def api_bins_upsert():
    data = request.get_json(force=True, silent=True) or {}
    bid = data.get("id")
    code = (data.get("code") or "").strip()
    name = (data.get("name") or "").strip() or None
    desc = (data.get("description") or "").strip() or None

    if not code:
        return jsonify({"ok": False, "error": "Missing code"}), 400

    now = _now_iso()
    try:
        conn = _scav_db_connect()
        if bid:
            conn.execute(
                "UPDATE bins SET code=?, name=?, description=?, updated_at=? WHERE id=?",
                (code, name, desc, now, int(bid)),
            )
        else:
            conn.execute(
                "INSERT INTO bins(code, name, description, created_at, updated_at) VALUES(?,?,?,?,?)",
                (code, name, desc, now, now),
            )
        conn.commit()
        # Return the row by code (unique)
        row = conn.execute(
            "SELECT id, code, name, description, created_at, updated_at FROM bins WHERE code=?",
            (code,),
        ).fetchone()
        conn.close()
        return jsonify({"ok": True, "row": dict(row) if row else None})
    except Exception as e:
        return jsonify({"ok": False, "error": f"{type(e).__name__}: {e}"}), 500

@ember.post("/api/bins/delete")
def api_bins_delete():
    data = request.get_json(force=True, silent=True) or {}
    bid = data.get("id")
    code = (data.get("code") or "").strip()
    if not bid and not code:
        return jsonify({"ok": False, "error": "Provide id or code"}), 400
    try:
        conn = _scav_db_connect()
        if bid:
            cur = conn.execute("DELETE FROM bins WHERE id=?", (int(bid),))
        else:
            cur = conn.execute("DELETE FROM bins WHERE code=?", (code,))
        conn.commit()
        deleted = cur.rowcount if cur is not None else 0
        conn.close()
        return jsonify({"ok": True, "deleted": deleted})
    except Exception as e:
        return jsonify({"ok": False, "error": f"{type(e).__name__}: {e}"}), 500

@ember.get("/api/categories/list")
def api_categories_list():
    q = (request.args.get("q") or "").strip()
    like = (request.args.get("like") or "").strip() in ("1", "true", "yes", "on")
    limit = int(request.args.get("limit") or "200")
    limit = max(1, min(limit, 1000))

    sql = """SELECT id, slug, name, description, parent_id, created_at, updated_at
             FROM categories"""
    params = []
    if q:
        if like:
            qq = f"%{q}%"
            sql += " WHERE slug LIKE ? OR name LIKE ? OR description LIKE ?"
            params += [qq, qq, qq]
        else:
            sql += " WHERE slug = ? OR name = ?"
            params += [q, q]
    sql += " ORDER BY COALESCE(parent_id, id), parent_id IS NOT NULL, name ASC LIMIT ?"
    params.append(limit)

    try:
        conn = _scav_db_connect()
        rows = [dict(r) for r in conn.execute(sql, params).fetchall()]
        conn.close()
        return jsonify({"ok": True, "rows": rows})
    except Exception as e:
        return jsonify({"ok": False, "error": f"{type(e).__name__}: {e}"}), 500

@ember.get("/api/categories/tree")
def api_categories_tree():
    try:
        conn = _scav_db_connect()
        rows = [dict(r) for r in conn.execute(
            "SELECT id, slug, name, description, parent_id FROM categories ORDER BY name ASC"
        ).fetchall()]
        conn.close()
        by_id = {r["id"]: r for r in rows}
        for r in rows:
            r["children"] = []
        roots = []
        for r in rows:
            pid = r.get("parent_id")
            if pid and pid in by_id:
                by_id[pid]["children"].append(r)
            else:
                roots.append(r)
        return jsonify({"ok": True, "roots": roots})
    except Exception as e:
        return jsonify({"ok": False, "error": f"{type(e).__name__}: {e}"}), 500

@ember.post("/api/categories/upsert")
def api_categories_upsert():
    data = request.get_json(force=True, silent=True) or {}
    cid = data.get("id")
    slug = (data.get("slug") or "").strip()
    name = (data.get("name") or "").strip()
    desc = (data.get("description") or "").strip() or None
    parent_id = data.get("parent_id")
    parent_id = int(parent_id) if str(parent_id).strip() not in ("", "None", "null") else None

    if not slug:
        return jsonify({"ok": False, "error": "Missing slug"}), 400
    if not name:
        return jsonify({"ok": False, "error": "Missing name"}), 400

    now = _now_iso()
    try:
        conn = _scav_db_connect()
        if cid:
            conn.execute(
                "UPDATE categories SET slug=?, name=?, description=?, parent_id=?, updated_at=? WHERE id=?",
                (slug, name, desc, parent_id, now, int(cid)),
            )
        else:
            conn.execute(
                "INSERT INTO categories(slug, name, description, parent_id, created_at, updated_at) VALUES(?,?,?,?,?,?)",
                (slug, name, desc, parent_id, now, now),
            )
        conn.commit()
        row = conn.execute(
            "SELECT id, slug, name, description, parent_id, created_at, updated_at FROM categories WHERE slug=?",
            (slug,),
        ).fetchone()
        conn.close()
        return jsonify({"ok": True, "row": dict(row) if row else None})
    except Exception as e:
        return jsonify({"ok": False, "error": f"{type(e).__name__}: {e}"}), 500

@ember.post("/api/categories/delete")
def api_categories_delete():
    data = request.get_json(force=True, silent=True) or {}
    cid = data.get("id")
    force = bool(data.get("force"))
    if not cid:
        return jsonify({"ok": False, "error": "Missing id"}), 400
    cid = int(cid)
    try:
        conn = _scav_db_connect()
        # check children
        child_count = conn.execute("SELECT COUNT(*) AS c FROM categories WHERE parent_id=?", (cid,)).fetchone()["c"]
        if child_count and not force:
            conn.close()
            return jsonify({"ok": False, "error": f"Category has {child_count} child(ren). Set force=true to delete (children will be detached)."}), 400
        if child_count and force:
            conn.execute("UPDATE categories SET parent_id=NULL, updated_at=? WHERE parent_id=?", (_now_iso(), cid))
        cur = conn.execute("DELETE FROM categories WHERE id=?", (cid,))
        conn.commit()
        deleted = cur.rowcount if cur is not None else 0
        conn.close()
        return jsonify({"ok": True, "deleted": deleted, "children_detached": int(child_count) if (child_count and force) else 0})
    except Exception as e:
        return jsonify({"ok": False, "error": f"{type(e).__name__}: {e}"}), 500

from tools_registry import tools_by_group, get_tool

# ----------------------------
# Pages
# ----------------------------
@ember.get("/")
def home():
    mode = request.args.get("mode", DEFAULT_MODE)
    return render_template("index.html", page="chat", mode=mode, tool_groups=tools_by_group())
@ember.get("/tools/_legacy_inventory")
def tools_inventory():
    # legacy path; tool host is canonical
    from flask import redirect
    return redirect("/tools/inventory", code=302)


@ember.get("/tools/_legacy_admin")
def tools_admin():
    # legacy path; tool host is canonical
    from flask import redirect
    return redirect("/tools/admin", code=302)


@ember.get("/control")
def control():
    # Standalone control page with buttons; doesn't require changing your existing UI.
    return render_template("control.html")

@ember.get("/admin")
def admin():
    # Project-wide Admin Console (status/config/logs/cache/etc.)
    return render_template("admin.html")


@ember.get("/tools/<tool_id>")
def tool_host(tool_id):
    # Canonical tool host page: renders Ember chrome + embeds tool content.
    from flask import abort
    mode = request.args.get("mode", DEFAULT_MODE)
    t = get_tool(tool_id)
    if not t:
        abort(404)
    return render_template(
        "index.html",
        page="tool",
        mode=mode,
        tool=t,
        tool_id=tool_id,
        tool_groups=tools_by_group(),
    )

import re

def _process_memory_commands(response: str) -> str:
    """Extract [MEMORY_SAVE: key | value] and [MEMORY_DELETE: key] from Eira's response,
    process them, and return the cleaned response with those tags removed."""
    mem = _load_memory()
    changed = False

    # Process saves: [MEMORY_SAVE: key | value]
    save_pattern = re.compile(r'\[MEMORY_SAVE:\s*([^|]+?)\s*\|\s*(.+?)\s*\]')
    for match in save_pattern.finditer(response):
        key = match.group(1).strip()
        value = match.group(2).strip()
        if key and value:
            mem[key] = value
            changed = True

    # Process deletes: [MEMORY_DELETE: key]
    del_pattern = re.compile(r'\[MEMORY_DELETE:\s*(.+?)\s*\]')
    for match in del_pattern.finditer(response):
        key = match.group(1).strip()
        if key:
            mem.pop(key, None)
            changed = True

    if changed:
        _save_memory(mem)

    # Strip memory tags from the response the user sees
    cleaned = save_pattern.sub('', response)
    cleaned = del_pattern.sub('', cleaned)
    cleaned = cleaned.strip()

    return cleaned

# ----------------------------
# Chat API (existing UI uses /query)
# ----------------------------
@ember.post("/query")
def query():
    from subminds import orchestrate

    data = request.get_json(force=True, silent=True) or {}
    q = (data.get("query") or "").strip()
    history = data.get("history") or []
    mode = (data.get("mode") or DEFAULT_MODE).lower()

    if not q:
        return jsonify({"response": ""})

    mem_block = _memory_block()

    explicit_prompt = (data.get("system_prompt") or "").strip()
    if explicit_prompt:
        from submind_router import _make_routing
        prompt = _build_prompt(q, history, explicit_prompt)
        try:
            resp = _llama_completion(prompt, mode=mode)
            return jsonify({"response": resp, "routing": _make_routing("override", "explicit system_prompt supplied"), "submind_id": "override"})
        except Exception as e:
            return jsonify({"response": f"Error: {type(e).__name__}: {e}"}), 200

    try:
        force_general = bool(data.get("force_general", False))
        result = orchestrate(query=q, history=history, llm_fn=_llama_completion, mode=mode, memory_block=mem_block, force_general=force_general)
        response = result["response"]

        # Extract and process memory commands from Eira's response
        response = _process_memory_commands(response)

        return jsonify({"response": response, "routing": result["routing"], "submind_id": result["submind_id"]})
    except Exception as e:
        return jsonify({"response": f"Error: {type(e).__name__}: {e}"}), 200


@ember.get("/health")
def health():
    return jsonify({"status": "ok", "llama_server": LLAMA_SERVER_URL})

# ----------------------------
# Memory API
# ----------------------------

# ----------------------------
# Scavenger Inventory mount
# ----------------------------
SCAVENGER_DIR = os.environ.get("SCAVENGER_DIR", "/home/daddy/scavenger-inventory").strip()
SCAVENGER_MOUNT_PATH = "/inventory"
INVENTORY_CSV_PATH = Path(os.environ.get("INVENTORY_CSV", os.path.join(SCAVENGER_DIR, "inventory.csv"))).resolve()

# ----------------------------
# Scavenger Inventory Items API (CSV-backed)
# ----------------------------
import csv

def _load_inventory():
    """Load inventory.csv → (headers, rows). Returns ([], []) if file missing."""
    if not INVENTORY_CSV_PATH.exists():
        return [], []
    with INVENTORY_CSV_PATH.open(newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        headers = list(reader.fieldnames or [])
        rows = [dict(r) for r in reader]
    return headers, rows

def _atomic_write_csv(headers: List[str], rows: List[dict]) -> str:
    """Write rows back to inventory.csv atomically.

    Creates a timestamped backup of the current file first (matching the
    scavenger app's own atomic_write_csv behaviour), then writes to a .tmp
    file and replaces the original.  Returns the backup path.
    """
    import shutil
    from datetime import datetime as _dt

    INVENTORY_CSV_PATH.parent.mkdir(parents=True, exist_ok=True)

    # Backup only if the file already exists (nothing to back up on first write)
    backup_path = ""
    if INVENTORY_CSV_PATH.exists():
        ts = _dt.now().strftime("%Y%m%d_%H%M%S")
        backup_path = str(
            INVENTORY_CSV_PATH.with_name(
                f"{INVENTORY_CSV_PATH.stem}_backup_{ts}.csv"
            )
        )
        shutil.copy2(str(INVENTORY_CSV_PATH), backup_path)

    tmp_path = INVENTORY_CSV_PATH.with_suffix(".tmp")
    try:
        with tmp_path.open("w", newline="", encoding="utf-8") as fh:
            writer = csv.DictWriter(fh, fieldnames=headers, extrasaction="ignore")
            writer.writeheader()
            for r in rows:
                writer.writerow({h: r.get(h, "") for h in headers})
        tmp_path.replace(INVENTORY_CSV_PATH)
    except Exception:
        try:
            tmp_path.unlink(missing_ok=True)
        except OSError:
            pass
        raise

    return backup_path

def _find_by_mpn(rows: List[dict], mpn: str) -> Optional[dict]:
    """Return the first row matching mpn (case-insensitive)."""
    mpn_l = mpn.strip().lower()
    for r in rows:
        if (r.get("mpn") or "").strip().lower() == mpn_l:
            return r
    return None

@ember.get("/api/inventory/list")
def api_inventory_list():
    """List / search / filter inventory items from the CSV."""
    q = (request.args.get("q") or "").strip().lower()
    cat_filter = (request.args.get("category") or "").strip().lower()
    loc_filter = (request.args.get("location_bin") or "").strip().lower()
    limit = int(request.args.get("limit") or "500")
    limit = max(1, min(limit, 2000))

    try:
        headers, rows = _load_inventory()
    except Exception as e:
        return jsonify({"ok": False, "error": f"{type(e).__name__}: {e}"}), 500

    filtered = []
    for r in rows:
        if q:
            haystack = " ".join((v or "") for v in r.values()).lower()
            if q not in haystack:
                continue
        if cat_filter:
            if (r.get("category") or "").strip().lower() != cat_filter:
                continue
        if loc_filter:
            if (r.get("location_bin") or "").strip().lower() != loc_filter:
                continue
        filtered.append(r)

    shown = filtered[:limit]

    # Build unique lists for filter dropdowns
    categories = sorted({(r.get("category") or "").strip() for r in rows if r.get("category")})
    locations = sorted({(r.get("location_bin") or "").strip() for r in rows if r.get("location_bin")})

    return jsonify({
        "ok": True,
        "headers": headers,
        "rows": shown,
        "total": len(rows),
        "shown": len(shown),
        "categories": categories,
        "locations": locations,
    })

@ember.get("/api/inventory/item/<mpn>")
def api_inventory_item(mpn):
    """Get a single inventory item by MPN."""
    mpn = (mpn or "").strip()
    if not mpn:
        return jsonify({"ok": False, "error": "MPN required"}), 400
    try:
        headers, rows = _load_inventory()
    except Exception as e:
        return jsonify({"ok": False, "error": f"{type(e).__name__}: {e}"}), 500
    row = _find_by_mpn(rows, mpn)
    if row is None:
        return jsonify({"ok": False, "error": f"MPN not found: {mpn}"}), 404
    return jsonify({"ok": True, "headers": headers, "row": row})

@ember.post("/api/inventory/add")
def api_inventory_add():
    """Add a new part (or merge qty if MPN already exists)."""
    data = request.get_json(force=True, silent=True) or {}
    mpn = (data.get("mpn") or "").strip()
    if not mpn:
        return jsonify({"ok": False, "error": "mpn required"}), 400

    try:
        headers, rows = _load_inventory()
    except Exception as e:
        return jsonify({"ok": False, "error": f"{type(e).__name__}: {e}"}), 500

    # Ensure standard columns exist in headers
    for col in ["mpn", "description", "category", "location_bin", "qty_on_hand", "notes", "needs_review"]:
        if col not in headers:
            headers.append(col)

    existing = _find_by_mpn(rows, mpn)
    merged = existing is not None

    if existing:
        # Merge: increment qty, append notes, propagate needs_review flag,
        # update all other non-empty incoming fields — matching scavenger app.py.
        new_qty_raw = str(data.get("qty_on_hand") or "").strip()
        if new_qty_raw:
            try:
                old_qty = int(float(existing.get("qty_on_hand") or 0))
                add_qty = int(float(new_qty_raw))
                existing["qty_on_hand"] = str(old_qty + add_qty)
            except ValueError:
                existing["qty_on_hand"] = new_qty_raw
        for col in headers:
            if col in ("mpn", "qty_on_hand"):
                continue
            val = str(data.get(col) or "").strip()
            if not val:
                continue
            if col == "notes":
                old_notes = (existing.get("notes") or "").strip()
                if old_notes and val not in old_notes:
                    existing["notes"] = old_notes + "\n" + val
                else:
                    existing["notes"] = val
            elif col == "needs_review" and val.lower() in ("1", "true", "yes", "y", "on"):
                existing["needs_review"] = "true"
            else:
                existing[col] = val
        row = existing
    else:
        row = {col: str(data.get(col) or "") for col in headers}
        row["mpn"] = mpn
        rows.append(row)

    try:
        _atomic_write_csv(headers, rows)
    except Exception as e:
        return jsonify({"ok": False, "error": f"{type(e).__name__}: {e}"}), 500

    return jsonify({"ok": True, "row": row, "merged": merged})

@ember.post("/api/inventory/update/<mpn>")
def api_inventory_update(mpn):
    """Update fields of an existing part."""
    mpn = (mpn or "").strip()
    if not mpn:
        return jsonify({"ok": False, "error": "MPN required"}), 400
    data = request.get_json(force=True, silent=True) or {}

    try:
        headers, rows = _load_inventory()
    except Exception as e:
        return jsonify({"ok": False, "error": f"{type(e).__name__}: {e}"}), 500

    row = _find_by_mpn(rows, mpn)
    if row is None:
        return jsonify({"ok": False, "error": f"MPN not found: {mpn}"}), 404

    for col in headers:
        if col == "mpn":
            continue
        if col in data:
            row[col] = str(data[col] or "")

    try:
        _atomic_write_csv(headers, rows)
    except Exception as e:
        return jsonify({"ok": False, "error": f"{type(e).__name__}: {e}"}), 500

    return jsonify({"ok": True, "row": row})

@ember.post("/api/inventory/delete/<mpn>")
def api_inventory_delete(mpn):
    """Delete a part by MPN."""
    mpn = (mpn or "").strip()
    if not mpn:
        return jsonify({"ok": False, "error": "MPN required"}), 400

    try:
        headers, rows = _load_inventory()
    except Exception as e:
        return jsonify({"ok": False, "error": f"{type(e).__name__}: {e}"}), 500

    new_rows = [r for r in rows if (r.get("mpn") or "").strip().lower() != mpn.lower()]
    if len(new_rows) == len(rows):
        return jsonify({"ok": False, "error": f"MPN not found: {mpn}"}), 404

    try:
        _atomic_write_csv(headers, new_rows)
    except Exception as e:
        return jsonify({"ok": False, "error": f"{type(e).__name__}: {e}"}), 500

    return jsonify({"ok": True, "deleted": mpn})

def build_wsgi_app():
    return ember

def main():
    application = build_wsgi_app()

    host = os.environ.get("EMBER_HOST", "127.0.0.1")
    port = int(os.environ.get("EMBER_PORT", "5000"))
    debug = os.environ.get("EMBER_DEBUG", "1") not in ("0", "false", "no")

    run_simple(
        hostname=host,
        port=port,
        application=application,
        use_reloader=debug,
        use_debugger=debug,
        threaded=True,
    )



# ------- Projects API -------

@ember.post("/api/projects/create")
def api_create_project():
    """Create a new project."""
    from chat_storage import create_project
    data = request.get_json(force=True, silent=True) or {}
    name = (data.get("name") or "Untitled Project").strip()
    project = create_project(name)
    return jsonify(project)

@ember.get("/api/projects/list")
def api_list_projects():
    """List all projects."""
    from chat_storage import list_projects
    projects = list_projects()
    return jsonify({"projects": projects})

@ember.get("/api/projects/<project_id>")
def api_get_project(project_id):
    """Get a single project."""
    from chat_storage import get_project
    project = get_project(project_id)
    if not project:
        return jsonify({"error": "Not found"}), 404
    return jsonify(project)

@ember.delete("/api/projects/<project_id>")
def api_delete_project(project_id):
    """Delete a project."""
    from chat_storage import delete_project
    delete_project(project_id)
    return jsonify({"ok": True})

@ember.post("/api/projects/<project_id>/rename")
def api_rename_project(project_id):
    """Rename a project."""
    from chat_storage import rename_project
    data = request.get_json(force=True, silent=True) or {}
    new_name = (data.get("name") or "Untitled").strip()
    project = rename_project(project_id, new_name)
    return jsonify(project)

# ------- Chats API -------

@ember.post("/api/chats/create")
def api_create_chat():
    """Create a new chat in a project."""
    from chat_storage import create_chat
    data = request.get_json(force=True, silent=True) or {}
    project_id = data.get("project_id") or ""
    name = (data.get("name") or "New Chat").strip()
    
    if not project_id:
        return jsonify({"error": "project_id required"}), 400
    
    chat = create_chat(project_id, name)
    return jsonify(chat)

@ember.get("/api/chats/list/<project_id>")
def api_list_chats(project_id):
    """List all chats in a project."""
    from chat_storage import list_chats
    chats = list_chats(project_id)
    return jsonify({"chats": chats})

@ember.get("/api/chats/<chat_id>")
def api_get_chat(chat_id):
    """Get a single chat."""
    from chat_storage import get_chat
    chat = get_chat(chat_id)
    if not chat:
        return jsonify({"error": "Not found"}), 404
    return jsonify(chat)

@ember.post("/api/chats/<chat_id>/rename")
def api_rename_chat(chat_id):
    """Rename a chat."""
    from chat_storage import rename_chat
    data = request.get_json(force=True, silent=True) or {}
    new_name = (data.get("name") or "Untitled").strip()
    chat = rename_chat(chat_id, new_name)
    return jsonify(chat)

@ember.delete("/api/chats/<chat_id>")
def api_delete_chat(chat_id):
    """Delete a chat."""
    from chat_storage import delete_chat
    delete_chat(chat_id)
    return jsonify({"ok": True})

# ------- Messages API -------

@ember.post("/api/messages/add")
def api_add_message():
    """Add a message to a chat."""
    from chat_storage import add_message
    data = request.get_json(force=True, silent=True) or {}
    chat_id = data.get("chat_id") or ""
    role = data.get("role") or "user"
    content = data.get("content") or ""
    
    if not chat_id or not content:
        return jsonify({"error": "chat_id and content required"}), 400
    
    msg = add_message(chat_id, role, content)
    return jsonify(msg)

@ember.get("/api/messages/history/<chat_id>")
def api_get_history(chat_id):
    """Get all messages in a chat."""
    from chat_storage import get_chat_history
    messages = get_chat_history(chat_id)
    return jsonify({"messages": messages})

@ember.delete("/api/messages/clear/<chat_id>")
def api_clear_history(chat_id):
    """Clear all messages in a chat."""
    from chat_storage import clear_chat_history
    clear_chat_history(chat_id)
    return jsonify({"ok": True})

@ember.route("/app")
def chat_app():
    """Serve the chat app frontend."""
    from flask import send_file
    return send_file('templates/index.html', mimetype='text/html')

@ember.get("/inventory-page")
def inventory_page():
    """Native inventory page using Ember's theme."""
    return render_template("inventory.html")

@ember.route("/inventory-only")
def inventory_only():
    """Serve the native scavenger inventory page."""
    return render_template("inventory.html")

if __name__ == "__main__":
    main()
