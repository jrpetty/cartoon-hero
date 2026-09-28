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
- (The age rename and the 4th age are done — see "Four ages, Oaths and
  Factions" below.)

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

## Crossings, standing orders and the record

**Bridges.** A timber span that can only be laid on shallows — the buildability
test *inverts* for it rather than relaxing, so a bridge is never a cheap wall on
dry land, and never half on the bank. Decking is counted per terrain cell rather
than flagged, because two spans can share an edge cell and a boolean would punch
a hole in a standing crossing the first time one of them burned. A decked ford
crosses at full speed instead of the 0.5× wade, which is what makes "go around"
a decision with a price. Lakes are untouched: deep water stays a hard wall, so
bridges change the *cost* of the map, never its connectivity.

**Rally points mean something.** Dropped on a resource, new villagers gather it;
on a building of yours that is going up or damaged, they go and work on it;
on bare ground everyone walks there as before. Resolved at spawn rather than at
drop time — the target's state changes in between, and the useful reading is the
one taken when the villager actually has hands free.

**Drag-place past walls.** A wall wants a gap-free line; a block of houses wants
the opposite, so it lays them on a one-tile pitch with walking room between, in
reading order so round-robin builders work a contiguous stretch instead of
criss-crossing. The ghost greys out once the *treasury* runs dry, not only where
the ground refuses.

**Farms are finite.** They were literally infinite — `amount = 999999`, and the
gather step skipped depletion for them — which made the 60-wood cost a one-off
toll on unlimited food and left auto-reseed with nothing to reseed. A field holds
350 food now, about four minutes of one villager, and auto-reseed (on by default,
toggled from the farm or Mill) re-sows the same ground whenever the wood is
there. Two things had to give way: `placeBuilding`'s "no building on top of
units" check now skips walkable buildings, because the farmer stands *on* the
plot and was blocking every in-place re-seed; and a farm test that asserted on a
villager's instantaneous order target was passing only because the farm held no
food at all, so the farmer filled up forever and never walked a load back.

**Veterancy was already drawn** — the claim that nothing showed it was wrong —
but a 1.4px gold chevron vanished against pale ground, and nothing anywhere named
the rank or the progress toward the next. Chevrons get a dark under-stroke; the
selection panel spells out rank, kills and the next threshold.

**The record is kept.** A summary of every match goes to localStorage, last
twenty, and the Codex grows a Records tab: the run of games, win rate, kills per
loss, best streak, and the share of an average match your Town Centre sat idle.
Twelve achievements and three weekly challenges are evaluated off that same
summary — the constraint being that if a condition can't be read from a finished
match, it doesn't belong. Challenges take a consecutive window of the pool that
advances by its own width, so consecutive weeks never overlap and the whole pool
is visited before anything repeats.

## The map editor, finished

**Start from a generated map.** `customFromGenerated` rolls a preset and hands
it back as an editable CustomMap with ground, resources and spawn points already
placed. A blank field is a lot of painting before there is anything to react to.

**Path tool.** Rivers, ridges and roads are lines, and a round brush dragged by
hand makes a wobbly sausage with holes on a steep diagonal. Press and release and
it walks the line, stamping the brush at every step.

**Scatter brush.** Drag to sprinkle resources at a density. The pattern is a hash
of the *cell*, not a running random, so dragging back over ground you covered is
a no-op rather than a pile-up, and the same drag looks the same twice.

**Region copy and stamp**, with flip X/Y. Deliberately *not* mirrored through the
symmetry setting: symmetry is for strokes, and a considered placement firing into
three other corners is almost never what is meant.

**Descriptions and preview cards.** Maps carry a description through the share
code, and the lobby draws a thumbnail with spawn dots and resource specks. Where
the seats are and how far apart is most of what says whether a map is a knife
fight or a long game, and neither a name nor a cell count can tell you.

## Random map scripting

`src/maps/script.ts` is a small line-based format that produces a **CustomMap**,
not a MapData — so a scripted map lands in the editor and can be tweaked, saved
and shared like any other. A script is a starting point, not a walled garden.

```
size 128            blob mountain count 10 radius 4-8
biome alpine        lake water count 4 radius 4-8
seats 4             river shallow width 3
symmetry quad       spawns ring radius 0.34
base grass          cluster gold count 2 near start radius 20-40 nodes 5
```

Four constraints drove it. **Everything is optional** — an empty script is a
valid map, because a format where you must say twelve things before the one you
care about is one nobody writes in. **Errors name the line and say what was
expected**, because a script language with a silent failure mode is worse than
none: you get a map, it just isn't the one you wrote. **Deterministic**, so a
seed is a share code. And **named arguments in any order**, since remembering an
argument order is exactly the friction that stops someone starting.

The editor's script panel is folded away by default. It is the most powerful
thing on that screen and also the one most people will never touch, and an editor
that greets you with a code box has told you it is for programmers.

## The AI and the water

Bridges shipped as something only the player understood — measured: zero
references to `bridge` in `skirmish_ai.ts`. Which is precisely the thing the
terrain section above argues against, committed two changes after writing it.

`fordCrossing` walks the straight line from a base toward its target and returns
the middle of the widest undecked run of shallows on it, with the width. The
straight line rather than the real path on purpose: the pathfinder already routes
around walls, and shallows are not walls, so the route it picks really does go
through them. The AI bridges a crossing wider than two tiles, once, only while it
can afford it and only when nothing of its own is already going up — an AI that
queues four bridges at one ford has spent four hundred wood on a crossing worth
one. Raiders now prefer an enemy span to anything else visible: it cost them a
hundred wood, it stands in water so nothing defends it, and burning it puts their
next wave back into the shallows at half speed.

Under `readsGround` with the rest of its terrain sense, so it stays measurable.

## Save and resume

A save is **the seed, the setup, and every order you gave** — not a snapshot.
The sim is a deterministic fixed-tick machine and the AI is deterministic too, so
replaying the setup and the order log reproduces a match tick for tick. The
alternative, serialising the world, loads instantly and costs a per-field
serialiser for every entity, player and grid, plus a new silent bug every time
someone adds a field and forgets it. That class of bug corrupts saves
retroactively. A command log has one failure mode instead, and the stored
checksum catches it at load — loudly, before an hour has been played on top.

Building it turned up two genuine determinism bugs:

1. **AI commanders were drawn with `Math.random()`.** A match was therefore not
   reproducible from its own config, which is the premise the whole format rests
   on. Now seeded off the match seed.
2. **Entity ids came from a module-global counter** reset in the `World`
   constructor. Fine while exactly one world exists, and silently wrong the
   moment two do: building world B resets the counter, then ticking A and B in
   turn interleaves their allocations. Ids are hashed into `worldChecksum`, so
   the same match built twice measured as *non-deterministic* — the thing
   lockstep and save/resume both stake everything on. The allocator is per-world
   now.

Neither showed up in normal play, because normal play has one world and never
compares two. Both would have shown up as an unreproducible save.

The cost is load time: twelve minutes of a two-player match is ~14,000 ticks, a
few seconds of headless simulation. Worth it for a format that cannot rot. It
also puts replays within reach — a replay is this minus the "keep playing".

## Siege, walls, and a market — measured

**Walls were never a decision.** Measured, 900 gold of army against one
1800hp stone wall segment:

| | before | after |
|---|---|---|
| Trebuchet ×2 | 23.9s | 28.5s |
| Battering Ram ×4 | 24.0s | 35.0s |
| **Militia ×11** | **23.1s** | **93.8s** |
| Knight ×6 | 34.1s | 151.2s |

Eleven Militia broke a stone wall *faster than four Battering Rams at the same
cost*. Siege was not merely weak against walls, it was worse per gold than the
cheapest infantry in the game — so walling bought nothing and a Siege Workshop
had no job.

The fix is armour on wall-class buildings rather than more damage on siege. The
damage floor is 20% of raw, so armour can slow a sword by at most five times and
barely touches a ram's 56: stone wall 20, gate 12 (a gate is a door, and should
stay the obvious place to hit a line), palisade 6 (timber slows an axe, it
doesn't laugh at one). Siege is now 3–5× better than an army, while infantry can
still chew through in ~90s — walls are a decision, not a hard counter to every
army without a workshop. The AI already ramped siege on `seenWalls >= 4`, so
both sides understand this one.

**The Market moves.** Flat rates made it a converter you pressed when short,
with no reason to think about when. Every sale now pushes a commodity's price
down and every purchase pushes it up, drifting back toward par over a couple of
minutes, inside a ±40% band — a smoothing tool for a lopsided economy, not
something you can break by holding a key.

**Trade Carts** run gold between two Markets, yours or an ally's, paying by the
distance of the route. Two bugs found by their own tests, both real:

1. **Buying got cheaper the more you bought.** The buy price was derived as
   `10000 / sell`, so a dearer commodity cost *less*. Both sides now scale the
   same way with value, and the constant spread between them is what guarantees
   a round trip can never make money at any point on the curve.
2. **A long route paid less per second than a short one.** Pay ∝ distance and
   trip time ∝ distance, so distance cancels out exactly — measured at 312 gold
   for a short route against 231 for a long one. The pay is superlinear now
   (`d^1.3`), so an 8-tile hop makes ~0.7 gold/sec and a 50-tile line ~1.2, and
   a long route is worth roughly twice a short one *and* spends its life outside
   your walls. That is the decision trade was supposed to be.

## Hosting multiplayer

Correcting something written in this file's own suggestion list: **the relay
server already existed** — `server/server.mjs`, zero dependencies, tested
end-to-end in `src/net/server.test.ts`. The claim that the net stack had no
server was wrong.

What was actually missing was everything a *host* needs. The server now reads
`PORT`/`HOST` from the environment (a server that only reads argv binds 8787
while the platform routes to whatever it assigned — healthy and unreachable),
answers `/healthz` with JSON, and closes cleanly on `SIGTERM` instead of being
SIGKILLed at the end of every deploy's grace period. Added: a Dockerfile, a
`fly.toml`, a `render.yaml`, and a hosting section in `server/README.md`.

One rule decides the whole topic and is now stated plainly: **a page served over
`https://` may only open `wss://`**. That is why "just run the server" isn't
enough for anything but a LAN or a VPN, and why the recommended hosts are the
ones that terminate TLS for you.

## The AI deadlock — the biggest bug in the project

Measured, on several Highlands seeds: a `knight` AI issued **four to six move
orders in twelve sim-minutes**, never attacked once, and sat at eleven villagers
with 1680 wood and 315 food. One loop caused it:

> `savingForAge` pauses villager production *and* caps the army at four. If the
> age-up is not actually within reach, the economy stops growing, the cost never
> becomes affordable, and it saves forever.

This is why half of every head-to-head measurement in this file finished
unresolved, why the terrain A/B could barely detect anything, and why staging
measured as *harmful* — every combat number here was taken against an opponent
that was not playing.

Three fixes, all measured:

1. **Saving is time-boxed and progress-aware.** Hold back only when 55% of the
   cost is already banked, never for more than a minute, then grow for 45
   seconds before trying again.
2. **Farms respond to food pressure**, not only to berry depletion or Castle
   Age. Reaching the age that used to unlock farming *needs* the food farming
   provides — the same deadlock wearing a different hat.
3. **The AI uses the Market.** It sat on 1600 wood and 300 food with a building
   in the game that converts one into the other. Trading a surplus here is not
   a clever play, it is first aid.

| seed, 12 min | attack orders | kills |
|---|---|---|
| 77 | 0 → **87** | 1 → **97** |
| 909 | 0 → **83** | 0 → **85** |
| 5 | 0 → **94** | 0 → **99** |

Decided games now resolve around the 15-minute mark instead of grinding. The
economy comes out balanced — food 24.6k, wood 23.4k, gold 25.1k across six
20-minute matches, against the 1685-wood/30-food state before.

**Trade carts were player-only too.** The first attempt gated them on
`diff.counters`, which is about picking counter-units and is *false for Knight*
— so the whole trade economy was switched off for the tier most games are played
at, measured at zero carts across six full-length matches. Gated on the size of
the economy now; a weak AI is kept out by never reaching the villager count,
which is the honest gate.

**Finite farms, finally measured.** 34 farms across six 20-minute matches cost
2040 wood — **8.7% of all wood gathered**. That is the intended recurring
wood-for-food trade, not a strangled economy. The earlier lopsidedness was the
deadlock, not the farm change.

**Staging, re-measured on an AI that fights.** It previously read 0.87 then 0.82
kill ratio and was switched off behind a `const false`. On the fixed AI: **3W 3L,
ratio 1.00, per-match 10–10** — exactly neutral. The "harmful" reading was an
artifact of measuring a broken opponent. Kept on, since it costs nothing and an
army that forms up on the ridge reads as an army; the dead flag is gone and it
is a per-AI field like `readsGround` so it stays measurable.

One test had to change with it. `personalities.test.ts` ran a single AI against
an empty seat, which was fine while the AI never attacked — it had thirteen
undisturbed minutes to build an economy. It now wipes that idle opponent out by
minute six, wins, and stops thinking, so the test was measuring a finished game.
Both seats get a brain.

## Building placement — one snap, and it lands where you point

The snap from cursor position to building centre was the same expression copied
into **five places across three modules**. Two of those copies were wrong, and
nothing on screen revealed it: each was individually consistent with the ghost
preview, so the building always went exactly where the ghost showed. The ghost
was simply in the wrong place.

**Odd footprints landed in the next cell.** `round(v / TILE) * TILE + TILE/2`
rounds *up* past the halfway point of a cell, so a 1×1 Watch Tower placed with
the cursor in the right-hand half of a cell went into the cell next door — and
the ghost, using the same rule, jumped with it. The rule is now `floor` for odd
footprints: a 1×1 or 3×3 building covers the cell the cursor is in, always,
wherever in the cell you are. Even footprints still centre on a tile corner,
because a 2×2 cannot be centred on one cell; which half of the cell you are in
decides which way it extends, and it covers your cell either way.

**Dragged walls landed a cell down-right of the drag.** `wallLinePoints` returns
tile *centres*, and those went straight into the place command, where the old
rule snapped them again — `round(cx + 0.5) === cx + 1`. `placeBuilding` even
carried a comment warning callers to pass raw coordinates for exactly this
reason, but the warning only protected callers inside the same file.

The fix is one shared `snapBuilding(v, tiles)` in `engine/gridsnap.ts`, and the
property that makes the warning unnecessary: **snapping twice is snapping once.**
It no longer matters whether a coordinate has already been through it, which is
what let the bug cross a module boundary in the first place.

`src/engine/gridsnap.test.ts` pins all of it: every footprint size covers the
cursor's cell from any position within it, every drag helper produces
coordinates the sim will not move, `canPlace` and `placeBuilding` agree, the nav
grid is stamped on exactly the cells the building occupies, and two adjacent
wall segments actually touch.

One existing test had to change its premise rather than its expectation. It
dragged from *exactly* a cell boundary two pixels upward and asserted one
segment; that genuinely crosses into the cell above, and the old rounding was
hiding it — the same rounding that put whole wall runs a cell off the line.

## Scripted maps kept resources off the seats

Chasing why a scripted map played 160 against 430 gathered in its first two
minutes. Everything measurable about it was fair: seats on a circle, terrain
quad-symmetric, nearest tree 1.0 cells at both, nearest berries 7.8 against 7.1,
open ground around both. Forcing both AIs to the same personality changed
nothing — it was deterministic and tied to the seat.

It was not the AI, and generated maps are not affected: over eight seeds team 0
gathered 4000 to team 1's 3950 and led in four of eight. The scripted map was
the outlier.

**The script scattered resources onto the starts.** Two nodes within two cells of
*both* seats — on the Town Centre — and nine within six cells of one seat against
four at the other. Blocking nodes in a base cost it roughly half its early
gather rate. The generator has always guarded this (`nearStart`); the script
format never did.

Resources now keep four cells clear of every seat: enough for the Town Centre and
a working ring around it, small enough that a starting berry patch authored at
six to ten cells stays exactly where the author put it. Checked twice — when each
node is placed, and again as a pass at the end, because nothing in the format
forces `spawns` to come above the resource lines and the second pass says out
loud what it cleared. The gap closed from 2.7× to 1.8×, which is inside the
normal early spread measured on fair generated maps (370–660 at the same mark).

## What the game actually looked like

Assessed by rendering real frames to PNG and looking at them, rather than by
reading the source. Three things were wrong, and the first was by far the worst.

**The fog frontier was a staircase.** Fog is one value per nav cell and only
ever takes three of them, so upscaling it straight to the screen ramps between
cell *centres* — and every diagonal frontier, which is most of them, arrived as
hard blocky steps across the whole map. A separable box blur over the mask
before it is upscaled costs two passes across ~16k cells at 8Hz and turns that
edge into the falloff a scouted horizon should have. The "explored but not
visible" tint also came down from a flat 110 of near-black to 88, tinted very
slightly blue, because the old one crushed remembered woodland and buildings
into a single colourless smudge.

**The ground was a blur at the zoom you play at.** The terrain cache is baked at
half resolution — right for a wide view, and magnified over three times when you
lean in, so every tuft baked into it becomes a smear. Rather than bake a bigger
texture (the cache already has to stay under Safari's canvas limit on the
largest maps), `drawGroundDetail` draws crisp marks live for the cells actually
on screen: grass in three tones, stone chips, pebbles, reeds, snow sparkle.

**High ground looked like a shadow.** The cache paints hills as a darker circle
per cell, which from above is indistinguishable from shade — and here high
ground is worth 20% range, so it is the one landform a player most needs to pick
out. Lighting the crest where the hill ends and shadowing the foot where it
drops away gives the mass an edge that reads as height. Woodland got the same
treatment from the other direction: crowns are drawn *lighter* than the forest
floor, since dark-on-dark was why the first attempt was invisible even though it
was measurably drawing (4,907 pixels of it).
(The per-frame crest/foot bands have since been replaced by a baked hillshade —
see "Water, relief, night and building sites" below.)

Detail is not free, and it was measured rather than assumed. The first version
issued a draw call per blade — **+58% render time at zoom 1.0**, and *worse*
zoomed out than in, because a wider view holds more cells, which is exactly
backwards from where detail is wanted. Batching everything into one path per
tone made it worse still (+190%): one enormous path costs more to rasterise
than many small ones. The version that shipped uses cheap primitives and fewer
of them (+29%), starts at zoom 0.85 where the blur actually shows, caps out
rather than drawing thousands of cells, and is skipped entirely whenever the
adaptive LOD is already shedding detail — so it can never be the thing that
makes a weak machine stutter.

## Woods you can hide in

Forest cost speed and sight but hid nobody, so the terrain this file calls the
*soft* barrier was purely a tax and never an opportunity. A unit standing in
woodland is now invisible to any hostile side with nothing within four tiles,
which makes a treeline somewhere to wait and gives scouting a job beyond lifting
fog. Attacking gives you away — an ambush that stays invisible while it kills
you is not an ambush, it is a bug.

Concealment reaches target acquisition as well as the renderer: without that a
longbowman would shoot a hidden unit from two hundred units away and the whole
thing would be decoration on the minimap. And the AI reads `visibleTo` rather
than raw fog in the four places it scans for units — intel, target priority,
focus fire and picking villagers to raid — because counting an ambush it cannot
see is the same "only one side understands it" trap that bridges and trade carts
both fell into.

## Water, relief, night and building sites

Second rendering pass, again judged from rendered frames.

**Water was a two-tone square per cell**, so every river was a staircase of
blue blocks, and **hills were a disc per cell** — the tile grid the rest of the
painter works to hide. Both are now painted per pixel, once, at map load
(`paintReliefAndWater` in `render/terrain.ts`), from smooth fields: a rounded
waterline with depth shading and a foam edge, sand that fades inland, and a
cartographic hillshade lit from the north-west.

- **The waterline must agree with the sim.** Water is impassable, so a picture
  that rounds a coast into dry land is lying. The shoreline field is a blur
  *pinned* at every cell centre (water ≥ 0.6, land ≤ 0.4); bilinear
  interpolation passes through the centres, so the coast can be as round as the
  blur likes while a lone pond, a one-cell channel and a one-cell island all
  survive. `relief.test.ts` checks every cell centre on a test map against the
  terrain grid; removing the pin fails it with 28 wrong cells.
- **Shade per cell, interpolate per pixel.** Taking the slope per pixel from a
  bilinear height field looks equivalent and isn't — its gradient jumps at
  every cell boundary and the first version drew hills as stacks of flat
  facets. Lit flanks mix toward warm light rather than scaling up (scaling made
  grass neon).
- **Grass was planted in the rivers.** Ground dressing scatters up to a cell
  from where it's seeded, which put tufts in the water along every bank; they
  now check the painted waterline. Found by the staircase test, which saw the
  coast "jump" 24px where a tuft broke the run of water.
- **Cost.** The bake went from ~60ms to ~210ms on a normal map (napi software
  canvas; ~700ms on the 8-player Islands map), a one-off at match start. About
  two thirds of that is napi's slow software `putImageData`; the per-pixel loop
  itself is ~200ms on the largest map. The pass never reads the canvas back —
  it accumulates everything as paint laid over the ground into one overlay —
  because a readback was half the cost and, in a browser, can move the canvas
  off the GPU for the rest of the match.

**Night was a flat navy rectangle** drawn on top of the lit windows and
watchfires meant to sell it, so they came out as dim as the grass. Night is now
a darkness layer with holes cut at the lights (`render/nightlight.ts`) at a
quarter of screen resolution — lived-in buildings, watchfires (widest),
anything burning, and a faint lantern on *your own* units only (a light on
enemy units would make night easier to scout). Remembered enemy buildings in
fog don't glow. Lights only cut in once it's actually night — at dusk the sky
is orange, and a window shouldn't punch a hole in a sunset.

**Building sites were a blank brown square**, identical for a house and a
castle and unchanged until the building popped into being. A foundation now
shows a faint ghost of the finished building over a staked-out plot with
materials waiting; under construction, the real building rises course by course
behind scaffolding that climbs just ahead of it, and the timber pile runs down.

## Fullscreen

A fullscreen toggle on the main menu, in the in-match top bar and in the pause
menu, and on `Alt+Enter` (rebindable; `Esc` also leaves). F11 was avoided
because browsers already own it and their fullscreen isn't the page's.
Browsers only grant fullscreen *synchronously inside a user gesture* (Safari
strictly), and this UI is immediate-mode — a button's click is noticed on the
next frame, which is too late. So buttons register a gesture zone while drawn,
and the DOM click handler toggles fullscreen directly when a click lands in
one. Where fullscreen isn't allowed (an iframe without permission, iPhone
Safari) the button is disabled with a tooltip saying why, rather than doing
nothing.

