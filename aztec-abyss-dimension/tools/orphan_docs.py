"""Comment blocks that document something other than what follows them.

A javadoc sitting directly above another javadoc, with no declaration between
them, is always a mistake here: the first one describes a member that has moved,
been renamed or been replaced, and it is now attached to whatever happened to end
up underneath it. That is worse than no comment at all, because this codebase's
whole style is that the prose explains the why - so a reader has no reason to
distrust it.

Twenty-four of these had accumulated before anything looked for them, in every
package. They cost nothing at runtime and everything in trust, and they are
invisible to javac, to the linter and to a diff of the change that created them:
the orphan is only wrong relative to a line somewhere else that moved.

    usage: tools/orphan_docs.py     (report, exit 1 if any)
"""

import os
import sys


def orphans_in(path: str) -> list:
    with open(path, encoding='utf-8') as fh:
        lines = fh.read().split('\n')

    out = []
    i = 0
    while i < len(lines):
        if not lines[i].lstrip().startswith('/*'):
            i += 1
            continue
        end = i
        while end < len(lines) and '*/' not in lines[end]:
            end += 1
        after = end + 1
        while after < len(lines) and not lines[after].strip():
            after += 1
        if after < len(lines) and lines[after].lstrip().startswith('/*'):
            out.append(f'{os.path.relpath(path)}:{i + 1}: comment block documents'
                       f' nothing - another block follows it at line {after + 1}')
        i = end + 1
    return out


def main() -> int:
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        '..', 'src', 'main', 'java')
    found = []
    for folder, _dirs, files in os.walk(root):
        for f in sorted(files):
            if f.endswith('.java'):
                found.extend(orphans_in(os.path.join(folder, f)))
    for line in found:
        print(line)
    return 1 if found else 0


if __name__ == '__main__':
    sys.exit(main())
