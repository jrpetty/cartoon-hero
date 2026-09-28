import { describe, expect, it } from "vitest";
import { createCanvas } from "@napi-rs/canvas";
(globalThis as unknown as { document: unknown }).document = { createElement: () => createCanvas(1, 1) };
import { ui } from "./ui";
import { OathPicker } from "./oath_picker";
import { World } from "../sim/world";
import { Kind, Team } from "../sim/types";
import { generateMap } from "../maps/generator";
import { oathsForAge } from "../content/oaths";

/**
 * Advancing an age opens a choice of three Oaths instead of researching
 * outright. The picker is the only way a player swears one, so it has to
 * work: three cards, the right research id on a click, and out of the way
 * once the choice is made.
 */

const W = 1280, H = 760;

function setup() {
  const w = new World(3);
  w.init(generateMap("open_plains", 3, 2), [{}, {}], [1, 1]);
  const p = w.player(Team.Player);
  p.resources = { food: 9999, wood: 9999, gold: 9999 };
  const tc = w.entities.find((e) => e.alive && e.team === Team.Player && e.type === "town_center")!;
  const start = w.map.starts[Team.Player];
  for (const t of ["barracks", "mill"]) {
    let placed = false;
    for (let r = 160; r < 600 && !placed; r += 40) {
      for (let a = 0; a < 12 && !placed; a++) {
        const b = w.placeBuilding(Team.Player, t, start.x + Math.cos(a) * r, start.y + Math.sin(a) * r);
        if (b) { b.buildState = 0; placed = true; }
      }
    }
  }
  return { w, tc };
}

/** Draw one frame, recording every button's label and rect. */
function frame(pk: OathPicker, w: World, tc: ReturnType<typeof setup>["tc"], click: { x: number; y: number } | null, swear: (id: string) => void) {
  const canvas = createCanvas(W, H);
  ui.begin(canvas.getContext("2d") as unknown as CanvasRenderingContext2D, {
    mx: click?.x ?? 0, my: click?.y ?? 0, clicked: !!click, rightClicked: false, alt: false,
  });
  const buttons: { label: string; x: number; y: number; w: number; h: number }[] = [];
  const orig = ui.button.bind(ui);
  ui.button = ((label: string, x: number, y: number, bw: number, bh: number, o?: Parameters<typeof ui.button>[5]) => {
    buttons.push({ label, x, y, w: bw, h: bh });
    return orig(label, x, y, bw, bh, o);
  }) as typeof ui.button;
  try { pk.draw(W, H, w, Team.Player, 0, (_b, id) => swear(id)); } finally { ui.button = orig; }
  void tc;
  return buttons;
}

describe("The Oath picker", () => {
  it("offers the three Oaths of the next age and swears the one clicked", () => {
    const { w, tc } = setup();
    const pk = new OathPicker();
    pk.open(tc);
    const buttons = frame(pk, w, tc, null, () => {});
    const swearButtons = buttons.filter((b) => b.label === "Swear this Oath");
    expect(swearButtons).toHaveLength(3);
    const sworn: string[] = [];
    const second = swearButtons[1];
    frame(pk, w, tc, { x: second.x + second.w / 2, y: second.y + second.h / 2 }, (id) => sworn.push(id));
    expect(sworn).toEqual([`age:${oathsForAge(1)[1].id}`]);
    expect(pk.isOpen, "stayed open after the choice").toBe(false);
  });

  it("closes itself once an advance is under way", () => {
    const { w, tc } = setup();
    const pk = new OathPicker();
    pk.open(tc);
    w.research(Team.Player, tc.id, "age:plough");
    frame(pk, w, tc, null, () => {});
    expect(pk.isOpen).toBe(false);
  });

  it("won't swear what the realm can't pay for, and says why", () => {
    const { w, tc } = setup();
    w.player(Team.Player).resources = { food: 0, wood: 0, gold: 0 };
    const pk = new OathPicker();
    pk.open(tc);
    const buttons = frame(pk, w, tc, null, () => {});
    expect(buttons.filter((b) => b.label === "Not enough resources")).toHaveLength(3);
    const first = buttons.find((b) => b.label === "Not enough resources")!;
    const sworn: string[] = [];
    frame(pk, w, tc, { x: first.x + first.w / 2, y: first.y + first.h / 2 }, (id) => sworn.push(id));
    expect(sworn).toEqual([]);
    expect(w.entities.some((e) => e.kind === Kind.Building && e.productionQueue.includes("a:age"))).toBe(false);
  });
});
