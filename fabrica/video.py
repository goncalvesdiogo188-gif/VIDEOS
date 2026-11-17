"""Gera um Reel vertical (1080x1920) a partir de um guião JSON.

Uso:
    python -m fabrica.video guioes/exemplo_compras_impulso.json  [saida.mp4]

Fase 1: a "voz" é provisória (bips sintetizados, um tom por personagem).
Os tempos de cada fala saem de uma estimativa por sílabas; na fase 3 passam
a sair da duração real do áudio da voz.
"""
import math
import random
import shutil
import struct
import subprocess
import sys
import wave
from multiprocessing import Pool, cpu_count
from pathlib import Path

from . import guiao as G
from .cenarios import cenario
from .personagens import PERSONAGENS, desenha
from .render import svg_para_png

W, H, FPS = 1080, 1920, 30
CHAO = 1480
POS_X = {"neurao": 300, "ze": 780}
ESCALA = 0.92
FONTE = "Sniglet"
TAXA = 22050  # amostras de áudio por segundo

# ritmo da voz provisória
SEG_POR_SILABA = 0.12  # medido com as vozes reais (edge-tts), 61 falas
PAUSA_PONTUACAO = 0.6
MARGEM_FALA = 0.0
PAUSA_ENTRE_FALAS = 0.22
INICIO = 0.35
FIM_EXTRA = 1.4


# ---------------------------------------------------------------- utilidades
def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def suave(k):
    k = max(0.0, min(1.0, k))
    return k * k * (3 - 2 * k)


def quebrar_linhas(palavras, tamanho, largura):
    """Divide uma lista de palavras em linhas que caibam na largura (estimativa)."""
    max_c = max(8, int(largura / (tamanho * 0.66)))
    linhas, atual = [], []
    for i, p in enumerate(palavras):
        teste = " ".join([palavras[j] for j in atual] + [p])
        if atual and len(teste) > max_c:
            linhas.append(atual)
            atual = []
        atual.append(i)
    if atual:
        linhas.append(atual)
    return linhas


# ---------------------------------------------------------------- planeamento
def planear(g):
    """Calcula início/fim de cada fala, de cada palavra e de cada sílaba."""
    t = INICIO
    plano = []
    for f in g["falas"]:
        pals = G.palavras(f["texto"])
        t0 = t
        ps, sils = [], []
        for p in pals:
            n = G.silabas(p)
            p0 = t
            for _ in range(n):
                sils.append((t, t + SEG_POR_SILABA))
                t += SEG_POR_SILABA
            if p[-1] in ".,:;!?":
                t += PAUSA_PONTUACAO
            ps.append((p, p0, t))
        t += MARGEM_FALA
        plano.append(dict(f, inicio=t0, fim=t, palavras=ps, silabas=sils))
        t += PAUSA_ENTRE_FALAS
    return plano, t - PAUSA_ENTRE_FALAS + FIM_EXTRA


# ---------------------------------------------------------------- áudio provisório
def audio_provisorio(plano, duracao, destino):
    n = int(duracao * TAXA)
    buf = [0.0] * n
    rnd = random.Random(7)
    base = {"neurao": 150.0, "ze": 290.0}

    def tom(t0, dur, freq, vol, forma="voz"):
        i0 = int(t0 * TAXA)
        m = int(dur * TAXA)
        for i in range(m):
            if i0 + i >= n:
                break
            tt = i / TAXA
            env = math.sin(math.pi * i / m) ** 0.6
            ph = 2 * math.pi * freq * tt
            if forma == "voz":
                v = 0.6 * math.sin(ph) + 0.25 * math.sin(2 * ph) + 0.15 * math.sin(3 * ph)
            else:
                v = math.sin(ph) * math.exp(-tt * 9)
            buf[i0 + i] += v * env * vol

    for f in plano:
        tom(f["inicio"] - 0.06, 0.12, 880 if f["quem"] == "ze" else 660, 0.10, "pop")
        for (s0, s1) in f["silabas"]:
            tom(s0, (s1 - s0) * 0.8, base[f["quem"]] * rnd.uniform(0.85, 1.25), 0.32)
    tom(plano[-1]["fim"] + 0.15, 0.6, 1046.5, 0.18, "pop")

    with wave.open(str(destino), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(TAXA)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v)) * 30000)) for v in buf))


