#!/usr/bin/env bash
# Echo launcher — installs deps (once) and starts the server.
#   ./run.sh
# Requires ANTHROPIC_API_KEY in your environment for AI replies.
set -e
cd "$(dirname "$0")"

# Use a local virtualenv so we never touch your system Python.
if [ ! -d ".venv" ]; then
  echo "→ creating virtualenv…"
  python3 -m venv .venv
fi
# shellcheck disable=SC1091
source .venv/bin/activate

echo "→ installing dependencies…"
pip install -q -r requirements.txt

if [ -z "$ANTHROPIC_API_KEY" ]; then
  echo "⚠  ANTHROPIC_API_KEY not set — Echo will run and store everything,"
  echo "   but you won't get AI replies until you set it:"
  echo "     export ANTHROPIC_API_KEY=sk-ant-..."
fi

echo "→ starting Echo at http://127.0.0.1:8000  (Ctrl+C to stop)"
exec python -m uvicorn app:app --reload
