import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team, Kind, OrderKind, Stance } from "./types";
import { generateMap } from "../maps/generator";
import { applyCommand } from "./commands";
import { SIM_HZ } from "../content/balance";

function mk(): World {
  const w = new World(1);
  w.init(generateMap("open_plains", 1, 2), [{}, {}], [1, 1], [0, 1]); // teams hostile
  return w;
}
const run = (w: World, secs: number) => { for (let i = 0; i < SIM_HZ * secs; i++) w.tick(); };

describe("Unit stances", () => {
  it("defaults to Defensive", () => {
    const w = mk();
    expect(w.spawnUnit(Team.Player, "militia", 900, 900).stance).toBe(Stance.Defensive);
  });

  it("Passive holds fire — never auto-attacks an adjacent enemy", () => {
    const w = mk();
    const me = w.spawnUnit(Team.Player, "militia", 1000, 1000);
    w.setStance([me.id], Stance.Passive);
    const foe = w.spawnUnit(Team.Enemy, "militia", 1014, 1000);
    const foeHp = foe.hp;
    run(w, 4);
    expect(foe.hp).toBe(foeHp); // we never struck it
    expect(me.order.kind).not.toBe(OrderKind.Attack);
  });

  it("Stand Ground fires in range but never moves to chase", () => {
    const w = mk();
    const me = w.spawnUnit(Team.Player, "archer", 1000, 1000);
    w.setStance([me.id], Stance.StandGround);
    const sx = me.x;
    const sy = me.y;
    const foe = w.spawnUnit(Team.Enemy, "militia", 1100, 1000); // inside archer range
    const foeHp = foe.hp;
    run(w, 3);
    expect(foe.hp).toBeLessThan(foeHp); // shot it
    expect(Math.hypot(me.x - sx, me.y - sy)).toBeLessThan(10); // held position
  });

  it("Stand Ground ignores a target it can't reach (no chase)", () => {
    const w = mk();
    const me = w.spawnUnit(Team.Player, "militia", 1000, 1000); // melee, tiny range
    w.setStance([me.id], Stance.StandGround);
    const sx = me.x;
    const foe = w.spawnUnit(Team.Enemy, "militia", 1240, 1000); // far away
    foe.stance = Stance.Passive; // it won't come to us either
    run(w, 4);
    expect(Math.abs(me.x - sx)).toBeLessThan(10); // never advanced
  });

  it("Aggressive holds the ground it took; Defensive returns to post", () => {
    const fight = (stance: Stance): number => {
      const w = mk();
      const me = w.spawnUnit(Team.Player, "knight", 1000, 1000);
      me.stance = stance;
      const foe = w.spawnUnit(Team.Enemy, "militia", 1110, 1000);
      foe.stance = Stance.Passive; // stays put to be cut down, doesn't fight back
      run(w, 16);
      expect(foe.alive).toBe(false); // both stances kill it
      return me.x;
    };
    const defX = fight(Stance.Defensive);
    const aggX = fight(Stance.Aggressive);
    // Defensive marches back toward its post (~1000); Aggressive stays forward (~1110).
    expect(defX).toBeLessThan(aggX - 40);
  });

  it("stance command respects ownership", () => {
    const w = mk();
    const mine = w.spawnUnit(Team.Player, "militia", 1000, 1000);
    applyCommand(w, { t: "stance", team: Team.Player, ids: [mine.id], stance: Stance.Aggressive });
    expect(mine.stance).toBe(Stance.Aggressive);
    const enemy = w.spawnUnit(Team.Enemy, "militia", 1100, 1000);
    applyCommand(w, { t: "stance", team: Team.Player, ids: [enemy.id], stance: Stance.Passive });
    expect(enemy.stance).toBe(Stance.Defensive); // unchanged — not ours
  });

  it("Hold command also sets the Stand Ground stance", () => {
    const w = mk();
    const me = w.spawnUnit(Team.Player, "militia", 1000, 1000);
    w.issueHold([me.id]);
    expect(me.stance).toBe(Stance.StandGround);
  });
});
