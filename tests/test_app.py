"""Backend API tests for PitchSite."""
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "backend"))

import app as appmod  # noqa: E402
import generator as gen  # noqa: E402

client = TestClient(appmod.app)


def test_health_reports_ai_flag(monkeypatch):
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    r = client.get("/api/health")
    assert r.status_code == 200
    body = r.json()
    assert body["ok"] is True
    assert body["ai"] is False


def test_generate_requires_prompt():
    r = client.post("/api/generate", json={"prompt": "   "})
    assert r.status_code == 400


def test_generate_without_key_returns_501(monkeypatch):
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    r = client.post("/api/generate", json={"prompt": "a plumber"})
    assert r.status_code == 501
    assert r.json()["error"] == "ai_unavailable"


def test_generate_with_mocked_claude(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    fake = {"meta": {"businessName": "Test Co", "theme": "aurora"}}
    monkeypatch.setattr(gen, "generate_with_claude", lambda p, c=None: fake)
    monkeypatch.setattr(appmod, "generate_with_claude", lambda p, c=None: fake)
    r = client.post("/api/generate", json={"prompt": "a test business"})
    assert r.status_code == 200
    data = r.json()
    assert data["source"] == "claude"
    assert data["site"]["meta"]["businessName"] == "Test Co"


def test_generate_handles_claude_failure(monkeypatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")

    def boom(p, c=None):
        raise RuntimeError("api down")

    monkeypatch.setattr(appmod, "generate_with_claude", boom)
    r = client.post("/api/generate", json={"prompt": "x"})
    assert r.status_code == 502


def test_extract_json_tolerates_fences():
    obj = gen._extract_json('```json\n{"a": 1}\n```')
    assert obj == {"a": 1}
    obj2 = gen._extract_json('Here you go: {"b": 2} cheers')
    assert obj2 == {"b": 2}


def test_static_index_served():
    r = client.get("/")
    assert r.status_code == 200
    assert "PitchSite" in r.text


SITE_HTML = "<!doctype html><html><head><title>T</title></head><body>hi</body></html>"


def test_health_reports_publish():
    r = client.get("/api/health").json()
    assert r["publish"] is True
    assert "qr" in r


def test_publish_rejects_non_html():
    r = client.post("/api/publish", json={"html": "just text"})
    assert r.status_code == 400


def test_publish_and_serve_roundtrip():
    r = client.post("/api/publish", json={"html": SITE_HTML, "name": "Acme Cafe"})
    assert r.status_code == 200
    data = r.json()
    assert data["id"].startswith("acme-cafe-")
    assert data["url"].endswith("/s/" + data["id"])
    # QR present because segno is installed in the test env
    assert data["qr"] and data["qr"].startswith("data:image/png")
    served = client.get("/s/" + data["id"])
    assert served.status_code == 200
    assert "hi" in served.text


def test_publish_reuses_id_for_stable_link():
    first = client.post("/api/publish", json={"html": SITE_HTML, "name": "Shop"}).json()
    again = client.post("/api/publish", json={"html": SITE_HTML, "id": first["id"]}).json()
    assert again["id"] == first["id"]


def test_serve_unknown_returns_404():
    assert client.get("/s/does-not-exist-123").status_code == 404


def test_serve_rejects_bad_id():
    assert client.get("/s/..%2f..%2fetc").status_code == 404
