#!/usr/bin/env python3
"""
Catch a class used without importing it.

tools/check.sh runs javac and then throws away every "cannot find symbol"
error, because the Minecraft classes genuinely are missing offline. That
filter also swallows real mistakes: GuessWhoManager used GuessWhoWager with
no import and check.sh said "clean" — CI found it, eight errors later.

This is narrow on purpose, so it has no false positives worth arguing with.
For every one of OUR OWN top-level classes, if a file names it as
`ClassName.something` or `new ClassName(` and that file neither imports it,
sits in the same package, nor spells it out fully qualified, that is a
compile error and nothing else.
"""
import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/java"

# every top-level class we own: simple name -> set of packages declaring it
owned = {}
files = []
for base, _, names in os.walk(ROOT):
    for n in names:
        if n.endswith(".java"):
            path = os.path.join(base, n)
            files.append(path)
            pkg = os.path.relpath(base, ROOT).replace(os.sep, ".")
            owned.setdefault(n[:-5], set()).add(pkg)

bad = []
for path in files:
    src = open(path, encoding="utf-8").read()
    pkg_m = re.search(r"^\s*package\s+([\w.]+);", src, re.M)
    pkg = pkg_m.group(1) if pkg_m else ""
    me = os.path.basename(path)[:-5]
    imports = set(re.findall(r"^\s*import\s+(?:static\s+)?([\w.]+);", src, re.M))
    imported_simple = {i.rsplit(".", 1)[-1] for i in imports}
    star_pkgs = {i[:-2] for i in imports if i.endswith(".*")}
    # strip comments and string literals so prose cannot trip this
    body = re.sub(r"//[^\n]*", "", src)
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    body = re.sub(r'"(?:\\.|[^"\\])*"', '""', body)

    for name, pkgs in owned.items():
        if name == me or name in imported_simple:
            continue
        if pkg in pkgs:
            continue          # same package needs no import
        if pkgs & star_pkgs:
            continue
        if not re.search(r"(?<![\w.])" + name + r"\s*(?:\.\w|\()", body):
            continue
        # fully qualified use is legal without an import
        qualified = any(re.search(r"(?<![\w])" + re.escape(p + "." + name), body) for p in pkgs)
        if qualified:
            continue
        bad.append((path, name, sorted(pkgs)))

if bad:
    print("FAIL  a class is used without an import:")
    for path, name, pkgs in bad:
        print(f"   {path}: {name}  (declared in {', '.join(pkgs)})")
    sys.exit(1)
print(f"PASS  {len(files)} files: every one of our own classes used is imported.")
