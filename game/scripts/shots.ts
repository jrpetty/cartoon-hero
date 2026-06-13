// Headless screenshot generator. Runs the game's own rendering code through a
// node canvas (no browser) to produce preview PNGs of units, buildings, a live
// battle, and the menus. Bundled with esbuild and run on node.

import { createCanvas, GlobalFonts } from "@napi-rs/canvas";
import { writeFileSync, mkdirSync } from "fs";

// --- Shim the few DOM bits our render/UI code expects -----------------------
(globalThis as any).document = {
  createElement: (tag: string) => {
    if (tag === "canvas") return createCanvas(8, 8);
    throw new Error("unsupported element " + tag);
  },
};

import { generateMap } from "../src/maps/generator";
import { World, FOG_VISIBLE } from "../src/sim/world";
import { Team, Kind, BuildState, OrderKind } from "../src/sim/types";
import { Renderer } from "../src/render/renderer";
import { Camera } from "../src/engine/camera";
import { Particles } from "../src/engine/particles";
import { drawUnit, drawBuilding, drawResource } from "../src/render/draw";
import { PAL, shade, withAlpha } from "../src/render/palette";
import { UNITS } from "../src/content/units";
import { BUILDINGS } from "../src/content/buildings";
import { RARITIES } from "../src/meta/rarity";
import { HUD } from "../src/ui/hud";
import { ui } from "../src/ui/ui";
import { MenuScreen, SetupScreen, ArmoryScreen, PostMatchScreen } from "../src/ui/screens";
import { CodexScreen } from "../src/ui/codex";
import { computeRewards } from "../src/meta/progression";
import { Profile } from "../src/meta/profile";

const OUT = new URL("../shots/", import.meta.url).pathname;
mkdirSync(OUT, { recursive: true });

function save(name: string, canvas: any) {
  writeFileSync(OUT + name, canvas.toBuffer("image/png"));
  console.log("wrote shots/" + name);
}

function makeCtx(w: number, h: number) {
  const c = createCanvas(w, h);
  const ctx = c.getContext("2d") as any;
  return { c, ctx };
}

function groundPlate(ctx: any, x: number, y: number, w: number, h: number) {
  const g = ctx.createLinearGradient(0, y, 0, y + h);
  g.addColorStop(0, shade(PAL.grass, 0.05));
  g.addColorStop(1, shade(PAL.grassDark, -0.05));
  ctx.fillStyle = g;
  ctx.fillRect(x, y, w, h);
  // soft vignette
  ctx.fillStyle = "rgba(0,0,0,0.18)";
  ctx.fillRect(x, y + h - 40, w, 40);
}

function label(ctx: any, text: string, x: number, y: number, sub?: string, color = "#f3e9d2") {
  ctx.textAlign = "center";
  ctx.font = "bold 15px sans-serif";
  ctx.fillStyle = "rgba(0,0,0,0.55)";
  ctx.fillText(text, x + 1, y + 1);
  ctx.fillStyle = color;
  ctx.fillText(text, x, y);
  if (sub) {
    ctx.font = "12px sans-serif";
    ctx.fillStyle = "#c8bfa6";
    ctx.fillText(sub, x, y + 17);
  }
  ctx.textAlign = "left";
}

// Build a throwaway world so we can mint properly-initialised entities.
function scratchWorld(): World {
  const map = generateMap("open_plains", 4242);
  const w = new World(4242);
  w.init(map, [{}, {}], [1, 1]);
  return w;
}

// ----------------------------------------------------------------- units --
function shotUnits() {
  const w = scratchWorld();
  const ids = Object.keys(UNITS);
  const cols = 5;
  const rows = Math.ceil(ids.length / cols);
  const cellW = 200;
  const cellH = 188;
  const W = cols * cellW;
  const H = rows * cellH + 64;
  const { c, ctx } = makeCtx(W, H);
  groundPlate(ctx, 0, 0, W, H);
  ctx.textAlign = "center";
  ctx.font = "bold 30px Georgia, serif";
  ctx.fillStyle = "#ffe9b0";
  ctx.fillText("Banner & Blade — The Host", W / 2, 40);
  ctx.textAlign = "left";

  ids.forEach((id, i) => {
    const def = UNITS[id];
    const cx = (i % cols) * cellW + cellW / 2;
    const cy = Math.floor(i / cols) * cellH + 130;
    // pedestal
    ctx.fillStyle = "rgba(20,24,12,0.25)";
    ctx.beginPath();
    ctx.ellipse(cx, cy + 24, 46, 16, 0, 0, Math.PI * 2);
    ctx.fill();
    const e = w.spawnUnit(Team.Player, id, cx, cy);
    e.x = cx;
    e.y = cy;
    e.vx = e.vy = 0;
    e.facing = -Math.PI / 6;
    // scale up heroes a touch for legibility
    ctx.save();
    ctx.translate(cx, cy);
    ctx.scale(1.7, 1.7);
    ctx.translate(-cx, -cy);
    drawUnit(ctx, e, 1.4);
    ctx.restore();
    label(ctx, def.name, cx, cy + 64, `${def.armorClass} · ⚔${def.attack} ❤${def.hp}`);
    e.alive = false;
  });
  save("01-units.png", c);
}

