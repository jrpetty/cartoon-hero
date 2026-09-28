import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Kind, Team } from "./types";
import { generateMap } from "../maps/generator";
import { TILE } from "../content/balance";
import { Terrain } from "../maps/terrain_kinds";

/**
 * A world with a band of shallows down the middle — a ford an army can wade,
 * slowly, which is exactly the situation a bridge is for.
 */
function forded(seed = 11, halfWidth = 2) {
  const map = generateMap("open_plains", seed, 2);
  const w = new World(seed);
  w.init(map, [{}, {}], [1, 1], [0, 1]);
  w.terrain = new Uint8Array(w.terrain.length);
  const midC = Math.floor(w.terrainCols / 2);
  for (let cy = 0; cy < w.terrainRows; cy++) {
    for (let dx = -halfWidth; dx <= halfWidth; dx++) {
      w.terrain[cy * w.terrainCols + midC + dx] = Terrain.Shallow;
      // The nav grid has to agree with the terrain we just painted. Shallows are
      // passable ground in a real map — whatever the generator had blocked here
      // (a pond, a tree) is not part of the situation being tested.
      w.grid.setBlocked(midC + dx, cy, false);
    }
  }
  return { w, fordX: midC * TILE + TILE / 2, midY: Math.floor(w.terrainRows / 2) * TILE };
}

/**
 * Reaching past the public surface on purpose. Finishing a building and razing
 * one are both things only the sim does to itself — there is no "complete this"
 * or "demolish this" order a player can give — so a test that wants a standing
 * bridge, or a burned one, has to call them directly.
 */
interface Internals {
  onBuildingCompleted(e: unknown, def: unknown, silent?: boolean): void;
  stampBridge(b: unknown, on: boolean): void;
  kill(e: unknown, byTeam: Team): void;
}
const internals = (w: World) => w as unknown as Internals;
const finish = (w: World, b: { buildState: number }) => {
  b.buildState = 0; // BuildState.Done
  internals(w).onBuildingCompleted(b, { onlyOnShallows: true, popProvided: 0, id: "bridge" }, true);
};

