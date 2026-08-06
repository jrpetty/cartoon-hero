# Banner & Blade — Roadmap & Decisions (memory bank)

Living notes so design decisions aren't lost between sessions. Newest decisions
at the top of each section.

## Locked decisions
- **Boons = Battle Plan.** Each boon has rarity tiers (8% → 24% on headline
  buffs); a higher tier is a stronger version of the *same* boon (no fixed
  power classes — multiplayer-fair). Pre-match you equip one Offensive + one
  Defensive + one Supportive boon and choose which **age** each unlocks at
  (I/II/III). They stack and buff the existing army on age-up.
- **Boon currency = Valor** (combat-earned), separate from Renown (units/
  commanders). *May be merged into one currency later — keep it modular.*
- **Open build order.** Advance an age by building **any 2 of a set** (not one
  fixed building). Feudal: any 2 of {Barracks, Mill, Lumber Camp, Mining Camp}.
  Castle: any 2 of {Barracks, Archery Range, Stable, Blacksmith, Market, Watch
  Tower}.
- **Up to 8 players**, ring maps, FFA / even teams, spectator mode.

## Deferred (agreed, not yet)
- **Rename the ages** to **Hearth → Banner → Crown** (drop the AoE feel).
  Liked, but on hold — get the 3 ages feeling great first.
- **4th age** (…→ Crown → Empire): not yet — more content to fill.

## Units
- Done: villager, man-at-arms, spearman, archer, skirmisher, **horseman**
  (Feudal ranged cav, pierce armor, anti-siege), crossbow, **javelin** (Feudal,
  anti-cav ranged), **handcannon** (Castle gunpowder), **raider** (Feudal eco
  raider), knight, catapult, ram, trebuchet, monk, champion (hero),
  **scout** (Dark, cheap vision), **two-handed swordsman** (Castle heavy inf),
  **pikeman** (Castle anti-cav).
- **Battlemage** is in (Castle, trained at the Castle: AoE fire, slow to cast,
  frail — worth guarding). Added alongside it: **Shieldbearer** (Feudal wall,
  4/5 armour, hits softly), **Berserker** (Castle, twin axes, no armour at all),
  **Longbowman** (Castle, 215 range, slow draw), **Cataphract** (Castle barded
  cavalry) and **Bombard** (Castle gunpowder siege). Roster 18 → 24, which is
  mostly aimed at Warband Tactics, where three units a tier made the shop feel
  bare.
- Two new synergies came with them: **Mystics** (Monk + Battlemage — the Monk
  previously belonged to no trait at all) and **Gunpowder** (Hand Cannoneer +
  Bombard).
- Backlog: **Trade Cart** (Market economy unit). Possible unique art passes for
  units currently sharing draws (twohand/militia, pikeman/spearman, scout/raider,
  javelin/skirmisher, handcannon/crossbow).

## Board terrain
Every round rolls its own ground from its own seed: **boulders** take a cell off
the board entirely, **rises** give whoever holds them +20% range, **mires** slow
what stands in them by 30%. Round 1 is deliberately clear.

Two rules keep it fair rather than random punishment. Features are **mirrored
across the centre line**, so a boulder in your third rank has a twin in theirs —
both warbands face identical ground. And the front rank (cols 4/5) is always
clear, so a wall always has somewhere to stand. The seed is shown on screen as a
six-character code, so a board can be noted down and found again.

## Abilities
Every shop unit now has a signature ability — it was 9 of 24, so most of the
Warband roster was a stat block while a lucky few actually did something. The
15 new ones reuse the existing kinds where they fit and added one new kind,
**guard**: a timed armour aura for nearby allies. `rally` only lifted attack,
so there was no way for a support unit to make a line harder to kill; the
Shieldbearer's Shield Wall is built on it.

## Upgrades
- Done: Long Swords, Pikes, Cavalier (Stable, Knight +25 HP), Heavy Cavalry
  Archer (Archery Range, Horseman +3 attack); Horse Collar / Bow Saw / Gold
  Mining; **Loom** (TC, villager +15 HP), **Town Watch** (TC, +25% vision),
  **Treadmill Crane** (TC, +20% build/repair), **Caravan** (Market, trades
  return 85 not 75).
