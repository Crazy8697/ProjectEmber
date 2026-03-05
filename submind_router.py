"""
Submind Router for Project Ember.

EAI (Ember) is a pure orchestrator. This module analyses an incoming
query and decides which submind should handle it, returning a routing
decision dict so the main query handler can load the right system prompt.

Routing decision schema
-----------------------
{
    "submind": str,       # submind id, e.g. "keto" or "general"
    "task":    str,       # short description of what needs doing
    "confidence": float,  # 0.0 – 1.0
    "needs_data": list    # optional data the submind may need from the user
}
"""

from typing import Any, Dict, List

# ---------------------------------------------------------------------------
# Keyword routing table
# Each entry: (submind_id, keyword_list, needs_data_hints)
# ---------------------------------------------------------------------------
_ROUTES: List[Dict[str, Any]] = []


def _load_routes() -> None:
    """Populate _ROUTES from registered subminds at import time."""
    global _ROUTES
    _ROUTES = []
    try:
        from subminds.keto_submind import KETO_KEYWORDS, SUBMIND_ID
        _ROUTES.append({
            "submind": SUBMIND_ID,
            "keywords": KETO_KEYWORDS,
            "needs_data": ["total_carbs", "fiber", "fat_grams", "protein_grams"],
            "task_default": "assist with keto tracking / macro calculation",
        })
    except (ImportError, AttributeError):
        pass


_load_routes()


def _make_routing(submind: str, task: str, confidence: float = 1.0, needs_data: List[str] = None) -> Dict[str, Any]:
    """Return a consistently structured routing decision dict."""
    return {
        "submind": submind,
        "task": task,
        "confidence": confidence,
        "needs_data": needs_data if needs_data is not None else [],
    }


def detect_submind(query: str) -> Dict[str, Any]:
    """
    Analyse *query* and return a routing decision dict.

    Returns
    -------
    dict with keys: submind, task, confidence, needs_data
    """
    if not query:
        return _general_response()

    q_lower = query.lower()
    best_confidence = 0.0
    best: Dict[str, Any] = {}

    for route in _ROUTES:
        keywords: List[str] = route["keywords"]
        hits = sum(1 for kw in keywords if kw in q_lower)
        if hits == 0:
            continue

        # Confidence: fraction of keyword matches scaled so that a single
        # strong match on a ~15-keyword list scores ~0.3 and a query that
        # hits 15 % or more of the keyword list scores 1.0.  The 0.15
        # multiplier was chosen empirically so a single-keyword hit on a
        # typical 20-30 word keyword list gives a clear positive signal
        # without over-triggering on coincidental word overlap.
        confidence = min(1.0, hits / max(len(keywords) * 0.15, 1))
        if confidence > best_confidence:
            best_confidence = confidence
            best = {
                "submind": route["submind"],
                "task": route["task_default"],
                "confidence": round(confidence, 3),
                "needs_data": route["needs_data"],
            }

    if best:
        return best

    return _general_response()


def _general_response() -> Dict[str, Any]:
    return _make_routing("general", "general query")