describe("Bridges", () => {
  it("can be laid on shallows", () => {
    const { w, fordX, midY } = forded();
    expect(w.canPlace(Team.Player, "bridge", fordX, midY)).toBe(true);
  });

  it("cannot be laid on dry land, so it is never a cheap wall", () => {
    // The whole cost/timing decision collapses if a bridge doubles as free
    // fortification anywhere you like.
    const { w, midY } = forded();
    expect(w.canPlace(Team.Player, "bridge", 400, midY)).toBe(false);
  });

  it("cannot be laid half on the bank", () => {
    // Every cell of the footprint has to be water. A bridge with one foot on
    // the grass would let you creep a wall out from the shore.
    const { w, fordX, midY } = forded();
    const edge = fordX + 3 * TILE; // footprint straddles shallows and grass
    expect(w.canPlace(Team.Player, "bridge", edge, midY)).toBe(false);
  });

  it("does not block movement — troops cross it, not around it", () => {
    const { w, fordX, midY } = forded();
    const b = w.placeBuilding(Team.Player, "bridge", fordX, midY)!;
    expect(b).not.toBeNull();
    expect(w.grid.isBlockedWorld(b.x, b.y)).toBe(false);
  });

  it("only carries traffic once it is finished", () => {
    // A half-built crossing is a construction site standing in a river.
    const { w, fordX, midY } = forded();
    const b = w.placeBuilding(Team.Player, "bridge", fordX, midY)!;
    expect(w.bridgedAt(b.x, b.y), "an unfinished bridge already carried traffic").toBe(false);
    finish(w, b);
    expect(w.bridgedAt(b.x, b.y)).toBe(true);
  });

  it("makes the crossing as quick as dry land, which is the point of paying for one", () => {
    // Same unit, same ford, with and without decking. Shallows are half speed,
    // so if the bridge did nothing the two would tie. The ford here is exactly
    // as wide as one bridge, so the crossing is either fully decked or not at
    // all — a partly-planked ford would muddy the comparison.
    const cross = (withBridge: boolean) => {
      const { w, fordX, midY } = forded(11, 2);
      w.player(Team.Player).resources.wood = 5000;
      if (withBridge) {
        for (let dy = -8; dy <= 8; dy++) {
          const b = w.placeBuilding(Team.Player, "bridge", fordX, midY + dy * 2 * TILE);
          if (b) finish(w, b);
        }
      }
      const u = w.spawnUnit(Team.Player, "knight", fordX - 6 * TILE, midY);
      const goal = fordX + 6 * TILE;
      w.issueMove([u.id], goal, midY);
      for (let i = 0; i < 20 * 40; i++) {
        w.tick();
        w.drainEvents();
        if (u.x >= goal - 20) return i;
      }
      return Infinity;
    };
    const bridged = cross(true);
    const waded = cross(false);
    expect(bridged, "the army never got across the bridge").toBeLessThan(Infinity);
    expect(bridged, "the bridge saved no time at all").toBeLessThan(waded);
  });

  it("goes back to being shallows when it burns down", () => {
    const { w, fordX, midY } = forded();
    const b = w.placeBuilding(Team.Player, "bridge", fordX, midY)!;
    finish(w, b);
    expect(w.bridgedAt(b.x, b.y)).toBe(true);
    internals(w).kill(b, Team.Enemy);
    expect(b.alive).toBe(false);
    expect(w.bridgedAt(b.x, b.y), "the decking outlived the bridge").toBe(false);
  });

  it("keeps the crossing intact where two bridges overlap and one is lost", () => {
    // The reason the decking is counted rather than a flag: two footprints can
    // cover the same cell, and clearing it with the first casualty would punch a
    // hole in a crossing that is still standing.
    const { w, fordX, midY } = forded();
    w.player(Team.Player).resources.wood = 5000;
    const a = w.placeBuilding(Team.Player, "bridge", fordX, midY)!;
    finish(w, a);
    // A second bridge whose decking covers the same cell. Stamped directly,
    // because placeBuilding quite rightly refuses to put two buildings on one
    // spot — the overlap this guards against comes from adjacent spans sharing
    // an edge cell, which is fiddly to arrange and identical in effect.
    internals(w).stampBridge({ x: a.x, y: a.y, type: "bridge" }, true);
    internals(w).kill(a, Team.Enemy);
    expect(w.bridgedAt(a.x, a.y), "one loss opened a hole in a standing crossing").toBe(true);
  });

  it("is walkable in the same way a farm is", () => {
    const { w, fordX, midY } = forded();
    const b = w.placeBuilding(Team.Player, "bridge", fordX, midY)!;
    const u = w.spawnUnit(Team.Player, "knight", b.x - 140, b.y);
    w.issueMove([u.id], b.x + 140, b.y);
    for (let i = 0; i < 20 * 20; i++) { w.tick(); w.drainEvents(); }
    expect(u.x, "a unit could not walk over a bridge").toBeGreaterThan(b.x);
  });

  it("costs wood, so a crossing is a decision rather than a formality", () => {
    const { w, fordX, midY } = forded();
    const p = w.player(Team.Player);
    const before = p.resources.wood;
    w.placeBuilding(Team.Player, "bridge", fordX, midY);
    expect(p.resources.wood).toBeLessThan(before);
  });
});

describe("Lakes stay hard walls", () => {
  it("takes no bridge — deep water is not fordable and never becomes so", () => {
    const map = generateMap("open_plains", 3, 2);
    const w = new World(3);
    w.init(map, [{}, {}], [1, 1], [0, 1]);
    w.terrain = new Uint8Array(w.terrain.length);
    const midC = Math.floor(w.terrainCols / 2);
    for (let cy = 0; cy < w.terrainRows; cy++) {
      for (let dx = -2; dx <= 2; dx++) w.terrain[cy * w.terrainCols + midC + dx] = Terrain.Water;
    }
    const wx = midC * TILE + TILE / 2;
    expect(w.canPlace(Team.Player, "bridge", wx, w.worldH / 2)).toBe(false);
  });
});

describe("What else the shallows rule protects", () => {
  it("still refuses ordinary buildings on the shallows", () => {
    const { w, fordX, midY } = forded();
    expect(w.canPlace(Team.Player, "house", fordX, midY)).toBe(false);
  });

  it("still allows ordinary buildings on grass", () => {
    const { w, midY } = forded();
    expect(w.canPlace(Team.Player, "house", 400, midY)).toBe(true);
  });
});

describe("Bridges in the wider world", () => {
  it("counts as a building the enemy can burn", () => {
    const { w, fordX, midY } = forded();
    const b = w.placeBuilding(Team.Player, "bridge", fordX, midY)!;
    expect(b.kind).toBe(Kind.Building);
    expect(b.maxHp).toBeGreaterThan(0);
  });
});
