// Optional baked-sprite registry. When scripts/meshy.ts has generated real
// models, their preview sprites are loaded here and override the procedural unit
// and building art. When no sprites are present (or in headless tests/
// screenshots where there's no DOM), every lookup returns null and the engine
// keeps drawing the procedural art — so the game always renders.

export interface SpriteEntry {
  img: CanvasImageSource;
  scale: number;
  anchorY: number;
  ready: boolean;
}

interface ManifestRow { sprite: string; scale: number; anchorY: number }

const sprites = new Map<string, SpriteEntry>();
let loaded = false;

/** Returns a ready sprite for a type, or null to use procedural art. */
export function spriteFor(type: string): SpriteEntry | null {
  const s = sprites.get(type);
  return s && s.ready ? s : null;
}

/** True once at least one baked sprite is ready (lets callers fast-path). */
export function spritesActive(): boolean {
  return loaded && sprites.size > 0;
}

/**
 * Browser-only: fetch the sprite manifest written by the Meshy pipeline and
 * preload each image. Safe to call always — no-ops in headless environments and
 * swallows a missing manifest (the common case before any generation).
 */
export async function loadSprites(): Promise<number> {
  if (typeof window === "undefined" || typeof Image === "undefined" || typeof fetch === "undefined") {
    return 0;
  }
  let manifest: Record<string, ManifestRow>;
  try {
    const res = await fetch("/sprite-manifest.json", { cache: "no-cache" });
    if (!res.ok) return 0;
    manifest = await res.json();
  } catch {
    return 0; // no manifest yet — procedural art stands in
  }

  let count = 0;
  for (const [id, row] of Object.entries(manifest)) {
    if (!row?.sprite) continue;
    const entry: SpriteEntry = { img: undefined as unknown as CanvasImageSource, scale: row.scale ?? 1, anchorY: row.anchorY ?? 0.5, ready: false };
    const img = new Image();
    img.onload = () => { entry.img = img; entry.ready = true; };
    img.onerror = () => { /* leave not-ready → procedural fallback */ };
    img.src = row.sprite;
    sprites.set(id, entry);
    count++;
  }
  loaded = true;
  return count;
}
