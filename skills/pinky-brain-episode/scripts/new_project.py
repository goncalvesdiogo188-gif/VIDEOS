#!/usr/bin/env python3
"""Create a new Pinky & Brain episode project from the bundled engine.

    python3 new_project.py <destination-folder>

Copies the engine (rigs, lab set, TTS + lip-sync, synth audio, renderer) into
the destination.  The copied pab/script.py and pab/episode.py contain the
reference episode "O Plano Perfeito" as a worked example: rewrite them for the
new story.
"""
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ENGINE = os.path.join(os.path.dirname(HERE), "engine")


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    dest = os.path.abspath(sys.argv[1])
    if os.path.exists(os.path.join(dest, "render.py")):
        sys.exit(f"{dest} already contains a project; refusing to overwrite it.")
    os.makedirs(dest, exist_ok=True)
    for name in ("pab", "render.py", "requirements.txt", "assets"):
        src = os.path.join(ENGINE, name)
        dst = os.path.join(dest, name)
        if os.path.isdir(src):
            shutil.copytree(src, dst, ignore=shutil.ignore_patterns("__pycache__"))
        else:
            shutil.copy2(src, dst)
    os.makedirs(os.path.join(dest, "assets", "voices"), exist_ok=True)
    os.makedirs(os.path.join(dest, "output"), exist_ok=True)
    with open(os.path.join(dest, ".gitignore"), "w") as f:
        f.write("__pycache__/\noutput/stills/\noutput/*_preview.mp4\noutput/*.raw.wav\noutput/mix.wav\n")
    print(f"Project created in {dest}")
    print("Next: edit pab/script.py (story) and pab/episode.py (staging), then run render.py")


if __name__ == "__main__":
    main()
