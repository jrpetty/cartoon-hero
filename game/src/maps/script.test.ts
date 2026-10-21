import { describe, expect, it } from "vitest";
import { EXAMPLE_SCRIPT, runMapScript } from "./script";
import { Terrain } from "./terrain_kinds";
import { hasErrors, serialiseMap, deserialiseMap, toMapData, validateMap } from "./custom";
import { TILE } from "../content/balance";
import { World } from "../sim/world";
import { Team } from "../sim/types";
import { SkirmishAI } from "../ai/skirmish_ai";
import { DIFFICULTIES } from "../ai/difficulty";

const ok = (src: string, seed = 1) => {
  const r = runMapScript(src, seed);
  expect(r.errors.map((e) => `line ${e.line}: ${e.text}`)).toEqual([]);
  expect(r.map).not.toBeNull();
  return r.map!;
};

const countTerrain = (m: { terrain: Uint8Array }, t: Terrain) =>
  [...m.terrain].filter((x) => x === t).length;

describe("The script format", () => {
  it("makes a map from nothing at all", () => {
    // Every command has a default, because a format where you must say twelve
    // things before you can say the one you care about is one nobody writes in.
    const m = ok("");
    expect(m.cols).toBe(128);
    expect(m.terrain.length).toBe(128 * 128);
  });

  it("ignores comments and blank lines", () => {
    const m = ok(`
      # a comment
      size 64    # and a trailing one

    `);
    expect(m.cols).toBe(64);
  });

  it("takes named arguments in any order", () => {
    // Remembering an argument order is exactly the friction that stops someone
    // writing a script at all.
    const a = ok("base grass\nblob rock count 6 radius 3-5", 7);
    const b = ok("base grass\nblob rock radius 3-5 count 6", 7);
    expect([...b.terrain]).toEqual([...a.terrain]);
  });

  it("is deterministic — the same script and seed give the same map", () => {
    const a = ok(EXAMPLE_SCRIPT, 99);
    const b = ok(EXAMPLE_SCRIPT, 99);
    expect([...b.terrain]).toEqual([...a.terrain]);
    expect(b.resources).toEqual(a.resources);
    expect(b.spawns).toEqual(a.spawns);
  });

  it("is a family, not a map — a different seed is a different battlefield", () => {
    // The whole point of the format. If seeds gave the same map it would just
    // be a slower editor.
    const a = ok(EXAMPLE_SCRIPT, 1);
    const b = ok(EXAMPLE_SCRIPT, 2);
    expect([...b.terrain]).not.toEqual([...a.terrain]);
    // …but the same *character*: both should still be recognisably alpine
    // highlands with mountains and hills in them.
    for (const m of [a, b]) {
      expect(countTerrain(m, Terrain.Rock)).toBeGreaterThan(50);
      expect(countTerrain(m, Terrain.Hill)).toBeGreaterThan(50);
    }
  });
});

describe("Reporting what is wrong", () => {
  it("names the line and says what it expected", () => {
    // A script language with a silent failure mode is worse than none: you get
    // a map, it just isn't the one you wrote.
    const r = runMapScript("size 128\nblob nonsense count 3\n", 1);
    expect(r.map).toBeNull();
    expect(r.errors).toHaveLength(1);
    expect(r.errors[0].line).toBe(2);
    expect(r.errors[0].text).toContain("nonsense");
  });

  it("rejects an unknown command and lists the ones that exist", () => {
    const r = runMapScript("frobnicate everything", 1);
    expect(r.map).toBeNull();
    expect(r.errors[0].text).toContain("blob");
  });

  it("refuses a size outside what the game can play", () => {
    expect(runMapScript("size 5", 1).errors[0].text).toContain("40");
    expect(runMapScript("size 9000", 1).errors[0].text).toContain("320");
  });

  it("refuses an unknown biome or symmetry by name", () => {
    expect(runMapScript("biome tropical", 1).errors[0].text).toContain("temperate");
    expect(runMapScript("symmetry sideways", 1).errors[0].text).toContain("rot180");
  });

  it("warns rather than fails when clusters are asked for before any seats exist", () => {
    // Ordering is the format's one real constraint, so it has to be said out
    // loud rather than silently producing a map with no gold on it.
    const r = runMapScript("base grass\ncluster gold count 2 near start", 1);
    expect(r.map).not.toBeNull();
    expect(r.warnings.some((w) => w.text.includes("spawns"))).toBe(true);
  });

  it("caps an absurd count instead of hanging, and says it did", () => {
    const r = runMapScript("scatter tree count 999999", 1);
    expect(r.map).not.toBeNull();
    expect(r.warnings.some((w) => w.text.includes("capped"))).toBe(true);
  });
});

