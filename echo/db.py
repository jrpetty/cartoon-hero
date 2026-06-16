"""The one giant database behind Echo.

Everything the user ever says, feels, decides, or believes lands here. Four
tables, one SQLite file. Designed so the whole picture of a person can be
rebuilt (and re-summarised) from raw rows at any time.
"""
import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

DB_PATH = Path(__file__).with_name("echo.db")


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def get_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    conn = get_connection()
    cur = conn.cursor()

    # The evolving first-person "operating manual for being you".
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS profile (
            id INTEGER PRIMARY KEY CHECK(id = 1),
            dossier TEXT NOT NULL DEFAULT '',
            updated_at TEXT
        );
        """
    )
    cur.execute("INSERT OR IGNORE INTO profile(id, dossier) VALUES (1, '')")

    # The onboarding interview: serious questions + the user's raw answers.
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS interview (
            question_id TEXT PRIMARY KEY,
            question TEXT NOT NULL,
            answer TEXT NOT NULL,
            created_at TEXT NOT NULL
        );
        """
    )

    # Every durable thing learned about the user: views, facts, feelings,
    # people, jokes. The "one giant database of what I've said and how I feel".
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS memory (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            kind TEXT NOT NULL,          -- view | fact | feeling | person | joke | preference
            content TEXT NOT NULL,
            topic TEXT,
            sentiment TEXT,              -- positive | negative | neutral | mixed
            source TEXT NOT NULL,        -- interview | chat | decision | argument | manual
            created_at TEXT NOT NULL
        );
        """
    )

    # Decisions, predictions and self-grading for calibration training.
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS decisions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            context TEXT,
            reasoning TEXT,
            prediction TEXT,
            confidence INTEGER,          -- 0-100
            decided_at TEXT NOT NULL,
            review_at TEXT NOT NULL,
            outcome TEXT,
            self_grade INTEGER,          -- 0-100, how right you turned out to be
            reviewed_at TEXT
        );
        """
    )

    # Full conversation log across every mode.
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            mode TEXT NOT NULL,          -- chat | argue | asme
            role TEXT NOT NULL,          -- user | echo
            content TEXT NOT NULL,
            created_at TEXT NOT NULL
        );
        """
    )
    conn.commit()
    conn.close()


# --- profile ---------------------------------------------------------------

def get_dossier() -> str:
    conn = get_connection()
    row = conn.execute("SELECT dossier FROM profile WHERE id = 1").fetchone()
    conn.close()
    return row["dossier"] if row else ""


def set_dossier(text: str) -> None:
    conn = get_connection()
    conn.execute(
        "UPDATE profile SET dossier = ?, updated_at = ? WHERE id = 1",
        (text, now()),
    )
    conn.commit()
    conn.close()


# --- interview -------------------------------------------------------------

def save_answer(question_id: str, question: str, answer: str) -> None:
    conn = get_connection()
    conn.execute(
        """
        INSERT INTO interview(question_id, question, answer, created_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(question_id) DO UPDATE SET answer = excluded.answer,
            created_at = excluded.created_at
        """,
        (question_id, question, answer, now()),
    )
    conn.commit()
    conn.close()


def answered_ids() -> List[str]:
    conn = get_connection()
    rows = conn.execute("SELECT question_id FROM interview").fetchall()
    conn.close()
    return [r["question_id"] for r in rows]


def all_answers() -> List[Dict[str, Any]]:
    conn = get_connection()
    rows = conn.execute(
        "SELECT question_id, question, answer FROM interview ORDER BY rowid"
    ).fetchall()
    conn.close()
    return [dict(r) for r in rows]


# --- memory ----------------------------------------------------------------

def add_memory(
    kind: str,
    content: str,
    source: str,
    topic: Optional[str] = None,
    sentiment: Optional[str] = None,
) -> int:
    conn = get_connection()
    cur = conn.execute(
        """
        INSERT INTO memory(kind, content, topic, sentiment, source, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        (kind, content, topic, sentiment, source, now()),
    )
    conn.commit()
    mem_id = cur.lastrowid
    conn.close()
    return mem_id


def list_memory(limit: int = 200, topic: Optional[str] = None) -> List[Dict[str, Any]]:
    conn = get_connection()
    if topic:
        rows = conn.execute(
            "SELECT * FROM memory WHERE topic = ? ORDER BY id DESC LIMIT ?",
            (topic, limit),
        ).fetchall()
    else:
        rows = conn.execute(
            "SELECT * FROM memory ORDER BY id DESC LIMIT ?", (limit,)
        ).fetchall()
    conn.close()
    return [dict(r) for r in rows]


def memory_digest(limit: int = 60) -> str:
    """A compact text blob of recent memories for prompt context."""
    rows = list_memory(limit=limit)
    lines = []
    for r in rows:
        topic = f" [{r['topic']}]" if r["topic"] else ""
        lines.append(f"- ({r['kind']}{topic}) {r['content']}")
    return "\n".join(reversed(lines))


# --- decisions -------------------------------------------------------------

def add_decision(
    title: str,
    context: str,
    reasoning: str,
    prediction: str,
    confidence: int,
    review_at: str,
) -> int:
    conn = get_connection()
    cur = conn.execute(
        """
        INSERT INTO decisions(title, context, reasoning, prediction, confidence,
            decided_at, review_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        (title, context, reasoning, prediction, confidence, now(), review_at),
    )
    conn.commit()
    dec_id = cur.lastrowid
    conn.close()
    return dec_id


def list_decisions() -> List[Dict[str, Any]]:
    conn = get_connection()
    rows = conn.execute("SELECT * FROM decisions ORDER BY id DESC").fetchall()
    conn.close()
    return [dict(r) for r in rows]


def decisions_due() -> List[Dict[str, Any]]:
    """Decisions whose review date has arrived and that aren't graded yet."""
    conn = get_connection()
    rows = conn.execute(
        """
        SELECT * FROM decisions
        WHERE reviewed_at IS NULL AND review_at <= ?
        ORDER BY review_at
        """,
        (now(),),
    ).fetchall()
    conn.close()
    return [dict(r) for r in rows]


def review_decision(dec_id: int, outcome: str, self_grade: int) -> None:
    conn = get_connection()
    conn.execute(
        """
        UPDATE decisions SET outcome = ?, self_grade = ?, reviewed_at = ?
        WHERE id = ?
        """,
        (outcome, self_grade, now(), dec_id),
    )
    conn.commit()
    conn.close()


# --- messages --------------------------------------------------------------

def add_message(mode: str, role: str, content: str) -> None:
    conn = get_connection()
    conn.execute(
        "INSERT INTO messages(mode, role, content, created_at) VALUES (?, ?, ?, ?)",
        (mode, role, content, now()),
    )
    conn.commit()
    conn.close()


def recent_messages(mode: str, limit: int = 12) -> List[Dict[str, Any]]:
    conn = get_connection()
    rows = conn.execute(
        "SELECT role, content FROM messages WHERE mode = ? ORDER BY id DESC LIMIT ?",
        (mode, limit),
    ).fetchall()
    conn.close()
    return [dict(r) for r in reversed(rows)]


def stats() -> Dict[str, Any]:
    conn = get_connection()
    c = conn.cursor()
    interview_count = c.execute("SELECT COUNT(*) FROM interview").fetchone()[0]
    memory_count = c.execute("SELECT COUNT(*) FROM memory").fetchone()[0]
    decision_count = c.execute("SELECT COUNT(*) FROM decisions").fetchone()[0]
    message_count = c.execute("SELECT COUNT(*) FROM messages").fetchone()[0]
    has_dossier = bool(
        c.execute("SELECT LENGTH(dossier) FROM profile WHERE id = 1").fetchone()[0]
    )
    conn.close()
    return {
        "interview_answers": interview_count,
        "memories": memory_count,
        "decisions": decision_count,
        "messages": message_count,
        "has_dossier": has_dossier,
    }


def export_all() -> str:
    """Dump the entire brain as JSON — your data, portable."""
    return json.dumps(
        {
            "dossier": get_dossier(),
            "interview": all_answers(),
            "memory": list_memory(limit=100000),
            "decisions": list_decisions(),
        },
        indent=2,
    )


if __name__ == "__main__":
    init_db()
    print(f"Initialised {DB_PATH}")