# ---------------------------------------------------------------- estado por frame
def fala_em(plano, t):
    """Índice da fala a decorrer (ou a última que já terminou)."""
    idx = -1
    for i, f in enumerate(plano):
        if t >= f["inicio"] - 0.05:
            idx = i
    return idx


def boca_em(f, t):
    env = f.get("envelope")
    if env:  # voz real: abertura da boca pelo volume do áudio
        i = int((t - f["inicio"]) * FPS)
        return env[i] if 0 <= i < len(env) else 0.0
    for (s0, s1) in f["silabas"]:
        if s0 <= t < s1:
            k = (t - s0) / (s1 - s0)
            return math.sin(math.pi * k) * (0.65 + 0.35 * ((int(s0 * 37) % 5) / 4))
    return 0.0


def estado_personagem(plano, quem, t):
    """(expressão, pose) com transição suave de 0,25 s entre estados."""
    mudancas = [(0.0, "neutro", "normal")]
    for f in plano:
        if f["quem"] == quem:
            pose = f["pose"]
            if f.get("adereco") and pose not in POSES_COM_ADERECO:
                pose = "segura"
            mudancas.append((f["inicio"] - 0.1, f["expressao"], pose))
        else:
            ult = mudancas[-1][1]
            ouvir = ult if ult in ("alegre", "convencido", "pensativo") else "neutro"
            mudancas.append((f["inicio"] + 0.1, ouvir, "normal"))
    ant, atual = mudancas[0], mudancas[0]
    for m in mudancas:
        if t >= m[0]:
            ant, atual = atual, m
    k = suave((t - atual[0]) / 0.25)
    return (ant[1], atual[1], k), (ant[2], atual[2], k)


POSES_COM_ADERECO = ("segura", "aponta", "bracos_ar", "vitoria", "acena")
ACOES = ("nenhuma", "salta", "treme", "aproxima")
ENTRADA = 0.45      # segundos que os personagens levam a entrar no início
APROXIMA = 90       # píxeis que o personagem anda na direção do outro


def movimento(plano, quem, t):
    """Deslocamento (dx, dy) e fase do passo (ou None) do personagem no instante t."""
    lado = -1 if quem == "neurao" else 1
    dx, dy, andar = 0.0, 0.0, None
    # entrada: chegam a andar dos lados
    if t < ENTRADA:
        dx += lado * 380 * (1 - suave(t / ENTRADA))
        andar = t * 16
    # aproximar: anda na direção do outro e volta na fala seguinte que não aproxima
    alvo_ant, alvo, t_mud = 0.0, 0.0, None
    for f in plano:
        if f["quem"] != quem or f["inicio"] > t:
            continue
        novo = -lado * APROXIMA if f.get("acao") == "aproxima" else 0.0
        if novo != alvo:
            alvo_ant, alvo, t_mud = alvo, novo, f["inicio"]
    if t_mud is not None:
        k = (t - t_mud) / 0.6
        dx += alvo_ant + (alvo - alvo_ant) * suave(k)
        if k < 1:
            andar = (t - t_mud) * 14
    else:
        dx += alvo
    for f in plano:
        if f["quem"] != quem:
            continue
        r = t - f["inicio"]
        if f.get("acao") == "salta" and 0 <= r < 0.5:
            dy -= 110 * math.sin(math.pi * r / 0.5)
        if f.get("acao") == "treme" and 0 <= r < min(1.0, f["fim"] - f["inicio"]):
            dx += 7 * math.sin(t * 70)
    return dx, dy, andar


def adereco_em(plano, quem, t):
    """(nome do objeto, 0..1) que o personagem segura no instante t."""
    for f in plano:
        if f["quem"] == quem and f.get("adereco") and f["inicio"] - 0.1 <= t <= f["fim"] + 0.4:
            k = suave((t - f["inicio"] + 0.1) / 0.2) * (1 - suave((t - f["fim"] - 0.2) / 0.2))
            return f["adereco"], k
    return None, 0.0


def piscar_em(quem, t):
    periodo = 3.3 if quem == "neurao" else 2.7
    fase = (t + (0.9 if quem == "ze" else 0.0)) % periodo
    return max(0.0, 1 - abs(fase - 0.07) / 0.07) if fase < 0.14 else 0.0


