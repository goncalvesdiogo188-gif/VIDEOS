"""Gera um carrossel (imagens 1080x1350) a partir do mesmo guião do vídeo.

Uso:
    python -m fabrica.carrossel guioes/exemplo_compras_impulso.json  [pasta_saida]
"""
import sys
from pathlib import Path

from . import guiao as G
from .personagens import PERSONAGENS, desenha
from .render import svg_para_png
from .video import esc, quebrar_linhas, FONTE

W, H = 1080, 1350
FUNDOS = ["#FFE8D2", "#E3F4EF", "#EDE7FF", "#FFF3C4", "#FFE1EA", "#DFF0FF"]


def _texto(txt, x, y, tam, cor, largura, entre=1.22, ancora="middle"):
    pals = txt.split()
    linhas = quebrar_linhas(pals, tam, largura)
    s = ""
    for i, ln in enumerate(linhas):
        s += (f'<text x="{x}" y="{y + i*tam*entre:.0f}" font-family="{FONTE}" font-size="{tam}" '
              f'fill="{cor}" text-anchor="{ancora}">{esc(" ".join(pals[j] for j in ln))}</text>')
    return s, len(linhas) * tam * entre


def _base(i, total):
    s = f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}">'
    s += f'<rect width="{W}" height="{H}" fill="{FUNDOS[i % len(FUNDOS)]}"/>'
    # pontos de navegação
    for k in range(total):
        cor = "#2B1B3A" if k == i else "#2B1B3A33"
        s += f'<circle cx="{540 - (total-1)*14 + k*28}" cy="1300" r="8" fill="{cor[:7]}" fill-opacity="{1 if k == i else 0.25}"/>'
    return s


def slides(g):
    itens = g.get("carrossel") or []
    if not itens:
        itens = [{"titulo": g["titulo"], "texto": ""}] + [
            {"titulo": "", "texto": f["texto"]} for f in g["falas"][1:-1]]
    licao = g["falas"][-1]["texto"]
    total = len(itens) + 1
    out = []

    # capa
    s = _base(0, total)
    t1, h1 = _texto(itens[0]["titulo"], 540, 230, 86, "#FFFFFF", 860)
    t2, h2 = _texto(itens[0].get("texto", ""), 540, 230 + h1 + 30, 46, "#FFE27A", 860)
    s += f'<rect x="60" y="90" width="960" height="{h1 + h2 + 150:.0f}" rx="44" fill="#2B1B3A"/>' + t1 + t2
    s += desenha("neurao", 320, 1230, 0.95, "convencido", "aponta", t=0.4)
    s += desenha("ze", 770, 1230, 0.95, "surpreendido", "bracos_ar", t=1.1)
    s += f'<text x="1010" y="1300" font-family="{FONTE}" font-size="34" fill="#2B1B3A" text-anchor="end">desliza &gt;</text>'
    out.append(s + "</svg>")

    # explicação
    for i, it in enumerate(itens[1:], start=1):
        s = _base(i, total)
        quem = "neurao" if i % 2 else "ze"
        cor = PERSONAGENS[quem]["cor_legenda"]
        s += f'<text x="80" y="200" font-family="{FONTE}" font-size="130" fill="{cor}" fill-opacity="0.35">{i}</text>'
        y = 230
        if it.get("titulo"):
            t, h = _texto(it["titulo"], 80, 300, 78, "#2B1B3A", 920, ancora="start")
            s += t
            y = 300 + h - 40
        t, h = _texto(it["texto"], 540, y + 105, 54, "#2B1B3A", 860)
        s += f'<rect x="60" y="{y+20:.0f}" width="960" height="{h + 70:.0f}" rx="36" fill="#FFFFFF"/>' + t
        expr = ["convencido", "pensativo", "alegre", "confuso"][i % 4]
        pose = ["aponta", "mao_queixo", "bracos_ar", "encolhe"][i % 4]
        s += desenha(quem, 540, 1230, 0.95, expr, pose, t=i)
        out.append(s + "</svg>")

    # lição final
    s = _base(total - 1, total)
    t, h = _texto(licao, 540, 290, 64, "#FFFFFF", 860)
    s += f'<rect x="60" y="90" width="960" height="{h + 250:.0f}" rx="44" fill="{PERSONAGENS["ze"]["cor_legenda"]}"/>'
    s += f'<text x="540" y="190" font-family="{FONTE}" font-size="52" fill="#FFE27A" text-anchor="middle">A lição do Zé</text>' + t
    s += f'<text x="540" y="{h + 300:.0f}" font-family="{FONTE}" font-size="44" fill="#FFFFFF" text-anchor="middle">Guarda para não esquecer!</text>'
    s += desenha("ze", 380, 1230, 0.95, "alegre", "aponta", t=2)
    s += desenha("neurao", 800, 1230, 0.8, "confuso", "encolhe", t=3)
    out.append(s + "</svg>")
    return out


def gerar_carrossel(g, pasta):
    pasta = Path(pasta)
    pasta.mkdir(parents=True, exist_ok=True)
    ficheiros = []
    for i, svg in enumerate(slides(g), start=1):
        f = pasta / f"slide_{i:02d}.png"
        f.write_bytes(svg_para_png(svg))
        ficheiros.append(f)
    return ficheiros


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    caminho = Path(sys.argv[1])
    g = G.carregar(caminho)
    pasta = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("out/teste") / f"{caminho.stem}_carrossel"
    fs = gerar_carrossel(g, pasta)
    print(f"Carrossel pronto: {len(fs)} imagens em {pasta}")


if __name__ == "__main__":
    main()
