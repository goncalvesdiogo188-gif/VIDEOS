"""Música de fundo original, sintetizada em código (sem direitos de autor de terceiros).

Um estilo por zona do cérebro:
  comando   eletrónica com energia (116 bpm, menor)
  memorias  sonhadora, acordes suaves e sinos (90 bpm)
  dopamina  saltitante, palmas e acordes a contratempo (124 bpm)
  sono      calma, só acordes longos e sinos, sem bateria (66 bpm)

A música fica por baixo das vozes e baixa automaticamente enquanto alguém fala.
Para desligar: variável de ambiente FABRICA_SEM_MUSICA=1, ou "musica": false no guião.

Ouvir as 4 músicas:  python -m fabrica.musica   (grava em out/teste/)
"""
import math
import random

TAXA = 22050
VOLUME = 0.17          # volume da música em relação à voz
DUCK = 0.45            # fator enquanto alguém fala
FADE_OUT = 1.2         # segundos

# acordes em notas MIDI (60 = dó central)
_Am, _F, _C, _G = [57, 60, 64], [53, 57, 60], [48, 52, 55], [55, 59, 62]
ESTILOS = {
    "comando": dict(bpm=116, acordes=[_Am, _F, _C, _G], camadas=("baixo8", "arpejo16", "bateria")),
    "memorias": dict(bpm=90, acordes=[[48, 52, 55, 59], [45, 48, 52, 55], [41, 45, 48, 52], [43, 47, 50, 52]],
                     camadas=("pad", "sinos4", "baixo1", "bateria_leve")),
    "dopamina": dict(bpm=124, acordes=[_C, _G, _Am, _F], camadas=("baixo_salta", "contratempo", "bateria_dança")),
    "sono": dict(bpm=66, acordes=[[41, 45, 48, 52], [40, 43, 47, 50], [38, 41, 45, 48], [36, 40, 43, 47]],
                 camadas=("pad", "sinos2")),
}
ALIASES = {"sala": "comando", "quarto": "sono", "loja": "dopamina"}

_cache = {}


def _hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def _somar(buf, i0, amostras, vol):
    n = len(buf)
    for i, v in enumerate(amostras):
        j = i0 + i
        if j >= n:
            j -= n  # a música é um ciclo: o que passa do fim volta ao início
        buf[j] += v * vol


# ------------------------------------------------------------- instrumentos
def _pluck(f, dur):
    n = int(dur * TAXA)
    w = 2 * math.pi * f / TAXA
    return [(math.sin(w * i) + 0.3 * math.sin(2 * w * i) + 0.1 * math.sin(3 * w * i))
            * math.exp(-i / TAXA * 7) * min(1.0, i / 60) for i in range(n)]


def _baixo(f, dur):
    n = int(dur * TAXA)
    w = 2 * math.pi * f / TAXA
    return [(math.sin(w * i) + 0.25 * math.sin(2 * w * i)) * min(1.0, i / 80)
            * math.exp(-i / TAXA * 2.5) * min(1.0, (n - i) / 300) for i in range(n)]


def _pad(f, dur):
    n = int(dur * TAXA)
    w1, w2 = 2 * math.pi * f / TAXA, 2 * math.pi * f * 1.006 / TAXA
    ata, rel = int(0.35 * TAXA), int(0.45 * TAXA)
    return [(math.sin(w1 * i) + math.sin(w2 * i) + 0.2 * math.sin(2 * w1 * i)) * 0.5
            * min(1.0, i / ata, (n - i) / rel) for i in range(n)]


def _sino(f, dur):
    n = int(dur * TAXA)
    w = 2 * math.pi * f / TAXA
    return [(math.sin(w * i) + 0.35 * math.sin(2.76 * w * i)) * math.exp(-i / TAXA * 3.2)
            * min(1.0, i / 40) for i in range(n)]


def _bombo():
    n, fase, out = int(0.35 * TAXA), 0.0, []
    for i in range(n):
        t = i / TAXA
        fase += 2 * math.pi * (48 + 95 * math.exp(-t * 28)) / TAXA
        out.append(math.sin(fase) * math.exp(-t * 9))
    return out


def _ruido(dur, decai, rnd, agudo=True):
    n, ant, out = int(dur * TAXA), 0.0, []
    for i in range(n):
        r = rnd.uniform(-1, 1)
        v = r - ant if agudo else r  # diferença = ruído mais agudo (tipo prato)
        ant = r
        out.append(v * math.exp(-i / TAXA * decai))
    return out


def _tarola(rnd):
    n = int(0.22 * TAXA)
    ru = _ruido(0.22, 20, rnd, agudo=False)
    return [0.7 * ru[i] + 0.5 * math.sin(2 * math.pi * 190 * i / TAXA) * math.exp(-i / TAXA * 25) for i in range(n)]


def _palmas(rnd):
    out = [0.0] * int(0.2 * TAXA)
    for k, atraso in enumerate((0.0, 0.012, 0.024)):
        _somar(out, int(atraso * TAXA), _ruido(0.15, 30 + k * 5, rnd), 0.6)
    return out


