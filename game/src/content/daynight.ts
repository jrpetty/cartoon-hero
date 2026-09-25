// Shared day/night model. The phase is derived from deterministic sim time, so
// both the simulation (vision) and the renderer (sky tint) agree exactly and
// stay replay-safe. Night shrinks everyone's sight — the core night mechanic.

import { lerp } from "../engine/math";

export const DAY_CYCLE = 200; // seconds for a full day → night → day rotation

export function dayPhase(time: number): number {
  return (time % DAY_CYCLE) / DAY_CYCLE;
}

export function isNight(phase: number): boolean {
  return phase > 0.55 && phase < 0.95;
}

/** Interpolate a scalar across sorted (phase, value) keyframes. */
function keyLerp(phase: number, keys: [number, number][]): number {
  for (let i = 0; i < keys.length - 1; i++) {
    const [p0, v0] = keys[i];
    const [p1, v1] = keys[i + 1];
    if (phase >= p0 && phase <= p1) {
      return lerp(v0, v1, (phase - p0) / (p1 - p0 || 1));
    }
  }
  return keys[keys.length - 1][1];
}

/**
 * Vision multiplier by phase. ~1.0 at midday down to 0.6 at deep night
 * (40% less sight in the dark). Dawn/dusk sit in between.
 */
export function visionMult(phase: number): number {
  return keyLerp(phase, [
    [0.0, 0.85],
    [0.22, 1.0],
    [0.46, 0.8],
    [0.6, 0.6],
    [0.9, 0.6],
    [1.0, 0.85],
  ]);
}

/** Short label for the HUD. */
export function dayLabel(phase: number): string {
  if (phase < 0.05 || phase >= 0.96) return "Dawn";
  if (phase < 0.4) return "Day";
  if (phase < 0.55) return "Dusk";
  return "Night";
}

/** Day/night sky tint by phase (0..1). Returns rgba overlay components. */
export function skyTint(phase: number): [number, number, number, number] {
  const keys: [number, [number, number, number, number]][] = [
    [0.0, [255, 168, 96, 0.16]], // dawn
    [0.22, [255, 255, 255, 0.0]], // bright day
    [0.46, [255, 120, 60, 0.2]], // dusk
    [0.6, [22, 32, 74, 0.5]], // night
    [0.9, [18, 28, 68, 0.46]], // deep night
    [1.0, [255, 168, 96, 0.16]], // back to dawn
  ];
  for (let i = 0; i < keys.length - 1; i++) {
    const [p0, c0] = keys[i];
    const [p1, c1] = keys[i + 1];
    if (phase >= p0 && phase <= p1) {
      const t = (phase - p0) / (p1 - p0 || 1);
      return [lerp(c0[0], c1[0], t), lerp(c0[1], c1[1], t), lerp(c0[2], c1[2], t), lerp(c0[3], c1[3], t)];
    }
  }
  return [0, 0, 0, 0];
}
