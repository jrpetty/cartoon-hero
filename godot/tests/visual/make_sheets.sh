#!/usr/bin/env bash
# Rebuild the visual comparison: the TS contact sheets (game/scripts/godot-
# visual-*.mjs on @napi-rs/canvas), the same sheets drawn by the Godot port,
# and side-by-side TS | Godot | difference images, with a closeness figure.
#
#   bash godot/tests/visual/make_sheets.sh            # everything
#   bash godot/tests/visual/make_sheets.sh misc ui    # just these sheets
#   CACHE=1 bash godot/tests/visual/make_sheets.sh    # Godot side through the art cache
#
# Needs node (with game/node_modules installed), godot 4.7 and xvfb-run.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../../.." && pwd)"
out="$here/out"
mkdir -p "$out"
touch "$out/.gdignore"
sheets=("$@")
if [ ${#sheets[@]} -eq 0 ]; then
  sheets=(ctx_shapes ui units_kingdom units_factions buildings misc)
fi
cache_arg=()
suffix=""
if [ "${CACHE:-}" = "1" ]; then
  cache_arg=(--cache)
  suffix="_cached"
fi

(cd "$root/game" && node scripts/godot-visual-sheet.mjs "$out" >/dev/null)
(cd "$root/game" && node scripts/godot-visual-ctx.mjs "$out/ts_ctx_shapes.png" >/dev/null)
if [ -f "$root/game/scripts/godot-visual-ui.mjs" ]; then (cd "$root/game" && node scripts/godot-visual-ui.mjs "$out/ts_ui.png" >/dev/null); fi

godot_run() {
  timeout 900 xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
    --path "$root/godot" "$@" 2>&1 | grep -E "^paint|SCRIPT ERROR|Parse Error" || true
}

for s in "${sheets[@]}"; do
  case "$s" in
    ctx_shapes) godot_run res://tests/visual/ctx_shapes.tscn -- "tests/visual/out/godot_ctx_shapes.png" ;;
    ui) godot_run res://tests/visual/ui_sheet.tscn -- "tests/visual/out/godot_ui.png" ;;
    *) godot_run res://tests/visual/art_sheet.tscn -- "${cache_arg[@]}" "$s" "tests/visual/out/godot_${s}${suffix}.png" ;;
  esac
  stack=()
  case "$s" in units_*|buildings) stack=(--stack) ;; esac
  printf '%-16s ' "$s$suffix"
  (cd "$root/game" && node scripts/godot-visual-compare.mjs "$out/ts_$s.png" "$out/godot_${s}${suffix}.png" "$out/cmp_${s}${suffix}.png" "${stack[@]}")
done
