// Echo service worker — just enough to be installable on a phone.
// Cache the app shell so it opens instantly; always hit the network for /api
// (your live brain) and never cache it.
const SHELL = "echo-shell-v1";
const ASSETS = [
  "/",
  "/static/style.css",
  "/static/app.js",
  "/static/icon-192.png",
  "/static/icon-512.png",
  "/manifest.webmanifest",
];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(SHELL).then((c) => c.addAll(ASSETS)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== SHELL).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.pathname.startsWith("/api") ||
      url.pathname === "/login" || url.pathname === "/logout") {
    return; // let the network handle live/dynamic requests
  }
  e.respondWith(
    caches.match(e.request).then((hit) => hit || fetch(e.request))
  );
});
