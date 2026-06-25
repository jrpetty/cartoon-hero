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

console.log("new sections & features:");
const f = sampleSite();
f.trust.enabled = true; f.trust.logos = [{ name: "Which? Trusted", img: "" }, { name: "", img: "https://x/logo.png" }];
f.video.enabled = true; f.video.url = "https://youtu.be/dQw4w9WgXcQ";
f.team.enabled = true; f.team.members = [{ name: "Jane", role: "Boss", bio: "Hi", photo: "" }];
f.consent.enabled = true;
f.integrations.ga4 = "G-ABC123"; f.integrations.metaPixel = "123456789012";
f.contact.bookingUrl = "https://calendly.com/test";
const fd = renderDocument(f);
ok(fd.includes('id="ps-announce"'), "announcement bar renders");
ok(fd.includes('ps-step__num'), "process steps render");
ok(fd.includes('data-ba') && fd.includes("ps-ba__range"), "before/after slider renders");
ok(fd.includes("youtube-nocookie.com/embed/dQw4w9WgXcQ"), "youtube video embeds with correct id");
ok(fd.includes("ps-trust__row"), "trust/logos bar renders");
ok(fd.includes('class="ps-member'), "team renders");
ok(fd.includes("wa.me/447700900123"), "whatsapp floating button");
ok(fd.includes('href="tel:'), "click-to-call present");
ok(fd.includes('id="ps-top"'), "back-to-top button");
ok(fd.includes('id="ps-consent"'), "cookie consent renders");
ok(fd.includes("googletagmanager.com/gtag/js?id=G-ABC123"), "GA4 injected");
ok(fd.includes("fbq('init','123456789012')"), "meta pixel injected");
ok(fd.includes("📅 Book an appointment") && fd.includes("calendly.com/test"), "booking button renders");

// vimeo + mp4 + bad analytics id
const v = emptySite(); v.meta.businessName = "V"; v.hero.video = "https://vimeo.com/123456";
ok(renderDocument(v).includes("player.vimeo.com/video/123456"), "vimeo embeds");
v.hero.video = "https://cdn.example.com/clip.mp4";
ok(renderDocument(v).includes("<video"), "mp4 uses <video>");
const bad = emptySite(); bad.meta.businessName = "B"; bad.integrations.ga4 = "not-an-id; alert(1)";
ok(!renderDocument(bad).includes("gtag"), "rejects malformed GA id (no injection)");

console.log("structured data (SEO):");
const ldDoc = renderDocument(sampleSite());
const m = ldDoc.match(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/);
ok(!!m, "json-ld script present");
let ld = null;
try { ld = JSON.parse(m[1]); } catch (e) { /* leave null */ }
ok(ld && ld["@type"] === "LocalBusiness", "json-ld is valid parseable LocalBusiness");
ok(ld && ld.aggregateRating && ld.aggregateRating.reviewCount === 3, "json-ld includes aggregate rating from reviews");
ok(ld && ld.name === "Northside Plumbing & Heating", "json-ld preserves spaces in name");

console.log("section reordering:");
const r1 = sampleSite();
const def = renderDocument(r1);
ok(def.indexOf('id="about"') < def.indexOf('id="contact"'), "default order: about before contact");
const r2 = normalize({ ...sampleSite(), layout: { order: ["contact", "about"] } });
const reordered = renderDocument(r2);
ok(reordered.indexOf('id="contact"') < reordered.indexOf('id="about"'), "custom order moves contact before about");
ok(reordered.includes('id="services"'), "missing sections still appended");

console.log("");
if (failures) { console.error(`${failures} check(s) FAILED`); process.exit(1); }
console.log("All smoke checks passed ✅");
