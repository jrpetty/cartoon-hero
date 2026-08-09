#!/usr/bin/env python3
"""
Synthesize the mod's sound set as OGG Vorbis.

Every sound here is deliberately quiet and soft-edged. UI audio that plays
hundreds of times a session is fatiguing long before it is loud, and the three
things that make it fatiguing are an instant attack, energy up in the 8-16kHz
sibilance band, and a peak that sits near full scale. So every sound in this
file is built to the same rules, and tools/checksounds.py asserts them:

  * peak stays at or under PEAK_CEILING (about -14 dBFS)
  * at least ATTACK_MS of fade-in, so nothing starts with a click
  * a fade-out to true zero, so nothing ends with one either
  * a gentle low-pass, so there is no sizzle on top
  * short - these are punctuation, not music

They are card sounds, so they are mostly filtered noise: paper is noise. The
tonal ones (the gavel, the shimmer) keep their partials low and let them decay
fast rather than ring.

Run: python3 tools/gensounds.py [outdir]
"""
import math
import os
import sys

import numpy as np
import soundfile as sf

SR = 44100
PEAK_CEILING = 0.18      # ~ -15 dBFS; Vorbis overshoots a little on encode.
ATTACK_MS = 3.0          # no sound starts instantly

rng = np.random.default_rng(20260809)


def noise(n):
    return rng.uniform(-1.0, 1.0, n)


def onepole_lp(x, cutoff):
    a = math.exp(-2.0 * math.pi * cutoff / SR)
    out = np.empty_like(x)
    prev = 0.0
    for i, v in enumerate(x):
        prev = (1.0 - a) * v + a * prev
        out[i] = prev
    return out


def lowpass(x, cutoff, poles=4):
    """A cascaded low-pass, which is what these actually need.

    A single pole rolls off at 6dB/octave, so noise "low-passed" at 5kHz still
    has most of its sizzle two octaves up — the first version of this file did
    exactly that and tools/checksounds.py measured a third of the energy of
    every paper sound sitting in the 8-16kHz band that stings. Four poles is
    24dB/octave, which actually removes it.
    """
    for _ in range(poles):
        x = onepole_lp(x, cutoff)
    return x


def onepole_hp(x, cutoff):
    return x - onepole_lp(x, cutoff)


def env(n, attack, decay, curve=2.0):
    """Attack-decay envelope, in samples, with a curved tail."""
    e = np.ones(n)
    a = max(1, int(attack))
    d = max(1, int(decay))
    e[:a] = np.linspace(0.0, 1.0, a) ** 1.5
    tail = np.linspace(1.0, 0.0, min(d, n - a)) ** curve
    e[a:a + len(tail)] = tail
    e[a + len(tail):] = 0.0
    return e


def finish(x):
    """Shared safety net: soft edges, no DC, and a peak under the ceiling."""
    n = len(x)
    x = x - np.mean(x)                       # kill any DC offset
    ramp = max(2, int(SR * ATTACK_MS / 1000))
    x[:ramp] *= np.linspace(0.0, 1.0, ramp) ** 1.5
    out = max(2, int(SR * 0.012))            # 12ms fade to true zero
    x[-out:] *= np.linspace(1.0, 0.0, out) ** 1.5
    peak = np.max(np.abs(x))
    if peak > 0:
        x = x / peak * PEAK_CEILING
    return x.astype(np.float32)


def card_flip():
    """One card turning: a short paper flick with a little air behind it."""
    n = int(SR * 0.16)
    body = lowpass(onepole_hp(noise(n), 600), 3000)
    return finish(body * env(n, SR * 0.004, SR * 0.13, 2.4))


def card_place():
    """A card set down on felt - softer and lower than a flip, no flick."""
    n = int(SR * 0.13)
    body = lowpass(onepole_hp(noise(n), 220), 1700)
    return finish(body * env(n, SR * 0.006, SR * 0.10, 2.8) * 0.8)


