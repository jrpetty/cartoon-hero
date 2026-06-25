/* PitchSite — application state.
 * The whole project is one serialisable object (`site`). It is the single
 * source of truth for the preview, the exporter and local autosave. */

import { THEME_KEYS } from "./themes.js";
import { SECTION_ORDER } from "./render.js";

const STORAGE_KEY = "pitchsite.project.v1";
const LIST_KEY = "pitchsite.projects.v1";

export function emptySite() {
  return {
    version: 1,
    savedAt: null,
    meta: {
      businessName: "",
      tagline: "",
      industry: "",
      theme: "aurora",
      accentColor: "",
      logoText: "",
      logoUrl: "",
      favicon: "🌐",
    },
    nav: { enabled: true, ctaText: "Get in touch", sticky: true },
    announce: { enabled: false, text: "", linkText: "", link: "" },
    hero: {
      eyebrow: "",
      headline: "",
      subheadline: "",
      ctaText: "Get a free quote",
      ctaLink: "#contact",
      secondaryText: "",
      secondaryLink: "",
      image: "",
      video: "",
      badges: [],
    },
    trust: { enabled: false, title: "Trusted by", logos: [] },
    about: {
      enabled: true,
      title: "About us",
      body: "",
      image: "",
      points: [],
    },
    stats: { enabled: false, items: [] },
    process: { enabled: false, title: "How it works", subtitle: "", steps: [] },
    services: { enabled: true, title: "What we do", subtitle: "", items: [] },
    beforeafter: { enabled: false, title: "See the difference", subtitle: "", pairs: [] },
    gallery: { enabled: false, title: "Our work", images: [] },
    video: { enabled: false, title: "Take a look", subtitle: "", url: "" },
    team: { enabled: false, title: "Meet the team", subtitle: "", members: [] },
    reviews: { enabled: true, title: "What our customers say", items: [] },
    pricing: { enabled: false, title: "Pricing", subtitle: "", plans: [] },
    faq: { enabled: false, title: "Frequently asked questions", items: [] },
    cta: {
      enabled: true,
      title: "Ready to get started?",
      body: "",
      buttonText: "Contact us today",
      buttonLink: "#contact",
    },
    contact: {
      enabled: true,
      title: "Get in touch",
      subtitle: "",
      phone: "",
      email: "",
      address: "",
      hours: [],
      mapQuery: "",
      formEnabled: true,
      bookingUrl: "",
      social: { facebook: "", instagram: "", twitter: "", linkedin: "", tiktok: "" },
    },
    floating: { enabled: false, whatsapp: "", call: false, backToTop: true },
    consent: { enabled: false, text: "We use cookies to improve your experience.", buttonText: "Got it" },
    integrations: { ga4: "", metaPixel: "" },
    layout: { order: [...SECTION_ORDER] },
    footer: { text: "", showCredit: true },
    seo: { description: "", keywords: "", ogImage: "" },
  };
}

