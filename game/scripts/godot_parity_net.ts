// Fixtures for the Godot port's wire tests: doubles as a browser writes them
// into JSON (the shortest text that reads back exactly), with their exact bit
// patterns, so the Godot client can be checked to read every one of them back
// to the same double — which lockstep cross-play depends on.
//
//   npx tsx scripts/godot_parity_net.ts
//
// Writes godot/tests/fixtures/net_floats.json: [[hex bits (little-endian), text], ...]

import fs from "fs";
import path from "path";
import { RNG } from "../src/engine/rng";

const OUT = path.resolve("../godot/tests/fixtures");
fs.mkdirSync(OUT, { recursive: true });

const bits = (x: number) => Buffer.from(new Float64Array([x]).buffer).toString("hex");
const rng = new RNG(20261021);
const r = () => rng.next();
const out: [string, string][] = [];
for (let i = 0; i < 4000; i++) {
  let x: number;
  switch (i % 10) {
    case 0: x = r() * 4096; break;                                  // a world coordinate
    case 1: x = (r() - 0.5) * 8192; break;
    case 2: x = 600 + i / 7; break;                                 // a computed one
    case 3: x = Math.hypot(r() * 3000, r() * 3000); break;
    case 4: x = r() * 10 ** Math.floor(r() * 24 - 12); break;
    case 5: x = Math.floor(r() * 5000) + 0.5; break;
    case 6: x = r() * 10 ** Math.floor(r() * 600 - 300); break;    // anything at all
    case 7: x = Math.floor(r() * 2 ** 62); break;                   // integers past 2^53
    case 8: x = r() * 1e-310; break;                                // subnormals
    default: x = Math.floor(r() * 1e6); break;                      // plain integers
  }
  out.push([bits(JSON.parse(JSON.stringify(x))), JSON.stringify(x)]); // what a browser reads back (-0 is written "0")
}
// And the awkward ones by name.
for (const x of [0, -0, 0.1, 0.2, 0.3, 1 / 3, 2 / 3, 1e21, 1e22, 1e23, 2 ** 53, 2 ** 53 + 2, 5e-324, 2.2250738585072014e-308,
  1.7976931348623157e308, 9007199254740993, 0.1 + 0.2, 123456789.12345679, 4.35, 4.345, 1.0000000000000002]) {
  out.push([bits(JSON.parse(JSON.stringify(x))), JSON.stringify(x)]); // what a browser reads back (-0 is written "0")
}
fs.writeFileSync(path.join(OUT, "net_floats.json"), JSON.stringify(out));
console.log(`wrote ${path.join(OUT, "net_floats.json")}: ${out.length} doubles`);
