#!/usr/bin/env bash
# One-command launcher for the Cartoon Hero dashboard.
# Installs dependencies (first run only) and starts the server, then the
# dashboard is available at http://127.0.0.1:8000/
set -euo pipefail
cd "$(dirname "$0")"

PY="${PYTHON:-python3}"

if ! "$PY" -c "import fastapi, uvicorn" >/dev/null 2>&1; then
  echo "Installing dependencies..."
  "$PY" -m pip install -r requirements.txt
fi

echo ""
echo "  Cartoon Hero dashboard -> http://127.0.0.1:8000/"
echo "  API docs               -> http://127.0.0.1:8000/docs"
echo "  (Ctrl+C to stop)"
echo ""

exec "$PY" -m uvicorn app:app --reload
