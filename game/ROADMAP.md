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

## Upgrades (to design/build)
- **Unit-line upgrades** (research that improves a whole class): Long Swords,
  Pikes, Cavalier, Heavy Cav Archer, etc.
- **Economy upgrades** (Mill/Lumber/Mining/Market): Horse Collar (farm food),
  Bow Saw (wood), Gold Mining (gold), Caravan (trade), Loom (villager survive),
  Town Watch (vision), Treadmill Crane (build speed).

## Game modes (next big build — order)
1. **Survival / Horde** — hold out vs escalating AI waves; survive N waves to
   win. *Decision pending: solo only, or allow AI allies (co-op)?*
2. **King of the Hill** — hold the central hill for a cumulative timer to win.
   *Decision pending: hold time (~3 min?).*
3. **Regicide** — each side has a King; kill the enemy King to win, protect yours.

## Bigger / later
- **Multiplayer (PvP)** — the real replayability unlock. Deterministic sim
  already supports it architecturally. Not now.
- A real balance pass (Knight is likely a touch overtuned; widen the late-game
  unit meta beyond Knight + Spear/Pike).
