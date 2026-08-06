import { describe, expect, it } from "vitest";
import { World } from "../sim/world";
import { Team } from "../sim/types";
import { generateMap } from "../maps/generator";
import { TILE } from "../content/balance";
import { Terrain } from "../maps/terrain_kinds";
import { SkirmishAI } from "./skirmish_ai";
import { DIFFICULTIES } from "./difficulty";
import {
  bestGroundNear, bestTowerSite, flankPoint, groundScore, isHighGround, stagingPoint,
} from "./terrain_sense";

/** A world on flat open ground, with helpers to paint terrain into it. */
function field(seed = 5) {
  const map = generateMap("open_plains", seed, 2);
  const w = new World(seed);
  w.init(map, [{}, {}], [1, 1], [0, 1]);
  // A clean slate: open_plains still has ponds and shore, and these tests are
  // about what the advisor does with ground we control.
  w.terrain = new Uint8Array(w.terrain.length);
  const paint = (wx: number, wy: number, t: Terrain, r = 3) => {
    for (let dy = -r; dy <= r; dy++) {
      for (let dx = -r; dx <= r; dx++) {
        const px = Math.floor(wx / TILE) + dx, py = Math.floor(wy / TILE) + dy;
        if (px < 0 || py < 0 || px >= w.terrainCols || py >= w.terrainRows) continue;
        w.terrain[py * w.terrainCols + px] = t;
      }
    }
  };
  return { w, paint, cx: w.worldW / 2, cy: w.worldH / 2 };
}

describe("Reading the ground", () => {
  it("ranks a hill above open ground, and a wood or a marsh below it", () => {
    const { w, paint, cx, cy } = field();
    paint(cx - 300, cy, Terrain.Hill);
    paint(cx + 300, cy, Terrain.Forest);
    paint(cx, cy + 300, Terrain.Marsh);
    const hill = groundScore(w, cx - 300, cy);
    const open = groundScore(w, cx, cy);
    const wood = groundScore(w, cx + 300, cy);
    const marsh = groundScore(w, cx, cy + 300);
    expect(hill).toBeGreaterThan(open);
    expect(wood).toBeLessThan(open);
    expect(marsh).toBeLessThan(open);
    // A wall is not ground at all.
    paint(cx, cy - 300, Terrain.Rock);
    expect(groundScore(w, cx, cy - 300)).toBe(-Infinity);
  });

  it("scores a wood and a marsh far enough below open ground to actually avoid them", () => {
    // The margin is the mechanism: there is no separate "is this fightable"
    // predicate, so woods and marsh are avoided only because the penalty is big
    // enough to outweigh the walk to somewhere else. If these drifted toward
    // zero the advisors would start meeting attacks in a bog and nothing else
    // in this file would notice.
    const { w, paint, cx, cy } = field();
    paint(cx + 200, cy, Terrain.Forest);
    paint(cx, cy + 200, Terrain.Marsh);
    const open = groundScore(w, cx, cy);
    expect(open - groundScore(w, cx + 200, cy), "wood is not punished enough").toBeGreaterThan(30);
    expect(open - groundScore(w, cx, cy + 200), "marsh is not punished enough").toBeGreaterThan(30);
  });

  it("walks to a nearby hill rather than standing where it was told", () => {
    const { w, paint, cx, cy } = field();
    paint(cx + 120, cy, Terrain.Hill, 4);
    const spot = bestGroundNear(w, cx, cy, { radius: 260 });
    expect(isHighGround(w, spot.x, spot.y), "did not find the hill").toBe(true);
  });

  it("will cross the whole search radius for high ground when told it is worth it", () => {
    // The scale guard. The distance penalty and the ground score have to be in
    // the same units, or the search radius is a lie: with the old raw divisor a
    // hill more than three tiles away could never win no matter how wide the
    // search, so the AI looked at high ground across the field and stood still.
    const { w, paint, cx, cy } = field();
    paint(cx + 250, cy, Terrain.Hill, 4);
    const far = bestGroundNear(w, cx, cy, { radius: 320, detour: 8 });
    expect(isHighGround(w, far.x, far.y), "would not walk for a hill").toBe(true);
    // …and the same knob, turned the other way, keeps it home.
    const near = bestGroundNear(w, cx, cy, { radius: 320, detour: 40 });
    expect(isHighGround(w, near.x, near.y), "wandered off for a distant hill").toBe(false);
  });

  it("stays put when the ground nearby is no better", () => {
    const { w, cx, cy } = field();
    const spot = bestGroundNear(w, cx, cy, { radius: 260 });
    expect(spot.x).toBe(cx);
    expect(spot.y).toBe(cy);
  });

  it("will not send an army to a hill it cannot reach", () => {
    const { w, paint, cx, cy } = field();
    // A hill on the far side of a mountain range that crosses the whole map —
    // a short wall would simply be walked around, which is the point of using
    // the component labels rather than a distance check.
    paint(cx + 200, cy, Terrain.Hill, 3);
    const wallX = Math.floor((cx + 110) / TILE);
    for (let py = 0; py < w.terrainRows; py++) {
      for (let dx = 0; dx <= 2; dx++) {
        w.terrain[py * w.terrainCols + wallX + dx] = Terrain.Rock;
        w.grid.setBlocked(wallX + dx, py, true);
      }
    }
    const spot = bestGroundNear(w, cx + 200, cy, { radius: 260, from: { x: cx - 100, y: cy } });
    expect(isHighGround(w, spot.x, spot.y), "staged on an unreachable hill").toBe(false);
  });

  it("prefers a rise for a tower, since a tower is all range", () => {
    const { w, paint, cx, cy } = field();
    paint(cx + 60, cy, Terrain.Hill, 2);
    const site = bestTowerSite(w, cx, cy, 4 * TILE);
    expect(isHighGround(w, site.x, site.y)).toBe(true);
  });

  it("never suggests a tower site on ground nothing can be built on", () => {
    // A wood is passable, so the blocks check waves it through — but no building
    // goes up in one. A site like that costs the caller its single retry and
    // lands the tower back on the arithmetic answer this replaced.
    const { w, paint, cx, cy } = field();
    paint(cx, cy, Terrain.Forest, 2);
    const site = bestTowerSite(w, cx, cy, 4 * TILE);
    expect(w.terrainAt(site.x, site.y)).not.toBe(Terrain.Forest);
  });

  it("never suggests a tower site inside a mountain", () => {
    const { w, paint, cx, cy } = field();
    paint(cx, cy, Terrain.Rock, 2);
    const site = bestTowerSite(w, cx, cy, 4 * TILE);
    expect(w.terrainAt(site.x, site.y)).not.toBe(Terrain.Rock);
  });
});

