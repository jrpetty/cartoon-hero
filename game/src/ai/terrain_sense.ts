// What the AI understands about the ground it is fighting on.
//
// The map gained mountains, lakes, hills and woods, and for a while only the
// player could read any of it: a hill was worth 20% more range and sight to
// whoever stood on it, and the AI would happily meet an attack in a marsh while
// an empty ridge sat forty metres away. Geography one side understands is worse
// than no geography — it reads as the game cheating in the player's favour.
//
// This is deliberately a small, cheap advisor rather than a planner. It answers
// one question — "of the ground near here, which patch would I rather be
// standing on?" — and the AI asks it at the few moments where the answer
// changes what it does: staging an attack, meeting a raid, and siting a tower.

import { World } from "../sim/world";
import { TILE } from "../content/balance";
import {
  TERRAIN_SPEED, TERRAIN_SIGHT, TERRAIN_BLOCKS, TERRAIN_BUILDABLE, Terrain,
} from "../maps/terrain_kinds";

/**
 * How much an army wants to be standing on this ground, in arbitrary points
 * around zero for open grass.
 *
 * Sight is weighted about twice as hard as speed because it is the thing that
 * decides a fight rather than a chase: a hill both sees the enemy first and
 * out-ranges them, while slow ground mostly costs you the option to leave.
 */
export function groundScore(world: World, wx: number, wy: number): number {
  const t = world.terrainAt(wx, wy);
  if (TERRAIN_BLOCKS[t]) return -Infinity;
  const sight = TERRAIN_SIGHT[t] ?? 1;
  const speed = TERRAIN_SPEED[t] ?? 1;
  return (sight - 1) * 100 - (1 - speed) * 60;
}

// There is deliberately no `isFightableGround` predicate here. "Don't get caught
// in a wood" is already carried by the score itself — forest comes out at -52
// and marsh at -34 against open grass at 0 — so every advisor below avoids them
// without a separate yes/no test, and a threshold nothing consults is just
// another number to keep in sync.

export interface GroundOpts {
  /** How far from (x,y) to look, in world units. */
  radius?: number;
  /**
   * Somewhere the chosen ground must be reachable from — usually the army. A
   * perfect hill on the far side of a lake is not a staging point.
   */
  from?: { x: number; y: number };
  /**
   * What crossing the whole search radius is worth giving up, in the same points
   * `groundScore` returns.
   *
   * This used to be a raw divisor on the distance, which quietly made the whole
   * feature inert: a hill is worth 11 points, so a divisor of 10 meant the AI
   * would divert at most 110 world units — three tiles — while searching a
   * radius of 260. Five sixths of every search was ground that could not win no
   * matter how good it was. Tying the penalty to the radius keeps the two scales
   * commensurate, so `detour` below a hill's 11 points means "cross the whole
   * search area for high ground" and above it means "only take what is close".
   */
  detour?: number;
}

/**
 * The best patch of ground within `radius` of (x,y).
 *
 * Returns the original point when nothing nearby beats it, so a caller can
 * always use the result without checking. Sampling is on a coarse lattice —
 * a couple of cells apart — because the answer only needs to be the right
 * *hill*, not the right cell, and this runs inside the AI's think step.
 */