## The match report, for more than two

The end-of-match report folded every game into "your alliance against theirs".
Right for a duel, wrong for everything else: a four-way free-for-all came out
as "1v3" with three rivals — who spent the game fighting *each other* — summed
into one "Opponent", and a 2v2 couldn't say whether you or your ally carried
it. The graph did the same with two lines.

- `MatchReport.players` now carries each realm's own ledger, relation to you,
  alliance, whether it won, and **when it was knocked out** (`defeatedAt`, new
  on `PlayerState`, set by the one place that marks a realm defeated). The
  alliance totals stay, because the match history's trend numbers are built
  from them, and reports saved before this still open — as a duel.
- A duel keeps the contest bars. Anything bigger is a **standings table**: a
  row per realm, grouped into team bands in a team game with team totals when
  there's room, ranked in a free-for-all. Order is winners, then the still
  standing by score, then the fallen *last-to-fall first* — outlasting someone
  is what finishing ahead of them means, whatever the scores say. Under every
  number a bar shows it against the column's best in the realm's own colour,
  and the best in each column is gold (lowest, for losses, damage taken and
  idle time), so "who was best at what" reads without reading numbers.
- Armies is chips per realm — "12 Man-at-Arms · 9 Archer", villagers last and
  muted — rather than a unit × realm grid that would be eight columns of dots.
