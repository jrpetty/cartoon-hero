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
    "decide": (
        "They're facing a choice. DECIDE FOR THEM: commit to a clear "
        "recommendation, then write the reasoning in THEIR voice and values — the "
        "case they would actually make to themselves. Structure it: a one-line "
        "verdict, the 'why' in their words, the main risk, and what would change "
        "your mind. Don't hedge into a both-sides shrug; make the call."
    ),
}


def chat(mode: str, history: List[Dict], dossier: str, memory: str) -> str:
    system = _identity(dossier, memory) + "\n\nMODE: " + MODE_INSTRUCTIONS.get(
        mode, MODE_INSTRUCTIONS["chat"]
    )
    return _complete(system, history, max_tokens=1200)


def draft_decision(title: str, context: str, dossier: str, memory: str) -> Dict:
    """Echo drafts the reasoning, prediction and confidence the user would give."""
    system = _identity(dossier, memory) + (
        "\n\nThe user wants you to draft a decision FOR them, as them. Based on who "
        "they are, fill in the reasoning they'd give, a concrete prediction, and a "
        "calibrated confidence. Respond with ONLY a JSON object with keys: "
        "reasoning (first-person, their voice), prediction (concrete, falsifiable), "
        "confidence (integer 0-100)."
    )
    user = f"Decision: {title}\nContext: {context or '(none given)'}"
    raw = _complete(system, [{"role": "user", "content": user}], max_tokens=700)
    obj = _parse_json_object(raw)
    return {
        "reasoning": str(obj.get("reasoning", "")),
        "prediction": str(obj.get("prediction", "")),
        "confidence": _clamp_int(obj.get("confidence", 50)),
    }


def find_contradictions(new_statements: List[str], prior_memory: str,
                        dossier: str) -> List[Dict]:
    """Spot where new statements clash with what the user said before."""
    if not new_statements:
        return []
    system = (
        "You compare a person's NEW statements against what they've said before and "
        "their dossier. Flag genuine contradictions or notable shifts in view — not "
        "trivial differences or added detail. For each, respond with the prior "
        "stance and the new one. Respond with ONLY a JSON array of objects with "
        "keys: old (what they used to think), new (what they're saying now), note "
        "(one sentence on the shift), topic (one or two words). Return [] if none."
    )
    user = (
        f"Their dossier:\n{dossier or '(none)'}\n\n"
        f"What they've said before:\n{prior_memory or '(nothing logged)'}\n\n"
        f"New statements:\n- " + "\n- ".join(new_statements)
    )
    raw = _complete(system, [{"role": "user", "content": user}], max_tokens=700)
    out = []
    for item in _parse_json_array(raw):
        if item.get("old") and item.get("new"):
            out.append(item)
    return out


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
    out = []
    for item in _parse_json_array(raw):
        if item.get("content"):
            out.append(
                {
                    "kind": item.get("kind", "fact"),
                    "content": str(item["content"]),
                    "topic": item.get("topic"),
                    "sentiment": item.get("sentiment"),
                }
            )
    return out


def _parse_json_array(raw: str) -> List[Dict]:
    """Pull the first JSON array out of a model response, tolerating fences/prose."""
    match = re.search(r"\[.*\]", raw.strip(), re.DOTALL)
    if not match:
        return []
    try:
        data = json.loads(match.group(0))
    except json.JSONDecodeError:
        return []
    return [item for item in data if isinstance(item, dict)] if isinstance(data, list) else []


def _parse_json_object(raw: str) -> Dict:
    match = re.search(r"\{.*\}", raw.strip(), re.DOTALL)
    if not match:
        return {}
    try:
        data = json.loads(match.group(0))
    except json.JSONDecodeError:
        return {}
    return data if isinstance(data, dict) else {}


def _clamp_int(value, lo: int = 0, hi: int = 100) -> int:
    try:
        return max(lo, min(hi, int(value)))
    except (TypeError, ValueError):
        return 50
