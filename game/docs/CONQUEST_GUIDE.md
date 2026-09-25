# Banner & Blade — Conquest Mode: Agent Primer

A complete, mechanics-accurate overview of the standard **Conquest** game for a
learning agent. All numbers are pulled from the game's content/sim and are the
ground truth the simulation runs on (`src/content/*`, `src/sim/world.ts`).

The simulation is **deterministic** and runs at a fixed **20 ticks/second**
(`SIM_HZ = 20`). Same seed + same commands ⇒ same outcome (good for replay and
reproducible training).

---

## 1. Purpose & Win Condition

It is a top-down medieval real-time-strategy game (Age-of-Empires-like). You
command one realm: gather resources, grow a town, climb the Ages, build an army,
and destroy the enemy.

- **You win Conquest when you are the last alliance standing.**
- **A team is eliminated only when it has ZERO buildings AND zero villagers**
  (`checkVictory`). A razed base with a surviving villager can rebuild — so
  fully erasing the opponent (or at least their ability to rebuild) is the goal,
  not just killing their army.
- Practical target: destroy the enemy's **Town Center**, production buildings,
  and **kill their villagers** so they can't recover.

The core loop every game: **Gather → Build economy → Advance Age → Train army
that counters the enemy → Attack / defend → Eliminate.**

---

## 2. Resources (three)

| Resource | Gathered from | Drop-off buildings |
|---|---|---|
| **Food** | Berries (forage), Hunt, **Farms** (renewable/infinite) | Town Center, **Mill** |
| **Wood** | Trees | Town Center, **Lumber Camp** |
| **Gold** | Gold mines (finite veins), Market trade | Town Center, **Mining Camp** |

- **Starting resources:** Food 200, Wood 200, Gold 100 (`START_RESOURCES`).
- **Gathering:** a villager collects `0.45` units per gather action every `0.6 s`
  → **~0.75 units/sec** at base, carries up to **10**, then walks to the nearest
  valid drop-off and deposits. **Walk distance is the main efficiency lever** —
  build drop-off camps next to the resource so villagers barely move.
- Berries/gold veins are **finite and deplete**; trees fall as they're chopped.
  **Farms are infinite** (cost 60 wood, tick ~10% slower than foraging) — the
  sustainable late-game food source. Transition onto farms before berries run
  dry or you starve your unit production.
- **Gold is the scarce resource**: most strong units need gold (Knights,
  Crossbows, Hand Cannoneers, Two-Handers, Monks, siege). Secure gold mines and
  research **Gold Mining**; build a **Market** to trade surplus food/wood for
  gold when mines run out.

---

## 3. Population

- Population cap starts at **10** (your Town Center provides 10).
- Each **House** adds **+10** pop. **Hard cap = 200** (`POP_CAP_HARD`).
- Almost every unit costs **1 pop** (Catapult/Ram = 2, Champion/Trebuchet = 3).
- **Being pop-blocked = lost production time.** Build Houses *ahead* of the
  curve so you always have headroom to train.

---

## 4. Ages (tech tiers)

Three ages. Advancing is researched at the **Town Center**, costs resources, and
requires you to already own at least 2 *distinct* building types from a set (so
multiple opening styles all reach the next age).

| Age | Cost | Requires (≥2 distinct of) | Research time | Unlocks (highlights) |
|---|---|---|---|---|
| **0 — Dark Age** | free (start) | — | — | Villager, Scout, Champion, Man-at-Arms, Spearman; House, Mill, Lumber/Mining Camp, Farm, Barracks, Palisade, Watchfire |
| **1 — Feudal Age** | 300 food | barracks, mill, lumber_camp, mining_camp | 30 s | Archery Range, Stable, Blacksmith, Market, Watch Tower, Stone Wall, Gate; Archer, Skirmisher, Horseman, Javelin, Raider; Feudal upgrades |
| **2 — Castle Age** | 500 food + 200 gold | barracks, archery_range, stable, blacksmith, market, watch_tower | 40 s | Castle, Siege Workshop; Knight, Crossbow, Hand Cannoneer, Two-Handed Swordsman, Pikeman, Monk, Catapult, Ram, Trebuchet; Castle upgrades |

