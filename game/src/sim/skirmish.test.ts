import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team, Stance, Kind } from "./types";
import { generateMap } from "../maps/generator";
import { SIM_HZ } from "../content/balance";

/** One-on-one to the death. Returns who was left standing. */
function duel(rangedType: string, meleeType: string, stance: Stance) {
  const w = new World(1234);
  w.init(generateMap("open_plains", 1234), [{}, {}], [1, 1]);
  const r = w.spawnUnit(Team.Player, rangedType, 1000, 1400);
  const m = w.spawnUnit(Team.Enemy, meleeType, 1300, 1400);
  r.stance = stance;
  w.issueAttack([r.id], m.id);
  w.issueAttack([m.id], r.id);
  for (let i = 0; i < SIM_HZ * 45; i++) {
    w.tick();
    if (!r.alive || !m.alive) break;
  }
  return r.alive && !m.alive ? "ranged" : !r.alive && m.alive ? "melee" : "draw";
}

describe("Skirmish stance", () => {
  it("lets ranged units beat infantry they would otherwise lose to", () => {
    // Standing and trading, an archer loses to a swordsman every time. Giving
    // ground between shots is what turns it around — this is the "archers melt
    // infantry" leg of the triangle, and it comes from behaviour rather than
    // from propping up the bonus damage.
    expect(duel("archer", "militia", Stance.Defensive)).toBe("melee");
    expect(duel("archer", "militia", Stance.Skirmish)).toBe("ranged");
    expect(duel("archer", "twohand", Stance.Skirmish)).toBe("ranged");
    expect(duel("crossbow", "twohand", Stance.Skirmish)).toBe("ranged");
  }, 60000);

  it("does not save them from cavalry — which is what keeps it fair", () => {
    // A skirmisher moves at 76-80 and only *holds* the gap against infantry
    // (78-80). Cavalry runs at 112-135 and closes anyway, so the counter that
    // is supposed to answer massed archers still answers them.
    expect(duel("archer", "knight", Stance.Skirmish)).toBe("melee");
    expect(duel("archer", "horseman", Stance.Skirmish)).toBe("melee");
    expect(duel("crossbow", "knight", Stance.Skirmish)).toBe("melee");
  }, 60000);

  it("holds its ground when the threat cannot reach it", () => {
    // Nothing to gain by backing away from another archer, so it shouldn't.
    const w = new World(99);
    w.init(generateMap("open_plains", 99), [{}, {}], [1, 1]);
    const a = w.spawnUnit(Team.Player, "archer", 1000, 1400);
    const b = w.spawnUnit(Team.Enemy, "archer", 1120, 1400);
    a.stance = Stance.Skirmish;
    w.issueAttack([a.id], b.id);
    w.issueAttack([b.id], a.id);
    const x0 = a.x;
    for (let i = 0; i < SIM_HZ * 4; i++) { w.tick(); if (!a.alive || !b.alive) break; }
    expect(Math.abs(a.x - x0)).toBeLessThan(40); // stood and shot
  });

  it("doesn't back away from things that can't chase it", () => {
    // Buildings don't follow, so there is nothing to give ground to. An archer
    // that crept backwards from a house would never finish a siege.
    const w = new World(5);
    w.init(generateMap("open_plains", 5), [{}, {}], [1, 1]);
    const a = w.spawnUnit(Team.Player, "archer", 1000, 1400);
    let house = null;
    for (const [hx, hy] of [[1060, 1400], [1060, 1340], [1080, 1400]]) {
      house = w.placeBuilding(Team.Enemy, "house", hx, hy);
      if (house) break;
    }
    expect(house).not.toBeNull();
    house!.buildState = 0;
    house!.hp = 40;
    a.stance = Stance.Skirmish;
    w.issueAttack([a.id], house!.id);
    const x0 = a.x;
    for (let i = 0; i < SIM_HZ * 20; i++) { w.tick(); if (!house!.alive) break; }
    expect(house!.alive).toBe(false);          // it kept shooting
    expect(Math.abs(a.x - x0)).toBeLessThan(60); // and stood its ground
  }, 30000);

  it("is a real stance the sim accepts and reports", () => {
    const w = new World(3);
    w.init(generateMap("open_plains", 3), [{}, {}], [1, 1]);
    const a = w.spawnUnit(Team.Player, "archer", 1000, 1000);
    expect(a.stance).not.toBe(Stance.Skirmish); // opt-in, not the default
    w.setStance([a.id], Stance.Skirmish);
    expect(a.stance).toBe(Stance.Skirmish);
    expect(a.kind).toBe(Kind.Unit);
  });
});