export function bestGroundNear(
  world: World, x: number, y: number, opts: GroundOpts = {},
): { x: number; y: number } {
  const radius = opts.radius ?? 240;
  const detour = opts.detour ?? 10;
  const step = TILE * 2;
  const grid = world.grid;
  const fromCell = opts.from
    ? [Math.floor(opts.from.x / TILE), Math.floor(opts.from.y / TILE)] as const
    : null;
  const reachable = (nx: number, ny: number): boolean => {
    if (!fromCell || !grid) return true;
    return !grid.provablyUnreachable(
      fromCell[0], fromCell[1], Math.floor(nx / TILE), Math.floor(ny / TILE),
    );
  };

  let bestX = x, bestY = y;
  let best = -Infinity;
  // The point we were asked about is a candidate like any other, and gets the
  // same two questions. It used to be seeded as the incumbent unchecked, which
  // meant asking "is this hill a good place to stand?" while standing on the
  // hill always answered yes — even with a mountain range between it and the army.
  {
    const own = groundScore(world, x, y);
    if (Number.isFinite(own) && reachable(x, y)) best = own; // distance 0
  }
  for (let dy = -radius; dy <= radius; dy += step) {
    for (let dx = -radius; dx <= radius; dx += step) {
      const d2 = dx * dx + dy * dy;
      if (d2 > radius * radius) continue;
      const nx = x + dx, ny = y + dy;
      if (nx < 0 || ny < 0 || nx >= world.worldW || ny >= world.worldH) continue;
      const s = groundScore(world, nx, ny) - (Math.sqrt(d2) / radius) * detour;
      if (s <= best) continue;
      // Only pay for the reachability check on ground that already won.
      if (!reachable(nx, ny)) continue;
      best = s;
      bestX = nx;
      bestY = ny;
    }
  }
  // Nothing in range survived both questions — the whole neighbourhood was lake
  // and mountain, or it was all walled off from the army. Widen the search until
  // we find real ground rather than handing back a point inside a wall, which
  // would become a move order the pathfinder has to quietly rescue.
  if (!Number.isFinite(best)) return nearestStandableGround(world, x, y, reachable);
  return { x: bestX, y: bestY };
}

/**
 * An expanding ring search for any cell an army could actually stand on.
 *
 * This reads the terrain array rather than only the nav grid on purpose. The two
 * normally agree, but the advisor's whole job is answering questions about
 * terrain, and a fallback that trusts a different source than the thing it is
 * falling back from can hand a lake back as dry land.
 */
function nearestStandableGround(
  world: World, x: number, y: number, reachable: (x: number, y: number) => boolean,
): { x: number; y: number } {
  const cx = Math.floor(x / TILE), cy = Math.floor(y / TILE);
  // Two passes, both bounded. The first wants ground the army can walk to; the
  // second settles for ground that exists. Without the second pass, an army
  // sealed into its own pocket of the map would sweep every cell on every think
  // step and still come back empty — and the bound keeps that sweep off the
  // frame budget either way.
  const MAX_RINGS = 32;
  for (const needReach of [true, false]) {
    for (let r = 1; r <= MAX_RINGS; r++) {
      for (let dy = -r; dy <= r; dy++) {
        for (let dx = -r; dx <= r; dx++) {
          if (Math.abs(dx) !== r && Math.abs(dy) !== r) continue; // ring only
          const px = cx + dx, py = cy + dy;
          if (px < 0 || py < 0 || px >= world.terrainCols || py >= world.terrainRows) continue;
          const wx = px * TILE + TILE / 2, wy = py * TILE + TILE / 2;
          if (!Number.isFinite(groundScore(world, wx, wy))) continue;
          if (world.grid?.isBlocked(px, py)) continue;
          if (needReach && !reachable(wx, wy)) continue;
          return { x: wx, y: wy };
        }
      }
    }
  }
  return { x, y };
}

/**
 * A place to gather before hitting a target: good ground, on our side of the
 * approach rather than in the enemy's lap.
 *
 * The staging point sits back along the line from the target toward the army,
 * so a wave collects itself and takes the high ground *outside* the base
 * instead of trickling in one unit at a time — which is what an army walking
 * straight at a Town Centre actually does.
 */
export function stagingPoint(
  world: World,
  target: { x: number; y: number },
  army: { x: number; y: number },
  standoff = 420,
): { x: number; y: number } {
  const dx = army.x - target.x;
  const dy = army.y - target.y;
  const d = Math.hypot(dx, dy) || 1;
  // Already close enough that staging would mean walking backwards.
  if (d < standoff * 1.2) {
    return bestGroundNear(world, army.x, army.y, { radius: 220, from: army, detour: 8 });
  }
  const sx = target.x + (dx / d) * standoff;
  const sy = target.y + (dy / d) * standoff;
  // detour 8 against a hill's 11: an army forming up will cross the whole search
  // area to gather on high ground, which is the one moment it has time to.
  return bestGroundNear(world, sx, sy, { radius: 320, from: army, detour: 8 });
}

/**
 * Somewhere a flanking force can actually get to. The old flank was a blind
 * ±420 offset, which on a map with lakes and ridges regularly sent half the
 * army into a mountain to stand around looking at it.
 */