- The progression chart draws a line per realm in its colour, yours heaviest
  and on top, with a wrapping legend.
- Type: nothing a player has to read is under 11.5px now; the old column
  headers were 9.5px in a grey barely off the background.
- **It has to fit.** `match_report.test.ts` draws every tab for 4-player FFA,
  2v2, 8-player FFA, 4v4 and 8v8 (sixteen realms) into the report's real rect
  at 1600×900 and 1280×760 and checks every realm's name is drawn inside it.
  The first version failed it twice — Armies and Overview both ran off the
  bottom in an 8v8 — so past ten realms rows close up to a single line.

## Four ages, Oaths and Factions

**Ages.** Hearth → Banner → Crown → **Empire**. Empire costs 900 food + 600
gold, needs a Castle *or* a Siege Workshop, and holds the gunpowder and great
siege units (Hand Cannoneer, Bombard, Trebuchet moved up from Crown) plus the
last tier of smithing and economy (Blast Furnace, Plate Mail, Bracer, Ring
Archer Armor, Crop Rotation, Two-Man Saw, Shaft Mining). Age names live in one
place (`AGES[i].short`, `ageShort`) — they had been hard-coded in five UIs.
The "Imperial Ambition" achievement asked for age index 3 when there were
three ages numbered 0–2; it was unwinnable until now. Saves are format 2: a
save replays orders against the current rules, and the rules changed.