- The upgrade schema gained three team-wide kinds — `vision`, `build`, `trade`
  — that skip the appliesTo matching the combat techs use.
- **The AI only ever researched at the Blacksmith**, so every economy tech was
  effectively player-only. It now researches from any building it owns, keeping
  a float so teching doesn't starve unit production.
- Backlog: more unit-line elites; a University-style building if the TC list
  gets crowded.

## Game modes (NEXT BIG BUILD — decisions locked, build in this order)
1. **Survival / Horde** — **co-op**: you + 1–3 AI allies vs escalating AI waves
   that spawn from the map edges. Survive all waves to win; wiped out = lose.
2. **King of the Hill** — hold the central hill for **5 minutes cumulative,
   uncontested** control to win. The timer **pauses while an enemy stands on
   the site** and **resumes when they step off** (progress is kept, not reset).
3. **Regicide** — each side gets a King; kill the enemy King to win, protect
   yours (buildings don't decide it).

## Done since last update
- New units: Scout (Dark/TC), Two-Handed Swordsman (Castle/Barracks), Pikeman
  (Castle/Barracks). Earlier: Horseman, Javelin, Hand Cannoneer, Raider.
- Unit-tech upgrades: Husbandry, Bloodlines, Long Swords, Pikes (Blacksmith);
  Horse Collar / Bow Saw / Gold Mining (Mill / Lumber Camp / Mining Camp).

## Balance — measured, not guessed
`src/sim/balance.test.ts` runs every unit against the whole roster at equal
gold, on a fixed layout with seeded fights, and reports a win matrix. Findings
from the first pass:

- **The Knight was never the problem.** It measured 58% — mid-table. The
  suspicion in this file was wrong.
- **The counter triangle was lopsided.** Anti-cavalry bonuses were huge (spear
  +25, pike +38) while anti-infantry was noise (archer +3, crossbow +4), so
  infantry had no real counter. Now Archer +5, Crossbow +9, Hand Cannoneer +14,
  and the Archer is 10 wood cheaper — archers are a numbers unit, so cost is
  the lever that helps them en masse.
- **The Two-Handed Swordsman was the actual outlier** — 100% win rate, the best
  army HP *and* DPS in its bracket, beating even its counters. Now 130 gold
  (was 100), 95 HP (was 110), 12 attack (was 13), 1 armour (was 2). Pikeman
  also went up 10 food; it had the largest army HP on the board.

**Skirmish stance (added).** Ranged units on Skirmish give ground while
reloading and plant to fire the moment the shot is ready — move, stand, shoot.
It is opt-in (Y cycles to it) rather than the default, so it is a lever the
player pulls rather than a blanket archer buff, and the skirmish AI sets it on
its own ranged units so neither side gets free micro. Two things keep it from
being overpowered, both measured: it cannot fire while moving, and a skirmisher
at 76-80 only *holds* the gap against infantry (78-80) while losing it outright
to cavalry (112-135). Archer beats Militia and Two-Handed Swordsman on
Skirmish; Knight and Horseman still beat the Archer. The counter triangle
enforces itself through speed rather than through bonus damage.

Hand Cannoneer was the floor of the matrix at 20% — 45 HP and a 2.8s reload
meant it fired twice and died. Now 58 HP and 2.5s, which puts it at 50%.

**The default combat model still does not kite.** Units close and trade, so massed archers lose a
head-on fight with massed infantry however the bonus is tuned — and pushing the
bonus far enough to change that also lets one archer beat one swordsman, which
breaks the anti-kite invariant in world.test.ts. That tension is real and was
hit during this pass. So "archers melt infantry" is a claim about a *line*, not
a duel: what the bonus buys is that archers behind a spear screen beat the same
archers standing alone, which is what balance.test.ts now asserts.

Spread went from 0.9–100% to 20–77%. The specialists (Spearman 29%, Skirmisher
36%) sit low on purpose — they read badly in pure-comp fights and well in mixed
ones. The bounds encode "nothing unbeatable, nothing dead weight" rather than
parity, because the metric has a known melee bias.

**Second armour pass (measured).** Base armour came down again — Cataphract 4→2,
Shieldbearer and Battering Ram 4→3, King 3→2, Knight / Champion / Bombard /
Trebuchet 2→1 — and every armour relic lost a point (Greatmail 7→6, Ironplate
5→4, Ironhide 4→3, Tower Shield 5→4, Duelist's Edge 4→3, Dancer's Mail 5→4).
Matrix afterwards: max 72%, min 28%, spread 43, and the correlation between base
armour and win rate is **−0.145** — armour no longer predicts who wins at all.
balance.test.ts now asserts that correlation stays under 0.35 and that no unit
carries more than 3 base armour.

**Correction to the earlier suspicion.** An "armour build beats an attack build
100%" reading looked damning until the builds were made single-stat. Pure armour
(Greatmail + Ironplate) loses to pure attack *and* to pure HP, from either side
of the board, and did so **before** the relic cut as well as after — the earlier
result was the +160 HP and +22% HP riding along on Ironhide and Tower Shield,
not the armour. So the relic cut is a safe ceiling reduction, not a fix for a
measured dominance. Note also that a one-directional matchup here is unreliable:
the mirror control (identical boards, both sides) swings 8–92% across units, so
anything measured this way has to be read in both orderings.

## Warband lobby difficulty — measured

The four lobbies scale *how well the rivals play the same economy*, not their
stats. Placement by player archetype over 30 seeds (1 is best of 8):

| lobby | idle | average | good |
|---|---|---|---|
| Squire | 8.00 (0 wins) | 4.13 (3/30) | 2.10 (13/30) |
| Veteran | 8.00 (0) | 5.13 (0/30) | 2.30 (14/30) |
| Warlord | 8.00 (0) | 6.17 (0/30) | 3.07 (8/30) |
| Conqueror | 8.00 (0) | 6.17 (0/30) | 3.63 (5/30) |

Squire's first cut softened the rivals *and* the damage, and a careful player
took it 15 times out of 18 — a formality is no way to learn a board. The
forgiveness now lives almost entirely in what a defeat costs (×0.66); the lobby
plays at 0.94 income and 0.92 tech, near the honest game. Squire is still
clearly the kind one for a middling player (4.13 vs Veteran's 5.13, and 3 wins
against 0) without being free at the top.

Warlord and Conqueror tie on the "average" row. That is real: both are already
past the point where a middling board survives, so the difference between them
only shows up in the "good" column.

## Performance — measured

`npm run perf [players] [minutes]` plays a headless AI-vs-AI match and reports
tick percentiles. Percentiles, not averages: a sim whose *average* tick is
comfortable still stutters if one tick in a hundred blows the frame budget, and
that is exactly what a busy eight-player match felt like.

Eight players, twelve minutes, ~1000 entities, 50ms budget at 20Hz:

| tick time | before | after |
|---|---|---|
| median | 0.37ms | 0.23ms |
| p95 | 8.59ms | 0.67ms |
| p99 | 25.07ms | 2.36ms |
| p99.9 | 32.13ms | 4.86ms |
| worst | 39.24ms | 12.11ms |

Two findings, both in A*:

1. **It allocated the whole grid per call.** Three arrays the size of the nav
   grid, two of them filled — ~190KB and 21,000 writes for every path request,
   whether the answer was five cells long or five hundred. Now the buffers are
   module-level and stamped with a generation counter, so starting a search is
   O(1). This alone was 2.4× on total sim time and cut garbage collection 5×.
2. **Two in five path requests were to targets with no route at all**, and each
   one expanded its full 6000-node budget before giving up. That was 36 of the
   36.4 million node expansions in a whole match. The nav grid now keeps
   connected-component labels (flood-filled lazily, invalidated by `setBlocked`)
   and A* returns null in constant time when the two ends are in different
   pockets.

Four-way flood fill is exactly right for this despite A* moving diagonally: a
diagonal step is only legal when at least one of its two orthogonal neighbours
is open, and that neighbour gives a 4-connected route to the same cell. So the
labels never refuse a route the search would have found. The shortcut also
deliberately does *not* fire when either end stands on a blocked cell — a unit
shoved onto a footprint can still walk out through its neighbours, and that case
keeps taking the full search.

Both changes are output-identical: 12,000 random queries across three map
presets return byte-identical paths to the old implementation. Getting there
took one real correction — the first rewrite differed on 41% of queries because
`ng + (a + b)` had become `(ng + a) + b`, and floating-point addition is not
associative, so ties broke differently and units took different (equally short)
routes.

What is *not* the bottleneck, measured: the per-frame vector drawing is 0.5–2.8ms
at 1000 entities. A headless render profile puts almost the whole frame in three
full-screen `drawImage` blits (terrain, fog, vignette), but that is software
rasterisation in node — a browser composites those on the GPU — so that number
says nothing about real-world frame time and was not acted on.

## Controls & accessibility

**Hotkeys are data now.** `meta/keybinds.ts` holds an action list (id, label,
group, default chord); bindings live in Settings and are resolved through a
cached chord→action map. Adding a hotkey is one entry in `ACTIONS`.

Defaults follow Age of Empires wherever the game has an equivalent, because
that is the muscle memory players arrive with: `.` / `,` next and previous idle
villager, `H` select the Town Centre, `Space` go to the last event, `Ctrl+1..9`
assign a control group (`Shift+1..9` adds, a bare digit selects, twice
re-centres), `Ctrl+A` select all soldiers, `F3` pause, `Del` disband. Hold
Position moved to `Shift+H` to free `H` for the Town Centre — that one is a
deliberate trade, and the test suite asserts the whole AoE set so it can't
drift silently.

Three things that were quietly broken and are fixed by the same change:
- **WASD panning only worked in two directions.** `A` and `S` were Attack-move
  and Stop, so pressing them panned *and* issued an order. Pan defaults to the
  arrow keys, and there is now a test that no pan direction may share a key
  with a command.
- **Shift+= reported `+`, not `=`.** Counting the Shift as well produced
  `Shift++`, which matched nothing, so Speed Up did nothing on most keyboards.
  A printable symbol now ignores Shift, because the symbol already encodes it;
  letters and digits keep it, so `Shift+H` stays distinct from `H`.
- **Rebinding Escape or Tab was impossible**, because the screen acted on them
  first. Keys route through the settings screen while it is listening.

Conflicts are allowed but flagged in red with the clashing chord named, and the
first action in the list wins — silent shadowing was the alternative and it is
worse.

**Interface scale (80–150%)** scales the HUD and menus but not the world, so
turning it up costs no view of the battlefield. It works by drawing the UI
through a canvas transform and dividing the pointer to match; widgets test
`ui.mx` against their own layout coordinates and never learn a transform is in
force. A test asserts a widget laid out at (100,100) under 1.5× is hit at
(150,150) and not at (160,160), because a mis-scaled hit test is the one bug
this feature can introduce.

**Performance overlay** (`F10`) answers "why is this chugging" without a
devtools profile: fps, frame time and worst frame, sim ms/tick and worst tick,
the drawing share, entity count, and whether detail has already been dropped.
The worst-case readouts are a rolling three-second window so they describe now
rather than the worst thing that ever happened.

**Adaptive detail** drops to the cheap unit renderer when smoothed frame time
sits under ~45fps for most of a second, and restores it after two seconds back
above ~55. Slow in, slower out: detail that flickers is worse than either
setting.

## Map editor

**The format is not MapData.** MapData is what the sim consumes for one match —
it already knows the player count, the starts and the walls. A `CustomMap` is
the authored thing that outlives any match: ground, resources, spawn points and
the rules about who may play on it. `toMapData` is the single place the two
meet, and the only place that knows about player counts, nomad or seating.

**Nomad is a property of the map, not the lobby.** Three states: `off` (always
played from its spawn points), `optional` (spawn points normally, ignored when
the match asks for nomad), `forced` (no spawn points at all — the editor takes
the spawn tool away, and validation stops asking for seats). Random landing
sites are drawn from the match seed, so a nomad game on a custom map is as
replayable as any other, and they only ever land where a Town Centre fits.

**Symmetry is the whole job on a competitive map.** Free, mirror ↔, mirror ↕,
rotate 180°, quarters, and radial ×3/×6/×8. Every stroke — ground, resources
*and* spawn points — is applied through it, so a four-player map is fair by
construction rather than by hand. Radial rotation sends far corners off a
square map, so those copies are dropped rather than clamped (clamping would
pile several seats onto one edge cell); the tooltip says so.

**Validation runs on every change**, in words, and blocks Test Map on an error:
- a spawn for every seat, each with room for a Town Centre
- no spawn walled off from spawn 1 (constant-time, via the nav grid's
  component labels — the same ones that fixed the pathfinding)
- wood on the map, and wood within reach of each base
- seats within 60% of each other on nearby resources
- Survival: at least a quarter of the map edge open, or waves have nowhere to
  arrive from
- King of the Hill: the centre passable and reachable
- more than half the map impassable

**Sharing is a paste-able string,** because the game is one HTML file and there
is nowhere else for a map to go. Terrain is RLE'd then base64'd: an empty
200×200 map (40,000 cells) is under 600 characters. Deserialisation trusts
nothing — an out-of-range terrain id would index off the end of the lookup
tables the movement path reads every tick, so it is clamped.

**Nine sizes, from a duel arena to something enormous.** Duel (40 cells) is
about thirteen Town Centres across — bases within sight of each other — and
Colossal (320) is eight times that in area. Nothing about a size implies a
player count: a Duel map may seat eight if that is the fight you want, and a
Colossal one may seat two. The blurb on each size says what it is *for*; it
decides nothing. New maps open wide (1–8 players, any mode) so the creator
narrows it deliberately rather than discovering the editor chose for them.

That did need one engine fix. The terrain cache is a single canvas spanning
the world, and at half scale a 320-cell map wants 5,120 pixels — past Safari's
4,096 limit, where it hands back a *blank texture rather than an error*. The
cache scale is adaptive now, capped so the biggest map bakes at 3,072px, and a
test asserts every size stays under 4,096. Nothing is lost: at the zoom you
view a map that size from, the extra resolution was sub-pixel.

**Custom maps are not a second-class citizen.** Every match resolves its map
through one seam, `App.resolveMap`, so skirmish, spectate and Test Map all take
the same path. The setup screen lists your maps beside the presets, filtered by
`mapSupports` to the ones that allow the current mode and player count, and
deselects one that stops qualifying. Testing from the editor returns to the
editor afterwards, so tweak-and-test is a loop rather than a trip through the
main menu.

A end-to-end test authors a symmetric 1v1 map with a river, a ford, mountain
spurs and hills, saves it, round-trips it through a share code, renders it
through the real terrain painter, and then plays a ten-minute AI match on it —
asserting both economies grew *and* that the two sides actually met, which is
what proves the ford is crossable.

## The AI reads the ground — measured

Terrain used to affect only the player. The AI had zero references to it: it
did not know hills existed, would not take one before a fight, and routed
around a mountain only because the pathfinder did it for them. Geography that
one side understands is worse than no geography.

`src/ai/terrain_sense.ts` is a cheap advisor, not a planner. It answers one
question — "of the ground near here, which patch would I rather stand on?" —
scoring sight at roughly twice speed (a hill is +11, a wood −52, a marsh −34,
open grass 0). The AI asks it at the three moments the answer changes what it
does: staging an attack, meeting a raid, and siting a tower. `readsGround` is
public and mutable purely so an aware AI can be played against a blind one on
the same map and seed.

Building it was easy. Making it *do* anything took three measured findings, and
the honest summary is that the first two were bugs in my own work and the third
is a null result.

**1. The distance penalty and the ground score were in different units.** A
hill scores 11 points; the search divided distance by a constant of 10, so the
AI would divert at most 110 world units — three tiles — while searching a
radius of 260. Five sixths of every search was ground that could not win no
matter how good it was. The penalty is now a fraction of the search radius, so
`detour` is denominated in the same points the ground is: below 11 means "cross
the whole search area for high ground", above it means "only take what is
close". Before the fix, a twelve-minute match produced **0** orders onto a hill.
After, 19–38.

**2. High ground was 4% of the map, and did not scale with map size.** Hill
count was `hills * 10` regardless of area, so the Colossal 320-cell map got the
same seven small rises as a duel map, and Highlands — the preset that asks for
the most — measured 4.0% high ground. Open Plains, Crossroads and Continental
had **zero** hills. At that density an army crossing the map would usually never
come within reach of one, so the AI's terrain sense measured as worthless
because there was nothing out there to sense. Count now scales with area and
the rises are wide enough to hold a battle line:

| preset | hill cells, before | after |
|---|---|---|
| Highlands | 4.0% | 11.7% |
| Riverlands | 2.7% | 7.4% |
| Gauntlet | 2.2% | 4.6% |
| Open Plains | 0% | 0% (deliberate — see the preset) |

**3. It does not measurably change who wins.** Aware against blind, ten seeds ×
both side assignments × 30 sim-minutes, which is 20 paired matches:

| variant | wins | kill ratio | per-match sign |
|---|---|---|---|
| before the scale fix (feature inert) | 5W 5L | 1.16 | 12–8 |
| feature firing, stage on any good ground | 7W 4L | 0.87 | 10–10 |
| feature firing, stage only on high ground | 6W 4L | 0.82 | 9–11 |
| feature firing, no staging leg at all | 7W 5L | 0.85 | 10–10 |

Read the last column, not the middle one. Every variant lands on a dead-even
sign test while the aggregate ratio drifts to 0.82–0.87, which is the signature
of a handful of high-variance seeds carrying the totals — individual matches
swing by ±70 kills (`-71, -57, -48` against `+51, +36, +29`). The honest
reading is **no measurable effect in either direction**: n=20 cannot resolve an
effect this small, and I originally mis-read the sub-1.0 ratio as evidence that
staging was costing something. Removing the staging leg entirely changed
nothing, which is what disproved that.

So staging stays, restricted to high ground — chosen for being the cheaper of
two indistinguishable options and because an army forming up on a ridge is the
visible half of the feature. Defence uses a tight five-tile search so a relief
force never trades interception for elevation.

The defensible claim is the narrow one: the AI now demonstrably reads terrain —
it takes hills, sites towers on them, avoids woods and marsh, and will not
stage on a hill it cannot reach. It is a fairness fix, not a strength buff, and
should not be sold as one.

Two things this surfaced and did **not** fix, both pre-existing:
- **This AI often never attacks.** On several Highlands seeds a `knight` AI
  issues four move orders in twelve minutes and fights nobody, never reaching
  its army threshold. That is why 9–10 of every 20 head-to-heads finish
  unresolved, and it caps how well any combat change can be measured.
- **A hill is abandoned at contact.** Units get the range bonus only while
  standing on it, so holding ground is worth far more than staging on it. A
  defensive posture that actually *holds* a rise is where the mechanic would
  pay off.

## Bigger / later
- **Naval** — water is currently only an impassable wall, and the Islands
  preset (55% water) is a maze rather than a naval map. Dock, transport,
  war galley + AI. The largest genuinely-missing pillar.
- **Battlemage** and **Trade Cart** remain the open content items.
- **Multiplayer (PvP)** — the whole net stack (lockstep, WebRTC, WS, lobby,
  session) is written and unused. Needs a signalling server to host, so it
  can't live in the single-file build. Not now.
