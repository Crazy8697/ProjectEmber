"""
Submind registry for Project Ember.

Each submind is a specialised assistant that handles a specific domain.
EAI (Ember) routes queries to the appropriate submind and formats the
final response back to the user.
"""

from typing import Dict, Optional

# Registry: submind_id -> module path / metadata
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


# ------------------------------------------------------------------
# Auto-register built-in subminds on import
# ------------------------------------------------------------------
def _bootstrap() -> None:
    try:
        from subminds.keto_submind import KETO_SYSTEM_PROMPT, SUBMIND_ID, DESCRIPTION
        register(SUBMIND_ID, KETO_SYSTEM_PROMPT, DESCRIPTION)
    except (ImportError, AttributeError):
        pass


_bootstrap()