// ------------------------------------------------------------- buildings --
function shotBuildings() {
  const w = scratchWorld();
  const ids = Object.keys(BUILDINGS);
  const cols = 5;
  const rows = Math.ceil(ids.length / cols);
  const cellW = 210;
  const cellH = 200;
  const W = cols * cellW;
  const H = rows * cellH + 64;
  const { c, ctx } = makeCtx(W, H);
  groundPlate(ctx, 0, 0, W, H);
  ctx.textAlign = "center";
  ctx.font = "bold 30px Georgia, serif";
  ctx.fillStyle = "#ffe9b0";
  ctx.fillText("Banner & Blade — The Realm", W / 2, 40);
  ctx.textAlign = "left";

  ids.forEach((id, i) => {
    const def = BUILDINGS[id];
    const cx = (i % cols) * cellW + cellW / 2;
    const cy = Math.floor(i / cols) * cellH + 132;
    const e = w.spawnBuilding(Team.Player, id, cx, cy, true);
    e.x = cx;
    e.y = cy;
    drawBuilding(ctx, e, 1.2, Team.Player);
    label(ctx, def.name, cx, cy + 70, `❤${def.hp}${def.attack ? ` · ⚔${def.attack}` : ""}`);
    e.alive = false;
  });
  save("02-buildings.png", c);
}

// ----------------------------------------------------------- rarity row --
function shotRarities() {
  const w = scratchWorld();
  const W = 1180;
  const H = 320;
  const { c, ctx } = makeCtx(W, H);
  groundPlate(ctx, 0, 0, W, H);
  ctx.textAlign = "center";
  ctx.font = "bold 28px Georgia, serif";
  ctx.fillStyle = "#ffe9b0";
  ctx.fillText("War Chest Rarities — the same Knight, six tiers", W / 2, 44);
  ctx.font = "15px sans-serif";
  ctx.fillStyle = "#c8bfa6";
  ctx.fillText("Higher rarity = more health, more attack, and an ornate aura", W / 2, 70);
  ctx.textAlign = "left";

  const n = RARITIES.length;
  const cellW = W / n;
  RARITIES.forEach((r, i) => {
    const cx = i * cellW + cellW / 2;
    const cy = 175;
    ctx.fillStyle = "rgba(20,24,12,0.25)";
    ctx.beginPath();
    ctx.ellipse(cx, cy + 26, 44, 15, 0, 0, Math.PI * 2);
    ctx.fill();
    const e = w.spawnUnit(Team.Player, "knight", cx, cy);
    e.x = cx;
    e.y = cy;
    e.vx = e.vy = 0;
    e.facing = -Math.PI / 6;
    e.variantRarity = i;
    ctx.save();
    ctx.translate(cx, cy);
    ctx.scale(1.7, 1.7);
    ctx.translate(-cx, -cy);
    drawUnit(ctx, e, 1.4);
    ctx.restore();
    label(ctx, r.name, cx, cy + 78, undefined, r.color);
    e.alive = false;
  });
  save("03-rarities.png", c);
}

