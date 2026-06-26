# Structural Integrity (NeoForge, Minecraft 1.21.x)

A **lightweight, deterministic structural-integrity gameplay system**. Player-built blocks must
trace a valid load path back to natural ground or a foundation, within their material's span, or
they fall. Pillars, buttresses and arches become meaningful.

This is a **building-rule system, not a physics simulator.** It deliberately favors *gameplay
clarity over realism*, *predictability over simulation*, and *local logic over global collapse*.

---

## TL;DR for players

- Natural terrain is always stable. Only blocks **you place** are checked.
- Each material can cantilever only so far from a support before it falls:

  | Material | Max unsupported span |
  |---|---|
  | Dirt / gravel | 1 |
  | Generic full block | 2 |
  | Wood (logs, planks) | 4 |
  | Stone (stone, brick, deepslate…) | 7 |
  | Reinforced (concrete, Reinforced Beam) | 12 |
  | Metal (iron/netherite/copper, Heavy Girder) | 20 |

- Build **pillars** under long floors, **buttresses** against walls, **arches** across gaps.
- A roof carried by two walls survives losing one wall, as long as the other is within span.
- Overreach falls as a normal vanilla **falling block** — it drops, lands, and piles up. No
  debris, no ragdolls, no chunk-wide cascades.
- Place the **Foundation Anchor** to root a structure anywhere (e.g. a bridge pier on a riverbed).

---

## What this is *not* (explicit non-goals)

No ragdolls. No visual physics debris. No randomness (the result is a pure function of the
blocks). No Newtonian forces or weight. No fluid dynamics. **No chunk-wide cascade destruction
unless it is genuinely unsupported.** Collapse is always the *local* set of blocks that truly
lost their load path — never "the whole connected component."

---

## 1. Core architecture

Two layers with a hard boundary between them:

```
dev.structint.core      ── pure Java, ZERO Minecraft imports. The algorithm.
        │   PackedPos          (x,y,z) <-> long, identical layout to BlockPos.asLong()
        │   CellRole           EMPTY | ANCHOR | STRUCTURAL
        │   Material            id + maxSpan
        │   GridAccess          the solver's entire view of the world (2 methods)
        │   SupportSolver       max-reach Dijkstra → which cells are stable
        │   StructuralEngine    flood a local region + solve it → which cells must fall
        │
        └── unit-tested with an in-memory grid, no game required
            (CoreSelfTest = dependency-free runner, SupportSolverTest = JUnit)

dev.structint (+ .world/.event/.registry)  ── the NeoForge integration
            Config                 every span & budget, server-config driven
            ModBlocks/ModContent   Foundation Anchor, Reinforced Beam, Heavy Girder
            ManagedBlocks          per-chunk set of player-placed positions (data attachment)
            BlockClassifier        BlockState → role / span, via data tags
            LevelGridAccess        implements GridAccess over a live ServerLevel
            StructuralManager      receives edits, schedules checks, drains collapses
            StructuralEventHandler place / break / level-tick / unload hooks
```

The boundary is the whole point: the structural rules are a pure, testable function of a tiny
`GridAccess` interface. The Minecraft side only has to answer *"what role does this coordinate
play, and what's its span?"*

---

## 2. Data structures for tracking support

**`ManagedBlocks` (per-chunk attachment).** A `LongOpenHashSet` of packed positions that a player
placed. It is the single source of truth for natural-vs-player:

> *Any full solid block that is **not** in its chunk's managed set is natural terrain → an anchor.*

Because it is a **chunk `AttachmentType`**, it serializes and unloads with the chunk for free —
no global save data, nothing to scan. This is what keeps the system chunk-local.

**`LevelStructuralState` (per-level work queues).** Two FIFO queues, each with a dedupe set:
- `pendingChanges` — positions of edits awaiting a stability check;
- `collapseQueue` — positions already proven unsupported, awaiting their fall.

Both are drained under a per-tick budget so one huge edit can never stall the server.

**Packed `long` keys everywhere.** `PackedPos` uses the exact bit layout of vanilla
`BlockPos.asLong()` (26/12/26), so the same key flows through the chunk attachment, the work
queues, and the pure solver with zero translation.

