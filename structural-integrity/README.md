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

  **Every** block you place is subject to the rules — full blocks, slabs, stairs, walls, fences,
  panes, bars, chains, trapdoors, torches, carpets, plants, redstone, signs, everything. Wooden
  slabs/stairs use the wood span and other slabs/stairs the stone span; anything without a more
  specific material is "generic" (span 2). The only exclusions are air, fluids (water/lava), and
  blocks tagged `structint:exempt` (the small escape hatch, e.g. scaffolding).

- Every building material's item tooltip shows its **strength tier** (Very weak → Extreme) and
  **max unsupported span**, so you can judge a block before you build with it.
- The three custom blocks are **craftable** (Foundation Anchor, Reinforced Beam, Heavy Girder).
- Build **pillars** under long floors, **buttresses** against walls, **arches** across gaps.
- A roof carried by two walls survives losing one wall, as long as the other is within span.
- A failing block **creaks and sheds dust** for a moment (configurable delay, default 0.75s)
  before it drops — failures are seen and heard, not instant.
- Falling blocks **hurt what they land on**, scaled by material strength — dirt stings,
  stone lands like an anvil, a metal girder is worse. Configurable (`collapseImpactDamageScale`).
- **Snow load**: snow piled on a roof eats into the span it can carry — a maxed wood roof
  sheds its far edge in deep snow, while reinforced and metal shrug it off. Clear the snow
  (or roof in something stronger) and the full span returns.
- Overreach falls as a normal vanilla **falling block** — it drops, lands, and piles up. No
  debris, no ragdolls, no chunk-wide cascades.
- Localized in **11 languages** (EN, ES, FR, DE, IT, PT-BR, RU, ZH-CN, JA, KO, PL).
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

> *Any participating block that is **not** in its chunk's managed set is natural terrain → an anchor.*

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
1. If it's a Foundation → clear managed (it's an anchor). Else if it participates (anything but
   air/fluid/exempt) → mark it managed.
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

  vertical (cell directly ABOVE S):       reach = r                  // resting on a column → INHERIT, no reset
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
- **Vertical load & pillars.** A block inherits the reach of the block beneath it, so a column
  rooted on the ground carries the anchor's full reach straight up — pillars of any height stand.
  Because it *inherits* rather than *resets*, the tip of a maxed-out cantilever (reach 0) cannot
  act as a fresh anchor: stepping up one block off it buys no new span, so the "staircase" cheese
  is closed. A genuine ground pillar at the new spot still grants a full fresh span.
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
  ships. No custom physics. Collapse-spawned falling blocks are flagged (entity attachment) and,
  on landing (`EntityLeaveLevelEvent`), re-marked as player-managed so piles stay in the system.
  Blocks with a block entity (chests, furnaces, shulker boxes, signs, …) are dropped via
  `destroyBlock` instead of a falling entity, so their contents are never voided.

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

## 7a. Crafting

| Block | Recipe | Yields |
|---|---|---|
| Foundation Anchor | ring of 8 Smooth Stone around 1 Iron Ingot | 4 |
| Reinforced Beam | 6 Stone Bricks (top+bottom rows) + 3 Iron Ingots (middle) | 3 |
| Heavy Girder | 8 Iron Ingots around 1 Reinforced Beam | 2 |

## 8. Tuning

Everything lives in the server config (`world/serverconfig/structint-server.toml`):
material spans, the support cap, `maxRegionNodes`, and the two per-tick budgets. Set
`enableCollapse=false` to track integrity without dropping blocks (e.g. on a creative server), or
`onlyPlayerPlaced=false` to subject every full block to the rules.

## 9. Known boundaries / future work

Hardened after an adversarial audit of the "every block is structural" change. Fixed: safe
collapse routing (multi-cell doors/beds/plants, attachment blocks, and containers drop in place
instead of becoming corrupt/voided falling entities); natural non-solid blocks (grass, flowers,
vines, snow) no longer act as free infinite anchors; collapse re-solve is O(cluster) per tick
instead of O(cluster·collapses) with proper cancellation when a player re-supports a block;
explosions now propagate collapse; the over-budget fail-safe is logged; the collapse flag survives
save/unload.

