"""PitchSite backend.

Serves the static builder and exposes a tiny API:
  GET  /api/health    -> {ok, ai}
  POST /api/generate  -> {site, source}   (Claude-powered when a key is set)

The builder works fully without this server (it falls back to an in-browser
generator), so the backend is an optional upgrade that adds real Claude copy.
"""
from __future__ import annotations

import re
import uuid
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from generator import ai_enabled, generate_with_claude, improve_with_claude

WEB_DIR = Path(__file__).resolve().parent.parent / "web"
PUBLISH_DIR = Path(__file__).resolve().parent / "published"
PUBLISH_DIR.mkdir(exist_ok=True)

app = FastAPI(title="PitchSite", version="1.0")


def _qr_data_uri(url: str) -> str | None:
    """Return a PNG data-URI QR for the URL, or None if segno isn't installed."""
    try:
        import segno  # pure-python, no native deps
    except Exception:
        return None
    return segno.make(url, error="m").png_data_uri(scale=6, border=2)


class GenerateRequest(BaseModel):
    prompt: str
    current: dict | None = None


class RewriteRequest(BaseModel):
    site: dict
    instruction: str | None = None


class PublishRequest(BaseModel):
    html: str
    name: str | None = None
    id: str | None = None  # re-publish to the same link when provided


@app.get("/api/health")
def health():
    qr = False
    try:
        import segno  # noqa: F401
        qr = True
    except Exception:
        qr = False
    return {"ok": True, "ai": ai_enabled(), "publish": True, "qr": qr}


@app.post("/api/generate")
def generate(req: GenerateRequest):
    if not req.prompt or not req.prompt.strip():
        return JSONResponse({"error": "empty prompt"}, status_code=400)
    if not ai_enabled():
        # No key — tell the client to use its offline generator.
        return JSONResponse({"error": "ai_unavailable"}, status_code=501)
    try:
        site = generate_with_claude(req.prompt, req.current)
        return {"site": site, "source": "claude"}
    except Exception as exc:  # noqa: BLE001 — surface as fallback signal
        return JSONResponse({"error": f"generation_failed: {exc}"}, status_code=502)


@app.post("/api/rewrite")
def rewrite(req: RewriteRequest):
    if not req.site:
        return JSONResponse({"error": "empty site"}, status_code=400)
    if not ai_enabled():
        return JSONResponse({"error": "ai_unavailable"}, status_code=501)
    try:
        site = improve_with_claude(req.site, req.instruction or "")
        return {"site": site, "source": "claude"}
    except Exception as exc:  # noqa: BLE001
        return JSONResponse({"error": f"rewrite_failed: {exc}"}, status_code=502)


def _slug(name: str | None) -> str:
    base = re.sub(r"[^a-z0-9]+", "-", (name or "site").lower()).strip("-")[:24] or "site"
    return f"{base}-{uuid.uuid4().hex[:6]}"


@app.post("/api/publish")
def publish(req: PublishRequest, request: Request):
    if not req.html or "<html" not in req.html.lower():
        return JSONResponse({"error": "invalid_html"}, status_code=400)
    # reuse id on re-publish (stable link), else mint a new one
    site_id = req.id if (req.id and re.fullmatch(r"[a-z0-9-]{3,40}", req.id)) else _slug(req.name)
    (PUBLISH_DIR / f"{site_id}.html").write_text(req.html, encoding="utf-8")
    base = str(request.base_url).rstrip("/")
    url = f"{base}/s/{site_id}"
    return {"id": site_id, "url": url, "qr": _qr_data_uri(url)}


@app.get("/s/{site_id}", response_class=HTMLResponse)
def serve_published(site_id: str):
    if not re.fullmatch(r"[a-z0-9-]{3,40}", site_id):
        return HTMLResponse("Not found", status_code=404)
    path = PUBLISH_DIR / f"{site_id}.html"
    if not path.exists():
        return HTMLResponse("This site hasn't been published, or the link has expired.", status_code=404)
    return HTMLResponse(path.read_text(encoding="utf-8"))


# Serve the builder UI. Mounted last so /api/* takes precedence.
if WEB_DIR.exists():
    app.mount("/", StaticFiles(directory=str(WEB_DIR), html=True), name="web")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host="127.0.0.1", port=8000, reload=True)
