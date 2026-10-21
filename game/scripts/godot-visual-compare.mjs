// Compare a TS-rendered sheet with the Godot port's rendering of the same
// sheet: writes TS | Godot | |difference| (amplified) side by side, and prints
// how close they are.
//
//   node scripts/godot-visual-compare.mjs ts.png godot.png out.png [--stack]
//
// --stack puts the three one above another (for wide sheets).
import { createCanvas, loadImage } from "@napi-rs/canvas";
import { writeFileSync } from "node:fs";

const args = process.argv.slice(2);
const [a, b, out] = args;
const stack = args.includes("--stack");
// --crop x,y,w,h --zoom k : compare just a region, magnified (nearest).
const cropAt = args.indexOf("--crop");
const crop = cropAt >= 0 ? args[cropAt + 1].split(",").map(Number) : null;
const zoomAt = args.indexOf("--zoom");
const zoom = zoomAt >= 0 ? Number(args[zoomAt + 1]) : 1;
async function load(p) {
  const img = await loadImage(p);
  if (!crop) return img;
  const [cx, cy, cw, ch] = crop;
  const c = createCanvas(cw * zoom, ch * zoom);
  const x = c.getContext("2d");
  x.imageSmoothingEnabled = false;
  x.drawImage(img, cx, cy, cw, ch, 0, 0, cw * zoom, ch * zoom);
  return c;
}
const ia = await load(a);
const ib = await load(b);
const w = Math.max(ia.width, ib.width);
const h = Math.max(ia.height, ib.height);

function pixels(img) {
  const c = createCanvas(w, h);
  const x = c.getContext("2d");
  x.drawImage(img, 0, 0);
  return x.getImageData(0, 0, w, h);
}
const pa = pixels(ia);
const pb = pixels(ib);
const diff = createCanvas(w, h).getContext("2d").createImageData(w, h);
let sum = 0;
let big = 0;
for (let i = 0; i < pa.data.length; i += 4) {
  const dr = Math.abs(pa.data[i] - pb.data[i]);
  const dg = Math.abs(pa.data[i + 1] - pb.data[i + 1]);
  const db = Math.abs(pa.data[i + 2] - pb.data[i + 2]);
  const d = (dr + dg + db) / 3;
  sum += d;
  if (d > 40) big++;
  const v = Math.min(255, d * 4);
  diff.data[i] = v;
  diff.data[i + 1] = v * 0.6;
  diff.data[i + 2] = 0;
  diff.data[i + 3] = 255;
}
const n = w * h;
console.log(`mean |diff| ${(sum / n).toFixed(2)} / 255, pixels off by >40: ${((big / n) * 100).toFixed(2)}%`);

const W = stack ? w : w * 3 + 8;
const H = stack ? h * 3 + 8 : h;
const c = createCanvas(W, H);
const x = c.getContext("2d");
x.fillStyle = "#222";
x.fillRect(0, 0, W, H);
x.drawImage(ia, 0, 0);
x.drawImage(ib, stack ? 0 : w + 4, stack ? h + 4 : 0);
const dc = createCanvas(w, h);
dc.getContext("2d").putImageData(diff, 0, 0);
x.drawImage(dc, stack ? 0 : 2 * w + 8, stack ? 2 * h + 8 : 0);
writeFileSync(out, c.toBuffer("image/png"));
