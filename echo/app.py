"""Echo — one brain, one memory, four modes.

A personal AI that interviews you, builds a model of how you think and feel,
and uses that single growing picture to log decisions, stress-test your takes,
remember your life, and one day speak on your behalf.
"""
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import List, Optional

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse, PlainTextResponse, Response
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

import brain
import db
import notify
import voice
from questions import BY_ID, QUESTIONS

db.init_db()
notify.start_scheduler()

app = FastAPI(title="Echo")

CHAT_MODES = ("chat", "argue", "asme", "decide")

STATIC_DIR = Path(__file__).with_name("static")


# --- schemas ---------------------------------------------------------------

class AnswerIn(BaseModel):
    question_id: str
    answer: str


class ChatIn(BaseModel):
    mode: str = "chat"  # chat | argue | asme | decide
    message: str


class DraftIn(BaseModel):
    title: str
    context: str = ""


class TTSIn(BaseModel):
    text: str


class DecisionIn(BaseModel):
    title: str
    context: str = ""
    reasoning: str = ""
    prediction: str = ""
    confidence: int = 50
    review_in_days: int = 90


class ReviewIn(BaseModel):
    outcome: str
    self_grade: int


class MemoryIn(BaseModel):
    kind: str = "fact"
    content: str
    topic: Optional[str] = None
    sentiment: Optional[str] = None


# --- pages -----------------------------------------------------------------

@app.get("/")
def index():
    return FileResponse(STATIC_DIR / "index.html")


@app.get("/api/state")
def state():
    s = db.stats()
    s["has_key"] = brain.has_key()
    s["total_questions"] = len(QUESTIONS)
    s["decisions_due"] = db.decisions_due()
    s["contradictions"] = len(db.list_contradictions())
    s["premium_tts"] = voice.tts_available()
    s["premium_stt"] = voice.stt_available()
    s["email_reminders"] = notify.email_configured()
    return s


# --- interview -------------------------------------------------------------

@app.get("/api/interview/next")
def interview_next():
    done = set(db.answered_ids())
    for q in QUESTIONS:
        if q["id"] not in done:
            return {"done": False, "question": q, "answered": len(done),
                    "total": len(QUESTIONS)}
    return {"done": True, "answered": len(done), "total": len(QUESTIONS)}


@app.post("/api/interview/answer")
def interview_answer(payload: AnswerIn):
    q = BY_ID.get(payload.question_id)
    if not q:
        raise HTTPException(404, "Unknown question")
    db.save_answer(q["id"], q["text"], payload.answer)
    # Each answer is also raw memory, regardless of whether a key is set.
    db.add_memory("view", payload.answer, source="interview", topic=q["id"])
    return interview_next()


@app.post("/api/interview/synthesize")
def interview_synthesize():
    answers = db.all_answers()
    if not answers:
        raise HTTPException(400, "No answers yet — do the interview first.")
    try:
        dossier = brain.synthesize_dossier(answers, previous=db.get_dossier())
    except brain.BrainError as exc:
        raise HTTPException(503, str(exc))
    db.set_dossier(dossier)
    return {"dossier": dossier}


@app.get("/api/dossier", response_class=PlainTextResponse)
def dossier():
    return db.get_dossier() or "(no dossier yet — finish the interview and synthesize)"


# --- chat / argue / asme ---------------------------------------------------

@app.post("/api/chat")
def chat(payload: ChatIn):
    mode = payload.mode if payload.mode in CHAT_MODES else "chat"
    db.add_message(mode, "user", payload.message)

    history = [
        {"role": "user" if m["role"] == "user" else "assistant", "content": m["content"]}
        for m in db.recent_messages(mode, limit=12)
    ]
    dossier = db.get_dossier()
    prior_memory = db.memory_digest()
    try:
        reply = brain.chat(mode, history, dossier, prior_memory)
    except brain.BrainError as exc:
        raise HTTPException(503, str(exc))

    db.add_message(mode, "echo", reply)

    # Learn from what the user said, and spot contradictions with the past.
    # Best-effort: never blocks or fails the reply.
    learned, contradictions = [], []
    try:
        learned = brain.extract_memories(payload.message, dossier)
        for m in learned:
            db.add_memory(m["kind"], m["content"], source=mode,
                          topic=m.get("topic"), sentiment=m.get("sentiment"))
        if learned:
            contradictions = brain.find_contradictions(
                [m["content"] for m in learned], prior_memory, dossier
            )
            for c in contradictions:
                db.add_contradiction(c["old"], c["new"], c.get("note", ""),
                                     c.get("topic"))
    except brain.BrainError:
        pass

    return {"reply": reply, "learned": learned, "contradictions": contradictions}


