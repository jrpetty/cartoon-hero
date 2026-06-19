"""Email reminders for Echo — optional, SMTP-based, fires only when configured.

A daemon thread wakes periodically, finds decisions whose review date has
arrived, and emails you to go grade your own calibration. Each decision is
emailed at most once. If SMTP isn't configured, the thread simply never sends —
the on-open prompt in the UI still works as a fallback.
"""
import os
import smtplib
import threading
import time
from email.message import EmailMessage
from typing import List

import db

CHECK_EVERY_SECONDS = int(os.environ.get("ECHO_REMINDER_INTERVAL", "3600"))


def _cfg(key: str, default: str = "") -> str:
    return os.environ.get(key, default)


def email_configured() -> bool:
    return bool(_cfg("SMTP_HOST") and _cfg("ECHO_EMAIL_TO"))


def _send(subject: str, body: str) -> None:
    msg = EmailMessage()
    msg["Subject"] = subject
    msg["From"] = _cfg("ECHO_EMAIL_FROM") or _cfg("SMTP_USER") or _cfg("ECHO_EMAIL_TO")
    msg["To"] = _cfg("ECHO_EMAIL_TO")
    msg.set_content(body)

    host, port = _cfg("SMTP_HOST"), int(_cfg("SMTP_PORT", "587"))
    user, password = _cfg("SMTP_USER"), _cfg("SMTP_PASS")
    with smtplib.SMTP(host, port, timeout=30) as server:
        server.starttls()
        if user:
            server.login(user, password)
        server.send_message(msg)


def _format(dec: dict) -> str:
    return (
        f"⚖️  Time to grade a decision in Echo.\n\n"
        f"Decision: {dec['title']}\n"
        f"You predicted: {dec.get('prediction') or '—'}\n"
        f"Your confidence at the time: {dec.get('confidence')}%\n\n"
        f"How did it actually turn out? Open Echo → Decisions and grade yourself.\n"
        f"This is the calibration loop — be honest with the score.\n"
    )


def run_due_reminders() -> int:
    """Send emails for any due decisions. Returns how many were sent."""
    if not email_configured():
        return 0
    sent = 0
    for dec in db.decisions_to_remind():
        try:
            _send(f"Echo: grade your call — “{dec['title']}”", _format(dec))
            db.mark_reminded(dec["id"])
            sent += 1
        except Exception as exc:  # pragma: no cover - network/SMTP failures
            print(f"[echo] reminder email failed for decision {dec['id']}: {exc}")
    return sent


def _loop() -> None:  # pragma: no cover - background thread
    while True:
        try:
            n = run_due_reminders()
            if n:
                print(f"[echo] sent {n} decision reminder email(s)")
        except Exception as exc:
            print(f"[echo] reminder loop error: {exc}")
        time.sleep(CHECK_EVERY_SECONDS)


def start_scheduler() -> bool:
    """Start the background reminder thread if email is configured."""
    if not email_configured():
        return False
    threading.Thread(target=_loop, daemon=True, name="echo-reminders").start()
    return True
