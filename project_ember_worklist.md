
# Project Ember – Worklist

## Current Status
- Local AI host working
- Scavenger Inventory tool mounted inside Ember UI
- GitHub repository connected and pushing
- GitHub push button implemented
- Basic tool iframe host functioning

---

# Priority Worklist

## 1. Inventory System Improvements
- Fix remaining layout quirks in embedded inventory UI
- Ensure Add / Inventory / Admin navigation works reliably in iframe
- Add part auto‑capitalization for part numbers
- Improve part lookup / enrichment pipeline
- Validate multi‑add enrichment flow
- Add category normalization
- Add location/bin management UI
- Add bulk edit capability
- Add CSV export

---

## 2. Admin Console
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

## 3. AI Integration
Primary architecture:

Main brain:
- DistilGPT‑2 (Ember)

Sub‑models:
- GPT‑Neo 1.3B
- T5

Responsibilities:

Ember:
- User interaction
- Decision routing
- Response synthesis

Sub‑models:
- Structured tasks
- Data lookup
- Reasoning jobs

Future additions:

- Tool‑calling layer
- Local knowledge store
- Component datasheet retrieval
- Circuit design assistance
- Code generation improvements

---

## 4. Inventory + AI Integration

Planned capabilities:

- Ask Ember about inventory parts
- Auto categorize parts
- Datasheet enrichment
- Suggest circuit uses
- Detect duplicate parts
- Auto generate descriptions
- AI assisted part entry

Example:
User enters:
"S9014"

System returns:

- Manufacturer options
- Package
- Ratings
- Category
- Notes
- Suggested uses

---

## 5. UI Improvements

Planned:

- Tool plugin system
- Consistent top bar across tools
- Better iframe host system
- Modal system cleanup
- Dark theme refinement
- Responsive layout improvements

---

## 6. Plugin System

Goal: allow tools to mount inside Ember easily

Plugin format:

Tool definition
- name
- id
- description
- type (iframe/native)
- route

Examples:

Inventory  
Admin Console  
Future tools:

- Circuit Builder
- Datasheet Browser
- Component Comparator
- Code Lab

---

## 7. GitHub Integration

Current:
- Manual git push button

Planned:

- commit message UI
- branch switching
- commit history viewer
- diff viewer
- repo health checks

---

## 8. Future Tools

### Circuit Builder
Interactive electronics design

### Datasheet Browser
Search local and remote datasheets

### Component Comparator
Compare parts side by side

### Knowledge Base
Technical notes + personal documentation

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

- prune branches
- clean unused models
- backup inventory CSV
- export knowledge base
- tag stable versions

---

# Notes

Project Ember is intended to function as a **local engineering brain**, combining:

- AI reasoning
- electronics inventory
- technical knowledge
- tool plugins

The system is designed to remain:

- local‑first
- modular
- extensible
