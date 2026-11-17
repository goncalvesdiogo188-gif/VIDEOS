"""Verifica se o computador tem tudo o que a fábrica precisa.

Uso:  python -m fabrica.verificar
"""
import shutil
import subprocess
import sys

OK, FALTA = "[OK]   ", "[FALTA]"


def main():
    tudo_ok = True
    v = sys.version_info
    if v >= (3, 9):
        print(f"{OK} Python {v.major}.{v.minor}")
    else:
        tudo_ok = False
        print(f"{FALTA} Python 3.9 ou mais recente (tens {v.major}.{v.minor}). Instala em python.org")

    try:
        import resvg_py  # noqa: F401
        print(f"{OK} resvg-py (desenha os bonecos)")
    except ImportError:
        if shutil.which("rsvg-convert"):
            print(f"{OK} rsvg-convert (desenha os bonecos)")
        else:
            tudo_ok = False
            print(f"{FALTA} resvg-py  ->  corre:  python -m pip install -r requirements.txt")

    try:
        import edge_tts  # noqa: F401
        print(f"{OK} edge-tts (vozes; precisa de internet)")
    except ImportError:
        tudo_ok = False
        print(f"{FALTA} edge-tts  ->  corre:  python -m pip install -r requirements.txt")

    if shutil.which("ffmpeg"):
        print(f"{OK} ffmpeg (monta o vídeo)")
    else:
        tudo_ok = False
        print(f"{FALTA} ffmpeg  ->  Windows: winget install Gyan.FFmpeg   (depois fecha e abre o terminal)")

    claude = shutil.which("claude")
    if claude:
        try:
            out = subprocess.run([claude, "--version"], capture_output=True, text=True, timeout=30).stdout.strip()
        except Exception:
            out = "?"
        print(f"{OK} Claude Code {out} (escreve os guiões; modo não interativo: claude -p)")
    else:
        print(f"{FALTA} Claude Code (só é preciso a partir da fase 2)  ->  ver README, secção Instalação")

    print("\nTudo pronto!" if tudo_ok else "\nInstala o que está em [FALTA] e corre isto outra vez.")
    return 0 if tudo_ok else 1


if __name__ == "__main__":
    sys.exit(main())
