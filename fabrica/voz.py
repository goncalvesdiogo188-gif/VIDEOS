"""Voz real dos personagens com edge-tts (vozes neurais gratuitas da Microsoft).

- Neurão: pt-PT-DuarteNeural, mais lento e grave.
- Zé Neurónio: pt-BR-AntonioNeural, mais rápido e agudo.

Para cada fala obtém-se o áudio e o tempo exato de cada palavra. O plano do vídeo
(início/fim de cada fala, de cada palavra e a abertura da boca) passa a sair da
duração real do áudio. Precisa de internet. Os áudios ficam guardados em
out/cache_voz/ para não serem pedidos duas vezes.
"""
import array
import asyncio
import hashlib
import json
import math
import os
import shutil
import subprocess
from pathlib import Path

from . import guiao as G

VOZES = {
    "neurao": {"voz": "pt-PT-DuarteNeural", "velocidade": -6, "tom": -8},
    "ze": {"voz": "pt-BR-AntonioNeural", "velocidade": 8, "tom": 18},
}
TAXA = 22050
FPS = 30
INICIO = 0.35
PAUSA = 0.22
FIM_EXTRA = 1.4
DURACAO_MAX = 30.0
CACHE = Path(__file__).resolve().parent.parent / "out" / "cache_voz"


class VozErro(Exception):
    pass


def _ffmpeg():
    return shutil.which("ffmpeg") or "ffmpeg"


async def _sintetizar(texto, voz, velocidade, tom, destino):
    import edge_tts
    if os.environ.get("SSL_CERT_FILE"):
        # para redes com proxy próprio (o edge-tts ignora SSL_CERT_FILE por omissão)
        import ssl
        import edge_tts.communicate as c
        c._SSL_CTX = ssl.create_default_context(cafile=os.environ["SSL_CERT_FILE"])
    com = edge_tts.Communicate(texto, voz, rate=f"{velocidade:+d}%", pitch=f"{tom:+d}Hz",
                               boundary="WordBoundary")
    palavras = []
    with open(destino, "wb") as f:
        async for ch in com.stream():
            if ch["type"] == "audio":
                f.write(ch["data"])
            elif ch["type"] == "WordBoundary":
                t0 = ch["offset"] / 1e7
                palavras.append((ch["text"], t0, t0 + ch["duration"] / 1e7))
    return palavras


def _ler_pcm(mp3):
    """Descodifica o mp3 para amostras mono de 16 bits."""
    r = subprocess.run([_ffmpeg(), "-v", "error", "-i", str(mp3), "-f", "s16le", "-ac", "1",
                        "-ar", str(TAXA), "-"], capture_output=True)
    if r.returncode != 0:
        raise VozErro("Não consegui ler o áudio da voz: " + r.stderr.decode(errors="ignore")[:200])
    a = array.array("h")
    a.frombytes(r.stdout)
    return a


def sintetizar_fala(quem, texto, velocidade_extra=0):
    """Devolve (amostras, palavras[(texto, t0, t1)]) da fala, usando a cache."""
    cfg = VOZES[quem]
    vel = cfg["velocidade"] + velocidade_extra
    chave = hashlib.sha1(f"{cfg['voz']}|{vel}|{cfg['tom']}|{texto}".encode()).hexdigest()[:16]
    CACHE.mkdir(parents=True, exist_ok=True)
    mp3, js = CACHE / f"{chave}.mp3", CACHE / f"{chave}.json"
    if not (mp3.exists() and js.exists() and mp3.stat().st_size > 0):
        ultimo = None
        for _ in range(3):
            try:
                pal = asyncio.run(_sintetizar(texto, cfg["voz"], vel, cfg["tom"], mp3))
                if mp3.stat().st_size > 0:
                    js.write_text(json.dumps(pal, ensure_ascii=False))
                    break
            except Exception as e:  # rede, servidor ocupado...
                ultimo = e
        else:
            mp3.unlink(missing_ok=True)
            raise VozErro(f"Não consegui gerar a voz (precisa de internet). Detalhe: {ultimo}")
    return _aparar(_ler_pcm(mp3), [tuple(p) for p in json.loads(js.read_text())])


