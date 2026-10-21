# BBNative — native speed-ups

C++ versions of the simulation's heaviest *pure* routines, loaded as a
GDExtension (`../bb_native.gdextension`, prebuilt libraries in `../bin/`):

| Routine | GDScript | Native |
|---|---|---|
| Flow-field build (group moves, 146×146 map) | ~75 ms | ~1.5 ms |
| A* path search + smoothing | ~5.7 ms | ~0.2 ms |

Each is a line-for-line port doing the same floating-point operations in the
same order (built with `-ffp-contract=off`, no fast-math), so results are bit
for bit those of the GDScript versions — and therefore of the TypeScript
game, which the parity tests check tick by tick. `tests/test_native.gd`
compares native against GDScript directly.

Desktop builds (Windows, Linux) ship the libraries. Anywhere they don't load
(the web build), `Native.available()` is false and the GDScript versions run,
with identical results — so lockstep multiplayer works across all of them.

Rebuild: build godot-cpp (tag `godot-4.5-stable`) for linux and windows
(`scons platform=linux target=template_release`,
`scons platform=windows target=template_release use_mingw=yes`), then
`GODOT_CPP=/path/to/godot-cpp bash build.sh`.
