#!/usr/bin/env bash
# Offline checks for Mob Trumps.
#
# The pure-game package has no Minecraft on its classpath, so it compiles and
# runs standalone. That is what makes these worth having: the CI build only
# tells you the mod compiles, this tells you the RULES still hold.
#
#   1. syntax over the whole source tree (filtered for the missing Minecraft
#      classes, which are expected).
#
#      KNOWN LIMIT — this cannot check calls in the client package. javac stops
#      at the unresolvable Minecraft imports and never performs method
#      resolution for those files, so a call with the wrong ARITY or wrong
#      argument types to your own method reports nothing at all. Verified by
#      deliberately breaking a drawFrame call: this script said "clean".
#      CI is the only real compiler for anything under client/. Do not read a
#      pass here as "the client code compiles" — it does not mean that.
#   2. enum switches: any without a default must list every constant. This is
#      the class of bug that broke the v1.49.0 build -- javac cannot catch it
#      offline because it gives up before flow analysis
#   3. the game rules themselves: card set, campaign decks, recycler economics
#
# Two further checks run against a BUILT jar rather than the source, because
# what they look for only exists once things are packaged:
#
#   tools/checkdist.py   <jar>      client-only classes reachable on a server
#   tools/checkassets.py <jar> .    missing models/textures, misordered overrides
#
# CI runs both before it will publish a release. The game rules also have a
# real JUnit suite now (src/test/java), run by `./gradlew build`.
#
# Usage:  mod/tools/check.sh
set -u
cd "$(dirname "$0")/.." || exit 1
SRC=src/main/java
OUT=$(mktemp -d)
fail=0

echo "== 1. syntax =="
if javac -nowarn -proc:none -Xmaxerrs 100000 -d "$OUT" $(find $SRC -name '*.java') 2>&1 \
     | grep "error:" \
     | grep -Ev "cannot find symbol|does not exist|cannot access|method does not override" \
     | grep . ; then
  echo "   FAILED"; fail=1
else
  echo "   clean"
fi

echo "== 1b. imports =="
if ! python3 tools/checkimports.py "$SRC"; then fail=1; fi

echo "== 1c. wager screen layout =="
if ! python3 tools/checkwagerlayout.py; then fail=1; fi

echo "== 1d. card portraits are placed, not cornered =="
if ! python3 tools/checkcardcoords.py "$SRC"; then fail=1; fi

echo "== 1h. no award falls off its page =="
if ! python3 tools/checkawardpages.py "$SRC"; then fail=1; fi

echo "== 1k. the profile page fits its own statistics =="
if ! python3 tools/checkprofilepage.py "$SRC"; then fail=1; fi

echo "== 1i. every award can actually be earned =="
if ! python3 tools/checkawards.py "$SRC"; then fail=1; fi

echo "== 1g. screens open with their state =="
if ! python3 tools/checkmenusync.py "$SRC"; then fail=1; fi

echo "== 1f. every block drops itself =="
if ! python3 tools/checkloot.py "$SRC" src/main/resources; then fail=1; fi

echo "== 1j. every sound is wired all the way through =="
if ! python3 tools/checksoundwiring.py "$SRC" src/main/resources; then fail=1; fi

echo "== 1e. sounds are quiet and comfortable =="
# A skip is tolerable on a developer machine and NOT tolerable in CI. This
# check spent its whole life skipping silently while its own message claimed
# "CI installs it" -- CI neither installed it nor ran it -- so a missing
# decoder is now a hard failure wherever $CI is set. A gate that can quietly
# not run is not a gate.
if python3 -c "import soundfile" 2>/dev/null; then
  python3 tools/checksounds.py || fail=1
elif [ -n "${CI:-}" ]; then
  echo "   FAIL  soundfile is missing in CI, so the sounds went unchecked."
  echo "         The workflow must 'pip install soundfile numpy' before this runs."
  fail=1
else
  echo "   SKIPPED - no soundfile module here, so the sounds were NOT checked."
  echo "   CI installs soundfile and fails without it; 'pip install soundfile numpy' to run it here."
fi

echo "== 2. enum switch exhaustiveness =="
python3 tools/checkswitch.py "$SRC/com/jrpetty/mobtrumps" || fail=1

echo "== 3. game rules =="
javac -nowarn -d "$OUT" $SRC/com/jrpetty/mobtrumps/game/*.java 2>/dev/null || { echo "   game package did not compile"; fail=1; }
javac -nowarn -cp "$OUT" -d "$OUT" tools/Regress.java 2>/dev/null || { echo "   harness did not compile"; fail=1; }
java -cp "$OUT" Regress || fail=1

rm -rf "$OUT"
[ $fail -eq 0 ] && echo "ALL CHECKS PASS" || echo "CHECKS FAILED"
exit $fail
