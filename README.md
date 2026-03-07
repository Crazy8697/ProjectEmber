# 🔥 Project Ember

A local, private AI assistant with a persistent memory system, submind architecture, and web UI. Runs entirely on your own hardware — no cloud, no telemetry, no subscriptions.

![Project Ember](static/projectember.png)

---

## Overview

Project Ember is a Flask-based AI assistant powered by a local LLM backend (llama.cpp + Mistral 7B). It features a persistent memory system, a modular submind architecture for specialized task routing, a scavenger inventory tracker, and a keto nutrition assistant — all accessible via a browser UI.

The system is designed around a two-user model: a runtime user (`eira`) owns the AI's private memory and storage, while an admin user (`daddy`) owns the repo and shared scripts. This is intentional — Eira's memory stays hers.

---

## Features

- **Chat UI** with context-aware token bar (CTX / IN / OUT / LIMIT / %)
- **Persistent memory** — key/value store, saved between sessions
- **Submind routing** — queries automatically routed to specialized sub-models
- **Keto submind** — nutrition tracking, meal planning, macro calculations
- **Scavenger inventory** — track physical items, locations, quantities
- **Admin panel** — memory management, system controls
- **Start/Stop desktop launchers** with KDE notifications
- **Fully local** — Mistral 7B Q4_K_M via llama.cpp, no internet required

---

## Architecture

```
ember_app.py          # Flask app, main routes, memory API
eai_core.py           # Core EAI logic, prompt construction
submind_router.py     # Routes queries to appropriate submind
subminds/
  __init__.py         # Submind orchestration
  keto_submind.py     # Keto/nutrition specialist
plugins/
  scavenger/          # Inventory tracking plugin
nutrition_db.py       # Nutrition database interface
seed_nutrition.py     # Seeds nutrition DB from source data
chat_storage.py       # Chat history persistence
tools_registry.py     # Tool definitions for EAI
templates/            # Jinja2 HTML templates
static/               # CSS, JS, favicon
```

**Runtime layout:**
```
/home/eira/ProjectEmber/          # Repo root (eira:eira, group-readable)
  run/                            # PID files
  logs/                           # flask.log, llama.log
/home/eira/.local/share/projectember/
  memory/memory.json              # Persistent memory (eira-only)
/home/daddy/ProjectEmber/
  ai_models/mistral7b/            # Model weights
  start_ember_shared.sh           # Desktop launcher wrapper
  stop_ember_shared.sh            # Desktop stop wrapper
```

---

## Requirements

### Hardware
- 64GB system RAM recommended
- 6GB+ VRAM (NVIDIA GPU)
- x86_64 Linux

### Software
- Bazzite / Fedora Linux (tested)
- Python 3.10+
- CUDA drivers
- llama-cpp-python with CUDA support
- Flask

### Python packages
```bash
pip install flask llama-cpp-python requests
```

For llama-cpp-python with CUDA:
```bash
CMAKE_ARGS="-DGGML_CUDA=on" pip install llama-cpp-python --force-reinstall --no-cache-dir
```

---

## Installation

### 1. Clone the repo
```bash
git clone https://github.com/Crazy8697/ProjectEmber.git /home/eira/ProjectEmber
cd /home/eira/ProjectEmber
```

### 2. Set up the model
Download [Mistral-7B-Instruct-v0.3-Q4_K_M.gguf](https://huggingface.co/mistralai/Mistral-7B-Instruct-v0.3) and place it at:
```
/home/daddy/ProjectEmber/ai_models/mistral7b/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf
```

Or update the `LLAMA_MODEL` path in `.env`.

### 3. Configure environment
```bash
cp .env.example .env
# Edit .env as needed
```

Key variables:
```bash
LLAMA_MODEL=/home/daddy/ProjectEmber/ai_models/mistral7b/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf
LLAMA_HOST=127.0.0.1
LLAMA_PORT=8080
FLASK_HOST=0.0.0.0
FLASK_PORT=5000
```

### 4. Set up permissions
```bash
# Allow admin user to traverse eira's home
sudo chmod o+x /home/eira

# Add admin user to eira group
sudo usermod -aG eira daddy

# Set correct ownership on run dir
sudo chown -R eira:eira /home/eira/ProjectEmber/run
```

### 5. Set up private storage
```bash
mkdir -p /home/eira/.local/share/projectember/memory
```

### 6. Seed the nutrition database
```bash
python3 seed_nutrition.py
```

### 7. Install desktop launchers (KDE/Plasma)
Copy `Start-Ember.desktop` and `Stop-Ember.desktop` to `~/.local/share/applications/`.

Launcher scripts live at:
- `/home/daddy/ProjectEmber/start_ember_shared.sh`
- `/home/daddy/ProjectEmber/stop_ember_shared.sh`

---

## Usage

### Start
```bash
/home/daddy/ProjectEmber/start_ember_shared.sh
```
Or use the **Start Ember** desktop launcher.

Then open: [http://127.0.0.1:5000](http://127.0.0.1:5000)

### Stop
```bash
/home/daddy/ProjectEmber/stop_ember_shared.sh
```
Or use the **Stop Ember** desktop launcher.

### Logs
```bash
# Flask
sudo -u eira bash -lc 'tail -f /home/eira/ProjectEmber/logs/flask.log'

# llama.cpp
sudo -u eira bash -lc 'tail -f /home/eira/ProjectEmber/logs/llama.log'
```

---

## Memory System

Eira can save and retrieve persistent memory mid-conversation using inline commands in her responses:

```
[MEMORY_SAVE: key | value]
[MEMORY_DELETE: key]
```

Memory is stored at `/home/eira/.local/share/projectember/memory/memory.json` and is owned exclusively by the `eira` user.

---

## Context & Token Limits

The llama.cpp server is configured with `--n_ctx 3072` (hardware-optimized for 6GB VRAM with Mistral 7B Q4_K_M). The token bar in the UI reflects this limit. The model supports up to 32768 tokens natively, but VRAM constrains practical context to ~3072.

---

## Project Status

Active development. See `docs/project_ember_worklist.md` for the current task list.

**Completed:**
- Query error handling
- Token context bar
- UI text rendering fixes
- Start/Stop desktop integration with KDE notifications
- Nutrition DB
- Scavenger inventory

**In progress:**
- Auto-summary / rolling memory at context threshold

---

## License

Private project. Not licensed for redistribution.
