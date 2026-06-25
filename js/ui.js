/* PitchSite — builder UI controller.
 * Owns the editor form, the live preview and all top-bar actions. */

import {
  emptySite, sampleSite, normalize, saveLocal, loadLocal,
  listProjects, saveProjectAs, deleteProject,
} from "./state.js";
import { THEMES, THEME_KEYS, resolveTheme } from "./themes.js";
import { renderDocument, SECTION_LABELS } from "./render.js";
import { generate, probeBackend, getHealth } from "./ai.js";
import { STOCK, STOCK_KEYS, suggestCategory, photosFor } from "./stock.js";
import { exportHtml, exportProject, openPreview, copyHtml, publishSite } from "./export.js";

let site = sampleSite();
let device = "desktop";
let previewTimer = null;
let snapTimer = null;

/* ---------- undo / redo history ---------- */
let history = [];
let hidx = -1;

function snapshotNow() {
  const json = JSON.stringify(site);
  if (history[hidx] === json) return;
  history = history.slice(0, hidx + 1);
  history.push(json);
  if (history.length > 80) history.shift();
  hidx = history.length - 1;
  updateHistoryButtons();
}
function scheduleSnapshot() {
  clearTimeout(snapTimer);
  snapTimer = setTimeout(snapshotNow, 600);
}
function resetHistory() {
  history = [JSON.stringify(site)];
  hidx = 0;
  updateHistoryButtons();
}
function undo() {
  clearTimeout(snapTimer);
  if (hidx <= 0) return;
  hidx--;
  site = normalize(JSON.parse(history[hidx]));
  refreshAll();
  saveLocal(site);
  updateHistoryButtons();
  toast("Undo");
}
function redo() {
  clearTimeout(snapTimer);
  if (hidx >= history.length - 1) return;
  hidx++;
  site = normalize(JSON.parse(history[hidx]));
  refreshAll();
  saveLocal(site);
  updateHistoryButtons();
  toast("Redo");
}
function updateHistoryButtons() {
  const u = document.getElementById("act-undo");
  const r = document.getElementById("act-redo");
  if (u) u.disabled = hidx <= 0;
  if (r) r.disabled = hidx >= history.length - 1;
}

/* ---------- tiny DOM helper ---------- */
function el(tag, props = {}, kids = []) {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === "class") n.className = v;
    else if (k === "html") n.innerHTML = v;
    else if (k === "text") n.textContent = v;
    else if (k.startsWith("on") && typeof v === "function") n.addEventListener(k.slice(2), v);
    else if (v === true) n.setAttribute(k, "");
    else if (v !== false && v != null) n.setAttribute(k, v);
  }
  (Array.isArray(kids) ? kids : [kids]).forEach((c) => {
    if (c == null) return;
    n.appendChild(typeof c === "string" ? document.createTextNode(c) : c);
  });
  return n;
}

/* ---------- path get/set ---------- */
const get = (path) => path.split(".").reduce((o, k) => (o == null ? o : o[k]), site);
function set(path, val) {
  const keys = path.split(".");
  let o = site;
  for (let i = 0; i < keys.length - 1; i++) o = o[keys[i]];
  o[keys[keys.length - 1]] = val;
}

/* ---------- field builders ---------- */
function textField(label, path, { type = "text", placeholder = "", area = false, rows = 3 } = {}) {
  const input = area
    ? el("textarea", { rows, placeholder })
    : el("input", { type, placeholder });
  input.value = get(path) ?? "";
  input.addEventListener("input", () => { set(path, input.value); schedulePreview(); });
  return el("div", { class: "field" }, [el("label", { text: label }), input]);
}

function rowFields(...fields) {
  return el("div", { class: fields.length === 3 ? "field--row3" : "field--row" }, fields);
}

const MAX_UPLOAD = 4 * 1024 * 1024; // 4MB guard — keeps exported files sane

function readImage(file) {
  return new Promise((resolve, reject) => {
    if (!file.type.startsWith("image/")) return reject(new Error("Not an image"));
    if (file.size > MAX_UPLOAD) return reject(new Error("Image too large (max 4MB)"));
    const r = new FileReader();
    r.onload = () => resolve(r.result);
    r.onerror = () => reject(new Error("Read failed"));
    r.readAsDataURL(file);
  });
}

/* Image field: paste a URL OR upload a file (embedded as data URI so the
 * exported site is fully self-contained). Shows a live thumbnail. */
function imageField(label, path) {
  const isData = (v) => typeof v === "string" && v.startsWith("data:");
  const input = el("input", { type: "url", placeholder: "https://…/photo.jpg or upload →" });
  if (isData(get(path))) { input.value = "(uploaded image)"; input.disabled = true; }
  else input.value = get(path) ?? "";
  const thumb = el("div", { class: "imgthumb" });
  const paintThumb = () => {
    const v = get(path);
    thumb.style.backgroundImage = v ? `url("${(v + "").replace(/"/g, '\\"')}")` : "";
    thumb.classList.toggle("empty", !v);
    thumb.textContent = v ? "" : "no image";
  };
  input.addEventListener("input", () => { set(path, input.value); paintThumb(); schedulePreview(); });

  const file = el("input", { type: "file", accept: "image/*", style: "display:none" });
  file.addEventListener("change", async () => {
    if (!file.files[0]) return;
    try {
      const data = await readImage(file.files[0]);
      set(path, data); input.value = data.slice(0, 48) + "… (uploaded)"; input.disabled = true;
      paintThumb(); schedulePreview(); toast("Image embedded ✅");
    } catch (e) { toast(e.message); }
    file.value = "";
  });
  const upload = el("button", { class: "iconbtn", title: "Upload image", text: "⬆", onclick: () => file.click() });
  const suggest = el("button", { class: "iconbtn", title: "Suggest a stock photo", text: "💡", onclick: () =>
    openStockPicker((url) => { set(path, url); input.value = url; input.disabled = false; paintThumb(); schedulePreview(); }) });
  const clear = el("button", { class: "iconbtn danger", title: "Remove image", text: "✕", onclick: () => {
    set(path, ""); input.value = ""; input.disabled = false; paintThumb(); schedulePreview();
  }});
  paintThumb();
  return el("div", { class: "field" }, [
    el("label", { text: label }),
    el("div", { class: "imgrow" }, [input, suggest, upload, clear, file]),
    thumb,
  ]);
}

