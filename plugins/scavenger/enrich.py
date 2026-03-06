from __future__ import annotations

import json
import os
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple

from flask import jsonify, request

from .db import get_part_cache, upsert_part_cache, get_search_cache, put_search_cache
from .brave_search import web_search, pick_best_datasheet_url, BraveSearchError

# Authoritative Frozen V1 columns (must match inventory.csv exactly)
FROZEN_V1_COLUMNS: List[str] = [
    "mpn",
    "manufacturer",
    "description",
    "category",
    "package",
    "polarity_or_channel",
    "voltage_rating_v",
    "rating_context",
    "current_rating_a",
    "power_rating_w",
    "qty_on_hand",
    "location_bin",
    "avg_cost",
    "datasheet_url",
    "notes",
    "confidence",
    "needs_review",
]


@dataclass
class EnrichResult:
    row: Dict[str, Any]
    warnings: List[str]
    meta: Dict[str, Any]


def _env(name: str, default: Optional[str] = None) -> str:
    val = os.getenv(name, default)
    if val is None or val == "":
        raise RuntimeError(f"Missing required env var: {name}")
    return val


def _coerce_schema(row: Dict[str, Any]) -> EnrichResult:
    """
    Ensure schema-shaped output, with safe defaults.
    We DO NOT write anything to CSV here—this only shapes return data.
    """
    warnings: List[str] = []
    meta: Dict[str, Any] = {}

    out: Dict[str, Any] = {k: "" for k in FROZEN_V1_COLUMNS}

    # mpn
    mpn = str(row.get("mpn", "")).strip()
    if not mpn:
        warnings.append("Missing mpn in enriched row.")
    out["mpn"] = mpn

    # Strings
    for k in [
        "manufacturer",
        "description",
        "category",
        "package",
        "polarity_or_channel",
        "rating_context",
        "location_bin",
        "datasheet_url",
        "notes",
    ]:
        v = row.get(k, "")
        out[k] = "" if v is None else str(v).strip()

    # Numeric-ish fields (store as strings in CSV, but validate lightly)
    for k in ["voltage_rating_v", "current_rating_a", "power_rating_w", "avg_cost"]:
        v = row.get(k, "")
        if v is None or v == "":
            out[k] = ""
        else:
            try:
                out[k] = str(float(v)).rstrip("0").rstrip(".") if str(v).strip() != "" else ""
            except Exception:
                warnings.append(f"Non-numeric value for {k}: {v!r}")
                out[k] = str(v).strip()

    # qty_on_hand should be integer string
    q = row.get("qty_on_hand", "")
    if q is None or q == "":
        out["qty_on_hand"] = ""
    else:
        try:
            out["qty_on_hand"] = str(int(float(q)))
        except Exception:
            warnings.append(f"Non-integer value for qty_on_hand: {q!r}")
            out["qty_on_hand"] = str(q).strip()

    # confidence float 0..1
    conf = row.get("confidence", "")
    conf_f: float = 0.0
    if conf is None or conf == "":
        warnings.append("Missing confidence; defaulting to 0.0")
        conf_f = 0.0
    else:
        try:
            conf_f = float(conf)
        except Exception:
            warnings.append(f"Non-float value for confidence: {conf!r}; defaulting to 0.0")
            conf_f = 0.0

    conf_f = max(0.0, min(1.0, conf_f))
    out["confidence"] = f"{conf_f:.2f}".rstrip("0").rstrip(".") if conf_f != 0 else "0"

    # needs_review bool + enforce rule
    needs = row.get("needs_review", False)
    if isinstance(needs, str):
        needs_b = needs.strip().lower() in {"1", "true", "yes", "y", "on"}
    else:
        needs_b = bool(needs)

    if conf_f < 0.90:
        needs_b = True
    out["needs_review"] = "true" if needs_b else "false"

    return EnrichResult(row=out, warnings=warnings, meta=meta)


def _normalize_mpn(s: str) -> str:
    return (s or "").strip()


def _as_float(s: Any, default: float = 0.0) -> float:
    try:
        return float(s)
    except Exception:
        return default