**Oaths** (`content/oaths.ts`) — the in-match identity layer. Every advance
past the Hearth swears one of three: Banner *how will you grow* (Plough /
Sword / Hearth), Crown *what will your army be* (Lance / Bow / Shield), Empire
*what will you be remembered for* (Coin / Engine / Crown). Each is a passive
bonus plus a signature: a unit only its sworn train (Sworn Blade, Lancer with a
triple-damage charge, Ranger that stays hidden in woods while shooting,
Halberdier, Great Bombard, Royal Guard) or a rule (free replanting, mending
buildings, a Market tithe). Sworn via `research(tc, "age:<oath>")`, so saves,
replays and lockstep needed nothing new. Oaths are public — everyone is told,
the scoreboard shows them — and the picker is a three-card screen showing each
Oath's real numbers, its unit drawn live, and the rarity you own of that unit.

**Commander affinity.** Nine commanders, nine Oaths: each commander favours
one, and swearing it is 50% stronger with its signature unit training 25%
faster. That ties the commander you bring to the realm you become.

**Factions** (`content/factions.ts`) — who you are, chosen before the match.
Kingdom (late: cheaper smithing and castles, Longbowmen), Legion (mid: faster
building, tougher infantry, Legionaries and Shieldbearers; cavalry costs more),
Jarls (early: fast infantry that burn buildings, Berserkers from Banner;
castles and smithing cost more), Shogunate (late: faster melee, tough
villagers, Samurai; soldiers train slower), Khanate (early: fast cavalry, cheap
stables and ranges, bigger yurts, Horse Archers and Cataphracts; flimsy
buildings, dear walls), Ascendancy (late: an off-world expedition — villagers
carry more, buildings self-repair, Pulse Troopers and Skimmers; soldiers cost
15% more). Nothing magical; the Ascendancy's glow is engineering. No new
resources — factions change how things look and play, not what is gathered.

