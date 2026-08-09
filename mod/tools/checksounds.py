#!/usr/bin/env python3
"""
Assert the sound set is quiet and comfortable, because nobody can listen to it
from here.

"Do not make the sounds too loud or uncomfortable" is a real requirement and it
decomposes into things that are measurable. Fatiguing UI audio is nearly always
one of four faults, so each gets a test:

  LOUD      peak near full scale, or a high sustained RMS
  CLICKY    an instant attack or a truncated tail - a step in the waveform
            reads as a click no matter how quiet the sound is
  SHARP     energy in the 8-16kHz sibilance band, which is what makes a sound
            "harsh" rather than "loud" and is the usual culprit
  DRONING   too long to be punctuation

It reads the ENCODED .ogg back rather than trusting the arrays that were
written, because Vorbis is lossy and can overshoot a peak the synthesizer
thought it had set.

Run: python3 tools/checksounds.py [dir]
"""
import glob
import os
import sys

import numpy as np
import soundfile as sf

# Vorbis may overshoot the 0.20 the generator targets; this is the hard ceiling.
PEAK_MAX = 0.30
RMS_MAX = 0.10
# above this share of total energy in 8-16kHz, a sound reads as harsh
SIBILANCE_MAX = 0.06
ATTACK_MS_MIN = 2.0
EDGE_MAX = 0.05        # sample value allowed at the very first/last sample
DURATION_MAX_S = 1.20
DC_MAX = 0.01


def check(path):
    data, sr = sf.read(path, dtype="float64", always_2d=False)
    if data.ndim > 1:
        data = data.mean(axis=1)
    name = os.path.basename(path)
    faults = []

    peak = float(np.max(np.abs(data)))
    rms = float(np.sqrt(np.mean(data ** 2)))
    if peak > PEAK_MAX:
        faults.append(f"LOUD peak {peak:.3f} > {PEAK_MAX}")
    if rms > RMS_MAX:
        faults.append(f"LOUD rms {rms:.3f} > {RMS_MAX}")

    dur = len(data) / sr
    if dur > DURATION_MAX_S:
        faults.append(f"DRONING {dur:.2f}s > {DURATION_MAX_S}s")

    if abs(float(np.mean(data))) > DC_MAX:
        faults.append(f"DC offset {np.mean(data):+.4f}")

    # CLICKY: the waveform must start and end at rest, and ramp in rather than
    # jumping. A step at either edge is an audible tick.
    if abs(float(data[0])) > EDGE_MAX or abs(float(data[-1])) > EDGE_MAX:
        faults.append(f"CLICKY edges start={data[0]:+.3f} end={data[-1]:+.3f}")
    attack = int(sr * ATTACK_MS_MIN / 1000)
    if peak > 0 and float(np.max(np.abs(data[:attack]))) > 0.55 * peak:
        faults.append(f"CLICKY reaches {np.max(np.abs(data[:attack])) / peak:.0%} "
                      f"of peak within {ATTACK_MS_MIN}ms")

    # SHARP: how much of the energy sits in the band that stings
    spec = np.abs(np.fft.rfft(data * np.hanning(len(data)))) ** 2
    freqs = np.fft.rfftfreq(len(data), 1.0 / sr)
    total = float(spec.sum()) or 1.0
    sibilance = float(spec[(freqs >= 8000) & (freqs <= 16000)].sum()) / total
    if sibilance > SIBILANCE_MAX:
        faults.append(f"SHARP {sibilance:.1%} of energy in 8-16kHz "
                      f"> {SIBILANCE_MAX:.0%}")

    centroid = float((freqs * spec).sum() / total)
    return name, peak, rms, dur, sibilance, centroid, faults


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/mobtrumps/sounds"
    files = sorted(glob.glob(os.path.join(d, "*.ogg")))
    if not files:
        print(f"FAIL  no .ogg files in {d}")
        sys.exit(1)
    bad = 0
    print(f"{'sound':16}{'peak':>7}{'rms':>8}{'secs':>7}{'8-16k':>8}{'centroid':>10}  verdict")
    for path in files:
        name, peak, rms, dur, sib, cen, faults = check(path)
        verdict = "ok" if not faults else "; ".join(faults)
        if faults:
            bad += 1
        print(f"{name:16}{peak:7.3f}{rms:8.4f}{dur:7.2f}{sib:7.1%}{cen:9.0f}Hz  {verdict}")
    if bad:
        print(f"FAIL  {bad} of {len(files)} sounds are too loud, clicky, sharp or long.")
        sys.exit(1)
    print(f"PASS  {len(files)} sounds: all quiet, soft-edged, free of sibilance "
          f"and short enough to be punctuation.")


if __name__ == "__main__":
    main()
