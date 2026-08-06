import { describe, expect, it, beforeAll } from "vitest";
import {
  CustomMap, deserialiseMap, newCustomMap, serialiseMap, toMapData, validateMap, hasErrors,
  mapSupports, listCustomMaps, saveCustomMap, deleteCustomMap, findCustomMap,
} from "./custom";
import { Terrain, TERRAIN_BLOCKS } from "./terrain_kinds";
import { TILE } from "../content/balance";

beforeAll(() => {
  const store: Record<string, string> = {};
  (globalThis as unknown as { localStorage: unknown }).localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
  };
});

/** A small, legal two-player map: clear ground, wood and food by each start. */
function playable(): CustomMap {
  const m = newCustomMap("Test Field", 48);
  const cell = (cx: number, cy: number) => ({ x: cx * TILE + TILE / 2, y: cy * TILE + TILE / 2 });
  m.maxPlayers = 2;
  m.spawns = [cell(10, 10), cell(37, 37)];
  for (const [sx, sy] of [[10, 10], [37, 37]] as const) {
    for (let i = 0; i < 6; i++) {
      m.resources.push({ type: "tree", ...cell(sx + 5, sy + i - 2), amount: 160 });
      m.resources.push({ type: "berries", ...cell(sx - 4, sy + i - 2), amount: 250 });
    }
    m.resources.push({ type: "gold_mine", ...cell(sx + 3, sy + 5), amount: 1000 });
  }
  return m;
}

describe("Custom map format", () => {
  it("survives a round trip through a pasteable string", () => {
    const m = playable();
    m.terrain[100] = Terrain.Rock;
    m.terrain[101] = Terrain.Forest;
    m.terrain[102] = Terrain.Hill;
    m.modes = ["conquest", "koth"];
    m.nomad = "optional";
    m.minPlayers = 2; m.maxPlayers = 4;
    const back = deserialiseMap(serialiseMap(m))!;
    expect(back).not.toBeNull();
    expect(back.name).toBe(m.name);
    expect(back.cols).toBe(m.cols);
    expect([...back.terrain]).toEqual([...m.terrain]);
    expect(back.modes).toEqual(["conquest", "koth"]);
    expect(back.nomad).toBe("optional");
    expect(back.minPlayers).toBe(2);
    expect(back.maxPlayers).toBe(4);
    expect(back.spawns).toEqual(m.spawns);
    expect(back.resources.length).toBe(m.resources.length);
  });

  it("run-length encodes, so a big empty map is still a short string", () => {
    const big = newCustomMap("Empty", 200); // 40,000 cells
    const code = serialiseMap(big);
    expect(code.length).toBeLessThan(600);
    expect(deserialiseMap(code)!.terrain.length).toBe(40000);
  });

  it("refuses anything it doesn't recognise instead of throwing", () => {
    for (const bad of ["", "hello", "BBMAP1:not-base64!!", "BBMAP1:" + btoa("{}"), btoa("{}")]) {
      expect(() => deserialiseMap(bad)).not.toThrow();
      expect(deserialiseMap(bad)).toBeNull();
    }
  });

  it("clamps a terrain value that isn't one, rather than trusting the string", () => {
    // An out-of-range terrain id would index off the end of the lookup tables
    // the movement path reads every tick.
    const m = newCustomMap("Odd", 20);
    const payload = JSON.parse(atob(serialiseMap(m).split(":")[1]));
    payload.t = `99x${20 * 20}`;
    const forged = `BBMAP1:${btoa(JSON.stringify(payload))}`;
    const back = deserialiseMap(forged)!;
    expect(back).not.toBeNull();
    for (const t of back.terrain) expect(t).toBeLessThanOrEqual(10);
  });

  it("keeps a name with characters outside Latin-1 intact", () => {
    const m = newCustomMap("Ardennes — Frühling ⚔", 24);
    expect(deserialiseMap(serialiseMap(m))!.name).toBe("Ardennes — Frühling ⚔");
  });
});

