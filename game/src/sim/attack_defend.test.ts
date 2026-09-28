import { describe, expect, it } from "vitest";
import { World } from "./world";
import { generateMap } from "../maps/generator";
import { matchReport } from "./metrics";
import { careerMatch } from "../meta/career";
import { matchStyles } from "../meta/playstyle";
import { Kind, Team } from "./types";

/**
 * "Rushed before 7:00" has to mean you attacked *their base*, not that you
 * beat off their attack in yours. Every blow is classed by whose ground it
 * landed on: attack (theirs), defend (yours) or field (no one's).
 */

const P = Team.Player, E = Team.Enemy;
function setup() {
  const w = new World(8);
  w.init(generateMap("open_plains", 8), [{}, {}], [1, 1], undefined, ["", ""], false, undefined, "conquest");
  w.tick();
  return w;
}
const tc = (w: World, t: Team) => w.entities.find((e) => e.alive && e.team === t && e.type === "town_center")!;
const run = (w: World, sec: number) => { for (let i = 0; i < sec * 20; i++) w.tick(); };
/** A fight between one of each side at a point, with the attacker told to attack. */
function fightAt(w: World, attacker: Team, x: number, y: number) {
  const a = w.spawnUnit(attacker, "militia", x - 12, y);
  const d = w.spawnUnit(attacker === P ? E : P, "spearman", x + 12, y);
  w.issueAttack([a.id], d.id);
  w.issueAttack([d.id], a.id);
  run(w, 3);
  return { a, d };
}

describe("Attacking vs defending", () => {
  it("a fight at the enemy's Town Center is an attack for you and a defence for them", () => {
    const w = setup();
    const home = tc(w, E);
    fightAt(w, P, home.x, home.y + 120);
    const you = w.player(P).stats, them = w.player(E).stats;
    expect(you.firstAttackAt).toBeGreaterThanOrEqual(0);
    expect(you.firstDefendAt).toBe(-1);
    expect(you.damageAttacking).toBeGreaterThan(0);
    expect(them.firstDefendAt).toBeGreaterThanOrEqual(0);
    expect(them.firstAttackAt, "defending at home is not an attack").toBe(-1);
    expect(them.damageDefending).toBeGreaterThan(0);
  });

  it("a fight at your own base is a defence for you", () => {
    const w = setup();
    const home = tc(w, P);
    fightAt(w, E, home.x, home.y + 120);
    expect(w.player(P).stats.firstAttackAt).toBe(-1);
    expect(w.player(P).stats.firstDefendAt).toBeGreaterThanOrEqual(0);
    expect(w.player(E).stats.firstAttackAt).toBeGreaterThanOrEqual(0);
  });

  it("a fight in the middle of the map is neither", () => {
    const w = setup();
    const a = tc(w, P), b = tc(w, E);
    fightAt(w, P, (a.x + b.x) / 2, (a.y + b.y) / 2);
    for (const t of [P, E]) {
      const s = w.player(t).stats;
      expect(s.firstAttackAt).toBe(-1);
      expect(s.firstDefendAt).toBe(-1);
      expect(s.firstFieldAt).toBeGreaterThanOrEqual(0);
    }
  });

  it("kills are split the same way, and only an attack counts as a rush", () => {
    const w = setup();
    const home = tc(w, E);
    for (let i = 0; i < 4; i++) {
      const v = w.spawnUnit(E, "villager", home.x + 60 + i * 10, home.y + 90);
      v.hp = 1;
      const a = w.spawnUnit(P, "archer", home.x + 60 + i * 10, home.y + 170);
      w.issueAttack([a.id], v.id);
    }
    run(w, 6);
    const s = w.player(P).stats;
    expect(s.killsAttacking).toBeGreaterThanOrEqual(4);
    expect(s.killsDefending).toBe(0);
    expect(s.openerByUnit.archer, "the opener is what did the damage").toBeGreaterThan(0);
    const cm = careerMatch(matchReport(w, P, "Test"), { at: 0, won: true, kind: "skirmish", mode: "conquest", difficulty: "knight", commander: "" } as any);
    expect(cm.firstAttackAt).toBeGreaterThanOrEqual(0);
    expect(cm.opener?.archer).toBeGreaterThan(0);
    expect(matchStyles(cm)).toContain("rush");
    // And the defender, who only fought at home, did not rush.
    const theirs = careerMatch(matchReport(w, E, "Test"), { at: 0, won: false, kind: "skirmish", mode: "conquest", difficulty: "knight", commander: "" } as any);
    expect(matchStyles(theirs)).not.toContain("rush");
  });

  it("a hit on the enemy is dated when it lands", () => {
    const w = setup();
    run(w, 30);
    const home = tc(w, E);
    fightAt(w, P, home.x, home.y + 120);
    expect(w.player(P).stats.firstAttackAt).toBeGreaterThanOrEqual(30);
    expect(w.player(P).stats.firstAttackAt).toBeLessThan(34);
    expect(w.player(P).stats.firstHitAt).toBe(w.player(P).stats.firstAttackAt);
  });

  it("a razed building and a kill are credited to the unit that did it", () => {
    const w = setup();
    const home = tc(w, E);
    const house = w.entities.find((e) => e.alive && e.team === E && e.kind === Kind.Building && e.type !== "town_center")
      ?? w.placeBuilding(E, "house", home.x + 150, home.y)!;
    house.hp = 5;
    const a = w.spawnUnit(P, "militia", house.x, house.y + 60);
    w.issueAttack([a.id], house.id);
    run(w, 8);
    const s = w.player(P).stats;
    expect(house.alive).toBe(false);
    expect(s.buildingsRazed).toBe(1);
    expect(s.razedByUnit.militia).toBe(1);
    expect(s.firstRazeAt).toBeGreaterThan(0);
  });
});