- **They look like themselves.** `render/faction_art.ts` restyles the shared
  building parts (walls, roofs, caps, doors, windows, standards) and
  `render/faction_figures.ts` redraws the whole soldier — back gear, legs,
  body, face, weapon, bow, mount. Terracotta and capes; stave halls, beards and
  axes; curved eaves, back-banners and hakama; felt yurts, robes and quivers;
  white domes, hardsuits, rifles and hover-sleds. The first pass only changed
  helmets and read as a colour swap. Team colour stays on something big —
  roof, cape, flag, robe, band — and `factions.test.ts` checks every faction's
  Town Centre still shows it (the first Ascendancy dome failed: 0 pixels).
- **The meta systems carry across.** Rarity is by *role* too: a unit's
  `role` names the shared unit it stands in for, and the higher of the two
  rarities applies — an unboxed Very Rare Man-at-Arms makes the Legionary Very
  Rare. Commanders, boons and rarity all apply to faction and Oath units, and
  every new unit is in the War Chests with a named top-rarity variant. Each
  faction remembers the commander last led with it.
- **Balance** is measured by AI round-robin — see below.

**The AI in the late game.** Measured first: in nine AI-vs-AI duels, *no* AI
reached the third age in thirty minutes; most sat in the second with a
thousand gold banked and no food. Three causes, none about Oaths: food
villagers walked to the nearest berry bush *anywhere* before a farm; several
were sent to one farm that only one can work; and a Town Centre hemmed in by
its own buildings could leave its nearest open cell sealed off, so every
food-carrier heading home was "provably unreachable" and stood still for the
rest of the match (one AI gathered zero food for twelve minutes). Fixed in the
AI (`foodNode`, farm count, saving once an age has been *ready* for 90s, buying
food at the Market) and in A* (`findPath` retargets a blocked goal to a
reachable open cell when the nearest one isn't — only then, so every path that
already worked is unchanged). Same nine seeds afterwards: three reach Empire.