def _aparar(am, palavras, limiar=600):
    """Corta o silêncio no início e no fim do áudio (o edge-tts deixa ~1 s no fim)."""
    ini = next((i for i, v in enumerate(am) if abs(v) > limiar), 0)
    fim = next((i for i in range(len(am) - 1, -1, -1) if abs(am[i]) > limiar), len(am) - 1)
    ini = max(0, ini - int(0.04 * TAXA))
    fim = min(len(am), fim + int(0.12 * TAXA))
    corte = ini / TAXA
    pal = [(p, max(0.0, a - corte), max(0.0, b - corte)) for (p, a, b) in palavras]
    return am[ini:fim], pal


def _alinhar(texto, pal_voz, dur):
    """Faz corresponder as palavras do guião aos tempos dados pela voz."""
    pals = G.palavras(texto)
    if len(pal_voz) == len(pals):
        res = []
        for i, (p, (_, t0, t1)) in enumerate(zip(pals, pal_voz)):
            fim = pal_voz[i + 1][1] if i + 1 < len(pal_voz) else max(t1, dur - 0.05)
            res.append((p, t0, fim))
        return res
    # número diferente (números, hífenes...): reparte pelas sílabas entre o 1.º e o último tempo
    ini = pal_voz[0][1] if pal_voz else 0.05
    fim = pal_voz[-1][2] if pal_voz else dur
    sil = [G.silabas(p) for p in pals]
    tot, t, res = sum(sil), ini, []
    for p, s in zip(pals, sil):
        d = (fim - ini) * s / tot
        res.append((p, t, t + d))
        t += d
    return res


def _envelope(amostras):
    """Abertura da boca (0..1) por frame, a partir do volume do áudio."""
    n = max(1, int(len(amostras) / TAXA * FPS))
    passo = TAXA / FPS
    env = []
    for i in range(n):
        bloco = amostras[int(i * passo):int((i + 1) * passo)]
        if not bloco:
            env.append(0.0)
            continue
        env.append(math.sqrt(sum(x * x for x in bloco[::4]) / len(bloco[::4])))
    topo = sorted(env)[int(len(env) * 0.95)] or 1.0
    return [min(1.0, max(0.0, (v / topo - 0.08) * 1.15)) for v in env]


def planear_com_voz(g, log=print):
    """Como video.planear, mas com a voz real. Devolve (plano, duracao, amostras_mistura).

    Se o total passar de 30 s, a fábrica acelera a fala um pouco e tenta de novo.
    """
    for extra in (0, 8, 15, 22):
        plano, t, faixas = [], INICIO, []
        for f in g["falas"]:
            am, pal = sintetizar_fala(f["quem"], f["texto"], extra)
            dur = len(am) / TAXA
            palavras = [(p, t + a, t + b) for (p, a, b) in _alinhar(f["texto"], pal, dur)]
            plano.append(dict(f, inicio=t, fim=t + dur, palavras=palavras, silabas=[],
                              envelope=_envelope(am)))
            faixas.append((t, am))
            t += dur + PAUSA
        duracao = t - PAUSA + FIM_EXTRA
        if duracao <= DURACAO_MAX:
            break
        log(f"   A voz ficou com {duracao:.1f} s; a acelerar um pouco a fala...")
    else:
        raise VozErro(f"Mesmo a acelerar, o vídeo fica com {duracao:.1f} s (máximo 30 s). Encurta o guião.")
    return plano, duracao, _misturar(plano, faixas, duracao)


def _misturar(plano, faixas, duracao):
    n = int(duracao * TAXA)
    mix = [0.0] * n
    for (t0, am) in faixas:
        i0 = int(t0 * TAXA)
        for i, v in enumerate(am):
            if i0 + i < n:
                mix[i0 + i] += v / 32768 * 0.95

    def pop(t0, freq, vol, dur=0.12):
        i0, m = int(t0 * TAXA), int(dur * TAXA)
        for i in range(m):
            if 0 <= i0 + i < n:
                tt = i / TAXA
                mix[i0 + i] += math.sin(2 * math.pi * freq * tt) * math.exp(-tt * 30) * vol

    for f in plano:
        pop(f["inicio"] - 0.08, 880 if f["quem"] == "ze" else 660, 0.05)
    pop(plano[-1]["fim"] + 0.2, 1046.5, 0.12, 0.6)
    a = array.array("h", (int(max(-1.0, min(1.0, v)) * 32000) for v in mix))
    return a


def gravar_wav(amostras, destino):
    import wave
    with wave.open(str(destino), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(TAXA)
        w.writeframes(amostras.tobytes())
