"""Converte SVG em PNG.

Usa a biblioteca `resvg-py` (instala-se com pip, funciona em Windows, Mac e Linux).
Se não existir, tenta o programa `rsvg-convert` (librsvg).
"""
import shutil
import subprocess
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
FONTES = str(RAIZ / "assets" / "fonts")

try:
    import resvg_py
except ImportError:  # pragma: no cover
    resvg_py = None


def svg_para_png(svg: str) -> bytes:
    if resvg_py is not None:
        return bytes(resvg_py.svg_to_bytes(svg_string=svg, font_dirs=[FONTES]))
    if shutil.which("rsvg-convert"):
        return subprocess.run(["rsvg-convert", "-f", "png"], input=svg.encode("utf-8"),
                              capture_output=True, check=True).stdout
    raise RuntimeError("Falta um conversor de SVG. Corre:  pip install resvg-py")