function toggleSwitch(path, onToggle) {
  const input = el("input", { type: "checkbox" });
  input.checked = !!get(path);
  input.addEventListener("change", (e) => {
    e.stopPropagation();
    set(path, input.checked);
    schedulePreview();
    if (onToggle) onToggle(input.checked);
  });
  const sw = el("label", { class: "switch", onclick: (e) => e.stopPropagation() }, [
    input, el("span", { class: "track" }),
  ]);
  return sw;
}

/* inline checkbox bound to a boolean path */
function checkField(path, label) {
  const cb = el("input", { type: "checkbox" });
  cb.checked = !!get(path);
  cb.addEventListener("change", () => { set(path, cb.checked); schedulePreview(); });
  return el("label", { class: "field", style: "display:flex;align-items:center;gap:8px;flex-direction:row" }, [cb, label]);
}

/* ---------- repeatable list ---------- */
/* itemSchema: function(item, index) -> array of field DOM nodes
 * makeItem: function() -> new blank item */
function repeatable(path, itemSchema, makeItem, addLabel, itemLabel = "Item") {
  const wrap = el("div", { class: "items" });
  const render = () => {
    wrap.innerHTML = "";
    const arr = get(path) || [];
    arr.forEach((item, i) => {
      const up = el("button", { class: "iconbtn", title: "Move up", text: "↑", onclick: () => move(i, -1) });
      const down = el("button", { class: "iconbtn", title: "Move down", text: "↓", onclick: () => move(i, 1) });
      const del = el("button", { class: "iconbtn danger", title: "Remove", text: "✕", onclick: () => remove(i) });
      const bar = el("div", { class: "item__bar" }, [
        el("span", { class: "lbl", text: `${itemLabel} ${i + 1}` }), up, down, del,
      ]);
      wrap.appendChild(el("div", { class: "item" }, [bar, ...itemSchema(item, i)]));
    });
    const add = el("button", { class: "addbtn", text: "＋ " + addLabel, onclick: () => {
      get(path).push(makeItem()); render(); schedulePreview();
    }});
    wrap.appendChild(add);
  };
  function move(i, dir) {
    const arr = get(path); const j = i + dir;
    if (j < 0 || j >= arr.length) return;
    [arr[i], arr[j]] = [arr[j], arr[i]]; render(); schedulePreview();
  }
  function remove(i) { get(path).splice(i, 1); render(); schedulePreview(); }
  render();
  return wrap;
}

/* image control bound to an array item's property (URL or upload) */
function itemImageField(item, key, label) {
  const isData = (v) => typeof v === "string" && v.startsWith("data:");
  const input = el("input", { type: "url", placeholder: "https://…/photo.jpg or upload →" });
  if (isData(item[key])) { input.value = "(uploaded image)"; input.disabled = true; }
  else input.value = item[key] ?? "";
  const thumb = el("div", { class: "imgthumb" });
  const paint = () => {
    const v = item[key];
    thumb.style.backgroundImage = v ? `url("${(v + "").replace(/"/g, '\\"')}")` : "";
    thumb.classList.toggle("empty", !v);
    thumb.textContent = v ? "" : "no image";
  };
  input.addEventListener("input", () => { item[key] = input.value; paint(); schedulePreview(); });
  const file = el("input", { type: "file", accept: "image/*", style: "display:none" });
  file.addEventListener("change", async () => {
    if (!file.files[0]) return;
    try {
      const data = await readImage(file.files[0]);
      item[key] = data; input.value = "(uploaded image)"; input.disabled = true; paint(); schedulePreview(); toast("Image embedded ✅");
    } catch (e) { toast(e.message); }
    file.value = "";
  });
  const upload = el("button", { class: "iconbtn", title: "Upload", text: "⬆", onclick: () => file.click() });
  const suggest = el("button", { class: "iconbtn", title: "Suggest a stock photo", text: "💡", onclick: () =>
    openStockPicker((url) => { item[key] = url; input.value = url; input.disabled = false; paint(); schedulePreview(); }) });
  const clear = el("button", { class: "iconbtn danger", title: "Remove", text: "✕", onclick: () => {
    item[key] = ""; input.value = ""; input.disabled = false; paint(); schedulePreview();
  }});
  paint();
  return el("div", { class: "field" }, [
    el("label", { text: label }),
    el("div", { class: "imgrow" }, [input, suggest, upload, clear, file]),
    thumb,
  ]);
}

