#!/usr/bin/env python3
"""Generate the game's soft SFX as 16-bit mono WAVs into app/src/main/res/raw/.

Python stdlib only (like tools/preprocess). Sounds are synthesized sine plucks with
exponential decay — deliberately quiet and round ("soft bundled SFX", CLAUDE.md §12
step 9), nothing percussive or chip-tune. Re-run any time the sound design changes:

    python3 generate_sfx.py
"""

import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 44100
OUT_DIR = Path(__file__).resolve().parent.parent.parent / "app" / "src" / "main" / "res" / "raw"


def pluck(freq, duration, amp=0.30, decay_tau=0.070, attack=0.004, harmonic_amp=0.12):
    """A soft sine pluck: fast attack, exponential decay, one quiet octave harmonic."""
    n = int(SAMPLE_RATE * duration)
    samples = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = (t / attack if t < attack else math.exp(-(t - attack) / decay_tau))
        v = amp * env * (
            math.sin(2 * math.pi * freq * t)
            + harmonic_amp * math.sin(2 * math.pi * freq * 2 * t)
        )
        samples.append(v)
    return samples


def mix(*layers):
    """Overlay (offset_seconds, samples) layers into one buffer."""
    total = max(int(off * SAMPLE_RATE) + len(s) for off, s in layers)
    out = [0.0] * total
    for off, s in layers:
        start = int(off * SAMPLE_RATE)
        for i, v in enumerate(s):
            out[start + i] += v
    return out


def write_wav(name, samples):
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    path = OUT_DIR / f"{name}.wav"
    peak = max(1e-9, max(abs(v) for v in samples))
    scale = min(1.0, 0.9 / peak)  # normalize only if clipping
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        frames = b"".join(
            struct.pack("<h", int(max(-1.0, min(1.0, v * scale)) * 32767)) for v in samples
        )
        w.writeframes(frames)
    print(f"wrote {path} ({len(samples) / SAMPLE_RATE * 1000:.0f}ms)")


def main():
    # Clear: a single bright-but-gentle G5 ping.
    write_wav("sfx_clear", pluck(784.0, 0.28))

    # Miss: a low, muted G3 thud — longer decay, no harmonic sparkle.
    write_wav("sfx_miss", pluck(196.0, 0.42, amp=0.34, decay_tau=0.13, harmonic_amp=0.05))

    # Level complete: an ascending two-note figure (G5 -> C6).
    write_wav("sfx_win", mix(
        (0.00, pluck(784.0, 0.26)),
        (0.14, pluck(1046.5, 0.34)),
    ))

    # Game over: a descending two-note figure (A4 -> E4), softer and slower.
    write_wav("sfx_game_over", mix(
        (0.00, pluck(440.0, 0.30, amp=0.26, decay_tau=0.10)),
        (0.20, pluck(329.6, 0.42, amp=0.26, decay_tau=0.14)),
    ))


if __name__ == "__main__":
    main()
