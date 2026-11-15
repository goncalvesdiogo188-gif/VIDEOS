"""Voice generation (neural TTS, pt-PT) with an on-disk cache + lip-sync analysis.

Audio for every line is cached in assets/voices/<id>.mp3 together with a JSON
file holding the word timings and a hash of the text/prosody.  A line is only
re-synthesised when its text or prosody changes, so the project re-renders
offline once the voices exist.
"""
from __future__ import annotations

import asyncio
import hashlib
import json
import os
import ssl
import subprocess

import numpy as np

from .script import LINES, VOICES

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VOICE_DIR = os.path.join(ROOT, "assets", "voices")
SR = 48000


def _line_cfg(line):
    v = dict(VOICES[line["who"]])
    for k in ("rate", "pitch", "volume"):
        if k in line:
            v[k] = line[k]
    v["text"] = line.get("tts", line["text"])
    return v


def _hash(cfg):
    return hashlib.sha1(json.dumps(cfg, sort_keys=True).encode()).hexdigest()[:16]


async def _synth(cfg, mp3_path):
    import edge_tts
    import edge_tts.communicate as comm
    ca = os.environ.get("SSL_CERT_FILE") or os.environ.get("REQUESTS_CA_BUNDLE")
    if ca and os.path.exists(ca):          # corporate / sandbox proxies
        comm._SSL_CTX = ssl.create_default_context(cafile=ca)
    c = edge_tts.Communicate(cfg["text"], cfg["voice"], rate=cfg["rate"], pitch=cfg["pitch"],
                             volume=cfg["volume"], boundary="WordBoundary")
    words = []
    with open(mp3_path, "wb") as f:
        async for ch in c.stream():
            if ch["type"] == "audio":
                f.write(ch["data"])
            elif ch["type"] == "WordBoundary":
                words.append((ch["offset"] / 1e7, (ch["offset"] + ch["duration"]) / 1e7, ch["text"]))
    return words


def ensure_voices(force=False, log=print):
    os.makedirs(VOICE_DIR, exist_ok=True)
    for line in LINES:
        cfg = _line_cfg(line)
        h = _hash(cfg)
        mp3 = os.path.join(VOICE_DIR, line["id"] + ".mp3")
        meta = os.path.join(VOICE_DIR, line["id"] + ".json")
        if not force and os.path.exists(mp3) and os.path.exists(meta):
            with open(meta) as f:
                if json.load(f).get("hash") == h:
                    continue
        log(f"  [tts] {line['id']}: {cfg['text']}")
        for attempt in range(4):
            try:
                words = asyncio.run(_synth(cfg, mp3))
                break
            except Exception as e:  # network hiccups
                if attempt == 3:
                    raise
                log(f"    retry ({e})")
        with open(meta, "w") as f:
            json.dump({"hash": h, "cfg": cfg, "words": words}, f, ensure_ascii=False, indent=1)


def decode(path, sr=SR):
    out = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-f", "f32le", "-ac", "1",
                          "-ar", str(sr), "-"], capture_output=True, check=True).stdout
    return np.frombuffer(out, dtype=np.float32).copy()


class Clip:
    """A trimmed voice line: samples, word timings (relative to clip start)."""

    def __init__(self, line):
        self.id = line["id"]
        self.who = line["who"]
        self.text = line["text"]
        mp3 = os.path.join(VOICE_DIR, self.id + ".mp3")
        with open(os.path.join(VOICE_DIR, self.id + ".json")) as f:
            meta = json.load(f)
        x = decode(mp3)
        # trim leading/trailing silence (keep a few ms of air)
        env = np.abs(x)
        thr = max(1e-3, env.max() * 0.02)
        idx = np.nonzero(env > thr)[0]
        a = max(0, idx[0] - int(0.03 * SR)) if len(idx) else 0
        b = min(len(x), idx[-1] + int(0.08 * SR)) if len(idx) else len(x)
        self.samples = x[a:b]
        off = a / SR
        self.words = [(max(0.0, s - off), max(0.0, e - off), w) for s, e, w in meta["words"]]
        self.duration = len(self.samples) / SR

    def mouth_curves(self, fps=24, t0=0.0):
        """Per-frame (time, open, wide, talk) tuples for lip-sync."""
        x = self.samples
        hop = SR // fps
        n = int(np.ceil(len(x) / hop)) + 1
        win = int(0.045 * SR)
        rms = np.zeros(n)
        cen = np.zeros(n)
        for i in range(n):
            c = i * hop
            seg = x[max(0, c - win // 2): c + win // 2]
            if len(seg) < 32:
                continue
            rms[i] = np.sqrt(np.mean(seg ** 2))
            spec = np.abs(np.fft.rfft(seg * np.hanning(len(seg))))
            freqs = np.fft.rfftfreq(len(seg), 1 / SR)
            band = (freqs > 150) & (freqs < 6000)
            cen[i] = (spec[band] * freqs[band]).sum() / (spec[band].sum() + 1e-9)
        db = 20 * np.log10(rms + 1e-6)
        ref = np.percentile(db[rms > 0], 95) if np.any(rms > 0) else -20
        open_ = np.clip((db - (ref - 30)) / 30, 0, 1) ** 1.3
        # vowels (low centroid) open more than sibilants (high centroid)
        sib = np.clip((cen - 2600) / 2200, 0, 1)
        open_ = open_ * (1 - 0.55 * sib)
        wide = np.clip(0.35 + 0.6 * np.clip((cen - 900) / 1800, 0, 1), 0, 1)
        # cartoon timing: close fully at gaps, quick attack, softer release
        sm = np.zeros(n)
        for i in range(n):
            prev = sm[i - 1] if i else 0
            sm[i] = open_[i] if open_[i] > prev else prev * 0.45 + open_[i] * 0.55
        sm[sm < 0.08] = 0.0
        sm[-1] = 0.0
        out = [(t0 + i / fps, float(sm[i]), float(wide[i]), float(np.clip(open_[i] * 1.2, 0, 1)))
               for i in range(n)]
        out[-1] = (out[-1][0], 0.0, out[-1][2], 0.0)
        return out


def load_clips():
    return {line["id"]: Clip(line) for line in LINES}
