"""Verify all seven maze presets the way the game actually builds them.

Two sources of connectivity, and they matter separately:
  1. the dataset  - baseOpenEdges plus the day's toggle points
  2. carveRoute   - four corridors cut from the Glade doors to the day's exit,
                    at build time, by construction

carveRoute is the load-bearing one: it has three `break` statements that abandon
the walk, and nothing checks whether it arrived. This replicates it bit for bit,
including Deco.hash's 32-bit overflow and String.hashCode, and reports whether
each route actually lands on the exit.
"""
import json
from collections import deque

GRID, GLADE_MIN, GLADE_MAX = 96, 40, 55
DOOR_CELLS = [(48, 39), (56, 48), (47, 56), (39, 47)]

d = json.load(open('src/main/resources/data/aztecabyss/maze/maze_config_v2.json'))
base, tps, exits, layouts = set(d['baseOpenEdges']), d['togglePoints'], d['exits'], d['layouts']

M32 = 0xFFFFFFFF


def i32(v):
    v &= M32
    return v - (1 << 32) if v >= (1 << 31) else v


def jhash(x, y, z, salt):
    h = i32(x * 374761393 + y * 668265263 + z * 1274126177 + salt * 1013904223)
    h = i32((h ^ ((h & M32) >> 13)) * 1274126177)
    return i32(h ^ ((h & M32) >> 16))


def hash_unit(x, y, z, salt):
    return ((jhash(x, y, z, salt) & M32) >> 8) / float(1 << 24)


def str_hash(s):
    h = 0
    for ch in s:
        h = i32(31 * h + ord(ch))
    return h


def in_glade(x, z):
    return GLADE_MIN <= x <= GLADE_MAX and GLADE_MIN <= z <= GLADE_MAX


def sign(v):
    return (v > 0) - (v < 0)


def carve_route(fx, fz, tx, tz, salt):
    """Exactly MazeBuilder.carveRoute. Returns (edges opened, final cell, why it stopped)."""
    edges, cx, cz, guard = set(), fx, fz, 0
    while (cx != tx or cz != tz) and guard < GRID * 4:
        guard += 1
        dx, dz = sign(tx - cx), sign(tz - cz)
        if dx == 0:
            step_x = False
        elif dz == 0:
            step_x = True
        else:
            step_x = hash_unit(cx, salt, cz, salt) < 0.5
        nx, nz = (cx + dx, cz) if step_x else (cx, cz + dz)
        if nx < 0 or nz < 0 or nx >= GRID or nz >= GRID:
            return edges, (cx, cz), "hit the rim"
        if in_glade(nx, nz):
            if step_x and dz != 0:
                nx, nz = cx, cz + dz
            elif not step_x and dx != 0:
                nx, nz = cx + dx, cz
            else:
                return edges, (cx, cz), "boxed in by the Glade"
            if in_glade(nx, nz):
                return edges, (cx, cz), "sidestep still in the Glade"
        edges.add(((cx, cz), (nx, nz)))
        edges.add(((nx, nz), (cx, cz)))
        cx, cz = nx, nz
    if cx == tx and cz == tz:
        return edges, (cx, cz), "arrived"
    return edges, (cx, cz), "ran out of guard"


def opened_for(lay):
    out = set()
    for tid in lay['open']:
        tp = tps.get(tid)
        if tp:
            out.add(tp['edge'])
            a, b = tp['edge'].split('>')
            out.add(f"{b}>{a}")
    return out


def is_open(ax, az, bx, bz, opened, carved):
    if ((ax, az), (bx, bz)) in carved:
        return True
    a, b = f"{ax},{az}>{bx},{bz}", f"{bx},{bz}>{ax},{az}"
    return a in base or b in base or a in opened or b in opened


def reach(opened, carved, starts=None):
    starts = starts or DOOR_CELLS
    dist = {c: 0 for c in starts}
    q = deque(starts)
    while q:
        x, z = q.popleft()
        for dx, dz in ((0, -1), (0, 1), (-1, 0), (1, 0)):
            nx, nz = x + dx, z + dz
            if not (0 <= nx < GRID and 0 <= nz < GRID) or (nx, nz) in dist:
                continue
            if in_glade(nx, nz) or not is_open(x, z, nx, nz, opened, carved):
                continue
            dist[(nx, nz)] = dist[(x, z)] + 1
            q.append((nx, nz))
    return dist