def _missing_signal(row: Dict[str, Any]) -> Tuple[int, List[str]]:
    """
    Heuristic: which key identity fields are missing?
    Used to decide whether to perform web-grounded extraction.
    """
    keys = [
        "manufacturer",
        "description",
        "category",
        "polarity_or_channel",
        "voltage_rating_v",
        "current_rating_a",
        "package",
    ]
    missing = [k for k in keys if (row.get(k) or "").strip() == ""]
    return len(missing), missing


def _evidence_block(query: str, results: List[dict]) -> str:
    """Compile a compact evidence block from Brave search results."""
    lines = [f"WEB_QUERY: {query}"]
    for i, r in enumerate(results[:5], start=1):
        title = (r.get("title") or "").strip()
        url = (r.get("url") or "").strip()
        snip = (r.get("snippet") or "").strip()
        snip = snip.replace("\n", " ").strip()
        if len(snip) > 400:
            snip = snip[:397] + "..."
        lines.append(f"[{i}] TITLE: {title}")
        lines.append(f"[{i}] URL: {url}")
        lines.append(f"[{i}] SNIPPET: {snip}")
    return "\n".join(lines).strip()


def _preferred_datasheet_url(results: List[dict]) -> str:
    """
    Prefer stable aggregators/manufacturer pages over random-host PDFs.
    Still falls back to existing pick_best_datasheet_url() if needed.
    """
    if not results:
        return ""

    allow_domains = (
        "alldatasheet.",
        "alltransistors.",
        "datasheetarchive.",
        "datasheetcafe.",
        "digikey.",
        "mouser.",
        "octopart.",
        "sanken",
        "onsemi",
        "infineon",
        "stmicro",
        "texas",
    )

    def score(url: str, title: str) -> int:
        u = (url or "").lower()
        t = (title or "").lower()
        s = 0
        if u.startswith("https://"):
            s += 5
        elif u.startswith("http://"):
            s += 1
        if u.endswith(".pdf"):
            s += 1
        if "index of" in t or "/pdf/" in u or "apache" in t:
            s -= 4
        for dom in allow_domains:
            if dom in u or dom in t:
                s += 6
        return s

    best = ""
    best_s = -10**9
    for r in results[:8]:
        url = (r.get("url") or "").strip()
        if not url:
            continue
        s = score(url, r.get("title") or "")
        if s > best_s:
            best_s = s
            best = url

    if best:
        return best

    try:
        return pick_best_datasheet_url(results) or ""
    except Exception:
        return ""


def _openai_extract_from_evidence(*, client: Any, model: str, mpn: str, hints: str, evidence: str) -> Dict[str, Any]:
    """
    Second-pass extraction: use search snippets as grounding evidence.
    Returns a Frozen V1 row dict (may still contain blanks).
    """
    system = (
        "You are an electronics parts enrichment assistant. "
        "You MUST ground your answers in the provided evidence. "
        "Return ONLY JSON. No markdown. No commentary."
    )

    user = {
        "task": (
            "Extract a single Frozen V1 row for the given mpn, using ONLY the EVIDENCE block. "
            "If a field is not supported by evidence, leave it as an empty string. "
            "Do not invent manufacturer, ratings, or package. "
            "Use numeric strings for voltage_rating_v/current_rating_a/power_rating_w when present in evidence. "
            "category should be broad but correct (e.g., 'BJT', 'MOSFET', 'Diode', 'Relay', 'IC', 'Connector'). "
            "polarity_or_channel examples: 'NPN', 'PNP', 'N-ch', 'P-ch', 'Schottky', 'Zener'. "
            "confidence is 0..1 and should reflect how explicit the evidence is."
        ),
        "frozen_v1_columns": FROZEN_V1_COLUMNS,
        "mpn": mpn,
        "hints": hints,
        "evidence": evidence,
        "output_format": {k: "string" for k in FROZEN_V1_COLUMNS},
    }

    resp = client.responses.create(
        model=model,
        input=[
            {"role": "system", "content": system},
            {"role": "user", "content": json.dumps(user)},
        ],
        text={"format": {"type": "json_object"}},
    )

    data = resp.output_text
    parsed = json.loads(data)
    if not isinstance(parsed, dict):
        return {}
    return parsed


