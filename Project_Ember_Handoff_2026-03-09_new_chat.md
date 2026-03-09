# Project Ember Handoff Summary

_Date: 2026-03-09_

## Current state

Project Ember now has a clear split between:

- **Live repo:** `/home/eira/ProjectEmber`
- **Dev/backup repo:** `/home/daddy/ProjectEmber_dev`

This split was created because prompt/core edits were accidentally made in the wrong copy. Going forward, the **Eira path is authoritative** unless explicitly working in dev.

---

## What was just worked on

### 1. Eira overusing the user's real name
Problem:
- Eira kept calling the user "Adam" in nearly every reply.
- Tone felt repetitive, corporate, and unnatural.

Findings:
- Some username display logic exists in `templates/index.html`, but that was mostly cosmetic.
- The bigger issue was prompt behavior in `eai_core.py`.
- An edited `eai_core.py` was initially applied to the wrong repo copy (`/home/daddy/ProjectEmber`).
- The updated file was then copied into the live path at `/home/eira/ProjectEmber/eai_core.py`.

Goal:
- Keep memory of the user's real name.
- Do **not** use it every message.
- Prefer sparse real-name usage, nicknames, or no name at all.

### 2. Live vs dev path cleanup
Decision:
- Keep the live repo at `/home/eira/ProjectEmber`
- Rename daddy's copy to `/home/daddy/ProjectEmber_dev`
- Do **not** prefix all files with `dev_`
- Do **not** try to make the backup tree runnable

Reason:
- Renaming every file would break imports, references, and scripts.
- Directory-level separation is much cleaner.

### 3. Eira personality evolution / self-editing
Discussion outcome:
- Eira should be able to evolve her own personality over time.
- The user does **not** want her asking permission for minor/moderate style changes.
- She should detect recurring friction, blandness, repetition, or mismatch and improve herself automatically.

Important distinction:
- **Stable identity** should remain more fixed.
- **Adaptive style** should be allowed to evolve autonomously.

Proposed behavior:
- notice issue
- classify issue
- edit adaptive style
- persist change
- log change
- continue without narrating the process unless relevant

---

## New files proposed for the self-edit system

These were generated as proposed additions for the live repo:

- `eai_core_self_edit_ready.py`
- `personality_profile.json`
- `self_edit_log.md`
- `EIRA_SELF_EDIT_USAGE.md`

Purpose:

### `eai_core_self_edit_ready.py`
A strengthened Eira core with:
- better style enforcement
- anti-corporate filler rules
- autonomous self-edit policy
- stronger validation behavior
- guidance for when to use the new personality/profile files

### `personality_profile.json`
Mutable style layer for things like:
- sarcasm level
- warmth level
- profanity level
- real-name usage policy
- nickname behavior
- anti-filler rules
- editable style traits

### `self_edit_log.md`
Append-only log of self-edits with:
- timestamp
- reason
- exact change
- whether change affected adaptive style or stable identity

### `EIRA_SELF_EDIT_USAGE.md`
Operating instructions describing:
- when Eira should update style profile
- when she should propose a core change
- how to use the log/profile files

---

## Important implementation note

The files above are only the **foundation**.

Actual code still needs to be written so Ember/Eira can:
- load `personality_profile.json`
- inject relevant style traits into prompts
- write to `self_edit_log.md`
- perform controlled persistent self-edits automatically

In other words:
- the files exist conceptually
- the plumbing still needs to be implemented

---

## Food / keto project direction added to worklist

The food side should become a real system, not just ad hoc memory.

Key targets:
- natural food logging by conversation
- macro tracking
- meal/day totals
- structured recipes
- persistent nutrition logs
- end-of-day scoring with `score my day`
- later trends/reports

Food rules already established and should be moved into logic:
- exact weights when provided
- midpoint then round up for ranges
- edible-portion estimation only when bone/ineligible weight is involved
- lock values once established

Named recipes/drinks that should become structured data:
- Pothole Coffee
- Cheesy Beef & Mushroom Skillet
- Olive Brine Aioli
- Egg, Broccoli & Cheddar Skillet
- Swamp Sauce
- Backwoods Greens Skillet
- Big Boy Salad
- Tangy Beef Bowl
- Template day patterns
- Tajín concentrate

Goal:
- make the food system usable as a real module
- keep the tone sounding like Eira, not a nutrition pamphlet

---

## Architecture direction clarified

A major conceptual point was captured:

**Project Ember is a personal operating system for life logistics.**

This is the SARAH-from-*Eureka* direction:
- Eira is the interface/reasoning layer
- independent systems sit beneath her
- those systems should work without Eira
- Eira makes them easier to interact with together

Correct architecture:

```text
systems -> AI interface
```

Not:

```text
AI -> everything
```

Core domain targets:
- food/nutrition
- inventory
- health data
- home automation
- calendar/reminders
- projects/admin

Long-term target:
- Eira Portal tablet dashboard as the central control surface

---

## Immediate next steps

1. Replace live `eai_core.py` with the self-edit-ready version
2. Add:
   - `personality_profile.json`
   - `self_edit_log.md`
   - `EIRA_SELF_EDIT_USAGE.md`
3. Wire backend prompt-loading for personality profile
4. Wire persistent self-edit logging
5. Build the food system into structured storage/retrieval
6. Implement `score my day`
7. Add trends/reporting later

---

## Critical working rule for future chats

Unless explicitly told otherwise:
- work against `/home/eira/ProjectEmber`
- treat `/home/daddy/ProjectEmber_dev` as backup/dev only
- do not assume changes made in the dev copy affect live behavior