// --------------------------------------------------------------- battle --
function shotBattle(dayPhase = 0.2, outName = "04-battle.png", title = "Lines clash on the Open Plains") {
  const map = generateMap("open_plains", 99221);
  const world = new World(99221);
  world.init(map, [{}, {}], [1, 1]);
  const W = 1280;
  const H = 760;
  const { c } = makeCtx(W, H);
  const renderer = new Renderer(c as any);
  renderer.prepare(map);
  const cam = new Camera();
  cam.setViewport(W, H);
  cam.setWorld(map.worldW, map.worldH);

  // Stage a clash just in front of the player's base, toward the centre.
  const start = map.starts[Team.Player];
  const cxw = map.worldW / 2;
  const cyw = map.worldH / 2;
  const dx = cxw - start.x;
  const dy = cyw - start.y;
  const dl = Math.hypot(dx, dy);
  const ux = dx / dl;
  const uy = dy / dl;
  const fieldX = start.x + ux * 260;
  const fieldY = start.y + uy * 260;
  // perpendicular for line formations
  const px = -uy;
  const py = ux;

  const playerComp = ["militia", "militia", "spearman", "archer", "archer", "knight", "knight", "catapult"];
  const enemyComp = ["militia", "spearman", "spearman", "archer", "skirmisher", "knight", "ram", "militia"];

  const pUnits: number[] = [];
  const eUnits: number[] = [];
  playerComp.forEach((t, i) => {
    const off = (i - playerComp.length / 2) * 34;
    const u = world.spawnUnit(Team.Player, t, fieldX - ux * 70 + px * off, fieldY - uy * 70 + py * off);
    pUnits.push(u.id);
  });
  enemyComp.forEach((t, i) => {
    const off = (i - enemyComp.length / 2) * 34;
    const u = world.spawnUnit(Team.Enemy, t, fieldX + ux * 70 + px * off, fieldY + uy * 70 + py * off);
    eUnits.push(u.id);
  });
  // A couple of defensive buildings + a tower for flavour near the player line,
  // flanked by watchfires (they really sing in the night shot).
  world.spawnBuilding(Team.Player, "watch_tower", start.x + ux * 150 + px * 120, start.y + uy * 150 + py * 120, true);
  world.spawnBuilding(Team.Player, "watchfire", fieldX - ux * 130 + px * 150, fieldY - uy * 130 + py * 150, true);
  world.spawnBuilding(Team.Player, "watchfire", fieldX - ux * 130 - px * 150, fieldY - uy * 130 - py * 150, true);

  // Order both lines to fight.
  for (const id of pUnits) {
    const t = eUnits[Math.floor(Math.random() * eUnits.length)];
    world.issueAttack([id], t);
  }
  for (const id of eUnits) {
    const t = pUnits[Math.floor(Math.random() * pUnits.length)];
    world.issueAttack([id], t);
  }

  // Run a short while so units close, swing, and arrows/rocks are mid-air.
  const particles = new Particles(600);
  for (let i = 0; i < 26; i++) {
    world.tick();
    for (const ev of world.drainEvents()) {
      if (ev.kind === "siege") particles.burst(ev.x, ev.y, 14, PAL.dust, 150, { maxLife: 0.8, size: 3 });
      if (ev.kind === "death") particles.burst(ev.x, ev.y, 7, PAL.blood, 100, { maxLife: 0.6, size: 2 });
    }
  }
  // Seed some battlefield juice for the still: corpses, stuck arrows, floating
  // damage, scorch — the kind of thing that builds up seconds into a fight.
  for (let i = 0; i < 10; i++) {
    const ang = Math.random() * Math.PI * 2;
    const rad = Math.random() * 90;
    renderer.addCorpse(fieldX + Math.cos(ang) * rad, fieldY + Math.sin(ang) * rad, Math.random() < 0.5 ? 0 : 1, false);
    renderer.addStuckArrow(fieldX + Math.cos(ang) * rad * 1.2, fieldY + Math.sin(ang) * rad, Math.random() * Math.PI * 2);
  }
  renderer.addScorch(fieldX + 40, fieldY + 20, 28);
  for (let i = 0; i < 6; i++) {
    renderer.addFloater(fieldX + (Math.random() - 0.5) * 140, fieldY + (Math.random() - 0.5) * 100, String(3 + Math.floor(Math.random() * 18)), Math.random() < 0.5 ? "#fff0c0" : "#ff9a8a", 13);
  }

  // Reveal the area so everything draws.
  world.fog[Team.Player].fill(FOG_VISIBLE);
  cam.centerOn(fieldX, fieldY);
  cam.zoom = 1.25;
  renderer.dayPhase = dayPhase;

  // Fire signature abilities so the pulsing auras show in the still.
  world.useAbility(pUnits);
  world.useAbility(eUnits);

  // Select a few player units to show selection rings + health bars.
  for (const id of pUnits.slice(0, 4)) {
    const e = world.byId.get(id);
    if (e) e.selected = true;
  }

  renderer.render(
    world, cam, particles, 0.016, 2.0, Team.Player,
    [{ x: fieldX + ux * 70, y: fieldY + uy * 70, age: 0.1, kind: "attack" }],
    null, { active: false, x0: 0, y0: 0, x1: 0, y1: 0 }, null,
  );

  // Title banner.
  const ctx = renderer.ctx as any;
  ctx.textAlign = "center";
  ctx.font = "bold 26px Georgia, serif";
  ctx.fillStyle = "rgba(0,0,0,0.5)";
  ctx.fillText(title, W / 2 + 2, 40 + 2);
  ctx.fillStyle = "#ffe9b0";
  ctx.fillText(title, W / 2, 40);
  ctx.textAlign = "left";
  save(outName, c);
  return { world, map, cam, pUnits };
}

