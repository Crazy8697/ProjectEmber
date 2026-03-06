"""
Submind Registry + EAI Orchestrator — Project Ember

Flow:
  User → EAI → Submind → EAI → User

EAI coordinates everything. The user never talks to a submind directly.
Subminds never talk to the user directly.

The orchestrate() function is the main entry point called by ember_app.py.
"""

from __future__ import annotations
import logging
from typing import Any, Callable, Dict, List, Optional

log = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Submind Registry
# ---------------------------------------------------------------------------
_REGISTRY: Dict[str, Dict] = {}


def register(submind_id: str, system_prompt: str, description: str = "") -> None:
    """Register a submind by id, system prompt, and optional description."""
    _REGISTRY[submind_id] = {
        "id": submind_id,
        "system_prompt": system_prompt,
        "description": description,
    }


def get_submind(submind_id: str) -> Optional[Dict]:
    """Return submind metadata dict or None if not found."""
    return _REGISTRY.get(submind_id)


def list_subminds() -> Dict[str, Dict]:
    """Return a copy of the full registry."""
    return dict(_REGISTRY)


# ---------------------------------------------------------------------------
# EAI Orchestrator
# ---------------------------------------------------------------------------

def orchestrate(
    query: str,
    history: List[Dict[str, str]],
    llm_fn: Callable[[str, str], str],  # fn(prompt, mode) -> str
    mode: str = "medium",
    memory_block: str = "",
    force_general: bool = False,
) -> Dict[str, Any]:
    """
    Full EAI orchestration pipeline.

    Steps:
        1. Route — detect which submind should handle the query
        2. Brief — EAI builds a task brief for the submind
        3. Submind — submind processes the task and returns a result
        4. Validate — EAI reviews the result and produces the final response

    Parameters
    ----------
    query       : raw user message
    history     : list of {role, content} dicts (recent chat)
    llm_fn      : callable(prompt, mode) -> str  (wraps _llama_completion)
    mode        : "quick" | "medium" | "deep"
    memory_block: persistent memory string injected into EAI prompts

    Returns
    -------
    dict with keys:
        response   : str  — final answer to show the user
        routing    : dict — routing decision
        submind_id : str  — which submind handled it
        steps      : list — internal step log (for admin/debug)
    """
    from submind_router import detect_submind
    from eai_core import (
        EAI_SYSTEM_PROMPT,
        EAI_BRIEF_TEMPLATE,
        EAI_VALIDATION_TEMPLATE,
    )

    steps = []

    # ------------------------------------------------------------------
    # Step 1: Route
    # ------------------------------------------------------------------
    if force_general:
        routing = {"submind": "general", "task": "", "confidence": 1.0, "reasoning": "force_general flag set", "method": "forced"}
        submind_id = "general"
        task = ""
        steps.append({"step": "route", "submind": "general", "method": "forced", "confidence": 1.0, "reasoning": "force_general bypass"})
        log.info("[EAI] Routed → general (forced bypass)")
    else:
        def _llm_only(prompt: str) -> str:
            return llm_fn(prompt, "quick")

        routing = detect_submind(query, llm_callable=_llm_only)
        submind_id = routing["submind"]
        task = routing["task"]
        steps.append({
            "step": "route",
            "submind": submind_id,
            "method": routing.get("method"),
            "confidence": routing.get("confidence"),
            "reasoning": routing.get("reasoning"),
        })
        log.info(f"[EAI] Routed → {submind_id} via {routing.get('method')} (conf={routing.get('confidence')})")

    # ------------------------------------------------------------------
    # Step 2: General path — EAI handles it directly (no submind needed)
    # ------------------------------------------------------------------
    if submind_id == "general":
        prompt = _build_eai_prompt(query, history, EAI_SYSTEM_PROMPT, memory_block)
        response = llm_fn(prompt, mode)
        steps.append({"step": "eai_direct", "note": "No submind needed, EAI answered directly."})
        return {
            "response": response,
            "routing": routing,
            "submind_id": "general",
            "steps": steps,
        }

    # ------------------------------------------------------------------
    # Step 3: Build task brief and wake the submind
    # ------------------------------------------------------------------
    submind = get_submind(submind_id)
    if not submind:
        # Submind registered in router but not in registry — fall back gracefully
        log.warning(f"[EAI] Submind '{submind_id}' not in registry, falling back to general.")
        prompt = _build_eai_prompt(query, history, EAI_SYSTEM_PROMPT, memory_block)
        response = llm_fn(prompt, mode)
        steps.append({"step": "fallback", "note": f"Submind {submind_id} not found in registry."})
        return {
            "response": response,
            "routing": routing,
            "submind_id": "general",
            "steps": steps,
        }

    brief = EAI_BRIEF_TEMPLATE.format(query=query, task=task)
    submind_prompt = _build_submind_prompt(brief, submind["system_prompt"])

    log.info(f"[EAI] Waking submind: {submind_id}")
    submind_result = llm_fn(submind_prompt, mode)
    steps.append({
        "step": "submind",
        "submind": submind_id,
        "result_preview": submind_result[:200] + ("…" if len(submind_result) > 200 else ""),
    })
    log.info(f"[EAI] Submind {submind_id} returned {len(submind_result)} chars. Dismissing.")

    # ------------------------------------------------------------------
    # Step 4: EAI validates and presents
    # ------------------------------------------------------------------
    validation_prompt = EAI_VALIDATION_TEMPLATE.format(
        submind_id=submind_id,
        query=query,
        submind_result=submind_result,
    )

    # Inject memory into validation prompt if present
    if memory_block:
        validation_prompt = memory_block + "\n\n" + validation_prompt

    final_response = llm_fn(validation_prompt, mode)
    steps.append({"step": "validate", "note": "EAI reviewed and presented submind result."})
    log.info(f"[EAI] Validation complete. Presenting to user.")

    return {
        "response": final_response,
        "routing": routing,
        "submind_id": submind_id,
        "steps": steps,
    }


# ---------------------------------------------------------------------------
# Prompt builders
# ---------------------------------------------------------------------------

def _build_eai_prompt(
    query: str,
    history: List[Dict[str, str]],
    system_prompt: str,
    memory_block: str = "",
) -> str:
    lines = [system_prompt.strip()]
    if memory_block:
        lines.append("")
        lines.append(memory_block)
    lines.append("")
    for m in history[-20:]:
        role = m.get("role", "")
        content = (m.get("content") or "").strip()
        if not content:
            continue
        lines.append(f"User: {content}" if role == "user" else f"EAI: {content}")
    lines.append(f"User: {query.strip()}")
    lines.append("EAI:")
    return "\n".join(lines)


def _build_submind_prompt(brief: str, system_prompt: str) -> str:
    lines = [
        system_prompt.strip(),
        "",
        brief.strip(),
        "",
        "Response:",
    ]
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Auto-register built-in subminds on import
# ---------------------------------------------------------------------------
def _bootstrap() -> None:
    try:
        from subminds.keto_submind import KETO_SYSTEM_PROMPT, SUBMIND_ID, DESCRIPTION
        register(SUBMIND_ID, KETO_SYSTEM_PROMPT, DESCRIPTION)
        log.debug(f"[Subminds] Registered: {SUBMIND_ID}")
    except (ImportError, AttributeError) as e:
        log.warning(f"[Subminds] Could not register keto_submind: {e}")


_bootstrap()
