from __future__ import annotations

import csv
import os
import shutil
import subprocess
from datetime import datetime
from pathlib import Path

from flask import Blueprint, flash, jsonify, redirect, render_template, request, url_for

from .db import (
    db_info as _db_info,
    delete_part_cache as _delete_part_cache,
    delete_search_cache as _delete_search_cache,
    get_part_cache as _get_part_cache,
    get_search_cache as _get_search_cache,
    list_part_cache as _list_part_cache,
    list_search_cache as _list_search_cache,
    upsert_part_cache as _upsert_part_cache,
)
from .enrich import enrich_batch as _enrich_batch

scavenger_bp = Blueprint(
    "scavenger",
    __name__,
    template_folder="templates",
    static_folder="static",
    static_url_path="/static",
)

# ---------------------------------------------------------------------------
# Configuration — driven by env vars so the blueprint is portable
# ---------------------------------------------------------------------------
_REPO_ROOT = Path(os.environ.get("SCAVENGER_DIR", "/home/daddy/scavenger-inventory")).resolve()
CSV_PATH = Path(os.environ.get("INVENTORY_CSV", str(_REPO_ROOT / "inventory.csv"))).resolve()
PUSH_SCRIPT = Path(os.environ.get("PUSH_SCRIPT", str(_REPO_ROOT / "push_inventory.sh"))).resolve()


def _is_safe_script(path: Path) -> bool:
    try:
        path = path.resolve()
        return str(path).startswith(str(_REPO_ROOT.resolve())) and path.is_file()
    except Exception:
        return False


# ---------------------------------------------------------------------------
# Inventory helpers
# ---------------------------------------------------------------------------

def load_inventory():
    if not CSV_PATH.exists():
        raise FileNotFoundError(f"inventory.csv not found at {CSV_PATH}")
    with CSV_PATH.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        rows = list(reader)
        headers = reader.fieldnames or []
    return headers, rows


def atomic_write_csv(headers, rows):
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = CSV_PATH.with_name(f"{CSV_PATH.stem}_backup_{ts}.csv")
    shutil.copy2(CSV_PATH, backup_path)

    tmp_path = CSV_PATH.with_suffix(".tmp")
    with tmp_path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=headers, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow({h: r.get(h, "") for h in headers})
    tmp_path.replace(CSV_PATH)
    return str(backup_path)


def find_by_mpn(rows, mpn):
    mpn = (mpn or "").strip().lower()
    for i, r in enumerate(rows):
        if (r.get("mpn") or "").strip().lower() == mpn:
            return i, r
    return None, None


def _to_int(s, default=0):
    try:
        s = (s or "").strip()
        if s == "":
            return default
        return int(float(s))
    except Exception:
        return default


def _truthy(v: str) -> bool:
    return (v or "").strip().lower() in ("1", "true", "yes", "y", "on")


def _collect_unique(rows, key: str):
    vals = set()
    for r in rows:
        v = (r.get(key) or "").strip()
        if v:
            vals.add(v)
    return sorted(vals, key=lambda s: s.lower())


def _row_matches_q(row: dict, q: str) -> bool:
    if not q:
        return True
    q = q.lower()
    return any(q in str(v).lower() for v in row.values() if v)


# ---------------------------------------------------------------------------
# Debug access guard
# ---------------------------------------------------------------------------

def _debug_allowed(req) -> bool:
    """If SCAVENGER_DEBUG_TOKEN set, require X-Debug-Token; else localhost only."""
    token = (os.getenv("SCAVENGER_DEBUG_TOKEN") or "").strip()
    if token:
        got = (req.headers.get("X-Debug-Token") or "").strip()
        return got == token
    ip = (req.remote_addr or "").strip()
    return ip in ("127.0.0.1", "::1")


def _debug_denied():
    return jsonify({"ok": False, "error": "forbidden"}), 403


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@scavenger_bp.get("/")
def index():
    headers, rows = load_inventory()
    q = (request.args.get("q") or "").strip()
    cat = (request.args.get("category") or "").strip()
    loc = (request.args.get("location_bin") or "").strip()

    categories = _collect_unique(rows, "category")
    locations = _collect_unique(rows, "location_bin")

    filtered = []
    for r in rows:
        if not _row_matches_q(r, q):
            continue
        if cat and (r.get("category") or "").strip() != cat:
            continue
        if loc and (r.get("location_bin") or "").strip() != loc:
            continue
        filtered.append(r)

    return render_template(
        "scavenger/index.html",
        headers=headers,
        rows=filtered,
        total=len(rows),
        shown=len(filtered),
        q=q,
        categories=categories,
        locations=locations,
        cat=cat,
        loc=loc,
    )


