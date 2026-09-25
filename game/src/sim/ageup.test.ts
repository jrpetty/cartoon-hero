import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team, Kind } from "./types";
import { generateMap } from "../maps/generator";
import { AGES } from "../content/tech";

function makeWorld(): World {
  const map = generateMap("open_plains", 1234);
  const w = new World(1234);
  w.init(map, [{}, {}], [1, 1]);
  return w;
}

// Build a finished building of a type near the player's base (scan many spots).
function build(w: World, type: string): void {
  const start = w.map.starts[Team.Player];
  w.player(Team.Player).resources = { food: 9999, wood: 9999, gold: 9999 }; // placement also pays
  for (let r = 140; r <= 520; r += 36) {
    for (let a = 0; a < 16; a++) {
      const ang = (a / 16) * Math.PI * 2;
      const b = w.placeBuilding(Team.Player, type, start.x + Math.cos(ang) * r, start.y + Math.sin(ang) * r);
      if (b) { b.buildState = 0; return; }
    }
  }
  throw new Error("no spot for " + type);
}

describe("Open build order (any-2-of-N age-up)", () => {
  it("Feudal needs any 2 of a set, not a single fixed building", () => {
    expect(AGES[1].requiresCount).toBe(2);
    expect(AGES[1].requiresAny.length).toBeGreaterThanOrEqual(3);
    const w = makeWorld();
    // Start: only a Town Center — no qualifiers.
    expect(w.ageRequirementMet(Team.Player, 1)).toBe(false);
    build(w, "barracks");
    expect(w.ageRequirementMet(Team.Player, 1)).toBe(false); // 1/2
    build(w, "mill");
    expect(w.ageRequirementMet(Team.Player, 1)).toBe(true); // 2/2
  });

  it("a pure-economy path can reach Feudal without a Barracks", () => {
    const w = makeWorld();
    build(w, "mill");
    build(w, "lumber_camp");
    expect(w.ageRequirementMet(Team.Player, 1)).toBe(true);
    // And research actually starts once the requirement + cost are met.
    const p = w.player(Team.Player);
    p.resources.food = 999;
    const tc = w.entitiesOf(Team.Player, Kind.Building).find((e) => e.type === "town_center")!;
    expect(w.research(Team.Player, tc.id, "age")).toBe(true);
  });

  it("research is blocked until the requirement is met", () => {
    const w = makeWorld();
    const p = w.player(Team.Player);
    p.resources.food = 999;
    const tc = w.entitiesOf(Team.Player, Kind.Building).find((e) => e.type === "town_center")!;
    expect(w.research(Team.Player, tc.id, "age")).toBe(false); // no qualifiers yet
    build(w, "barracks");
    build(w, "mining_camp");
    expect(w.research(Team.Player, tc.id, "age")).toBe(true);
  });
});