/* A polished default so a brand-new builder never looks empty. */
export function sampleSite() {
  const s = emptySite();
  s.meta.businessName = "Northside Plumbing & Heating";
  s.meta.tagline = "Fast, reliable plumbers — 24/7";
  s.meta.industry = "plumbing";
  s.meta.theme = "coral";
  s.meta.favicon = "🔧";
  s.hero.eyebrow = "Trusted local plumbers since 2009";
  s.hero.headline = "Leaky tap or burst pipe? We're on our way.";
  s.hero.subheadline =
    "Same-day callouts across the city, fixed pricing with no surprises, and a 12-month guarantee on every job.";
  s.hero.ctaText = "Book a callout";
  s.hero.secondaryText = "Call 0800 123 456";
  s.hero.secondaryLink = "tel:0800123456";
  s.hero.image =
    "https://images.unsplash.com/photo-1607472586893-edb57bdc0e39?auto=format&fit=crop&w=1400&q=80";
  s.hero.badges = ["Gas Safe registered", "12-month guarantee", "No call-out fee"];
  s.stats.enabled = true;
  s.stats.items = [
    { value: "15+", label: "Years experience" },
    { value: "8,000+", label: "Jobs completed" },
    { value: "4.9★", label: "Average rating" },
    { value: "60min", label: "Average response" },
  ];
  s.about.body =
    "We're a family-run team of fully qualified, Gas Safe registered engineers. From a dripping tap to a full bathroom installation, we turn up on time, tidy up after ourselves, and never leave a job until you're happy.";
  s.about.points = [
    "Upfront, fixed pricing — quoted before we start",
    "Friendly, uniformed, background-checked engineers",
    "Fully insured and guaranteed workmanship",
  ];
  s.about.image =
    "https://images.unsplash.com/photo-1581244277943-fe4a9c777189?auto=format&fit=crop&w=1100&q=80";
  s.services.items = [
    { icon: "🚿", title: "Emergency repairs", description: "Burst pipes, leaks and blockages fixed fast, day or night." },
    { icon: "🔥", title: "Boilers & heating", description: "Servicing, repairs and new installations from top brands." },
    { icon: "🛁", title: "Bathroom fitting", description: "Full design and installation, managed end to end." },
    { icon: "🚰", title: "Taps & fixtures", description: "Replacements and upgrades with no mess left behind." },
    { icon: "🌡️", title: "Annual servicing", description: "Keep everything running safely with a yearly check." },
    { icon: "📋", title: "Landlord certificates", description: "Gas safety checks and certificates, sorted same week." },
  ];
  s.reviews.items = [
    { quote: "Came out within the hour and fixed our burst pipe. Polite, tidy and fairly priced. Couldn't ask for more.", author: "Sarah M.", role: "Homeowner", rating: 5 },
    { quote: "Replaced our old boiler in a day and explained everything clearly. Highly recommend.", author: "James T.", role: "Local resident", rating: 5 },
    { quote: "Honest and reliable — they told me a repair would do rather than selling me a new unit.", author: "Priya K.", role: "Landlord", rating: 5 },
  ];
  s.contact.phone = "0800 123 456";
  s.contact.email = "hello@northsideplumbing.co.uk";
  s.contact.address = "Unit 4, Riverside Trade Park, City";
  s.contact.mapQuery = "Riverside Trade Park";
  s.contact.hours = [
    { day: "Mon–Fri", hours: "7am – 8pm" },
    { day: "Saturday", hours: "8am – 6pm" },
    { day: "Sunday", hours: "Emergencies only" },
  ];
  s.cta.body = "Tell us what's wrong and we'll give you a clear, fixed price — usually within the hour.";
  s.announce.enabled = true;
  s.announce.text = "❄️ Winter boiler service offer — 15% off this month";
  s.announce.linkText = "Claim offer";
  s.announce.link = "#contact";
  s.process.enabled = true;
  s.process.subtitle = "Getting sorted couldn't be simpler.";
  s.process.steps = [
    { title: "Call or message", description: "Tell us what's wrong — we'll give honest advice straight away." },
    { title: "Fixed-price quote", description: "A clear price agreed before any work begins. No surprises." },
    { title: "We fix it", description: "On time, tidy, and guaranteed for 12 months." },
  ];
  s.beforeafter.enabled = true;
  s.beforeafter.subtitle = "Drag the slider to see our work.";
  s.beforeafter.pairs = [
    {
      label: "Bathroom renovation",
      before: "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=900&q=80",
      after: "https://images.unsplash.com/photo-1620626011761-996317b8d101?auto=format&fit=crop&w=900&q=80",
    },
  ];
  s.floating.enabled = true;
  s.floating.whatsapp = "447700900123";
  s.floating.call = true;
  s.floating.backToTop = true;
  s.footer.text = "";
  return s;
}

/* Migrate / harden any loaded object so older or partial saves never crash
 * the renderer. Deep-merges onto a fresh empty site. */
export function normalize(obj) {
  const base = emptySite();
  const merged = deepMerge(base, obj || {});
  if (!THEME_KEYS.includes(merged.meta.theme)) merged.meta.theme = "aurora";
  // reconcile section order: keep known/unique, append any missing
  const seen = [];
  for (const k of merged.layout.order || []) if (SECTION_ORDER.includes(k) && !seen.includes(k)) seen.push(k);
  for (const k of SECTION_ORDER) if (!seen.includes(k)) seen.push(k);
  merged.layout.order = seen;
  return merged;
}

function deepMerge(target, src) {
  if (Array.isArray(target)) return Array.isArray(src) ? src : target;
  if (typeof target === "object" && target !== null) {
    const out = { ...target };
    for (const k of Object.keys(target)) {
      if (src && k in src) out[k] = deepMerge(target[k], src[k]);
    }
    // preserve unknown keys from src too (forward-compat)
    if (src) for (const k of Object.keys(src)) if (!(k in out)) out[k] = src[k];
    return out;
  }
  return src === undefined ? target : src;
}

export function saveLocal(site) {
  site.savedAt = new Date().toISOString();
  localStorage.setItem(STORAGE_KEY, JSON.stringify(site));
}

export function loadLocal() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    return normalize(JSON.parse(raw));
  } catch {
    return null;
  }
}

/* Named project library (save / load multiple client drafts). */
export function listProjects() {
  try {
    return JSON.parse(localStorage.getItem(LIST_KEY) || "[]");
  } catch {
    return [];
  }
}

export function saveProjectAs(name, site) {
  const projects = listProjects().filter((p) => p.name !== name);
  projects.unshift({ name, savedAt: new Date().toISOString(), site });
  localStorage.setItem(LIST_KEY, JSON.stringify(projects.slice(0, 30)));
}

export function deleteProject(name) {
  localStorage.setItem(
    LIST_KEY,
    JSON.stringify(listProjects().filter((p) => p.name !== name))
  );
}
