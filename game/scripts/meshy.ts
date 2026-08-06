// Meshy AI asset pipeline for Banner & Blade.
//
// Generates real 3D models from text prompts and downloads each one's GLB plus
// a rendered preview PNG that the 2D engine uses as a billboard sprite (the
// "bake to 2D sprites" path). Procedural art remains the fallback for anything
// not yet generated, so the game always renders.
//
// Usage:
//   MESHY_API_KEY=... npx tsx scripts/meshy.ts            # generate all missing
//   MESHY_API_KEY=... npx tsx scripts/meshy.ts villager   # one asset
//   MESHY_API_KEY=... npx tsx scripts/meshy.ts --refine    # also run refine pass
//   MESHY_API_KEY=... npx tsx scripts/meshy.ts --force     # regenerate even if present
//
// No external deps: Node 18+ global fetch + node:fs. Output lands in
// assets/models/<id>/ and a sprite manifest is written to
// src/render/sprite-manifest.json for the game to load.

import { mkdirSync, existsSync, writeFileSync, readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { ASSET_PROMPTS, type AssetPrompt } from "../assets/manifest";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, "..");
// Generated assets live under public/ so Vite serves them in dev and copies them
// on build; the game fetches the manifest at runtime (no static import coupling).
const PUBLIC_DIR = join(ROOT, "public");
const MODELS_DIR = join(PUBLIC_DIR, "models");
const SPRITE_MANIFEST = join(PUBLIC_DIR, "sprite-manifest.json");

const API = "https://api.meshy.ai/openapi/v2/text-to-3d";
const KEY = process.env.MESHY_API_KEY;

const args = process.argv.slice(2);
const REFINE = args.includes("--refine");
const FORCE = args.includes("--force");
const only = args.filter((a) => !a.startsWith("--"));

function authHeaders(): Record<string, string> {
  return { Authorization: `Bearer ${KEY}`, "Content-Type": "application/json" };
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Submit a text-to-3D job; returns the Meshy task id. */
async function submit(p: AssetPrompt, mode: "preview" | "refine", previewId?: string): Promise<string> {
  const body =
    mode === "preview"
      ? {
          mode,
          prompt: p.prompt,
          art_style: "sculpture", // clean, stylized; we re-tint per team in-engine
          negative_prompt: "low quality, blurry, text, watermark, multiple objects",
          should_remesh: true,
          topology: "triangle",
        }
      : { mode, preview_task_id: previewId };
  const res = await fetch(API, { method: "POST", headers: authHeaders(), body: JSON.stringify(body) });
  if (!res.ok) throw new Error(`submit ${mode} ${p.id} failed: ${res.status} ${await res.text()}`);
  const j = (await res.json()) as { result: string };
  return j.result;
}

interface TaskState {
  status: "PENDING" | "IN_PROGRESS" | "SUCCEEDED" | "FAILED" | "CANCELED";
  progress: number;
  model_urls?: { glb?: string };
  thumbnail_url?: string;
  task_error?: { message?: string };
}

async function poll(id: string, label: string): Promise<TaskState> {
  for (;;) {
    const res = await fetch(`${API}/${id}`, { headers: authHeaders() });
    if (!res.ok) throw new Error(`poll ${id} failed: ${res.status} ${await res.text()}`);
    const t = (await res.json()) as TaskState;
    process.stdout.write(`\r  ${label}: ${t.status} ${t.progress ?? 0}%   `);
    if (t.status === "SUCCEEDED") { process.stdout.write("\n"); return t; }
    if (t.status === "FAILED" || t.status === "CANCELED") {
      throw new Error(`\n  ${label} ${t.status}: ${t.task_error?.message ?? "unknown error"}`);
    }
    await sleep(5000);
  }
}

async function download(url: string, dest: string) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`download ${url} failed: ${res.status}`);
  const buf = Buffer.from(await res.arrayBuffer());
  writeFileSync(dest, buf);
}

async function generate(p: AssetPrompt): Promise<{ sprite: string; glb: string }> {
  const dir = join(MODELS_DIR, p.id);
  mkdirSync(dir, { recursive: true });
  const glbPath = join(dir, "model.glb");
  const pngPath = join(dir, "preview.png");

  if (!FORCE && existsSync(pngPath) && existsSync(glbPath)) {
    console.log(`• ${p.id}: already generated, skipping (use --force to redo)`);
    return { sprite: pngPath, glb: glbPath };
  }

  console.log(`• ${p.id}: "${p.prompt.slice(0, 60)}..."`);
  const previewId = await submit(p, "preview");
  let task = await poll(previewId, `${p.id} preview`);

  if (REFINE) {
    const refineId = await submit(p, "refine", previewId);
    task = await poll(refineId, `${p.id} refine`);
  }

  if (task.model_urls?.glb) await download(task.model_urls.glb, glbPath);
  if (task.thumbnail_url) await download(task.thumbnail_url, pngPath);
  console.log(`  saved → ${dir}`);
  return { sprite: pngPath, glb: glbPath };
}

async function main() {
  if (!KEY) {
    console.error(
      "MESHY_API_KEY is not set.\n" +
        "Get a key at https://app.meshy.ai/ (Settings → API), then run:\n" +
        "  MESHY_API_KEY=msy-... npx tsx scripts/meshy.ts"
    );
    process.exit(1);
  }
  const list = only.length ? ASSET_PROMPTS.filter((p) => only.includes(p.id)) : ASSET_PROMPTS;
  if (!list.length) {
    console.error(`No matching assets for: ${only.join(", ")}`);
    console.error(`Known ids: ${ASSET_PROMPTS.map((p) => p.id).join(", ")}`);
    process.exit(1);
  }

  // Preserve any sprites already recorded, then upsert the ones we (re)generate.
  const manifest: Record<string, { sprite: string; scale: number; anchorY: number }> = existsSync(SPRITE_MANIFEST)
    ? JSON.parse(readFileSync(SPRITE_MANIFEST, "utf8"))
    : {};

  for (const p of list) {
    try {
      const { sprite } = await generate(p);
      // Path the game can fetch (served from the public/ web root).
      const webPath = sprite.replace(PUBLIC_DIR, "");
      manifest[p.id] = { sprite: webPath, scale: p.scale ?? 1, anchorY: p.anchorY ?? 0.5 };
    } catch (e) {
      console.error((e as Error).message);
    }
  }

  writeFileSync(SPRITE_MANIFEST, JSON.stringify(manifest, null, 2) + "\n");
  console.log(`\nWrote ${SPRITE_MANIFEST} (${Object.keys(manifest).length} sprites).`);
}

main();
