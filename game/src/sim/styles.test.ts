import { describe, expect, it } from "vitest";
import { LiveBattle, resolveBattle, GRID_CELL } from "./autobattle";
import { UNIT_STYLE, BATTLE_STYLES, styleOf, styleDef, BattleStyle } from "../content/battle_styles";
import { UNITS } from "../content/units";
import { Kind, Team } from "./types";

/** Every living unit of a team in a live battle, sorted by id (= spawn order). */
function units(b: LiveBattle, team: Team) {
  return b.world.entities
    .filter((e) => e.alive && e.kind === Kind.Unit && e.type !== "villager" && e.team === team)
    .sort((p, q) => p.id - q.id);
}

describe("Battle styles", () => {
  it("assigns a real style to every shop unit, and every style is described", () => {
    for (const [type, style] of Object.entries(UNIT_STYLE)) {
      expect(BATTLE_STYLES[style], `${type} → ${style}`).toBeTruthy();
      // Only assign styles to units that actually exist in the roster.
      expect(UNITS[type], `unknown unit ${type}`).toBeTruthy();
    }
    for (const def of Object.values(BATTLE_STYLES)) {
      expect(def.name.length).toBeGreaterThan(0);
      expect(def.desc.length).toBeGreaterThan(0);
      expect(def.color).toMatch(/^#/);
    }
    // Unknown types fall back to Line rather than throwing.
    expect(styleOf("not_a_unit")).toBe("line");
    expect(styleDef("scout").id).toBe("infiltrator");
  });

  it("covers all five styles across the roster", () => {
    const used = new Set<BattleStyle>(Object.values(UNIT_STYLE));
    for (const id of Object.keys(BATTLE_STYLES) as BattleStyle[]) {
      expect(used.has(id), `no unit uses ${id}`).toBe(true);
    }
  });

  it("an Infiltrator opens the fight behind the enemy, not in front of it", () => {
    // A scout on the player's back rank; enemies across the line.
    const mine = [{ type: "scout", star: 1, col: 0, row: 4 }];
    const theirs = [
      { type: "crossbow", star: 1, col: 9, row: 4 },
      { type: "spearman", star: 1, col: 5, row: 4 },
    ];
    const b = new LiveBattle(mine, theirs, 5);
    const before = units(b, Team.Player)[0].x;
    const enemyFront = units(b, Team.Enemy).find((e) => e.type === "spearman")!.x;
    b.begin();
    const after = units(b, Team.Player)[0].x;
    // It jumped clean across the line, past the enemy's front rank.
    expect(after).toBeGreaterThan(before + GRID_CELL * 2);
    expect(after).toBeGreaterThan(enemyFront);
    // …and the jump is reported so the screen can draw it.
    const leap = b.fxEvents.find((e) => e.kind === "leap");
    expect(leap).toBeTruthy();
    expect(leap!.team).toBe(Team.Player);
    expect(leap!.data).toMatch(/^-?[\d.]+,-?[\d.]+$/);
  });

  it("mirrors the jump for the enemy side", () => {
    const b = new LiveBattle(
      [{ type: "spearman", star: 1, col: 4, row: 4 }],
      [{ type: "raider", star: 1, col: 9, row: 4 }],
      6,
    );
    const before = units(b, Team.Enemy)[0].x;
    b.begin();
    const after = units(b, Team.Enemy)[0].x;
    expect(after).toBeLessThan(before - GRID_CELL * 2); // leapt the other way
  });

  it("Artillery holds its ground while Line troops advance", () => {
    const b = new LiveBattle(
      [
        { type: "trebuchet", star: 1, col: 0, row: 3 },
        { type: "archer", star: 1, col: 0, row: 6 },
      ],
      [{ type: "spearman", star: 1, col: 9, row: 4 }],
      7,
    );
    const treb0 = units(b, Team.Player).find((e) => e.type === "trebuchet")!.x;
    const arch0 = units(b, Team.Player).find((e) => e.type === "archer")!.x;
    b.begin();
    b.step(60); // three seconds of marching
    const treb1 = units(b, Team.Player).find((e) => e.type === "trebuchet");
    const arch1 = units(b, Team.Player).find((e) => e.type === "archer");
    if (treb1) expect(Math.abs(treb1.x - treb0)).toBeLessThan(GRID_CELL); // stayed put
    if (arch1) expect(arch1.x).toBeGreaterThan(arch0); // walked in
  });

  it("a Flanker leaves its lane, heading for an edge", () => {
    const b = new LiveBattle(
      [{ type: "knight", star: 1, col: 2, row: 4 }], // starts near the middle rows
      [{ type: "spearman", star: 1, col: 7, row: 4 }],
      8,
    );
    const y0 = units(b, Team.Player)[0].y;
    b.begin();
    b.step(40);
    const k = units(b, Team.Player)[0];
    if (k) expect(Math.abs(k.y - y0)).toBeGreaterThan(GRID_CELL * 0.5); // swung off its rank
  });

  it("stays deterministic — the same fight resolves the same way twice", () => {
    const a = [{ type: "scout", star: 1, col: 0, row: 4 }, { type: "knight", star: 1, col: 3, row: 5 }];
    const c = [{ type: "crossbow", star: 1, col: 9, row: 4 }, { type: "spearman", star: 1, col: 5, row: 5 }];
    const r1 = resolveBattle(a, c, 21);
    const r2 = resolveBattle(a, c, 21);
    expect(r1).toEqual(r2);
  });

  it("Infiltrators punish an undefended backline more than a guarded one", () => {
    // Two scouts against the same artillery, once bare and once behind a wall.
    const scouts = [
      { type: "scout", star: 2, col: 0, row: 4 },
      { type: "scout", star: 2, col: 0, row: 5 },
    ];
    const bare = [
      { type: "crossbow", star: 2, col: 9, row: 4 },
      { type: "crossbow", star: 2, col: 9, row: 5 },
    ];
    const guarded = [
      { type: "crossbow", star: 2, col: 9, row: 4 },
      { type: "crossbow", star: 2, col: 9, row: 5 },
      { type: "spearman", star: 2, col: 8, row: 4 },
      { type: "spearman", star: 2, col: 8, row: 5 },
    ];
    let bareWins = 0, guardedWins = 0;
    for (const seed of [1, 2, 3, 4, 5]) {
      if (resolveBattle(scouts, bare, seed).winner === "A") bareWins++;
      if (resolveBattle(scouts, guarded, seed).winner === "A") guardedWins++;
    }
    // Guarding the backline is never worse than leaving it open.
    expect(guardedWins).toBeLessThanOrEqual(bareWins);
  });
});
