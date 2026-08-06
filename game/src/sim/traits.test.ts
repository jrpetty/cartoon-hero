import { describe, expect, it } from "vitest";
import { activeTraits, applyBuff, traitsOf } from "./traits";
import { resolveBattle } from "./autobattle";

describe("Warband synergies", () => {
  it("activates a trait once enough distinct types are deployed", () => {
    expect(activeTraits(["militia"]).length).toBe(0); // 1 footman — not enough
    const two = activeTraits(["archer", "skirmisher"]);
    const marks = two.find((a) => a.trait.id === "marksmen")!;
    expect(marks).toBeTruthy();
    expect(marks.count).toBe(2);
    expect(marks.tier?.label).toContain("18%"); // first tier
  });

  it("escalates to a higher tier with more distinct types", () => {
    const four = activeTraits(["archer", "skirmisher", "crossbow", "handcannon"]);
    const marks = four.find((a) => a.trait.id === "marksmen")!;
    expect(marks.count).toBe(4);
    expect(marks.tier?.label).toContain("45%"); // tier 2
  });

  it("counts overlapping traits independently (a Spearman is Footman + Pike)", () => {
    expect(traitsOf("spearman").map((t) => t.id).sort()).toEqual(["footmen", "pikes"]);
    const act = activeTraits(["spearman", "pikeman"]); // 2 of each overlapping trait
    expect(act.find((a) => a.trait.id === "footmen")?.count).toBe(2);
    expect(act.find((a) => a.trait.id === "pikes")?.count).toBe(2);
  });

  it("applies flat and percent buffs correctly", () => {
    const u = { maxHp: 100, hp: 100, attack: 10, armor: 1, speed: 80 };
    applyBuff(u, { armor: 3, atkPct: 50, hpPct: 20, speedPct: 10 });
    expect(u.armor).toBe(4);
    expect(u.attack).toBe(15); // +50%
    expect(u.maxHp).toBe(120); // +20%
    expect(u.hp).toBe(120); // refilled
    expect(u.speed).toBe(88); // +10%
  });

  it("a synergized warband beats the same unit count with no synergy", () => {
    // A: four DISTINCT marksmen → Marksmen ×4 (+45% attack to all).
    const synced = [
      { type: "archer", count: 1 }, { type: "skirmisher", count: 1 },
      { type: "crossbow", count: 1 }, { type: "handcannon", count: 1 },
    ];
    // B: four archers — only 1 distinct marksman type, so NO synergy.
    const flat = [{ type: "archer", count: 4 }];
    let syncedWins = 0;
    for (const seed of [1, 2, 3, 4, 5, 6, 7]) {
      if (resolveBattle(synced, flat, seed).winner === "A") syncedWins++;
    }
    expect(syncedWins).toBeGreaterThanOrEqual(5); // the synergy + variety carries it
  });
});