**Balance, measured.** Faction round-robin, AI vs AI, every pairing both
seatings on Open Plains, Highlands and Riverlands (90 games, 25-minute cap,
a draw counts half): Legion 58%, Ascendancy 57%, Jarls 48%, Kingdom 47%,
Khanate 47%, Shogunate 43%. Everything inside 40–60%, and the curves show
where intended: the Jarls and Khanate win *early* when they win (13 and 12
minutes on average), the Kingdom and Shogunate late (18–19). Tuning that got
there: the Legion lost an infantry-HP bonus and its build speed went 1.2 →
1.1 (it was at 70–80%); the Shogunate gained 8% soldier HP; Kingdom knights
10% cheaper; Jarl smithing 10% dearer. Banner Oaths, same method: Sword 59%,
Plough 47%, Hearth 44%. New units in equal-budget arena duels: 17–65%.
Most AI games still run to the cap — AI-vs-AI measures shape, not a finish.

## Teams, set up on purpose

Teams used to be one "Even Teams" switch that split seats by parity — 1, 3, 5,
7 against 2, 4, 6, 8 — so who fought beside whom looked random. Now every seat
has a team (1–8) or none (`ui/teams.ts`), with presets that fill in organised
blocks: two teams of 8 is seats 1–4 against 5–8, three teams is 3/3/2, four is
pairs, co-op is you and N allies against the rest. Any seat can then be moved
by hand; the header reads "4 v 4" or "3 v 3 v 2". A layout with one side
can't be started. The Skirmish screen is two columns now (battlefield and
options; roster and your realm) with a pinned action bar.

**Online, the same.** The relay (`server/server.mjs`) keeps a team per
player instead of Side A/B: the host picks a layout (free-for-all, 2/3/4
teams — blocks in join order) and can move anyone; players can join any team
or start a new one. The start message carries alliances from teams (solo
players get their own), each player's faction (online matches were
all-Kingdom), and the host's battlefield. Older clients' `side` still works.
`net/lobby_teams.test.ts` drives it with real sockets.

## The map pool — publishing your maps

A map is a **draft** until its author **publishes** it (editor top bar, or
from the library list). Publishing needs a map with no errors; it puts the
map in the pool beside the built-in battlefields — on the Skirmish list, in
the **Random** roll, and in the host's choices online. Drafts stay in the
editor (Test map still works on them). Maps saved before publishing existed
come back published, since they were already on the Skirmish list; an
imported map arrives as a draft. The published flag lives in the library
entry, not the share code; the author's name travels in the code (`au`).

Random stays replay-safe: each built-in battlefield and each fitting
published map gets one chance from the seed; a built-in result is still
"random" (the generator rolls it exactly as before), a published map comes
back as its id and is written into the config, so a save records it.

**Online, a community pool.** Players can publish their maps to the server
they play on; it keeps them in `community-maps.json` beside it (or
`MAPS_FILE`), deduplicated by content, capped at 500, also at `GET /maps`.
The host picks from built-in, their own published maps, or the server's pool;
the map's code travels in the start message, so nobody else needs a copy —
and anyone can "Keep a copy" of a community map into their own pool.

## The menus

The main menu keeps the dusk, the castle and the gold title and loses the
column of emoji buttons: a profile card, a **Your realm** card (faction,
era, commander, and one of its soldiers drawn live), a Continue card, one big
Skirmish tile and illustrated tiles for everything else — icons drawn in the
game's own style (`ui/menu_icons.ts`) rather than emoji that render
differently everywhere. Panels and buttons got depth (shadow, gradient, gold
inset line). The lobby overlay was restyled to match.

**A bug found by looking.** Driving the built game in Chromium with four
clients: one click on Multiplayer opened the lobby *twice*. Mouse-up is heard
on the whole window (so drags can end off the canvas), so clicking anything
on a DOM overlay also clicked the canvas button drawn beneath it. A click now
has to start on the canvas (`input.test.ts`), and the lobby refuses to stack.

## Factions are owned

A new player's first stop (after claiming a commander) is the **Factions
book**: pick one faction, free. Every other costs **1500 renown** — the same
currency War Chests take — and is bought from the book. Locked factions show
a lock and the price on the Skirmish screen (clicking one opens its page), and
only owned factions can be chosen in online lobbies. AI opponents still use
all six. A save replays with the faction it was played with.

