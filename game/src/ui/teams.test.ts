import { describe, expect, it } from "vitest";
import { alliancesFor, blockTeams, coopTeams, formatLabel, freeForAll, resizeTeams, sides, teamsValid } from "./teams";

/**
 * Teams used to be one switch that split seats by parity — 1, 3, 5, 7 against
 * 2, 4, 6, 8 — so nobody could tell why they were on the side they were on.
 * Now every seat picks its team, and presets fill them in organised blocks.
 */
describe("Team layouts", () => {
  it("fills presets in contiguous blocks, larger teams first", () => {
    expect(blockTeams(8, 2)).toEqual([1, 1, 1, 1, 2, 2, 2, 2]);
    expect(blockTeams(8, 4)).toEqual([1, 1, 2, 2, 3, 3, 4, 4]);
    expect(blockTeams(8, 3)).toEqual([1, 1, 1, 2, 2, 2, 3, 3]);
    expect(blockTeams(6, 2)).toEqual([1, 1, 1, 2, 2, 2]);
    expect(coopTeams(6, 2)).toEqual([1, 1, 1, 2, 2, 2]);
  });

  it("turns teams into alliances, solos each on their own", () => {
    const a = alliancesFor([1, 1, 0, 2, 0, 2]);
    expect(a[0]).toBe(a[1]);
    expect(a[3]).toBe(a[5]);
    expect(new Set([a[0], a[2], a[3], a[4]]).size).toBe(4);
  });

  it("names the format", () => {
    expect(formatLabel(blockTeams(8, 2))).toBe("4 v 4");
    expect(formatLabel(blockTeams(8, 4))).toBe("2 v 2 v 2 v 2");
    expect(formatLabel([1, 1, 1, 0, 0])).toBe("3 v 1 v 1");
    expect(formatLabel(freeForAll(4))).toBe("4-player free-for-all");
    expect(formatLabel(freeForAll(2))).toBe("1 v 1");
    // Seven teams of one and a pair — as many teams as you like.
    expect(sides([1, 2, 3, 4, 5, 6, 7, 7])).toHaveLength(7);
  });

  it("refuses a match with only one side", () => {
    expect(teamsValid([1, 1, 1, 1])).toBe(false);
    expect(teamsValid([1, 1, 1, 0])).toBe(true);
    expect(teamsValid(freeForAll(2))).toBe(true);
  });

  it("resizes without losing seats' choices", () => {
    expect(resizeTeams([1, 2, 2], 5)).toEqual([1, 2, 2, 0, 0]);
    expect(resizeTeams([1, 2, 2, 1], 2)).toEqual([1, 2]);
  });
});