// ------------------------------------------------------------- in-match HUD --
// ---------------------------------------------------- abilities & watchfire --
function shotAbilities() {
  const map = generateMap("open_plains", 4242);
  const world = new World(4242);
  world.init(map, [{}, {}], [1, 1]);
  const W = 1280;
  const H = 760;
  const { c, ctx } = makeCtx(W, H);
  const renderer = new Renderer(c as any);
  renderer.prepare(map);
  const cam = new Camera();
  cam.setViewport(W, H);
  cam.setWorld(map.worldW, map.worldH);

  const cx = map.worldW / 2;
  const cy = map.worldH / 2;
  // A line of ability-using units, each mid-ability, behind a watchfire.
  const roster = ["militia", "spearman", "knight", "archer", "skirmisher"];
  const ids: number[] = [];
  roster.forEach((t, i) => {
    const u = world.spawnUnit(Team.Player, t, cx - 150 + i * 74, cy + 30);
    u.facing = -Math.PI / 2;
    ids.push(u.id);
  });
  // Enemy knights charging in from the top for the auras to read against.
  for (let i = 0; i < 3; i++) {
    const e = world.spawnUnit(Team.Enemy, "knight", cx - 80 + i * 74, cy - 110);
    e.facing = Math.PI / 2;
    world.useAbility([e.id]);
  }
  world.spawnBuilding(Team.Player, "watchfire", cx, cy + 96, true);
  world.useAbility(ids);

  world.fog[Team.Player].fill(FOG_VISIBLE);
  cam.centerOn(cx, cy);
  cam.zoom = 2.3;
  renderer.dayPhase = 0.78; // night, so the watchfire glow and auras pop

  renderer.render(
    world, cam, new Particles(10), 0.016, 2.0, Team.Player,
    [], null, { active: false, x0: 0, y0: 0, x1: 0, y1: 0 }, null,
  );

  // Caption + per-unit ability tags.
  ctx.textAlign = "center";
  ctx.font = "bold 24px Georgia, serif";
  ctx.fillStyle = "rgba(0,0,0,0.55)";
  ctx.fillText("Signature abilities — fire them in the fight", W / 2 + 2, 42);
  ctx.fillStyle = "#ffe9b0";
  ctx.fillText("Signature abilities — fire them in the fight", W / 2, 40);
  const tags = ["War Cry", "Brace Spears", "Cavalry Charge", "Arrow Volley", "Caltrops"];
  ids.forEach((id, i) => {
    const e = world.byId.get(id)!;
    const sx = cam.worldToScreenX(e.x);
    const sy = cam.worldToScreenY(e.y) + 54;
    ctx.font = "bold 13px sans-serif";
    ctx.fillStyle = "rgba(0,0,0,0.6)";
    ctx.fillText(tags[i], sx + 1, sy + 1);
    ctx.fillStyle = "#cfe9ff";
    ctx.fillText(tags[i], sx, sy);
  });
  ctx.textAlign = "left";
  save("11-abilities.png", c);
}

// ------------------------------------------------------- drag-paint walls --
function shotWallPaint() {
  const map = generateMap("highlands", 7);
  const world = new World(7);
  world.init(map, [{}, {}], [1, 1]);
  const W = 1280;
  const H = 760;
  const { c, ctx } = makeCtx(W, H);
  const renderer = new Renderer(c as any);
  renderer.prepare(map);
  const cam = new Camera();
  cam.setViewport(W, H);
  cam.setWorld(map.worldW, map.worldH);

  const start = map.starts[Team.Player];
  // A handful of villagers ready to build.
  for (let i = 0; i < 4; i++) {
    world.spawnUnit(Team.Player, "villager", start.x - 40 + i * 26, start.y + 70).selected = true;
  }
  // A short existing wall + gate, with a gap the player is now painting closed.
  const TILE = map.worldW / map.cols;
  for (let i = -4; i <= -2; i++) {
    world.spawnBuilding(Team.Player, "stone_wall", start.x + i * TILE, start.y - 60, true);
  }
  world.spawnBuilding(Team.Player, "gate", start.x + 3 * TILE, start.y - 60, true);

  world.fog[Team.Player].fill(FOG_VISIBLE);
  cam.centerOn(start.x, start.y - 30);
  cam.zoom = 2.1;

  // Build the live drag preview: a snapped run of stone-wall ghosts.
  const y = start.y - 60;
  const line: { x: number; y: number; valid: boolean }[] = [];
  for (let i = -1; i <= 3; i++) {
    line.push({ x: start.x + i * TILE, y, valid: world.canPlace(Team.Player, "stone_wall", start.x + i * TILE, y) });
  }
  const ghost = { type: "stone_wall", x: start.x, y, valid: true, line };

  renderer.render(
    world, cam, new Particles(10), 0.016, 2.0, Team.Player,
    [], ghost, { active: false, x0: 0, y0: 0, x1: 0, y1: 0 }, null,
  );

  ctx.textAlign = "center";
  ctx.font = "bold 24px Georgia, serif";
  ctx.fillStyle = "rgba(0,0,0,0.55)";
  ctx.fillText("Drag to paint a wall — the whole run at once", W / 2 + 2, 42);
  ctx.fillStyle = "#ffe9b0";
  ctx.fillText("Drag to paint a wall — the whole run at once", W / 2, 40);
  ctx.textAlign = "left";
  save("11b-wallpaint.png", c);
}

