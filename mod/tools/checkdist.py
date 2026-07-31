#!/usr/bin/env python3
"""
Dedicated-server safety check.

A NeoForge dedicated server has no net.minecraft.client classes on its
classpath at all. If a class the server loads carries a reference to one in a
position the JVM must resolve -- a superclass, an interface, a field type, a
method signature -- the server dies at class-load with NoClassDefFoundError,
usually before the world finishes loading.

So: find every one of our classes that names a client type anywhere in its
constant pool, then find which of those are reachable from classes the server
definitely loads. Anything in the sealed-off client package is expected and
fine; anything outside it is a crash waiting to happen.

Reads the built jar, so it checks the artifact that actually ships.
"""
import struct
import sys
import zipfile
from collections import defaultdict, deque

JAR = sys.argv[1]
OURS = "com/jrpetty/mobtrumps/"
CLIENT_PKG = OURS + "client/"
CLIENT_NS = ("net/minecraft/client", "com/mojang/blaze3d")


def utf8s(data):
    """Every Utf8 constant in a class file: class names and descriptors alike."""
    count = struct.unpack_from(">H", data, 8)[0]
    out, refs, i, pos = [], [], 1, 10
    while i < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            n = struct.unpack_from(">H", data, pos)[0]
            out.append(data[pos + 2:pos + 2 + n].decode("utf-8", "replace"))
            pos += 2 + n
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 4
        elif tag in (5, 6):
            pos += 8
            i += 1                      # longs and doubles eat two slots
        elif tag in (7, 8, 16, 19, 20):
            refs.append(struct.unpack_from(">H", data, pos)[0])
            pos += 2
        elif tag == 15:
            pos += 3
        else:
            raise ValueError("unknown constant tag %d at %d" % (tag, pos - 1))
        i += 1
    return out


def names(strings):
    """Internal class names mentioned, from bare names and from descriptors."""
    found = set()
    for s in strings:
        if "/" not in s:
            continue
        if s.startswith("(") or ";" in s or s.startswith("["):
            part = ""
            for ch in s:
                if ch == "L":
                    part = ""
                elif ch == ";":
                    if "/" in part:
                        found.add(part)
                    part = ""
                elif part is not None:
                    part += ch
        else:
            found.add(s.lstrip("["))
    return found


classes, touches_client, edges = {}, {}, defaultdict(set)
with zipfile.ZipFile(JAR) as z:
    for entry in z.namelist():
        if not entry.endswith(".class") or not entry.startswith(OURS):
            continue
        name = entry[:-6]
        strings = utf8s(z.read(entry))
        mentioned = names(strings)
        classes[name] = mentioned
        touches_client[name] = sorted(
            m for m in mentioned if m.startswith(CLIENT_NS))
        for m in mentioned:
            if m.startswith(OURS) and m != name:
                edges[name].add(m)

# Which of ours name a client type directly?
direct = {c: v for c, v in touches_client.items() if v}
leaks = {c: v for c, v in direct.items() if not c.startswith(CLIENT_PKG)}

print("classes in jar under %s: %d" % (OURS, len(classes)))
print("in the client package:   %d" % sum(
    1 for c in classes if c.startswith(CLIENT_PKG)))
print("naming a client type:    %d" % len(direct))
print()

if not leaks:
    print("PASS  no class outside %s names a client type." % CLIENT_PKG)
else:
    print("FAIL  %d class(es) outside the client package name client types:"
          % len(leaks))
    for c in sorted(leaks):
        print("  %s" % c)
        for v in leaks[c][:6]:
            print("      -> %s" % v)

# Second question: can a server-loaded class reach the client package at all?
# Reachability is only a warning -- a guarded call through ClientHooks is the
# correct pattern and shows up here -- but an unexpected name is worth a look.
reach = defaultdict(set)
for root in classes:
    if root.startswith(CLIENT_PKG):
        continue
    for target in edges[root]:
        if target.startswith(CLIENT_PKG):
            reach[target].add(root)

print()
print("server-side classes that mention a client-package class:")
if not reach:
    print("  (none)")
for target in sorted(reach):
    print("  %-58s <- %s" % (target.split("/")[-1],
                             ", ".join(sorted(r.split("/")[-1] for r in reach[target]))))

sys.exit(1 if leaks else 0)
