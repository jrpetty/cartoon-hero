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
  post-match screens.
