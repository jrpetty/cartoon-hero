#!/usr/bin/env bash
# The online lobby and the map editor, TS | Godot | difference, with a
# closeness figure per scene. The lobby's TS side is the real DOM overlay in
# Chromium (Playwright); the editor's is the real canvas screen on
# @napi-rs/canvas. Both sides use the same font files (Liberation).
#
#   bash godot/tests/visual/lobby_editor/make.sh            # everything
#   bash godot/tests/visual/lobby_editor/make.sh lobby      # just the lobby
#   bash godot/tests/visual/lobby_editor/make.sh editor     # just the editor
#
# Needs node (game/node_modules, and Playwright's chromium for the lobby),
# godot 4.7 and xvfb-run.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../../../.." && pwd)"
out="$here/out"
mkdir -p "$out"
touch "$out/.gdignore"
what="${1:-all}"

godot_run() {
  timeout 900 xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
    --path "$root/godot" "$@" 2>&1 | grep -E "^wrote|SCRIPT ERROR|Parse Error" || true
}
compare() {
  printf '%-22s ' "$1"
  (cd "$root/game" && node scripts/godot-visual-compare.mjs "$out/ts_$1.png" "$out/godot_$1.png" "$out/cmp_$1.png" --stack)
}

if [ "$what" = all ] || [ "$what" = lobby ]; then
  (cd "$root/game" && node scripts/godot-visual-lobby.mjs "$out" >/dev/null)
  godot_run res://tests/visual/lobby_editor/lobby_sheet.tscn -- tests/visual/lobby_editor/out >/dev/null
  for f in "$out"/ts_lobby_*.png; do n="$(basename "$f" .png)"; compare "${n#ts_}"; done
fi
if [ "$what" = all ] || [ "$what" = editor ]; then
  (cd "$root/game" && node scripts/godot-visual-editor.mjs "$out" >/dev/null)
  godot_run res://tests/visual/lobby_editor/editor_sheet.tscn -- tests/visual/lobby_editor/out >/dev/null
  for f in "$out"/ts_editor_*.png; do n="$(basename "$f" .png)"; compare "${n#ts_}"; done
fi