**Each age also grants a flat stat bump** to all your units:
`AGE_ATTACK_BONUS = [0, +1, +2]` and `AGE_ARMOR_BONUS = [0, 0, +1]` by age index.
Teching is both an unlock and a power spike.

---

## 5. Buildings

Costs are (food / wood / gold). "Drop-off" = villagers deposit that resource here.
All buildings are built by **villagers**.

| Building | Age | Cost | HP | Role / Produces |
|---|---|---|---|---|
| **Town Center** | 0 | 0/350/0 | 2400 | Heart of the realm. Trains **Villager, Scout, Champion**; drops off **all** resources; +10 pop; shoots arrows (atk 8, rng 180); garrison 12. Researches Ages. Losing all TCs is usually fatal. |
| **House** | 0 | 0/30/0 | 480 | +10 population cap. Build proactively. |
| **Mill** | 0 | 0/80/0 | 500 | Food drop-off; anchors farms/berries. Researches **Horse Collar** (+18% food). |
| **Lumber Camp** | 0 | 0/80/0 | 480 | Wood drop-off; plant in the trees. Researches **Bow Saw** (+18% wood). |
| **Mining Camp** | 0 | 0/80/0 | 480 | Gold drop-off; build on a vein. Researches **Gold Mining** (+18% gold). |
| **Farm** | 0 | 0/60/0 | 240 | Renewable/infinite food. The sustainable food economy. |
| **Barracks** | 0 | 0/160/0 | 1000 | Trains infantry: **Man-at-Arms, Spearman**, later **Two-Hander, Pikeman**. |
| **Archery Range** | 1 | 0/175/0 | 1000 | (needs Barracks) Trains **Archer, Skirmisher, Javelin**, later **Crossbow, Hand Cannoneer**. |
| **Stable** | 1 | 0/175/0 | 1000 | (needs Barracks) Trains **Horseman, Raider**, later **Knight**. |
| **Blacksmith** | 1 | 0/150/0 | 1000 | Researches attack/armor/eco upgrades (see §8). No units. |
| **Market** | 1 | 0/175/0 | 900 | Trade resources for gold; smooths shortages late. |
| **Watch Tower** | 1 | 0/50/25 | 1020 | Defensive tower (atk 10, rng 200, fast). Garrison 5 to add arrows. |
| **Watchfire** | 0 | 0/40/0 | 360 | Wide sight (320) that the night can't dim. Vision/early-warning, no attack. |
| **Palisade** | 0 | 0/5/0 | 250 | Cheap wood wall segment. Throw up fast to wall off / screen villagers. |
| **Stone Wall** | 1 | 0/12/4 | 1800 | Tough wall segment; fortress backbone. |
| **Gate** | 1 | 0/30/0 | 1600 | Wall opening that lets your units through but bars enemies. |
| **Siege Workshop** | 2 | 0/200/0 | 1000 | Trains **Catapult, Battering Ram, Trebuchet**. |
| **Castle** | 2 | 0/200/400 | 3600 | Fortress: heavy arrows (atk 22, rng 240, very fast), garrison 20; trains **Monk**. Anchors a position. |

---

## 6. Units

Costs (food/wood/gold). `Trained at` and `Age` gate availability. `Bonus` is
**extra attack damage vs a specific armor class** — the counter system.

