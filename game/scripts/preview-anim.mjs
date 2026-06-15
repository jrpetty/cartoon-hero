// Animate the REAL in-game drawUnit for a few units (walk + attack) to a GIF, so
// we can confirm the integrated rig moves smoothly. Bundles draw.ts via esbuild.
import { build } from "esbuild";
import { createCanvas } from "canvas";
import GIFEncoder from "gif-encoder-2";
import { writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

const outdir = mkdtempSync(join(tmpdir(), "anim-"));
const outfile = join(outdir, "draw.cjs");
await build({
  entryPoints: ["src/render/draw.ts"],
  bundle: true, format: "cjs", platform: "node", outfile,
  external: ["canvas"], logLevel: "error",
});
const draw = await import(pathToFileURL(outfile).href);

function ent(type, facing) {
  return {
    id: 3, kind: 0, team: 1, type, alive: true, x: 0, y: 0, radius: 17, facing,
    vx: 40, vy: 0, speed: 60,
    attack: 8, range: 0, attackCooldown: 0, attackInterval: 1.0, armor: 0, pierceArmor: 0,
    armorClass: "infantry", visionRange: 0, animPhase: 0,
    hp: 100, maxHp: 100, carry: 0, carryKind: null, gatherCooldown: 0, amount: 0,
    variantRarity: 0, veterancy: 0, heroLevel: 0,
    abilityActive: 0, rallyTimer: 0, slowTimer: 0, hitFlash: 0,
    order: { kind: 0, tx: 0, ty: 0, target: -1 },
  };
}

const show = ["militia", "knight", "archer", "twohand"];
const ents = show.map((t) => ent(t, 0));
const fps = 30, secs = 2.4, frames = Math.round(fps * secs);
const W = 110 * ents.length, H = 130;
const enc = new GIFEncoder(W, H, "neuquant", true);
enc.setDelay(Math.round(1000 / fps));
enc.setRepeat(0);
enc.setQuality(8);
enc.start();
const canvas = createCanvas(W, H);
const ctx = canvas.getContext("2d");

for (let f = 0; f < frames; f++) {
  const dt = 1 / fps;
  const time = f * dt;
  const bg = ctx.createLinearGradient(0, 0, 0, H);
  bg.addColorStop(0, "#6da944"); bg.addColorStop(1, "#588f37");
  ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H);
  for (let i = 0; i < ents.length; i++) {
    const e = ents[i];
    e.animPhase += dt * 6;                    // advance the walk cycle
    e.attackCooldown -= dt;                    // periodic attack pulse
    if (e.attackCooldown <= 0) e.attackCooldown = e.attackInterval;
    ctx.save();
    ctx.translate(55 + i * 110, 86);
    ctx.scale(2.4, 2.4);
    e.x = 0; e.y = 0;
    draw.drawUnit(ctx, e, time);
    ctx.restore();
    ctx.fillStyle = "#10210a"; ctx.font = "11px sans-serif";
    ctx.fillText(show[i], 24 + i * 110, 122);
  }
  enc.addFrame(ctx);
}
enc.finish();
writeFileSync("dist/example-anim.gif", enc.out.getData());
console.log("wrote dist/example-anim.gif", W + "x" + H, frames + " frames");
