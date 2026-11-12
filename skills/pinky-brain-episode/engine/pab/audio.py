"""Original music and sound effects, synthesised from scratch with numpy, plus
the final mix (voices + music + sfx + ambience) with ducking, reverb and
loudness normalisation.  No samples or third-party recordings are used.
"""
from __future__ import annotations

import json
import math
import subprocess

import numpy as np
from scipy.signal import butter, fftconvolve, sosfilt

SR = 48000
RNG = np.random.default_rng(1234)


def mtof(m):
    return 440.0 * 2 ** ((m - 69) / 12)


NOTE = {n: i for i, n in enumerate(["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"])}
NOTE.update({"Db": 1, "Eb": 3, "Gb": 6, "Ab": 8, "Bb": 10})


def n(name):
    """'D4' -> midi."""
    pitch, octv = name[:-1], int(name[-1])
    return 12 * (octv + 1) + NOTE[pitch]


def tvec(dur):
    return np.arange(int(dur * SR)) / SR


def adsr(nsamp, a=0.01, d=0.05, s=0.8, r=0.05, sustain_until=None):
    t = np.arange(nsamp) / SR
    total = nsamp / SR
    rel_start = total - r if sustain_until is None else sustain_until
    env = np.where(t < a, t / max(a, 1e-4),
                   np.where(t < a + d, 1 - (1 - s) * (t - a) / max(d, 1e-4), s))
    env = env * np.clip((total - t) / max(r, 1e-4), 0, 1) if sustain_until is None else env
    return env


def bp(x, lo, hi, order=2):
    sos = butter(order, [lo, hi], btype="band", fs=SR, output="sos")
    return sosfilt(sos, x)


def lp(x, f, order=2):
    return sosfilt(butter(order, f, btype="low", fs=SR, output="sos"), x)


def hp(x, f, order=2):
    return sosfilt(butter(order, f, btype="high", fs=SR, output="sos"), x)


# ---------------------------------------------------------------------------
# Instruments (all additive / subtractive synthesis)
# ---------------------------------------------------------------------------
def pizz(f, dur=0.5, amp=1.0):
    t = tvec(dur + 0.4)
    y = np.zeros_like(t)
    for k in range(1, 11):
        if f * k > 12000:
            break
        y += (1 / k ** 1.25) * np.sin(2 * np.pi * f * k * t + k) * np.exp(-t * (5 + 3.2 * k))
    y *= np.clip(t / 0.003, 0, 1)
    return amp * 0.5 * y


def bassoon(f, dur, amp=1.0, vib=0.0):
    t = tvec(dur + 0.08)
    ph = 2 * np.pi * f * t + vib * np.sin(2 * np.pi * 5.2 * t) * np.clip((t - 0.15) / 0.2, 0, 1) * 0.6
    y = np.zeros_like(t)
    weights = [0.5, 1.0, 0.9, 0.7, 0.45, 0.35, 0.25, 0.18, 0.12, 0.08, 0.05, 0.04]
    for k, w in enumerate(weights, 1):
        if f * k > 10000:
            break
        y += w * np.sin(k * ph)
    env = adsr(len(t), 0.025, 0.05, 0.85, 0.07)
    return amp * 0.22 * y * env


def clarinet(f, dur, amp=1.0):
    t = tvec(dur + 0.06)
    ph = 2 * np.pi * f * t + 0.004 * np.sin(2 * np.pi * 5 * t) * 2 * np.pi
    y = np.zeros_like(t)
    for k in range(1, 16, 2):
        if f * k > 11000:
            break
        y += (1 / k) * np.sin(k * ph)
    y += 0.08 * np.sin(2 * ph)
    env = adsr(len(t), 0.02, 0.04, 0.9, 0.05)
    return amp * 0.25 * y * env


def brass(f, dur, amp=1.0, swell=0.06, wah=0.0, vib=0.0, bend=0.0):
    t = tvec(dur + 0.12)
    env = adsr(len(t), swell, 0.1, 0.85, 0.12)
    freq = f * (1 + bend * np.clip(1 - t / 0.12, 0, 1)) * (1 + vib * 0.012 * np.sin(2 * np.pi * 5.5 * t))
    ph = 2 * np.pi * np.cumsum(freq) / SR
    bright = env.copy()
    if wah > 0:
        bright = bright * (0.55 + 0.45 * np.sin(2 * np.pi * wah * t - np.pi / 2))
    y = np.zeros_like(t)
    for k in range(1, 18):
        if f * k > 12000:
            break
        y += (1 / k) * np.sin(k * ph) * bright ** (0.25 + 0.22 * k)
    return amp * 0.24 * y * env


