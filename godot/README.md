# Banner & Blade — Godot

The Godot 4 build of Banner & Blade (the TypeScript original is in `../game`).

**Open it:** install Godot 4.7 (the standard build, not .NET) from
godotengine.org, run it, choose *Import*, and pick this folder's
`project.godot`. Press F5 to play.

**Tests:** `godot --headless --path godot res://tests/run.tscn`
(or `tests/run.sh [test_name ...]`).

See `PORTING.md` for how this codebase maps onto the TypeScript one.

## Running scenarios, screenshots and benchmarks

`tests/shots/harness.tscn` runs the real game (`scenes/main.tscn`) and drives
it through a scenario. Rendering needs a display; under Xvfb:

    xvfb-run -a -s "-screen 0 1920x1080x24" godot --rendering-driver opengl3 \
      --path godot res://tests/shots/harness.tscn -- <scenario> [--sync] [--novsync]

- `shots` — the comparison set (opening, selection, battle in the fog,
  production + scoreboard, Oath picker, night, rain, caster, post-match) as
  `tests/shots/godot_NN_*.png`. `node scripts/godot-shots.mjs ../godot/tests/shots`
  (from `game/`) plays the identical scenario in the TS build (Chromium) as
  `ts_NN_*.png`; `scripts/godot-visual-compare.mjs` makes the `cmp_*.png`.
- `perf [--seconds N]` — a busy 8-realm battle: frame interval, main-thread
  CPU (snapshot / world / HUD / events), sim ticks, slow frames. `--sync`
  steps the simulation on the main thread instead of the worker. Runs
  headless too (`godot --headless ...`) for CPU-only numbers.
- `render` — the TS suite's pixel checks on what the GPU drew (terrain bake,
  fog, night layer).
- `gpuprobe` — where a frame's time goes, one feature switched off at a time.

`--sim-sync` on the game's own command line also disables threaded stepping.
