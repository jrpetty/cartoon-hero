"""Types used but never imported - found from the source, not from javac.

Why not from javac: without Minecraft (or gson) on the classpath, a file with a
missing import errors at its *import block* first - "package com.google.gson does
not exist" - and javac never reaches the line where the unimported type is used.
So the very case this exists to catch is invisible in javac's output. It was
tried, twice, and produced nothing both times.

Reading the source needs no classpath at all. For each file: collect the
CamelCase identifiers it uses unqualified, then subtract everything it is
entitled to see - its imports, its own declarations, its package siblings, and
java.lang. What is left is a type the file cannot name, which is a build failure
on CI.

Nested types inherited from a supertype (CustomPacketPayload.Type,
BlockBehaviour.Properties) are legal unqualified and cannot be resolved without
the classpath, so they land here as false positives. Those are baselined in
precheck-known.txt and only NEW names are reported - which is enough, because the
bug this catches is always a type introduced by the edit being checked.

    usage: tools/missing_imports.py            (report new problems, exit 1)
           PRECHECK_BASELINE=1 ... (rewrite the known list, exit 0)
"""

import os
import re
import sys

JAVA_LANG = {
    'String', 'Integer', 'Long', 'Double', 'Float', 'Boolean', 'Byte', 'Short',
    'Character', 'Object', 'Math', 'System', 'Exception', 'RuntimeException',
    'IllegalStateException', 'IllegalArgumentException', 'NumberFormatException',
    'Comparable', 'Iterable', 'Override', 'Deprecated', 'SuppressWarnings',
    'Enum', 'Class', 'Thread', 'Runnable', 'StringBuilder', 'Number', 'Void',
    'Error', 'Throwable', 'Record', 'FunctionalInterface', 'SafeVarargs',
}

# CamelCase only: a lower-case second letter keeps ALL_CAPS constants out.
TOKEN = re.compile(r'\b([A-Z][a-z][A-Za-z0-9_]*)\b')


def strip_noise(src: str) -> str:
    """Removes comments and string literals, which are not code."""
    src = re.sub(r'/\*.*?\*/', ' ', src, flags=re.S)
    src = re.sub(r'//[^\n]*', ' ', src)
    src = re.sub(r'"(?:\\.|[^"\\])*"', '""', src)
    return src


def problems_in(path: str) -> list:
    with open(path, encoding='utf-8', errors='replace') as fh:
        raw = fh.read()

    entitled = set(JAVA_LANG)
    for imp in re.findall(r'^import\s+(?:static\s+)?([\w.]+)\s*;', raw, re.M):
        entitled.add(imp.rsplit('.', 1)[-1])
    if re.search(r'^import\s+[\w.]+\.\*\s*;', raw, re.M):
        return []  # a wildcard could supply anything
    for decl in re.findall(r'\b(?:class|interface|enum|record)\s+(\w+)', raw):
        entitled.add(decl)
    folder = os.path.dirname(path)
    try:
        for entry in os.listdir(folder):
            if entry.endswith('.java'):
                entitled.add(entry[:-5])
    except OSError:
        pass

    body = strip_noise(raw)
    body = re.sub(r'^\s*(?:package|import)[^;]*;', ' ', body, flags=re.M)

    out = []
    for m in TOKEN.finditer(body):
        name = m.group(1)
        if name in entitled:
            continue
        # Qualified by anything - a package or an outer class - is fine.
        if m.start() > 0 and body[m.start() - 1] == '.':
            continue
        out.append(f'{os.path.relpath(path)}: uses {name} but never imports it')
    return out


def main() -> int:
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        '..', 'src', 'main', 'java')
    problems = []
    for folder, _dirs, files in os.walk(root):
        for f in sorted(files):
            if f.endswith('.java'):
                problems.extend(problems_in(os.path.join(folder, f)))
    problems = sorted(set(problems))

    here = os.path.dirname(os.path.abspath(__file__))
    known_path = os.path.join(here, 'precheck-known.txt')
    known = set()
    if os.path.exists(known_path):
        with open(known_path, encoding='utf-8') as fh:
            known = {l.strip() for l in fh if l.strip() and not l.startswith('#')}

    if os.environ.get('PRECHECK_BASELINE'):
        with open(known_path, 'w', encoding='utf-8') as fh:
            fh.write('# Unqualified types this checker cannot resolve but which\n')
            fh.write('# compile fine on CI - nested types inherited from a\n')
            fh.write('# supertype, mostly. Regenerate: PRECHECK_BASELINE=1\n')
            for p in problems:
                fh.write(p + '\n')
        print(f'baseline written: {len(problems)} known')
        return 0

    fresh = [p for p in problems if p not in known]
    for p in fresh:
        print(p)
    return 1 if fresh else 0


if __name__ == '__main__':
    sys.exit(main())
