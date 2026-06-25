/* PitchSite — site renderer.
 * Pure function: site object -> a complete, self-contained HTML document.
 * The builder shows this in an <iframe srcdoc>, and the exporter downloads
 * the identical string, so the preview is truly WYSIWYG. */

import { resolveTheme } from "./themes.js";

/* ---------- helpers ---------- */
const esc = (s = "") =>
  String(s)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");

const attr = (s = "") => esc(s);
const has = (s) => s !== undefined && s !== null && String(s).trim() !== "";
const safeUrl = (u = "") => {
  const t = String(u).trim();
  if (t === "") return "";
  // data:image is allowed (for embedded uploads) but never data:text/html etc.
  if (/^(https?:|tel:|mailto:|data:image\/|#|\/)/i.test(t)) return t;
  if (/^data:/i.test(t)) return ""; // block any non-image data URI
  return "https://" + t;
};
const nl2p = (s = "") =>
  esc(s)
    .split(/\n{2,}/)
    .map((p) => `<p>${p.replace(/\n/g, "<br>")}</p>`)
    .join("");

function faviconDataUri(emoji = "🌐") {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><text y=".9em" font-size="90">${emoji}</text></svg>`;
  return "data:image/svg+xml," + encodeURIComponent(svg);
}

function stars(n = 5) {
  const full = Math.max(0, Math.min(5, Math.round(n)));
  return "★★★★★☆☆☆☆☆".slice(5 - full, 10 - full);
}

function mapEmbed(query) {
  if (!has(query)) return "";
  const q = encodeURIComponent(query);
  return `<iframe class="ps-map" loading="lazy" title="Map" src="https://www.google.com/maps?q=${q}&output=embed"></iframe>`;
}

/* ---------- sections ---------- */
function navHtml(site) {
  if (!site.nav.enabled) return "";
  const links = [
    site.about.enabled && ["#about", "About"],
    site.services.enabled && ["#services", "Services"],
    site.gallery.enabled && ["#gallery", "Work"],
    site.reviews.enabled && ["#reviews", "Reviews"],
    site.pricing.enabled && ["#pricing", "Pricing"],
    site.faq.enabled && ["#faq", "FAQ"],
    site.contact.enabled && ["#contact", "Contact"],
  ].filter(Boolean);
  const logo = has(site.meta.logoUrl)
    ? `<img src="${attr(safeUrl(site.meta.logoUrl))}" alt="${attr(site.meta.businessName)}" class="ps-logo-img">`
    : `<span class="ps-logo-mark">${esc(site.meta.favicon || "")}</span><span>${esc(site.meta.logoText || site.meta.businessName)}</span>`;
  return `
  <header class="ps-nav${site.nav.sticky ? " ps-nav--sticky" : ""}">
    <div class="ps-container ps-nav__row">
      <a href="#top" class="ps-logo">${logo}</a>
      <nav class="ps-nav__links" aria-label="Primary">
        ${links.map(([h, t]) => `<a href="${h}">${esc(t)}</a>`).join("")}
      </nav>
      <div class="ps-nav__actions">
        ${site.contact.enabled ? `<a class="ps-btn ps-btn--sm" href="#contact">${esc(site.nav.ctaText || "Contact")}</a>` : ""}
        <button class="ps-burger" aria-label="Menu" aria-expanded="false"><span></span><span></span><span></span></button>
      </div>
    </div>
    <nav class="ps-nav__mobile" aria-label="Mobile">
      ${links.map(([h, t]) => `<a href="${h}">${esc(t)}</a>`).join("")}
    </nav>
  </header>`;
}

function heroHtml(site, theme) {
  const h = site.hero;
  const hasImg = has(h.image);
  const badges = (h.badges || []).filter(has);
  const primary = has(h.ctaText)
    ? `<a class="ps-btn ps-btn--lg" href="${attr(safeUrl(h.ctaLink || "#contact"))}">${esc(h.ctaText)}</a>`
    : "";
  const secondary = has(h.secondaryText)
    ? `<a class="ps-btn ps-btn--ghost ps-btn--lg" href="${attr(safeUrl(h.secondaryLink || "#contact"))}">${esc(h.secondaryText)}</a>`
    : "";
  const media = hasImg
    ? `<div class="ps-hero__media"><img src="${attr(safeUrl(h.image))}" alt="${attr(site.meta.businessName)}" loading="eager"></div>`
    : "";
  return `
  <section class="ps-hero ps-hero--${esc(theme.heroStyle)} ${hasImg ? "ps-hero--haspic" : "ps-hero--nopic"}" id="top">
    <div class="ps-container ps-hero__inner">
      <div class="ps-hero__copy ps-reveal">
        ${has(h.eyebrow) ? `<p class="ps-eyebrow">${esc(h.eyebrow)}</p>` : ""}
        <h1 class="ps-hero__title">${esc(h.headline || site.meta.businessName)}</h1>
        ${has(h.subheadline) ? `<p class="ps-hero__sub">${esc(h.subheadline)}</p>` : ""}
        <div class="ps-hero__cta">${primary}${secondary}</div>
        ${badges.length ? `<ul class="ps-hero__badges">${badges.map((b) => `<li>✓ ${esc(b)}</li>`).join("")}</ul>` : ""}
      </div>
      ${media}
    </div>
  </section>`;
}

function statsHtml(site) {
  if (!site.stats.enabled) return "";
  const items = (site.stats.items || []).filter((i) => has(i.value));
  if (!items.length) return "";
  return `
  <section class="ps-stats">
    <div class="ps-container ps-stats__grid">
      ${items
        .map(
          (i) => `<div class="ps-stat ps-reveal"><div class="ps-stat__value">${esc(i.value)}</div><div class="ps-stat__label">${esc(i.label)}</div></div>`
        )
        .join("")}
    </div>
  </section>`;
}

function aboutHtml(site) {
  if (!site.about.enabled) return "";
  const a = site.about;
  const points = (a.points || []).filter(has);
  const img = has(a.image)
    ? `<div class="ps-about__media ps-reveal"><img src="${attr(safeUrl(a.image))}" alt="${attr(a.title)}" loading="lazy"></div>`
    : "";
  return `
  <section class="ps-section ps-about" id="about">
    <div class="ps-container ps-about__grid ${has(a.image) ? "" : "ps-about__grid--solo"}">
      <div class="ps-about__copy ps-reveal">
        <h2 class="ps-h2">${esc(a.title)}</h2>
        <div class="ps-prose">${nl2p(a.body)}</div>
        ${points.length ? `<ul class="ps-checklist">${points.map((p) => `<li>${esc(p)}</li>`).join("")}</ul>` : ""}
      </div>
      ${img}
    </div>
  </section>`;
}

function servicesHtml(site) {
  if (!site.services.enabled) return "";
  const items = (site.services.items || []).filter((i) => has(i.title));
  if (!items.length) return "";
  return `
  <section class="ps-section ps-services" id="services">
    <div class="ps-container">
      <div class="ps-section__head ps-reveal">
        <h2 class="ps-h2">${esc(site.services.title)}</h2>
        ${has(site.services.subtitle) ? `<p class="ps-section__sub">${esc(site.services.subtitle)}</p>` : ""}
      </div>
      <div class="ps-cards">
        ${items
          .map(
            (i) => `<article class="ps-card ps-reveal">
            ${has(i.icon) ? `<div class="ps-card__icon">${esc(i.icon)}</div>` : ""}
            <h3 class="ps-card__title">${esc(i.title)}</h3>
            ${has(i.description) ? `<p class="ps-card__text">${esc(i.description)}</p>` : ""}
          </article>`
          )
          .join("")}
      </div>
    </div>
  </section>`;
}

function galleryHtml(site) {
  if (!site.gallery.enabled) return "";
  const imgs = (site.gallery.images || []).filter(has);
  if (!imgs.length) return "";
  return `
  <section class="ps-section ps-gallery" id="gallery">
    <div class="ps-container">
      <div class="ps-section__head ps-reveal"><h2 class="ps-h2">${esc(site.gallery.title)}</h2></div>
      <div class="ps-gallery__grid">
        ${imgs.map((u) => `<figure class="ps-reveal"><img src="${attr(safeUrl(u))}" alt="" loading="lazy"></figure>`).join("")}
      </div>
    </div>
  </section>`;
}

function reviewsHtml(site) {
  if (!site.reviews.enabled) return "";
  const items = (site.reviews.items || []).filter((i) => has(i.quote));
  if (!items.length) return "";
  return `
  <section class="ps-section ps-reviews" id="reviews">
    <div class="ps-container">
      <div class="ps-section__head ps-reveal"><h2 class="ps-h2">${esc(site.reviews.title)}</h2></div>
      <div class="ps-reviews__grid">
        ${items
          .map(
            (r) => `<figure class="ps-quote ps-reveal">
            <div class="ps-quote__stars" aria-label="${attr((r.rating || 5) + " out of 5")}">${stars(r.rating || 5)}</div>
            <blockquote>${esc(r.quote)}</blockquote>
            <figcaption><strong>${esc(r.author)}</strong>${has(r.role) ? `<span>${esc(r.role)}</span>` : ""}</figcaption>
          </figure>`
          )
          .join("")}
      </div>
    </div>
  </section>`;
}

function pricingHtml(site) {
  if (!site.pricing.enabled) return "";
  const plans = (site.pricing.plans || []).filter((p) => has(p.name));
  if (!plans.length) return "";
  return `
  <section class="ps-section ps-pricing" id="pricing">
    <div class="ps-container">
      <div class="ps-section__head ps-reveal">
        <h2 class="ps-h2">${esc(site.pricing.title)}</h2>
        ${has(site.pricing.subtitle) ? `<p class="ps-section__sub">${esc(site.pricing.subtitle)}</p>` : ""}
      </div>
      <div class="ps-plans">
        ${plans
          .map(
            (p) => `<article class="ps-plan ps-reveal ${p.featured ? "ps-plan--featured" : ""}">
            ${p.featured ? `<div class="ps-plan__tag">Most popular</div>` : ""}
            <h3>${esc(p.name)}</h3>
            <div class="ps-plan__price">${esc(p.price)}</div>
            ${has(p.period) ? `<div class="ps-plan__period">${esc(p.period)}</div>` : ""}
            <ul>${(p.features || []).filter(has).map((f) => `<li>${esc(f)}</li>`).join("")}</ul>
            <a class="ps-btn ${p.featured ? "" : "ps-btn--ghost"}" href="#contact">${esc(p.cta || "Choose")}</a>
          </article>`
          )
          .join("")}
      </div>
    </div>
  </section>`;
}

function faqHtml(site) {
  if (!site.faq.enabled) return "";
  const items = (site.faq.items || []).filter((i) => has(i.q));
  if (!items.length) return "";
  return `
  <section class="ps-section ps-faq" id="faq">
    <div class="ps-container ps-faq__inner">
      <div class="ps-section__head ps-reveal"><h2 class="ps-h2">${esc(site.faq.title)}</h2></div>
      <div class="ps-faq__list">
        ${items
          .map(
            (i) => `<details class="ps-faq__item ps-reveal"><summary>${esc(i.q)}</summary><div class="ps-prose">${nl2p(i.a)}</div></details>`
          )
          .join("")}
      </div>
    </div>
  </section>`;
}

function ctaHtml(site) {
  if (!site.cta.enabled || !has(site.cta.title)) return "";
  const c = site.cta;
  return `
  <section class="ps-cta-band">
    <div class="ps-container ps-cta-band__inner ps-reveal">
      <div>
        <h2 class="ps-h2">${esc(c.title)}</h2>
        ${has(c.body) ? `<p>${esc(c.body)}</p>` : ""}
      </div>
      ${has(c.buttonText) ? `<a class="ps-btn ps-btn--lg" href="${attr(safeUrl(c.buttonLink || "#contact"))}">${esc(c.buttonText)}</a>` : ""}
    </div>
  </section>`;
}

function contactHtml(site) {
  if (!site.contact.enabled) return "";
  const c = site.contact;
  const social = Object.entries(c.social || {})
    .filter(([, v]) => has(v))
    .map(
      ([k, v]) =>
        `<a class="ps-social" href="${attr(safeUrl(v))}" target="_blank" rel="noopener" aria-label="${attr(k)}">${socialIcon(k)}</a>`
    )
    .join("");
  const hours = (c.hours || []).filter((h) => has(h.day));
  const details = `
    <ul class="ps-contact__list">
      ${has(c.phone) ? `<li><span>📞</span><a href="tel:${attr(c.phone.replace(/[^\d+]/g, ""))}">${esc(c.phone)}</a></li>` : ""}
      ${has(c.email) ? `<li><span>✉️</span><a href="mailto:${attr(c.email)}">${esc(c.email)}</a></li>` : ""}
      ${has(c.address) ? `<li><span>📍</span><span>${esc(c.address)}</span></li>` : ""}
    </ul>
    ${hours.length ? `<div class="ps-hours"><h4>Opening hours</h4><table>${hours.map((h) => `<tr><td>${esc(h.day)}</td><td>${esc(h.hours)}</td></tr>`).join("")}</table></div>` : ""}
    ${social ? `<div class="ps-socials">${social}</div>` : ""}`;
  const form = c.formEnabled
    ? `<form class="ps-form" onsubmit="return psSubmit(this)">
        <label>Name<input name="name" required></label>
        <label>Email<input name="email" type="email" required></label>
        <label>Phone<input name="phone"></label>
        <label>Message<textarea name="message" rows="4" required></textarea></label>
        <button class="ps-btn ps-btn--lg" type="submit">Send message</button>
        <p class="ps-form__note" data-note></p>
      </form>`
    : "";
  return `
  <section class="ps-section ps-contact" id="contact">
    <div class="ps-container">
      <div class="ps-section__head ps-reveal">
        <h2 class="ps-h2">${esc(c.title)}</h2>
        ${has(c.subtitle) ? `<p class="ps-section__sub">${esc(c.subtitle)}</p>` : ""}
      </div>
      <div class="ps-contact__grid">
        <div class="ps-contact__info ps-reveal">${details}</div>
        <div class="ps-contact__form ps-reveal">${form || mapEmbed(c.mapQuery)}</div>
      </div>
      ${form && has(c.mapQuery) ? `<div class="ps-contact__map ps-reveal">${mapEmbed(c.mapQuery)}</div>` : ""}
    </div>
  </section>`;
}

function socialIcon(k) {
  const map = { facebook: "f", instagram: "◎", twitter: "𝕏", linkedin: "in", tiktok: "♪" };
  return map[k] || "•";
}

function footerHtml(site) {
  const year = "©";
  const text = has(site.footer.text)
    ? esc(site.footer.text)
    : `${year} ${esc(site.meta.businessName)}. All rights reserved.`;
  return `
  <footer class="ps-footer">
    <div class="ps-container ps-footer__row">
      <span>${text}</span>
      ${site.footer.showCredit ? `<span class="ps-credit">Built with PitchSite</span>` : ""}
    </div>
  </footer>`;
}

/* ---------- document ---------- */
export function renderDocument(site) {
  const theme = resolveTheme(site.meta.theme, site.meta.accentColor);
  const css = siteCss(theme);
  const title = has(site.meta.businessName) ? site.meta.businessName : "PitchSite preview";
  const desc = has(site.seo.description) ? site.seo.description : site.hero.subheadline || "";
  const og = has(site.seo.ogImage) ? site.seo.ogImage : site.hero.image || "";
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(title)}</title>
<meta name="description" content="${attr(desc)}">
<meta property="og:title" content="${attr(title)}">
<meta property="og:description" content="${attr(desc)}">
${has(og) ? `<meta property="og:image" content="${attr(safeUrl(og))}">` : ""}
<meta property="og:type" content="website">
<meta name="twitter:card" content="summary_large_image">
<link rel="icon" href="${faviconDataUri(site.meta.favicon || "🌐")}">
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="${attr(theme.fontset.googleHref)}">
<style>${css}</style>
</head>
<body class="${theme.dark ? "ps-dark" : ""}">
<a href="#main" class="ps-skip">Skip to content</a>
${navHtml(site)}
<main id="main">
${heroHtml(site, theme)}
${statsHtml(site)}
${aboutHtml(site)}
${servicesHtml(site)}
${galleryHtml(site)}
${reviewsHtml(site)}
${pricingHtml(site)}
${faqHtml(site)}
${ctaHtml(site)}
${contactHtml(site)}
</main>
${footerHtml(site)}
<script>${siteJs()}</script>
</body>
</html>`;
}

/* ---------- generated-site CSS (themeable) ---------- */
function siteCss(t) {
  return `
:root{
  --accent:${t.accent};--accent2:${t.accent2};--bg:${t.bg};--surface:${t.surface};
  --ink:${t.ink};--muted:${t.muted};--radius:${t.radius};
  --maxw:1140px;--head:${t.fontset.heading};--body:${t.fontset.body};
  --shadow:0 10px 40px -12px rgba(10,12,30,.18);--shadow-sm:0 4px 16px -8px rgba(10,12,30,.25);
  --border:color-mix(in srgb, var(--ink) 10%, transparent);
}
*{box-sizing:border-box;margin:0;padding:0}
html{scroll-behavior:smooth;scroll-padding-top:84px}
.ps-skip{position:absolute;left:-9999px;top:8px;z-index:200;background:var(--accent);color:#fff;padding:10px 16px;border-radius:8px;font-weight:600}
.ps-skip:focus{left:8px}
:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
body{font-family:var(--body);color:var(--ink);background:var(--bg);line-height:1.6;-webkit-font-smoothing:antialiased;overflow-x:hidden}
img{max-width:100%;display:block}
.ps-hero__media img,.ps-about__media img,.ps-gallery__grid img{background:var(--surface)}
a{color:inherit;text-decoration:none}
.ps-container{width:100%;max-width:var(--maxw);margin:0 auto;padding:0 24px}
h1,h2,h3,h4{font-family:var(--head);line-height:1.1;font-weight:800;letter-spacing:-.02em}
.ps-h2{font-size:clamp(1.7rem,3.2vw,2.6rem);margin-bottom:14px}
.ps-eyebrow{text-transform:uppercase;letter-spacing:.12em;font-size:.78rem;font-weight:700;color:var(--accent);margin-bottom:14px}
.ps-section{padding:clamp(56px,8vw,104px) 0}
.ps-section__head{max-width:680px;margin:0 auto clamp(32px,5vw,56px);text-align:center}
.ps-section__sub{color:var(--muted);font-size:1.08rem}
.ps-prose p{margin-bottom:14px;color:var(--muted)}
.ps-prose p:last-child{margin-bottom:0}

/* buttons */
.ps-btn{display:inline-flex;align-items:center;justify-content:center;gap:8px;background:var(--accent);color:#fff;font-weight:700;font-family:var(--body);padding:13px 22px;border-radius:calc(var(--radius) * .7);border:0;cursor:pointer;transition:transform .15s ease,box-shadow .2s ease,filter .2s ease;box-shadow:var(--shadow-sm);font-size:.98rem}
.ps-btn:hover{transform:translateY(-2px);filter:brightness(1.05);box-shadow:var(--shadow)}
.ps-btn--lg{padding:16px 30px;font-size:1.05rem}
.ps-btn--sm{padding:9px 16px;font-size:.9rem}
.ps-btn--ghost{background:transparent;color:var(--ink);border:1.5px solid var(--border);box-shadow:none}
.ps-btn--ghost:hover{border-color:var(--accent);color:var(--accent)}

/* nav */
.ps-nav{position:absolute;top:0;left:0;right:0;z-index:50;padding:14px 0}
.ps-nav--sticky{position:sticky}
.ps-nav.ps-scrolled{background:color-mix(in srgb,var(--bg) 86%,transparent);backdrop-filter:blur(12px);box-shadow:var(--shadow-sm)}
.ps-nav__row{display:flex;align-items:center;justify-content:space-between;gap:20px}
.ps-logo{display:flex;align-items:center;gap:9px;font-family:var(--head);font-weight:800;font-size:1.15rem}
.ps-logo-mark{font-size:1.3rem}
.ps-logo-img{height:38px;width:auto}
.ps-nav__links{display:flex;gap:26px;font-weight:600;font-size:.96rem}
.ps-nav__links a{color:var(--ink);opacity:.82;transition:opacity .2s,color .2s}
.ps-nav__links a:hover{opacity:1;color:var(--accent)}
.ps-nav__actions{display:flex;align-items:center;gap:12px}
.ps-burger{display:none;flex-direction:column;gap:5px;background:none;border:0;cursor:pointer;padding:6px}
.ps-burger span{width:24px;height:2px;background:var(--ink);border-radius:2px;transition:.25s}
.ps-nav__mobile{display:none;flex-direction:column;padding:8px 24px 18px;gap:4px}
.ps-nav__mobile a{padding:12px 8px;border-bottom:1px solid var(--border);font-weight:600}

/* hero */
.ps-hero{position:relative;padding:clamp(120px,15vw,180px) 0 clamp(56px,8vw,96px);overflow:hidden}
.ps-hero__inner{display:grid;grid-template-columns:1.05fr .95fr;gap:clamp(32px,5vw,64px);align-items:center}
.ps-hero--nopic .ps-hero__inner{grid-template-columns:1fr;text-align:center;max-width:820px;margin:0 auto}
.ps-hero--nopic .ps-hero__cta,.ps-hero--nopic .ps-hero__badges{justify-content:center}
.ps-hero__title{font-size:clamp(2.2rem,5.4vw,4rem);margin-bottom:18px}
.ps-hero__sub{font-size:clamp(1.05rem,1.6vw,1.3rem);color:var(--muted);max-width:54ch;margin-bottom:30px}
.ps-hero__cta{display:flex;gap:14px;flex-wrap:wrap;margin-bottom:26px}
.ps-hero__badges{list-style:none;display:flex;flex-wrap:wrap;gap:10px 22px;font-size:.92rem;font-weight:600;color:var(--muted)}
.ps-hero__badges li{display:flex;align-items:center;gap:6px}
.ps-hero__media img{width:100%;border-radius:var(--radius);box-shadow:var(--shadow);aspect-ratio:4/3;object-fit:cover}
.ps-hero--gradient{background:
  radial-gradient(120% 120% at 90% 0%, color-mix(in srgb,var(--accent2) 22%,transparent), transparent 55%),
  radial-gradient(120% 120% at 0% 30%, color-mix(in srgb,var(--accent) 16%,transparent), transparent 50%),var(--bg)}
.ps-hero--glow{background:radial-gradient(120% 90% at 50% -10%, color-mix(in srgb,var(--accent) 30%,transparent), transparent 60%),var(--bg)}
.ps-hero--image::before{content:"";position:absolute;inset:0;background:linear-gradient(180deg,transparent,var(--bg))}

/* stats */
.ps-stats{background:var(--surface);padding:clamp(28px,4vw,40px) 0}
.ps-stats__grid{display:grid;grid-template-columns:repeat(4,1fr);gap:18px;text-align:center}
.ps-stat__value{font-family:var(--head);font-size:clamp(1.7rem,3.2vw,2.6rem);font-weight:800;color:var(--accent);line-height:1}
.ps-stat__label{color:var(--muted);font-size:.92rem;margin-top:6px}

/* about */
.ps-about__grid{display:grid;grid-template-columns:1fr 1fr;gap:clamp(32px,5vw,64px);align-items:center}
.ps-about__grid--solo{grid-template-columns:1fr;max-width:760px;margin:0 auto;text-align:center}
.ps-about__media img{width:100%;border-radius:var(--radius);box-shadow:var(--shadow);aspect-ratio:4/3;object-fit:cover}
.ps-checklist{list-style:none;margin-top:22px;display:grid;gap:12px}
.ps-checklist li{position:relative;padding-left:34px;font-weight:600}
.ps-checklist li::before{content:"✓";position:absolute;left:0;top:-1px;width:22px;height:22px;display:grid;place-items:center;background:color-mix(in srgb,var(--accent) 16%,transparent);color:var(--accent);border-radius:50%;font-size:.8rem;font-weight:800}
.ps-about__grid--solo .ps-checklist{justify-content:center;text-align:left;display:inline-grid}

/* cards */
.ps-services{background:var(--surface)}
.ps-cards{display:grid;grid-template-columns:repeat(3,1fr);gap:22px}
.ps-card{background:var(--bg);border:1px solid var(--border);border-radius:var(--radius);padding:30px 26px;transition:transform .2s ease,box-shadow .2s ease}
.ps-card:hover{transform:translateY(-4px);box-shadow:var(--shadow)}
.ps-card__icon{font-size:1.9rem;width:58px;height:58px;display:grid;place-items:center;background:color-mix(in srgb,var(--accent) 12%,transparent);border-radius:calc(var(--radius) * .7);margin-bottom:18px}
.ps-card__title{font-size:1.2rem;margin-bottom:8px}
.ps-card__text{color:var(--muted);font-size:.98rem}

/* gallery */
.ps-gallery__grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}
.ps-gallery__grid figure{border-radius:var(--radius);overflow:hidden;box-shadow:var(--shadow-sm)}
.ps-gallery__grid img{width:100%;aspect-ratio:1;object-fit:cover;transition:transform .4s ease}
.ps-gallery__grid figure:hover img{transform:scale(1.06)}

/* reviews */
.ps-reviews{background:var(--surface)}
.ps-reviews__grid{display:grid;grid-template-columns:repeat(3,1fr);gap:22px}
.ps-quote{background:var(--bg);border:1px solid var(--border);border-radius:var(--radius);padding:28px}
.ps-quote__stars{color:var(--accent2);letter-spacing:2px;margin-bottom:14px;font-size:1.05rem}
.ps-quote blockquote{font-size:1.05rem;margin-bottom:18px}
.ps-quote figcaption{display:flex;flex-direction:column;font-size:.92rem}
.ps-quote figcaption span{color:var(--muted)}

/* pricing */
.ps-plans{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:22px;max-width:980px;margin:0 auto}
.ps-plan{position:relative;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);padding:32px 26px;text-align:center;display:flex;flex-direction:column;gap:8px}
.ps-plan--featured{border-color:var(--accent);box-shadow:var(--shadow);transform:scale(1.03)}
.ps-plan__tag{position:absolute;top:-13px;left:50%;transform:translateX(-50%);background:var(--accent);color:#fff;font-size:.74rem;font-weight:700;padding:5px 14px;border-radius:99px}
.ps-plan__price{font-family:var(--head);font-size:2.4rem;font-weight:800;color:var(--accent)}
.ps-plan__period{color:var(--muted);font-size:.9rem;margin-top:-6px}
.ps-plan ul{list-style:none;text-align:left;margin:16px 0;display:grid;gap:10px}
.ps-plan li{padding-left:26px;position:relative}
.ps-plan li::before{content:"✓";position:absolute;left:0;color:var(--accent);font-weight:800}
.ps-plan .ps-btn{margin-top:auto}

/* faq */
.ps-faq__inner{max-width:760px}
.ps-faq__list{display:grid;gap:12px}
.ps-faq__item{background:var(--surface);border:1px solid var(--border);border-radius:calc(var(--radius) * .7);padding:4px 22px}
.ps-faq__item summary{cursor:pointer;font-weight:700;padding:18px 0;list-style:none;display:flex;justify-content:space-between;align-items:center}
.ps-faq__item summary::after{content:"+";font-size:1.5rem;color:var(--accent);transition:transform .2s}
.ps-faq__item[open] summary::after{transform:rotate(45deg)}
.ps-faq__item .ps-prose{padding-bottom:18px}

/* cta band */
.ps-cta-band{background:linear-gradient(120deg,var(--accent),color-mix(in srgb,var(--accent) 55%,var(--accent2)));color:#fff;padding:clamp(40px,6vw,72px) 0}
.ps-cta-band__inner{display:flex;align-items:center;justify-content:space-between;gap:28px;flex-wrap:wrap}
.ps-cta-band h2{color:#fff}
.ps-cta-band p{opacity:.92;margin-top:8px;max-width:50ch}
.ps-cta-band .ps-btn{background:#fff;color:var(--accent)}

/* contact */
.ps-contact__grid{display:grid;grid-template-columns:1fr 1.1fr;gap:clamp(28px,5vw,56px)}
.ps-contact__list{list-style:none;display:grid;gap:16px;margin-bottom:26px}
.ps-contact__list li{display:flex;align-items:center;gap:13px;font-size:1.05rem;font-weight:600}
.ps-contact__list a:hover{color:var(--accent)}
.ps-hours h4{margin-bottom:10px}
.ps-hours table{width:100%;max-width:320px;border-collapse:collapse}
.ps-hours td{padding:7px 0;border-bottom:1px solid var(--border);color:var(--muted)}
.ps-hours td:last-child{text-align:right;color:var(--ink);font-weight:600}
.ps-socials{display:flex;gap:10px;margin-top:22px}
.ps-social{width:42px;height:42px;border-radius:50%;display:grid;place-items:center;background:var(--surface);border:1px solid var(--border);font-weight:700;transition:.2s}
.ps-social:hover{background:var(--accent);color:#fff;border-color:var(--accent)}
.ps-form{display:grid;gap:14px;background:var(--surface);padding:28px;border-radius:var(--radius);border:1px solid var(--border)}
.ps-form label{display:grid;gap:6px;font-size:.88rem;font-weight:600}
.ps-form input,.ps-form textarea{font-family:var(--body);font-size:1rem;padding:12px 14px;border:1px solid var(--border);border-radius:calc(var(--radius) * .55);background:var(--bg);color:var(--ink);resize:vertical}
.ps-form input:focus,.ps-form textarea:focus{outline:2px solid var(--accent);border-color:var(--accent)}
.ps-form__note{font-size:.9rem;color:var(--accent);min-height:1.2em}
.ps-map,.ps-contact__map iframe{width:100%;border:0;border-radius:var(--radius);min-height:280px}
.ps-contact__map{margin-top:32px}

/* footer */
.ps-footer{background:var(--surface);padding:30px 0;border-top:1px solid var(--border)}
.ps-footer__row{display:flex;justify-content:space-between;align-items:center;gap:16px;color:var(--muted);font-size:.92rem;flex-wrap:wrap}
.ps-credit{opacity:.7}

/* reveal animation — progressive enhancement only.
   Content is fully visible by default; it is hidden ONLY once JS adds
   .ps-anim, so no-JS / print / pre-scroll states never show blank sections. */
.ps-anim .ps-reveal{opacity:0;transform:translateY(18px);transition:opacity .6s ease,transform .6s ease}
.ps-anim .ps-reveal.ps-in{opacity:1;transform:none}
@media (prefers-reduced-motion:reduce){.ps-anim .ps-reveal{opacity:1;transform:none;transition:none}html{scroll-behavior:auto}}

/* responsive */
@media(max-width:900px){
  .ps-nav__links{display:none}
  .ps-burger{display:flex}
  .ps-nav.ps-open .ps-nav__mobile{display:flex}
  .ps-nav.ps-open{background:var(--bg);box-shadow:var(--shadow-sm)}
  .ps-hero__inner,.ps-about__grid,.ps-contact__grid,.ps-cta-band__inner{grid-template-columns:1fr}
  .ps-hero--haspic .ps-hero__media{order:-1}
  .ps-hero{text-align:center}
  .ps-hero__cta,.ps-hero__badges{justify-content:center}
  .ps-hero__sub{margin-left:auto;margin-right:auto}
  .ps-cards,.ps-reviews__grid,.ps-gallery__grid{grid-template-columns:repeat(2,1fr)}
  .ps-stats__grid{grid-template-columns:repeat(2,1fr);gap:24px}
}
@media(max-width:560px){
  .ps-cards,.ps-reviews__grid,.ps-gallery__grid{grid-template-columns:1fr}
  .ps-container{padding:0 18px}
}`;
}

/* ---------- generated-site JS (tiny, dependency-free) ---------- */
function siteJs() {
  return `
(function(){
  var nav=document.querySelector('.ps-nav');
  var burger=document.querySelector('.ps-burger');
  if(burger){burger.addEventListener('click',function(){
    var open=nav.classList.toggle('ps-open');
    burger.setAttribute('aria-expanded',open);
  });}
  document.querySelectorAll('.ps-nav__mobile a').forEach(function(a){
    a.addEventListener('click',function(){nav.classList.remove('ps-open');burger&&burger.setAttribute('aria-expanded',false);});
  });
  if(nav){var onScroll=function(){nav.classList.toggle('ps-scrolled',window.scrollY>20);};onScroll();window.addEventListener('scroll',onScroll,{passive:true});}
  var reveals=[].slice.call(document.querySelectorAll('.ps-reveal'));
  var revealAll=function(){reveals.forEach(function(el){el.classList.add('ps-in');});};
  if('IntersectionObserver' in window){
    document.documentElement.classList.add('ps-anim');
    var io=new IntersectionObserver(function(es){es.forEach(function(e){if(e.isIntersecting){e.target.classList.add('ps-in');io.unobserve(e.target);}});},{threshold:.08,rootMargin:'0px 0px -6% 0px'});
    reveals.forEach(function(el){io.observe(el);});
    // safety net: never leave content hidden if something goes wrong
    setTimeout(revealAll,2600);
    window.addEventListener('beforeprint',revealAll);
  }
})();
function psSubmit(form){
  var note=form.querySelector('[data-note]');
  var to=document.querySelector('a[href^="mailto:"]');
  note.textContent='Thanks! Your message has been prepared — opening your email app…';
  var email=to?to.getAttribute('href').replace('mailto:',''):'';
  var d=new FormData(form);
  var body=encodeURIComponent('Name: '+(d.get('name')||'')+'\\nEmail: '+(d.get('email')||'')+'\\nPhone: '+(d.get('phone')||'')+'\\n\\n'+(d.get('message')||''));
  if(email){window.location.href='mailto:'+email+'?subject='+encodeURIComponent('Website enquiry')+'&body='+body;}
  return false;
}`;
}
