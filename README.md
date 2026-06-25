# PitchSite ⚡

**Build a client's website live, on the sales call.**

PitchSite turns a few sentences typed during a call — _"family-run Italian
restaurant in Soho, open since 1985, warm and traditional, phone 020 7123
4567"_ — into a clean, ready-to-ship one-page website you can show, tweak and
hand over before you hang up. Paste a brief, link or upload photos, pick a
theme, and export a single self-contained `.html` file.

It's the workflow a freelancer or agency needs to **close website deals in one
call**: prospect describes their business, you generate the site in front of
them, adjust live, and send it over.

---

## Why it's built this way

- **Works with zero setup, even offline.** The whole builder is plain
  HTML/CSS/JS — no build step, no framework. Open `web/index.html` and it runs.
  An in-browser generator drafts the site from your brief with no internet, so a
  dropped signal on a call never blocks you.
- **Real AI when you want it.** Run the optional FastAPI backend with an
  `ANTHROPIC_API_KEY` and the brief is turned into polished, bespoke copy by
  Claude. Without a key, it transparently falls back to the offline generator.
- **WYSIWYG, truly.** The live preview is the _exact_ document you export — the
  renderer is one pure function used by both.
- **Self-contained exports.** Uploaded images are embedded; the downloaded
  `.html` is a single portable file you can email or drop on any host.

## Features

- 🎤 **Prompt → site** from free-text sales-call notes (detects trade, tone,
  name, phone, email and image links).
- 🎨 **6 hand-tuned themes** + custom accent colour, with live device preview
  (desktop / tablet / mobile).
- 🧩 **Full section kit**: hero, stats, about, services, gallery, reviews,
  pricing, FAQ, CTA band, contact (with map, opening hours, social, form), SEO.
- 🖼️ **Image upload _or_ URL** — uploads are embedded as data URIs so exports
  stay portable.
- 🌐 **Publish to a live link + QR code** (backend): one click mints a
  shareable URL and a scannable QR so the client can open the site on their
  phone _during the call_. Re-publishing keeps the same link.
- 📢 **Conversion kit**: announcement bar, trust/logos bar, "how it works"
  steps, before/after slider, video, team, floating WhatsApp/call buttons,
  cookie-consent banner, Calendly booking button, GA4 + Meta Pixel.
- 💾 **Save / open / import / export** projects (local library + JSON files).
- 📤 **Export**: download `.html`, open full preview in a new tab, or copy HTML.
- ♿ **Accessible & robust**: semantic markup, skip link, keyboard-friendly
  editor, reveal animations that degrade gracefully (no blank sections without
  JS), and all user content HTML-escaped.

## Quick start

### Just the builder (no backend)

Open `web/index.html` in a browser, or serve the folder:

```bash
cd web && python3 -m http.server 5173
# visit http://127.0.0.1:5173
```

### With the Claude-powered backend

```bash
cd backend
pip install -r requirements.txt
export ANTHROPIC_API_KEY=sk-ant-...      # optional — omit for offline mode
python app.py                            # serves the builder + API on :8000
# visit http://127.0.0.1:8000
```

The status chip in the top bar shows whether Claude AI is connected, the
backend is up without a key, or you're in offline mode.

Configure the model with `PITCHSITE_MODEL` (default `claude-sonnet-4-6`).

## How a call goes

1. Type or paste what the prospect tells you into the prompt box → **Generate**.
2. The site appears in the live preview. Refine any field on the left.
3. Upload their logo and a few photos, switch theme/accent to taste.
4. **Export → Publish** to mint a live link + QR — the client scans it and
   sees their site on their own phone, on the call. Then **Download
   website (.html)** or re-publish edits to the same link afterwards.

## Project layout

```
web/                 # the builder (static, no build step)
  index.html
  css/app.css        # builder UI
  js/
    themes.js        # theme catalogue (palettes + fonts)
    state.js         # data model, sample, persistence
    generator.js     # offline prompt → site generator
    render.js        # site object → standalone HTML document (the engine)
    export.js        # download / copy / open-in-tab
    ai.js            # backend-or-local generation bridge
    ui.js            # editor form + live preview controller
    main.js          # bootstrap
backend/             # optional FastAPI + Claude generation
  app.py             # serves the builder + /api endpoints
  generator.py       # Claude prompt + JSON extraction
  requirements.txt
tests/
  smoke.mjs          # Node test of the pure render/generate engine
  test_app.py        # FastAPI endpoint tests
```

## Testing

```bash
node tests/smoke.mjs                 # front-end engine (render, generate, XSS, themes)
python3 -m pytest tests/test_app.py  # backend API
```

## Roadmap ideas

Custom domains / one-click publish, more section types (team, process, logos
bar), undo/redo history, and direct hand-off (emailing the export to the client
from the call).