def shuffle():
    """A riffle: a run of small flicks, unevenly spaced so it is not a machine."""
    n = int(SR * 0.62)
    out = np.zeros(n)
    t = int(SR * 0.02)
    while t < n - int(SR * 0.05):
        seg = int(SR * 0.045)
        tick = lowpass(onepole_hp(noise(seg), 700), 3200)
        tick *= env(seg, SR * 0.002, SR * 0.04, 2.6)
        # later ticks sit back a little, the way a real riffle tails off
        tick *= 0.55 + 0.45 * (1.0 - t / n)
        out[t:t + seg] += tick
        t += int(SR * rng.uniform(0.032, 0.055))
    return finish(out)


def pack_rip():
    """Foil tearing: broadband crackle whose grain speeds up then stops."""
    n = int(SR * 0.55)
    base = lowpass(onepole_hp(noise(n), 800), 3400)
    # amplitude "grain" - the irregular crackle that makes a tear a tear
    grain = np.abs(onepole_lp(noise(n), 90))
    grain = grain / (np.max(grain) or 1.0)
    shape = env(n, SR * 0.008, SR * 0.50, 1.6)
    return finish(base * (0.35 + 0.65 * grain) * shape * 0.9)


def foil_shimmer():
    """A holo catching the light: a few high partials, staggered and brief.
    Kept under 7kHz and well spread so it sparkles rather than stings."""
    n = int(SR * 0.75)
    out = np.zeros(n)
    for i, f in enumerate((2100.0, 2800.0, 3550.0, 4300.0, 5250.0, 6200.0)):
        start = int(SR * (0.02 + 0.055 * i))
        seg = n - start
        if seg <= 0:
            continue
        t = np.arange(seg) / SR
        partial = np.sin(2 * math.pi * f * t)
        partial *= env(seg, SR * 0.012, SR * 0.34, 3.0)
        out[start:] += partial * (0.85 ** i)
    return finish(lowpass(out, 6500, 2) * 0.75)


def gavel():
    """The wager settling: a wooden knock. A low damped body under a soft tap,
    with the tap filtered so it reads as wood rather than a snare."""
    n = int(SR * 0.30)
    t = np.arange(n) / SR
    body = np.zeros(n)
    for f, amp, dec in ((172.0, 1.0, 26.0), (298.0, 0.45, 34.0), (511.0, 0.2, 46.0)):
        body += amp * np.sin(2 * math.pi * f * t) * np.exp(-dec * t)
    tap = lowpass(onepole_hp(noise(n), 700), 2400) * env(n, SR * 0.003, SR * 0.05, 3.0)
    return finish((body * 0.8 + tap * 0.5))


def chip():
    """A fragment landing on the table when a price is agreed - very small."""
    n = int(SR * 0.12)
    t = np.arange(n) / SR
    tone = (np.sin(2 * math.pi * 1650 * t) + 0.5 * np.sin(2 * math.pi * 2480 * t))
    tone *= np.exp(-38.0 * t)
    tick = lowpass(onepole_hp(noise(n), 1100), 3000) * env(n, SR * 0.002, SR * 0.04, 3.0)
    return finish((tone * 0.6 + tick * 0.4) * 0.7)


SOUNDS = {
    "card_flip": card_flip,
    "card_place": card_place,
    "shuffle": shuffle,
    "pack_rip": pack_rip,
    "foil_shimmer": foil_shimmer,
    "gavel": gavel,
    "chip": chip,
}


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/mobtrumps/sounds"
    os.makedirs(out, exist_ok=True)
    for name, make in SOUNDS.items():
        data = make()
        path = os.path.join(out, name + ".ogg")
        sf.write(path, data, SR, format="OGG", subtype="VORBIS")
        peak = float(np.max(np.abs(data)))
        rms = float(np.sqrt(np.mean(data ** 2)))
        print(f"  {name:14} {len(data) / SR:5.2f}s  peak {peak:.3f}  "
              f"rms {rms:.4f}  {os.path.getsize(path):6d} bytes")
    print(f"wrote {len(SOUNDS)} sounds to {out}")


if __name__ == "__main__":
    main()
