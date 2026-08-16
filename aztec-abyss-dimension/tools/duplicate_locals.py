"""Local variables declared twice in one scope - found from the source.

This is precheck's oldest blind spot, and it cost a CI cycle again. Without
Minecraft on the classpath javac never attributes a single method body, so it
never reaches the check that would say "variable portal is already defined".
The whole of precheck.sh sees a clean run; CI sees a compile error four minutes
later. Confirmed rather than assumed: a full javac pass over this source with
the offending code in place produced 3,806 errors and not one of them was the
duplicate.

Java's rule is simple enough to check without a parser. A local is visible from
its declaration to the end of the block that declares it, and Java forbids
re-declaring a name that is already visible - including from an enclosing block,
which is why this cannot be done by looking at one brace level. So: walk the
method body, push a scope on '{' and pop it on '}', and complain when a
declaration names something already on the stack. Two sibling blocks each
declaring 'i' are fine, because the first scope popped before the second opened.

Deliberately conservative about what counts as a declaration - a capitalised or
primitive type followed by a name followed by '=' or ';', plus the for-each and
catch forms. It would rather miss a declaration than invent one, because a false
positive here blocks a commit for no reason. Validated by running it over the
whole tree (zero findings) and by re-introducing the real bug (found).

    usage: tools/duplicate_locals.py     (report, exit 1 if any)
"""

import os
import re
import sys

PRIMITIVES = {'int', 'long', 'short', 'byte', 'char', 'float', 'double', 'boolean', 'var'}

# A type is a primitive or something capitalised, optionally generic or an array.
TYPE = r'(?:[A-Z][\w.]*(?:\s*<[^;{}()]*?>)?|int|long|short|byte|char|float|double|boolean|var)(?:\s*\[\s*\])*'

# "Type name =" or "Type name;" at a statement position.
DECL = re.compile(r'(?:^|[;{}])\s*(?:final\s+)?(?P<type>' + TYPE + r')\s+(?P<name>[a-z_$]\w*)\s*(?==[^=]|;)')

# "for (Type name : xs)" and "catch (Type name)".
BOUND = re.compile(r'\b(?:for|catch)\s*\(\s*(?:final\s+)?(?:' + TYPE
                   + r'(?:\s*\|\s*' + TYPE + r')*)\s+(?P<name>[a-z_$]\w*)\s*[:)]')

# Headers whose declarations scope to the block that follows, not the one around it.
HEADER = re.compile(r'\b(?:for|catch|try)\s*\(')

# Anything that introduces its own parameter scope we simply do not track.
SKIP_WORDS = {'return', 'new', 'else', 'case', 'default', 'assert', 'throw', 'yield'}


def strip_noise(src: str) -> str:
    """Blanks comments, strings and chars, preserving newlines and length."""
    out = list(src)
    i = 0
    n = len(src)
    while i < n:
        two = src[i:i + 2]
        if two == '/*':
            j = src.find('*/', i + 2)
            j = n if j < 0 else j + 2
            for k in range(i, j):
                if out[k] != '\n':
                    out[k] = ' '
            i = j
        elif two == '//':
            j = src.find('\n', i)
            j = n if j < 0 else j
            for k in range(i, j):
                out[k] = ' '
            i = j
        elif src[i] in '"\'':
            quote = src[i]
            j = i + 1
            while j < n and src[j] != quote:
                j += 2 if src[j] == '\\' else 1
            j = min(j + 1, n)
            for k in range(i, j):
                if out[k] != '\n':
                    out[k] = ' '
            i = j
        else:
            i += 1
    return ''.join(out)


def method_bodies(src: str):
    """Yields (start, end) of each method body, by brace matching after a ')'."""
    for m in re.finditer(r'\)\s*(?:throws\s+[\w.,\s]+?)?\{', src):
        start = src.index('{', m.start())
        depth = 0
        i = start
        while i < len(src):
            if src[i] == '{':
                depth += 1
            elif src[i] == '}':
                depth -= 1
                if depth == 0:
                    yield start, i
                    break
            i += 1


def duplicates_in(path: str) -> list:
    with open(path, encoding='utf-8') as fh:
        raw = fh.read()
    src = strip_noise(raw)

    out = []
    seen_bodies = []
    for start, end in method_bodies(src):
        # Nested bodies (lambdas, anonymous classes) are covered by the outer
        # walk already; skip anything wholly inside a body we have done.
        if any(s < start < e for s, e in seen_bodies):
            continue
        seen_bodies.append((start, end))

        scopes = [{}]
        # Names declared in a for/catch/try header belong to the block that
        # follows, not to the block containing it. Without this every second
        # "for (ServerPlayer p : ...)" in a method looks like a redeclaration,
        # which is twenty-nine false alarms on this source alone.
        pending = {}
        i = start
        while i <= end:
            ch = src[i]
            if ch == '{':
                scopes.append(pending)
                pending = {}
                i += 1
                continue
            if ch == '}':
                if len(scopes) > 1:
                    scopes.pop()
                i += 1
                continue

            header = HEADER.match(src, i)
            if header is not None:
                open_paren = src.index('(', i)
                depth = 0
                j = open_paren
                while j <= end:
                    if src[j] == '(':
                        depth += 1
                    elif src[j] == ')':
                        depth -= 1
                        if depth == 0:
                            break
                    j += 1
                inside = src[open_paren:j + 1]
                for m in list(DECL.finditer(inside)) + list(BOUND.finditer(inside)):
                    pending[m.group('name')] = open_paren + m.start('name')
                i = j + 1
                continue

            hit = DECL.match(src, i) or BOUND.match(src, i)
            if hit is not None:
                name = hit.group('name')
                gtype = hit.groupdict().get('type') or ''
                if name not in SKIP_WORDS and gtype.strip() not in SKIP_WORDS:
                    for scope in scopes:
                        if name in scope:
                            line = raw[:i].count('\n') + 1
                            first = raw[:scope[name]].count('\n') + 1
                            out.append(
                                f'{os.path.relpath(path)}:{line}: local "{name}" is already '
                                f'defined at line {first} and still in scope')
                            break
                    else:
                        scopes[-1][name] = hit.start('name')
                i = hit.end()
                continue
            i += 1
    return out


def main() -> int:
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        '..', 'src', 'main', 'java')
    found = []
    for folder, _dirs, files in os.walk(root):
        for f in sorted(files):
            if f.endswith('.java'):
                found.extend(duplicates_in(os.path.join(folder, f)))
    for line in found:
        print(line)
    return 1 if found else 0


if __name__ == '__main__':
    sys.exit(main())
