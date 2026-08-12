"""Walk the maze's fixed door calendar exactly as MazeDoors builds it, and
prove the one promise that matters: EVERY day, all four Glade doors reach the
portal.

The calendar depends on the day number and nothing else - day three of this
game is day three of every game - and each midnight only a handful of doors
flip. Because the whole schedule is one deterministic sequence, this script is
not a statistical argument but an exhaustive check of the exact days that will
ship: it replicates the algorithm bit for bit (Deco.hash's 32-bit overflow,
the sorted toggle order, the flip selection, the retry ladder), so what passes
here is what runs in game.
"""
import json
from collections import deque

GRID, GLADE_MIN, GLADE_MAX = 96, 40, 55
DOORS = [(48, 39), (56, 48), (47, 56), (39, 47)]
DEAD_X, DEAD_Z, DEAD_SPAN = 16, 70, 10
FLIP_COUNT, RETRIES, CAL = 8, 24, 0xCD4
M32 = 0xFFFFFFFF

d = json.load(open('src/main/resources/data/aztecabyss/maze/maze_config_v2.json'))
base_edges = set(d['baseOpenEdges'])
tps = d['togglePoints']
exit_ids = sorted(d['exits'].keys())          # PortalAnnex.EXIT_IDS order
exits = d['exits']
layouts = d['layouts']


def i32(v):
    v &= M32
    return v - (1 << 32) if v >= (1 << 31) else v


def deco_hash(x, y, z, salt):
    h = i32(x * 374761393 + y * 668265263 + z * 1274126177 + salt * 1013904223)
    h = i32((h ^ ((h & M32) >> 13)) * 1274126177)
    return i32(h ^ ((h & M32) >> 16))


def in_glade(x, z):
    return GLADE_MIN <= x <= GLADE_MAX and GLADE_MIN <= z <= GLADE_MAX


def in_camp(x, z):
    return DEAD_X <= x < DEAD_X + DEAD_SPAN and DEAD_Z <= z < DEAD_Z + DEAD_SPAN


def frozen(tid):
    tp = tps.get(tid)
    if tp is None:
        return True
    for end in tp['edge'].split('>'):
        x, z = map(int, end.split(','))
        if in_camp(x, z):
            return True
    return False


SORTED_IDS = sorted(tps.keys())
BASE_STATE = frozenset(t for t in layouts[0]['open'] if not frozen(t))
# MazeDoors.ATLAS_NAME: the authored layout the ladder falls back to. It must
# reach ALL seven portals with the camp solid, or the whole ladder is built on
# sand - asserted below, so a dataset change that breaks it fails loudly here.
ATLAS_NAME = 'day_6'
ATLAS_STATE = frozenset(
    t for lay in layouts if lay.get('name') == ATLAS_NAME
    for t in lay['open'] if not frozen(t))
assert ATLAS_STATE, f"atlas layout {ATLAS_NAME!r} missing from dataset"


def exit_for(day):
    h = deco_hash(CAL, day, 0x0E17, 0x5EED) & 0x7FFFFFFF
    return exit_ids[h % len(exit_ids)]


def flipped(from_state, day, salt):
    want = FLIP_COUNT
    nxt = set(from_state)
    chosen = set()
    i = 0
    while len(chosen) < want and i < want * 6:
        tid = SORTED_IDS[(deco_hash(CAL, day, salt, i) & 0x7FFFFFFF) % len(SORTED_IDS)]
        i += 1
        if frozen(tid) or tid in chosen:
            continue
        chosen.add(tid)
        if tid in nxt:
            nxt.remove(tid)
        else:
            nxt.add(tid)
    return frozenset(nxt)


def is_open(ax, az, bx, bz, state):
    a, b = f"{ax},{az}>{bx},{bz}", f"{bx},{bz}>{ax},{az}"
    if a in base_edges or b in base_edges:
        return True
    for tid in state:                         # mirrors MazeData.isOpen's scan
        edge = tps[tid]['edge']
        if edge == a or edge == b:
            return True
    return False


def solvable(state, exit_id):
    """MazeData.exitReachable from each door: BFS with the Glade skipped and
    the Dead Glade treated as SOLID - its wall ring physically seals those
    cells bar a few one-block breaches, so a route that only exists through
    the camp is not a route."""
    target = tuple(exits[exit_id]['cell'])
    # Precompute open-edge lookup for speed (semantically identical) —
    # BOTH directions of every edge, base and toggled alike.
    open_edges = set()
    for e in base_edges:
        a, b = e.split('>')
        open_edges.add(e)
        open_edges.add(f"{b}>{a}")
    for tid in state:
        e = tps[tid]['edge']
        a, b = e.split('>')
        open_edges.add(e)
        open_edges.add(f"{b}>{a}")

    def open_(ax, az, bx, bz):
        return f"{ax},{az}>{bx},{bz}" in open_edges

    for door in DOORS:
        seen = {door}
        q = deque([door])
        ok = False
        while q:
            x, z = q.popleft()
            if (x, z) == target:
                ok = True
                break
            for dx, dz in ((0, -1), (0, 1), (-1, 0), (1, 0)):
                n = (x + dx, z + dz)
                if not (0 <= n[0] < GRID and 0 <= n[1] < GRID) or n in seen:
                    continue
                if in_glade(*n) or in_camp(*n) or not open_(x, z, n[0], n[1]):
                    continue
                seen.add(n)
                q.append(n)
        if not ok:
            return False
    return True