# ---------------------------------------------------------------- desenho do frame
def caixa_titulo(titulo, t):
    pals = titulo.split()
    tam = 64
    linhas = quebrar_linhas(pals, tam, 900)
    alt = len(linhas) * 76 + 56
    pop = 0.85 + 0.15 * suave(t / 0.35)
    s = f'<g transform="translate(540,{200 + alt/2:.0f}) scale({pop:.3f}) translate(-540,{-(200 + alt/2):.0f})">'
    s += f'<rect x="60" y="200" width="960" height="{alt}" rx="34" fill="#120A2E" stroke="#2EE6FF" stroke-width="6"/>'
    for i, ln in enumerate(linhas):
        txt = esc(" ".join(pals[j] for j in ln))
        s += (f'<text x="540" y="{200 + 28 + 60 + i*76}" font-family="{FONTE}" font-size="{tam}" '
              f'fill="#FFFFFF" text-anchor="middle">{txt}</text>')
    return s + "</g>", 200 + alt


def caixa_legenda(f, t, topo):
    p = PERSONAGENS[f["quem"]]
    pals = [w for (w, _, _) in f["palavras"]]
    tam = 62
    linhas = quebrar_linhas(pals, tam, 860)
    alt = len(linhas) * 78 + 50
    y0 = topo + 70
    entra = suave((t - f["inicio"] + 0.05) / 0.2)
    s = f'<g opacity="{entra:.2f}">'
    s += f'<rect x="60" y="{y0}" width="960" height="{alt}" rx="30" fill="#FFFFFF" stroke="{p["cor_legenda"]}" stroke-width="8"/>'
    nome = esc(p["nome"])
    lx = 90 if f["quem"] == "neurao" else 990 - (len(nome) * 24 + 40)
    s += f'<rect x="{lx}" y="{y0-34}" width="{len(nome)*24+40}" height="56" rx="20" fill="{p["cor_legenda"]}"/>'
    s += f'<text x="{lx+20}" y="{y0+6}" font-family="{FONTE}" font-size="38" fill="#FFFFFF">{nome}</text>'
    for i, ln in enumerate(linhas):
        partes = []
        for j in ln:
            w, w0, w1 = f["palavras"][j]
            if w0 <= t < w1 + 0.05:
                cor = p["cor_legenda"]
            elif t >= w1:
                cor = "#2B1B3A"
            else:
                cor = "#9A8FA6"
            partes.append(f'<tspan fill="{cor}">{esc(w)}</tspan>')
        s += (f'<text x="540" y="{y0 + 25 + 60 + i*78}" font-family="{FONTE}" font-size="{tam}" '
              f'text-anchor="middle">{" ".join(partes)}</text>')
    return s + "</g>"


def frame_svg(g, plano, duracao, t):
    i = fala_em(plano, t)
    f = plano[i] if i >= 0 else None
    a_falar = f["quem"] if (f and t < f["fim"]) else None

    # câmara: aproximação suave alternada em cada fala
    z, cx = 1.0, 540
    if f:
        alvo = 1.05 if i % 2 == 0 else 1.0
        anterior = 1.0 if i % 2 == 0 else 1.05
        z = anterior + (alvo - anterior) * suave((t - f["inicio"]) / 0.5)
        cx = POS_X[f["quem"]]
    s = f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}">'
    s += f'<g transform="translate({cx},{CHAO}) scale({z:.4f}) translate({-cx},{-CHAO})">'
    s += cenario(g["cenario"], W, H, CHAO, t=t, focos=(POS_X["neurao"], POS_X["ze"]))
    for quem in ("neurao", "ze"):
        expr, pose = estado_personagem(plano, quem, t)
        fala_ativa = a_falar == quem
        boca = boca_em(f, t) if fala_ativa else 0.0
        outro = "ze" if quem == "neurao" else "neurao"
        olhar = None
        if a_falar == outro:
            olhar = (0.7, 0.0)  # o Zé está espelhado, por isso +0.7 também olha para o outro
        esc_p = ESCALA * (1 + 0.05 * (suave((t - f["inicio"]) / 0.3) if (f and f["quem"] == quem) else 0))
        dx, dy, andar = movimento(plano, quem, t)
        ad, ad_k = adereco_em(plano, quem, t)
        s += desenha(quem, POS_X[quem] + dx, CHAO + dy, esc_p, expr, pose, boca=boca,
                     piscar=piscar_em(quem, t), t=t, olhar=olhar,
                     virado=-1 if quem == "ze" else 1,  # gestos e objetos do Zé ficam virados para o centro
                     adereco=ad, adereco_k=ad_k, andar=andar)
    s += "</g>"

    # barra de progresso
    s += f'<rect x="0" y="0" width="{W * min(1, t / duracao):.0f}" height="12" fill="#2EE6FF"/>'
    tit, fundo_tit = caixa_titulo(g["titulo"], t)
    s += tit
    if f:
        s += caixa_legenda(f, t, fundo_tit)
    # chamada final
    fim = plano[-1]["fim"]
    if t > fim + 0.1:
        k = suave((t - fim - 0.1) / 0.3)
        s += (f'<g transform="translate(540,1600) scale({0.6 + 0.4*k:.3f})" opacity="{k:.2f}">'
              f'<rect x="-300" y="-60" width="600" height="110" rx="55" fill="#120A2E" stroke="#FFE27A" stroke-width="5"/>'
              f'<text x="0" y="16" font-family="{FONTE}" font-size="54" fill="#FFE27A" '
              f'text-anchor="middle">Segue para mais!</text></g>')
    return s + "</svg>"