# ------------------------------------------------------------- composição
def _ciclo(estilo, semente=5):
    """Compõe 4 compassos (um ciclo que se repete)."""
    e = ESTILOS[estilo]
    tempo = 60 / e["bpm"]
    compasso = 4 * tempo
    buf = [0.0] * int(4 * compasso * TAXA)
    rnd = random.Random(semente)
    bombo, tarola, palmas = _bombo(), _tarola(rnd), _palmas(rnd)

    def em(batida):  # amostra onde começa a batida (contada desde o início do ciclo)
        return int(batida * tempo * TAXA)

    for c, acorde in enumerate(e["acordes"]):
        b0 = c * 4
        raiz = acorde[0]
        for camada in e["camadas"]:
            if camada == "baixo8":
                for k in range(8):
                    _somar(buf, em(b0 + k / 2), _baixo(_hz(raiz - 12), tempo * 0.45), 0.5)
            elif camada == "baixo1":
                _somar(buf, em(b0), _baixo(_hz(raiz - 12), compasso * 0.95), 0.45)
            elif camada == "baixo_salta":
                for k in range(8):
                    nota = raiz - 12 if k % 2 == 0 else raiz
                    _somar(buf, em(b0 + k / 2), _baixo(_hz(nota), tempo * 0.4), 0.45)
            elif camada == "arpejo16":
                seq = acorde + [acorde[1] + 12]
                for k in range(16):
                    _somar(buf, em(b0 + k / 4), _pluck(_hz(seq[k % len(seq)] + 12), tempo * 0.6), 0.16)
            elif camada == "contratempo":
                for k in range(4):
                    for nota in acorde:
                        _somar(buf, em(b0 + k + 0.5), _pluck(_hz(nota + 12), tempo * 0.5), 0.13)
            elif camada == "pad":
                for nota in acorde:
                    _somar(buf, em(b0), _pad(_hz(nota), compasso), 0.12)
            elif camada in ("sinos4", "sinos2"):
                passo = 1 if camada == "sinos4" else 2
                for k in range(0, 4, passo):
                    nota = acorde[(k + c) % len(acorde)] + 24
                    _somar(buf, em(b0 + k), _sino(_hz(nota), tempo * 2), 0.10)
            elif camada in ("bateria", "bateria_leve", "bateria_dança"):
                leve = camada == "bateria_leve"
                bombos = {"bateria": (0, 1.5, 2), "bateria_leve": (0,), "bateria_dança": (0, 1, 2, 3)}[camada]
                for b in bombos:
                    _somar(buf, em(b0 + b), bombo, 0.55 if not leve else 0.35)
                for b in (1, 3):
                    if camada == "bateria_dança":
                        _somar(buf, em(b0 + b), palmas, 0.45)
                    elif not leve:
                        _somar(buf, em(b0 + b), tarola, 0.35)
                for k in range(8 if not leve else 4):
                    pos = k / 2 if not leve else k
                    _somar(buf, em(b0 + pos), _ruido(0.05, 70, rnd), 0.14 if not leve else 0.08)
    pico = max(1e-9, max(abs(v) for v in buf))
    return [v / pico for v in buf]


def gerar(zona, duracao):
    """Música normalizada (-1..1) com a duração pedida, em amostras a 22050 Hz."""
    zona = ALIASES.get(zona, zona)
    if zona not in ESTILOS:
        zona = "comando"
    if zona not in _cache:
        _cache[zona] = _ciclo(zona)
    ciclo = _cache[zona]
    n = int(duracao * TAXA)
    out = [ciclo[i % len(ciclo)] for i in range(n)]
    fade = int(FADE_OUT * TAXA)
    for i in range(max(0, n - fade), n):
        out[i] *= (n - i) / fade
    entra = int(0.3 * TAXA)
    for i in range(min(entra, n)):
        out[i] *= i / entra
    return out


def ganho_ducking(plano, duracao):
    """Volume da música ao longo do tempo: baixa enquanto alguém fala (rampas de 0,15 s)."""
    n = int(duracao * TAXA)
    g = [1.0] * n
    rampa = int(0.15 * TAXA)
    for f in plano:
        a, b = int(f["inicio"] * TAXA), int(f["fim"] * TAXA)
        for i in range(max(0, a - rampa), min(n, b + rampa)):
            if i < a:
                k = (a - i) / rampa
            elif i > b:
                k = (i - b) / rampa
            else:
                k = 0.0
            g[i] = min(g[i], DUCK + (1 - DUCK) * k)
    return g


def misturar_em(mix, plano, duracao, zona):
    """Acrescenta a música (com ducking) à lista `mix` de amostras float."""
    m = gerar(zona, duracao)
    g = ganho_ducking(plano, duracao)
    for i in range(min(len(mix), len(m))):
        mix[i] += m[i] * g[i] * VOLUME


def ativa(g):
    import os
    return os.environ.get("FABRICA_SEM_MUSICA") != "1" and g.get("musica", True) is not False


if __name__ == "__main__":
    import array
    import shutil
    import subprocess
    import wave
    from pathlib import Path
    pasta = Path("out/teste")
    pasta.mkdir(parents=True, exist_ok=True)
    for z in ESTILOS:
        m = gerar(z, 16)
        wav = pasta / f"musica_{z}.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(TAXA)
            w.writeframes(array.array("h", (int(v * 0.8 * 32000) for v in m)).tobytes())
        subprocess.run([shutil.which("ffmpeg") or "ffmpeg", "-v", "error", "-y", "-i", str(wav),
                        "-b:a", "128k", str(wav.with_suffix(".mp3"))], check=True)
        wav.unlink()
        print("Gravado:", wav.with_suffix(".mp3"))