@scavenger_bp.get("/part/<mpn>")
def part_detail(mpn):
    headers, rows = load_inventory()
    _, row = find_by_mpn(rows, mpn)
    if not row:
        flash(f"mpn not found: {mpn}", "error")
        return redirect(url_for("scavenger.index"))
    return render_template("scavenger/edit.html", headers=headers, row=row)


@scavenger_bp.post("/part/<mpn>")
def part_update(mpn):
    headers, rows = load_inventory()
    idx, row = find_by_mpn(rows, mpn)
    if row is None:
        flash(f"mpn not found: {mpn}", "error")
        return redirect(url_for("scavenger.index"))

    updated = {h: request.form.get(h, "") for h in headers}
    updated["mpn"] = (row.get("mpn") or mpn).strip()
    rows[idx] = updated
    atomic_write_csv(headers, rows)
    flash("Saved.", "ok")
    return redirect(url_for("scavenger.part_detail", mpn=updated["mpn"]))


@scavenger_bp.get("/add")
def add_form():
    headers, _ = load_inventory()
    return render_template("scavenger/add.html", headers=headers)


@scavenger_bp.post("/add")
def add_submit():
    headers, rows = load_inventory()
    incoming = {h: request.form.get(h, "") for h in headers}
    mpn = (incoming.get("mpn") or "").strip()
    incoming["mpn"] = mpn

    if not mpn:
        flash("mpn is required.", "error")
        return redirect(url_for("scavenger.add_form"))

    idx, existing = find_by_mpn(rows, mpn)
    if existing is None:
        rows.append(incoming)
        atomic_write_csv(headers, rows)
        flash("Added.", "ok")
        return redirect(url_for("scavenger.index"))

    merged = dict(existing)
    old_qty = _to_int(existing.get("qty_on_hand"), 0)
    add_qty = _to_int(incoming.get("qty_on_hand"), 0)
    merged["qty_on_hand"] = str(old_qty + add_qty)

    for h in headers:
        if h in ("mpn", "qty_on_hand"):
            continue
        nv = (incoming.get(h) or "").strip()
        if not nv:
            continue
        if h == "notes":
            ov = (existing.get("notes") or "").strip()
            merged["notes"] = (ov + "\n" + nv) if ov and nv not in ov else nv
            continue
        if h == "needs_review" and _truthy(nv):
            merged["needs_review"] = "true"
            continue
        merged[h] = nv

    rows[idx] = merged
    atomic_write_csv(headers, rows)
    flash("Merged into existing part (qty incremented).", "ok")
    return redirect(url_for("scavenger.part_detail", mpn=merged["mpn"]))


@scavenger_bp.post("/delete/<mpn>")
def delete_item(mpn):
    headers, rows = load_inventory()
    idx, row = find_by_mpn(rows, mpn)
    if row is None:
        flash(f"mpn not found: {mpn}", "error")
        return redirect(url_for("scavenger.index"))
    rows.pop(idx)
    atomic_write_csv(headers, rows)
    flash(f"Deleted {mpn}.", "ok")
    return redirect(url_for("scavenger.index"))


@scavenger_bp.post("/push")
def push_to_github():
    """
    Run the repo push script, but first recover from a detached HEAD state.
    """
    if not _is_safe_script(PUSH_SCRIPT):
        return jsonify({"ok": False, "error": "Push script path is not safe."}), 400

    def _run_git(args, timeout=20):
        return subprocess.run(
            ["git"] + args,
            cwd=str(_REPO_ROOT),
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )

    try:
        head = _run_git(["rev-parse", "--abbrev-ref", "HEAD"])
        branch = (head.stdout or "").strip()

        if branch == "HEAD" or branch == "":
            origin = _run_git(["symbolic-ref", "--quiet", "refs/remotes/origin/HEAD"])
            ref = (origin.stdout or "").strip()
            candidate = ref.split("/")[-1] if ref else ""

            for b in [candidate, "main", "master"]:
                if not b:
                    continue
                chk = _run_git(["show-ref", "--verify", "--quiet", f"refs/heads/{b}"])
                if chk.returncode == 0:
                    sw = _run_git(["switch", b], timeout=30)
                    if sw.returncode != 0:
                        out = (sw.stdout or "") + (sw.stderr or "")
                        return jsonify({"ok": False, "error": "Failed to switch branch", "output": out}), 500
                    break

        proc = subprocess.run(
            [str(PUSH_SCRIPT)],
            cwd=str(_REPO_ROOT),
            capture_output=True,
            text=True,
            timeout=180,
            check=False,
        )
        out = (proc.stdout or "") + (proc.stderr or "")
        return jsonify({"ok": proc.returncode == 0, "code": proc.returncode, "output": out})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500