/* item field bound to array element property */
function itemField(item, key, label, { area = false, placeholder = "", width } = {}) {
  const input = area ? el("textarea", { rows: 2, placeholder }) : el("input", { type: "text", placeholder });
  input.value = item[key] ?? "";
  input.addEventListener("input", () => { item[key] = input.value; schedulePreview(); });
  const f = el("div", { class: "field" }, [el("label", { text: label }), input]);
  if (width) f.style.gridColumn = width;
  return f;
}

/* ---------- section shell ---------- */
function section(icon, title, path, bodyNodes, { toggle = true, open = false } = {}) {
  const head = el("div", { class: "sec__head" }, [
    el("span", { class: "ico", text: icon }),
    el("span", { class: "title", text: title }),
  ]);
  if (toggle && path) head.appendChild(toggleSwitch(path + ".enabled"));
  head.appendChild(el("span", { class: "chev", text: "›" }));
  const host = document.getElementById("sections");
  const wasOpen = host && host._restoreOpen && host._restoreOpen.has(title);
  const sec = el("div", { class: "sec" + (open || wasOpen ? " open" : "") }, [
    head, el("div", { class: "sec__body" }, bodyNodes),
  ]);
  const toggleOpen = () => sec.classList.toggle("open");
  head.addEventListener("click", toggleOpen);
  // keyboard a11y for the accordion header
  head.setAttribute("role", "button");
  head.setAttribute("tabindex", "0");
  head.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") { e.preventDefault(); toggleOpen(); }
  });
  return sec;
}

/* ---------- theme picker ---------- */
function themePicker() {
  const grid = el("div", { class: "themes" });
  const paint = () => {
    grid.innerHTML = "";
    THEME_KEYS.forEach((key) => {
      const t = THEMES[key];
      const active = site.meta.theme === key;
      const sw = el("button", { class: "theme-swatch" + (active ? " active" : "") }, [
        el("div", { class: "bar" }),
        el("div", { class: "nm", text: t.label }),
        el("div", { class: "bl", text: t.blurb }),
      ]);
      sw.querySelector(".bar").style.background = `linear-gradient(120deg, ${t.accent}, ${t.accent2})`;
      sw.addEventListener("click", () => { site.meta.theme = key; site.meta.accentColor = ""; paint(); syncAccent(); schedulePreview(); });
      grid.appendChild(sw);
    });
  };
  paint();
  return grid;
}

let accentInput;
function syncAccent() {
  if (accentInput) accentInput.value = site.meta.accentColor || resolveTheme(site.meta.theme).accent;
}

function accentRow() {
  accentInput = el("input", { type: "color" });
  accentInput.value = site.meta.accentColor || resolveTheme(site.meta.theme).accent;
  accentInput.addEventListener("input", () => { site.meta.accentColor = accentInput.value; schedulePreview(); });
  const reset = el("button", { class: "tbtn", text: "Reset", onclick: () => { site.meta.accentColor = ""; syncAccent(); schedulePreview(); } });
  return el("div", { class: "field" }, [
    el("label", { text: "Accent colour (overrides theme)" }),
    el("div", { class: "colorrow" }, [accentInput, reset]),
  ]);
}

/* ---------- page layout reorder ---------- */
function layoutEditor() {
  const list = el("div", { class: "layout-list" });
  let dragKey = null;
  const render = () => {
    list.innerHTML = "";
    site.layout.order.forEach((key, i) => {
      const enabled = site[key] && site[key].enabled;
      const row = el("div", { class: "layout-row" + (enabled ? "" : " is-off"), draggable: "true", "data-key": key }, [
        el("span", { class: "grip", text: "⠿" }),
        el("span", { class: "layout-row__name", text: SECTION_LABELS[key] || key }),
        el("span", { class: "layout-row__state", text: enabled ? "on" : "off" }),
        el("button", { class: "iconbtn", title: "Up", text: "↑", onclick: () => mv(i, -1) }),
        el("button", { class: "iconbtn", title: "Down", text: "↓", onclick: () => mv(i, 1) }),
      ]);
      row.addEventListener("dragstart", () => { dragKey = key; row.classList.add("dragging"); });
      row.addEventListener("dragend", () => { dragKey = null; row.classList.remove("dragging"); list.querySelectorAll(".dragover").forEach((r) => r.classList.remove("dragover")); });
      row.addEventListener("dragover", (e) => { e.preventDefault(); row.classList.add("dragover"); });
      row.addEventListener("dragleave", () => row.classList.remove("dragover"));
      row.addEventListener("drop", (e) => {
        e.preventDefault();
        row.classList.remove("dragover");
        if (!dragKey || dragKey === key) return;
        const order = site.layout.order;
        order.splice(order.indexOf(dragKey), 1);
        order.splice(order.indexOf(key), 0, dragKey);
        render(); schedulePreview();
      });
      list.appendChild(row);
    });
  };
  const mv = (i, d) => {
    const o = site.layout.order; const j = i + d;
    if (j < 0 || j >= o.length) return;
    [o[i], o[j]] = [o[j], o[i]]; render(); schedulePreview();
  };
  render();
  return el("div", { class: "field" }, [
    el("label", { text: "Drag rows (or use arrows) to reorder the page. Hero always stays at the top." }),
    list,
  ]);
}

