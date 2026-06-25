/* PitchSite — export & share helpers. */
import { renderDocument } from "./render.js";

function slug(s) {
  return (s || "site")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 40) || "site";
}

function download(filename, text, type = "text/html") {
  const blob = new Blob([text], { type });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}

/* Download the finished website as one self-contained .html file. */
export function exportHtml(site) {
  const doc = renderDocument(site);
  download(slug(site.meta.businessName) + ".html", doc);
}

/* Download the editable project JSON (to reopen later / move machines). */
export function exportProject(site) {
  download(slug(site.meta.businessName) + ".pitchsite.json", JSON.stringify(site, null, 2), "application/json");
}

/* Open the finished site full-screen in a new tab (great for showing on a call). */
export function openPreview(site) {
  const doc = renderDocument(site);
  const w = window.open("", "_blank");
  if (w) {
    w.document.open();
    w.document.write(doc);
    w.document.close();
  }
}

/* Publish to a live, shareable URL via the backend. Returns {id, url, qr}.
 * Reuses the site's previous publish id so the link stays stable. */
export async function publishSite(site) {
  const html = renderDocument(site);
  const res = await fetch("/api/publish", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ html, name: site.meta.businessName, id: site._publishId || null }),
  });
  if (!res.ok) throw new Error("Publish failed (" + res.status + ")");
  const data = await res.json();
  site._publishId = data.id; // remember for stable re-publishing
  return data;
}

export async function copyHtml(site) {
  const doc = renderDocument(site);
  try {
    await navigator.clipboard.writeText(doc);
    return true;
  } catch {
    return false;
  }
}