describe("Custom map validation", () => {
  it("passes a map that is actually playable", () => {
    expect(validateMap(playable()).filter((i) => i.level === "error")).toEqual([]);
  });

  it("insists on a spawn for every seat", () => {
    const m = playable();
    m.maxPlayers = 4; // only two spawns placed
    const errs = validateMap(m).filter((i) => i.level === "error");
    expect(errs.some((e) => e.text.includes("spawn point"))).toBe(true);
  });

  it("refuses a spawn with no room for a Town Centre", () => {
    const m = playable();
    const cx = Math.floor(m.spawns[0].x / TILE), cy = Math.floor(m.spawns[0].y / TILE);
    m.terrain[cy * m.cols + cx + 1] = Terrain.Rock;
    expect(validateMap(m).some((i) => i.level === "error" && i.text.includes("Town Centre"))).toBe(true);
  });

  it("catches a spawn walled off behind mountains", () => {
    const m = playable();
    // Ring the first spawn in solid rock.
    const cx = Math.floor(m.spawns[0].x / TILE), cy = Math.floor(m.spawns[0].y / TILE);
    for (let a = 0; a < 360; a += 3) {
      const r = 6;
      const px = cx + Math.round(Math.cos((a * Math.PI) / 180) * r);
      const py = cy + Math.round(Math.sin((a * Math.PI) / 180) * r);
      if (px >= 0 && py >= 0 && px < m.cols && py < m.rows) m.terrain[py * m.cols + px] = Terrain.Rock;
    }
    expect(validateMap(m).some((i) => i.level === "error" && i.text.includes("walled off"))).toBe(true);
  });

  it("notices a map with no wood at all", () => {
    const m = playable();
    m.resources = m.resources.filter((r) => r.type !== "tree");
    expect(validateMap(m).some((i) => i.level === "error" && i.text.includes("no wood"))).toBe(true);
  });

  it("warns when one seat is much richer than another", () => {
    const m = playable();
    // Strip the second start bare.
    m.resources = m.resources.filter((r) => Math.hypot(r.x - m.spawns[1].x, r.y - m.spawns[1].y) / TILE > 14);
    expect(validateMap(m).some((i) => i.level === "warning" && i.text.includes("not equal"))).toBe(true);
  });

  it("requires open edges for a survival map", () => {
    const m = playable();
    m.modes = ["survival"];
    expect(hasErrors(validateMap(m))).toBe(false);
    for (let cx = 0; cx < m.cols; cx++) {
      m.terrain[cx] = Terrain.Rock;
      m.terrain[(m.rows - 1) * m.cols + cx] = Terrain.Rock;
    }
    for (let cy = 0; cy < m.rows; cy++) {
      m.terrain[cy * m.cols] = Terrain.Rock;
      m.terrain[cy * m.cols + m.cols - 1] = Terrain.Rock;
    }
    expect(validateMap(m).some((i) => i.level === "error" && i.text.includes("Survival"))).toBe(true);
  });

  it("requires a reachable centre for King of the Hill", () => {
    const m = playable();
    m.modes = ["koth"];
    expect(hasErrors(validateMap(m))).toBe(false);
    m.terrain[Math.floor(m.rows / 2) * m.cols + Math.floor(m.cols / 2)] = Terrain.Water;
    expect(validateMap(m).some((i) => i.level === "error" && i.text.includes("King of the Hill"))).toBe(true);
  });

  it("stops asking for spawn points once a map is always-nomad", () => {
    const m = playable();
    m.spawns = [];
    m.maxPlayers = 6;
    expect(hasErrors(validateMap(m))).toBe(true);
    m.nomad = "forced";
    expect(validateMap(m).filter((i) => i.level === "error")).toEqual([]);
  });
});

