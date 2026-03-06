#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

RUN_DIR="${RUN_DIR:-./run}"
LLAMA_PID="$RUN_DIR/llama.pid"
FLASK_PID="$RUN_DIR/flask.pid"

notify() {
  local summary="$1"
  local body="${2:-}"
  local icon="${3:-dialog-information}"
  if command -v notify-send &>/dev/null; then
    notify-send --urgency=normal --icon="$icon" "$summary" "$body" || true
  fi
}

stop_pidfile() {
  local name="$1"
  local pidfile="$2"

  if [[ ! -f "$pidfile" ]]; then
    echo "[stop_all] $name: no pidfile ($pidfile)"
    return 0
  fi

  local pid
  pid="$(cat "$pidfile" 2>/dev/null || true)"
  if [[ -z "${pid:-}" ]]; then
    echo "[stop_all] $name: empty pidfile"
    rm -f "$pidfile"
    return 0
  fi

  if kill -0 "$pid" 2>/dev/null; then
    echo "[stop_all] stopping $name (pid $pid)..."
    kill "$pid" 2>/dev/null || true

    # wait up to ~2s
    for _ in {1..20}; do
      if kill -0 "$pid" 2>/dev/null; then
        sleep 0.1
      else
        break
      fi
    done

    if kill -0 "$pid" 2>/dev/null; then
      echo "[stop_all] $name still alive, forcing (pid $pid)..."
      kill -9 "$pid" 2>/dev/null || true
    fi

    echo "[stop_all] $name stopped."
  else
    echo "[stop_all] $name: pid $pid not running"
  fi

  rm -f "$pidfile"
}

stop_pidfile "flask" "$FLASK_PID"
stop_pidfile "llama" "$LLAMA_PID"
echo "[stop_all] done."

# ---- Ember Offline notification ----
notify "🔥 Ember Offline" "All services stopped." "dialog-warning"