def breaches_enterable(state):
    """DeadGlade.breachesFor: at least one reachable breach candidate."""
    open_edges = set(base_edges)
    for tid in state:
        e = tps[tid]['edge']
        a, b = e.split('>')
        open_edges.add(e)
        open_edges.add(f"{b}>{a}")
    seen = set(DOORS)
    q = deque(DOORS)
    while q:
        x, z = q.popleft()
        for dx, dz in ((0, -1), (0, 1), (-1, 0), (1, 0)):
            n = (x + dx, z + dz)
            if not (0 <= n[0] < GRID and 0 <= n[1] < GRID) or n in seen:
                continue
            if in_glade(*n) or in_camp(*n):
                continue
            if f"{x},{z}>{n[0]},{n[1]}" not in open_edges \
                    and f"{n[0]},{n[1]}>{x},{z}" not in open_edges:
                continue
            seen.add(n)
            q.append(n)
    for k in range(DEAD_SPAN):
        for out in ((DEAD_X - 1, DEAD_Z + k), (DEAD_X + DEAD_SPAN, DEAD_Z + k),
                    (DEAD_X + k, DEAD_Z - 1), (DEAD_X + k, DEAD_Z + DEAD_SPAN)):
            if out in seen:
                return True
    return False


# ---------------------------------------------------------------------------
print("=" * 76)
print("THE DOOR CALENDAR — every shipping day, walked exhaustively")
print("=" * 76)

for _e in exit_ids:
    assert solvable(ATLAS_STATE, _e), \
        f"atlas {ATLAS_NAME} cannot reach {_e} with the camp solid"
assert breaches_enterable(ATLAS_STATE), f"atlas {ATLAS_NAME} seals the camp"
print(f"atlas ({ATLAS_NAME}) reaches all {len(exit_ids)} portals camp-solid : OK")

# The game's deadline is day 8; the horizon leaves generous room beyond it.
HORIZON = 30
held = based = carve_needed = 0
flip_sizes = []
exit_seen = {}
camp_ok = True
rows = []


def acceptable(state, exit_id):
    """MazeDoors.acceptable: portal reachable from every door, camp open."""
    return solvable(state, exit_id) and breaches_enterable(state)


def from_atlas(day, exit_id, last_resort):
    """MazeDoors.fromAtlas: salted flips of the atlas, then the atlas itself."""
    for salt in range(RETRIES):
        cand = flipped(ATLAS_STATE, day, RETRIES + salt)
        if acceptable(cand, exit_id):
            return cand, False
    if acceptable(ATLAS_STATE, exit_id):
        return ATLAS_STATE, False
    return last_resort, True


state = None
for day in range(HORIZON):
    exit_id = exit_for(day)
    exit_seen[exit_id] = exit_seen.get(exit_id, 0) + 1
    how = "flip"
    if day == 0:
        # Day zero validated like any other: base, else flips of base, else
        # the atlas (the layout proven good for all seven portals).
        adopted = None
        if acceptable(BASE_STATE, exit_id):
            adopted = BASE_STATE
            how = "base"
        for salt in range(RETRIES):
            if adopted is not None:
                break
            cand = flipped(BASE_STATE, 0, salt)
            if acceptable(cand, exit_id):
                adopted = cand
        if adopted is None:
            adopted, carved = from_atlas(0, exit_id, BASE_STATE)
            how = "CARVE" if carved else "atlas"
            if carved:
                carve_needed += 1
            else:
                based += 1
        moved = len(adopted ^ BASE_STATE)
    else:
        adopted = None
        for salt in range(RETRIES):
            cand = flipped(state, day, salt)
            if acceptable(cand, exit_id):
                adopted = cand
                break
        if adopted is None and acceptable(state, exit_id):
            adopted = state
            held += 1
            how = "hold"
        if adopted is None:
            adopted, carved = from_atlas(day, exit_id,
                                         flipped(state, day, 0))
            how = "CARVE" if carved else "atlas"
            if carved:
                carve_needed += 1
            else:
                based += 1
        moved = len(adopted ^ state)
        flip_sizes.append(moved)
    assert acceptable(adopted, exit_id) or carve_needed, f"UNSOLVABLE day {day}"
    if not breaches_enterable(adopted):
        camp_ok = False
        print(f"  !! camp sealed on day {day}")
    rows.append((day, exit_id.split("_")[1], moved, how, len(adopted)))
    state = adopted

print()
print("day | portal | doors moved | via   | doors open")
for day, ex, moved, how, n_open in rows:
    mark = "  <- deadline week" if day == 8 else ""
    print(f"{day:3} | {ex:6} | {moved:11} | {how:5} | {n_open}{mark}")
print()
print(f"days in the calendar            : {HORIZON}")
print(f"doors held still (fallback 2)   : {held}")
print(f"fell back to the atlas (rung 3) : {based}  <- must be 0 inside the horizon")
print(f"needed the physical carve       : {carve_needed}  <- must be 0")
if flip_sizes:
    print(f"doors moved per night           : min {min(flip_sizes)}"
          f" avg {sum(flip_sizes)/len(flip_sizes):.1f} max {max(flip_sizes)}"
          f"  (of {len(SORTED_IDS)} toggles)")
print(f"portal distribution             : "
      + "  ".join(f"{k.split('_')[1]}:{v}" for k, v in sorted(exit_seen.items())))
repeats = sum(1 for day in range(1, HORIZON)
              if exit_for(day) == exit_for(day - 1))
print(f"portal stayed put next morning  : {repeats} times  (allowed, on purpose)")
print(f"Dead Glade enterable every day  : {camp_ok}")
window = {exit_for(d) for d in range(9)}
print(f"all 7 portals inside the game   : {len(window) == len(exit_ids)}"
      f"  (days 0-8 cover {len(window)})")
print()
ok = (carve_needed == 0 and based == 0 and camp_ok
      and len(window) == len(exit_ids) and rows[0][3] == "base")
print("VERDICT:", "PASS — a route stands every single day, on small flips alone"
      if ok else "** FAIL **")