/* ---------- build the whole panel ---------- */
function buildSections() {
  const host = document.getElementById("sections");
  // preserve which sections are expanded across rebuilds
  const open = new Set(
    [...host.querySelectorAll(".sec.open .sec__head .title")].map((t) => t.textContent)
  );
  host.innerHTML = "";
  host._restoreOpen = open;

  // Brand & theme
  host.appendChild(section("🎨", "Brand & theme", null, [
    textField("Business name", "meta.businessName", { placeholder: "Acme Co." }),
    rowFields(
      textField("Logo text (optional)", "meta.logoText", { placeholder: "defaults to name" }),
      textField("Favicon emoji", "meta.favicon", { placeholder: "🌐" }),
    ),
    imageField("Logo image (optional)", "meta.logoUrl"),
    el("div", { class: "field" }, [el("label", { text: "Theme" }), themePicker()]),
    accentRow(),
  ], { toggle: false, open: true }));

  // Announcement bar
  host.appendChild(section("📢", "Announcement bar", "announce", [
    textField("Message", "announce.text", { placeholder: "20% off this month!" }),
    rowFields(
      textField("Link text", "announce.linkText", { placeholder: "Claim offer" }),
      textField("Link", "announce.link", { placeholder: "#contact" }),
    ),
  ]));

  // Page layout (reorder sections)
  host.appendChild(section("🧱", "Page layout (drag to reorder)", null, [
    layoutEditor(),
  ], { toggle: false }));

  // Hero
  host.appendChild(section("🚀", "Hero (top of page)", null, [
    textField("Eyebrow (small label)", "hero.eyebrow", { placeholder: "Trusted since 2009" }),
    textField("Headline", "hero.headline", { area: true, rows: 2, placeholder: "Your big promise" }),
    textField("Sub-headline", "hero.subheadline", { area: true, placeholder: "One or two supporting lines" }),
    rowFields(
      textField("Primary button text", "hero.ctaText", { placeholder: "Get a quote" }),
      textField("Primary button link", "hero.ctaLink", { placeholder: "#contact" }),
    ),
    rowFields(
      textField("Secondary button text", "hero.secondaryText", { placeholder: "Call us" }),
      textField("Secondary button link", "hero.secondaryLink", { placeholder: "tel:0800…" }),
    ),
    imageField("Hero image", "hero.image"),
    textField("Hero video (YouTube/Vimeo/MP4 — replaces image)", "hero.video", { placeholder: "https://youtu.be/…" }),
    el("div", { class: "field" }, [
      el("label", { text: "Trust badges" }),
      repeatable("hero.badges", (item, i) => {
        // badges are plain strings; wrap via proxy object
        return [stringListField("hero.badges", i, "Badge")];
      }, () => "", "Add badge", "Badge"),
    ]),
  ], { toggle: false }));

  // Trust / logos bar
  host.appendChild(section("🤝", "Trust / logos bar", "trust", [
    textField("Title", "trust.title", { placeholder: "Trusted by" }),
    repeatable("trust.logos", (item) => [
      itemField(item, "name", "Name (used if no logo)", { placeholder: "Which? Trusted Trader" }),
      itemImageField(item, "img", "Logo image"),
    ], () => ({ name: "", img: "" }), "Add logo", "Logo"),
  ]));

  // Stats
  host.appendChild(section("📊", "Stats bar", "stats", [
    repeatable("stats.items", (item) => [
      rowFields(
        itemField(item, "value", "Value", { placeholder: "8,000+" }),
        itemField(item, "label", "Label", { placeholder: "Jobs done" }),
      ),
    ], () => ({ value: "", label: "" }), "Add stat", "Stat"),
  ]));

  // About
  host.appendChild(section("📖", "About", "about", [
    textField("Section title", "about.title", { placeholder: "About us" }),
    textField("Body text", "about.body", { area: true, rows: 4 }),
    imageField("Image", "about.image"),
    el("div", { class: "field" }, [
      el("label", { text: "Key points" }),
      repeatable("about.points", (item, i) => [stringListField("about.points", i, "Point")], () => "", "Add point", "Point"),
    ]),
  ]));

  // Process
  host.appendChild(section("🪜", "How it works (steps)", "process", [
    textField("Section title", "process.title", { placeholder: "How it works" }),
    textField("Subtitle", "process.subtitle"),
    repeatable("process.steps", (item) => [
      itemField(item, "title", "Step title", { placeholder: "Get in touch" }),
      itemField(item, "description", "Description", { area: true }),
    ], () => ({ title: "", description: "" }), "Add step", "Step"),
  ]));

  // Services
  host.appendChild(section("🧰", "Services", "services", [
    textField("Section title", "services.title", { placeholder: "What we do" }),
    textField("Subtitle", "services.subtitle", { placeholder: "optional" }),
    repeatable("services.items", (item) => [
      rowFields(
        itemField(item, "icon", "Icon (emoji)", { placeholder: "🚿" }),
        itemField(item, "title", "Title", { placeholder: "Service name" }),
      ),
      itemField(item, "description", "Description", { area: true, placeholder: "Short description" }),
    ], () => ({ icon: "✅", title: "", description: "" }), "Add service", "Service"),
  ]));

  // Before / After
  host.appendChild(section("↔️", "Before / after slider", "beforeafter", [
    textField("Section title", "beforeafter.title", { placeholder: "See the difference" }),
    textField("Subtitle", "beforeafter.subtitle"),
    repeatable("beforeafter.pairs", (item) => [
      itemField(item, "label", "Caption", { placeholder: "Bathroom renovation" }),
      itemImageField(item, "before", "Before image"),
      itemImageField(item, "after", "After image"),
    ], () => ({ label: "", before: "", after: "" }), "Add before/after pair", "Pair"),
  ]));

  // Gallery
  host.appendChild(section("🖼️", "Gallery", "gallery", [
    textField("Section title", "gallery.title", { placeholder: "Our work" }),
    galleryUpload(),
    el("div", { class: "field" }, [
      el("label", { text: "Image URLs" }),
      repeatable("gallery.images", (item, i) => [stringListField("gallery.images", i, "Image URL")], () => "", "Add image", "Image"),
    ]),
  ]));

  // Video
  host.appendChild(section("🎬", "Video", "video", [
    textField("Section title", "video.title", { placeholder: "Take a look" }),
    textField("Subtitle", "video.subtitle"),
    textField("Video URL (YouTube / Vimeo / MP4)", "video.url", { placeholder: "https://youtu.be/…" }),
  ]));

  // Reviews
  host.appendChild(section("⭐", "Reviews", "reviews", [
    textField("Section title", "reviews.title", { placeholder: "What customers say" }),
    repeatable("reviews.items", (item) => [
      itemField(item, "quote", "Quote", { area: true, placeholder: "They were amazing…" }),
      rowFields(
        itemField(item, "author", "Author", { placeholder: "Sarah M." }),
        itemField(item, "role", "Role", { placeholder: "Customer" }),
      ),
      ratingField(item),
    ], () => ({ quote: "", author: "", role: "", rating: 5 }), "Add review", "Review"),
  ]));

  // Team
  host.appendChild(section("👥", "Team", "team", [
    textField("Section title", "team.title", { placeholder: "Meet the team" }),
    textField("Subtitle", "team.subtitle"),
    repeatable("team.members", (item) => [
      rowFields(
        itemField(item, "name", "Name", { placeholder: "Jane Doe" }),
        itemField(item, "role", "Role", { placeholder: "Lead engineer" }),
      ),
      itemField(item, "bio", "Short bio", { area: true }),
      itemImageField(item, "photo", "Photo"),
    ], () => ({ name: "", role: "", bio: "", photo: "" }), "Add team member", "Member"),
  ]));

  // Pricing
  host.appendChild(section("💷", "Pricing", "pricing", [
    textField("Section title", "pricing.title", { placeholder: "Pricing" }),
    textField("Subtitle", "pricing.subtitle"),
    repeatable("pricing.plans", (item) => [
      rowFields(
        itemField(item, "name", "Plan name", { placeholder: "Standard" }),
        itemField(item, "price", "Price", { placeholder: "£99" }),
      ),
      rowFields(
        itemField(item, "period", "Period", { placeholder: "/month" }),
        itemField(item, "cta", "Button text", { placeholder: "Choose" }),
      ),
      featuredField(item),
      el("div", { class: "field" }, [
        el("label", { text: "Features (one per line)" }),
        planFeatures(item),
      ]),
    ], () => ({ name: "", price: "", period: "", cta: "Choose", featured: false, features: [] }), "Add plan", "Plan"),
  ]));

  // FAQ
  host.appendChild(section("❓", "FAQ", "faq", [
    textField("Section title", "faq.title"),
    repeatable("faq.items", (item) => [
      itemField(item, "q", "Question", { placeholder: "Do you offer…?" }),
      itemField(item, "a", "Answer", { area: true }),
    ], () => ({ q: "", a: "" }), "Add question", "Q"),
  ]));

  // CTA band
  host.appendChild(section("📣", "Call-to-action band", "cta", [
    textField("Title", "cta.title", { placeholder: "Ready to start?" }),
    textField("Body", "cta.body", { area: true }),
    rowFields(
      textField("Button text", "cta.buttonText", { placeholder: "Contact us" }),
      textField("Button link", "cta.buttonLink", { placeholder: "#contact" }),
    ),
  ]));

  // Contact
  host.appendChild(section("✉️", "Contact", "contact", [
    textField("Section title", "contact.title"),
    textField("Subtitle", "contact.subtitle"),
    rowFields(
      textField("Phone", "contact.phone", { type: "tel" }),
      textField("Email", "contact.email", { type: "email" }),
    ),
    textField("Address", "contact.address"),
    textField("Map search (place / postcode)", "contact.mapQuery", { placeholder: "10 High St, City" }),
    textField("Booking link (Calendly etc.)", "contact.bookingUrl", { placeholder: "https://calendly.com/…" }),
    el("div", { class: "field" }, [
      el("label", { text: "Opening hours" }),
      repeatable("contact.hours", (item) => [
        rowFields(
          itemField(item, "day", "Day(s)", { placeholder: "Mon–Fri" }),
          itemField(item, "hours", "Hours", { placeholder: "9am – 5pm" }),
        ),
      ], () => ({ day: "", hours: "" }), "Add hours row", "Row"),
    ]),
    contactToggle(),
    el("div", { class: "field" }, [el("label", { text: "Social links" })]),
    rowFields(
      socialField("facebook", "Facebook"),
      socialField("instagram", "Instagram"),
    ),
    rowFields(
      socialField("twitter", "X / Twitter"),
      socialField("linkedin", "LinkedIn"),
    ),
  ]));

  // Site features (floating buttons, consent, analytics)
  host.appendChild(section("✨", "Site features", null, [
    el("div", { class: "field" }, [el("label", { text: "Floating buttons" })]),
    checkField("floating.enabled", "Show floating buttons"),
    textField("WhatsApp number (with country code)", "floating.whatsapp", { placeholder: "447700900123" }),
    checkField("floating.call", "Show click-to-call button (uses contact phone)"),
    checkField("floating.backToTop", "Show back-to-top button"),
    el("div", { class: "field" }, [el("label", { text: "Cookie consent banner" })]),
    checkField("consent.enabled", "Show cookie banner"),
    textField("Banner text", "consent.text"),
    el("div", { class: "field" }, [el("label", { text: "Analytics & pixels" })]),
    rowFields(
      textField("Google Analytics ID", "integrations.ga4", { placeholder: "G-XXXXXXX" }),
      textField("Meta Pixel ID", "integrations.metaPixel", { placeholder: "123456789" }),
    ),
  ], { toggle: false }));

  // SEO & footer
  host.appendChild(section("🔍", "SEO & footer", null, [
    textField("Meta description", "seo.description", { area: true, placeholder: "Shown in Google results" }),
    imageField("Social share image", "seo.ogImage"),
    textField("Footer text", "footer.text", { placeholder: "© Your Business…" }),
  ], { toggle: false }));
}