| Unit | Age | Trained at | Cost | HP | Atk | Rng | Spd | Armor | Role & key bonuses |
|---|---|---|---|---|---|---|---|---|---|
| **Villager** | 0 | Town Center | 50/0/0 | 40 | 3 | 0 | 70 | 0 | Gather + build + repair. Helpless in combat. |
| **Scout** | 0 | Town Center | 65/0/0 | 45 | 3 | 0 | 132 | 0 | Fast eyes (vision 230). Scout/harass; not a real fighter. |
| **Man-at-Arms** | 0 | Barracks | 60/0/20 | 70 | 8 | 0 | 78 | 1 | Cheap frontline. +4 vs archers, +2 vs buildings. Melts to massed cavalry. |
| **Spearman** | 0 | Barracks | 35/25/0 | 60 | 5 | 0 | 80 | 0 | **Anti-cavalry** (+25 vs cavalry!), +5 vs siege. Weak vs everything else. |
| **Champion (Hero)** | 0 | Town Center | 150/0/120 | 230 | 12 | 0 | 96 | 2 | Unique hero, **levels up on kills, respawns** at your TC. One per player. Strong but don't over-rely. |
| **Archer** | 1 | Archery Range | 25/45/0 | 38 | 6 | 150 | 80 | 0 | Ranged DPS, +3 vs infantry. Fragile; dies to cavalry. |
| **Skirmisher** | 1 | Archery Range | 35/35/0 | 42 | 4 | 130 | 80 | 1 | **Anti-archer** (+6 vs archers), cheap (no gold). Poor vs melee. |
| **Javelin Thrower** | 1 | Archery Range | 40/0/30 | 48 | 9 | 105 | 84 | 1 | Hits hard, short range, +8 vs cavalry. Punishes chargers. |
| **Horseman** | 1 | Stable | 70/35/0 | 80 | 9 | 0 | 112 | 1 | Light cav, arrives a whole age before Knights. +6 vs archers, +10 vs siege. Spears gut it. |
| **Raider** | 1 | Stable | 60/0/15 | 55 | 7 | 0 | 135 | 0 | Very fast, **+14 vs villagers** — economy raider. Strike-and-run; fragile in a fair fight. |
| **Knight** | 2 | Stable | 70/0/75 | 130 | 12 | 0 | 115 | 2 | Heavy cav. Runs down archers/siege/villagers (+6/+8/+6). **Hard-countered by spears/pikes.** |
| **Crossbowman** | 2 | Archery Range | 0/25/55 | 45 | 9 | 170 | 78 | 1 | Heavy ranged backbone; outranges Archer. +4 vs infantry. |
| **Hand Cannoneer** | 2 | Archery Range | 0/30/70 | 45 | 22 | 195 | 76 | 1 | Gunpowder; huge range + big hit, slow reload. +10 vs infantry. Devastating massed, helpless if rushed. |
| **Two-Handed Swordsman** | 2 | Barracks | 70/0/30 | 110 | 13 | 0 | 80 | 2 | Elite infantry; cleaves infantry + buildings. Still wary of massed cavalry. |
| **Pikeman** | 2 | Barracks | 40/25/0 | 75 | 6 | 0 | 80 | 1 | Upgraded anti-cav (**+38 vs cavalry**), keeps spears relevant vs Knights. |
| **Monk** | 2 | Castle | 0/0/100 | 45 | 0 | 0 | 70 | 0 | Heals nearby allies over time. No attack. Force multiplier behind a line. |
| **Catapult** | 2 | Siege Workshop | 0/160/80 | 90 | 24 | 200 | 45 | 1 | Long-range **siege**, AoE (r36), +40 vs buildings, +8 vs clumped infantry/archers. Helpless up close. |
| **Battering Ram** | 2 | Siege Workshop | 0/160/40 | 200 | 6 | 0 | 50 | 4 | Armored wall/building breaker (+50 vs buildings). Tanky vs arrows, clumsy vs troops. |
| **Trebuchet** | 2 | Siege Workshop | 0/200/200 | 120 | 55 | 340 | 34 | 2 | Colossal range; levels walls/towers from beyond their reach (+110 vs buildings). Glacial, helpless up close. |

(*King* exists only in the Regicide mode, not Conquest.)

---

## 7. Combat Model

Per hit:

```
raw    = attacker.attack + age_attack_bonus + blacksmith_attack_upgrades
       + bonus[target.armorClass]            // the counter bonus, added BEFORE armor
armor  = target.(armor | pierceArmor) + age_armor_bonus + armor_upgrades
damage = max(1, raw - armor)                 // always at least 1
```

