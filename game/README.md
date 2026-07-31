# ⚔ Banner & Blade

A stylized-fantasy real-time strategy game in the spirit of **Age of Empires** and
**Command & Conquer**, built for the browser. Skirmish-focused: you against an AI
commander on a fair, symmetric, procedurally generated battlefield — wrapped in a
CS-style **War Chest** collection meta where every match earns XP and Renown toward
unlocking rarer, stronger unit variants.

Everything — terrain, buildings, units, FX, audio — is generated procedurally in
code. No asset files, no downloads, no dependencies beyond the toolchain.

## Run it

```bash
cd game
npm install
npm run dev      # then open the printed URL (default http://localhost:5173)
```

Production build: `npm run build` (output in `dist/`), preview with `npm run preview`.

## Play it

**Goal:** raze every enemy building before they raze yours.

1. **Economy** — Villagers gather **food** (berries → farms), **wood** (trees) and
   **gold** (mines). Build drop-off camps near resources to cut walking time. Houses
   raise your population cap.
2. **Ages** — Advance Dark → Feudal → Castle at the Town Center. Each age unlocks
   units, buildings and flat combat bonuses.
3. **Counters** — Spearmen shred cavalry. Knights run down archers and siege.
   Archers melt infantry. Skirmishers out-trade archers. Catapults flatten
   buildings and clumps; rams eat walls. Mix your army or die to a hard counter.
4. **Skirmish AI** — Four tiers: **Squire** (learns you the ropes), **Knight**
   (honest opponent), **Lord** (scouts you, counters your comp, expands, raids),
   **Warlord** (all of that, faster, with a stronger economy).
5. **The Armory** — Matches pay **Renown**; spend it on War Chests that roll unit
   variants across six rarities (Common → One-of-a-Kind). Equip variants in the
   Collection to take stronger units into your next (non-Ranked) match.

### Controls

| Input | Action |
|---|---|
| Left click / drag | Select / box-select |
| Double-click | Select all of that unit type on screen |
| Right click | Context order: move, attack, gather, repair, garrison, rally |
| Shift | Queue orders, add to selection |
| A | Attack-move |
| S / H | Stop / Hold position |
| B | Build menu (villagers) |
| G | Ungarrison selected building |
| Ctrl/Alt+1–9 / 1–9 | Set / recall control group (double-tap to center) |
| WASD-ish + arrows, edge scroll, middle-drag | Camera |
| Mouse wheel | Zoom |
| Esc | Cancel placement / pause menu |

## Warband Tactics

A second mode: an **auto-battler** in the spirit of *Teamfight Tactics*, played on
the same deterministic combat sim. You draft a warband, place it on a 10×10 arena,
and watch it fight — the units, counters, armour and abilities are the real RTS
ones, not a separate ruleset.

**The loop.** Each round you take income (5 base + interest, 1 per 10 gold banked,
plus a win/loss-streak bonus), buy from a five-slot shop, and fight. Three copies
of a unit merge into a 2★; three of those into a 3★. Levelling costs gold, and
your **level is your board size** — so teching, rerolling and saving for interest
all compete for the same coins. Lose a fight and you lose life; last warband
standing wins.

- **Shared pool** — every warband in the lobby draws from one finite pool of unit
  copies, so contesting a comp genuinely starves your rivals (and you).
- **Synergies** — deploying enough *distinct* types of a trait (Footmen, Marksmen,
  Riders, War Engines, Pikes, Elite) activates escalating buffs. Traits overlap,
  so drafting is a puzzle.
- **Relics** — earned every few rounds; stack up to three on one unit to build a
  carry.
- **Augments** — three times a run (rounds 2, 5 and 9) the run pauses and offers
  three of them, escalating silver → gold → prismatic. They bend the whole run:
  economy engines, warband-wide stat spikes, extra board slots, or a banner that
  makes a synergy count two units higher.
- **Monster camps** — the opening round and every fifth round after it are PvE.
  The camp is visible while you set up, no player life is at stake, and clearing
  it drops relics and gold.
- **Scouting** — click any rival in the standings to see the warband they'd field
  right now, their level and their live synergies, and adapt before you spend.

**Positioning matters:** drag units between the bench and your half of the board,
swap cells, and drop onto the sell box to cash out. Front-rank placement decides
who absorbs the charge.

## Test it

```bash
npm test
```

The suite covers the deterministic sim (pathfinding, combat math with armor and
bonus-damage counters, economy and pop accounting, fog of war, victory), the meta
(chest rarity distributions, duplicate refunds, XP/level curve, profile loadouts)
and two full **headless AI-vs-AI matches** that run the entire game loop with no
renderer.

## Architecture

- `src/sim` — deterministic fixed-tick (20 Hz) world: all rules live here. The
  human and the AI act through the same command API.
- `src/pathfinding` — A* for individual paths, shared flow fields for group moves,
  boid-style separation steering.
- `src/ai` — difficulty-scaled skirmish brain (economy + military managers).
- `src/content` — data-driven unit/building/age/upgrade definitions and the
  central `balance.ts`.
- `src/meta` — profile (localStorage), XP/Renown progression, rarity tables,
  chest rolls, collection/loadout.
- `src/render` — painterly procedural renderer: cached terrain, per-type building
  and unit art, fog overlay, particles, screen shake.
- `src/ui` — immediate-mode canvas UI: in-match HUD plus menu/setup/armory/
  post-match screens, and the Warband Tactics screen.
- Warband Tactics lives in `src/sim/warband.ts` (run engine), `autobattle.ts`
  (headless + watchable battle resolution), `traits.ts`, `items.ts`,
  `augments.ts`, `creeps.ts`, drawn by `src/ui/warband_screen.ts`.
