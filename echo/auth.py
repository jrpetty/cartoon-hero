"""A single-password gate for Echo.

Echo holds the most personal data imaginable, so once it's reachable on the
public internet it must not be wide open. Set ECHO_PASSWORD and every page/API
requires it; leave it unset (e.g. local dev) and Echo runs open as before.

This is a deliberately simple shared-secret gate for a single user — not a
multi-account system. The session cookie is an HMAC of the password, so it
can't be forged without knowing the password, and it changes if you change it.
"""
import hashlib
import hmac
import os

COOKIE_NAME = "echo_auth"


def password() -> str:
    return os.environ.get("ECHO_PASSWORD", "")


def enabled() -> bool:
    return bool(password())


def expected_token() -> str:
    return hmac.new(password().encode(), b"echo-session-v1", hashlib.sha256).hexdigest()


def check_password(candidate: str) -> bool:
    return enabled() and hmac.compare_digest(candidate or "", password())


def cookie_valid(token: str) -> bool:
    return bool(token) and hmac.compare_digest(token, expected_token())