// ------------------------------------------------------- 4-player free-for-all --
function shotFFA() {
  const map = generateMap("open_plains", 31337, 4);
  const world = new World(31337);
  world.init(map, [{}, {}, {}, {}], [1, 1, 1, 1]);
  const W = 820;
  const H = 820;
  const { c, ctx } = makeCtx(W, H);
  const renderer = new Renderer(c as any);
  renderer.prepare(map);
  const cam = new Camera();
  cam.setViewport(W, H);
  cam.setWorld(map.worldW, map.worldH);

  // Drop a little starter army by each base so the four colours read clearly.
  const comps = ["militia", "spearman", "archer", "knight"];
  map.starts.forEach((s, t) => {
    const cxw = map.worldW / 2;
    const cyw = map.worldH / 2;
    const ux = (cxw - s.x) / Math.hypot(cxw - s.x, cyw - s.y);
    const uy = (cyw - s.y) / Math.hypot(cxw - s.x, cyw - s.y);
    comps.forEach((u, i) => {
      world.spawnUnit(t as any, u, s.x + ux * 90 + (i - 1.5) * 26, s.y + uy * 90);
    });
  });

  for (let t = 0; t < 4; t++) world.fog[t].fill(FOG_VISIBLE);
  cam.centerOn(map.worldW / 2, map.worldH / 2);
  cam.zoom = Math.min(W / map.worldW, H / map.worldH) * 0.99;

  renderer.render(
    world, cam, new Particles(10), 0.016, 2.0, Team.Player,
    [], null, { active: false, x0: 0, y0: 0, x1: 0, y1: 0 }, null,
  );

  // Corner labels in each team's colour.
  const names = ["You (Azure)", "Crimson", "Verdant", "Amber"];
  map.starts.forEach((s, t) => {
    const sx = cam.worldToScreenX(s.x);
    const sy = cam.worldToScreenY(s.y) - 36;
    const col = PAL.teams[t].light;
    ctx.textAlign = "center";
    ctx.font = "bold 15px sans-serif";
    ctx.fillStyle = "rgba(0,0,0,0.6)";
    ctx.fillText(names[t], sx + 1, sy + 1);
    ctx.fillStyle = col;
    ctx.fillText(names[t], sx, sy);
  });

  ctx.textAlign = "center";
  ctx.font = "bold 26px Georgia, serif";
  ctx.fillStyle = "rgba(0,0,0,0.55)";
  ctx.fillText("Free-for-All — four realms, last one standing", W / 2 + 2, 42);
  ctx.fillStyle = "#ffe9b0";
  ctx.fillText("Free-for-All — four realms, last one standing", W / 2, 40);
  ctx.textAlign = "left";
  save("12-ffa.png", c);
}

// ------------------------------------------------------------- 2v2 teams --
function shotTeams() {
  const map = generateMap("open_plains", 8181, 4);
  const world = new World(8181);
  // Teams 0 & 2 (top) ally vs 1 & 3 (bottom).
  world.init(map, [{}, {}, {}, {}], [1, 1, 1, 1], [0, 1, 0, 1]);
  const W = 820;
  const H = 820;
  const { c, ctx } = makeCtx(W, H);
  const renderer = new Renderer(c as any);
  renderer.prepare(map);
  const cam = new Camera();
  cam.setViewport(W, H);
  cam.setWorld(map.worldW, map.worldH);

  const comps = ["militia", "spearman", "archer", "knight"];
  map.starts.forEach((s, t) => {
    const cxw = map.worldW / 2;
    const cyw = map.worldH / 2;
    const dl = Math.hypot(cxw - s.x, cyw - s.y);
    const ux = (cxw - s.x) / dl;
    const uy = (cyw - s.y) / dl;
    comps.forEach((u, i) => {
      world.spawnUnit(t as any, u, s.x + ux * 90 + (i - 1.5) * 26, s.y + uy * 90);
    });
  });

  for (let t = 0; t < 4; t++) world.fog[t].fill(FOG_VISIBLE);
  cam.centerOn(map.worldW / 2, map.worldH / 2);
  cam.zoom = Math.min(W / map.worldW, H / map.worldH) * 0.99;

  renderer.render(
    world, cam, new Particles(10), 0.016, 2.0, Team.Player,
    [], null, { active: false, x0: 0, y0: 0, x1: 0, y1: 0 }, null,
  );

  // Labels by diplomacy: you & ally on top, two enemies below.
  map.starts.forEach((s, t) => {
    const rel = world.relationTo(Team.Player, t as any);
    const label = rel === "self" ? "You" : rel === "ally" ? "Ally" : "Enemy";
    const col = rel === "self" ? PAL.diplomacy.self.light
      : rel === "ally" ? PAL.diplomacy.ally.light
      : PAL.diplomacy.enemies[world.enemyIndexOf(Team.Player, t as any)].light;
    const sx = cam.worldToScreenX(s.x);
    const sy = cam.worldToScreenY(s.y) - 36;
    ctx.textAlign = "center";
    ctx.font = "bold 15px sans-serif";
    ctx.fillStyle = "rgba(0,0,0,0.6)";
    ctx.fillText(label, sx + 1, sy + 1);
    ctx.fillStyle = col;
    ctx.fillText(label, sx, sy);
  });
  // A dotted line marking the front between the two alliances.
  ctx.strokeStyle = "rgba(255,255,255,0.35)";
  ctx.lineWidth = 2;
  ctx.setLineDash([10, 8]);
  ctx.beginPath();
  ctx.moveTo(0, H / 2);
  ctx.lineTo(W, H / 2);
  ctx.stroke();
  ctx.setLineDash([]);

  ctx.textAlign = "center";
  ctx.font = "bold 26px Georgia, serif";
  ctx.fillStyle = "rgba(0,0,0,0.55)";
  ctx.fillText("Teams 2v2 — you and an ally vs two foes", W / 2 + 2, 42);
  ctx.fillStyle = "#ffe9b0";
  ctx.fillText("Teams 2v2 — you and an ally vs two foes", W / 2, 40);
  ctx.textAlign = "left";
  save("13-teams.png", c);
}