def strings(f, dur, amp=1.0, trem=0.0, attack=0.4):
    t = tvec(dur + 0.5)
    y = np.zeros_like(t)
    for det in (-0.004, 0.0, 0.005):
        ph = 2 * np.pi * f * (1 + det) * t + RNG.random() * 6
        for k in range(1, 12):
            if f * k > 9000:
                break
            y += (1 / k) * np.sin(k * ph)
    y = lp(y, 3500)
    env = adsr(len(t), attack, 0.2, 0.9, 0.5)
    if trem:
        env *= 0.65 + 0.35 * np.sin(2 * np.pi * trem * t)
    return amp * 0.07 * y * env


def glock(f, dur=1.2, amp=1.0):
    t = tvec(dur)
    y = (np.sin(2 * np.pi * f * t) * np.exp(-t * 3)
         + 0.4 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t * 7)
         + 0.2 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t * 12))
    return amp * 0.25 * y * np.clip(t / 0.002, 0, 1)


def timpani(f, dur=1.8, amp=1.0):
    t = tvec(dur)
    fr = f * (1 + 0.04 * np.exp(-t * 20))
    ph = 2 * np.pi * np.cumsum(fr) / SR
    y = (np.sin(ph) + 0.5 * np.sin(1.5 * ph) * np.exp(-t * 3) + 0.25 * np.sin(1.98 * ph) * np.exp(-t * 4))
    y = y * np.exp(-t * 2.2)
    y += lp(RNG.standard_normal(len(t)), 900) * np.exp(-t * 30) * 0.6
    return amp * 0.5 * y


def cymbal(dur=2.5, amp=1.0):
    t = tvec(dur)
    y = hp(RNG.standard_normal(len(t)), 5000, 2) * np.exp(-t * 2.2)
    y += hp(RNG.standard_normal(len(t)), 2500) * np.exp(-t * 9) * 0.6
    return amp * 0.18 * y


def brush(amp=1.0):
    t = tvec(0.12)
    return amp * 0.08 * hp(RNG.standard_normal(len(t)), 3000) * np.exp(-t * 45)


# ---------------------------------------------------------------------------
# Sound effects
# ---------------------------------------------------------------------------
def sfx_whoosh(dur=0.35, amp=1.0, lo=500, hi=3500):
    t = tvec(dur)
    x = RNG.standard_normal(len(t))
    env = np.sin(np.pi * t / dur) ** 2
    y = bp(x, lo, hi) * env
    return amp * 0.35 * y


def sfx_step(amp=1.0):
    t = tvec(0.12)
    y = lp(RNG.standard_normal(len(t)), 1200) * np.exp(-t * 60) * 0.6
    y += np.sin(2 * np.pi * 110 * t) * np.exp(-t * 40)
    return amp * 0.25 * y


def sfx_knock(amp=1.0):
    t = tvec(0.15)
    y = (np.sin(2 * np.pi * 420 * t) * np.exp(-t * 45) + 0.5 * np.sin(2 * np.pi * 980 * t) * np.exp(-t * 70)
         + 0.3 * hp(RNG.standard_normal(len(t)), 2000) * np.exp(-t * 200))
    return amp * 0.4 * y


def sfx_scratch(dur=0.9, amp=1.0):
    t = tvec(dur)
    x = hp(RNG.standard_normal(len(t)), 2500)
    gate = (np.sin(2 * np.pi * 7.5 * t) > 0.2).astype(float)
    gate = lp(gate, 60)
    return amp * 0.12 * x * gate


def sfx_thunder(dur=4.0, amp=1.0):
    t = tvec(dur)
    crack = hp(RNG.standard_normal(len(t)), 800) * np.exp(-t * 14)
    brown = np.cumsum(RNG.standard_normal(len(t)))
    brown = hp(brown, 25)
    brown /= np.abs(brown).max() + 1e-9
    rumble = lp(brown, 220) * (np.exp(-t * 1.1) * (1 - np.exp(-t * 18)))
    rumble *= 0.7 + 0.3 * lp(np.abs(RNG.standard_normal(len(t))), 6) * 3
    y = 0.4 * crack + 2.6 * rumble
    return amp * 0.5 * y / (np.abs(y).max() + 1e-9)