print("=" * 76)
print("PART 1 — from EACH DOOR, does the dataset alone reach the exit?")
print("        (this is exactly the check MazeData.exitReachable makes)")
print("=" * 76)
print(f"{'preset':8} {'exit':7} {'cell':>10}  {'door1':>6} {'door2':>6} {'door3':>6} {'door4':>6}")
dataset_ok = []
for lay in layouts:
    opened = opened_for(lay)
    ex = exits[lay['exit']]
    t = (ex['cell'][0], ex['cell'][1])
    row = f"{lay['name']:8} {lay['exit']:7} {str(t):>10}  "
    for door in DOOR_CELLS:
        ok = t in reach(opened, set(), starts=[door])
        dataset_ok.append(ok)
        row += f"{'YES' if ok else '**NO**':>6} "
    print(row)

print()
print("=" * 76)
print("PART 2 — does carveRoute actually ARRIVE? (four doors x seven presets)")
print("=" * 76)
all_arrived = True
for lay in layouts:
    ex = exits[lay['exit']]
    t = (ex['cell'][0], ex['cell'][1])
    base_salt = str_hash(lay['name'])
    line = f"{lay['name']:8} -> {str(t):>10}  "
    for i, door in enumerate(DOOR_CELLS, start=1):
        _, end, why = carve_route(door[0], door[1], t[0], t[1], i32(base_salt + i * 7919))
        good = why == "arrived"
        all_arrived &= good
        line += f"[{'OK' if good else why}] "
    print(line)

print()
print("=" * 76)
print("PART 3 — dataset + carving together (what a player actually walks)")
print("=" * 76)
print(f"{'preset':8} {'solved':>8} {'steps':>6} {'reachable':>10} {'perfect':>8}")
reach_sets, all_solved = {}, True
for lay in layouts:
    opened = opened_for(lay)
    ex = exits[lay['exit']]
    t = (ex['cell'][0], ex['cell'][1])
    base_salt = str_hash(lay['name'])
    carved = set()
    for i, door in enumerate(DOOR_CELLS, start=1):
        e, _, _ = carve_route(door[0], door[1], t[0], t[1], i32(base_salt + i * 7919))
        carved |= e
    dist = reach(opened, carved)
    ok = t in dist
    all_solved &= ok
    reach_sets[lay['name']] = (set(dist), dist)
    print(f"{lay['name']:8} {'YES' if ok else '** NO **':>8} {dist.get(t, -1):>6} "
          f"{len(dist):>10} {lay['perfectRouteBlocks']:>8}")

print()
print("=" * 76)
print("PART 4 — are they different enough to tell apart?")
print("=" * 76)
names = [l['name'] for l in layouts]
print("Percentage of walkable cells that differ between each pair:")
print(f"{'':8}" + "".join(f"{n:>8}" for n in names))
worst = 100.0
for a in names:
    row = f"{a:8}"
    for b in names:
        if a == b:
            row += f"{'-':>8}"
            continue
        ra, rb = reach_sets[a][0], reach_sets[b][0]
        pct = len(ra ^ rb) / len(ra | rb) * 100
        worst = min(worst, pct)
        row += f"{pct:>7.1f}%"
    print(row)

print()
print(f"Closest any two presets get: {worst:.1f}% of walkable cells differ")
print()
print("Distance from each door to that day's exit, in cells:")
print(f"{'preset':8}" + "".join(f"{'door' + str(i):>9}" for i in range(1, 5)))
for lay in layouts:
    ex = exits[lay['exit']]
    t = (ex['cell'][0], ex['cell'][1])
    _, dist = reach_sets[lay['name']]
    row = f"{lay['name']:8}"
    for door in DOOR_CELLS:
        sub = deque([door])
        row += f"{dist.get(t, -1):>9}"
    print(row)

print()
print("=" * 76)
print(f"every door reaches its exit unaided : {all(dataset_ok)}  <- if True, the carve fallback never fires")
print(f"every carved route arrives     : {all_arrived}")
print(f"combined solves all seven      : {all_solved}")
print("=" * 76)