/* bulk image upload for the gallery (embeds each as a data URI) */
function galleryUpload() {
  const file = el("input", { type: "file", accept: "image/*", multiple: true, style: "display:none" });
  file.addEventListener("change", async () => {
    const files = [...file.files];
    let added = 0;
    for (const f of files) {
      try { site.gallery.images.push(await readImage(f)); added++; } catch (e) { toast(e.message); }
    }
    file.value = "";
    if (added) { site.gallery.enabled = true; buildSections(); schedulePreview(); toast(`Added ${added} image${added > 1 ? "s" : ""} ✅`); }
  });
  const btn = el("button", { class: "addbtn", text: "⬆ Upload photos from this device", onclick: () => file.click() });
  const suggest = el("button", { class: "addbtn", text: "💡 Add suggested stock photos", onclick: () =>
    openStockPicker((url) => { site.gallery.images.push(url); site.gallery.enabled = true; buildSections(); schedulePreview(); }, { multi: true }) });
  return el("div", { class: "field" }, [btn, suggest, file]);
}

/* string-array element editor */
function stringListField(path, i, label) {
  const input = el("input", { type: "text", value: get(path)[i] ?? "" });
  input.addEventListener("input", () => { get(path)[i] = input.value; schedulePreview(); });
  return el("div", { class: "field" }, [el("label", { text: label }), input]);
}

