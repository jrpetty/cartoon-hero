// Offline render of the sword-clash SFX to a WAV so we can hear it without the
// browser. Mirrors the WebAudio synthesis in src/engine/audio.ts (oscillators,
// RBJ biquad filters, the same envelopes). Approximate, but faithful enough.
import { writeFileSync } from "node:fs";

const SR = 44100;

function biquad(type, f0, Q, signal) {
  const w0 = (2 * Math.PI * f0) / SR;
  const cw = Math.cos(w0), sw = Math.sin(w0), alpha = sw / (2 * Q);
  let b0, b1, b2, a0, a1, a2;
  if (type === "lowpass") { b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = b0; a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha; }
  else if (type === "highpass") { b0 = (1 + cw) / 2; b1 = -(1 + cw); b2 = b0; a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha; }
  else { b0 = alpha; b1 = 0; b2 = -alpha; a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha; } // bandpass
  b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0;
  const out = new Float32Array(signal.length);
  let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
  for (let i = 0; i < signal.length; i++) {
    const x = signal[i];
    const y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
    x2 = x1; x1 = x; y2 = y1; y1 = y;
    out[i] = y;
  }
  return out;
}

function wave(type, ph) {
  if (type === "sine") return Math.sin(ph);
  if (type === "square") return Math.sin(ph) >= 0 ? 1 : -1;
  if (type === "sawtooth") { const p = (ph / (2 * Math.PI)) % 1; return 2 * p - 1; }
  // triangle
  const p = (ph / (2 * Math.PI)) % 1; return 4 * Math.abs(p - 0.5) - 1;
}

function addTone(buf, t0, freq, type, attack, decay, peak, freqEnd) {
  const dur = attack + decay + 0.02;
  const n = Math.floor(dur * SR);
  const s0 = Math.floor(t0 * SR);
  let ph = 0;
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    // frequency: exponential glide over attack+decay, then hold
    let f = freq;
    if (freqEnd !== undefined) { const tt = Math.min(t, attack + decay) / (attack + decay); f = freq * Math.pow(freqEnd / freq, tt); }
    ph += (2 * Math.PI * f) / SR;
    // gain envelope: linear up, exponential down
    let g;
    if (t < attack) g = (t / attack) * peak;
    else { const td = Math.min(1, (t - attack) / decay); g = peak * Math.pow(0.0001 / peak, td); }
    const idx = s0 + i;
    if (idx < buf.length) buf[idx] += wave(type, ph) * g;
  }
}

function addNoise(buf, t0, duration, peak, filterFreq, type = "bandpass") {
  const n = Math.floor(duration * SR);
  const raw = new Float32Array(n);
  for (let i = 0; i < n; i++) raw[i] = (Math.random() * 2 - 1) * (1 - i / n);
  const filt = biquad(type, filterFreq, 1, raw);
  const s0 = Math.floor(t0 * SR);
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    const g = peak * Math.pow(0.0001 / peak, Math.min(1, t / duration));
    const idx = s0 + i;
    if (idx < buf.length) buf[idx] += filt[i] * g;
  }
}

const vary = (a = 0.09) => 1 + (Math.random() * 2 - 1) * a;

// One sword clash at time t0 — exactly the layers in audio.ts.
function sword(buf, t0) {
  const b = vary();
  addNoise(buf, t0 + 0, 0.05, 0.26, 5200 * b, "highpass");
  addNoise(buf, t0 + 0.004, 0.07, 0.18, 3000 * b, "bandpass");
  addTone(buf, t0 + 0.004, 2100 * b, "triangle", 0.001, 0.17, 0.11, 1500 * b);
  addTone(buf, t0 + 0.004, 3170 * b, "triangle", 0.001, 0.14, 0.07, 2300 * b);
  addTone(buf, t0 + 0.006, 4600 * b, "sine", 0.001, 0.11, 0.05, 3600 * b);
  addTone(buf, t0 + 0.001, 170 * b, "triangle", 0.002, 0.05, 0.11, 85);
}

const total = 3.2;
const buf = new Float32Array(Math.floor(total * SR));
// a single clear hit, then a flurry to hear the variation + overlap of a melee
sword(buf, 0.15);
let t = 1.0;
for (let i = 0; i < 7; i++) { sword(buf, t); t += 0.18 + Math.random() * 0.16; }

// overall bus gain (master*sfx in game ≈ 0.56) + soft clip
for (let i = 0; i < buf.length; i++) { let s = buf[i] * 0.62; s = Math.tanh(s * 1.4); buf[i] = s; }

// write 16-bit mono WAV
const bytes = 44 + buf.length * 2;
const ab = new ArrayBuffer(bytes);
const dv = new DataView(ab);
const ws = (off, str) => { for (let i = 0; i < str.length; i++) dv.setUint8(off + i, str.charCodeAt(i)); };
ws(0, "RIFF"); dv.setUint32(4, bytes - 8, true); ws(8, "WAVE");
ws(12, "fmt "); dv.setUint32(16, 16, true); dv.setUint16(20, 1, true); dv.setUint16(22, 1, true);
dv.setUint32(24, SR, true); dv.setUint32(28, SR * 2, true); dv.setUint16(32, 2, true); dv.setUint16(34, 16, true);
ws(36, "data"); dv.setUint32(40, buf.length * 2, true);
for (let i = 0; i < buf.length; i++) { const v = Math.max(-1, Math.min(1, buf[i])); dv.setInt16(44 + i * 2, v * 32767, true); }
writeFileSync("dist/sword-clash.wav", Buffer.from(ab));
console.log("wrote dist/sword-clash.wav", (bytes / 1024 | 0) + "KB,", total + "s");
