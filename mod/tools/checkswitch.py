#!/usr/bin/env python3
"""Fail if any enum switch without a `default` misses a constant.

javac cannot tell us this offline: with Minecraft absent from the classpath it
gives up before flow analysis, which is exactly how a non-exhaustive switch got
into the v1.49.0 build and broke it in CI.
"""
import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."


def enum_constants(src, name):
    m = re.search(r'\benum\s+' + name + r'\b[^{]*\{', src)
    if not m:
        return None
    i, depth, start, end = m.end(), 1, m.end(), None
    while i < len(src):
        c = src[i]
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                end = i
                break
        elif c == ';' and depth == 1:
            end = i
            break
        i += 1
    if end is None:
        return None
    head = re.sub(r'//[^\n]*', '', re.sub(r'/\*.*?\*/', '', src[start:end], flags=re.S))
    buf, depth2 = '', 0
    for ch in head:
        if ch in '({[':
            depth2 += 1
        elif ch in ')}]':
            depth2 -= 1
        elif depth2 == 0:
            buf += ch
    return {t.strip() for t in buf.split(',') if re.fullmatch(r'[A-Z][A-Z0-9_]*', t.strip())}


def java_files():
    for root, _, files in os.walk(ROOT):
        for f in files:
            if f.endswith('.java'):
                yield os.path.join(root, f)


sizes = {}
for path in java_files():
    s = open(path).read()
    for m in re.finditer(r'\benum\s+(\w+)', s):
        c = enum_constants(s, m.group(1))
        if c:
            sizes.setdefault(m.group(1), c)

bad = []
for path in java_files():
    s = open(path).read()
    for m in re.finditer(r'switch\s*\(([^)]*)\)\s*\{', s):
        i, depth = m.end(), 1
        while i < len(s) and depth:
            if s[i] == '{':
                depth += 1
            elif s[i] == '}':
                depth -= 1
            i += 1
        body = s[m.end():i]
        if re.search(r'\bdefault\s*(->|:)', body):
            continue
        # A case can carry several labels -- `case BO1, BO3, BO5 ->` -- and the
        # old pattern only saw the label right after `case`, reporting the rest
        # missing. That is a false alarm on perfectly exhaustive code, so every
        # comma-separated label is collected.
        cases = set()
        for labels in re.findall(r'case\s+([A-Z][A-Z0-9_]*(?:\s*,\s*[A-Z][A-Z0-9_]*)*)\s*(?:->|:)', body):
            for label in labels.split(','):
                cases.add(label.strip())
        if len(cases) < 2:
            continue
        for name, consts in sizes.items():
            if cases <= consts:
                missing = consts - cases
                if missing:
                    line = s[:m.start()].count('\n') + 1
                    bad.append("%s:%d switch on %s missing %s"
                               % (os.path.basename(path), line, name, sorted(missing)))
                break

if bad:
    print("   FAILED:")
    for b in bad:
        print("     " + b)
    sys.exit(1)
print("   %d enums scanned, no non-exhaustive switches" % len(sizes))