function ratingField(item) {
  const sel = el("select");
  [5, 4, 3, 2, 1].forEach((n) => {
    const o = el("option", { value: n, text: `${n} star${n > 1 ? "s" : ""}` });
    if ((item.rating || 5) === n) o.selected = true;
    sel.appendChild(o);
  });
  sel.addEventListener("change", () => { item.rating = +sel.value; schedulePreview(); });
  return el("div", { class: "field" }, [el("label", { text: "Rating" }), sel]);
}

function featuredField(item) {
  const cb = el("input", { type: "checkbox" });
  cb.checked = !!item.featured;
  cb.addEventListener("change", () => { item.featured = cb.checked; schedulePreview(); });
  const lab = el("label", { class: "field", style: "display:flex;align-items:center;gap:8px;flex-direction:row" }, [cb, "Highlight as most popular"]);
  return lab;
}

function planFeatures(item) {
  const ta = el("textarea", { rows: 3 });
  ta.value = (item.features || []).join("\n");
  ta.addEventListener("input", () => { item.features = ta.value.split("\n").map((s) => s.trim()).filter(Boolean); schedulePreview(); });
  return ta;
}

function contactToggle() {
  const cb = el("input", { type: "checkbox" });
  cb.checked = !!site.contact.formEnabled;
  cb.addEventListener("change", () => { site.contact.formEnabled = cb.checked; schedulePreview(); });
  return el("label", { class: "field", style: "display:flex;align-items:center;gap:8px;flex-direction:row" }, [cb, "Show contact form (otherwise show map)"]);
}

function socialField(key, label) {
  const input = el("input", { type: "url", placeholder: "https://…" });
  input.value = site.contact.social[key] ?? "";
  input.addEventListener("input", () => { site.contact.social[key] = input.value; schedulePreview(); });
  return el("div", { class: "field" }, [el("label", { text: label }), input]);
}

/* ---------- preview ---------- */
function schedulePreview() {
  clearTimeout(previewTimer);
  previewTimer = setTimeout(() => { renderPreview(); saveLocal(site); }, 180);
  scheduleSnapshot();
}

function renderPreview() {
  const frame = document.getElementById("preview-frame");
  frame.srcdoc = renderDocument(site);
}

function setDevice(d) {
  device = d;
  const wrap = document.getElementById("frame-wrap");
  wrap.className = "frame-wrap " + d;
  document.querySelectorAll(".devices button").forEach((b) => b.classList.toggle("active", b.dataset.dev === d));
}

/* ---------- top bar / actions ---------- */
function toast(msg) {
  const t = document.getElementById("toast");
  t.textContent = msg;
  t.classList.add("show");
  clearTimeout(t._t);
  t._t = setTimeout(() => t.classList.remove("show"), 2400);
}

function refreshAll() {
  buildSections();
  syncAccent();
  renderPreview();
  document.getElementById("project-name").value = site.meta.businessName || "";
}

