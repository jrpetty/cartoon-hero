/* Node smoke test for the pure front-end engine (no DOM needed).
 * Run: node tests/smoke.mjs   — exits non-zero on failure. */
import { generateLocally } from "../web/js/generator.js";
import { renderDocument } from "../web/js/render.js";
import { sampleSite, normalize, emptySite } from "../web/js/state.js";
import { THEME_KEYS } from "../web/js/themes.js";

let failures = 0;
function ok(cond, msg) {
  if (cond) { console.log("  ✓ " + msg); }
  else { console.error("  ✗ " + msg); failures++; }
}

console.log("generator:");
const brief = 'Family-run plumbing & heating company "Northside Plumbing", Gas Safe registered, 24/7 callouts. Phone 0800 123 456, email hello@northside.co.uk';
const g = generateLocally(brief);
ok(g.meta.businessName === "Northside Plumbing", "extracts quoted business name");
ok(g.meta.theme === "coral", "maps plumbing -> coral theme");
ok(g.contact.phone === "0800 123 456", "extracts phone");
ok(g.contact.email === "hello@northside.co.uk", "extracts email");
ok(g.services.items.length >= 3, "generates services");
ok(g.reviews.items.length === 3, "generates sample reviews");

console.log("render (sample site):");
const doc = renderDocument(sampleSite());
ok(doc.startsWith("<!doctype html>"), "produces a full document");
ok(doc.includes("Northside Plumbing"), "includes business name");
ok(doc.includes("ps-hero"), "includes hero section");
ok(doc.includes("</html>"), "document is closed");
ok(/<script>/.test(doc), "includes runtime script");

console.log("render (every theme):");
for (const key of THEME_KEYS) {
  const s = sampleSite(); s.meta.theme = key; s.meta.accentColor = "";
  const d = renderDocument(s);
  ok(d.includes("--accent:") && d.length > 4000, "theme renders: " + key);
}

console.log("render (all sections enabled):");
const all = sampleSite();
all.gallery.enabled = true; all.gallery.images = ["https://x/a.jpg", "https://x/b.jpg"];
all.pricing.enabled = true; all.pricing.plans = [
  { name: "Basic", price: "£99", period: "/mo", cta: "Pick", featured: false, features: ["A", "B"] },
  { name: "Pro", price: "£199", period: "/mo", cta: "Pick", featured: true, features: ["A", "B", "C"] },
];
all.faq.enabled = true; all.faq.items = [{ q: "Q1?", a: "A1" }, { q: "Q2?", a: "A2" }];
all.contact.social = { instagram: "https://insta/x", facebook: "https://fb/x", twitter: "", linkedin: "", tiktok: "" };
const allDoc = renderDocument(all);
ok(allDoc.includes("ps-pricing") && allDoc.includes("ps-plan--featured"), "pricing with featured plan");
ok(allDoc.includes("ps-faq__item"), "faq renders");
ok(allDoc.includes("ps-gallery__grid"), "gallery renders");
ok(allDoc.includes("ps-social"), "social links render");
ok(allDoc.includes("</html>") && !/undefined/.test(allDoc), "no undefined leaks");

console.log("render (empty site doesn't crash):");
const e = renderDocument(normalize(emptySite()));
ok(e.includes("</html>"), "empty site still produces valid doc");

console.log("xss escaping:");
const s = emptySite();
s.meta.businessName = '<script>alert(1)</script>';
s.hero.headline = '"></h1><img src=x onerror=alert(1)>';
const d = renderDocument(s);
ok(!d.includes("<script>alert(1)"), "escapes script in business name");
ok(!d.includes("<img src=x onerror"), "neutralises attribute-breaking hero text");
ok(d.includes("&lt;img src=x onerror"), "renders injected markup as inert text");

console.log("url safety + uploads:");
const su = emptySite();
su.hero.image = "data:image/png;base64,AAAA";
su.about.enabled = true; su.about.image = "data:text/html,<script>alert(1)</script>";
su.meta.businessName = "Img Co";
const du = renderDocument(su);
ok(du.includes("data:image/png;base64,AAAA"), "allows embedded data:image uploads");
ok(!du.includes("data:text/html"), "blocks non-image data URIs");
ok(du.includes('href="#main" class="ps-skip"'), "includes skip-to-content link");
ok(du.includes('id="main"'), "main landmark present");

console.log("");
if (failures) { console.error(`${failures} check(s) FAILED`); process.exit(1); }
console.log("All smoke checks passed ✅");
