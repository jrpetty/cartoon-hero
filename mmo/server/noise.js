// Deterministic value-noise. Same seed => identical world on every machine.
// No dependencies, no Math.random — generation must be reproducible so that
// clients and server agree on the base terrain and only edits need syncing.

function hash2(x, y, seed) {
  // Integer hash -> [0, 1). Cheap, deterministic, good enough for terrain.
  let h = x * 374761393 + y * 668265263 + seed * 2147483647;
  h = (h ^ (h >>> 13)) >>> 0;
  h = Math.imul(h, 1274126177) >>> 0;
  return (h >>> 0) / 4294967296;
}

function smooth(t) {
  return t * t * (3 - 2 * t); // smoothstep
}

// Bilinearly-interpolated value noise at floating point (x, y).
export function valueNoise(x, y, seed) {
  const x0 = Math.floor(x), y0 = Math.floor(y);
  const xf = x - x0, yf = y - y0;
  const v00 = hash2(x0, y0, seed);
  const v10 = hash2(x0 + 1, y0, seed);
  const v01 = hash2(x0, y0 + 1, seed);
  const v11 = hash2(x0 + 1, y0 + 1, seed);
  const u = smooth(xf), v = smooth(yf);
  const a = v00 + (v10 - v00) * u;
  const b = v01 + (v11 - v01) * u;
  return a + (b - a) * v;
}

// Fractal Brownian motion: sum octaves of value noise. Returns ~[0, 1].
export function fbm(x, y, seed, octaves = 4, freq = 0.012, persistence = 0.5) {
  let amp = 1, sum = 0, norm = 0, f = freq;
  for (let o = 0; o < octaves; o++) {
    sum += valueNoise(x * f, y * f, seed + o * 1013) * amp;
    norm += amp;
    amp *= persistence;
    f *= 2;
  }
  return sum / norm;
}
