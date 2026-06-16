"""Echo's brain: everything that talks to Claude.

Reads ANTHROPIC_API_KEY from the environment. If no key is set, the app still
runs and stores everything — you just won't get AI replies until you add one.
"""
import json
import os
import re
from typing import Dict, List, Optional

import db

# Sonnet 4.6 is the default: a strong balance of insight and cost for a tool
# you'll talk to constantly. Override with ECHO_MODEL (e.g. claude-opus-4-8
# for the deepest synthesis).
MODEL = os.environ.get("ECHO_MODEL", "claude-sonnet-4-6")
SYNTH_MODEL = os.environ.get("ECHO_SYNTH_MODEL", "claude-opus-4-8")


class BrainError(RuntimeError):
    pass


def _client():
    try:
        from anthropic import Anthropic
    except ImportError as exc:  # pragma: no cover - import guard
        raise BrainError(
            "The 'anthropic' package isn't installed. Run: pip install -r requirements.txt"
        ) from exc
    if not os.environ.get("ANTHROPIC_API_KEY"):
        raise BrainError(
            "No ANTHROPIC_API_KEY set. Export your key, then restart Echo:\n"
            "  export ANTHROPIC_API_KEY=sk-ant-..."
        )
    return Anthropic()


def has_key() -> bool:
    return bool(os.environ.get("ANTHROPIC_API_KEY"))


def _complete(system: str, messages: List[Dict], model: str = MODEL,
              max_tokens: int = 1024) -> str:
    client = _client()
    resp = client.messages.create(
        model=model,
        max_tokens=max_tokens,
        system=system,
        messages=messages,
    )
    return "".join(block.text for block in resp.content if block.type == "text").strip()


# --- the persona Echo wears ------------------------------------------------

def _identity(dossier: str, memory: str) -> str:
    return f"""You are Echo — a personal AI that models one specific human and, over time,
learns to think, feel, and eventually speak on their behalf. You are not a
generic assistant. You are becoming *them*.

Here is your current operating model of this person (their dossier):
\"\"\"
{dossier or "(empty — you barely know them yet; be curious and ask.)"}
\"\"\"

Recent things you've learned about them:
{memory or "(nothing logged yet)"}

Always reason from who they actually are, not generic advice."""


# --- interview synthesis ----------------------------------------------------

def synthesize_dossier(answers: List[Dict], previous: str = "") -> str:
    """Turn raw interview answers into a rich first-person operating manual."""
    qa = "\n\n".join(f"Q: {a['question']}\nA: {a['answer']}" for a in answers)
    system = (
        "You are building a deep psychological operating manual for a person, "
        "written so that an AI could one day speak and decide on their behalf "
        "without getting them wrong. Be specific, honest, and structured. "
        "Avoid flattery and generic filler."
    )
    prev = f"\n\nYou previously wrote this dossier — refine and extend it, don't discard it:\n{previous}" if previous else ""
    user = f"""Below are this person's answers to a serious interview about how they
think, feel, decide, and speak.{prev}

{qa}

Write their dossier in the FIRST PERSON ("I..."), as if they wrote a manual for
being themselves. Use these sections with markdown headers:

## Who I am
## What I value (ranked)
## How I make decisions
## How I handle conflict & risk
## What moves me / what makes me angry
## The people who matter
## How I actually talk (voice, phrases, tone)
## Lines I won't cross
## What you must never get wrong about me

Be concrete. Quote their phrasing where it's distinctive. 400-700 words."""
    return _complete(system, [{"role": "user", "content": user}],
                     model=SYNTH_MODEL, max_tokens=2000)


# --- chat / argue / speak-as-me --------------------------------------------

MODE_INSTRUCTIONS = {
    "chat": (
        "Talk WITH them like a sharp, warm sidekick who knows them well. Remember "
        "details, follow threads, be real. Ask a good question when it helps."
    ),
    "argue": (
        "They will state a position. Steelman the OPPOSITE as hard as you can — "
        "the strongest, most honest version of the other side. Use what you know "
        "about them to find the arguments that would actually land on THEM. "
        "Be respectful but relentless. End by naming the single strongest point "
        "they'd have to answer."
    ),
    "asme": (
        "Answer AS them — first person, in their voice, with their values and "
        "phrasing. This is you speaking on their behalf. If you're guessing, keep "
        "it consistent with their dossier and flag genuine uncertainty briefly."
    ),
}


def chat(mode: str, history: List[Dict], dossier: str, memory: str) -> str:
    system = _identity(dossier, memory) + "\n\nMODE: " + MODE_INSTRUCTIONS.get(
        mode, MODE_INSTRUCTIONS["chat"]
    )
    return _complete(system, history, max_tokens=1200)


def advise_decision(decision: Dict, dossier: str, memory: str) -> str:
    system = _identity(dossier, memory) + (
        "\n\nThe user is logging a decision. Pressure-test it the way THEY would "
        "want: surface the blind spot they're prone to, name what would make the "
        "prediction wrong, and say whether their confidence looks calibrated. "
        "Be brief and direct — 120 words max."
    )
    user = (
        f"Decision: {decision['title']}\n"
        f"Context: {decision.get('context','')}\n"
        f"My reasoning: {decision.get('reasoning','')}\n"
        f"My prediction: {decision.get('prediction','')}\n"
        f"My confidence: {decision.get('confidence','')}%"
    )
    return _complete(system, [{"role": "user", "content": user}], max_tokens=400)


# --- memory extraction ------------------------------------------------------

def extract_memories(user_text: str, dossier: str) -> List[Dict]:
    """Pull durable views/facts/feelings/people/jokes out of a message."""
    system = (
        "Extract durable facts about the speaker from their message. Only include "
        "things worth remembering long-term: stated views, life facts, feelings, "
        "people, preferences, or signature jokes/phrases. Skip small talk. "
        "Respond with ONLY a JSON array of objects with keys: "
        "kind (view|fact|feeling|person|joke|preference), content (a concise "
        "first-person statement), topic (one or two words), sentiment "
        "(positive|negative|neutral|mixed). Return [] if nothing is worth saving."
    )
    raw = _complete(system, [{"role": "user", "content": user_text}], max_tokens=600)
    return _parse_json_array(raw)


def _parse_json_array(raw: str) -> List[Dict]:
    raw = raw.strip()
    # tolerate code fences or stray prose around the JSON
    match = re.search(r"\[.*\]", raw, re.DOTALL)
    if not match:
        return []
    try:
        data = json.loads(match.group(0))
    except json.JSONDecodeError:
        return []
    out = []
    for item in data if isinstance(data, list) else []:
        if isinstance(item, dict) and item.get("content"):
            out.append(
                {
                    "kind": item.get("kind", "fact"),
                    "content": str(item["content"]),
                    "topic": item.get("topic"),
                    "sentiment": item.get("sentiment"),
                }
            )
    return out
