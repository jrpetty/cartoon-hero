import { describe, expect, it } from "vitest";
import { World } from "./world";
import { Team } from "./types";
import { generateMap } from "../maps/generator";
import { applyCommand } from "./commands";

// Backs the "double-click to select all stables, then queue across all of them"
// QoL feature: the controller dispatches one train command per selected building,
// so verify each same-type building queues independently via the command path.
describe("Mass production across multiple same-type buildings", () => {
  it("queues a unit into every selected stable", () => {
    const w = new World(1);
    w.init(generateMap("open_plains", 1, 2), [{}, {}], [1, 1]);
    const p = w.player(Team.Player);
    p.age = 2; // Castle — stable units unlocked
    p.resources.food = 9999; p.resources.wood = 9999; p.resources.gold = 9999;
    const unit = "horseman";

    const stables = [0, 1, 2].map((i) => w.spawnBuilding(Team.Player, "stable", 800 + i * 70, 800, true));
    for (const s of stables) applyCommand(w, { t: "train", team: Team.Player, buildingId: s.id, unit });

    for (const s of stables) {
      expect(s.productionQueue.filter((q) => q === `u:${unit}`).length).toBe(1);
    }
    const combined = stables.reduce((n, s) => n + s.productionQueue.length, 0);
    expect(combined).toBe(3);
  });

  it("won't queue into a building you don't own", () => {
    const w = new World(2);
    w.init(generateMap("open_plains", 2, 2), [{}, {}], [1, 1]);
    w.player(Team.Enemy).age = 2;
    const enemyStable = w.spawnBuilding(Team.Enemy, "stable", 1200, 1200, true);
    // Player tries to train in the enemy's stable — rejected by ownership.
    applyCommand(w, { t: "train", team: Team.Player, buildingId: enemyStable.id, unit: "horseman" });
    expect(enemyStable.productionQueue.length).toBe(0);
  });
});
