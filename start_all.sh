#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# ---- load local secrets/config (.env) ----
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source ./.env
  set +a
fi

# ---- config (override via env or .env) ----
MODEL="${LLAMA_MODEL:-/home/daddy/ProjectEmber/ai_models/mistral7b/Mistral-7B-Instruct-v0.3-Q4_K_M.gguf}"
LLAMA_HOST="${LLAMA_HOST:-127.0.0.1}"
LLAMA_PORT="${LLAMA_PORT:-8080}"

FLASK_HOST="${FLASK_HOST:-0.0.0.0}"
FLASK_PORT="${FLASK_PORT:-5000}"
FLASK_APP_CMD="${FLASK_APP_CMD:-python3 ember_app.py}"

# How long to wait for llama to become ready (seconds)
LLAMA_WAIT_TIMEOUT="${LLAMA_WAIT_TIMEOUT:-120}"

# ---- files ----
RUN_DIR="${RUN_DIR:-./run}"
LOG_DIR="${LOG_DIR:-./logs}"
mkdir -p "$RUN_DIR" "$LOG_DIR"

LLAMA_PID="$RUN_DIR/llama.pid"
FLASK_PID="$RUN_DIR/flask.pid"
LLAMA_LOG="$LOG_DIR/llama.log"
FLASK_LOG="$LOG_DIR/flask.log"

# ---- helpers ----
is_pid_running() {
  local pidfile="$1"
  [[ -f "$pidfile" ]] || return 1
  local pid
  pid="$(cat "$pidfile" 2>/dev/null || true)"
  [[ -n "${pid:-}" ]] || return 1
  kill -0 "$pid" 2>/dev/null
}

notify() {
  local summary="$1"
  local body="${2:-}"
  local icon="${3:-dialog-information}"
  # notify-send is available on KDE/Plasma — silently skip if not found
  if command -v notify-send &>/dev/null; then
    notify-send --urgency=normal --icon="$icon" "$summary" "$body" || true
  fi
}

wait_for_llama() {
  local timeout="$LLAMA_WAIT_TIMEOUT"
  local elapsed=0
  local interval=2
  echo "[start_all] waiting for llama server to become ready (timeout: ${timeout}s)..."
  while (( elapsed < timeout )); do
    if curl -sf "http://${LLAMA_HOST}:${LLAMA_PORT}/v1/models" &>/dev/null; then
      echo "[start_all] llama ready after ${elapsed}s."
      return 0
    fi
    # Also check the process didn't die
    if ! is_pid_running "$LLAMA_PID"; then
      echo "[start_all] ERROR: llama process died during startup. Check $LLAMA_LOG"
      notify "Ember Failed to Start" "llama server crashed during startup. Check logs." "dialog-error"
      exit 1
    fi
    sleep "$interval"
    (( elapsed += interval ))
    echo "[start_all] still waiting... (${elapsed}s)"
  done
  echo "[start_all] ERROR: llama did not become ready within ${timeout}s. Check $LLAMA_LOG"
  notify "Ember Failed to Start" "llama server timed out after ${timeout}s. Check logs." "dialog-error"
  exit 1
}

# ---- banner ----
echo "[start_all] model: $MODEL"
echo "[start_all] llama: ${LLAMA_HOST}:${LLAMA_PORT}"
echo "[start_all] flask: ${FLASK_HOST}:${FLASK_PORT}"
echo "[start_all] flask cmd: ${FLASK_APP_CMD}"

# ---- start llama ----
if is_pid_running "$LLAMA_PID"; then
  echo "[start_all] llama already running (pid $(cat "$LLAMA_PID"))"
else
  echo "[start_all] starting llama server..."
  nohup python3 -m llama_cpp.server --model "$MODEL" --host "$LLAMA_HOST" --port "$LLAMA_PORT" --n_ctx 3072 >"$LLAMA_LOG" 2>&1 &
  echo $! > "$LLAMA_PID"
  echo "[start_all] llama pid $(cat "$LLAMA_PID") (log: $LLAMA_LOG)"

  # Wait until llama is actually ready before starting Flask
  wait_for_llama
fi

# ---- start flask (Ember UI) ----
if is_pid_running "$FLASK_PID"; then
  echo "[start_all] flask already running (pid $(cat "$FLASK_PID"))"
else
  echo "[start_all] starting flask UI..."

  # ember_app.py reads EMBER_HOST/EMBER_PORT (not HOST/PORT)
  export EMBER_HOST="$FLASK_HOST"
  export EMBER_PORT="$FLASK_PORT"

  nohup bash -lc "$FLASK_APP_CMD" >"$FLASK_LOG" 2>&1 &
  echo $! > "$FLASK_PID"
  echo "[start_all] flask pid $(cat "$FLASK_PID") (log: $FLASK_LOG)"
fi

echo "[start_all] done."
echo "[start_all] UI:    http://${FLASK_HOST}:${FLASK_PORT}"
echo "[start_all] llama: http://${LLAMA_HOST}:${LLAMA_PORT}"

