// The TS half of the Godot port's in-match comparison: drives the real game
// (src/main.ts, in Chromium at 1920×1080) through exactly the scenario
// godot/tests/shots/scenarios.gd's shots() plays — same config and seed, no
// AI, the same scripted orders on the same ticks, the same camera — and saves
// a PNG per step as ts_NN_*.png.
//
//   node scripts/godot-shots.mjs ../godot/tests/shots
//
// The App isn't exported, so this serves a temporary copy of main.ts that
// keeps a handle on it (window.__app), and removes it afterwards.
import { readFileSync, writeFileSync, rmSync, mkdirSync } from "node:fs";
import { resolve } from "node:path";
import { createServer } from "vite";
import { chromium } from "/opt/node22/lib/node_modules/playwright/index.mjs";

const out = resolve(process.argv[2] ?? "../godot/tests/shots");
mkdirSync(out, { recursive: true });
const main = readFileSync("src/main.ts", "utf8").replace(/\nnew App\(\);\s*$/, "\n(window as any).__app = new App();\n");
writeFileSync("src/__shots_main.ts", main);
writeFileSync("__shots.html", readFileSync("index.html", "utf8").replace("/src/main.ts", "/src/__shots_main.ts"));

const server = await createServer({ configFile: "vite.config.ts", server: { port: 5199, host: "127.0.0.1" }, logLevel: "error" });
await server.listen();
const browser = await chromium.launch({ args: ["--use-gl=swiftshader", "--enable-unsafe-swiftshader"] });
try {
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  page.on("pageerror", (e) => console.error("page error:", e.message));
  await page.addInitScript(() => { try { localStorage.clear(); } catch { /* */ } });
  await page.goto("http://127.0.0.1:5199/__shots.html");
  await page.waitForFunction(() => !!window.__app);
  await page.mouse.move(960, 540); // the pointer starts at 0,0, which edge-scrolls
  // Software rendering is slow enough to trip the adaptive detail; keep it at
  // full, as the Godot harness does.
  await page.evaluate(() => { window.__app.settings.autoLod = false; });
  const frames = (n) => page.evaluate((n) => new Promise((r) => { let k = 0; const f = () => (++k >= n ? r() : requestAnimationFrame(f)); requestAnimationFrame(f); }), n);
  const shot = async (name) => { await frames(2); await page.screenshot({ path: `${out}/${name}.png` }); console.log("shot", name); };
  const run = (fn, arg) => page.evaluate(fn, arg);

  // The fixed match (scenarios.gd baseConfig).
  const baseConfig = (players = 2) => {
    const facs = ["legion", "norse", "khanate", "shogunate", "ascendancy", "kingdom", "legion"];
    return {
      presetId: "open_plains", seed: 4242, difficulty: "knight", aiDifficulties: [], fairMode: true,
      players, teams: Array.from({ length: players }, () => 0), commander: "drillmaster", faction: "kingdom",
      aiFactions: ["", ...Array.from({ length: players - 1 }, (_, i) => facs[i % facs.length])], nomad: false, mode: "conquest",
    };
  };
  await run(() => { const a = window.__app; a.ticksFor = (n) => { for (let i = 0; i < n; i++) a.world.tick(); }; });
  const startPaused = (cfg) => run((cfg) => { const a = window.__app; a.startMatch(cfg); a.ais = []; a.paused = true; a.camera.zoom = 1; }, cfg);
  const ticks = (n) => run((n) => window.__app.ticksFor(n), n);

  // 1. The opening.
  await startPaused(baseConfig());
  await ticks(40);
  await run(() => { const a = window.__app; const s = a.world.map.starts[0]; a.camera.centerOn(s.x, s.y); });
  await frames(30);
  await shot("ts_01_start");

  // 2. Selection + HUD.
  await run(() => { const a = window.__app; const tc = a.world.entities.find((e) => e.alive && e.team === 0 && e.type === "town_center"); a.select([tc.id]); });
  await frames(10);
  await shot("ts_02_select_tc");
  await run(() => { const a = window.__app; a.select(a.world.entitiesOf(0, 0).map((e) => e.id)); });
  await frames(10);
  await shot("ts_03_select_villagers");

  // 3. A battle in the fog (scenarios.gd battle()).
  await run(() => {
    const a = window.__app; const w = a.world;
    a.select([]);
    const mix = [["militia", 6], ["spearman", 5], ["archer", 6], ["knight", 4], ["crossbow", 4]];
    const A = w.map.starts[0], B = w.map.starts[1];
    const mx = (A.x + B.x) / 2, my = (A.y + B.y) / 2;
    const l = Math.hypot(A.x - mx, A.y - my);
    const nx = (A.x - mx) / l, ny = (A.y - my) / l;
    for (let t = 0; t < 2; t++) {
      const s = t === 0 ? 1 : -1;
      const bx = mx + nx * 260 * s, by = my + ny * 260 * s;
      let k = 0;
      for (const [type, n] of mix) for (let j = 0; j < n; j++) { w.spawnUnit(t, type, bx + (k % 6) * 22 - 55, by + Math.floor(k / 6) * 22 - 50); k++; }
    }
    for (let t = 0; t < 2; t++) {
      const ids = w.entitiesOf(t, 0).filter((e) => e.type !== "villager").map((e) => e.id);
      w.issueFormationMove(ids, mx, my, false, true);
    }
  });
  await ticks(170);
  await run(() => { const a = window.__app; const w = a.world; a.camera.centerOn((w.map.starts[0].x + w.map.starts[1].x) / 2, (w.map.starts[0].y + w.map.starts[1].y) / 2); });
  await frames(20);
  await shot("ts_04_battle_fog");

  // 4. Production overview + scoreboard.
  await run(() => {
    const a = window.__app; const w = a.world; const st = w.map.starts[0];
    w.player(0).resources = { food: 5000, wood: 5000, gold: 5000 };
    const tc = w.entities.find((e) => e.alive && e.team === 0 && e.type === "town_center");
    const bar = w.spawnBuilding(0, "barracks", st.x + 200, st.y - 40, true);
    for (let i = 0; i < 3; i++) { w.trainUnit(0, tc.id, "villager"); w.trainUnit(0, bar.id, "militia"); }
  });
  await ticks(30);
  await run(() => { const a = window.__app; a.showProduction = true; a.showScoreboard = true; });
  await frames(10);
  await shot("ts_05_production_scoreboard");
  await run(() => { const a = window.__app; a.showProduction = false; a.showScoreboard = false; });

  // 5. The Oath picker.
  await run(() => {
    const a = window.__app; const w = a.world; const st = w.map.starts[0];
    w.spawnBuilding(0, "mill", st.x - 200, st.y + 60, true);
    a.camera.centerOn(st.x, st.y);
    a.hud.oathPicker.open(w.entities.find((e) => e.alive && e.team === 0 && e.type === "town_center"));
  });
  await frames(10);
  await shot("ts_06_oath_picker");
  await run(() => window.__app.hud.oathPicker.close());

  // 6. Night at the base (DAY_CYCLE = 200).
  await run(() => { const a = window.__app; const w = a.world; w.time = Math.floor(w.time / 200) * 200 + 200 * 0.74; });
  await ticks(10);
  await run(() => { const a = window.__app; const s = a.world.map.starts[0]; a.camera.centerOn(s.x, s.y); });
  await frames(15);
  await shot("ts_07_night");

  // 7. Weather (rain) by day.
  await run(() => { const a = window.__app; const w = a.world; w.time = Math.floor(w.time / 200) * 200 + 200 * 1.25; });
  await ticks(10);
  await run(() => { const a = window.__app; const w = a.world; a.weather.type = "rain"; a.camera.centerOn((w.map.starts[0].x + w.map.starts[1].x) / 2, (w.map.starts[0].y + w.map.starts[1].y) / 2); });
  await frames(20);
  await shot("ts_08_rain");
  await run(() => { window.__app.weather.type = "clear"; });

  // 8. The caster view over a 4-realm watch game.
  await run((cfg) => { const a = window.__app; a.startSpectate(cfg); a.ais = []; a.paused = true; }, baseConfig(4));
  await ticks(40);
  await frames(20);
  await shot("ts_09_caster");

  // 9. Post-match.
  await startPaused(baseConfig());
  await ticks(200);
  await run(() => window.__app.finishMatch(true));
  await frames(20);
  await shot("ts_10_postmatch");
} finally {
  await browser.close();
  await server.close();
  rmSync("src/__shots_main.ts", { force: true });
  rmSync("__shots.html", { force: true });
}
