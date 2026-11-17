"""Verifica se o computador tem tudo o que a fábrica precisa.

Uso:  python -m fabrica.verificar          (ou: fabrica verificar)
      python -m fabrica.verificar --rapido (não testa o login do Claude)
"""
import shutil
import subprocess
import sys

OK, FALTA = "[OK]   ", "[FALTA]"


def main(rapido=None):
    if rapido is None:
        rapido = "--rapido" in sys.argv
    tudo_ok = True
    v = sys.version_info
    if v >= (3, 10):
        print(f"{OK} Python {v.major}.{v.minor}")
    else:
        tudo_ok = False
        print(f"{FALTA} Python 3.10 ou mais recente (tens {v.major}.{v.minor}). Instala em python.org")

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

    from .guionista import achar_claude
    claude = achar_claude()
    if claude:
        try:
            out = subprocess.run([claude, "--version"], capture_output=True, text=True, timeout=30).stdout.strip()
        except Exception:
            out = "?"
        print(f"{OK} Claude Code {out} (escreve os guiões)")
        if not rapido:
            print("        A testar o login do Claude (demora uns segundos)...", flush=True)
            if _claude_responde():
                print(f"{OK} Login do Claude feito")
            else:
                tudo_ok = False
                print(f"{FALTA} Login do Claude  ->  abre o PowerShell, escreve  claude  e faz login no navegador")
    else:
        tudo_ok = False
        print(f"{FALTA} Claude Code  ->  corre o instalar.bat (ou ver README, secção Instalação)")

    print("\nTudo pronto!" if tudo_ok else "\nInstala o que está em [FALTA] e corre isto outra vez.")
    return 0 if tudo_ok else 1


def _claude_responde():
    """Faz uma pergunta mínima ao Claude em modo não interativo."""
    try:
        from .guionista import perguntar_claude
        return "OK" in perguntar_claude("Responde apenas com a palavra OK.", timeout=90).upper()
    except Exception:
        return False


if __name__ == "__main__":
    sys.exit(main())