function shotHero() {
  const map = generateMap("open_plains", 909, 2);
  const world = new World(909);
  world.init(map, [{}, {}], [1, 1]);
  const W = 1280;
  const H = 760;
  const { c, ctx } = makeCtx(W, H);
  const renderer = new Renderer(c as any);
  renderer.prepare(map);
  const cam = new Camera();
  cam.setViewport(W, H);
  cam.setWorld(map.worldW, map.worldH);

  const cx = map.worldW / 2;
  const cy = map.worldH / 2;
  // The Champion, mid-cleave, surrounded by foes, with allies at his back.
  const hero = world.spawnUnit(Team.Player, "hero", cx, cy);
  hero.heroLevel = 3; // show the level pips + golden aura
  hero.facing = -0.3;
  const foes = ["militia", "spearman", "archer", "knight", "skirmisher", "militia"];
  foes.forEach((t, i) => {
    const a = (i / foes.length) * Math.PI * 2;
    world.spawnUnit(Team.Enemy, t, cx + Math.cos(a) * 70, cy + Math.sin(a) * 70);
  });
  world.spawnUnit(Team.Player, "militia", cx - 70, cy + 50);
  world.spawnUnit(Team.Player, "archer", cx - 100, cy + 30);
  world.useAbility([hero.id]); // Heroic Cleave aura + rally

  world.fog[Team.Player].fill(FOG_VISIBLE);
  cam.centerOn(cx, cy);
  cam.zoom = 2.4;
  renderer.render(
    world, cam, new Particles(10), 0.016, 2.0, Team.Player,
    [], null, { active: false, x0: 0, y0: 0, x1: 0, y1: 0 }, null,
  );

  ctx.textAlign = "center";
  ctx.font = "bold 25px Georgia, serif";
  ctx.fillStyle = "rgba(0,0,0,0.55)";
  ctx.fillText("The Champion — a hero who levels up, cleaves, and rises again", W / 2 + 2, 46);
  ctx.fillStyle = "#ffe9b0";
  ctx.fillText("The Champion — a hero who levels up, cleaves, and rises again", W / 2, 44);
  ctx.textAlign = "left";
  save("16-hero.png", c);
}

function shotDiplomacy() {
  const map = generateMap("open_plains", 51, 4);
  const world = new World(51);
  world.init(map, [{}, {}, {}, {}], [1, 1, 1, 1], [0, 1, 0, 1]);
  const W = 1280;
  const H = 760;
  const { c, ctx } = makeCtx(W, H);
  const renderer = new Renderer(c as any);
  renderer.prepare(map);
  const cam = new Camera();
  cam.setViewport(W, H);
  cam.setWorld(map.worldW, map.worldH);

  // A skirmish near the centre: you + ally on the left, two foes on the right.
  const cx = map.worldW / 2;
  const cy = map.worldH / 2;
  const line = (team: number, x: number, types: string[]) =>
    types.forEach((t, i) => world.spawnUnit(team as any, t, x, cy - 60 + i * 40));
  line(Team.Player, cx - 90, ["militia", "knight", "archer"]); // You (blue)
  line(Team.Team3, cx - 150, ["spearman", "militia", "monk"]); // Ally (teal)
  line(Team.Enemy, cx + 90, ["knight", "militia", "archer"]); // Enemy (red)
  line(Team.Team4, cx + 150, ["spearman", "skirmisher", "knight"]); // Enemy (amber)

  for (let t = 0; t < 4; t++) world.fog[t].fill(FOG_VISIBLE);
  cam.centerOn(cx, cy);
  cam.zoom = 2.0;
  renderer.render(
    world, cam, new Particles(10), 0.016, 2.0, Team.Player,
    [], null, { active: false, x0: 0, y0: 0, x1: 0, y1: 0 }, null,
  );

  // Draw the HUD too, so the diplomacy minimap legend shows in context.
  const hud = new HUD();
  hud.prepare(map);
  const noop = () => {};
  const ctrl: any = {
    trainUnit: noop, research: noop, startPlacement: noop, ungarrison: noop, toggleGate: noop,
    trade: noop, stopSelection: noop, holdSelection: noop, setAttackMoveMode: noop, useAbility: noop,
    minimapNavigate: noop, minimapCommand: noop, minimapPing: noop, openMenu: noop,
  };
  ui.begin(ctx, { mx: -50, my: -50, clicked: false, rightClicked: false });
  hud.draw(W, H, world, cam, Team.Player, [], 0.016, ctrl, false);
  ui.flushTooltip(W, H);

  ctx.textAlign = "center";
  ctx.font = "bold 24px Georgia, serif";
  ctx.fillStyle = "rgba(0,0,0,0.55)";
  ctx.fillText("Diplomacy colours — you (blue) + ally (teal) vs foes (red/amber)", W / 2 + 2, 58);
  ctx.fillStyle = "#ffe9b0";
  ctx.fillText("Diplomacy colours — you (blue) + ally (teal) vs foes (red/amber)", W / 2, 56);
  ctx.textAlign = "left";
  save("15-diplomacy.png", c);
}