describe("What the commands do", () => {
  it("base fills the whole map", () => {
    const m = ok("size 64\nbase snow");
    expect(countTerrain(m, Terrain.Snow)).toBe(64 * 64);
  });

  it("blob paints roughly what was asked for, and more of it for a bigger radius", () => {
    const small = ok("size 96\nbase grass\nblob rock count 10 radius 2", 5);
    const big = ok("size 96\nbase grass\nblob rock count 10 radius 6", 5);
    expect(countTerrain(big, Terrain.Rock)).toBeGreaterThan(countTerrain(small, Terrain.Rock) * 2);
  });

  it("accepts the words an author would actually reach for", () => {
    // "mountain" and "lake" are what people write; Rock and Water are what the
    // enum calls them.
    const m = ok("size 64\nbase grass\nlake lake count 3 radius 4\nrange mountain count 3 radius 4", 3);
    expect(countTerrain(m, Terrain.Water)).toBeGreaterThan(0);
    expect(countTerrain(m, Terrain.Rock)).toBeGreaterThan(0);
  });

  it("river lays a crossable band from one edge toward the other", () => {
    const m = ok("size 96\nbase grass\nriver shallow width 3", 4);
    const shallow = countTerrain(m, Terrain.Shallow);
    expect(shallow).toBeGreaterThan(96); // at least a cell per row of the span
  });

  it("ring lays a band at a fraction of the way out", () => {
    const m = ok("size 96\nbase grass\nring water radius 0.5 width 4");
    expect(countTerrain(m, Terrain.Water)).toBeGreaterThan(100);
    // The centre is untouched — that is what makes it a ring and not a disc.
    expect(m.terrain[48 * 96 + 48]).toBe(Terrain.Grass);
  });

  it("seats players evenly around a circle", () => {
    const m = ok("size 128\nseats 4\nspawns ring radius 0.34");
    expect(m.spawns).toHaveLength(4);
    // Equidistant from the centre, which is what "fair" means here.
    const ox = (128 * TILE) / 2, oy = ox;
    const ds = m.spawns.map((s) => Math.hypot(s.x - ox, s.y - oy));
    for (const d of ds) expect(Math.abs(d - ds[0])).toBeLessThan(TILE * 2);
  });

  it("mirrors an explicit spawn through the symmetry", () => {
    const m = ok("size 64\nseats 2\nsymmetry rot180\nspawn at 12,12");
    expect(m.spawns.length).toBe(2);
    const [a, b] = m.spawns;
    // Opposite corners: the two should be symmetric about the centre.
    const ox = (64 * TILE) / 2, oy = ox;
    expect(Math.abs((a.x - ox) + (b.x - ox))).toBeLessThan(TILE * 2);
    expect(Math.abs((a.y - oy) + (b.y - oy))).toBeLessThan(TILE * 2);
  });

  it("gives every seat its own clusters, at the distance asked for", () => {
    // "6 gold clusters at radius 20-40 from each start, mirrored" — the thing
    // the format exists to say.
    const m = ok(`
      size 160
      seats 2
      base grass
      spawns ring radius 0.3
      cluster gold count 6 near start radius 20-40 nodes 4
    `, 11);
    const gold = m.resources.filter((r) => r.type === "gold_mine");
    expect(gold.length).toBeGreaterThan(20);
    // Each node sits within the asked-for band of *some* seat.
    for (const g of gold) {
      const near = m.spawns.some((s) => {
        const d = Math.hypot(g.x - s.x, g.y - s.y) / TILE;
        return d >= 18 && d <= 44; // the band, plus the cluster's own spread
      });
      expect(near, `a cluster landed at the wrong distance from every seat`).toBe(true);
    }
  });

  it("never puts a resource inside a wall", () => {
    // A tree in a lake is a resource no villager can ever reach.
    const m = ok(`
      size 96
      base water
      blob grass count 6 radius 8
      scatter tree count 400
    `, 8);
    for (const r of m.resources) {
      const cx = Math.floor(r.x / TILE), cy = Math.floor(r.y / TILE);
      expect(m.terrain[cy * m.cols + cx], "a node was placed in a wall").not.toBe(Terrain.Water);
    }
  });

  it("never stacks two nodes on one cell", () => {
    const m = ok("size 64\nbase grass\nscatter tree count 900\nscatter gold count 300", 2);
    const cells = m.resources.map((r) => `${Math.floor(r.x / TILE)},${Math.floor(r.y / TILE)}`);
    expect(new Set(cells).size).toBe(cells.length);
  });

  it("marks a map with no seats as nomad rather than shipping a broken one", () => {
    const r = runMapScript("size 64\nbase grass", 1);
    expect(r.map!.nomad).toBe("forced");
    expect(r.warnings.some((w) => w.text.includes("nomad"))).toBe(true);
  });
});