# --- decisions -------------------------------------------------------------

@app.post("/api/decisions")
def create_decision(payload: DecisionIn):
    review_at = (
        datetime.now(timezone.utc) + timedelta(days=max(1, payload.review_in_days))
    ).isoformat()
    dec_id = db.add_decision(
        payload.title, payload.context, payload.reasoning,
        payload.prediction, max(0, min(100, payload.confidence)), review_at,
    )
    decision = {**payload.model_dump(), "id": dec_id}
    advice = None
    if brain.has_key():
        try:
            advice = brain.advise_decision(decision, db.get_dossier(), db.memory_digest())
        except brain.BrainError:
            advice = None
    db.add_memory("fact", f"I decided: {payload.title}. Prediction: {payload.prediction}",
                  source="decision", topic="decisions")
    return {"id": dec_id, "review_at": review_at, "advice": advice}


@app.post("/api/decisions/draft")
def draft_decision(payload: DraftIn):
    if not payload.title.strip():
        raise HTTPException(400, "Give the decision a title first.")
    try:
        return brain.draft_decision(payload.title, payload.context,
                                    db.get_dossier(), db.memory_digest())
    except brain.BrainError as exc:
        raise HTTPException(503, str(exc))


@app.get("/api/decisions")
def get_decisions():
    return {"decisions": db.list_decisions(), "due": db.decisions_due()}


@app.post("/api/reminders/run")
def run_reminders():
    if not notify.email_configured():
        raise HTTPException(503, "Email reminders aren't configured (set SMTP_HOST and ECHO_EMAIL_TO).")
    return {"sent": notify.run_due_reminders()}


@app.post("/api/decisions/{dec_id}/review")
def review(dec_id: int, payload: ReviewIn):
    db.review_decision(dec_id, payload.outcome, max(0, min(100, payload.self_grade)))
    return {"ok": True}


# --- memory ----------------------------------------------------------------

@app.get("/api/memory")
def memory(topic: Optional[str] = None, limit: int = 200):
    return {"memory": db.list_memory(limit=limit, topic=topic)}


@app.post("/api/memory")
def add_memory(payload: MemoryIn):
    mem_id = db.add_memory(payload.kind, payload.content, source="manual",
                           topic=payload.topic, sentiment=payload.sentiment)
    return {"id": mem_id}


# --- contradictions --------------------------------------------------------

@app.get("/api/contradictions")
def get_contradictions():
    return {"contradictions": db.list_contradictions()}


@app.post("/api/contradictions/{cid}/dismiss")
def dismiss_contradiction(cid: int):
    db.dismiss_contradiction(cid)
    return {"ok": True}


# --- voice (premium, optional) ---------------------------------------------

@app.get("/api/voice/config")
def voice_config():
    return {"premium_tts": voice.tts_available(), "premium_stt": voice.stt_available()}


@app.post("/api/tts")
def tts(payload: TTSIn):
    if not voice.tts_available():
        # 204 tells the frontend to fall back to browser speech synthesis.
        return Response(status_code=204)
    try:
        audio = voice.synthesize(payload.text)
    except Exception as exc:
        raise HTTPException(502, f"TTS failed: {exc}")
    return Response(content=audio, media_type="audio/mpeg")


@app.post("/api/stt")
async def stt(audio: UploadFile = File(...)):
    if not voice.stt_available():
        raise HTTPException(503, "Premium speech-to-text not configured (set OPENAI_API_KEY).")
    data = await audio.read()
    try:
        text = voice.transcribe(data, audio.filename or "speech.webm")
    except Exception as exc:
        raise HTTPException(502, f"Transcription failed: {exc}")
    return {"text": text}


@app.get("/api/export", response_class=PlainTextResponse)
def export():
    return db.export_all()


# Static assets (served last so /api routes win).
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")
