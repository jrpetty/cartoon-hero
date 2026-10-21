// Throwaway: boot the inlined bundle under a DOM shim to prove it initialises
// and renders several frames (menu) without throwing. Not committed.
import { readFileSync } from "node:fs";
import { createCanvas } from "@napi-rs/canvas";

const rafQueue = [];
const stubEl = () => ({
  appendChild() {}, removeChild() {}, addEventListener() {}, removeEventListener() {},
  setAttribute() {}, style: {}, classList: { add() {}, remove() {}, toggle() {}, contains: () => false },
  getBoundingClientRect: () => ({ left: 0, top: 0, width: 1280, height: 760 }),
  focus() {}, requestPointerLock() {},
});
const mkCanvas = (w = 1280, h = 760) => {
  const c = createCanvas(w, h);
  c.addEventListener = () => {}; c.removeEventListener = () => {};
  c.getBoundingClientRect = () => ({ left: 0, top: 0, width: w, height: h });
  c.style = {}; c.setAttribute = () => {}; c.focus = () => {};
  c.requestPointerLock = () => {}; c.classList = { add() {}, remove() {}, toggle() {} };
  return c;
};

globalThis.document = {
  getElementById: () => stubEl(),
  createElement: (t) => (t === "canvas" ? mkCanvas() : stubEl()),
  querySelectorAll: () => [],
  addEventListener() {}, removeEventListener() {},
  body: stubEl(), documentElement: stubEl(),
};
class AudioParamStub { setValueAtTime() {} linearRampToValueAtTime() {} exponentialRampToValueAtTime() {} }
class AudioCtxStub {
  currentTime = 0; sampleRate = 44100; state = "running"; destination = {};
  createGain() { return { gain: new AudioParamStub(), connect() {} }; }
  createOscillator() { return { type: "", frequency: new AudioParamStub(), connect() {}, start() {}, stop() {} }; }
  createBuffer() { return { getChannelData: () => new Float32Array(8) }; }
  createBufferSource() { return { buffer: null, connect() {}, start() {}, stop() {} }; }
  createBiquadFilter() { return { type: "", frequency: { value: 0 }, connect() {} }; }
  resume() {} suspend() {}
}
const store = {};
globalThis.window = {
  innerWidth: 1280, innerHeight: 760, devicePixelRatio: 1,
  addEventListener() {}, removeEventListener() {},
  requestAnimationFrame: (cb) => { rafQueue.push(cb); return rafQueue.length; },
  cancelAnimationFrame() {},
  AudioContext: AudioCtxStub, webkitAudioContext: AudioCtxStub,
  localStorage: { getItem: (k) => store[k] ?? null, setItem: (k, v) => { store[k] = String(v); }, removeItem: (k) => { delete store[k]; } },
  matchMedia: () => ({ matches: false, addEventListener() {}, removeEventListener() {} }),
  devicePixelRatio: 1,
};
globalThis.localStorage = globalThis.window.localStorage;
globalThis.requestAnimationFrame = globalThis.window.requestAnimationFrame;
globalThis.cancelAnimationFrame = () => {};
globalThis.AudioContext = AudioCtxStub;
globalThis.devicePixelRatio = 1;
globalThis.performance = globalThis.performance ?? { now: () => Date.now() };
globalThis.fetch = () => Promise.resolve({ ok: false, json: async () => ({}) });

const html = readFileSync(new URL("../dist/banner-and-blade.html", import.meta.url), "utf8");
const m = html.match(/<script>\n([\s\S]*?)\n<\/script>/);
if (!m) { console.error("BOOT FAIL: no inline classic <script> found"); process.exit(1); }

try {
  (0, eval)(m[1]); // run the IIFE → constructs App, schedules first frame
  // Drive several frames; menu rendering exercises ui + canvas paths.
  for (let i = 0; i < 5; i++) {
    const cb = rafQueue.shift();
    if (cb) cb(performance.now() + i * 16);
  }
  console.log("BOOT OK: bundle initialised and rendered", 5, "frames with no errors");
} catch (e) {
  console.error("BOOT FAIL:", e && e.stack ? e.stack.split("\n").slice(0, 6).join("\n") : e);
  process.exit(1);
}
