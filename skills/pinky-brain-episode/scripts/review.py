#!/usr/bin/env python3
"""Review helpers, run from inside an episode project.

    python3 review.py audio [output/mix.wav]              # loudness per second
    python3 review.py sheet <out.png> <png> [<png> ...]   # custom contact sheet
                                                          # (render.py --still already makes them)

The audio report prints RMS/peak per second with a bar so you can check, without
listening, that dialogue sits above the music (~ -14..-20 dB RMS), that
"silence" beats are really silent (< -45 dB) and that stings land where planned.
"""
import sys
import wave

import numpy as np


def sheet(out, files):
    from PIL import Image, ImageDraw
    ims = [Image.open(f) for f in files]
    landscape = ims[0].width > ims[0].height
    w = 640 if landscape else 300
    h = int(ims[0].height * w / ims[0].width)
    cols = 3 if landscape else 5
    rows = (len(ims) + cols - 1) // cols
    S = Image.new("RGB", (cols * w, rows * h), "black")
    dr = ImageDraw.Draw(S)
    for i, (im, f) in enumerate(zip(ims, files)):
        x, y = (i % cols) * w, (i // cols) * h
        S.paste(im.convert("RGB").resize((w, h)), (x, y))
        dr.text((x + 5, y + 5), f.rsplit("_", 1)[-1], fill="yellow")
    S.save(out)
    print(out)


def audio(path="output/mix.wav"):
    w = wave.open(path)
    sr = w.getframerate()
    x = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").reshape(-1, w.getnchannels())
    m = x.astype(float).mean(1) / 32768
    for s in range(len(m) // sr):
        seg = m[s * sr:(s + 1) * sr]
        r = 20 * np.log10(np.sqrt((seg ** 2).mean()) + 1e-9)
        pk = 20 * np.log10(np.abs(seg).max() + 1e-9)
        print(f"{s:3d}s rms {r:6.1f} pk {pk:6.1f} " + "#" * max(0, int((r + 70) / 2)))


if __name__ == "__main__":
    if len(sys.argv) >= 4 and sys.argv[1] == "sheet":
        sheet(sys.argv[2], sys.argv[3:])
    elif len(sys.argv) >= 2 and sys.argv[1] == "audio":
        audio(*sys.argv[2:3])
    else:
        sys.exit(__doc__)
