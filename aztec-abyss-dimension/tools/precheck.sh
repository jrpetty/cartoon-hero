#!/usr/bin/env bash
# Catches compile errors that are NOT just the missing Minecraft classpath.
#
# There is no Minecraft jar in this sandbox, so javac cannot fully compile the
# mod - it reports a fixed set of "package does not exist" and the "cannot find
# symbol" errors that follow from them. Those are noise. Anything else is a real
# mistake that would otherwise cost a five-minute CI round trip to discover.
#
# Written after exactly that happened: a duplicate local variable was sitting in
# javac's output the whole time, and the histogram it was being read through was
# truncated to the top five categories, so it was never seen. This prints every
# category and never truncates.
#
# What it does and does not catch, honestly. javac stops after the phase that
# produced errors, and without Minecraft the resolution phase always produces
# some - so declaration-level faults (syntax, duplicate names, bad arity, broken
# structure) are reported, and nothing inside a method body is ever reached.
#
# That second half is not a small gap and it has cost a CI cycle already. A block
# of code pasted into the WRONG METHOD - referencing a local that does not exist
# there - is invisible here, because javac never attributes any method body. It
# was tried: a deliberate out-of-scope reference produced no error at all, only
# the unrelated missing-class one from the same file. There is no filter that
# recovers it, so do not go looking for one.
#
# The guard against that class of mistake is not this script, it is discipline in
# the edit itself: when replacing text programmatically, ALWAYS assert the match
# count is what you expect before writing. Every instance of this bug so far has
# been a replace() that silently took the first of two matches.
#
# CI remains the real compiler.
#
#   usage: tools/precheck.sh
#   exit 0 = nothing but classpath noise; exit 1 = look at what it prints
set -u
cd "$(dirname "$0")/.." || exit 2

# -Xmaxerrs matters: javac stops at 100 errors by default, and the classpath
# noise alone is more than that - so a real error further down the alphabet was
# never reached. That is exactly how the duplicate variable got through.
OUT=$(javac -proc:none -Xmaxerrs 100000 -Xmaxwarns 1 -nowarn -d /tmp/precheck-classes $(find src/main/java -name '*.java') 2>&1)
rm -f javac.*.args
rm -rf /tmp/precheck-classes

# Errors that are purely a consequence of having no Minecraft on the classpath.
# The @Override one is in that set too: a method overriding a Minecraft class
# cannot be seen to override anything when that class is not there.
REAL=$(printf '%s\n' "$OUT" \
  | grep -E '\.java:[0-9]+: error:' \
  | grep -vE 'error: (package [A-Za-z0-9_.]+ does not exist|cannot find symbol)' \
  | grep -vE 'error: method does not override or implement a method from a supertype')


# The one kind of "cannot find symbol" that is NOT classpath noise: a type used
# but never imported. javac words both identically, so the distinction has to
# come from the source - see tools/missing_imports.py. This has cost three CI
# cycles, which is why it is worth a second pass over the same output.
IMPORTS=$(python3 "$(dirname "$0")/missing_imports.py")
if [ -n "$IMPORTS" ]; then
  echo "precheck: MISSING IMPORTS"
  echo "------------------------"
  printf '%s\n' "$IMPORTS"
  [ -n "$REAL" ] && { echo; echo "precheck: REAL ERRORS"; printf '%s\n' "$REAL"; }
  exit 1
fi

if [ -z "$REAL" ]; then
  echo "precheck: clean (classpath noise only)"
  exit 0
fi

echo "precheck: REAL ERRORS"
echo "---------------------"
printf '%s\n' "$REAL"
exit 1