---

## 3. Block update algorithm (placement + removal)

The check is deferred to the next level tick (on a *break*, the block is still present during the
event; deferring lets us re-evaluate a settled world and batch bursts of edits).

**On place:**
1. If it's a Foundation → clear managed (it's an anchor). Else if it's a full solid block → mark
   it managed.
2. Enqueue its position as a pending change. (Placing usually *adds* support; the one case that
   matters is a block placed too far out — it must immediately fall.)

**On break (or programmatic collapse):**
1. Clear the managed flag at the position.
2. Enqueue the position as a pending change so whatever it was holding up gets re-checked.

**Per tick, per level:**
1. *Change phase* (budgeted): for each pending origin, **flood** the connected structural cluster
   and **solve** it once; add every unsupported cell to the collapse queue.
2. *Collapse phase* (budgeted): pop cells, re-verify against the now-current world (so a player
   who quickly props something up isn't punished), and turn each survivor-of-re-check into a
   vanilla `FallingBlockEntity`.

**Key correctness property — one solve is complete.** The solver only relays support *through
supported blocks*, so an unsupported block can never be holding anything else up. A single solve
therefore returns the *entire* set of blocks that lose support from an edit. Collapsing them can
never destabilize a block the solve judged stable, so we can stagger the known doomed set across
ticks (gradual, local) **without** re-flooding after every block — no cascade bookkeeping, no
risk of runaway propagation.

---

## 4. The stability model (pseudocode)

Each block carries one number: **remaining reach** — how many more blocks of horizontal
cantilever it can still support. A block is **stable iff some path gives it reach ≥ 0.**

```
reach(anchor)            = CAP            // natural terrain & foundations: effectively infinite
support flows along two edge types, from any already-supported cell S with reach r:

  vertical (cell directly ABOVE S):       reach = span(above)        // resting on a column → full reset
  horizontal (4 cells beside S):          reach = min(r, span(side)) - 1   // cantilever, costs 1 per step

  (there is deliberately NO downward edge: nothing is held up by the block above it)

a cell is stable  ⇔  best reach over ALL paths ≥ 0
```

Computed as a **max-reach Dijkstra** (always finalize the highest-reach frontier cell first):

```
solve(region):
    best = {}                                  # cell -> highest proven reach
    pq   = max-heap by reach
    for cell in region:                        # seed: anchors bordering the region
        for n in 6-neighbours(cell):
            if role(n) == ANCHOR and n not in best:
                best[n] = CAP; pq.push(n, CAP)

    while pq:
        (p, r) = pq.pop_max()
        if r < best[p]: continue               # stale
        up = p + (0,1,0)
        if role(up) == STRUCTURAL: relax(up, span(up))          # vertical reset
        if r > 0:
            for h in 4 horizontal neighbours of p:
                if role(h) == STRUCTURAL:
                    c = min(r, span(h)) - 1
                    if c >= 0: relax(h, c)                       # cantilever
    return { c in region : best[c] >= 0 }
```

Why this satisfies the design:
- **Calibrated spans.** A material with span `S` cantilevers *exactly* `S` blocks from a support
  (dirt 1, wood 4, stone 7…). Verified by tests.
- **Multi-path / redundancy.** `best` is the *maximum* reach over all paths. A roof is supported
  from whichever wall is nearer; remove one wall and the near half still stands. Removing an
  intermediate block never collapses a structure that still has another valid load path. This is
  explicitly **not** single-path connectivity failure.
- **Vertical load & pillars.** The vertical edge resets reach to full, so a column of any height
  transfers load straight to the ground.
- **Deterministic.** The result is the unique fixed point of a monotone relaxation — independent
  of iteration order, tie-breaks, or which block was touched.

---

## 5. Performance strategy

- **Event-driven, never polled.** Work happens only on placement/removal, never on a schedule and
  never as a world scan.
- **Local flood, hard node cap.** Each check floods *only* the touched structural cluster (anchors
  and air wall it off). `maxRegionNodes` (default 4096) bounds per-edit CPU. A structure larger
  than the cap is treated as **stable** (never mass-collapsed) — a deliberate fail-safe.
- **Per-tick budgets.** `changeBudgetPerTick` and `collapseBudgetPerTick` cap work per level per
  tick, so a 10 000-block demolition spreads smoothly instead of spiking.
- **Chunk-local, self-cleaning state.** Support tracking lives in chunk attachments; per-level
  queues are dropped when idle and on unload. No global structures.
- **One solve is complete (see §3),** so collapse staggering needs no re-flooding.
- **Unloaded chunks = anchors.** The system never chases work into chunks that aren't in memory; a
  structure running off the loaded edge is considered supported there.
- **Per-pass role/state cache.** `LevelGridAccess` memoizes each coordinate's role and state for
  the duration of a single flood+solve, so repeated visits cost one classification.

Complexity per edit: `O(region · log region)`, with `region ≤ maxRegionNodes`.

---

## 6. NeoForge implementation approach (events, hooks, registration)

- **Events** (`@EventBusSubscriber`, game bus, server-side only):
  - `BlockEvent.EntityPlaceEvent` → `StructuralManager.onBlockPlaced`
  - `BlockEvent.BreakEvent` → `StructuralManager.onBlockRemoved`
  - `LevelTickEvent.Post` → `StructuralManager.tick` (drains the queues)
  - `LevelEvent.Unload` → drop per-level state
- **Data attachment** (`NeoForgeRegistries.Keys.ATTACHMENT_TYPES`): `ManagedBlocks` on chunks,
  with a `Codec` so it persists.
- **Registration** via `DeferredRegister` for blocks, items, creative tab, attachment type.
- **Config** via `ModConfigSpec` (SERVER type): all spans and budgets.
- **Classification via data tags** (`structint:structural_wood`, `…_stone`, `…_reinforced`,
  `…_metal`, `…_dirt`, `foundations`, `exempt`) so packs can reclassify without code.
- **Collapse via vanilla** `FallingBlockEntity.fall(...)` — reuses the gravity Minecraft already
  ships. No custom physics.

---

## 7. Build & test

Requires JDK 21.

```bash
# Full mod build + JUnit tests (downloads NeoForge + Minecraft on first run)
gradle build            # or ./gradlew build after `gradle wrapper`

# The pure structural core needs no Minecraft and runs in seconds:
javac -d out $(find src/main/java/dev/structint/core -name '*.java') \
             src/test/java/dev/structint/core/CoreSelfTest.java
java  -cp out dev.structint.core.CoreSelfTest
```

`CoreSelfTest` builds toy worlds (cantilevers, pillars, redundant roofs, arches, weak links,
local-tail collapse, the over-budget fail-safe) and asserts the verdicts. The same scenarios run
under `gradle test` via `SupportSolverTest`.

> Pin `neoforge_version` in `gradle.properties` to a build that matches your target Minecraft
> 1.21.x; the default targets 1.21.1.

### In-game manual test plan
1. Pillar of stone to y=40 — stays up.
2. Cantilever planks out from a wall — the 5th block falls (wood span 4).
3. Same in stone — survives to 7, the 8th falls.
4. Two stone walls 14 apart with a flat roof — stable; mine one wall and only the far half falls.
5. Dig the natural ground out from under a building — only blocks that lose their load path drop.
6. Place a Foundation Anchor on a riverbed and build a tower on it — rooted and stable.

---

## 8. Tuning

Everything lives in the server config (`world/serverconfig/structint-server.toml`):
material spans, the support cap, `maxRegionNodes`, and the two per-tick budgets. Set
`enableCollapse=false` to track integrity without dropping blocks (e.g. on a creative server), or
`onlyPlayerPlaced=false` to subject every full block to the rules.

## 9. Known boundaries / future work

- Removal hooks cover player place/break (the primary design driver) and programmatic collapse.
  Piston pushes, explosions, and fluid-driven removals are not yet wired (easy follow-ups: hook
  the corresponding NeoForge events and enqueue the affected positions).
- Weight/load accumulation is intentionally absent — span is the single, predictable knob.