- **Armor classes:** infantry, archer, cavalry, siege, building, villager.
- **Melee vs pierce armor:** ranged hits subtract the target's `pierceArmor`;
  melee hits subtract `armor`. (For most units they're equal; some cavalry have
  light pierce armor to shrug a bit of arrow fire.)
- **Bonus damage is huge and is what makes counters work.** Example: Spearman
  base atk 5, but **+25 vs cavalry** ⇒ 30 − Knight armor 2 = **28 per hit** to a
  130-HP Knight. Pikeman is +38. This is why cavalry must avoid spears.
- **Siege AoE:** Catapult/Trebuchet damage everything in a radius — brutal vs
  clumped armies and buildings, so spread out against them.
- **Ranged units** have projectile travel + reload (attackInterval). Kite melee;
  never let cavalry reach your archer line.
- **Buildings (TC, Towers, Castle) shoot arrows** and can be **garrisoned** to
  add more arrows — defenders have a real edge.

---

## 8. The Counter Triangle (memorize this)

- **Infantry** (Man-at-Arms/Two-Hander) beat **Archers** and are cheap vs
  buildings → but get run down by **Cavalry**.
- **Cavalry** (Horseman/Knight/Raider) beat **Archers, Siege, and Villagers**
  (fast flank/raid) → but are **gutted by Spearmen/Pikemen**.
- **Spearmen/Pikemen** beat **Cavalry** hard → but are weak vs **Archers** and
  general infantry.
- **Archers** beat **Infantry/Spearmen** at range → but die to **Cavalry** and
  to **Skirmishers**.
- **Skirmishers** beat **Archers** → but are poor vs everything melee.
- **Siege** beats **Buildings** and **clumped units** → but is helpless vs fast
  units up close (especially cavalry).
- **Monks** heal; **Champion** is a strong solo brawler that snowballs on kills.

**Golden rule:** scout what the enemy makes, then train its counter. A pure
single-unit army always loses to its counter.

---

## 9. Upgrades (Blacksmith + gathering buildings)

Vertical progression that makes late armies feel earned. Researched while you
keep producing.

- **Attack:** Forging (+2 melee, Feudal), Fletching (+1 ranged, Feudal),
  Iron Casting (+2 melee, Castle), Bodkin Arrow (+2 ranged, Castle),
  Long Swords (+3 to Man-at-Arms/Two-Hander), **Pikes** (+12 bonus vs cav to
  Spear/Pike — stacks the anti-cav role even higher).
- **Armor:** Scale Mail (+1 melee armor), Padded Archer Armor (+1 ranged armor),
  Chain Mail (+1, Castle).
- **Cavalry:** Husbandry (+speed), Bloodlines (+20 HP).
- **Economy:** Wheelbarrow (+15% gather), Hand Cart (+15% more), and per-resource
  Horse Collar (food +18%, at Mill), Bow Saw (wood +18%, at Lumber Camp),
  Gold Mining (gold +18%, at Mining Camp).

Economy upgrades usually pay for themselves quickly — high value early.

---

## 10. Economy Strategy (the part that wins games)

Efficient resource use beats brute force. Key levers:

1. **Keep the Town Center always producing villagers** until you have ~enough
   workers (commonly 25–40+), and **never sit pop-blocked** (build Houses early).
2. **Place drop-off camps ON the resource** (Lumber Camp in the trees, Mining
   Camp on the vein, Mill by berries/farms) to cut villager walk time — this is
   the single biggest eco efficiency gain.
3. **Balance gatherers** to what you're spending: food fuels villagers/most
   units, wood fuels buildings/archers/siege, **gold gates your strongest
   units** — don't let any one stall production.
4. **Transition to farms** before berries deplete; farms are infinite and
   raid-safer. A sustained army needs a farm economy.
5. **Expand** to a second Town Center / new resource patch when safe — more
   drop-off points and worker capacity.
6. **Protect villagers** — they are the economy. Enemy Raiders/Knights will try
   to kill them; wall the eco, garrison into the TC under attack, keep defenders.

---

## 11. A Solid Opening (build order skeleton)

