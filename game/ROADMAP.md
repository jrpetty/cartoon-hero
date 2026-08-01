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
- Backlog: **Battlemage** (Castle arcane caster — fireball / slow, "not too
  sci-fi"), **Trade Cart** (Market economy unit). Possible unique art passes for
  units currently sharing draws (twohand/militia, pikeman/spearman, scout/raider,
  javelin/skirmisher, handcannon/crossbow).

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

**Nothing in the sim kites.** Units close and trade, so massed archers lose a
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

## Bigger / later
- **Naval** — water is currently only an impassable wall, and the Islands
  preset (55% water) is a maze rather than a naval map. Dock, transport,
  war galley + AI. The largest genuinely-missing pillar.
- **Battlemage** and **Trade Cart** remain the open content items.
- **Multiplayer (PvP)** — the whole net stack (lockstep, WebRTC, WS, lobby,
  session) is written and unused. Needs a signalling server to host, so it
  can't live in the single-file build. Not now.