function shotCommanders() {
  const W = 1280;
  const H = 760;
  // 1) First-launch commander reveal on the menu.
  {
    const p = new Profile();
    p.data.commanders = ["banneret"];
    p.data.commander = "banneret";
    p.data.commanderReveal = "banneret";
    const { c, ctx } = makeCtx(W, H);
    ui.begin(ctx, { mx: -50, my: -50, clicked: false, rightClicked: false });
    new MenuScreen().draw(W, H, 1.0, p);
    ui.flushTooltip(W, H);
    save("18-commander-reveal.png", c);
  }
  // 2) Armory Commanders tab.
  {
    const p = new Profile();
    p.data.renown = 760;
    for (const id of ["steward", "quartermaster", "marshal", "banneret"]) {
      if (!p.ownsCommander(id)) p.data.commanders.push(id);
    }
    p.data.commander = "marshal";
    p.clearCommanderReveal();
    const a = new ArmoryScreen();
    (a as any).tab = "commanders";
    const { c, ctx } = makeCtx(W, H);
    ui.begin(ctx, { mx: -50, my: -50, clicked: false, rightClicked: false });
    a.draw(W, H, 1.0, 0.016, p);
    ui.flushTooltip(W, H);
    save("18b-commander-armory.png", c);
  }
}

function shotCodex() {
  const W = 1280;
  const H = 760;
  const screen = new CodexScreen();
  // Units tab, Knight selected — shows the counter web + ability block.
  (screen as any).selUnit = "knight";
  {
    const { c, ctx } = makeCtx(W, H);
    ui.begin(ctx, { mx: -50, my: -50, clicked: false, rightClicked: false });
    screen.draw(W, H, 2.0);
    ui.flushTooltip(W, H);
    save("17-codex.png", c);
  }
  // Ages & Tech tab.
  (screen as any).tab = "tech";
  {
    const { c, ctx } = makeCtx(W, H);
    ui.begin(ctx, { mx: -50, my: -50, clicked: false, rightClicked: false });
    screen.draw(W, H, 2.0);
    ui.flushTooltip(W, H);
    save("17b-codex-tech.png", c);
  }
}

function shotPostmatch() {
  const W = 1280;
  const H = 760;
  const { c, ctx } = makeCtx(W, H);
  const profile = new Profile();
  const rewards = computeRewards({
    win: true, durationSec: 735, unitsKilled: 48, buildingsRazed: 6,
    difficulty: "lord", fairMode: false,
  });
  const screen = new PostMatchScreen();
  ui.begin(ctx, { mx: -50, my: -50, clicked: false, rightClicked: false });
  screen.draw(
    W, H, 2, 1, true,
    { unitsKilled: 48, unitsLost: 22, buildingsRazed: 6, buildingsLost: 3, gathered: 9400 },
    { unitsKilled: 22, gathered: 7100, buildingsRazed: 3 },
    735, rewards, profile, profile.data.totalXp, 1,
  );
  ui.flushTooltip(W, H);
  save("14-postmatch.png", c);
}

function shotHUD(scene: ReturnType<typeof shotBattle>) {
  const { world, map, cam, pUnits } = scene;
  const W = 1280;
  const H = 760;
  const { c, ctx } = makeCtx(W, H);
  const renderer = new Renderer(c as any);
  renderer.prepare(map);
  cam.setViewport(W, H);
  // Push the clock into the dead of night so the moon dial and the tighter
  // night vision both show in the HUD shot.
  world.time = 144; // ~0.72 of the day cycle
  for (let i = 0; i < 6; i++) world.tick();
  renderer.dayPhase = 0.72;
  renderer.render(
    world, cam, new Particles(10), 0.016, 2.0, Team.Player,
    [], null, { active: false, x0: 0, y0: 0, x1: 0, y1: 0 }, null,
  );

  const hud = new HUD();
  hud.prepare(map);
  hud.addAlert("⚠ Night raid — your forces are under attack!", scene.map.worldW / 2, scene.map.worldH / 2);
  hud.addPing(scene.map.worldW / 2 + 120, scene.map.worldH / 2 - 60);
  const sel = pUnits.map((id) => world.byId.get(id)!).filter(Boolean).slice(0, 6);
  for (const e of sel) e.selected = true;
  const noop = () => {};
  const ctrl: any = {
    trainUnit: noop, research: noop, startPlacement: noop, ungarrison: noop, toggleGate: noop,
    trade: noop, stopSelection: noop, holdSelection: noop, setAttackMoveMode: noop,
    minimapNavigate: noop, minimapCommand: noop, openMenu: noop,
  };
  ui.begin(ctx, { mx: -50, my: -50, clicked: false, rightClicked: false });
  hud.draw(W, H, world, cam, Team.Player, sel, 0.016, ctrl, false);
  ui.flushTooltip(W, H);
  save("05-hud.png", c);
}

