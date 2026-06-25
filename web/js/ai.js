/* PitchSite — generation bridge.
 * Prefers the backend (real Claude generation) when reachable; otherwise
 * falls back to the local heuristic generator so the tool always works,
 * even fully offline on a sales call with no signal. */

import { generateLocally } from "./generator.js";
import { normalize } from "./state.js";

let backendAvailable = null; // null = unknown, true/false once probed
let lastHealth = { ok: false, ai: false, publish: false, qr: false };

export function getHealth() {
  return lastHealth;
}

export async function probeBackend() {
  try {
    const res = await fetch("/api/health", { method: "GET" });
    const data = await res.json();
    // only treat the backend as a generation source when an AI key is present
    backendAvailable = !!(data.ok && data.ai);
    lastHealth = { available: !!data.ok, ...data };
    return lastHealth;
  } catch {
    backendAvailable = false;
    lastHealth = { ok: false, available: false, ai: false, publish: false, qr: false };
    return lastHealth;
  }
}

/* Returns { site, source } where source is 'claude' | 'local'. */
export async function generate(prompt, { current } = {}) {
  if (backendAvailable === null) await probeBackend();
  if (backendAvailable) {
    try {
      const res = await fetch("/api/generate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ prompt, current }),
      });
      if (res.ok) {
        const data = await res.json();
        if (data && data.site) {
          return { site: normalize(data.site), source: data.source || "claude" };
        }
      }
    } catch {
      /* fall through to local */
    }
  }
  return { site: generateLocally(prompt), source: "local" };
}

/* Improve the copy of the current site via Claude. Requires the backend +
 * an API key. Returns { site } or throws (caller shows a message). */
export async function improveCopy(site, instruction) {
  const res = await fetch("/api/rewrite", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ site, instruction }),
  });
  if (res.status === 501) throw new Error("needs_ai");
  if (!res.ok) throw new Error("rewrite_failed");
  const data = await res.json();
  if (!data || !data.site) throw new Error("rewrite_failed");
  return { site: normalize(data.site), source: data.source || "claude" };
}
