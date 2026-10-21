// Print pixel values along a line in one or more PNGs (for tuning the Godot
// port's Ctx against the canvas):
//   node scripts/godot-visual-probe.mjs x0,y0,x1,y1,steps a.png b.png ...
import { createCanvas, loadImage } from "@napi-rs/canvas";

const [line, ...files] = process.argv.slice(2);
const [x0, y0, x1, y1, steps] = line.split(",").map(Number);
for (const f of files) {
  const img = await loadImage(f);
  const c = createCanvas(img.width, img.height);
  const x = c.getContext("2d");
  x.drawImage(img, 0, 0);
  const d = x.getImageData(0, 0, img.width, img.height).data;
  const row = [];
  for (let i = 0; i <= steps; i++) {
    const px = Math.round(x0 + ((x1 - x0) * i) / steps);
    const py = Math.round(y0 + ((y1 - y0) * i) / steps);
    const o = (py * img.width + px) * 4;
    row.push(`${d[o]},${d[o + 1]},${d[o + 2]}`);
  }
  console.log(f.split("/").pop().padEnd(24), row.join(" "));
}