A reliable Dark→Feudal→Castle plan (adapt to the map and what you scout):

1. Send the 3 starting villagers to **food** (berries/hunt). Queue villagers
   from the TC continuously.
2. Build a **House** before you hit the pop cap; keep building them ahead.
3. Put new villagers on **wood**; drop a **Lumber Camp** in the trees.
4. Build a **Mill** by berries and a **Mining Camp** on gold; spread workers as
   you need each resource.
5. Train a **Scout** and explore — find the enemy and their resources.
6. With ≥2 of {Barracks, Mill, Lumber, Mining} and 300 food, **advance to
   Feudal**. Add **Barracks** and start a few **Spearmen/Man-at-Arms** for safety.
7. In Feudal: add **Blacksmith** (eco + combat upgrades), **Archery Range** or
   **Stable** depending on the matchup, and a **Watch Tower** at a choke. Start
   **farms**.
8. With ≥2 Feudal buildings + 500 food + 200 gold, **advance to Castle**.
9. In Castle: build the army that **counters what you scouted** (Knights vs
   archers, Pikes vs their Knights, Crossbows vs infantry, Siege vs walls/towers),
   keep upgrading, and push to **eliminate** their base and villagers.

---

## 12. Military, Scouting, Harass, Siege, Defense

- **Scout constantly.** Information drives the whole counter game. Fog of war
  has three states (unexplored/explored/visible); buildings are remembered where
  last seen.
- **Power spikes:** mass an army, then attack when you out-tier or out-number,
  especially right after an Age-up or key upgrade.
- **Harass:** **Raiders** (and Knights) into the enemy economy kill villagers and
  buy you tempo. Defend your own eco against the same.
- **Siege:** use **Rams/Catapults/Trebuchets** to break walls, towers, and the
  Town Center — but escort them; they die to anything fast up close.
- **Defense:** **Walls + Gates** to channel attackers, **Watch Towers/Castle** at
  chokes, **garrison** units into towers/TC/Castle to multiply arrow output, and
  keep a reaction force. Defender's advantage is real.
- **Retreat losing fights** — preserving units/eco beats trading badly.

---

## 13. Difficulty / Economy Handicaps (for self-play reference)

The built-in AI tiers scale **behavior first**, with only a mild economy
multiplier at the top (`AI_ECON_MULT`): squire 0.7, knight 1.0, lord 1.15,
warlord 1.35. Behaviorally: easy tiers don't scout/expand and spam one unit;
harder tiers scout, counter your composition, multi-prong attack, expand, and
harass. A genuinely good policy should beat the top tier on *play*, not stats.

---

## 14. Decision Heuristics for a Learning Agent

Compact priorities the reward/credit-assignment can lean on:

- **Never idle a Town Center** (train villagers/units) and **never sit
  pop-blocked** — both are pure lost economy. Cheap, high-value signals.
- **Keep all three resources flowing**; a stalled resource = stalled production.
  Watch for gold starvation specifically.
- **Workers are value:** more (protected) villagers ⇒ more everything. Killing
  enemy villagers / losing your own is a large swing.
- **Tech timing matters:** advancing an Age and getting eco/combat upgrades are
  power spikes; reaching Castle with an economy usually dominates.
- **Composition beats mass:** match production to *counter the scouted enemy
  army*; a mono-army is exploitable.
- **Map control:** secure resource patches with drop-off camps and expansions;
  vision (Scouts/Watchfires) de-risks everything.
- **Tempo vs. greed:** attack on power spikes, harass to deny eco, but don't
  trade armies badly — retreat preserves the lead.
- **Terminal objective:** to actually win you must remove **all** enemy buildings
  *and* villagers — finish the job, don't just win a battle.

Useful state to observe: per-resource stockpiles & gather rates, villager count
& allocation, pop used/cap, current age & in-progress research, building counts,
own vs. scouted-enemy army composition (by unit/armor class), map vision, and
under-attack alerts. Useful rewards: economy throughput, army value traded,
villagers killed/lost, buildings razed/lost, age/upgrade progress, and the win.
