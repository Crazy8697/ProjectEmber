"""
Submind Router — Project Ember

Hybrid routing: fast keyword scan first, LLM fallback if confidence is low.

Routing decision schema
-----------------------
{
    "submind":    str,    # submind id, e.g. "keto" or "general"
    "task":       str,    # one-sentence description of what the submind should do
    "confidence": float,  # 0.0 – 1.0
    "reasoning":  str,    # why this submind was chosen
    "method":     str,    # "keyword" | "llm" | "general"
}
"""

from __future__ import annotations
import json
import logging
from typing import Any, Dict, List, Optional

log = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Keyword route table — populated at import from registered subminds
# ---------------------------------------------------------------------------
_ROUTES: List[Dict[str, Any]] = []

# Confidence threshold below which we fall back to LLM routing
_KEYWORD_CONFIDENCE_THRESHOLD = 0.25


def _load_routes() -> None:
    global _ROUTES
    _ROUTES = []
    try:
        from subminds.keto_submind import KETO_KEYWORDS, SUBMIND_ID, DESCRIPTION
        _ROUTES.append({
            "submind": SUBMIND_ID,
            "keywords": KETO_KEYWORDS,
            "description": DESCRIPTION,
            "task_default": "assist with keto tracking, macro calculation, or meal planning",
        })
    except (ImportError, AttributeError):
        pass
    # Add more subminds here as they are built


_load_routes()


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def detect_submind(query: str, llm_callable=None) -> Dict[str, Any]:
    """
    Analyse *query* and return a routing decision dict.

    Parameters
    ----------
    query : str
        The raw user message.
    llm_callable : callable, optional
        A function (prompt: str) -> str that calls the local LLM.
        Required for the LLM fallback path. If None, falls back to general.

    Returns
    -------
    dict with keys: submind, task, confidence, reasoning, method
    """
    if not query or not query.strip():
        return _general("empty query")

    # --- Stage 1: keyword scan ---
    result = _keyword_scan(query)
    if result and result["confidence"] >= _KEYWORD_CONFIDENCE_THRESHOLD:
        log.debug(f"[Router] Keyword match → {result['submind']} (conf={result['confidence']})")
        return result

    # --- Stage 2: LLM fallback ---
    if llm_callable is not None:
        try:
            llm_result = _llm_route(query, llm_callable)
            if llm_result:
                log.debug(f"[Router] LLM match → {llm_result['submind']} (conf={llm_result['confidence']})")
                return llm_result
        except Exception as e:
            log.warning(f"[Router] LLM fallback failed: {e}")

    return _general("no confident match found")


def _keyword_scan(query: str) -> Optional[Dict[str, Any]]:
    """Fast keyword scan. Returns best match or None."""
    q_lower = query.lower()
    best_confidence = 0.0
    best: Optional[Dict[str, Any]] = None

    for route in _ROUTES:
        keywords: List[str] = route["keywords"]
        hits = sum(1 for kw in keywords if kw in q_lower)
        if hits == 0:
            continue

        # Scale: single hit on a ~30 keyword list scores ~0.22,
        # hitting 15%+ of the list scores 1.0.
        confidence = min(1.0, hits / max(len(keywords) * 0.15, 1))
        if confidence > best_confidence:
            best_confidence = confidence
            best = {
                "submind": route["submind"],
                "task": route["task_default"],
                "confidence": round(confidence, 3),
                "reasoning": f"Matched {hits} keyword(s) from {route['submind']} keyword list.",
                "method": "keyword",
            }

    return best


def _llm_route(query: str, llm_callable) -> Optional[Dict[str, Any]]:
    """LLM fallback routing. Asks the LLM to choose a submind."""
    from eai_core import EAI_ROUTING_PROMPT_TEMPLATE

    # Build submind list for the prompt
    submind_lines = []
    for route in _ROUTES:
        submind_lines.append(f"  - {route['submind']}: {route['description']}")
    submind_lines.append("  - general: catch-all for anything else")
    submind_list = "\n".join(submind_lines)

    prompt = EAI_ROUTING_PROMPT_TEMPLATE.format(
        submind_list=submind_list,
        query=query,
    )

    raw = llm_callable(prompt)
    if not raw:
        return None

    # Parse JSON response — strip any accidental markdown fences
    clean = raw.strip()
    if clean.startswith("```"):
        clean = clean.split("```")[1]
        if clean.startswith("json"):
            clean = clean[4:]
    clean = clean.strip()

    try:
        data = json.loads(clean)
    except json.JSONDecodeError:
        log.warning(f"[Router] LLM returned non-JSON: {raw[:200]}")
        return None

    submind_id = (data.get("submind") or "general").strip()
    # Validate it's a known submind
    known = {r["submind"] for r in _ROUTES} | {"general"}
    if submind_id not in known:
        submind_id = "general"

    return {
        "submind": submind_id,
        "task": (data.get("task") or "handle this query").strip(),
        "confidence": float(data.get("confidence") or 0.5),
        "reasoning": (data.get("reasoning") or "LLM routing decision").strip(),
        "method": "llm",
    }


def _general(reason: str) -> Dict[str, Any]:
    return {
        "submind": "general",
        "task": "handle this as a general query",
        "confidence": 1.0,
        "reasoning": reason,
        "method": "general",
    }


def _make_routing(submind: str, task: str, confidence: float = 1.0,
                  needs_data: List[str] = None, reasoning: str = "") -> Dict[str, Any]:
    """Compatibility shim — ember_app.py calls this for explicit overrides."""
    return {
        "submind": submind,
        "task": task,
        "confidence": confidence,
        "reasoning": reasoning,
        "method": "override",
        "needs_data": needs_data or [],
    }