describe("Staging and flanking", () => {
  it("gathers short of the target rather than walking into its lap", () => {
    const { w, cx, cy } = field();
    const target = { x: cx + 800, y: cy };
    const army = { x: cx - 800, y: cy };
    const stage = stagingPoint(w, target, army);
    const toTarget = Math.hypot(stage.x - target.x, stage.y - target.y);
    expect(toTarget, "staged on top of the target").toBeGreaterThan(200);
    // …and on the army's side of it, not past it.
    expect(stage.x).toBeLessThan(target.x);
  });

  it("takes the high ground on the approach when there is some", () => {
    const { w, paint, cx, cy } = field();
    const target = { x: cx + 800, y: cy };
    const army = { x: cx - 800, y: cy };
    // A rise sitting right where a sensible force would gather.
    paint(cx + 380, cy, Terrain.Hill, 4);
    const stage = stagingPoint(w, target, army);
    expect(isHighGround(w, stage.x, stage.y), "ignored the hill on the approach").toBe(true);
  });

  it("does not walk backwards to stage when it is already on top of them", () => {
    const { w, cx, cy } = field();
    const target = { x: cx + 100, y: cy };
    const army = { x: cx, y: cy };
    const stage = stagingPoint(w, target, army);
    expect(Math.hypot(stage.x - army.x, stage.y - army.y)).toBeLessThan(260);
  });

  it("flanks to the side of the approach, on ground that exists", () => {
    const { w, cx, cy } = field();
    const target = { x: cx, y: cy };
    const army = { x: cx - 800, y: cy };
    for (const side of [1, -1] as const) {
      const fp = flankPoint(w, target, army, side);
      // Perpendicular to the approach means it moves in y, not straight in x.
      expect(Math.abs(fp.y - cy), `side ${side}`).toBeGreaterThan(100);
      expect(fp.x >= 0 && fp.x < w.worldW).toBe(true);
      expect(fp.y >= 0 && fp.y < w.worldH).toBe(true);
    }
  });

  it("never picks a flank inside a lake", () => {
    const { w, paint, cx, cy } = field();
    const target = { x: cx, y: cy };
    const army = { x: cx - 800, y: cy };
    // Drown both flanks, wide enough that the whole search radius is water —
    // which is the case that used to hand back a point inside the lake.
    paint(cx, cy - 420, Terrain.Water, 14);
    paint(cx, cy + 420, Terrain.Water, 14);
    for (const side of [1, -1] as const) {
      const fp = flankPoint(w, target, army, side);
      expect(w.terrainAt(fp.x, fp.y), `side ${side}`).not.toBe(Terrain.Water);
    }
  });
});

