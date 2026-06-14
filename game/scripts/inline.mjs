// Inline the Vite build into a single self-contained HTML file that runs from
// file:// with no server. Robust against two classic pitfalls:
//   1. Using the JS as String.replace's 2nd arg lets `$&`/`$\`` etc. inject the
//      matched text — so we use a function replacer (no special substitution).
//   2. A literal `</script>` anywhere in the JS would close the inline tag early
//      and dump the rest as page text — so we neutralise it.

import { readFileSync, writeFileSync } from "node:fs";

const dist = new URL("../dist/", import.meta.url);
const html = readFileSync(new URL("index.html", dist), "utf8");

const tag = html.match(/<script type="module"[^>]*src="([^"]+)"[^>]*><\/script>/);
if (!tag) throw new Error("inline: no <script type=module src=...> found in dist/index.html");

const jsRel = tag[1].replace(/^\.?\//, "");
let js = readFileSync(new URL(jsRel, dist), "utf8");
// Never let the bundle terminate the inline <script>.
js = js.split("</script").join("<\\/script");

const out = html.replace(tag[0], () => `<script type="module">\n${js}\n</script>`);
writeFileSync(new URL("banner-and-blade.html", dist), out);

const closes = (out.match(/<\/script>/g) || []).length;
if (closes !== 1) throw new Error(`inline: expected exactly one </script>, found ${closes}`);
console.log(`wrote dist/banner-and-blade.html (${Math.round(out.length / 1024)}KB, ${closes} script tag)`);
