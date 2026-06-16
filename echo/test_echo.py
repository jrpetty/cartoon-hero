"""Tests for Echo's database and API plumbing.

These run without an API key — they exercise storage and the endpoints that
don't depend on Claude. The AI calls are mocked.
"""
import importlib
import os
import sys
import tempfile
from pathlib import Path

import pytest
from fastapi.testclient import TestClient


@pytest.fixture()
def client(monkeypatch):
    # Fresh temp DB per test so nothing leaks between runs.
    tmp = Path(tempfile.mkdtemp()) / "echo.db"
    import db
    monkeypatch.setattr(db, "DB_PATH", tmp)
    db.init_db()
    import app as app_module
    importlib.reload(app_module)
    monkeypatch.setattr(app_module.db, "DB_PATH", tmp)
    return TestClient(app_module.app)


def test_state_starts_empty(client):
    r = client.get("/api/state")
    assert r.status_code == 200
    body = r.json()
    assert body["interview_answers"] == 0
    assert body["has_dossier"] is False
    assert body["total_questions"] == 20


def test_interview_flow_saves_and_advances(client):
    first = client.get("/api/interview/next").json()
    assert first["done"] is False
    qid = first["question"]["id"]

    r = client.post("/api/interview/answer", json={"question_id": qid, "answer": "I trust my gut, then sanity-check it."})
    assert r.status_code == 200
    assert r.json()["answered"] == 1

    # The answer is also stored as raw memory.
    mem = client.get("/api/memory").json()["memory"]
    assert any("gut" in m["content"] for m in mem)


def test_unknown_question_rejected(client):
    r = client.post("/api/interview/answer", json={"question_id": "nope", "answer": "x"})
    assert r.status_code == 404


def test_decision_logging_and_review(client):
    r = client.post("/api/decisions", json={
        "title": "Take the job", "reasoning": "Better team", "prediction": "I'll be happier",
        "confidence": 70, "review_in_days": 1,
    })
    assert r.status_code == 200
    dec_id = r.json()["id"]

    listed = client.get("/api/decisions").json()["decisions"]
    assert listed[0]["title"] == "Take the job"
    assert listed[0]["confidence"] == 70

    rev = client.post(f"/api/decisions/{dec_id}/review", json={"outcome": "Happier", "self_grade": 80})
    assert rev.status_code == 200
    graded = client.get("/api/decisions").json()["decisions"][0]
    assert graded["self_grade"] == 80
    assert graded["outcome"] == "Happier"


def test_confidence_is_clamped(client):
    r = client.post("/api/decisions", json={"title": "X", "confidence": 250, "review_in_days": 5})
    dec_id = r.json()["id"]
    graded = client.get("/api/decisions").json()["decisions"][0]
    assert graded["confidence"] == 100


def test_chat_without_key_returns_503(client, monkeypatch):
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    r = client.post("/api/chat", json={"mode": "chat", "message": "hey"})
    assert r.status_code == 503
    assert "ANTHROPIC_API_KEY" in r.json()["detail"]


def test_export_roundtrips(client):
    client.post("/api/decisions", json={"title": "Ship it", "review_in_days": 3})
    dump = client.get("/api/export").text
    assert "Ship it" in dump


def test_memory_extraction_json_parsing():
    import brain
    raw = 'Sure! Here you go:\n```json\n[{"kind":"view","content":"I value freedom","topic":"values","sentiment":"positive"}]\n```'
    parsed = brain._parse_json_array(raw)
    assert len(parsed) == 1
    assert parsed[0]["content"] == "I value freedom"

    assert brain._parse_json_array("nothing structured here") == []


def test_json_object_and_clamp_helpers():
    import brain
    obj = brain._parse_json_object('here: {"reasoning":"because","confidence":80}')
    assert obj["confidence"] == 80
    assert brain._parse_json_object("no json") == {}
    assert brain._clamp_int(250) == 100
    assert brain._clamp_int(-5) == 0
    assert brain._clamp_int("oops") == 50


def test_contradictions_crud(client):
    import db
    cid = db.add_contradiction("I hate risk", "I just took a big risk", "shift", "risk")
    listed = client.get("/api/contradictions").json()["contradictions"]
    assert any(c["id"] == cid for c in listed)

    client.post(f"/api/contradictions/{cid}/dismiss")
    assert client.get("/api/contradictions").json()["contradictions"] == []
    # state still counts only active contradictions
    assert client.get("/api/state").json()["contradictions"] == 0


def test_reminders_require_smtp(client, monkeypatch):
    monkeypatch.delenv("SMTP_HOST", raising=False)
    monkeypatch.delenv("ECHO_EMAIL_TO", raising=False)
    assert client.post("/api/reminders/run").status_code == 503


def test_decisions_to_remind_lifecycle(client):
    import db
    from datetime import datetime, timezone
    dec_id = db.add_decision("Old call", "", "", "it works out", 60,
                             datetime(2000, 1, 1, tzinfo=timezone.utc).isoformat())
    due = db.decisions_to_remind()
    assert any(d["id"] == dec_id for d in due)
    db.mark_reminded(dec_id)
    assert all(d["id"] != dec_id for d in db.decisions_to_remind())


