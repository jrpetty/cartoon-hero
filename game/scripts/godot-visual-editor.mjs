// The map editor as the TS draws it, for the Godot port's visual comparison:
// the real src/ui/editor_screen.ts on @napi-rs/canvas, in each state listed
// in godot/tests/visual/lobby_editor/editor_scenes.json — the same states
// godot/tests/visual/lobby_editor/editor_sheet.gd draws in Godot.
//
//   node scripts/godot-visual-editor.mjs <out dir> [scene ...]
//
// Writes ts_editor_<scene>.png.
import { build } from "esbuild";
import { createCanvas } from "@napi-rs/canvas";
import { writeFileSync, readFileSync, mkdtempSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { aliasFonts } from "./godot-visual-fonts.mjs";

aliasFonts();
const outDir = resolve(process.argv[2] ?? "../godot/tests/visual/lobby_editor/out");
const only = process.argv.slice(3);
mkdirSync(outDir, { recursive: true });
const spec = JSON.parse(readFileSync(resolve("../godot/tests/visual/lobby_editor/editor_scenes.json"), "utf8"));

const store = {};
globalThis.localStorage = {
  getItem: (k) => store[k] ?? null, setItem: (k, v) => { store[k] = String(v); }, removeItem: (k) => { delete store[k]; },
  clear: () => { for (const k of Object.keys(store)) delete store[k]; },
};

const tmp = mkdtempSync(join(tmpdir(), "gve-"));
const outfile = join(tmp, "editor.cjs");
await build({
  stdin: {
    contents: `export { ui } from "./src/ui/ui"; export { EditorScreen } from "./src/ui/editor_screen";
      export { customFromGenerated, newCustomMap, publishCustomMap, saveCustomMap } from "./src/maps/custom";`,
    resolveDir: process.cwd(), loader: "ts",
  },
  bundle: true, format: "cjs", platform: "node", outfile, logLevel: "error",
});
const { ui, EditorScreen, customFromGenerated, newCustomMap, publishCustomMap, saveCustomMap } = await import(pathToFileURL(outfile).href);

const W = 1600, H = 900;
for (const scene of spec.scenes) {
  if (only.length && !only.includes(scene.name)) continue;
  localStorage.clear();
  for (const m of spec.maps) {
    const cm = customFromGenerated(m.preset, m.seed, m.players, m.name);
    cm.desc = m.desc;
    if (m.publish) publishCustomMap(cm, m.author); else saveCustomMap(cm);
  }
  const s = new EditorScreen();
  s.author = "Aldric";
  if (scene.open) {
    const o = scene.open;
    const m = o.blank ? newCustomMap(o.name, o.blank, o.biome) : customFromGenerated(o.preset, o.seed, o.players, o.name);
    if (o.publish) publishCustomMap(m, "Aldric");
    s.open(m);
    if (scene.nomad) m.nomad = scene.nomad;
  }
  Object.assign(s, scene.set ?? {});
  if (scene.copy) s.copyRegion(...scene.copy);
  const canvas = createCanvas(W, H);
  const ctx = canvas.getContext("2d");
  let t = 0.25;
  for (let f = 0; f < (scene.frames ?? 1); f++) {
    ctx.clearRect(0, 0, W, H);
    ui.begin(ctx, { mx: scene.mouse[0], my: scene.mouse[1], clicked: false, rightClicked: false, alt: false, leftHeld: false, rightHeld: false, ctrlHeld: false, shiftHeld: false, wheel: 0 });
    s.draw(W, H, t, false);
    ui.flushTooltip(W, H);
  }
  const file = join(outDir, `ts_editor_${scene.name}.png`);
  writeFileSync(file, canvas.toBuffer("image/png"));
  console.log("wrote", file);
}