async function handleGenerate() {
  const box = document.getElementById("prompt-text");
  const prompt = box.value.trim();
  if (!prompt) { toast("Type a quick brief first ✍️"); box.focus(); return; }
  const wrap = document.getElementById("prompt");
  const btn = document.getElementById("gen-btn");
  wrap.classList.add("busy");
  btn.innerHTML = '<span class="spinner"></span> Generating…';
  try {
    const { site: generated, source } = await generate(prompt, { current: site });
    site = generated;
    refreshAll();
    saveLocal(site);
    const notes = document.getElementById("gen-notes");
    const src = source === "claude" ? "<b>Claude</b>" : "offline generator";
    const extra = (generated._notes || []).map((n) => "• " + n).join("<br>");
    notes.innerHTML = `Draft built with ${src}.<br>${extra}`;
    notes.classList.add("show");
    toast(source === "claude" ? "Draft generated with Claude ✨" : "Draft generated ✨");
  } catch (e) {
    toast("Generation failed — try again");
  } finally {
    wrap.classList.remove("busy");
    btn.innerHTML = "✨ Generate site";
  }
}

function newProject() {
  if (!confirm("Start a new blank project? Unsaved changes to the current one will be lost.")) return;
  site = emptySite();
  site.meta.businessName = "";
  refreshAll();
  saveLocal(site);
  toast("New project started");
}

function loadSampleProject() {
  site = sampleSite();
  refreshAll();
  saveLocal(site);
  toast("Loaded example project");
}

function openProjectsModal() {
  const modal = document.getElementById("modal");
  const body = document.getElementById("modal-body");
  body.innerHTML = "";
  const projects = listProjects();
  if (!projects.length) {
    body.appendChild(el("div", { class: "empty", text: "No saved projects yet. Use “Save” to store a draft here." }));
  }
  projects.forEach((p) => {
    const openBtn = el("button", { class: "tbtn tbtn--primary", text: "Open", onclick: () => {
      site = normalize(p.site); refreshAll(); saveLocal(site); closeModal(); toast(`Opened “${p.name}”`);
    }});
    const delBtn = el("button", { class: "tbtn", text: "Delete", onclick: () => {
      if (confirm(`Delete “${p.name}”?`)) { deleteProject(p.name); openProjectsModal(); }
    }});
    const when = p.savedAt ? new Date(p.savedAt).toLocaleString() : "";
    body.appendChild(el("div", { class: "proj" }, [
      el("div", { class: "meta" }, [el("b", { text: p.name }), el("span", { text: when })]),
      openBtn, delBtn,
    ]));
  });
  modal.classList.add("open");
}
function closeModal() { document.getElementById("modal").classList.remove("open"); }

async function handlePublish() {
  const health = getHealth();
  if (!health || !health.publish) {
    toast("Start the backend (python app.py) to publish a live link");
    return;
  }
  const modal = document.getElementById("pub-modal");
  const body = document.getElementById("pub-body");
  modal.classList.add("open");
  body.innerHTML = '<div class="pub-loading"><span class="spinner"></span> Publishing your site…</div>';
  try {
    const { url, qr } = await publishSite(site);
    saveLocal(site); // persist the publish id
    body.innerHTML = "";
    body.appendChild(el("p", { class: "pub-lead", text: "Your site is live 🎉 Share this link or let your client scan the code:" }));
    const link = el("a", { class: "pub-url", href: url, target: "_blank", rel: "noopener", text: url });
    const copyBtn = el("button", { class: "tbtn tbtn--primary", text: "📋 Copy link", onclick: async () => {
      try { await navigator.clipboard.writeText(url); toast("Link copied 📋"); } catch { toast("Copy failed"); }
    }});
    const openBtn = el("button", { class: "tbtn", text: "↗ Open", onclick: () => window.open(url, "_blank") });
    const rePub = el("button", { class: "tbtn", text: "🔄 Re-publish changes", onclick: handlePublish });
    body.appendChild(el("div", { class: "pub-linkrow" }, [link]));
    body.appendChild(el("div", { class: "pub-actions" }, [copyBtn, openBtn, rePub]));
    if (qr) {
      body.appendChild(el("div", { class: "pub-qr" }, [
        el("img", { src: qr, alt: "QR code to open the site", width: 200, height: 200 }),
        el("p", { class: "pub-qrhint", text: "📱 Scan with a phone camera to open it instantly" }),
      ]));
    } else {
      body.appendChild(el("p", { class: "pub-qrhint", text: "Install the optional 'segno' package to show a scannable QR code." }));
    }
  } catch (e) {
    body.innerHTML = "";
    body.appendChild(el("p", { class: "pub-lead", text: "Couldn't publish: " + e.message }));
  }
}
function closePubModal() { document.getElementById("pub-modal").classList.remove("open"); }

