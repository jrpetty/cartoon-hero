import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team, Kind } from "./types";
import { generateMap } from "../maps/generator";
import { ABILITIES, GUARD_ARMOR, RALLY_ATK_MULT } from "../content/abilities";
import { UNIT_TIER } from "./unit_tiers";
import { UNITS } from "../content/units";

const makeWorld = () => {
  const w = new World(4242);
  w.init(generateMap("open_plains", 4242), [{}, {}], [1, 1]);
  return w;
};

describe("Unit abilities", () => {
  it("every shop unit has one, and every one is well-formed", () => {
    for (const type of Object.keys(UNIT_TIER)) {
      const ab = ABILITIES[type];
      expect(ab, `${type} has no ability`).toBeTruthy();
      expect(ab.name.length).toBeGreaterThan(0);
      expect(ab.desc.length).toBeGreaterThan(0);
      expect(ab.cooldown).toBeGreaterThan(0);
      expect(ab.duration).toBeGreaterThan(0);
      expect(ab.color).toMatch(/^#/);
      // Area abilities need the numbers they resolve against.
      if (ab.kind === "volley" || ab.kind === "heal" || ab.kind === "cleave") {
        expect(ab.amount, `${type} ${ab.kind} needs amount`).toBeGreaterThan(0);
        expect(ab.radius, `${type} ${ab.kind} needs radius`).toBeGreaterThan(0);
      }
      if (ab.kind === "guard" || ab.kind === "rally" || ab.kind === "slow") {
        expect(ab.radius, `${type} ${ab.kind} needs radius`).toBeGreaterThan(0);
        expect(ab.statusDuration, `${type} ${ab.kind} needs statusDuration`).toBeGreaterThan(0);
      }
      // A buff that changes nothing would be a dead button.
      if (ab.kind === "buff") {
        const changes = ab.speedMult ?? ab.attackMult ?? ab.armorBonus ?? ab.atkIntervalMult ?? ab.bonusVs;
        expect(changes, `${type} buff does nothing`).toBeTruthy();
      }
    }
  });

  it("gives every ability a distinct id and name", () => {
    const ids = Object.values(ABILITIES).map((a) => a.id);
    const names = Object.values(ABILITIES).map((a) => a.name);
    expect(new Set(ids).size).toBe(ids.length);
    expect(new Set(names).size).toBe(names.length);
  });

  it("Shield Wall armours the line — the defensive counterpart to War Cry", () => {
    const w = makeWorld();
    const guard = w.spawnUnit(Team.Player, "shieldbearer", 1000, 1000);
    const mate = w.spawnUnit(Team.Player, "militia", 1040, 1000);
    const faraway = w.spawnUnit(Team.Player, "militia", 1000, 1900);
    w.tick(); // the spatial grid is built during the tick; area effects query it
    expect(w.useAbility([guard.id])).toBe(1);
    expect(mate.guardTimer).toBeGreaterThan(0);
    expect(faraway.guardTimer).toBe(0); // out of radius
    // And it actually reduces damage taken.
    const victim = w.spawnUnit(Team.Enemy, "militia", 1500, 1500);
    const bare = victim.hp;
    w.dealDamage(Team.Player, victim, 30, "twohand");
    const withoutGuard = bare - victim.hp;
    mate.hp = mate.maxHp;
    const before = mate.hp;
    w.dealDamage(Team.Enemy, mate, 30, "twohand");
    expect(before - mate.hp).toBe(withoutGuard - GUARD_ARMOR);
  });

  it("fires every ability without error, and each does something observable", () => {
    for (const type of Object.keys(UNIT_TIER)) {
      const w = makeWorld();
      const caster = w.spawnUnit(Team.Player, type, 1200, 1200);
      const ally = w.spawnUnit(Team.Player, "militia", 1240, 1200);
      // Inside the tightest area radius in the roster (Scattershot, 76).
      const foe = w.spawnUnit(Team.Enemy, "militia", 1250, 1200);
      ally.hp = Math.floor(ally.maxHp / 2);
      w.tick(); // populate the spatial grid before any area effect queries it
      const foeHp = foe.hp;
      const allyHp = ally.hp;
      expect(w.useAbility([caster.id]), `${type} did not fire`).toBe(1);
      const ab = ABILITIES[type];
      switch (ab.kind) {
        case "buff": expect(caster.abilityActive, type).toBeGreaterThan(0); break;
        case "rally": expect(ally.rallyTimer, type).toBeGreaterThan(0); break;
        case "guard": expect(ally.guardTimer, type).toBeGreaterThan(0); break;
        case "slow": expect(foe.slowTimer, type).toBeGreaterThan(0); break;
        case "heal": expect(ally.hp, type).toBeGreaterThan(allyHp); break;
        case "volley":
        case "cleave": expect(foe.hp, type).toBeLessThan(foeHp); break;
      }
      // It goes on cooldown and can't be spammed.
      expect(caster.abilityCooldown).toBeGreaterThan(0);
      expect(w.useAbility([caster.id])).toBe(0);
    }
  }, 60000);

  it("stays deterministic — the same cast resolves identically", () => {
    const run = () => {
      const w = makeWorld();
      const c = w.spawnUnit(Team.Player, "battlemage", 1200, 1200);
      const f = w.spawnUnit(Team.Enemy, "militia", 1260, 1200);
      w.useAbility([c.id]);
      for (let i = 0; i < 20; i++) w.tick();
      return f.hp;
    };
    expect(run()).toBe(run());
  });
});
