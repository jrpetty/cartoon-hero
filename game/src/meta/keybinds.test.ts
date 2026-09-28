import { describe, expect, it } from "vitest";
import {
  ACTIONS, KeybindResolver, chordFor, chordLabel, chordOf, conflictsOf, isReserved, keyName,
} from "./keybinds";

describe("Chords", () => {
  it("folds a key to one canonical name whatever case it arrives in", () => {
    expect(keyName("a")).toBe("A");
    expect(keyName("A")).toBe("A");
    expect(chordOf("a")).toBe(chordOf("A"));
    // Named keys keep their DOM spelling.
    expect(keyName("Tab")).toBe("Tab");
    expect(keyName("ArrowUp")).toBe("ArrowUp");
    expect(keyName("F10")).toBe("F10");
  });

  it("gives Space and the shifted speed keys a stable name", () => {
    // Otherwise "Speed up" would only fire on a keyboard where + is unshifted.
    expect(keyName(" ")).toBe("Space");
    expect(keyName("=")).toBe("+");
    expect(keyName("_")).toBe("-");
  });

  it("orders modifiers so the same combination is always the same string", () => {
    expect(chordOf("a", { ctrl: true })).toBe("Ctrl+A");
    expect(chordOf("a", { shift: true, ctrl: true })).toBe("Ctrl+Shift+A");
    expect(chordOf("1", { shift: true })).toBe("Shift+1");
  });

  it("is not a chord when only a modifier is down", () => {
    expect(chordOf("Control", { ctrl: true })).toBe("");
    expect(chordOf("Shift", { shift: true })).toBe("");
    expect(chordOf("Alt", { alt: true })).toBe("");
  });

  it("prints arrows and Delete in a form that fits a button", () => {
    expect(chordLabel("ArrowUp")).toBe("↑");
    expect(chordLabel("Delete")).toBe("Del");
    expect(chordLabel("")).toBe("—");
  });
});

describe("Bindings", () => {
  it("falls back to an action's default until it is overridden", () => {
    expect(chordFor({}, "attackMove")).toBe("A");
    expect(chordFor({ attackMove: "Ctrl+Q" }, "attackMove")).toBe("Ctrl+Q");
    // An explicit empty string is "unbound", not "use the default".
    expect(chordFor({ attackMove: "" }, "attackMove")).toBe("");
  });

  it("resolves a chord to its action, and rebuilds when a binding changes", () => {
    const r = new KeybindResolver();
    expect(r.resolve({}, "A")).toBe("attackMove");
    expect(r.resolve({}, "Ctrl+A")).toBe("selectArmy");
    expect(r.resolve({}, "Z")).toBeNull();
    // Rebind and ask again — the cached map has to notice.
    expect(r.resolve({ attackMove: "Z" }, "Z")).toBe("attackMove");
    expect(r.resolve({ attackMove: "Z" }, "A")).toBeNull();
  });

  it("keeps an unbound action from swallowing every unrecognised key", () => {
    const r = new KeybindResolver();
    expect(r.resolve({ stop: "" }, "")).toBeNull();
    expect(r.resolve({ stop: "" }, "S")).toBeNull();
  });

  it("honours a second default that isn't shown in the list", () => {
    const r = new KeybindResolver();
    expect(r.resolve({}, "F3")).toBe("pause");
    expect(r.resolve({}, "P")).toBe("pause"); // the alt
  });

  it("ships defaults with no conflicts of its own", () => {
    expect([...conflictsOf({}).keys()]).toEqual([]);
  });

  it("reports a conflict the player creates, naming both actions", () => {
    const c = conflictsOf({ stop: "A" }); // A is already Attack-move
    expect([...c.keys()]).toEqual(["A"]);
    expect(c.get("A")!.sort()).toEqual(["attackMove", "stop"]);
  });

  it("does not count an unbound action as conflicting with another", () => {
    expect(conflictsOf({ stop: "", hold: "" }).size).toBe(0);
  });

  it("refuses the chords the browser needs for itself", () => {
    expect(isReserved("F5")).toBe(true);
    expect(isReserved("Ctrl+W")).toBe(true);
    expect(isReserved("A")).toBe(false);
  });

  it("follows Age of Empires where the game has an equivalent", () => {
    // These are the ones players arrive already knowing; if one of them moves
    // it should be a decision, not a drift.
    const byId = new Map(ACTIONS.map((a) => [a.id, a.def]));
    expect(byId.get("idleVillagerNext")).toBe(".");
    expect(byId.get("idleVillagerPrev")).toBe(",");
    expect(byId.get("selectTownCentre")).toBe("H");
    expect(byId.get("cameraLastEvent")).toBe("Space");
    expect(byId.get("selectArmy")).toBe("Ctrl+A");
    expect(byId.get("deleteUnit")).toBe("Delete");
    expect(byId.get("pause")).toBe("F3");
  });

  it("never binds a pan direction to a key that also issues an order", () => {
    // Panning is held while commands are tapped, so sharing a key means every
    // pan also fires an order. WASD panning was broken exactly this way.
    const pans = ACTIONS.filter((a) => a.id.startsWith("pan")).map((a) => a.def);
    const commands = ACTIONS.filter((a) => a.group === "Units" || a.group === "Selection").map((a) => a.def);
    for (const p of pans) expect(commands, `${p} is also a command`).not.toContain(p);
  });
});

describe("Shifted symbols", () => {
  it("binds the symbol the browser reports, not the key you reached for", () => {
    // Shift+= reports "+" with shiftKey set. Counting the Shift as well gave
    // "Shift++", which matched nothing, so Speed up silently stopped working
    // on every keyboard where + is a shifted key — that is, most of them.
    expect(chordOf("+", { shift: true })).toBe("+");
    expect(chordOf("=", { shift: true })).toBe("+");
    expect(chordOf(".", { shift: true })).toBe(".");
    const r = new KeybindResolver();
    expect(r.resolve({}, chordOf("+", { shift: true }))).toBe("speedUp");
    expect(r.resolve({}, chordOf("=", {}))).toBe("speedUp");
  });

  it("keeps Shift meaningful on letters and digits", () => {
    expect(chordOf("h", { shift: true })).toBe("Shift+H");
    expect(chordOf("1", { shift: true })).toBe("Shift+1");
    const r = new KeybindResolver();
    expect(r.resolve({}, "Shift+H")).toBe("hold");
    expect(r.resolve({}, "H")).toBe("selectTownCentre");
  });
});