export function flankPoint(
  world: World,
  target: { x: number; y: number },
  army: { x: number; y: number },
  side: 1 | -1,
  reach = 420,
): { x: number; y: number } {
  const dx = target.x - army.x;
  const dy = target.y - army.y;
  const d = Math.hypot(dx, dy) || 1;
  // Perpendicular to the approach, so a flank comes in from the side rather
  // than from a random compass direction.
  const px = (-dy / d) * reach * side;
  const py = (dx / d) * reach * side;
  // A flank is a position, not a picnic spot: detour 16 keeps it roughly where
  // it belongs and only shifts it for good ground that is on the way anyway.
  return bestGroundNear(world, target.x + px, target.y + py, { radius: 300, from: army, detour: 16 });
}

/**
 * Where to put something that shoots. A tower on a hill is worth 20% more range
 * for nothing, which over a whole game is the cheapest defensive upgrade there
 * is — and the AI was picking its tower site by arithmetic on a straight line.
 */
export function bestTowerSite(
  world: World, x: number, y: number, radius = 3 * TILE,
): { x: number; y: number } {
  const step = TILE;
  let bestX = x, bestY = y, best = -Infinity;
  for (let dy = -radius; dy <= radius; dy += step) {
    for (let dx = -radius; dx <= radius; dx += step) {
      const nx = x + dx, ny = y + dy;
      if (nx < 0 || ny < 0 || nx >= world.worldW || ny >= world.worldH) continue;
      const t = world.terrainAt(nx, ny);
      // A tower has to be buildable where it stands; a rise is, a wood is not.
      // Checking this here rather than leaving it to placeBuilding matters
      // because the caller only gets one retry — hand it a site it cannot build
      // on and it silently falls back to the arithmetic answer we replaced.
      if (TERRAIN_BLOCKS[t] || !TERRAIN_BUILDABLE[t]) continue;
      const sight = TERRAIN_SIGHT[t] ?? 1;
      const s = (sight - 1) * 100 - Math.hypot(dx, dy) / 20;
      if (s > best) { best = s; bestX = nx; bestY = ny; }
    }
  }
  return { x: bestX, y: bestY };
}

/**
 * Where the line from A to B wades through shallows, if it does.
 *
 * Bridges arrived and only the player could see the point of them: the AI would
 * march an army through a ford at half speed, every wave, past a crossing it
 * could have paid a hundred wood for once. That is the same mistake as not
 * knowing hills exist, and worse — a bridge is a *thing the AI can build*, so
 * the gap shows up as a base that never uses a whole building.
 *
 * Walks the straight line rather than the actual path on purpose. The pathfinder
 * already routes around walls; shallows are not walls, so the route it picks
 * really does go through them, and the straight line is where. Returns the
 * middle of the widest run crossed — the middle because a bridge wants both
 * banks, the widest because that is the crossing costing the most time.
 */
export function fordCrossing(
  world: World,
  from: { x: number; y: number },
  to: { x: number; y: number },
): { x: number; y: number; width: number } | null {
  const steps = Math.ceil(Math.hypot(to.x - from.x, to.y - from.y) / (TILE / 2));
  if (steps <= 0) return null;
  let best: { x: number; y: number; width: number } | null = null;
  let runStart = -1;
  const closeRun = (endStep: number) => {
    if (runStart < 0) return;
    const width = endStep - runStart;
    if (!best || width > best.width) {
      const mid = (runStart + endStep) / 2 / steps;
      best = {
        x: from.x + (to.x - from.x) * mid,
        y: from.y + (to.y - from.y) * mid,
        width,
      };
    }
    runStart = -1;
  };
  for (let i = 0; i <= steps; i++) {
    const t = i / steps;
    const x = from.x + (to.x - from.x) * t;
    const y = from.y + (to.y - from.y) * t;
    const wading = world.terrainAt(x, y) === Terrain.Shallow && !world.bridgedAt(x, y);
    if (wading) { if (runStart < 0) runStart = i; }
    else closeRun(i);
  }
  closeRun(steps);
  return best;
}

/** True when this cell is high ground — used by tests and callouts. */
export const isHighGround = (world: World, wx: number, wy: number): boolean =>
  world.terrainAt(wx, wy) === Terrain.Hill;