def sfx_static(dur=0.6, amp=1.0):
    t = tvec(dur)
    y = bp(RNG.standard_normal(len(t)), 800, 7000)
    env = np.clip(t / 0.01, 0, 1) * np.clip((dur - t) / 0.15, 0, 1)
    buzz = np.sign(np.sin(2 * np.pi * 120 * t)) * 0.3
    return amp * 0.12 * (y + buzz * np.exp(-t * 6)) * env


def sfx_bwip(amp=1.0, up=True):
    t = tvec(0.18)
    f = (500 + 900 * t / 0.18) if up else (1300 - 900 * t / 0.18)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return amp * 0.18 * np.sin(ph) * np.sin(np.pi * t / 0.18)


def sfx_bubble(amp=1.0):
    t = tvec(0.06)
    f = 350 + 900 * t / 0.06
    ph = 2 * np.pi * np.cumsum(f) / SR
    return amp * 0.08 * np.sin(ph) * np.sin(np.pi * t / 0.06)


def sfx_pop(amp=1.0):
    t = tvec(0.09)
    f = 900 * np.exp(-t * 25) + 200
    return amp * 0.3 * np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 35)


SFX = {
    "whoosh": lambda a: sfx_whoosh(0.35, a),
    "whoosh_big": lambda a: sfx_whoosh(0.6, a, 250, 2500),
    "swish": lambda a: sfx_whoosh(0.18, a, 1500, 6000),
    "step": sfx_step,
    "knock": sfx_knock,
    "scratch": lambda a: sfx_scratch(0.9, a),
    "thunder": lambda a: sfx_thunder(4.5, a),
    "static": lambda a: sfx_static(0.7, a),
    "bwip": lambda a: sfx_bwip(a, True),
    "bwip_down": lambda a: sfx_bwip(a, False),
    "pop": sfx_pop,
    "plink": lambda a: glock(mtof(n("A5")), 1.4, a) + glock(mtof(n("D6")), 1.4, a * 0.6),
}


# ---------------------------------------------------------------------------
# Music cues
# ---------------------------------------------------------------------------
BPM = 112
BEAT = 60 / BPM

# 8-bar sneaky theme in D minor.  (bar, beat, note, beats)
THEME_BASS = []
_prog = ["D2", "D2", "G1", "A1", "D2", "A#1", "E2", "A1"]
_fifth = ["A2", "A2", "D2", "E2", "A2", "F2", "A#1", "E2"]
for b, (r, f5) in enumerate(zip(_prog, _fifth)):
    THEME_BASS += [(b, 0, r, 0.5), (b, 1, f5, 0.5), (b, 2, r, 0.5), (b, 3, f5, 0.4), (b, 3.5, r, 0.3)]
THEME_MEL = [
    (0, 0, "D4", .5), (0, .5, "F4", .5), (0, 1, "A4", .5), (0, 2, "G#4", .5), (0, 2.5, "A4", .5),
    (1, 0, "D4", .5), (1, .5, "F4", .5), (1, 1, "A4", .5), (1, 2, "C5", .5), (1, 2.5, "A#4", .5), (1, 3, "A4", .5),
    (2, 0, "G4", .5), (2, .5, "A#4", .5), (2, 1, "D5", .5), (2, 2, "C#5", .5), (2, 2.5, "D5", .5),
    (3, 0, "E4", .5), (3, 1, "G4", .5), (3, 1.5, "F4", .5), (3, 2, "E4", .5), (3, 3, "C#4", .5),
    (4, 0, "D4", .5), (4, .5, "F4", .5), (4, 1, "A4", .5), (4, 2, "D5", .5), (4, 3, "A4", .5),
    (5, 0, "A#4", .5), (5, .5, "A4", .5), (5, 1, "G4", .5), (5, 2, "F4", 1.0),
    (6, 0, "E4", .5), (6, .5, "F4", .5), (6, 1, "G4", .5), (6, 2, "A#4", .5), (6, 3, "G4", .5),
    (7, 0, "A4", .5), (7, 1, "C#5", .5), (7, 2, "E5", .5), (7, 3, "A4", .5),
]