/* ---------- stock photo picker ---------- */
function openStockPicker(onPick, { multi = false } = {}) {
  const modal = document.getElementById("stock-modal");
  const body = document.getElementById("stock-body");
  let cat = suggestCategory(site.meta.industry || site.meta.businessName || "");
  const grid = el("div", { class: "stock-grid" });
  const paint = () => {
    grid.innerHTML = "";
    photosFor(cat).forEach((url) => {
      const cell = el("button", { class: "stock-cell", title: "Use this photo", onclick: () => {
        onPick(url);
        toast("Photo added 🖼️");
        if (!multi) closeStockModal();
      }});
      cell.style.backgroundImage = `url("${url}")`;
      grid.appendChild(cell);
    });
  };
  const sel = el("select", { class: "stock-cat" });
  STOCK_KEYS.forEach((k) => {
    const o = el("option", { value: k, text: STOCK[k].label });
    if (k === cat) o.selected = true;
    sel.appendChild(o);
  });
  sel.addEventListener("change", () => { cat = sel.value; paint(); });
  body.innerHTML = "";
  body.appendChild(el("p", { class: "pub-lead", text: "Royalty-free photos to use when your client has none to hand. Pick a category, then click a photo." }));
  body.appendChild(el("div", { class: "field" }, [el("label", { text: "Category" }), sel]));
  body.appendChild(grid);
  if (multi) body.appendChild(el("p", { class: "pub-qrhint", text: "Click several — they'll all be added. Close when done." }));
  paint();
  modal.classList.add("open");
}
function closeStockModal() { document.getElementById("stock-modal").classList.remove("open"); }

function saveProject() {
  const def = site.meta.businessName || "Untitled";
  const name = prompt("Save project as:", def);
  if (!name) return;
  saveProjectAs(name, site);
  toast(`Saved “${name}”`);
}

function importProject() {
  const inp = el("input", { type: "file", accept: ".json,application/json" });
  inp.addEventListener("change", () => {
    const file = inp.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        site = normalize(JSON.parse(reader.result));
        refreshAll(); saveLocal(site); toast("Project imported");
      } catch { toast("Couldn't read that file"); }
    };
    reader.readAsText(file);
  });
  inp.click();
}

/* ---------- boot ---------- */
export async function mountApp() {
  // restore last session if present
  const restored = loadLocal();
  if (restored && restored.meta && restored.meta.businessName !== undefined) site = restored;

  wireTopbar();
  wirePrompt();
  wirePreviewBar();
  wireKeyboard();
  refreshAll();
  resetHistory();

  // backend status
  const status = await probeBackend();
  const chip = document.getElementById("status-chip");
  if (status.available && status.ai) {
    chip.className = "statuschip ok";
    chip.querySelector("span:last-child").textContent = "Claude AI connected";
  } else if (status.available) {
    chip.className = "statuschip local";
    chip.querySelector("span:last-child").textContent = "Backend up · no AI key";
  } else {
    chip.className = "statuschip local";
    chip.querySelector("span:last-child").textContent = "Offline mode";
  }
}

function wireTopbar() {
  document.getElementById("project-name").addEventListener("input", (e) => {
    site.meta.businessName = e.target.value; schedulePreview();
  });
  document.getElementById("act-new").onclick = newProject;
  document.getElementById("act-sample").onclick = loadSampleProject;
  document.getElementById("act-save").onclick = saveProject;
  document.getElementById("act-open").onclick = openProjectsModal;
  document.getElementById("act-import").onclick = importProject;
  document.getElementById("act-export-html").onclick = () => { exportHtml(site); toast("Downloaded website .html ⬇️"); };
  document.getElementById("act-export-json").onclick = () => { exportProject(site); toast("Downloaded project file ⬇️"); };
  document.getElementById("act-open-tab").onclick = () => openPreview(site);
  document.getElementById("act-copy").onclick = async () => { toast((await copyHtml(site)) ? "HTML copied 📋" : "Copy failed"); };
  document.getElementById("act-publish").onclick = handlePublish;
  document.getElementById("pub-close").onclick = closePubModal;
  document.getElementById("pub-modal").addEventListener("click", (e) => { if (e.target.id === "pub-modal") closePubModal(); });
  document.getElementById("stock-close").onclick = closeStockModal;
  document.getElementById("stock-modal").addEventListener("click", (e) => { if (e.target.id === "stock-modal") closeStockModal(); });

  // dropdown menu
  const menu = document.getElementById("export-menu");
  document.getElementById("export-btn").onclick = (e) => { e.stopPropagation(); menu.classList.toggle("open"); };
  document.addEventListener("click", () => menu.classList.remove("open"));
  menu.addEventListener("click", () => menu.classList.remove("open"));

  document.getElementById("modal-close").onclick = closeModal;
  document.getElementById("modal").addEventListener("click", (e) => { if (e.target.id === "modal") closeModal(); });
}

function wirePrompt() {
  document.getElementById("gen-btn").onclick = handleGenerate;
  document.getElementById("prompt-text").addEventListener("keydown", (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key === "Enter") handleGenerate();
  });
  document.querySelectorAll(".chip").forEach((c) => {
    c.addEventListener("click", () => { document.getElementById("prompt-text").value = c.dataset.prompt; });
  });
}

function wireKeyboard() {
  document.getElementById("act-undo").onclick = undo;
  document.getElementById("act-redo").onclick = redo;
  document.addEventListener("keydown", (e) => {
    const mod = e.metaKey || e.ctrlKey;
    if (!mod || e.key.toLowerCase() !== "z" && e.key.toLowerCase() !== "y") return;
    const t = e.target;
    const editing = t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.isContentEditable);
    if (editing) return; // let native text undo work inside fields
    e.preventDefault();
    if (e.key.toLowerCase() === "y" || (e.key.toLowerCase() === "z" && e.shiftKey)) redo();
    else undo();
  });
}

function wirePreviewBar() {
  document.querySelectorAll(".devices button").forEach((b) => {
    b.addEventListener("click", () => setDevice(b.dataset.dev));
  });
  const toggle = document.getElementById("mobile-toggle");
  if (toggle) toggle.addEventListener("click", () => document.body.classList.toggle("show-preview"));
}