def test_voice_config_off_without_keys(client, monkeypatch):
    monkeypatch.delenv("ELEVENLABS_API_KEY", raising=False)
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    cfg = client.get("/api/voice/config").json()
    assert cfg == {"premium_tts": False, "premium_stt": False}


def test_tts_no_key_returns_204(client, monkeypatch):
    monkeypatch.delenv("ELEVENLABS_API_KEY", raising=False)
    assert client.post("/api/tts", json={"text": "hello"}).status_code == 204


def test_draft_without_key_503(client, monkeypatch):
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    r = client.post("/api/decisions/draft", json={"title": "Move cities"})
    assert r.status_code == 503


def test_grading_feeds_calibration_into_memory(client):
    dec_id = client.post("/api/decisions", json={
        "title": "Bet on the startup", "prediction": "Big win", "confidence": 90,
        "review_in_days": 1,
    }).json()["id"]
    r = client.post(f"/api/decisions/{dec_id}/review",
                    json={"outcome": "Flopped", "self_grade": 20})
    assert r.json()["calibration"] == "overconfident"
    # The graded outcome is now a memory the rest of Echo reasons from.
    mem = client.get("/api/memory?topic=calibration").json()["memory"]
    assert any("overconfident" in m["content"] for m in mem)


def test_calibration_empty(client):
    r = client.get("/api/calibration").json()
    assert r["graded"] == 0
    assert r["label"] is None
    assert r["buckets"] == []


def test_calibration_flags_overconfidence(client):
    # Three high-confidence calls that mostly went badly => overconfident.
    for grade in (20, 30, 40):
        dec = client.post("/api/decisions", json={
            "title": "Bold bet", "confidence": 90, "review_in_days": 1,
        }).json()["id"]
        client.post(f"/api/decisions/{dec}/review",
                    json={"outcome": "meh", "self_grade": grade})

    r = client.get("/api/calibration").json()
    assert r["graded"] == 3
    assert r["avg_confidence"] == 90
    assert r["avg_accuracy"] == 30
    assert r["bias"] == 60            # 90 stated - 30 actual
    assert r["label"] == "overconfident"
    # All three calls sit in the 80-100 confidence band.
    assert len(r["buckets"]) == 1
    assert r["buckets"][0]["count"] == 3
    assert r["buckets"][0]["stated"] == 90
    assert r["buckets"][0]["actual"] == 30
    assert r["best"]["grade"] == 40 and r["worst"]["grade"] == 20


def test_calibration_well_calibrated_label(client):
    dec = client.post("/api/decisions", json={
        "title": "Steady call", "confidence": 70, "review_in_days": 1,
    }).json()["id"]
    client.post(f"/api/decisions/{dec}/review",
                json={"outcome": "as expected", "self_grade": 72})
    r = client.get("/api/calibration").json()
    assert r["label"] == "well-calibrated"
    assert r["avg_gap"] == 2


def test_context_digest_links_everything(client):
    import db
    db.add_memory("view", "I value freedom", source="interview")
    db.add_decision("Quit job", "", "", "happier", 70, db.now())
    db.add_contradiction("I hate risk", "I quit my stable job", "shift", "risk")
    digest = db.context_digest()
    assert "freedom" in digest
    assert "Quit job" in digest
    assert "used to think" in digest


def test_confirm_contradiction_logs_view_and_resolves(client):
    import db
    cid = db.add_contradiction("I'd never move abroad", "I'm moving to Lisbon", "", "location")
    r = client.post(f"/api/contradictions/{cid}/confirm")
    assert r.status_code == 200
    assert client.get("/api/contradictions").json()["contradictions"] == []
    mem = client.get("/api/memory").json()["memory"]
    assert any("changed my mind" in m["content"] for m in mem)


def test_dossier_refresh_needs_key(client, monkeypatch):
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    assert client.post("/api/dossier/refresh").status_code == 503


def test_open_when_no_password(client, monkeypatch):
    monkeypatch.delenv("ECHO_PASSWORD", raising=False)
    assert client.get("/api/state").status_code == 200


def test_password_gate_blocks_and_unlocks(client, monkeypatch):
    monkeypatch.setenv("ECHO_PASSWORD", "hunter2")
    # API is blocked without a valid cookie
    assert client.get("/api/state").status_code == 401
    # The page redirects to login (TestClient follows it to a 200 login page)
    page = client.get("/")
    assert "Unlock" in page.text

    # Wrong password is rejected
    assert client.post("/login", data={"password": "nope"}).status_code == 401

    # Correct password sets the cookie; the client keeps it for later requests
    ok = client.post("/login", data={"password": "hunter2"})
    assert ok.status_code == 200  # followed redirect to "/"
    assert client.get("/api/state").status_code == 200

    # Static assets stay reachable for the login page
    monkeypatch.setenv("ECHO_PASSWORD", "hunter2")
    assert client.get("/static/style.css").status_code == 200


def test_manifest_and_sw_served(client):
    assert client.get("/manifest.webmanifest").status_code == 200
    sw = client.get("/sw.js")
    assert sw.status_code == 200
    assert "serviceWorker".lower() in sw.text.lower() or "fetch" in sw.text
