from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Optional


@dataclass(frozen=True)
class Tool:
    id: str
    name: str
    kind: str  # "iframe" | "native"
    description: str = ""
    group: str = "Tools"
    iframe_src: str = ""   # for kind="iframe"
    hint: str = ""         # small helper text shown under iframe
    external: bool = False # reserved for future use


# Central registry: add a new tool here, and it appears in the UI automatically.
TOOLS: List[Tool] = [
    Tool(
        id="inventory",
        name="Scavenger Inventory",
        kind="iframe",
        description="Local parts inventory.",
        group="Tools",
        iframe_src="/scavenger",
        hint="Native Ember-themed inventory page.",
    ),
    Tool(
        id="admin",
        name="Admin Console",
        kind="iframe",
        description="Project-wide admin console (status/config/logs/cache/etc.).",
        group="Tools",
        iframe_src="/admin",
        hint="Admin Console running at /admin (embedded here for convenience).",
    ),
    # Future example:
    # Tool(id="keto", name="Keto Tracker", kind="native", description="AI food logging + macros + charts."),
]


def list_tools() -> List[Tool]:
    return list(TOOLS)


def get_tool(tool_id: str) -> Optional[Tool]:
    tool_id = (tool_id or "").strip()
    for t in TOOLS:
        if t.id == tool_id:
            return t
    return None


def tools_by_group() -> Dict[str, List[Tool]]:
    groups: Dict[str, List[Tool]] = {}
    for t in TOOLS:
        groups.setdefault(t.group or "Tools", []).append(t)
    return groups