// ----------------------------------------------------------------- menus --
function shotMenus() {
  const profile = new Profile();
  profile.data.totalXp = 1820;
  profile.data.renown = 760;
  profile.data.stats = { wins: 7, losses: 3, played: 10, bestStreak: 4, streak: 2 };
  // Own a few commanders so the selectors look alive; no reveal for these shots.
  for (const id of ["steward", "marshal", "banneret"]) {
    if (!profile.ownsCommander(id)) profile.data.commanders.push(id);
  }
  profile.data.commander = "banneret";
  profile.clearCommanderReveal();

  const W = 1280;
  const H = 760;

  {
    const { c, ctx } = makeCtx(W, H);
    ui.begin(ctx, { mx: -50, my: -50, clicked: false, rightClicked: false });
    new MenuScreen().draw(W, H, 1.0, profile);
    ui.flushTooltip(W, H);
    save("06-menu.png", c);
  }
  {
    const { c, ctx } = makeCtx(W, H);
    ui.begin(ctx, { mx: -50, my: -50, clicked: false, rightClicked: false });
    new SetupScreen().draw(W, H, 1.0, profile);
    ui.flushTooltip(W, H);
    save("07-setup.png", c);
  }
  {
    const { c, ctx } = makeCtx(W, H);
    // Grant a few shinies so the collection/odds look alive.
    ui.begin(ctx, { mx: -50, my: -50, clicked: false, rightClicked: false });
    new ArmoryScreen().draw(W, H, 1.0, 0.016, profile);
    ui.flushTooltip(W, H);
    save("08-armory.png", c);
  }
}

// --------------------------------------------------------------- fortress --
function shotFortress() {
  const map = generateMap("black_forest", 31415);
  const world = new World(31415);
  world.init(map, [{}, {}], [1, 1]);
  const W = 1280;
  const H = 760;
  const { c } = makeCtx(W, H);
  const renderer = new Renderer(c as any);
  renderer.prepare(map);
  renderer.dayPhase = 0.5; // dusk siege
  const cam = new Camera();
  cam.setViewport(W, H);
  cam.setWorld(map.worldW, map.worldH);

  const T = 32;
  const cx = map.starts[Team.Player].x;
  const cy = map.starts[Team.Player].y;
  const R = 5;
  for (let i = -R; i <= R; i++) {
    for (const [gx, gy] of [[i, -R], [i, R], [-R, i], [R, i]] as [number, number][]) {
      const wx = cx + gx * T;
      const wy = cy + gy * T;
      if (gy === R && gx === 0) {
        const g = world.spawnBuilding(Team.Player, "gate", wx, wy, true);
        g.gateOpen = true;
        continue;
      }
      if (Math.abs(gx) === R && Math.abs(gy) === R) continue; // corners get towers
      world.spawnBuilding(Team.Player, "stone_wall", wx, wy, true);
    }
  }
  for (const [gx, gy] of [[-R, -R], [R, -R], [-R, R], [R, R]] as [number, number][]) {
    world.spawnBuilding(Team.Player, "watch_tower", cx + gx * T, cy + gy * T, true);
  }
  world.spawnBuilding(Team.Player, "house", cx - 3 * T, cy - 2 * T, true);
  world.spawnBuilding(Team.Player, "house", cx + 3 * T, cy - 2 * T, true);
  // defenders inside
  for (let i = 0; i < 9; i++) {
    world.spawnUnit(Team.Player, i % 2 ? "archer" : "militia", cx + (Math.random() - 0.5) * R * T, cy + (Math.random() - 0.3) * R * T);
  }
  // besiegers massing at the gate
  for (let i = 0; i < 7; i++) {
    world.spawnUnit(Team.Enemy, i % 3 === 0 ? "ram" : "knight", cx + (i - 3) * 34, cy + (R + 3) * T + (i % 2) * 26);
  }
  world.fog[Team.Player].fill(FOG_VISIBLE);
  cam.centerOn(cx, cy + 30);
  cam.zoom = 1.15;
  renderer.render(world, cam, new Particles(10), 0.016, 2.0, Team.Player, [], null, { active: false, x0: 0, y0: 0, x1: 0, y1: 0 }, null);
  const ctx = renderer.ctx as any;
  ctx.textAlign = "center";
  ctx.font = "bold 26px Georgia, serif";
  ctx.fillStyle = "rgba(0,0,0,0.5)";
  ctx.fillText("Hold the walls — a fortress at dusk", W / 2 + 2, 42);
  ctx.fillStyle = "#ffe9b0";
  ctx.fillText("Hold the walls — a fortress at dusk", W / 2, 40);
  ctx.textAlign = "left";
  save("10-fortress.png", c);
}

console.log("Rendering Banner & Blade preview shots…");
shotUnits();
shotBuildings();
shotRarities();
const scene = shotBattle();
shotBattle(0.74, "09-night-battle.png", "Night raid — battle under the dark");
shotHUD(scene);
shotAbilities();
shotWallPaint();
shotFFA();
shotTeams();
shotHero();
shotDiplomacy();
shotPostmatch();
shotCodex();
shotFortress();
shotMenus();
shotCommanders();
console.log("Done.");
