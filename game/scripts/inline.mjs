// Build a single self-contained HTML that runs by double-click from file:// in
// any browser. We bundle the app to a *classic* IIFE script (not an ES module)
// so there are no module/CORS/file:// execution quirks, then inline it.
//
//   node scripts/inline.mjs   →   dist/banner-and-blade.html

import { build } from "esbuild";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";

const root = new URL("../", import.meta.url);

// 1. Bundle src/main.ts → minified IIFE (classic script, self-executing).
const result = await build({
  entryPoints: [new URL("src/main.ts", root).pathname],
  bundle: true,
  minify: true,
  format: "iife",
  target: ["es2020"],
  write: false,
  legalComments: "none",
  logLevel: "warning",
});
let js = result.outputFiles[0].text;
// A literal </script> in the bundle would close the inline tag early.
js = js.split("</script").join("<\\/script");

// 2. Inline it into the HTML template, replacing the dev module script.
const html = readFileSync(new URL("index.html", root), "utf8");
const tag = /<script[^>]*src="[^"]*main\.ts"[^>]*><\/script>/;
if (!tag.test(html)) throw new Error("inline: could not find the dev <script src=…main.ts> in index.html");
const out = html.replace(tag, () => `<script>\n${js}\n</script>`);

const closes = (out.match(/<\/script>/g) || []).length;
if (closes !== 1) throw new Error(`inline: expected exactly one </script>, found ${closes}`);

mkdirSync(new URL("dist/", root), { recursive: true });
writeFileSync(new URL("dist/banner-and-blade.html", root), out);
console.log(`wrote dist/banner-and-blade.html (${Math.round(out.length / 1024)}KB, classic IIFE, ${closes} script tag)`);
