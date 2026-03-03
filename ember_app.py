import os
import sys
import json
import time
import signal
import subprocess
from pathlib import Path
from typing import Any, Dict, List, Optional

from flask import Flask, request, jsonify, render_template
from werkzeug.middleware.dispatcher import DispatcherMiddleware
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

# ----------------------------
# Personality + honesty guardrails
# ----------------------------
DEFAULT_SYSTEM_PROMPT = (
    "You are a local assistant running on the user's machine.\n"
    "Voice: blunt, witty, disciplined chaos goblin. You can swear.\n"
    "Be honest about limits. If you cannot access something (time, files, network), say so plainly.\n"
    "Default to concise replies (1-3 sentences) unless the user asks for detail.\n"
    "Do not invent tools, services, websites, or actions you didn't do.\n"
    "Do not claim you ran commands unless the user provided the output.\n"
)

# ----------------------------
# Persistent memory (simple JSON)
# ----------------------------
MEM_DIR = Path(os.environ.get("EMBER_MEM_DIR", Path.home() / ".local" / "share" / "projectember")).resolve()
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
# Pages
# ----------------------------
@ember.get("/")
def home():
    mode = request.args.get("mode", DEFAULT_MODE)
    return render_template("index.html", page="chat", mode=mode)

@ember.get("/tools/inventory")
def tools_inventory():
    return render_template("index.html", page="inventory", mode=DEFAULT_MODE)

@ember.get("/control")
def control():
    # Standalone control page with buttons; doesn't require changing your existing UI.
    return render_template("control.html")

# ----------------------------
# Chat API (existing UI uses /query)
# ----------------------------
@ember.post("/query")
def query():
    data = request.get_json(force=True, silent=True) or {}
    q = (data.get("query") or "").strip()
    history = data.get("history") or []
    mode = (data.get("mode") or DEFAULT_MODE).lower()

    # Allow overriding system prompt, but default to our chaos-with-discipline one.
    system_prompt = (data.get("system_prompt") or DEFAULT_SYSTEM_PROMPT).strip()

    if not q:
        return jsonify({"response": ""})

    prompt = _build_prompt(q, history, system_prompt)
    try:
        resp = _llama_completion(prompt, mode=mode)
        return jsonify({"response": resp})
    except Exception as e:
        return jsonify({"response": f"Error: {type(e).__name__}: {e}"}), 200

@ember.get("/health")
def health():
    return jsonify({"status": "ok", "llama_server": LLAMA_SERVER_URL})

# ----------------------------
# Memory API
# ----------------------------
@ember.get("/api/memory/list")
def api_memory_list():
    return jsonify({"ok": True, "memory": _load_memory()})

@ember.post("/api/memory/set")
def api_memory_set():
    data = request.get_json(force=True, silent=True) or {}
    key = (data.get("key") or "").strip()
    value = (data.get("value") or "").strip()
    if not key:
        return jsonify({"ok": False, "error": "Missing key"}), 400
    mem = _load_memory()
    if value:
        mem[key] = value
    else:
        mem.pop(key, None)
    _save_memory(mem)
    return jsonify({"ok": True, "memory": mem})

# ----------------------------
# Scavenger Inventory mount
# ----------------------------
SCAVENGER_DIR = os.environ.get("SCAVENGER_DIR", "/home/daddy/scavenger-inventory").strip()
SCAVENGER_MOUNT_PATH = "/inventory"

def _load_scavenger_app(scavenger_dir: str):
    """Load Scavenger Inventory's Flask module reliably (no 'app' collisions)."""
    import importlib.util
    import os
    import sys
    import types

    scavenger_dir = os.path.abspath(scavenger_dir)
    pkg_dir = os.path.join(scavenger_dir, "app")
    app_py = os.path.join(pkg_dir, "app.py")
    if not os.path.isdir(pkg_dir) or not os.path.isfile(app_py):
        raise FileNotFoundError(f"Scavenger app not found at: {app_py}")

    # Purge any cached 'app' modules.
    for k in list(sys.modules.keys()):
        if k == "app" or k.startswith("app."):
            sys.modules.pop(k, None)

    # Create synthetic package 'app'
    pkg = types.ModuleType("app")
    pkg.__path__ = [pkg_dir]  # type: ignore[attr-defined]
    sys.modules["app"] = pkg

    spec = importlib.util.spec_from_file_location("app.app", app_py)
    if spec is None or spec.loader is None:
        raise ImportError(f"Could not create import spec for: {app_py}")
    mod = importlib.util.module_from_spec(spec)
    sys.modules["app.app"] = mod
    spec.loader.exec_module(mod)
    return mod

def build_wsgi_app():
    scavenger_mod = _load_scavenger_app(SCAVENGER_DIR)
    scavenger_flask = getattr(scavenger_mod, "app", None) or getattr(scavenger_mod, "application", None)
    if scavenger_flask is None:
        raise RuntimeError("Ember: scavenger module imported, but no Flask app found (expected .app)")
    return DispatcherMiddleware(ember, {SCAVENGER_MOUNT_PATH: scavenger_flask})

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


if __name__ == "__main__":
    main()
