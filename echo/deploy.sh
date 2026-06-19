#!/usr/bin/env bash
# One-command Fly.io deploy for Echo.
#
# Your only manual step is logging into YOUR Fly account first (a browser auth
# that only you can do). After that, run this — it does everything else:
# creates the app, the persistent disk, your secrets, and deploys.
#
#   curl -L https://fly.io/install.sh | sh   # install flyctl (once)
#   fly auth login                           # log into YOUR account (once)
#   ./deploy.sh                              # <- this script does the rest
set -euo pipefail
cd "$(dirname "$0")"

fly_cmd="$(command -v fly || command -v flyctl || true)"
if [ -z "$fly_cmd" ]; then
  echo "✗ Fly CLI not found. Install it, then re-run:"
  echo "    curl -L https://fly.io/install.sh | sh"
  exit 1
fi

if ! "$fly_cmd" auth whoami >/dev/null 2>&1; then
  echo "✗ You're not logged in to Fly. Run this first (it opens your browser):"
  echo "    fly auth login"
  exit 1
fi
echo "✓ Logged in to Fly as: $("$fly_cmd" auth whoami 2>/dev/null)"

# --- the few things only you can decide ------------------------------------
read -rp "Pick a unique app name (e.g. echo-jrp): " APP
read -rp "Region near you [lhr/iad/lax/ams/syd/…]: " REGION
read -rsp "Choose a password to unlock Echo on your phone: " ECHO_PW; echo
read -rsp "Your Anthropic API key (sk-ant-…, blank to skip AI for now): " ANTHROPIC_KEY; echo

[ -n "$APP" ] && [ -n "$REGION" ] && [ -n "$ECHO_PW" ] || { echo "✗ App name, region and password are all required."; exit 1; }

# Personalise the local fly.toml (your clone only; the repo stays generic).
tmp="$(mktemp)"
sed -e "s/^app = .*/app = \"$APP\"/" \
    -e "s/^primary_region = .*/primary_region = \"$REGION\"/" \
    fly.toml > "$tmp" && mv "$tmp" fly.toml
echo "✓ Configured fly.toml for app '$APP' in '$REGION'"

# Create the app (ignore error if it already exists).
"$fly_cmd" apps create "$APP" 2>/dev/null || echo "  (app '$APP' already exists — continuing)"

# Persistent disk so your one giant database survives redeploys.
if ! "$fly_cmd" volumes list --app "$APP" 2>/dev/null | grep -q echo_data; then
  "$fly_cmd" volumes create echo_data --size 1 --region "$REGION" --app "$APP" --yes
  echo "✓ Created 1GB persistent volume 'echo_data'"
else
  echo "✓ Volume 'echo_data' already exists"
fi

# Secrets. ECHO_PASSWORD is mandatory — it's the lock on your most personal data.
"$fly_cmd" secrets set ECHO_PASSWORD="$ECHO_PW" --app "$APP" >/dev/null
echo "✓ Set ECHO_PASSWORD"
if [ -n "$ANTHROPIC_KEY" ]; then
  "$fly_cmd" secrets set ANTHROPIC_API_KEY="$ANTHROPIC_KEY" --app "$APP" >/dev/null
  echo "✓ Set ANTHROPIC_API_KEY (Talk / decide-for-me / dossier enabled)"
else
  echo "• Skipped Anthropic key — interview, decisions & calibration still work; add it later with:"
  echo "    fly secrets set ANTHROPIC_API_KEY=sk-ant-… --app $APP"
fi

echo "→ Deploying… (first build takes a couple of minutes)"
"$fly_cmd" deploy --app "$APP"

echo
echo "🎉 Echo is live:  https://$APP.fly.dev"
echo "   Open it on your phone, enter your password, then Add to Home Screen."