class Bus:
    def __init__(self, dur):
        self.L = np.zeros(int(dur * SR) + SR * 6)
        self.R = np.zeros_like(self.L)

    def add(self, y, t, gain=1.0, pan=0.0):
        i = int(round(t * SR))
        if i < 0:
            y = y[-i:]
            i = 0
        j = min(len(self.L), i + len(y))
        if j <= i:
            return
        gl = math.cos((pan + 1) * math.pi / 4) * math.sqrt(2)
        gr = math.sin((pan + 1) * math.pi / 4) * math.sqrt(2)
        self.L[i:j] += y[:j - i] * gain * gl
        self.R[i:j] += y[:j - i] * gain * gr

    def stereo(self):
        return np.stack([self.L, self.R])


def render_theme(bus, t0, t1, gain=1.0, melody=True):
    """Loop the sneaky theme from t0 to t1, fading out at the end."""
    bar = 4 * BEAT
    nbar = int(math.ceil((t1 - t0) / bar))
    for b in range(nbar):
        tb = t0 + b * bar
        idx = b % 8
        for (bb, beat, note, beats) in THEME_BASS:
            if bb != idx:
                continue
            tt = tb + beat * BEAT
            if tt < t1:
                bus.add(pizz(mtof(n(note)), beats * BEAT, 1.0), tt, gain * 0.9, -0.2)
        if melody:
            for (bb, beat, note, beats) in THEME_MEL:
                if bb != idx:
                    continue
                tt = tb + beat * BEAT
                if tt < t1:
                    inst = bassoon if (b // 8) % 2 == 0 else clarinet
                    bus.add(inst(mtof(n(note)), beats * BEAT * 0.75, 0.8), tt, gain * 0.7, 0.25)
        for k in range(4):  # brushes on the off-beats
            tt = tb + (k + 0.5) * BEAT
            if tt < t1:
                bus.add(brush(0.8 if k % 2 else 0.5), tt, gain, 0.4)
        if idx in (0, 4):
            bus.add(glock(mtof(n("D6")), 1.0, 0.25), tb, gain, 0.3)


def render_intro(bus, t0, dur):
    # timpani roll crescendo + tremolo strings, then a brass stab on the title
    roll_end = t0 + dur * 0.55
    tt = t0
    while tt < roll_end:
        u = (tt - t0) / (roll_end - t0)
        bus.add(timpani(mtof(n("D2")), 0.4, 0.15 + 0.6 * u), tt, 0.6, -0.1)
        tt += 1 / 16
    for note in ("D3", "A3", "D4", "F4"):
        bus.add(strings(mtof(n(note)), dur * 0.55, 0.9, trem=11, attack=dur * 0.4), t0, 0.9)
    hit = roll_end
    for note, pan in (("D3", -0.2), ("A3", 0.1), ("D4", 0.0), ("F4", 0.2)):
        bus.add(brass(mtof(n(note)), 0.9, 1.0, swell=0.02), hit, 1.0, pan)
    bus.add(timpani(mtof(n("D2")), 2.0, 1.0), hit, 0.9)
    bus.add(cymbal(2.5, 1.0), hit, 0.8, 0.2)
    for i, note in enumerate(("D5", "F5", "A5", "D6")):
        bus.add(glock(mtof(n(note)), 1.2, 0.6), hit + 0.25 + i * 0.09, 0.8, 0.3)


def render_drone(bus, t0, t1, gain=1.0):
    dur = t1 - t0
    for note in ("D2", "A2", "D3"):
        bus.add(strings(mtof(n(note)), dur, 1.0, trem=0, attack=dur * 0.8), t0, gain)
    bus.add(strings(mtof(n("D#3")), dur, 0.6, trem=8, attack=dur * 0.9), t0, gain * 0.7)


def render_hit(bus, t):
    for note, pan in (("D2", 0), ("D3", -0.2), ("F3", 0.2), ("A3", -0.1), ("D4", 0.1)):
        bus.add(brass(mtof(n(note)), 1.6, 1.1, swell=0.015), t, 1.0, pan)
    bus.add(timpani(mtof(n("D2")), 2.5, 1.3), t, 1.0)
    bus.add(timpani(mtof(n("A1")), 2.5, 0.9), t + 0.02, 0.8)
    bus.add(cymbal(3.0, 1.3), t, 0.9)
    for note in ("D3", "F3", "A3"):
        bus.add(strings(mtof(n(note)), 2.0, 1.2, trem=12, attack=0.05), t, 0.9)


def render_suspense(bus, t0, t1, gain=1.0):
    dur = t1 - t0
    for note, tr in (("D3", 9), ("F3", 10), ("G#3", 11)):
        bus.add(strings(mtof(n(note)), dur, 1.0, trem=tr, attack=1.5), t0, gain)
    bus.add(strings(mtof(n("D2")), dur, 1.0, attack=2.0), t0, gain)


def render_sting(bus, t):
    """'dun dun DUUUN' – the decisive question."""
    for i, (note, d, a) in enumerate((("D3", 0.28, 0.8), ("D3", 0.28, 0.9), ("D3", 1.6, 1.2))):
        tt = t + i * 0.36
        for nn in (note, "D2"):
            bus.add(brass(mtof(n(nn)), d, a, swell=0.01), tt, 1.0)
        bus.add(timpani(mtof(n("D2")), 1.4, a), tt, 0.9)
    tt = t + 0.72
    for nn in ("F3", "G#3"):
        bus.add(brass(mtof(n(nn)), 1.6, 1.0, swell=0.01), tt, 0.9)
    bus.add(strings(mtof(n("G#4")), 1.8, 1.0, trem=13, attack=0.05), tt, 0.8)


def render_sad_trombone(bus, t):
    """Comic 'wah wah wah waaah' as Brain's brain blue-screens."""
    notes = (("G3", 0.42), ("F#3", 0.42), ("F3", 0.42), ("E3", 1.6))
    tt = t
    for i, (note, d) in enumerate(notes):
        last = i == len(notes) - 1
        y = brass(mtof(n(note)), d, 1.0, swell=0.05, wah=2.4 if last else 2.0 / d / 2,
                  vib=1.5 if last else 0.0, bend=-0.03)
        bus.add(lp(y, 2600), tt, 1.0, 0.0)
        tt += d + 0.04
    # a soft bassoon "bloop" underneath
    bus.add(bassoon(mtof(n("E2")), 1.4, 0.7), tt - 1.6, 0.8)


def render_outro(bus, t0, dur):
    """Gentle pizzicato + glock reprise ending on a final D minor chord."""
    seq = [("D4", 0), ("F4", .5), ("A4", 1), ("G#4", 2), ("A4", 2.5), ("F4", 4), ("E4", 5), ("D4", 6)]
    for note, beat in seq:
        tt = t0 + beat * BEAT
        bus.add(pizz(mtof(n(note)), 0.5, 0.9), tt, 0.8, 0.1)
        bus.add(glock(mtof(n(note) + 12), 1.0, 0.25), tt, 0.6, 0.3)
    end = t0 + 6 * BEAT
    for note in ("D2", "D3", "F3", "A3"):
        bus.add(pizz(mtof(n(note)), 1.0, 1.0), end + 1.0 * BEAT, 0.8)
    bus.add(glock(mtof(n("D6")), 2.0, 0.5), end + 1.0 * BEAT, 0.7, 0.2)


# ---------------------------------------------------------------------------
# Reverb + mixing
# ---------------------------------------------------------------------------
def make_ir(dur, decay, seed):
    r = np.random.default_rng(seed)
    t = tvec(dur)
    ir = r.standard_normal(len(t)) * np.exp(-t * decay)
    ir = lp(ir, 6000)
    ir[: int(0.012 * SR)] = 0
    return ir / np.sqrt((ir ** 2).sum())


def reverb(st, dur=1.8, decay=3.2, wet=0.25):
    out = st.copy()
    for ch in range(2):
        ir = make_ir(dur, decay, 10 + ch)
        out[ch] = st[ch] + wet * fftconvolve(st[ch], ir)[: st.shape[1]]
    return out


def envelope_follower(x, attack=0.03, release=0.35):
    a = np.exp(-1 / (attack * SR))
    r = np.exp(-1 / (release * SR))
    # decimated follower for speed
    hop = 240
    xa = np.abs(x)
    nfr = len(xa) // hop + 1
    frames = np.array([xa[i * hop:(i + 1) * hop].max() if i * hop < len(xa) else 0 for i in range(nfr)])
    env = np.zeros(nfr)
    a_h, r_h = a ** hop, r ** hop
    for i in range(nfr):
        prev = env[i - 1] if i else 0
        c = a_h if frames[i] > prev else r_h
        env[i] = c * prev + (1 - c) * frames[i]
    return np.repeat(env, hop)[: len(x)]


def mix(timeline, clips, voice_spans, duration, out_wav, log=print):
    """voice_spans: list of (clip_id, start_time, pan)."""
    voices = Bus(duration)
    music = Bus(duration)
    sfx = Bus(duration)
    amb = Bus(duration)

    for cid, t, pan in voice_spans:
        voices.add(clips[cid].samples.astype(np.float64), t, 1.0, pan)

    for (t, kind, d) in sorted(timeline.events, key=lambda e: e[0]):
        if kind == "sfx":
            y = SFX[d["name"]](d.get("amp", 1.0))
            sfx.add(y, t, d.get("gain", 1.0), d.get("pan", 0.0))
        elif kind == "music":
            name = d["name"]
            if name == "theme":
                render_theme(music, t, t + d["dur"], d.get("gain", 1.0), d.get("melody", True))
            elif name == "intro":
                render_intro(music, t, d["dur"])
            elif name == "drone":
                render_drone(music, t, t + d["dur"], d.get("gain", 1.0))
            elif name == "hit":
                render_hit(music, t)
            elif name == "suspense":
                render_suspense(music, t, t + d["dur"], d.get("gain", 1.0))
            elif name == "sting":
                render_sting(music, t)
            elif name == "sad_trombone":
                render_sad_trombone(music, t)
            elif name == "outro":
                render_outro(music, t, d.get("dur", 4))

    # ambience: lab hum + flask bubbles, gated by the amb.level track
    total = len(amb.L)
    tt = np.arange(total) / SR
    hum = 0.012 * (np.sin(2 * np.pi * 50 * tt) + 0.5 * np.sin(2 * np.pi * 100 * tt))
    hiss = 0.004 * lp(RNG.standard_normal(total), 1800)
    lvl_t = np.arange(0, total / SR, 1 / 50)
    lvl = np.array([timeline.get("amb.level", x, 1.0) for x in lvl_t])
    lvl = np.interp(tt, lvl_t, lvl)
    amb.L += (hum + hiss) * lvl
    amb.R += (hum * 0.9 + hiss) * lvl
    bt = 0.3
    while bt < total / SR - 1:
        if timeline.get("amb.level", bt, 1.0) > 0.3:
            amb.add(sfx_bubble(RNG.uniform(0.3, 0.8)), bt, 0.6 * timeline.get("amb.level", bt, 1.0), 0.6)
        bt += RNG.uniform(0.25, 0.9)

    v = reverb(voices.stereo(), 0.5, 9.0, 0.10)
    m = reverb(music.stereo(), 2.2, 2.6, 0.32)
    s = reverb(sfx.stereo(), 0.9, 5.0, 0.15)
    a = amb.stereo()

    # duck music & ambience under dialogue
    venv = envelope_follower(v[0] + v[1])
    venv = venv / (venv.max() + 1e-9)
    duck = 1.0 - 0.6 * np.clip(venv * 3, 0, 1)
    mus_level = np.array([timeline.get("music.level", x, 1.0) for x in lvl_t])
    mus_level = np.interp(tt, lvl_t, mus_level)

    out = (v * 1.0 + m * 0.42 * duck * mus_level + s * 0.8 + a * 0.6 * duck)
    n_out = int(duration * SR)
    out = out[:, :n_out]
    # soft limiter
    peak = np.abs(out).max()
    out = out / max(peak, 1e-9) * 0.9
    out = np.tanh(out * 1.2) / np.tanh(1.2)
    pcm = (np.clip(out, -1, 1) * 32767).astype("<i2").T.copy()
    raw = out_wav + ".raw.wav"
    _write_wav(raw, pcm)
    _loudnorm(raw, out_wav, log)


def _write_wav(path, pcm):
    import wave
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def _loudnorm(src_wav, dst_wav, log):
    """Two-pass EBU R128 normalisation to -14 LUFS (social platforms)."""
    target = "I=-14:TP=-1.5:LRA=11"
    p = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", src_wav, "-af",
                        f"loudnorm={target}:print_format=json", "-f", "null", "-"],
                       capture_output=True, text=True)
    txt = p.stderr
    js = json.loads(txt[txt.rindex("{"): txt.rindex("}") + 1])
    log(f"  [audio] measured {js['input_i']} LUFS, peak {js['input_tp']} dBTP")
    af = (f"loudnorm={target}:measured_I={js['input_i']}:measured_TP={js['input_tp']}:"
          f"measured_LRA={js['input_lra']}:measured_thresh={js['input_thresh']}:"
          f"offset={js['target_offset']}:linear=true")
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", src_wav, "-af", af, "-ar", str(SR), dst_wav],
                   check=True)
