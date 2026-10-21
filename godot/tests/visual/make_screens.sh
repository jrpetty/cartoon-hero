#!/usr/bin/env bash
# Rebuild the menus/full-screen UI comparison: every screen state drawn by the
# TS (game/scripts/godot-visual-screens.mjs on @napi-rs/canvas), the same
# states drawn by the port (screens_sheet.tscn), and TS / Godot / difference
# images (stacked) with a closeness figure per state in summary.txt.
#
#   bash godot/tests/visual/make_screens.sh              # everything
#   bash godot/tests/visual/make_screens.sh menu career  # states starting with these
#
# Needs node (with game/node_modules installed), godot 4.7 and xvfb-run.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../../.." && pwd)"
out="$here/screens"
mkdir -p "$out"
touch "$out/.gdignore"
(cd "$root/game" && TZ=UTC node scripts/godot-visual-screens.mjs "$out" "$@" >/dev/null)
TZ=UTC timeout 1800 xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
  --path "$root/godot" res://tests/visual/screens_sheet.tscn -- "$@" 2>&1 \
  | grep -E "SCRIPT ERROR|Parse Error|ERROR: .*res://|at: .*res://" || true
summary="$out/summary.txt"
fresh="$out/summary.new"
: > "$fresh"
for ts in "$out"/ts_*.png; do
  name="$(basename "$ts" .png)"
  name="${name#ts_}"
  if [ $# -gt 0 ]; then
    keep=0
    for p in "$@"; do
      case "$name" in "$p"*) keep=1 ;; esac
    done
    [ "$keep" = 1 ] || continue
  fi
  gd="$out/godot_$name.png"
  if [ ! -f "$gd" ]; then
    echo "$name: no Godot image" | tee -a "$fresh"
    continue
  fi
  line=$(cd "$root/game" && node scripts/godot-visual-compare.mjs "$ts" "$gd" "$out/cmp_$name.png" --stack)
  printf '%-28s %s\n' "$name" "$line" | tee -a "$fresh"
done
# Keep the other states' lines when only some were rebuilt.
if [ $# -gt 0 ] && [ -f "$summary" ]; then
  awk 'NR==FNR { seen[$1] = 1; next } !($1 in seen)' "$fresh" "$summary" >> "$fresh"
fi
sort "$fresh" > "$summary"
rm -f "$fresh"