def _maybe_brave_fallback(mpn: str, shaped: EnrichResult, *, client: Any = None, model: str = "gpt-5-mini", hints: str = "") -> None:
    """
    If confidence is low OR critical identity fields are missing OR datasheet_url is empty,
    use Brave web search to gather evidence.

    Important: We don't just "find a datasheet link" — we use snippets as grounding evidence
    to fill manufacturer/category/ratings when possible.
    """
    conf = _as_float(shaped.row.get("confidence"), 0.0)
    needs_datasheet = (shaped.row.get("datasheet_url") or "").strip() == ""
    missing_n, _missing_keys = _missing_signal(shaped.row)

    low_conf = conf < 0.90
    needs_web = low_conf or needs_datasheet or (missing_n >= 3)

    if not needs_web:
        return

    if (os.getenv("BRAVE_ENABLED", "1") or "1").strip().lower() in ("0", "false", "no", "off"):
        shaped.warnings.append("brave_disabled")
        return

    query = f"{mpn} datasheet"
    shaped.meta.setdefault("web_query", query)

    cached = get_search_cache(query)
    results: List[dict] = []
    if cached:
        results = cached
        shaped.meta["search_results"] = cached
        shaped.warnings.append("search_cache_hit")
    else:
        try:
            results = web_search(query, count=5)
        except BraveSearchError as e:
            shaped.warnings.append("brave_error")
            shaped.meta["brave_error"] = str(e)
            return

        shaped.meta["search_results"] = results

        if (os.getenv("BRAVE_CACHE_ENABLED", "1") or "1").strip().lower() in ("1", "true", "yes", "on"):
            put_search_cache(query, results)

    best = _preferred_datasheet_url(results)
    if needs_datasheet and best:
        shaped.row["datasheet_url"] = best
        shaped.warnings.append("datasheet_from_search")
    elif results:
        shaped.meta["download_url"] = results[0].get("url")
        shaped.warnings.append("download_manually")

    if not results:
        return
    if client is None:
        shaped.warnings.append("no_openai_client_for_grounding")
        return

    evidence = _evidence_block(query, results)
    shaped.meta["evidence"] = evidence

    after_missing_n, _ = _missing_signal(shaped.row)
    if not (low_conf or after_missing_n >= 2):
        return

    try:
        extracted = _openai_extract_from_evidence(
            client=client,
            model=model,
            mpn=mpn,
            hints=hints,
            evidence=evidence,
        )
    except Exception as e:
        shaped.warnings.append("grounding_model_error")
        shaped.meta["grounding_error"] = str(e)
        return

    if not isinstance(extracted, dict):
        shaped.warnings.append("grounding_model_bad_return")
        return

    shaped2 = _coerce_schema(extracted)

    for k in FROZEN_V1_COLUMNS:
        v_new = (shaped2.row.get(k) or "").strip()
        if v_new:
            shaped.row[k] = v_new

    shaped.warnings.append("grounded_from_search_snippets")

    if (shaped.row.get("notes") or "").strip() == "":
        shaped.row["notes"] = "Grounded from web search snippets; verify against primary datasheet."

    # normalize confidence/needs_review formatting
    normalized = _coerce_schema(shaped.row)
    shaped.row["confidence"] = normalized.row.get("confidence", shaped.row.get("confidence", "0"))
    shaped.row["needs_review"] = normalized.row.get("needs_review", shaped.row.get("needs_review", "true"))


