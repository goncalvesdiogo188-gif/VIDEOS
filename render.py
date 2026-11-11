#!/usr/bin/env python3
"""Pinky & the Brain — O Plano Perfeito :: render pipeline.

Examples
  python3 render.py                       # both formats, full quality
  python3 render.py --format horizontal   # 1920x1080 only
  python3 render.py --preview             # fast half-resolution draft
  python3 render.py --still 12.5 40       # PNG stills at given seconds
  python3 render.py --start 30 --end 45   # render only a time range (draft)
"""
from __future__ import annotations

import argparse
import multiprocessing as mp
import os
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, ROOT)

from pab import fonts  # noqa: E402,F401  (must precede cairo text)
from pab.compositor import Compositor  # noqa: E402
from pab.episode import FPS, build  # noqa: E402
from pab.voices import ensure_voices, load_clips  # noqa: E402
from pab import audio  # noqa: E402

OUT = os.path.join(ROOT, "output")

_G = {}


def _init(fmt, scale):
    d = build()
    comp = Compositor(fmt, scale)
    comp.cues = d.cues
    comp.background()
    _G["d"], _G["comp"] = d, comp


def _frame(i):
    d, comp = _G["d"], _G["comp"]
    t = i / FPS
    surf = comp.render(d.tl.sampler(t), t)
    return bytes(surf.get_data())


def srt_time(t):
    ms = int(round(t * 1000))
    h, ms = divmod(ms, 3600000)
    m, ms = divmod(ms, 60000)
    s, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def write_srt(cues, path):
    with open(path, "w", encoding="utf-8") as f:
        for i, c in enumerate(cues, 1):
            f.write(f"{i}\n{srt_time(c['start'])} --> {srt_time(c['end'])}\n")
            f.write(f"{c['speaker'].title()}: {c['text']}\n\n")


def render_video(fmt, scale, audio_wav, out_path, start, end, workers, crf, preset, log):
    comp = Compositor(fmt, scale)
    W, H = comp.W, comp.H
    f0, f1 = int(round(start * FPS)), int(round(end * FPS))
    cmd = ["ffmpeg", "-y", "-v", "error",
           "-f", "rawvideo", "-pix_fmt", "bgra", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
           "-ss", f"{start:.3f}", "-t", f"{(f1 - f0) / FPS:.3f}", "-i", audio_wav,
           "-map", "0:v", "-map", "1:a",
           "-c:v", "libx264", "-preset", preset, "-crf", str(crf), "-pix_fmt", "yuv420p",
           "-profile:v", "high", "-tune", "animation", "-r", str(FPS),
           "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
           "-movflags", "+faststart", "-shortest", out_path]
    ff = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    t0 = time.time()
    with mp.get_context("fork").Pool(workers, initializer=_init, initargs=(fmt, scale)) as pool:
        for k, buf in enumerate(pool.imap(_frame, range(f0, f1), chunksize=4)):
            ff.stdin.write(buf)
            if k % 96 == 0:
                el = time.time() - t0
                log(f"  [{fmt}] frame {k}/{f1 - f0}  ({el:.0f}s)")
    ff.stdin.close()
    if ff.wait() != 0:
        raise SystemExit("ffmpeg failed")
    log(f"  [{fmt}] done in {time.time() - t0:.0f}s -> {out_path}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--format", choices=["both", "horizontal", "vertical"], default="both")
    ap.add_argument("--preview", action="store_true", help="half resolution, fast encode")
    ap.add_argument("--scale", type=float, default=None)
    ap.add_argument("--start", type=float, default=0.0)
    ap.add_argument("--end", type=float, default=None)
    ap.add_argument("--still", type=float, nargs="*", help="write PNG stills at these times")
    ap.add_argument("--workers", type=int, default=max(1, os.cpu_count() or 1))
    ap.add_argument("--retts", action="store_true", help="force re-synthesis of all voices")
    ap.add_argument("--crf", type=int, default=17)
    args = ap.parse_args()
    log = lambda m: print(m, flush=True)  # noqa: E731

    os.makedirs(OUT, exist_ok=True)
    log("1/4 voices")
    ensure_voices(force=args.retts, log=log)
    clips = load_clips()
    d = build(clips)
    log(f"    episode duration: {d.duration:.2f}s, {len(d.cues)} subtitle cues")
    fmts = ["horizontal", "vertical"] if args.format == "both" else [args.format]
    scale = args.scale or (0.5 if args.preview else 1.0)

    if args.still is not None:
        for fmt in fmts:
            comp = Compositor(fmt, scale)
            comp.cues = d.cues
            for t in args.still:
                p = os.path.join(OUT, "stills", f"{fmt}_{t:06.2f}.png")
                os.makedirs(os.path.dirname(p), exist_ok=True)
                comp.render(d.tl.sampler(t), t).write_to_png(p)
                log(f"    still {p}")
        return

    log("2/4 subtitles")
    write_srt(d.cues, os.path.join(OUT, "legendas_pt-PT.srt"))
    log("3/4 audio mix")
    wav = os.path.join(OUT, "mix.wav")
    audio.mix(d.tl, clips, d.voice_spans, d.duration, wav, log)
    log("4/4 video")
    end = args.end if args.end is not None else d.duration
    suffix = "_preview" if args.preview else ""
    names = {"horizontal": f"plano_perfeito_16x9{suffix}.mp4",
             "vertical": f"plano_perfeito_9x16{suffix}.mp4"}
    for fmt in fmts:
        render_video(fmt, scale, wav, os.path.join(OUT, names[fmt]), args.start, end,
                     args.workers, args.crf if not args.preview else 23,
                     "slow" if not args.preview else "veryfast", log)


if __name__ == "__main__":
    main()
