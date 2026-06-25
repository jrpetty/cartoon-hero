/* PitchSite — local prompt → site generator.
 * Works with zero backend. Given a free-text brief typed during a sales
 * call ("a cosy family Italian restaurant in Soho, open since 1985, wants
 * to look warm; phone 020 123 4567"), it infers the industry, tone, name
 * and details and produces a complete, editable site draft.
 *
 * When the backend (Claude) is available, ai.js prefers it and this becomes
 * the offline fallback — but the output here is good enough to demo live. */

import { emptySite } from "./state.js";

const INDUSTRIES = {
  plumbing: {
    match: ["plumb", "heating", "boiler", "gas", "drain"],
    theme: "coral",
    favicon: "🔧",
    services: [
      ["🚿", "Emergency repairs", "Burst pipes, leaks and blockages fixed fast, day or night."],
      ["🔥", "Boilers & heating", "Servicing, repairs and new installations."],
      ["🛁", "Bathroom fitting", "Full design and installation, managed end to end."],
      ["🌡️", "Annual servicing", "Keep everything running safely with a yearly check."],
    ],
    badges: ["Fully insured", "12-month guarantee", "No call-out fee"],
    hero: "Leaky tap or burst pipe? We're on our way.",
    sub: "Same-day callouts, fixed pricing with no surprises, and a guarantee on every job.",
  },
  restaurant: {
    match: ["restaurant", "cafe", "café", "bistro", "eatery", "diner", "pizz", "italian", "kitchen", "food", "coffee", "bar "],
    theme: "linen",
    favicon: "🍽️",
    services: [
      ["🍝", "Fresh menu", "Seasonal dishes made from scratch every day."],
      ["🥂", "Private events", "Celebrations, parties and set menus for groups."],
      ["🥡", "Takeaway & delivery", "Your favourites, ready to collect or delivered."],
      ["☕", "All-day dining", "From morning coffee to a late evening table."],
    ],
    badges: ["Locally sourced", "Booking recommended", "Family run"],
    hero: "Honest food, made with love.",
    sub: "A warm welcome, a seasonal menu and a table that feels like home.",
  },
  salon: {
    match: ["salon", "hair", "barber", "beauty", "nails", "spa", "lash", "aesthetic"],
    theme: "aurora",
    favicon: "💇",
    services: [
      ["✂️", "Cuts & styling", "Tailored cuts and finishes for every look."],
      ["🎨", "Colour", "Balayage, highlights and full colour by experts."],
      ["💅", "Treatments", "Nourishing treatments to keep you looking your best."],
      ["💍", "Occasions", "Wedding and event styling, booked in advance."],
    ],
    badges: ["Walk-ins welcome", "Award-winning team", "Easy online booking"],
    hero: "Look good. Feel even better.",
    sub: "Expert stylists, a relaxed space, and a finish you'll love.",
  },
  fitness: {
    match: ["gym", "fitness", "personal train", "yoga", "pilates", "crossfit", "studio", "coach"],
    theme: "midnight",
    favicon: "💪",
    services: [
      ["🏋️", "Personal training", "One-to-one sessions built around your goals."],
      ["🧘", "Group classes", "Energising classes for every level."],
      ["📈", "Progress coaching", "Plans, check-ins and accountability that work."],
      ["🥗", "Nutrition", "Simple, sustainable guidance that fits your life."],
    ],
    badges: ["First class free", "Certified coaches", "All levels welcome"],
    hero: "Stronger starts here.",
    sub: "Expert coaching and a community that keeps you coming back.",
  },
  trades: {
    match: ["electric", "build", "construct", "carpenter", "roof", "paint", "decorat", "landscap", "garden", "handyman", "joiner", "tiler", "plaster"],
    theme: "emerald",
    favicon: "🛠️",
    services: [
      ["🔨", "Quality workmanship", "Done properly, on time, and tidied up after."],
      ["📐", "Free quotes", "Clear, fixed pricing before any work begins."],
      ["🏠", "Big or small", "From quick fixes to full projects."],
      ["✅", "Guaranteed", "Fully insured with workmanship you can trust."],
    ],
    badges: ["Free quotes", "Fully insured", "References available"],
    hero: "Quality work you can rely on.",
    sub: "Trusted local tradespeople doing the job right, first time.",
  },
  professional: {
    match: ["account", "law", "solicitor", "consult", "agency", "financ", "advisor", "estate", "insur", "mortgage", "recruit", "marketing"],
    theme: "slate",
    favicon: "💼",
    services: [
      ["📊", "Expert advice", "Clear guidance tailored to your situation."],
      ["🤝", "Personal service", "A dedicated point of contact who knows you."],
      ["🛡️", "Trusted & compliant", "Fully regulated and acting in your interest."],
      ["⏱️", "Responsive", "Quick answers when you need them most."],
    ],
    badges: ["Free consultation", "Fully regulated", "Trusted advisors"],
    hero: "Expert advice you can trust.",
    sub: "Practical, personal guidance to help you make confident decisions.",
  },
  health: {
    match: ["dental", "dentist", "clinic", "physio", "chiro", "therap", "medical", "health", "doctor", "optician", "vet"],
    theme: "emerald",
    favicon: "🩺",
    services: [
      ["🦷", "Expert care", "Gentle, modern treatment in a calm setting."],
      ["📅", "Easy appointments", "Flexible times that fit around your life."],
      ["💬", "Clear advice", "We explain your options, with no pressure."],
      ["🛡️", "Trusted team", "Qualified, caring and fully registered."],
    ],
    badges: ["New patients welcome", "Registered practice", "Flexible hours"],
    hero: "Caring treatment you can trust.",
    sub: "A friendly, expert team focused on you and your wellbeing.",
  },
  retail: {
    match: ["shop", "store", "boutique", "retail", "florist", "bakery", "gift", "jewel"],
    theme: "linen",
    favicon: "🛍️",
    services: [
      ["✨", "Curated range", "Hand-picked products you won't find everywhere."],
      ["🎁", "Perfect gifts", "Something special for every occasion."],
      ["🚚", "Local delivery", "Order today and we'll bring it to your door."],
      ["💬", "Friendly help", "Honest advice from people who know their stuff."],
    ],
    badges: ["Locally loved", "Gift wrapping", "Local delivery"],
    hero: "Something special, just for you.",
    sub: "A carefully chosen collection and a warm welcome every time.",
  },
};

