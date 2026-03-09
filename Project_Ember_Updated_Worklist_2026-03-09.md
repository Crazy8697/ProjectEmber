# Project Ember Updated Worklist

_Date: 2026-03-09_

## Current source-of-truth paths

- **Live:** `/home/eira/ProjectEmber`
- **Dev/backup:** `/home/daddy/ProjectEmber_dev`
- Rule: anything in the live path is authoritative unless explicitly working in dev.

---

## Highest priority

### 1. Patch live Eira core
- Replace live `/home/eira/ProjectEmber/eai_core.py` with the self-edit-ready version.
- Keep Eira's real-name memory intact.
- Stop repetitive real-name usage.
- Stop canned assistant filler.
- Keep tone sharp, natural, sarcastic, and conversational.

### 2. Add autonomous personality system
- Add `personality_profile.json`
- Add `self_edit_log.md`
- Add `EIRA_SELF_EDIT_USAGE.md`
- Make sure Eira understands:
  - core identity is stable
  - adaptive style can self-modify
  - self-edits should not require user approval for minor/moderate style changes
  - every self-edit must be persisted and logged

### 3. Wire actual self-edit plumbing
- Load `personality_profile.json` into prompt construction.
- Let Eira update adaptive style values on her own.
- Append real entries to `self_edit_log.md`.
- Keep stable identity changes separate from adaptive style changes.
- Make self-edits survive restarts.

---

## Food / keto project

### 4. Build the food/keto system properly
- Natural food logging through conversation.
- Macro tracking.
- Meal totals.
- Day totals.
- Recipe recall.
- Reusable meal templates.

### 5. Move saved food rules into working logic
- Use exact ingredient weights when provided.
- Use midpoint-then-round-up for ranged nutrition values.
- Only estimate edible portion when bone/ineligible mass is involved.
- Lock established values so they do not drift later.

### 6. End-of-day scoring system
- Support trigger phrase: `score my day`
- Log fields:
  - Day Score
  - Calories
  - Net Carbs
  - Protein
  - Water
  - Notes
- Store entries in a form that can later be graphed/trended.

### 7. Recipe/memory layer for food project
- Save structured recipes already built.
- Keep named recipes reusable.
- Support lookup by name.
- Support alternate versions when needed.

### 8. Food knowledge base cleanup
Organize saved food data into structure instead of raw memory sprawl:
- recipes
- sauces
- drinks
- template meals
- daily logs

### 9. Template day to structure
- Coffee with half-and-half
- Big Boy Salad
- Tangy Beef Bowl

### 10. Named recipes/drinks to structure
- Pothole Coffee
- Cheesy Beef & Mushroom Skillet
- Olive Brine Aioli
- Egg, Broccoli & Cheddar Skillet
- Swamp Sauce
- Backwoods Greens Skillet

### 11. Electrolyte support
- Keep Tajín concentrate recipe in structured form.
- Make it easy to calculate dose, jars, sodium, and potassium.

### 12. Persistent nutrition storage
- Move daily food logs into actual file/DB storage.
- Make logs survive restart cleanly.
- Keep date-based retrieval simple.

### 13. Recipe and log retrieval commands
Examples:
- `show today`
- `show my macros`
- `show saved recipes`
- `use template day`
- `score my day`
- `show week trend`

### 14. Trend/report layer
- Daily nutrition score history
- Calories trend
- Net carbs trend
- Protein trend
- Water trend
- Optional later: weight correlation

### 15. Make the food system sound like Eira
- No diet-brochure tone.
- Keep same personality shell.
- Domain-specific competence underneath.

### 16. Let Eira improve food interaction style over time
- Improve meal confirmation flow.
- Improve missing-data questions.
- Improve day summary style.
- Log those style changes through the self-edit system.

---

## Project architecture direction

### 17. Formalize Ember as a personal operating system for life logistics
Project Ember is not just a chatbot.

Eira is the interface and reasoning layer sitting on top of independent systems.

### 18. Architectural rule
Build in this order:

```text
systems -> AI interface
```

Not this:

```text
AI -> everything
```

### 19. Domain systems should work independently
Each major system should function even without Eira:
- Food system
- Inventory system
- Home Assistant integration
- Calendar/reminders
- Admin/project tools

### 20. Cross-system reasoning goal
Teach Eira to reason across systems instead of only within one silo.
Examples:
- health + food
- inventory + food
- schedule + meal planning
- weather + fishing/outdoor planning
- household state + routines

### 21. Eira Portal as the central control surface
Long-term tablet/dashboard target:
- Home
- Health
- Food
- Inventory
- Projects
- Automation

### 22. Core domain modules to build toward
- Food / nutrition
- Inventory
- Health metrics
- Home automation
- Calendar / reminders
- Project/admin

### 23. Eira's role in the architecture
Eira should be:
- the voice layer
- the reasoning layer
- the natural-language interface across systems

Eira should **not** be:
- the only control path
- the only place data lives
- the only way systems operate

---

## Admin / platform follow-up

### 24. Continue Admin Console expansion
- System metrics panel
- Cache visibility tools
- Audit logging
- Safer destructive actions
- Backup/export options
- Future model/runtime management

### 25. Clean up persistent path assumptions
- Live path is `/home/eira/ProjectEmber`
- Dev/backup path is `/home/daddy/ProjectEmber_dev`
- Remove ambiguity in future instructions and file edits

---

## Suggested build order

1. Patch live `eai_core.py`
2. Add `personality_profile.json`, `self_edit_log.md`, `EIRA_SELF_EDIT_USAGE.md`
3. Wire self-edit/profile loading and persistence
4. Build persistent food log storage
5. Build structured recipe storage and retrieval
6. Implement `score my day`
7. Add food trends/reports
8. Formalize the life-OS architecture in code and structure
9. Expand cross-system reasoning
10. Grow toward Portal, mobile, Home Assistant, and health integration

---

## Short version

- patch live Eira core
- add autonomous self-edit files
- wire self-edit plumbing
- build real food/keto system
- structure recipes and logs
- implement `score my day`
- add trends/reports
- keep food interactions sounding like Eira
- build Ember as a SARAH-style personal life OS