def enrich_batch():
    """
    Cache-first enrichment:
    - Try SQLite part cache first.
    - Only cache-misses hit OpenAI.
    - Store successful enrichments back into SQLite.

    Web-search fallback:
    - For low-confidence OR missing datasheet_url OR missing key identity fields:
      - attaches meta.search_results + meta.download_url
      - may fill datasheet_url if a good candidate is found
      - uses search snippets as EVIDENCE for a grounded extraction pass
      - if not, sets warning 'download_manually' for UI button.
    """
    payload = request.get_json(silent=True) or {}
    items = payload.get("items", [])
    hints = payload.get("hints", "")

    if not isinstance(items, list) or not all(isinstance(x, str) for x in items):
        return jsonify({"error": "items must be a list of strings"}), 400

    client = None
    model = os.getenv("OPENAI_MODEL", "gpt-5-mini")
    try:
        from openai import OpenAI

        api_key = _env("OPENAI_API_KEY")
        client = OpenAI(api_key=api_key)
    except Exception:
        client = None

    cached_by_item: Dict[str, Optional[dict]] = {}
    misses: List[str] = []
    for raw in items:
        key = _normalize_mpn(raw)
        if not key:
            cached_by_item[raw] = None
            misses.append(raw)
            continue

        hit = get_part_cache(key)
        if hit is not None:
            cached_by_item[raw] = hit
        else:
            cached_by_item[raw] = None
            misses.append(raw)

    shaped_by_input: Dict[str, EnrichResult] = {}

    for raw, hit in cached_by_item.items():
        if hit is None:
            continue
        shaped = _coerce_schema(hit)
        shaped.warnings.append("cache_hit")
        _maybe_brave_fallback(_normalize_mpn(raw), shaped, client=client, model=model, hints=hints)
        shaped_by_input[raw] = shaped

    if misses:
        if client is None:
            for raw in misses:
                shaped = _coerce_schema({"mpn": _normalize_mpn(raw), "confidence": 0.0, "needs_review": True})
                shaped.warnings.append("openai_unavailable")
                _maybe_brave_fallback(_normalize_mpn(raw), shaped, client=None, model=model, hints=hints)
                shaped_by_input[raw] = shaped
        else:
            system = (
                "You are an electronics parts enrichment assistant. "
                "Return ONLY JSON. No markdown. No commentary."
            )

            user = {
                "task": (
                    "For each input item, produce ONE Frozen V1 row matching the exact columns list. "
                    "Use empty strings when unknown. Keep mpn as best-guess normalized. "
                    "confidence is 0..1. If confidence < 0.90, needs_review MUST be true. "
                    "rating_context must clarify numeric ratings (e.g., 'Vds max', 'Vz nominal'). "
                    "Do not hallucinate datasheet_url; leave blank unless you are confident it is correct."
                ),
                "frozen_v1_columns": FROZEN_V1_COLUMNS,
                "items": misses,
                "hints": hints,
                "output_format": {"rows": [{k: "string" for k in FROZEN_V1_COLUMNS}]},
            }

            resp = client.responses.create(
                model=model,
                input=[
                    {"role": "system", "content": system},
                    {"role": "user", "content": json.dumps(user)},
                ],
                text={"format": {"type": "json_object"}},
            )

            data = resp.output_text
            try:
                parsed = json.loads(data)
            except Exception as e:
                return jsonify({"error": "model_return_not_json", "detail": str(e), "raw": data}), 502

            rows = parsed.get("rows", [])
            if not isinstance(rows, list):
                return jsonify({"error": "model_return_missing_rows", "raw": parsed}), 502

            for raw, r in zip(misses, rows):
                if not isinstance(r, dict):
                    continue
                shaped = _coerce_schema(r)
                _maybe_brave_fallback(_normalize_mpn(raw), shaped, client=client, model=model, hints=hints)
                shaped_by_input[raw] = shaped

                cache_key = _normalize_mpn(raw)
                if cache_key:
                    conf = None
                    try:
                        conf = float(shaped.row.get("confidence") or 0.0)
                    except Exception:
                        conf = None
                    upsert_part_cache(cache_key, shaped.row, confidence=conf, source="openai+brave+grounded")

    results = []
    for raw in items:
        shaped = shaped_by_input.get(raw)
        if shaped is None:
            shaped = _coerce_schema({"mpn": _normalize_mpn(raw), "confidence": 0.0, "needs_review": True})
            shaped.warnings.append("no_result")
        results.append({"row": shaped.row, "warnings": shaped.warnings, "meta": shaped.meta})

    return jsonify({"model": model if misses else "cache", "results": results})