const GENERIC = {
  theme: "aurora",
  favicon: "⭐",
  services: [
    ["⚡", "What we offer", "A service built around exactly what you need."],
    ["🤝", "Why choose us", "Reliable, friendly and focused on results."],
    ["✅", "Our promise", "Quality work and a service you can count on."],
    ["💬", "Get in touch", "Tell us what you need and we'll take it from there."],
  ],
  badges: ["Trusted locally", "Friendly service", "Free quote"],
  hero: "Welcome to {name}.",
  sub: "Quality service, friendly people, and results you can count on.",
};

const SAMPLE_REVIEWS = [
  ["Absolutely brilliant from start to finish. Professional, friendly and great value. Highly recommend!", "Sarah M.", "Verified customer"],
  ["Couldn't be happier with the service. They went above and beyond and I'll definitely be back.", "James T.", "Local resident"],
  ["First class all the way. Clear communication and a fantastic result. Five stars.", "Priya K.", "Happy client"],
];

function pickIndustry(text) {
  const t = text.toLowerCase();
  let best = null;
  let bestScore = 0;
  for (const [key, def] of Object.entries(INDUSTRIES)) {
    const score = def.match.reduce((n, w) => (t.includes(w) ? n + 1 : n), 0);
    if (score > bestScore) {
      bestScore = score;
      best = key;
    }
  }
  return best ? { key: best, def: INDUSTRIES[best] } : { key: "generic", def: GENERIC };
}

function detectTone(text) {
  const t = text.toLowerCase();
  if (/(luxur|premium|high.end|elegant|upscale|exclusive)/.test(t)) return "premium";
  if (/(fun|friendly|warm|cosy|cozy|family|playful|welcoming)/.test(t)) return "friendly";
  if (/(modern|tech|startup|innovat|cutting.edge|sleek)/.test(t)) return "modern";
  if (/(trust|professional|reliable|established|expert)/.test(t)) return "professional";
  return null;
}