@scavenger_bp.get("/api/mpn/<mpn>")
def api_mpn(mpn):
    headers, rows = load_inventory()
    _, row = find_by_mpn(rows, mpn)
    if not row:
        return jsonify({"ok": False, "error": "not found"}), 404
    return jsonify({"ok": True, "row": row, "headers": headers})


@scavenger_bp.get("/api/health")
def api_health():
    return jsonify({"ok": True})


# --- Debug/Admin endpoints ---

@scavenger_bp.get("/api/debug/db")
def api_debug_db():
    if not _debug_allowed(request):
        return _debug_denied()
    return jsonify({"ok": True, "info": _db_info()})


@scavenger_bp.get("/api/debug/cache/parts")
def api_debug_cache_parts():
    if not _debug_allowed(request):
        return _debug_denied()
    try:
        lim = int(request.args.get("limit", "50"))
    except Exception:
        lim = 50
    return jsonify({"ok": True, "items": _list_part_cache(lim)})


@scavenger_bp.get("/api/debug/cache/part/<mpn>")
def api_debug_cache_part_get(mpn):
    if not _debug_allowed(request):
        return _debug_denied()
    hit = _get_part_cache(mpn)
    if hit is None:
        return jsonify({"ok": False, "error": "not found"}), 404
    return jsonify({"ok": True, "mpn": mpn, "payload": hit})


@scavenger_bp.post("/api/debug/cache/part/<mpn>/delete")
def api_debug_cache_part_delete(mpn):
    if not _debug_allowed(request):
        return _debug_denied()
    n = _delete_part_cache(mpn)
    return jsonify({"ok": True, "deleted": n, "mpn": mpn})


@scavenger_bp.get("/api/debug/cache/searches")
def api_debug_cache_searches():
    if not _debug_allowed(request):
        return _debug_denied()
    try:
        lim = int(request.args.get("limit", "50"))
    except Exception:
        lim = 50
    return jsonify({"ok": True, "items": _list_search_cache(lim)})


@scavenger_bp.get("/api/debug/cache/search")
def api_debug_cache_search_get():
    if not _debug_allowed(request):
        return _debug_denied()
    q = (request.args.get("q") or "").strip()
    if not q:
        return jsonify({"ok": False, "error": "missing q"}), 400
    results = _get_search_cache(q)
    if not results:
        return jsonify({"ok": False, "error": "not found", "q": q}), 404
    return jsonify({"ok": True, "q": q, "results": results})


@scavenger_bp.post("/api/debug/cache/search/delete")
def api_debug_cache_search_delete():
    if not _debug_allowed(request):
        return _debug_denied()
    q = (request.args.get("q") or "").strip()
    if not q:
        return jsonify({"ok": False, "error": "missing q"}), 400
    n = _delete_search_cache(q)
    return jsonify({"ok": True, "q": q, "deleted": n})


@scavenger_bp.post("/api/enrich/batch")
def api_enrich_batch():
    return _enrich_batch()


# --- Admin console ---

@scavenger_bp.get("/admin")
def admin_console():
    if not _debug_allowed(request):
        return redirect(url_for("scavenger.index"))

    token = (request.args.get("token") or "").strip()
    try:
        log_lines = int(request.args.get("lines", "100"))
    except Exception:
        log_lines = 100

    info = _db_info()
    db_path = info.get("db_path", "")
    db_size = info.get("db_size_bytes", 0) or 0
    counts = info.get("counts", {})
    part_cache_count = counts.get("part_enrich_cache", 0)
    search_cache_count = counts.get("web_search_cache", 0)

    health = {
        "openai_model": os.getenv("OPENAI_MODEL", "(not set)"),
        "brave_enabled": os.getenv("BRAVE_ENABLED", "1"),
        "brave_cache_enabled": os.getenv("BRAVE_CACHE_ENABLED", "1"),
    }

    log_path = ""
    log_tail = ""

    return render_template(
        "scavenger/admin.html",
        token=token,
        now=datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        db_path=db_path,
        db_size=db_size,
        part_cache_count=part_cache_count,
        search_cache_count=search_cache_count,
        health=health,
        log_path=log_path,
        log_lines=log_lines,
        log_tail=log_tail,
    )


@scavenger_bp.post("/admin/cache-bust")
def admin_cache_bust():
    if not _debug_allowed(request):
        return redirect(url_for("scavenger.index"))

    token = (request.form.get("token") or "").strip()
    raw = (request.form.get("mpns") or "").strip()
    mpns = [m.strip() for m in raw.replace(",", "\n").splitlines() if m.strip()]

    deleted = 0
    for mpn in mpns:
        deleted += _delete_part_cache(mpn)

    flash(f"Busted cache for {deleted} entry/entries.", "ok")
    return redirect(url_for("scavenger.admin_console", token=token))
