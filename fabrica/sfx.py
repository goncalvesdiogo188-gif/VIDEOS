"""Efeitos sonoros originais (sintetizados em código) para dar vida às ações.

Cada efeito devolve uma lista de amostras (-1..1) a 22050 Hz. O vídeo junta-os
nos momentos certos com `eventos(plano)`.

  boing    salto           whoosh   aproximar-se
  ding     lâmpada/ideia   toque    despertador
  bonk     mão na testa    fanfarra vitória
  uh       surpresa        ola      acenar
"""
import math
import random

TAXA = 22050
VOLUME = 0.30  # em relação à voz


def _n(dur):
    return int(dur * TAXA)


def boing():
    n, fase, out = _n(0.45), 0.0, []
    for i in range(n):
        t = i / TAXA
        f = 230 + 330 * math.sin(math.pi * min(1.0, t / 0.45)) ** 1.5 + 40 * math.sin(t * 60)
        fase += 2 * math.pi * f / TAXA
        out.append((math.sin(fase) + 0.3 * math.sin(2 * fase)) * math.exp(-t * 4.5) * min(1.0, i / 80))
    return out


def whoosh():
    rnd, n, ant, out = random.Random(3), _n(0.55), 0.0, []
    for i in range(n):
        t = i / TAXA
        k = 0.05 + 0.5 * math.sin(math.pi * t / 0.55)  # filtro que abre e fecha
        ant += (rnd.uniform(-1, 1) - ant) * k
        out.append(ant * math.sin(math.pi * t / 0.55) ** 1.3 * 1.6)
    return out


def ding():
    n, out = _n(1.0), []
    for i in range(n):
        t = i / TAXA
        v = math.sin(2 * math.pi * 1318.5 * t) + 0.5 * math.sin(2 * math.pi * 2637 * t)
        if t > 0.12:
            v += 0.8 * math.sin(2 * math.pi * 1760 * (t - 0.12)) * min(1.0, (t - 0.12) * 80)
        out.append(v * math.exp(-t * 4.2) * min(1.0, i / 40))
    return out


def toque():
    n, out = _n(0.9), []
    for i in range(n):
        t = i / TAXA
        on = 1.0 if (t % 0.15) < 0.075 else 0.0
        v = math.sin(2 * math.pi * 1900 * t) + 0.5 * math.sin(2 * math.pi * 2400 * t)
        out.append(v * on * math.exp(-t * 1.6) * 0.8)
    return out


def bonk():
    rnd, n, out = random.Random(5), _n(0.3), []
    fase = 0.0
    for i in range(n):
        t = i / TAXA
        fase += 2 * math.pi * (160 * math.exp(-t * 12) + 70) / TAXA
        out.append((math.sin(fase) + (0.5 * rnd.uniform(-1, 1) if t < 0.03 else 0)) * math.exp(-t * 14))
    return out


def fanfarra():
    notas = [(523.3, 0.0), (659.3, 0.11), (784.0, 0.22), (1046.5, 0.33)]
    n, out = _n(1.0), [0.0] * _n(1.0)
    for f, t0 in notas:
        for i in range(_n(0.5)):
            j = i + _n(t0)
            if j < n:
                t = i / TAXA
                out[j] += (math.sin(2 * math.pi * f * t) + 0.4 * math.sin(4 * math.pi * f * t)) * math.exp(-t * 5) * 0.5
    return out


def uh():
    n, fase, out = _n(0.28), 0.0, []
    for i in range(n):
        t = i / TAXA
        fase += 2 * math.pi * (420 + 520 * t / 0.28) / TAXA
        out.append(math.sin(fase) * math.sin(math.pi * t / 0.28) ** 0.8)
    return out


def ola():
    n, fase, out = _n(0.4), 0.0, []
    for i in range(n):
        t = i / TAXA
        fase += 2 * math.pi * (700 + 350 * math.sin(math.pi * t / 0.4)) / TAXA
        out.append(math.sin(fase) * math.sin(math.pi * t / 0.4))
    return out


EFEITOS = {"boing": boing, "whoosh": whoosh, "ding": ding, "toque": toque,
           "bonk": bonk, "fanfarra": fanfarra, "uh": uh, "ola": ola}
_cache = {}


def amostras(nome):
    if nome not in _cache:
        v = EFEITOS[nome]()
        pico = max(1e-9, max(abs(x) for x in v))
        _cache[nome] = [x / pico for x in v]
    return _cache[nome]


def eventos(plano):
    """Lista de (instante em segundos, nome do efeito) a partir das ações do guião."""
    ev = []
    for f in plano:
        t0 = f["inicio"]
        if f.get("acao") == "salta":
            ev.append((t0 + 0.02, "boing"))
        elif f.get("acao") == "aproxima":
            ev.append((t0 - 0.05, "whoosh"))
        if f.get("adereco") == "lampada":
            ev.append((t0 + 0.1, "ding"))
        elif f.get("adereco") == "despertador":
            ev.append((t0 + 0.1, "toque"))
        if f.get("pose") == "mao_testa":
            ev.append((t0 + 0.15, "bonk"))
        elif f.get("pose") == "vitoria":
            ev.append((t0 + 0.05, "fanfarra"))
        elif f.get("pose") == "acena":
            ev.append((t0 + 0.05, "ola"))
        if f.get("expressao") == "surpreendido":
            ev.append((t0 - 0.1, "uh"))
    return ev


def misturar_em(mix, plano):
    n = len(mix)
    for t, nome in eventos(plano):
        i0 = int(max(0.0, t) * TAXA)
        for i, v in enumerate(amostras(nome)):
            if i0 + i < n:
                mix[i0 + i] += v * VOLUME