function guessName(text) {
  // explicit: "called X", "named X", "business X is", quoted "X"
  const quoted = text.match(/["'“”]([^"'“”]{2,40})["'“”]/);
  if (quoted) return clean(quoted[1]);
  const called = text.match(/\b(?:called|named|business is|company is|it's called|its called)\s+([A-Z0-9][\w&'’.-]*(?:\s+[A-Z0-9][\w&'’.-]*){0,4})/);
  if (called) return clean(called[1]);
  return "";
}

function extractContacts(text) {
  const out = {};
  const email = text.match(/[\w.+-]+@[\w-]+\.[\w.-]+/);
  if (email) out.email = email[0];
  const phone = text.match(/(?:\+?\d[\d\s().-]{7,}\d)/);
  if (phone) out.phone = phone[0].trim();
  const urls = [...text.matchAll(/https?:\/\/[^\s,]+/g)].map((m) => m[0]);
  out.images = urls.filter((u) => /\.(png|jpe?g|webp|gif|avif)(\?|$)/i.test(u) || /unsplash|cloudinary|imgur|images/.test(u));
  return out;
}

function clean(s) {
  return s.replace(/\s+/g, " ").trim();
}

function titleCase(s) {
  return s.replace(/\b\w/g, (c) => c.toUpperCase());
}

const THEME_FOR_TONE = {
  premium: "midnight",
  friendly: "coral",
  modern: "aurora",
  professional: "slate",
};

/* Main entry: returns a fully-populated `site` object. */
export function generateLocally(prompt) {
  const text = clean(prompt || "");
  const site = emptySite();
  const { def } = pickIndustry(text);
  const tone = detectTone(text);
  const name = guessName(text) || "Your Business";
  const contacts = extractContacts(text);

  site.meta.businessName = name;
  site.meta.industry = "";
  site.meta.favicon = def.favicon;
  site.meta.theme = (tone && THEME_FOR_TONE[tone]) || def.theme;

  const heroLine = def.hero.replace("{name}", name);
  site.hero.eyebrow = def.badges[0] || "";
  site.hero.headline = heroLine;
  site.hero.subheadline = def.sub;
  site.hero.ctaText = "Get in touch";
  site.hero.badges = def.badges.slice();
  if (contacts.phone) {
    site.hero.secondaryText = "Call " + contacts.phone;
    site.hero.secondaryLink = "tel:" + contacts.phone.replace(/[^\d+]/g, "");
  }
  if (contacts.images && contacts.images.length) {
    site.hero.image = contacts.images[0];
    if (contacts.images.length > 1) {
      site.gallery.enabled = true;
      site.gallery.images = contacts.images.slice(1, 7);
    }
  }

  site.about.body =
    `At ${name}, we pride ourselves on doing things properly. ` +
    `${def.sub} We're local, we listen, and we treat every customer the way we'd want to be treated.`;
  site.about.points = def.badges.map((b) => b);

  site.services.items = def.services.map(([icon, title, description]) => ({ icon, title, description }));

  site.reviews.items = SAMPLE_REVIEWS.map(([quote, author, role]) => ({ quote, author, role, rating: 5 }));

  site.stats.enabled = true;
  site.stats.items = [
    { value: "100%", label: "Recommend us" },
    { value: "5★", label: "Average rating" },
    { value: "Local", label: "& trusted" },
    { value: "Fast", label: "response" },
  ];

  if (contacts.phone) site.contact.phone = contacts.phone;
  if (contacts.email) site.contact.email = contacts.email;
  site.cta.title = `Ready to work with ${name}?`;
  site.cta.body = "Get in touch today for a friendly chat and a free, no-obligation quote.";

  site.seo.description = `${name} — ${def.sub}`;
  site._notes = buildNotes(name, def, tone, contacts);
  return site;
}

function buildNotes(name, def, tone, contacts) {
  const found = [];
  found.push(`Detected business name: ${name}`);
  found.push(`Tone: ${tone || "balanced"}`);
  if (contacts.phone) found.push(`Phone: ${contacts.phone}`);
  if (contacts.email) found.push(`Email: ${contacts.email}`);
  if (contacts.images && contacts.images.length) found.push(`${contacts.images.length} image link(s) added`);
  found.push("Drafted offline — edit anything on the left, it updates instantly.");
  return found;
}
