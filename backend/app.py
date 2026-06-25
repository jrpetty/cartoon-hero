"""PitchSite backend.

Serves the static builder and exposes a tiny API:
  GET  /api/health    -> {ok, ai}
  POST /api/generate  -> {site, source}   (Claude-powered when a key is set)

The builder works fully without this server (it falls back to an in-browser
generator), so the backend is an optional upgrade that adds real Claude copy.
"""
from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from generator import ai_enabled, generate_with_claude

WEB_DIR = Path(__file__).resolve().parent.parent / "web"

app = FastAPI(title="PitchSite", version="1.0")


class GenerateRequest(BaseModel):
    prompt: str
    current: dict | None = None


@app.get("/api/health")
def health():
    return {"ok": True, "ai": ai_enabled()}


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


# Serve the builder UI. Mounted last so /api/* takes precedence.
if WEB_DIR.exists():
    app.mount("/", StaticFiles(directory=str(WEB_DIR), html=True), name="web")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host="127.0.0.1", port=8000, reload=True)
