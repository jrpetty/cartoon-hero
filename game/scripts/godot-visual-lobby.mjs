// The online lobby as the browser draws it, for the Godot port's visual
// comparison. The lobby is a DOM overlay, so this renders the real
// src/ui/net_lobby.ts in Chromium (Playwright): each scene in
// godot/tests/visual/lobby_editor/lobby_scenes.json opens a NetLobby, gives it
// a fake socket and feeds it server messages through its own onMessage — the
// same thing godot/tests/visual/lobby_editor/lobby_sheet.gd does in Godot.
//
//   node scripts/godot-visual-lobby.mjs <out dir> [scene ...]
//
// Writes ts_lobby_<scene>.png (and ts_lobby_<scene>.json: the boxes of the
// panel's elements, for lining the two up). Fonts: like the other sheets,
// the families the page asks for are pointed at the files the Godot port
// falls back to (Liberation Sans / Serif / Mono), via a fontconfig file.
import { build } from "esbuild";
import { createRequire } from "node:module";
import { writeFileSync, readFileSync, mkdtempSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";

const require = createRequire(import.meta.url);
const { chromium } = require("/opt/node22/lib/node_modules/playwright");

const outDir = resolve(process.argv[2] ?? "../godot/tests/visual/lobby_editor/out");
const only = process.argv.slice(3);
mkdirSync(outDir, { recursive: true });
const spec = JSON.parse(readFileSync(resolve("../godot/tests/visual/lobby_editor/lobby_scenes.json"), "utf8"));
const W = 1100, H = 900;

const tmp = mkdtempSync(join(tmpdir(), "gvl-"));
const fontsConf = join(tmp, "fonts.conf");
writeFileSync(fontsConf, `<?xml version="1.0"?><!DOCTYPE fontconfig SYSTEM "fonts.dtd"><fontconfig>
  <include ignore_missing="yes">/etc/fonts/fonts.conf</include>
  ${[["sans-serif", "Liberation Sans"], ["system-ui", "Liberation Sans"], ["Trebuchet MS", "Liberation Sans"],
    ["serif", "Liberation Serif"], ["Georgia", "Liberation Serif"], ["monospace", "Liberation Mono"], ["ui-monospace", "Liberation Mono"]]
    .map(([a, b]) => `<match target="pattern"><test name="family"><string>${a}</string></test><edit name="family" mode="prepend" binding="strong"><string>${b}</string></edit></match>`).join("\n  ")}
</fontconfig>`);

const bundle = join(tmp, "lobby.js");
await build({
  stdin: {
    contents: `import { NetLobby } from "./src/ui/net_lobby";
      import { customFromGenerated, publishCustomMap } from "./src/maps/custom";
      window.BB = { NetLobby, customFromGenerated, publishCustomMap };`,
    resolveDir: process.cwd(), loader: "ts",
  },
  bundle: true, format: "iife", platform: "browser", outfile: bundle, logLevel: "error",
});
const html = join(tmp, "index.html");
writeFileSync(html, `<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;background:#22301c;width:${W}px;height:${H}px;overflow:hidden}</style></head>
<body><script src="lobby.js"></script></body></html>`);

const browser = await chromium.launch({ env: { ...process.env, FONTCONFIG_FILE: fontsConf } });
const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
await page.goto("file://" + html);

for (const scene of spec.scenes) {
  if (only.length && !only.includes(scene.name)) continue;
  const boxes = await page.evaluate(({ spec, scene }) => {
    localStorage.clear();
    for (const [k, v] of Object.entries(spec.storage)) localStorage.setItem(k, v);
    // The player's own published maps, for the battlefield picker.
    for (const m of spec.maps) {
      const cm = window.BB.customFromGenerated(m.preset, m.seed, m.players, m.name);
      cm.desc = m.desc;
      window.BB.publishCustomMap(cm, m.author);
    }
    document.body.querySelectorAll("div").forEach((d) => d.remove());
    const lobby = new window.BB.NetLobby();
    lobby.open(() => {}, spec.opts);
    const fake = { send() {}, close() {}, connected: true };
    lobby.ws = fake;
    if (scene.fromHub) { lobby.fromHub = true; lobby.serverUrl = scene.serverUrl; lobby.view = "hub"; }
    for (const m of scene.messages) lobby.onMessage(fake, m, () => {});
    if (scene.set) {
      Object.assign(lobby, scene.set);
      if (lobby.view === "hub") lobby.renderHub(); else if (lobby.view === "room") lobby.render();
    }
    // The boxes of everything visible in the panel, for lining the two up.
    const panel = document.querySelector("#mp-panel");
    const out = [];
    for (const el of panel.querySelectorAll("button,input,textarea,div,b,span")) {
      const r = el.getBoundingClientRect();
      if (!r.width || !r.height) continue;
      out.push({ tag: el.tagName.toLowerCase(), text: (el.textContent || el.value || el.placeholder || "").slice(0, 40), x: r.x, y: r.y, w: r.width, h: r.height });
    }
    const pr = panel.getBoundingClientRect();
    return { panel: { x: pr.x, y: pr.y, w: pr.width, h: pr.height, scrollH: panel.scrollHeight }, boxes: out };
  }, { spec, scene });
  const file = join(outDir, `ts_lobby_${scene.name}.png`);
  await page.screenshot({ path: file });
  writeFileSync(join(outDir, `ts_lobby_${scene.name}.json`), JSON.stringify(boxes, null, 1));
  console.log("wrote", file);
}
await browser.close();
