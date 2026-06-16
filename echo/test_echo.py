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
