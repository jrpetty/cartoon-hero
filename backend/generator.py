"""PitchSite — server-side site generation with Claude.

Given a free-text brief from a sales call, ask Claude to return a complete
site specification as JSON that matches the front-end's data model. The
front-end normalises whatever comes back, so partial objects are safe.
"""
from __future__ import annotations

import json
import os
from typing import Any, Dict, Optional

DEFAULT_MODEL = os.environ.get("PITCHSITE_MODEL", "claude-sonnet-4-6")

# Compact description of the target shape. Kept terse on purpose — Claude fills
# only what it can justify from the brief; missing keys are fine.
SCHEMA_HINT = """
Return ONLY a JSON object (no prose, no markdown fences) shaped like:
{
  "meta": {"businessName": str, "tagline": str, "favicon": "single emoji",
           "theme": one of ["aurora","midnight","linen","emerald","coral","slate"]},
  "hero": {"eyebrow": str, "headline": str, "subheadline": str,
            "ctaText": str, "secondaryText": str, "secondaryLink": str,
            "image": "image URL if one was given else ''",
            "badges": [str, str, str]},
  "stats": {"enabled": bool, "items": [{"value": str, "label": str}]},
  "about": {"enabled": bool, "title": str, "body": str, "image": str,
             "points": [str, ...]},
  "services": {"enabled": bool, "title": str,
                "items": [{"icon": "emoji", "title": str, "description": str}]},
  "gallery": {"enabled": bool, "title": str, "images": [url, ...]},
  "reviews": {"enabled": bool, "title": str,
               "items": [{"quote": str, "author": str, "role": str, "rating": 5}]},
  "faq": {"enabled": bool, "title": str, "items": [{"q": str, "a": str}]},
  "cta": {"enabled": bool, "title": str, "body": str, "buttonText": str},
  "contact": {"enabled": bool, "title": str, "phone": str, "email": str,
               "address": str, "mapQuery": str,
               "hours": [{"day": str, "hours": str}]},
  "seo": {"description": str}
}
""".strip()

THEME_GUIDE = (
    "Theme guidance: coral = friendly local/trades; linen = warm hospitality/retail; "
    "midnight = premium/dark/fitness/luxury; aurora = modern startup/services; "
    "slate = corporate/professional; emerald = trustworthy health/finance/trades."
)

SYSTEM_PROMPT = (
    "You are a senior conversion copywriter and web designer. You turn a short "
    "brief about a small business into a polished one-page marketing website spec. "
    "Write concrete, benefit-led, believable copy in British English. Invent 3 "
    "realistic-sounding customer reviews unless real ones are provided. Choose 4-6 "
    "relevant services with fitting emoji icons. Pick the single best theme. Only "
    "include sections that make sense for this business. " + THEME_GUIDE
)


def ai_enabled() -> bool:
    return bool(os.environ.get("ANTHROPIC_API_KEY"))


def _extract_json(text: str) -> Dict[str, Any]:
    text = text.strip()
    if text.startswith("```"):
        text = text.split("```", 2)[1]
        if text.lstrip().startswith("json"):
            text = text.lstrip()[4:]
    start, end = text.find("{"), text.rfind("}")
    if start != -1 and end != -1:
        text = text[start : end + 1]
    return json.loads(text)


def generate_with_claude(prompt: str, current: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """Return a site dict. Raises on failure so the caller can fall back."""
    import anthropic  # imported lazily so the server runs without the package

    client = anthropic.Anthropic()
    user_msg = (
        f"Business brief from a sales call:\n\"\"\"\n{prompt.strip()}\n\"\"\"\n\n"
        f"{SCHEMA_HINT}"
    )
    resp = client.messages.create(
        model=DEFAULT_MODEL,
        max_tokens=2400,
        system=SYSTEM_PROMPT,
        messages=[{"role": "user", "content": user_msg}],
    )
    text = "".join(block.text for block in resp.content if getattr(block, "type", "") == "text")
    return _extract_json(text)
