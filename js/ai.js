/* PitchSite — generation bridge.
 * Prefers the backend (real Claude generation) when reachable; otherwise
 * falls back to the local heuristic generator so the tool always works,
 * even fully offline on a sales call with no signal. */

import { generateLocally } from "./generator.js";
import { normalize } from "./state.js";

let backendAvailable = null; // null = unknown, true/false once probed

export async function probeBackend() {
  try {
    const res = await fetch("/api/health", { method: "GET" });
    const data = await res.json();
    // only treat the backend as a generation source when an AI key is present
    backendAvailable = !!(data.ok && data.ai);
    return { ...data, available: !!data.ok };
  } catch {
    backendAvailable = false;
    return { ok: false, available: false, ai: false };
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
