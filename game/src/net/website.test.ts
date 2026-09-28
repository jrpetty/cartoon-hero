import { describe, expect, it, afterAll, beforeAll } from "vitest";
// Node built-ins; the project has no @types/node.
// @ts-ignore
import { mkdtempSync, writeFileSync } from "fs";
// @ts-ignore
import { tmpdir } from "os";
// @ts-ignore
import { join } from "path";
// @ts-ignore
import { gunzipSync } from "zlib";
// @ts-ignore
import http from "http";
import { startServer } from "../../server/server.mjs";

/** The website half of the server: the game page, fast and cache-friendly. */

let srv: { port: number; close: () => Promise<void> };
const page = "<!doctype html><html><body>" + "Banner & Blade ".repeat(5000) + "</body></html>";
beforeAll(async () => {
  const dir = mkdtempSync(join(tmpdir(), "bb-site-"));
  writeFileSync(join(dir, "index.html"), page);
  srv = await startServer(0, "127.0.0.1", { gameHtml: join(dir, "index.html"), mapsFile: join(dir, "maps.json") });
});
afterAll(async () => { await srv.close(); });

/** A raw GET, so we see the bytes as sent (fetch would decompress for us). */
function get(path: string, headers: Record<string, string> = {}) {
  return new Promise<{ status: number; headers: Record<string, string>; body: Uint8Array }>((resolve, reject) => {
    http.get({ host: "127.0.0.1", port: srv.port, path, headers }, (res: { statusCode: number; headers: Record<string, string>; on: (e: string, f: (x?: Uint8Array) => void) => void }) => {
      const chunks: Uint8Array[] = [];
      res.on("data", (c) => chunks.push(c!));
      res.on("end", () => {
        const n = chunks.reduce((a, c) => a + c.length, 0), body = new Uint8Array(n);
        let o = 0; for (const c of chunks) { body.set(c, o); o += c.length; }
        resolve({ status: res.statusCode, headers: res.headers, body });
      });
    }).on("error", reject);
  });
}

describe("The website", () => {
  it("serves the game compressed to browsers that accept it", async () => {
    const r = await get("/", { "accept-encoding": "gzip, deflate, br" });
    expect(r.status).toBe(200);
    expect(r.headers["content-encoding"]).toBe("gzip");
    expect(r.body.length).toBeLessThan(page.length / 5);
    expect(new TextDecoder().decode(gunzipSync(r.body))).toBe(page);
  });

  it("serves it plain to those that don't", async () => {
    const r = await get("/");
    expect(r.headers["content-encoding"]).toBeUndefined();
    expect(new TextDecoder().decode(r.body)).toBe(page);
  });

  it("answers an unchanged page with 304", async () => {
    const first = await get("/");
    const again = await get("/", { "if-none-match": first.headers.etag });
    expect(again.status).toBe(304);
    expect(again.body.length).toBe(0);
  });

  it("404s anything else rather than serving the game everywhere", async () => {
    expect((await get("/wp-login.php")).status).toBe(404);
  });
});
