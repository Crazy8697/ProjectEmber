# Project Ember – Worklist

## Current Status
- Mistral 7B Q4_K_M running via llama.cpp at 3072 token context (GPU-accelerated)
- Flask UI running at 0.0.0.0:5000
- Persistent memory system (key/value, eira-owned)
- Submind routing (keto submind active)
- Scavenger Inventory tool mounted inside Ember UI
- Token context bar in UI (CTX / IN / OUT / LIMIT / %)
- Query error handling (real errors, no silent failures)
- Start/Stop desktop launchers with KDE notifications
- Nutrition DB seeded
- GitHub repository connected, README added, master branch current

---

# Priority Worklist

## 1. Auto-Summary / Rolling Memory
**Next up.**

Goal: prevent context overflow gracefully

Plan:
- 60% context → display warning in token bar
- 80% context → trigger rolling summary
- Summary preserves: active goal, project state, decisions, constraints, file paths, unresolved issues, next steps
- Show progress UI during summarization (similar to Claude compaction screen)
- List what was saved to memory after summary completes
- Rule: summarize aggressively, memorize selectively

---

## 2. Auto-Scroll
**Next up (parallel with auto-summary).**

Chat view should scroll to latest message automatically after each response.

---

## 3. Inventory System Improvements
- Fix remaining layout quirks in embedded inventory UI
- Ensure Add / Inventory / Admin navigation works reliably in iframe
- Add part auto-capitalization for part numbers
- Improve part lookup / enrichment pipeline
- Validate multi-add enrichment flow
- Add category normalization
- Add location/bin management UI
- Add bulk edit capability
- Add CSV export

---

## 4. Admin Console
Goal: central control panel for the entire Ember system

Planned features:
- Cache refresh button
- Inventory reindex
- Model restart
- Model switch
- System health display
- Git push panel
- Log viewer
- Plugin management
- Database / CSV maintenance tools

---

## 5. AI Architecture
Current:
- Main brain: Mistral 7B Instruct v0.3 Q4_K_M (llama.cpp)
- Submind routing via submind_router.py
- Active subminds: keto

Planned subminds:
- Electronics / component lookup
- Code generation
- Circuit design assistance

Future additions:
- Tool-calling layer
- Local knowledge store
- Component datasheet retrieval

---

## 6. Inventory + AI Integration

Planned capabilities:
- Ask Ember about inventory parts
- Auto categorize parts
- Datasheet enrichment
- Suggest circuit uses
- Detect duplicate parts
- Auto generate descriptions
- AI assisted part entry

Example — user enters "S9014", system returns:
- Manufacturer options
- Package
- Ratings
- Category
- Notes
- Suggested uses

---

## 7. UI Improvements

Planned:
- Tool plugin system
- Consistent top bar across tools
- Better iframe host system
- Modal system cleanup
- Dark theme refinement
- Responsive layout improvements

---

## 8. Plugin System

Goal: allow tools to mount inside Ember easily

Plugin format:
- name, id, description, type (iframe/native), route

Current plugins: Inventory, Admin Console

Future plugins:
- Circuit Builder
- Datasheet Browser
- Component Comparator
- Code Lab

---

## 9. GitHub Integration

Current:
- Manual git push via terminal
- README in place

Planned:
- Commit message UI in admin panel
- Branch switching
- Commit history viewer
- Diff viewer
- Repo health checks

---

## 10. Future Tools

- Circuit Builder — interactive electronics design
- Datasheet Browser — search local and remote datasheets
- Component Comparator — compare parts side by side
- Knowledge Base — technical notes + personal documentation

---

# Known Fixes / TODO

- Fix part number capitalization
- Fix GitHub push failure in detached HEAD situations
- Improve iframe navigation reliability
- Improve enrichment UI feedback
- Reduce layout nesting in Ember template
- Improve error handling in tool host

---

# Long Term Goals

- Fully local AI engineering assistant
- Hardware design support
- Code generation
- Personal technical knowledge system
- Integrated electronics lab database

---

# Repository Maintenance Tasks

Recommended periodic actions:
- Prune branches
- Clean unused models from ai_models/
- Backup inventory CSV
- Export knowledge base
- Tag stable versions

---

# Notes

Project Ember is intended to function as a **local engineering brain**, combining:
- AI reasoning
- Electronics inventory
- Technical knowledge
- Tool plugins

The system is designed to remain:
- Local-first
- Modular
- Extensible

---

## 11. Keto Submind Guardrails

The following rules must be permanently enforced in the keto submind — no exceptions:

1. Never invent macros — use only user-provided nutrition or standard reference data
2. Prefer inventory-aware meals over generic recipes
3. Default to shopping lists / meal builds, not essay mode
4. Always show net carbs, not just total carbs
5. Never freestyle recipes without a verified macro source

These are welded on. Not configurable.