describe("A scripted map is a real map", () => {
  it("validates clean", () => {
    const m = ok(EXAMPLE_SCRIPT, 21);
    const issues = validateMap(m);
    expect(hasErrors(issues), issues.map((i) => i.text).join("; ")).toBe(false);
  });

  it("round-trips through a share code", () => {
    const m = ok(EXAMPLE_SCRIPT, 5);
    const back = deserialiseMap(serialiseMap(m));
    expect(back).not.toBeNull();
    expect([...back!.terrain]).toEqual([...m.terrain]);
    expect(back!.spawns.length).toBe(m.spawns.length);
  });

  it("actually plays — economies grow on it", () => {
    // The end of the chain: script → CustomMap → MapData → a running match with
    // real AI on both sides. Without brains nobody gathers and the test would
    // be measuring nothing.
    //
    // Growth between two samples, not a total at one arbitrary moment. The two
    // seats on this map start at noticeably different speeds — 160 against 430
    // gathered at the two-minute mark — so a fixed threshold there measures the
    // seat lottery rather than whether the map supports an economy at all.
    const m = ok(EXAMPLE_SCRIPT, 33);
    const data = toMapData(m, 33, 2);
    const w = new World(33);
    w.init(data, [{}, {}], [1, 1], [0, 1]);
    const ais = [
      new SkirmishAI(w, Team.Player, DIFFICULTIES.knight),
      new SkirmishAI(w, Team.Enemy, DIFFICULTIES.knight),
    ];
    const run = (secs: number) => {
      for (let i = 0; i < 20 * secs; i++) {
        w.tick();
        for (const ai of ais) ai.update(1 / 20);
        w.drainEvents();
      }
      return [Team.Player, Team.Enemy].map((t) => w.player(t).stats.gathered);
    };
    const early = run(120);
    const later = run(120);
    for (const t of [0, 1]) {
      expect(early[t], `team ${t} gathered nothing at all`).toBeGreaterThan(50);
      expect(later[t], `team ${t}'s economy stalled: ${early[t]} → ${later[t]}`)
        .toBeGreaterThan(early[t] * 2);
    }
  }, 60000);

  it("keeps every seat reachable from every other", () => {
    // A script can paint mountains anywhere, so this is the failure mode that
    // matters most: a beautiful map where one player is walled into a pocket.
    for (const seed of [1, 2, 3, 4, 5]) {
      const m = ok(EXAMPLE_SCRIPT, seed);
      const issues = validateMap(m);
      const walled = issues.filter((i) => i.level === "error" && /reach|isolat|connect/i.test(i.text));
      expect(walled.map((i) => i.text), `seed ${seed}`).toEqual([]);
    }
  });
});

