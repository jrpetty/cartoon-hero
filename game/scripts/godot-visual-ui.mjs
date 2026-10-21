// UI widgets for the Godot port's visual comparison: panels, text, every
// button state, bars, a slider, a scrollbar, a tooltip and the menu icons,
// through the real src/ui/ui.ts and menu_icons.ts. The same calls are made by
// godot/tests/visual/ui_sheet.gd.
//
//   node scripts/godot-visual-ui.mjs ../godot/tests/visual/out/ts_ui.png
import { build } from "esbuild";
import { createCanvas } from "@napi-rs/canvas";
import { writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { aliasFonts } from "./godot-visual-fonts.mjs";

aliasFonts();
const out = process.argv[2] ?? "../godot/tests/visual/out/ts_ui.png";
const tmp = mkdtempSync(join(tmpdir(), "gvu-"));
const outfile = join(tmp, "ui.cjs");
await build({
  stdin: {
    contents: `export { ui } from "./src/ui/ui"; export * as icons from "./src/ui/menu_icons"; export { PAL } from "./src/render/palette";`,
    resolveDir: process.cwd(), loader: "ts",
  },
  bundle: true, format: "cjs", platform: "node", outfile, logLevel: "error",
});
const { ui, icons, PAL } = await import(pathToFileURL(outfile).href);

const W = 760, H = 540;
const canvas = createCanvas(W, H);
const ctx = canvas.getContext("2d");
const bg = ctx.createLinearGradient(0, 0, 0, H);
bg.addColorStop(0, "#3b5a2c"); bg.addColorStop(1, "#22331a");
ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H);

const input = (mx, my, extra = {}) => ({ mx, my, clicked: false, rightClicked: false, ...extra });
ui.begin(ctx, input(-100, -100));

// Panels with text.
ui.panel(16, 16, 350, 210);
ui.panel(382, 16, 362, 210, { light: true });
ui.text("Banner & Blade", 32, 40, { size: 20, bold: true, color: PAL.uiAccent });
ui.text("Parchment body text, 14px", 32, 70);
ui.text("Small grey note at 12px", 32, 92, { size: 12, color: "#b8a888" });
ui.text("Centred in the panel", 191, 118, { align: "center", size: 13 });
ui.text("right-aligned", 350, 140, { align: "right", size: 13, color: PAL.uiGood });
ui.text("Georgia, bold 17px", 32, 168, { size: 17, bold: true, font: "Georgia, serif" });
ui.text("12.5px fractional size", 32, 196, { size: 12.5, color: PAL.uiBad });

// Every button state.
const bw = 160, bh = 34;
ui.begin(ctx, input(-100, -100));
ui.button("Normal", 398, 32, bw, bh);
ui.begin(ctx, input(398 + 80, 90));
ui.button("Hover", 398, 74, bw, bh);
ui.begin(ctx, input(398 + 80, 132, { leftHeld: true }));
ui.button("Pressed", 398, 116, bw, bh);
ui.begin(ctx, input(-100, -100));
ui.button("Accent", 570, 32, bw, bh, { accent: true });
ui.button("Danger", 570, 74, bw, bh, { danger: true });
ui.button("Disabled", 570, 116, bw, bh, { disabled: true });
ui.button("Queue", 398, 166, bw, bh, { badge: "3", size: 13 });
ui.begin(ctx, input(570 + 80, 182));
ui.button("Danger hover", 570, 166, bw, bh, { danger: true, size: 12.5 });

// Bars, slider, scrollbar.
ui.begin(ctx, input(-100, -100));
ui.panel(16, 242, 350, 150);
ui.bar(32, 262, 300, 10, 0.2, PAL.uiBad);
ui.bar(32, 282, 300, 10, 0.62, "#e8c33a");
ui.bar(32, 302, 300, 10, 1.0, PAL.uiGood);
ui.bar(32, 322, 300, 6, 0.005, PAL.uiAccent);
ui.slider(40, 352, 280, 0.35, false);
ui.scrollbar(346, 258, 120, 60, 400);

// A scrolled, clipped block of text.
ui.panel(382, 242, 362, 150, { light: true });
ui.pushScroll(30, { x: 398, y: 256, w: 330, h: 120 });
for (let i = 0; i < 8; i++) ui.text(`Scrolled line ${i + 1} — clipped to the box`, 410, 262 + i * 20, { size: 13 });
ui.popScroll();

// A hovered button with a tooltip, drawn last.
ui.begin(ctx, input(120, 430));
ui.button("Train Knight", 30, 412, 170, 36, { accent: true, tooltip: ["Knight", "Cost: 60 food, 75 gold", "Strong against archers"] });
ui.flushTooltip(W, H);

// The menu icons.
ui.begin(ctx, input(-100, -100));
const row = [
  (x, y) => icons.iconSkirmish(ctx, x, y, 56, "#b8483e"),
  (x, y) => icons.iconMultiplayer(ctx, x, y, 56, 0.7),
  (x, y) => icons.iconWarband(ctx, x, y, 56),
  (x, y) => icons.iconArmory(ctx, x, y, 56),
  (x, y) => icons.iconMap(ctx, x, y, 56),
  (x, y) => icons.iconCodex(ctx, x, y, 56),
  (x, y) => icons.iconSettings(ctx, x, y, 56),
  (x, y) => icons.iconResume(ctx, x, y, 56),
  (x, y) => icons.iconFactions(ctx, x, y, 56),
  (x, y) => icons.iconCareer(ctx, x, y, 56),
  (x, y) => icons.iconNemesis(ctx, x, y, 56),
];
row.forEach((f, i) => f(270 + (i % 6) * 80, 432 + Math.floor(i / 6) * 66));
writeFileSync(out, canvas.toBuffer("image/png"));
console.log("wrote", out);
