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

# ---- files ----
RUN_DIR="${RUN_DIR:-./run}"
LOG_DIR="${LOG_DIR:-./logs}"
mkdir -p "$RUN_DIR" "$LOG_DIR"

LLAMA_PID="$RUN_DIR/llama.pid"
FLASK_PID="$RUN_DIR/flask.pid"
LLAMA_LOG="$LOG_DIR/llama.log"
FLASK_LOG="$LOG_DIR/flask.log"

is_pid_running() {
  local pidfile="$1"
  [[ -f "$pidfile" ]] || return 1
  local pid
  pid="$(cat "$pidfile" 2>/dev/null || true)"
  [[ -n "${pid:-}" ]] || return 1
  kill -0 "$pid" 2>/dev/null
}

echo "[start_all] model: $MODEL"
echo "[start_all] llama: ${LLAMA_HOST}:${LLAMA_PORT}"
echo "[start_all] flask: ${FLASK_HOST}:${FLASK_PORT}"
echo "[start_all] flask cmd: ${FLASK_APP_CMD}"

# ---- start llama ----
if is_pid_running "$LLAMA_PID"; then
  echo "[start_all] llama already running (pid $(cat "$LLAMA_PID"))"
else
  echo "[start_all] starting llama server..."
  nohup python3 -m llama_cpp.server --model "$MODEL" --host "$LLAMA_HOST" --port "$LLAMA_PORT" >"$LLAMA_LOG" 2>&1 &
  echo $! > "$LLAMA_PID"
  echo "[start_all] llama pid $(cat "$LLAMA_PID") (log: $LLAMA_LOG)"
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
