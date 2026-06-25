---
name: company-to-website
description: >-
  Research a real company online (its website, Google Business listing, reviews
  and socials), gather the facts needed to build a marketing site, and output a
  ready-to-use PitchSite project (an importable .pitchsite.json) plus a sales-
  call brief. Use when the user names a business and wants its details pulled
  together for a website — e.g. "look up <company> and make me a site",
  "research this business for a website", "inspect <company> on Google and
  prep the site", "gather info on <company> so I can build their site". Pairs
  with the PitchSite builder in this repo.
allowed-tools: WebSearch, WebFetch, Write, Read, Glob
---

# company-to-website — research a business, output website-ready data

Goal: turn a company name into accurate, structured, **website-ready** content
that imports straight into PitchSite. Two deliverables every time:
1. `<slug>.pitchsite.json` — an importable PitchSite project (primary).
2. A short **brief** (paste-able into PitchSite's prompt box as an alternative).
Plus a **sources list** and a **confidence/gaps note**.

## 0. Inputs & disambiguation
Take: company name, and (ideally) town/city, postcode, or website URL. Many
businesses share names — if the name is ambiguous and no locating detail was
given, do ONE quick search; if a single clear match emerges, proceed and state
which one you picked. If several plausible matches remain, ask one targeted
question (location or website) before spending effort. Otherwise don't block.

## 1. Research (use WebSearch + WebFetch; cite every source)
Gather, in roughly this order:
- **Official website** — find it, then WebFetch the home, about, services and
  contact pages. This is your most reliable source; prefer it over aggregators.
- **Google Business / maps listing** — category (industry), full address,
  phone, opening hours, star rating and review count.
- **Reviews** — real customer quotes with first name + initial from Google,
  Facebook, Trustpilot, Yelp etc. Capture the rating too.
- **Services / products**, key selling points, accreditations, "years
  established", service area.
- **Socials** — Facebook, Instagram, X, LinkedIn, TikTok URLs.
- **Imagery** — hero/logo/work photo URLs that live on their own site (only use
  URLs you actually found; never invent image links).

Rules:
- **Never fabricate facts.** Phone, email, address, hours, rating, and review
  quotes must come from a real source you found. If you can't find one, leave it
  blank rather than guessing.
- Keep a running **sources** list (URL per fact group).
- Note the date; listings change.

## 2. Map findings → PitchSite schema
Output a JSON object using the fields below. It does **not** need every key —
PitchSite's `normalize()` fills defaults — but populate everything you have
evidence for. Keep arrays realistic in length.

```jsonc
{
  "meta": { "businessName": "", "tagline": "", "industry": "",
            "theme": "aurora|midnight|linen|emerald|coral|slate",
            "favicon": "single emoji", "logoUrl": "" },
  "hero": { "eyebrow": "", "headline": "", "subheadline": "",
             "ctaText": "Get in touch", "image": "<photo URL if found>",
             "secondaryText": "Call <phone>", "secondaryLink": "tel:<phone>",
             "badges": ["USP 1","USP 2","USP 3"] },
  "stats": { "enabled": true, "items": [ {"value":"","label":""} ] },
  "about": { "title": "About us", "body": "", "image": "", "points": [] },
  "process": { "enabled": false, "title": "How it works", "steps": [ {"title":"","description":""} ] },
  "services": { "title": "What we do",
                 "items": [ {"icon":"emoji","title":"","description":""} ] },
  "gallery": { "enabled": false, "title": "Our work", "images": [] },
  "reviews": { "title": "What our customers say",
                "items": [ {"quote":"","author":"","role":"","rating":5} ] },
  "faq": { "enabled": false, "title": "FAQ", "items": [ {"q":"","a":""} ] },
  "contact": { "phone": "", "email": "", "address": "",
                "mapQuery": "<address or place>",
                "hours": [ {"day":"Mon–Fri","hours":"9am – 5pm"} ],
                "bookingUrl": "",
                "social": {"facebook":"","instagram":"","twitter":"","linkedin":"","tiktok":""} },
  "floating": { "enabled": true, "whatsapp": "<digits only, with country code>", "call": true },
  "seo": { "description": "" }
}
```

**Theme guide:** `coral` = friendly local / trades; `linen` = warm hospitality /
retail; `midnight` = premium / dark / fitness / luxury; `aurora` = modern
startup / services; `slate` = corporate / professional; `emerald` = trustworthy
health / finance / trades. Pick the single best fit for the business.

**Copywriting:** write benefit-led headlines and service descriptions in the
business's own voice and region's spelling — but base every claim on what you
found (don't promise "24/7" or "free quotes" unless the source says so). Set
`floating.whatsapp` and `contact` from the real phone; build `mapQuery` from the
real address.

## 3. Reviews — real vs placeholder (be explicit)
- If you found **real** reviews: use the genuine quotes, first name + initial,
  and the real rating.
- If you found **none**: you may include up to 3 clearly-marked *placeholder*
  testimonials so the page isn't empty, but you MUST list them under "Gaps"
  and tell the user to replace them with real ones before publishing. Never
  present invented reviews as real.

## 4. Output
1. **Write** the project to `<slug>.pitchsite.json` (slug = kebab-cased name).
   Validate it is parseable JSON before finishing.
2. **Validate against PitchSite** if the repo is available: render it to catch
   errors, e.g.
   `node -e "Promise.all([import('./web/js/render.js'),import('./web/js/state.js')]).then(([r,s])=>{const o=JSON.parse(require('fs').readFileSync('<slug>.pitchsite.json'));const d=r.renderDocument(s.normalize(o));if(!d.includes('</html>'))throw new Error('render failed');console.log('valid, '+d.length+' bytes');})"`
3. Give the user the **brief** (3–5 sentences capturing name, trade, tone,
   phone, standout points) as a paste-able alternative.
4. Print the **sources** (URLs) and a **confidence/gaps** note: which fields are
   verified, which are assumed, and what's a placeholder needing replacement.

## 5. How the user uses it
Tell them: open the PitchSite builder → **Export ▾ → "Import project file…"** →
pick `<slug>.pitchsite.json`. The researched site loads ready to tweak, preview
and publish. (Or paste the brief into the prompt box and hit **Generate**.)

## Don'ts
- Don't invent phone numbers, emails, addresses, hours, ratings, or reviews.
- Don't scrape behind logins or present aggregator guesses as official facts.
- Don't claim certifications/offers the sources don't support.
- Don't output invalid JSON or skip the sources/gaps note.