# ---------------------------------------------------------------- render em paralelo
_CTX = {}


def _init(g, plano, duracao):
    _CTX.update(g=g, plano=plano, duracao=duracao)


def _frame_png(n):
    return svg_para_png(frame_svg(_CTX["g"], _CTX["plano"], _CTX["duracao"], n / FPS))


def preparar_audio(g, destino_wav, log=print):
    """Plano e áudio do vídeo. Usa a voz real (edge-tts); com a variável de ambiente
    FABRICA_SEM_VOZ=1 usa os bips provisórios. Devolve (plano, duracao, wav, tipo_de_voz)."""
    import os
    if os.environ.get("FABRICA_SEM_VOZ") == "1":
        plano, duracao = planear(g)
        audio_provisorio(plano, duracao, destino_wav)
        return plano, duracao, Path(destino_wav), "provisória (bips)"
    from . import voz
    plano, duracao, amostras = voz.planear_com_voz(g, log=log)
    voz.gravar_wav(amostras, destino_wav)
    return plano, duracao, Path(destino_wav), "real (edge-tts)"


def gerar_video(g, saida, plano=None, duracao=None, audio=None):
    saida = Path(saida)
    saida.parent.mkdir(parents=True, exist_ok=True)
    if plano is None:
        plano, duracao = planear(g)
    if audio is None:
        audio = saida.with_suffix(".wav")
        audio_provisorio(plano, duracao, audio)
        apagar_audio = True
    else:
        apagar_audio = False
    total = int(duracao * FPS)
    ff = shutil.which("ffmpeg") or "ffmpeg"
    cmd = [ff, "-y", "-loglevel", "error", "-f", "image2pipe", "-framerate", str(FPS),
           "-c:v", "png", "-i", "-", "-i", str(audio),
           "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p",
           "-af", "loudnorm=I=-14:TP=-1.5:LRA=11", "-ar", "44100",
           "-c:a", "aac", "-b:a", "160k", "-shortest", "-movflags", "+faststart", str(saida)]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    with Pool(max(1, cpu_count() - 1), initializer=_init, initargs=(g, plano, duracao)) as pool:
        for n, png in enumerate(pool.imap(_frame_png, range(total), chunksize=6)):
            proc.stdin.write(png)
            if n % (FPS * 5) == 0:
                print(f"  frame {n}/{total}", flush=True)
    proc.stdin.close()
    if proc.wait() != 0:
        raise RuntimeError("O ffmpeg falhou a criar o vídeo.")
    if apagar_audio:
        Path(audio).unlink(missing_ok=True)
    return saida, duracao


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    caminho = Path(sys.argv[1])
    g = G.carregar(caminho)
    saida = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("out/teste") / f"{caminho.stem}.mp4"
    saida.parent.mkdir(parents=True, exist_ok=True)
    wav = saida.with_suffix(".wav")
    plano, dur, wav, tipo = preparar_audio(g, wav)
    print(f"Guião: {g['titulo']}  |  {len(plano)} falas  |  duração {dur:.1f} s  |  voz {tipo}")
    # imagem de pré-visualização a meio da 4.ª fala
    prev = saida.with_name(saida.stem + "_preview.png")
    tp = (plano[min(3, len(plano)-1)]["inicio"] + plano[min(3, len(plano)-1)]["fim"]) / 2
    prev.write_bytes(svg_para_png(frame_svg(g, plano, dur, tp)))
    gerar_video(g, saida, plano, dur, audio=wav)
    wav.unlink(missing_ok=True)
    print(f"Vídeo pronto: {saida}  ({dur:.1f} s)")


if __name__ == "__main__":
    main()