describe("The AI reads the ground", () => {
  it("can be switched off, which is how the feature is measured at all", () => {
    const map = generateMap("highlands", 3, 2);
    const w = new World(3);
    w.init(map, [{}, {}], [1, 1], [0, 1]);
    const ai = new SkirmishAI(w, Team.Player, DIFFICULTIES.knight);
    expect(ai.readsGround).toBe(true);
    ai.readsGround = false;
    expect(ai.readsGround).toBe(false);
  });

  it("actually orders armies onto high ground in a real match", () => {
    // The unit tests above prove the advisors work in isolation; this proves the
    // AI reaches them. Without it the whole feature could be wired to a branch
    // that never runs and every other test here would still pass.
    // Totalled over several seeds rather than pinned to one. On any given
    // highlands seed the fighting may simply never happen near a hill — seed 1
    // produces the identical move count either way — and a test hostage to that
    // breaks whenever an unrelated economy change shifts where the armies meet.
    const onHill = (readsGround: boolean) => {
      let hills = 0;
      for (const seed of [2, 3, 4, 6]) {
        const map = generateMap("highlands", seed, 2);
        const world = new World(seed);
        world.init(map, [{}, {}], [1, 1], [0, 1]);
        const ais = [
          new SkirmishAI(world, Team.Player, DIFFICULTIES.knight),
          new SkirmishAI(world, Team.Enemy, DIFFICULTIES.knight),
        ];
        for (const ai of ais) ai.readsGround = readsGround;
        const realMove = world.issueMove.bind(world);
        world.issueMove = (ids, x, y, queue, attack) => {
          if (isHighGround(world, x, y)) hills++;
          return realMove(ids, x, y, queue, attack);
        };
        for (let i = 0; i < 20 * 60 * 12; i++) {
          world.tick();
          for (const ai of ais) ai.update(1 / 20);
          world.drainEvents();
          if (world.winner !== null) break;
        }
      }
      return hills;
    };
    const aware = onHill(true);
    const blind = onHill(false);
    expect(aware, "never sent anyone to a hill").toBeGreaterThan(0);
    expect(aware, "reading the ground changed nothing about where armies went")
      .toBeGreaterThan(blind);
  }, 300000);

  it("plays a full match on terrain-heavy ground without stalling", () => {
    // The advisors run inside the think step and can return a point the army
    // then has to path to. If any of them ever returned somewhere unreachable or
    // off the map, an army would sit still and the economy would be the only
    // thing that moved — so this asserts the armies actually fought.
    //
    // Across several seeds rather than one, and asserting on the total: this AI
    // is genuinely streaky and there are highlands seeds where it never reaches
    // its army threshold inside twelve minutes and fights nobody. Pinning the
    // test to a seed that happens to fight makes it a hostage to any change that
    // shifts the generator's RNG, which is how it broke the last time.
    let kills = 0;
    for (const seed of [1, 2, 3]) {
      const map = generateMap("highlands", seed, 2);
      const world = new World(seed);
      world.init(map, [{}, {}], [1, 1], [0, 1]);
      const ais = [
        new SkirmishAI(world, Team.Player, DIFFICULTIES.knight),
        new SkirmishAI(world, Team.Enemy, DIFFICULTIES.knight),
      ];
      for (let i = 0; i < 20 * 60 * 12; i++) {
        world.tick();
        for (const ai of ais) ai.update(1 / 20);
        world.drainEvents();
        if (world.winner !== null) break;
      }
      kills += world.player(Team.Player).stats.unitsKilled + world.player(Team.Enemy).stats.unitsKilled;
      for (const t of [Team.Player, Team.Enemy]) {
        expect(world.player(t).stats.gathered, `seed ${seed} economy stalled`).toBeGreaterThan(500);
      }
    }
    expect(kills, "the armies never met on any seed").toBeGreaterThan(0);
  }, 300000);
});