describe("The worked example", () => {
  it("runs clean, since it is also the documentation", () => {
    const r = runMapScript(EXAMPLE_SCRIPT, 1);
    expect(r.errors).toEqual([]);
    expect(r.warnings.map((w) => w.text)).toEqual([]);
  });

  it("produces a playable four-seat map", () => {
    const m = ok(EXAMPLE_SCRIPT, 1);
    expect(m.spawns).toHaveLength(4);
    expect(m.maxPlayers).toBe(4);
    expect(m.resources.length).toBeGreaterThan(100);
  });
});

describe("Seats get room to breathe", () => {
  /** Cells between a resource and the nearest seat, Chebyshev. */
  const gapToSeat = (m: { resources: { x: number; y: number }[]; spawns: { x: number; y: number }[] }) => {
    let min = Infinity;
    for (const r of m.resources) {
      for (const s of m.spawns) {
        const d = Math.max(
          Math.abs(Math.floor(r.x / TILE) - Math.floor(s.x / TILE)),
          Math.abs(Math.floor(r.y / TILE) - Math.floor(s.y / TILE)),
        );
        min = Math.min(min, d);
      }
    }
    return min;
  };

  it("never scatters a resource onto a start", () => {
    // Measured on the worked example before this existed: two nodes within two
    // cells of *both* seats, sitting on the Town Centre, and nine within six
    // cells of one seat against four at the other. Blocking nodes in a base cost
    // it half its early gather rate — which is how a map that is symmetric by
    // every other measure played 160 against 430 in its first two minutes.
    const m = ok(EXAMPLE_SCRIPT, 33);
    expect(m.spawns.length).toBeGreaterThan(0);
    expect(gapToSeat(m), "a resource is sitting on a seat").toBeGreaterThan(4);
  });

  it("holds across seeds, not just the one that was debugged", () => {
    for (const seed of [1, 7, 33, 99, 404]) {
      const m = ok(EXAMPLE_SCRIPT, seed);
      expect(gapToSeat(m), `seed ${seed}`).toBeGreaterThan(4);
    }
  });

  it("clears up afterwards when the script seats players last", () => {
    // Nothing in the format forces `spawns` above the resource lines, so the
    // check cannot rely on the order it happens to run in.
    const r = runMapScript(`
      size 96
      seats 2
      base grass
      scatter tree count 900
      spawns ring radius 0.3
    `, 5);
    expect(r.map).not.toBeNull();
    expect(gapToSeat(r.map!), "a late spawns line left nodes on the seats").toBeGreaterThan(4);
    expect(r.warnings.some((w) => w.text.includes("on top of a spawn")),
      "cleared nodes silently").toBe(true);
  });

  it("leaves the author's deliberately-close clusters alone", () => {
    // The clearance has to be small enough that a starting berry patch authored
    // at six to ten cells stays exactly where it was put.
    const m = ok(`
      size 128
      seats 2
      base grass
      spawns ring radius 0.3
      cluster berries count 1 near start radius 6-8 nodes 6
    `, 3);
    const berries = m.resources.filter((r) => r.type === "berries");
    expect(berries.length, "the starting berries were cleared away").toBeGreaterThan(4);
  });

  it("keeps both seats about equally clear", () => {
    // The asymmetry that started this: one seat's base far more cluttered than
    // the other's, on a map that is symmetric in every other respect.
    const m = ok(EXAMPLE_SCRIPT, 33);
    const within = (s: { x: number; y: number }, tiles: number) =>
      m.resources.filter((r) => Math.hypot(r.x - s.x, r.y - s.y) < TILE * tiles).length;
    const counts = m.spawns.map((s) => within(s, 6));
    const spread = Math.max(...counts) - Math.min(...counts);
    expect(spread, `nodes within six cells per seat: ${counts.join(", ")}`).toBeLessThanOrEqual(6);
  });
});
