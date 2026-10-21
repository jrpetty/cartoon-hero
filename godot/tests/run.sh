#!/usr/bin/env bash
# Run the Godot tests; fail on any test failure OR any script error (GDScript
# has no exceptions, so a runtime error prints and carries on — this catches it).
cd "$(dirname "$0")/.." || exit 1
out=$(godot --headless --path . res://tests/run.tscn -- "$@" 2>&1)
code=$?
echo "$out" | grep -v "^$\|^Godot Engine"
if echo "$out" | grep -q "SCRIPT ERROR\|Parse Error\|Compile Error"; then
  echo "FAILED: script errors above"
  exit 1
fi
exit $code
