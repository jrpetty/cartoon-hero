import { describe, expect, it } from "vitest";
import { World } from "../sim/world";
import { Kind, Team } from "../sim/types";
import { generateMap } from "../maps/generator";
import { TILE } from "../content/balance";
import { Terrain } from "../maps/terrain_kinds";
import { SkirmishAI } from "./skirmish_ai";
import { DIFFICULTIES } from "./difficulty";
import {
  bestGroundNear, bestTowerSite, flankPoint, fordCrossing, groundScore, isHighGround, stagingPoint,
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

describe("Finding a ford on the way", () => {
  /** A world with a band of shallows across the middle. */
  const forded = (halfWidth = 3) => {
    const { w } = field(31);
    const midC = Math.floor(w.terrainCols / 2);
    for (let cy = 0; cy < w.terrainRows; cy++) {
      for (let dx = -halfWidth; dx <= halfWidth; dx++) {
        w.terrain[cy * w.terrainCols + midC + dx] = Terrain.Shallow;
        w.grid.setBlocked(midC + dx, cy, false);
      }
    }
    return { w, fordX: midC * TILE + TILE / 2, midY: Math.floor(w.terrainRows / 2) * TILE };
  };

  it("finds the crossing on the line between two points", () => {
    const { w, fordX, midY } = forded();
    const cross = fordCrossing(w, { x: fordX - 600, y: midY }, { x: fordX + 600, y: midY });
    expect(cross, "did not notice the ford at all").not.toBeNull();
    expect(Math.abs(cross!.x - fordX), "the crossing is not where the water is")
      .toBeLessThan(TILE * 3);
  });

  it("finds nothing when the route stays on dry land", () => {
    const { w, fordX, midY } = forded();
    // Both ends on the same bank, never reaching the water.
    expect(fordCrossing(w, { x: fordX - 900, y: midY }, { x: fordX - 400, y: midY })).toBeNull();
  });

  it("reports the width, so a puddle can be told from a real ford", () => {
    const narrow = forded(1);
    const wide = forded(6);
    const a = fordCrossing(narrow.w, { x: narrow.fordX - 600, y: narrow.midY }, { x: narrow.fordX + 600, y: narrow.midY });
    const b = fordCrossing(wide.w, { x: wide.fordX - 600, y: wide.midY }, { x: wide.fordX + 600, y: wide.midY });
    expect(b!.width).toBeGreaterThan(a!.width);
  });

  it("stops seeing a ford once it has been bridged", () => {
    // Otherwise the AI would pay for a second crossing beside the first, and a
    // third beside that, for as long as it could afford them. The band here is
    // exactly two cells — one bridge footprint — so a decked crossing really is
    // fully decked rather than leaving a lip of water the line still clips.
    const { w } = field(31);
    const midC = Math.floor(w.terrainCols / 2);
    for (let cy = 0; cy < w.terrainRows; cy++) {
      for (const cx of [midC, midC + 1]) {
        w.terrain[cy * w.terrainCols + cx] = Terrain.Shallow;
        w.grid.setBlocked(cx, cy, false);
      }
    }
    const fordX = midC * TILE + TILE / 2;
    const midY = Math.floor(w.terrainRows / 2) * TILE;
    const from = { x: fordX - 600, y: midY }, to = { x: fordX + 600, y: midY };
    expect(fordCrossing(w, from, to)).not.toBeNull();

    w.player(Team.Player).resources.wood = 5000; // enough to deck the whole span
    for (let dy = -8; dy <= 8; dy++) {
      const b = w.placeBuilding(Team.Player, "bridge", fordX, midY + dy * 2 * TILE);
      if (b) {
        b.buildState = 0;
        (w as unknown as { onBuildingCompleted(e: unknown, d: unknown, s: boolean): void })
          .onBuildingCompleted(b, { onlyOnShallows: true, popProvided: 0, id: "bridge" }, true);
      }
    }
    expect(fordCrossing(w, from, to), "kept seeing a ford that is now decked").toBeNull();
  });

  it("picks the widest crossing when the route wades more than once", () => {
    const { w } = field(31);
    const band = (cx: number, half: number) => {
      for (let cy = 0; cy < w.terrainRows; cy++) {
        for (let dx = -half; dx <= half; dx++) w.terrain[cy * w.terrainCols + cx + dx] = Terrain.Shallow;
      }
    };
    band(30, 1);
    band(60, 5);
    const midY = Math.floor(w.terrainRows / 2) * TILE;
    const cross = fordCrossing(w, { x: 10 * TILE, y: midY }, { x: 90 * TILE, y: midY });
    // The wide one, not the first one met — that is the crossing costing time.
    expect(Math.abs(cross!.x - 60 * TILE)).toBeLessThan(TILE * 4);
  });
});

describe("The AI and the water", () => {
  /** Two bases either side of a ford, so every attack has to cross it. */
  const acrossAFord = (seed: number) => {
    const map = generateMap("open_plains", seed, 2);
    const world = new World(seed);
    world.init(map, [{}, {}], [1, 1], [0, 1]);
    const midC = Math.floor(world.terrainCols / 2);
    for (let cy = 0; cy < world.terrainRows; cy++) {
      for (let dx = -3; dx <= 3; dx++) {
        world.terrain[cy * world.terrainCols + midC + dx] = Terrain.Shallow;
        world.grid.setBlocked(midC + dx, cy, false);
      }
    }
    return world;
  };

  it("lays a crossing over the ford its army keeps wading through", () => {
    // The regression this whole pair of features exists for. Bridges shipped as
    // something only the player understood; an AI that never builds one is a
    // building the game owns and never uses.
    const world = acrossAFord(4);
    const ais = [
      new SkirmishAI(world, Team.Player, DIFFICULTIES.knight),
      new SkirmishAI(world, Team.Enemy, DIFFICULTIES.knight),
    ];
    for (let i = 0; i < 20 * 60 * 14; i++) {
      world.tick();
      for (const ai of ais) ai.update(1 / 20);
      world.drainEvents();
      if (world.winner !== null) break;
    }
    const spans = world.entities.filter((e) => e.kind === Kind.Building && e.type === "bridge");
    expect(spans.length, "neither AI ever bridged the ford").toBeGreaterThan(0);
  }, 180000);

  it("does not bridge when the route never touches water", () => {
    // A hundred wood set on fire. The check is tied to the route it actually
    // takes, not to "is there water anywhere on this map".
    const map = generateMap("open_plains", 4, 2);
    const world = new World(4);
    world.init(map, [{}, {}], [1, 1], [0, 1]);
    world.terrain = new Uint8Array(world.terrain.length); // all grass
    const ais = [
      new SkirmishAI(world, Team.Player, DIFFICULTIES.knight),
      new SkirmishAI(world, Team.Enemy, DIFFICULTIES.knight),
    ];
    for (let i = 0; i < 20 * 60 * 10; i++) {
      world.tick();
      for (const ai of ais) ai.update(1 / 20);
      world.drainEvents();
      if (world.winner !== null) break;
    }
    expect(world.entities.some((e) => e.type === "bridge"), "bridged dry land").toBe(false);
  }, 180000);

  it("can be switched off with the rest of its terrain sense", () => {
    // Same flag as the hills: the feature has to be measurable against an AI
    // that cannot see it.
    const world = acrossAFord(4);
    const ais = [
      new SkirmishAI(world, Team.Player, DIFFICULTIES.knight),
      new SkirmishAI(world, Team.Enemy, DIFFICULTIES.knight),
    ];
    for (const ai of ais) ai.readsGround = false;
    for (let i = 0; i < 20 * 60 * 14; i++) {
      world.tick();
      for (const ai of ais) ai.update(1 / 20);
      world.drainEvents();
      if (world.winner !== null) break;
    }
    expect(world.entities.some((e) => e.type === "bridge")).toBe(false);
  }, 180000);
});