The book (`ui/faction_book.ts`) is the encyclopedia: for each faction a
diorama of its town and soldiers, difficulty (forgiving → punishing), its
power early / mid / late, how it plays, bonuses and their price, who it
suits, its own soldiers drawn with their numbers, how to win with it, and
what gives it trouble — the guide text lives with the faction
(`FactionDef.guide`) and was checked against the real numbers (the first
draft claimed Longbowmen out-range everything; the Longbow unit doesn't).

## Online: one website

The game and the server are one deployable thing now. `server.mjs` serves
the single-file build at `/`, and the game, when it was loaded from a
website, connects back to that same address — `wss://` under `https://` for
free, nothing to type. `Dockerfile` (two stages: build the game, then Node
plus two files), `fly.toml`, and `render.yaml` at the repo root.

**The hub** (protocol 2): live room list pushed to everyone browsing it,
create (name, password, size 2–16) and join rooms, watch open ones, quick
match (1v1, 2v2, 4-FFA — seats whoever is waiting, picks a built-in field,
counts down, starts), per-room chat (rate-limited), host kick, and return to
the hub after a match. **Ranked**: quick matches are rated by Elo when every
remaining player's report of the winner agrees; disagreement rates nothing;
a quitter loses. Identity is a random per-browser id plus a name — no
accounts. **Hardening**: per-connection token-bucket rate limit, 1 MB frame
cap, heartbeat that frees dead seats within ~45 s, cleaned names, protocol
version check that asks an old page to reload, per-instance state (tests
run several servers side by side).

Verified the way a player would: the Docker runtime image serving the build,
two separate Chromium profiles opening the site, each picking a free faction,
queueing for a 1v1, being matched, counting down and playing into the match
in lockstep with no page errors (`/healthz` reported one game, two players).
The image's build stage couldn't run here (no package registry inside the
sandbox's containers) — it's the ordinary `npm ci && npm run build:single`.

**Not yet**: server-side accounts (renown, unlocks and War Chests live in the
browser and aren't verified — fine while online play is all-Common, needed
before anything bought affects ranked play); reconnecting to a match in
progress (lockstep would need the order log replayed to the returning
client); more than one server instance.

## The admin dashboard and a production server

Every online match is recorded (`MATCHES_FILE`, append-only JSON lines):
kind, map, format, sides with names and factions — "Random" resolved by the
clients' own report — winner (only when reports agree), game-clock and
wall-clock length, quitters, desyncs. A per-minute activity sample is kept
too (`SAMPLES_FILE`). `/admin` (token: `ADMIN_TOKEN`, ten wrong guesses per
address per ten minutes) turns it into the operator's view: live counts,
faction pick and win rates, a 1 v 1 match-up grid, maps, game lengths,
formats, activity by day / hour / hour-of-day, players and ratings, rooms
with a close button, recent matches, server health, announcements, CSV
export. `src/net/admin.test.ts` checks the numbers against matches played
through a real server.

Production: the game page is gzipped (712 KB → 233 KB) with an ETag; data is
written atomically; the server starts as root only to take ownership of the
Fly volume and then drops to `node` (Node's own setuid — no extra packages);
`fly.toml` has an always-on machine, a volume, SIGTERM with a 10 s grace, and
the health check. Verified: the full two-stage image built here (through a
mirror of the official Node images — Docker Hub was rate-limiting this
sandbox), run with a root-owned volume, served the game, two Chromium
players played a quick match through it, the match appeared in the admin
API with both factions, and the container stopped cleanly with its data on
disk. A 400-player load test peaked at 10 ms event-loop lag and 84 MB.

## Career — the player's own stats

`meta/career.ts` + `ui/career_screen.ts`. Every finished match — skirmish or
online, ranked or not — is kept from the player's side: faction, commander,
Oaths, map, format, allies' and enemies' factions, length, when each age was
reached (new: `PlayerState.ageTimes`, reported in `PlayerReport.ageTimes`),
resources, kills and losses, damage, peak army, idle time, and units trained,
lost and killed by type. Two stores: lifetime totals updated match by match
(exact forever) and a log of the last 400 matches (for the skirmish / online /
ranked filters and the match list). Storage-full degrades to fewer logged
matches, never lost totals.

The screen (menu tile "Career", or click the profile card): Overview —
record, win rate and streaks, time played, average game, **average win time**
and fastest win, K:L, favourite faction / map / unit / commander / Oath, best
and weakest faction and map, easiest opponent and nemesis, recent form with a
rolling win-rate line, per-game averages, share reaching each age and how
long it takes, skirmish vs online vs ranked, and against each AI. Factions —
per faction W–L, win rate, average game, average win, fastest win, K:L and
favourite unit with it; your record against each enemy faction and beside
each ally. Maps & modes — the same by map, format, mode, commander and Oath.
Units — trained most, enemy killed most, lost most, and every unit's trained
/ lost / killed-of-its-type with survival rate. Matches — the list, with a
tooltip per game.

## Caster mode and replays

Every way of watching — an AI game from the Skirmish screen ("Watch"), an
online room joined with "Watch", or a replay — now uses one caster view
(`ui/caster.ts`), built from what the big competitive RTS observer tools
have: a player bar (colour, name, faction, age, Oaths, resources,
population, army value) with the clock and LIVE / DELAY / REPLAY in the
middle; tug-of-war bars for army, economy and kills by side; a bottom panel
with Overview, Army, Economy, Production (every building's current item with
progress) and Tech & Oaths (Q W E R T / Tab); vision switching (1–8 one
player's fog, 0 everything); an auto-director that cuts to the hottest fight
(deaths and collapses decaying over a few seconds), follows it, tours the
bases when it's quiet and waits ten seconds whenever the caster moves the
camera; an event feed (first blood, battles, ages, Oaths, fallen Town
Centers and castles, eliminations, the winner) that jumps the camera when
clicked; fight markers on the minimap; army-value and score graphs (G); a
clean feed for streaming (H); speed up to 16× offline; a help card (?).

**Replays.** Every match is recorded when it ends — a skirmish (its setup,
now including the human's rarities and boons, plus the human's orders; the
AIs replay themselves), an AI game you watched (setup only), an online match
(every order the lockstep applied, from everyone). Twelve are kept; Career →
Replays watches one with the caster view and a timeline: pause, 0.5–16×, and
click to jump (backwards re-runs from the start, a slice per frame so the
screen never freezes). Verified: a replay reproduces the live game's numbers
exactly at the same moment; `caster_sync.test.ts` rebuilds an online match
from its record to the same checksum.

**Broadcast delay.** An online caster picks Live, 30 s, 1, 2 or 5 minutes in
the lobby, and simulates that far behind the players (`stepReady` holds back
against `Lockstep.readyThrough()`), so a stream can't be used to scout.
That needed one lockstep fix: a player who drops used to have *all* their
buffered turns ignored from the moment the drop arrived, so a caster
receiving the drop minutes "early" would have skipped orders the players had
applied. Now a dropped player's turns that arrived still count, for
everyone; only later ticks stop waiting for them. Tested with a delayed
caster through a mid-match disconnect, and in the browser: 48 s after the
start, a 30 s caster read 0:18. Online starts now carry player names.

## Per-unit stats, placement, and the small things

**Per-unit stats.** The sim now credits each kill, each point of damage and
each razed building to the *attacking unit's type* (`stats.killsByUnit /
damageByUnit / razedByUnit`), carried through the match report into the
Career: per unit, trained / lost / kills / K:D / damage / razed / win rate in
games you used it, plus "deadliest", "best trader" (highest K:D, 10+
trained) and "you lose most". Cost, measured A/B on the same seeded 4-AI
match for 8 game-minutes: 0.873 vs 0.877 ms per tick — noise; identical
kills and damage. Career portraits are now fitted and clipped to their box
(mounted units spilled out of the cards).

**Placement.** `World.placementProblem` is the single source of truth — the
same checks `placeBuilding` makes, returning a reason ("Needs the Crown
Age", "Need 27 more wood", "Blocked — trees, rocks or a building in the
way", "Units are standing there", "Overlaps a farm"); a test places 400
buildings and checks the preview and the sim never disagree. The ghost is
the real building art, translucent, on a tile grid, drawn above the fog; a
cursor panel shows the name, the *real* (discounted) cost, the reason it
won't fit, how many villagers will build it (or that none is selected), and
for Lumber Camps, Mining Camps and Mills the resources within reach (ringed
on the map). Walls stay armed on a click as well as a drag; wall/house drags
check a running budget and report "Placed 6 of 9 — out of resources"; dotted
lines briefly show who's walking to a new site. Hidden over the HUD.

**Interface.** Production queues show every item — click one to cancel it,
refunded (new `cancel` command, lockstep-safe; age advances too, dropping
their Oath). Greyed buttons say why ("⚠ Need 40 more wood", "Population
capped — build a House"). A unit finishing at the pop cap now says so
instead of vanishing silently. Alerts that happened somewhere are links —
click to jump — and a repeated alert refreshes instead of stacking. In a
mixed selection, click a type chip to keep only those, Shift+click to drop
them. Drag across the minimap to sweep the camera; middle-mouse drag grabs
the map. The cursor shows what a right-click would do (attack, gather,
build/repair). Every selected building's rally line is drawn. Escape closes
the build menu; B with no villager says so; right-clicks and drags that end
on the corner chips no longer fall through to the map.

## Farms that just work, stats you can trust, a style in your own words

**Farms.** Whoever builds a farm now farms it (the first to finish claims it,
co-builders take the nearest free one); a Lumber Camp, Mining Camp or Mill
sends its builder to the wood, gold or berries beside it. A replanted field is
worked again by the same farmer. A villager bumped off a taken farm goes to
the nearest *free* farm or nearby berries — never a gold mine across the map —
and a field that runs out with nothing to replant finds the nearest work.
Clicking anywhere on a farm's square counts (it used to need the centre).
Unworked farms pulse on the map. Idle villagers no longer go looking for a
fight: a crew that finished a bridge used to spend minutes hacking at the
enemy's bridge (it stalled one AI economy — found by the scripted-map test).

**Attack vs defence.** The map is split into territory (4×4-tile cells owned
by the nearest building in reach — TC 18 tiles, castle 14, others 9, rebuilt
every 2 s), and every blow is classed by whose ground it landed on: *attack*
(theirs), *defend* (yours) or *field*. Recorded per realm: first attack /
defence / field hit, damage and kills in each, and the "opener" — damage by
unit type away from home in the first 10 minutes. "Rush" now means your first
**attack on their base** landed before 7:00; beating off their rush at home no
longer counts. Old records fall back to "first hit". Proven in
`attack_defend.test.ts` (at their TC = attack for you, defence for them; at
yours = defence; mid-map = field; kills split the same way; razes and kills
credited to the right unit; hit times dated correctly).

**Styles built from words.** The rush title comes from what actually fought
early: the dominant unit ("Man-at-Arms Rush"), else the class ("Archer Rush",
"Cavalry Rush"), else "Mixed Rush". New archetype *Early Pressure, Late
Finish* for players who attack early but win long ("Archer Harass, Late
Closer"). A keyword row sums the player up — tempo (Early attacker /
Mid-game / Slow starter), opener, army, game length, and what stands out
(Demolition, Villager hunter, Fortifier, Strong economy, Tech-heavy,
Efficient fighter).

**Feedback.** After every match a *Feedback* tab lists what went well and
what cost you, each a comparison with the realm(s) you played: income, idle
Town Center and villagers, unspent bank, kills per loss, villagers lost and
killed, whether you held their early attack and whether yours paid off,
buildings razed/lost, Banner Age timing, research, the unit that carried it.
The career's Playstyle page has the same for the whole record ("Your games,
honestly"): economy, TC idle, trades, how you do when rushed, early losses,
long games, research, Crown Age timing.

## Nemesis — a rival who remembers you

Borrowed from action RPGs (Shadow of Mordor's Nemesis system), not from
other RTSs: one named rival warlord, with a rank, a faction, scars and a
memory, on its own menu tile. It learns a layer each time you meet — after
one battle your army (it brings the counter before it has scouted a
soldier), after two your timing (an early attacker finds it walled up and
turtling; a slow builder gets rushed; a mid-game player gets out-built),
after three how you win (your playstyle title). Everything it knows is read
from the career record, so the playstyle analysis *is* its brain.

Lose to it and it climbs a rank (Captain → Tyrant; Knight → Conqueror
strength) and earns a name for how it beat you (the Swift, the Burner, the
Patient, the Butcher). Beat it and it flees with a scar and a grudge against
the unit class that did most of the killing — next time it brings their
answer. Three defeats end it (+250 renown on top of 60 × rank), and one of
its captains rises to replace it, keeping the grudge; the fallen are listed.
It taunts you in chat at the start of the battle with what it remembers, and
the result banner says what became of it. The adaptation goes into the match
config, so saves and replays rebuild the same rival. `SkirmishAI.adapt()` is
the hook: a style, an expected army (a floor under its scouting) and walls.
Tests: `meta/rival.test.ts` (learning order, rank/epithet, scars, the heir,
counters it brings, an adapted AI plays a full economy).

## Bigger / later
- **Naval** — water is currently only an impassable wall, and the Islands
  preset (55% water) is a maze rather than a naval map. Dock, transport,
  war galley + AI. The largest genuinely-missing pillar.
- **Battlemage** and **Trade Cart** remain the open content items.
- **Accounts** — server-side profiles, so renown, unlocks and collections
  follow a player between devices and can be trusted in ranked play.
- **Reconnect** to a match in progress.
