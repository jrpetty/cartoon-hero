"""Premium voice for Echo — optional, key-gated, graceful fallback.

Text-to-speech via ElevenLabs and speech-to-text via OpenAI Whisper. If the
relevant keys aren't set, these report "unavailable" and the frontend falls
back to the browser's built-in Web Speech API. No keys, no problem.
"""
import os
from typing import Optional, Tuple

import httpx

ELEVEN_KEY = lambda: os.environ.get("ELEVENLABS_API_KEY")
# "Rachel" — a widely-available default ElevenLabs voice. Override freely.
ELEVEN_VOICE = lambda: os.environ.get("ELEVENLABS_VOICE_ID", "21m00Tcm4TlvDq8ikWAM")
ELEVEN_MODEL = lambda: os.environ.get("ELEVENLABS_MODEL", "eleven_turbo_v2_5")

OPENAI_KEY = lambda: os.environ.get("OPENAI_API_KEY")
WHISPER_MODEL = lambda: os.environ.get("ECHO_WHISPER_MODEL", "whisper-1")


def tts_available() -> bool:
    return bool(ELEVEN_KEY())


def stt_available() -> bool:
    return bool(OPENAI_KEY())


def synthesize(text: str) -> bytes:
    """Return MP3 audio bytes for `text` via ElevenLabs."""
    if not tts_available():
        raise RuntimeError("ELEVENLABS_API_KEY not set")
    url = f"https://api.elevenlabs.io/v1/text-to-speech/{ELEVEN_VOICE()}"
    resp = httpx.post(
        url,
        headers={"xi-api-key": ELEVEN_KEY(), "accept": "audio/mpeg"},
        json={
            "text": text,
            "model_id": ELEVEN_MODEL(),
            "voice_settings": {"stability": 0.45, "similarity_boost": 0.8},
        },
        timeout=60,
    )
    resp.raise_for_status()
    return resp.content


def transcribe(audio: bytes, filename: str = "speech.webm") -> str:
    """Transcribe recorded audio via OpenAI Whisper."""
    if not stt_available():
        raise RuntimeError("OPENAI_API_KEY not set")
    resp = httpx.post(
        "https://api.openai.com/v1/audio/transcriptions",
        headers={"Authorization": f"Bearer {OPENAI_KEY()}"},
        data={"model": WHISPER_MODEL()},
        files={"file": (filename, audio, "application/octet-stream")},
        timeout=120,
    )
    resp.raise_for_status()
    return resp.json().get("text", "").strip()
