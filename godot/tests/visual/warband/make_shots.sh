#!/usr/bin/env bash
# The Warband Tactics screen in each of its phases, drawn by the TS
# (game/scripts/godot-visual-warband.mjs on @napi-rs/canvas) and by the Godot
# port (warband_shot.tscn), with side-by-side TS | Godot | difference images
# and a closeness figure per shot.
#
#   bash godot/tests/visual/warband/make_shots.sh               # every shot
#   bash godot/tests/visual/warband/make_shots.sh shop battle   # just these
#   CACHE=1 bash godot/tests/visual/warband/make_shots.sh       # units through the art cache
#
# Needs node (with game/node_modules installed), godot 4.7 and xvfb-run.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../../../.." && pwd)"
out="$here/out"
mkdir -p "$out"
touch "$out/.gdignore"
shots=("$@")
if [ ${#shots[@]} -eq 0 ]; then
  shots=(difficulty commander shop battle carousel augment result summary scout)
fi
cache_arg=()
suffix=""
if [ "${CACHE:-}" = "1" ]; then
  cache_arg=(--cache)
  suffix="_cached"
fi

(cd "$root/game" && node scripts/godot-visual-warband.mjs "$out" >/dev/null)

for s in "${shots[@]}"; do
  timeout 900 xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
    --path "$root/godot" res://tests/visual/warband/warband_shot.tscn -- "${cache_arg[@]}" "$s" \
    "tests/visual/warband/out/godot_${s}${suffix}.png" 2>&1 | grep -E "SCRIPT ERROR|Parse Error|shot .*reached" || true
  printf '%-14s ' "$s$suffix"
  (cd "$root/game" && node scripts/godot-visual-compare.mjs "$out/ts_$s.png" "$out/godot_${s}${suffix}.png" "$out/cmp_${s}${suffix}.png" --stack)
done