describe("Custom map to a match", () => {
  it("seats players on the authored spawns, in order", () => {
    const m = playable();
    const md = toMapData(m, 7, 2);
    expect(md.starts).toEqual(m.spawns);
    expect(md.nomad).toBe(false);
    expect(md.custom).toBe(true);
  });

  it("derives blocked cells from the ground, and only from the ground", () => {
    const m = playable();
    m.terrain[500] = Terrain.Rock;
    m.terrain[501] = Terrain.Water;
    m.terrain[502] = Terrain.Forest; // passable — must NOT be blocked
    const md = toMapData(m, 1, 2);
    const blocked = new Set(md.blockedCells.map(([cx, cy]) => cy * m.cols + cx));
    expect(blocked.has(500)).toBe(true);
    expect(blocked.has(501)).toBe(true);
    expect(blocked.has(502)).toBe(false);
    for (const i of blocked) expect(TERRAIN_BLOCKS[md.terrain[i]]).toBe(1);
  });

  it("never lets a match edit the author's copy of the map", () => {
    const m = playable();
    const md = toMapData(m, 1, 2);
    md.terrain[0] = Terrain.Rock;
    expect(m.terrain[0]).not.toBe(Terrain.Rock);
  });

  it("ignores spawn points in a nomad game, and is still replayable", () => {
    const m = playable();
    m.nomad = "optional";
    const a = toMapData(m, 4242, 2, true);
    const b = toMapData(m, 4242, 2, true);
    expect(a.starts).toEqual(b.starts);        // same seed, same landing sites
    expect(a.starts).not.toEqual(m.spawns);    // and not the authored ones
    expect(a.nomad).toBe(true);
    // A different seed puts them somewhere else.
    expect(toMapData(m, 99, 2, true).starts).not.toEqual(a.starts);
  });

  it("ignores the nomad request on a map that forbids it", () => {
    const m = playable();
    m.nomad = "off";
    expect(toMapData(m, 1, 2, true).starts).toEqual(m.spawns);
  });

  it("is always nomad when the map says so, whatever the match asks", () => {
    const m = playable();
    m.nomad = "forced";
    expect(toMapData(m, 1, 2, false).starts).not.toEqual(m.spawns);
    expect(toMapData(m, 1, 2, false).nomad).toBe(true);
  });

  it("only ever lands a nomad start on ground a Town Centre fits on", () => {
    const m = playable();
    m.nomad = "forced";
    // Scatter mountains over most of the map, leaving corners open.
    for (let cy = 8; cy < m.rows - 8; cy++) {
      for (let cx = 8; cx < m.cols - 8; cx++) {
        if ((cx * 7 + cy * 13) % 11 === 0) m.terrain[cy * m.cols + cx] = Terrain.Rock;
      }
    }
    for (const seed of [1, 2, 3, 4, 5]) {
      const md = toMapData(m, seed, 4, true);
      expect(md.starts.length).toBe(4);
      for (const s of md.starts) {
        const cx = Math.floor(s.x / TILE), cy = Math.floor(s.y / TILE);
        expect(TERRAIN_BLOCKS[m.terrain[cy * m.cols + cx]]).toBe(0);
      }
    }
  });

  it("always returns as many starts as there are players", () => {
    const m = playable();
    m.nomad = "forced";
    for (const players of [1, 2, 4, 8]) {
      expect(toMapData(m, 3, players, true).starts.length).toBe(players);
    }
  });
});

describe("Custom map library", () => {
  it("saves, lists, finds and deletes", () => {
    const m = playable();
    m.name = "Library Test";
    saveCustomMap(m);
    expect(listCustomMaps().some((x) => x.id === m.id)).toBe(true);
    const found = findCustomMap(m.id)!;
    expect(found.name).toBe("Library Test");
    expect([...found.terrain]).toEqual([...m.terrain]);
    deleteCustomMap(m.id);
    expect(findCustomMap(m.id)).toBeNull();
  });

  it("overwrites rather than duplicating when the same map is saved twice", () => {
    const m = playable();
    saveCustomMap(m);
    m.name = "Renamed";
    saveCustomMap(m);
    const all = listCustomMaps().filter((x) => x.id === m.id);
    expect(all.length).toBe(1);
    expect(all[0].name).toBe("Renamed");
    deleteCustomMap(m.id);
  });
});

describe("Which matches a map allows", () => {
  it("takes any mode when the author named none", () => {
    const m = playable();
    m.modes = [];
    expect(mapSupports(m, "conquest", 2)).toBe(true);
    expect(mapSupports(m, "survival", 2)).toBe(true);
  });

  it("honours a 1v1-only map", () => {
    const m = playable();
    m.modes = ["conquest"];
    m.minPlayers = 2; m.maxPlayers = 2;
    expect(mapSupports(m, "conquest", 2)).toBe(true);
    expect(mapSupports(m, "conquest", 4)).toBe(false);
    expect(mapSupports(m, "koth", 2)).toBe(false);
  });
});