Remaining boundaries (documented, lower-impact, want in-world testing before changing):

- **Removal paths without a break event.** Pistons, fluid wash-away, fire, leaf decay, `/setblock`
  and `/fill`, and mob griefing don't fire `BlockEvent.BreakEvent`, so a support removed that way
  may not immediately propagate a collapse (player break and explosions do). Piston-moved blocks
  also keep their managed flag at the old position. Future work: `PistonEvent` migration and a
  scoped fluid/fire hook.
- **Programmatic placement** (`/setblock`, structures, other mods) isn't marked player-managed, so
  it reads as natural terrain (stable). Fine for map-makers; a `/fill` build won't obey the rules.
- **`onlyPlayerPlaced=false`** is an expert/experimental mode — with every block structural it can
  tear down natural overhangs and cave ceilings. Leave it `true` (the default) for normal play.
- **Performance.** Work is bounded by `maxRegionNodes` and the per-tick budgets; the solver still
  uses boxed `long` keys (clusters are capped, so this is minor). A very large connected build can
  exceed `maxRegionNodes` and is then treated as stable (logged at debug).
- Weight/load accumulation is intentionally absent — span is the single, predictable knob.

## 10. Roadmap — candidate features

A backlog of novel, on-theme ideas that reuse the span/load-path math (not yet built). Each stays
deterministic, local, and gameplay-rule-first.

**New load-graph primitives (blocks)**
- **Keystone** — a span-1 block that promotes to a full anchor the moment it gets support from two
  opposing sides within span; completing an arch makes the structure "set." (Monotone → still a
  deterministic fixpoint.) ⭐ high-impact, self-contained.
- **Diode beam** — conducts reach one direction only; lets you deliberately starve a wing.
- **Load splitter** — one strong feed in, three fixed modest branches out (a routing hub).
- **Support relay** — transmits load only while redstone-powered: switchable bridges, drop-floors.
- **Curing concrete** — placed weak, ramps a tier at a time only while continuously supported
  ("scaffold a span, let it cure, pull the scaffold").

**Anchors & load as economy**
- **Finite-reach anchors** — foundations grant a *number* (Stake 8, Foundation 64, Bedrock ∞)
  instead of infinite CAP. One-line change to the Dijkstra seed; big design space. ⭐
- **Span permits** — a consumable that raises a *chunk's* allowed span tier (area license, not
  block ownership).

**Failure as a designed tool**
- **Sacrificial strut ("fuse")** — pre-mark blocks the solver drops first; authored controlled failure.
- **Keystone demolition** — a charge that reads out how many blocks depend on this one (solver diff
  with it as air) as a redstone signal, then pulls exactly that span on command.

**Span-literate threats**
- **Sapper mobs** — mine your lowest-reach load-bearing block (the keystone), then re-query.
- **Undermine burrowers** — eat the natural ground anchor beneath a pillar's feet.
- **Overload-by-crowd** — mobs crowd a cantilever; each on a block applies a flat −1 span, so a
  horde drops the bridge from under itself. (Occupancy is an integer modifier, not weight.)

**Solver as a side-channel**
- **Spawn-proof by suspension** — maxed cantilever tips (reach 0) read as precarious → no mob spawns.
- **Structure-validated multiblocks** — a machine powers on only because it genuinely stands as one
  load path (any shape); switches off when a buttress is mined.
- **Public solver API + datapack rulesets** — reloadable span JSON plus `isSupported` /
  `remainingReachAt` and a "support flipped" event for other mods/maps.

**Environment as a span input**
- ~~**Snow load**~~ — *shipped*: snow depth subtracts span, capped, with strong materials immune.
- **Mineshaft integrity** — apply the cantilever rule to *mined-out* ceilings: player-dug tunnels
  wider than the ceiling's span cave in unless timbered. Integrity governs removal, not just building.
- **Pre-stressed ruins / donated anchors** — worldgen structures born at reach 0 (stable until
  disturbed) and mid-air/cliff foundation features to bridge off during exploration.
