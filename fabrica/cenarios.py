"""Cenários de fundo (1080 de largura, altura variável), desenho plano em SVG.

A decoração fica nas margens e atrás dos personagens, para não competir com o texto.
O chão começa em `chao` (y).
"""


def _chao_madeira(chao, W, H, cor="#C98F5E", risca="#B07A4C"):
    s = f'<rect x="0" y="{chao}" width="{W}" height="{H-chao}" fill="{cor}"/>'
    for i, y in enumerate(range(chao + 50, H, 70)):
        s += f'<rect x="0" y="{y}" width="{W}" height="5" fill="{risca}"/>'
        off = (i % 2) * 180
        for x in range(off, W, 360):
            s += f'<rect x="{x}" y="{y-70}" width="5" height="70" fill="{risca}"/>'
    return s


def _planta(x, y, k=1.0):
    s = f'<g transform="translate({x},{y}) scale({k})">'
    for a, c in ((-30, "#3E9B6A"), (0, "#4FB47C"), (30, "#3E9B6A"), (-55, "#58C08A"), (55, "#58C08A")):
        s += f'<ellipse cx="0" cy="-120" rx="26" ry="90" fill="{c}" transform="rotate({a} 0 -20)"/>'
    s += '<path d="M-60,-30 L60,-30 L45,60 L-45,60 Z" fill="#E07A4F" stroke="#9C4A2A" stroke-width="5"/>'
    return s + "</g>"


def sala(W=1080, H=1920, chao=1480):
    s = f'<rect width="{W}" height="{H}" fill="#FFE8D2"/>'
    s += f'<rect x="0" y="{chao-260}" width="{W}" height="260" fill="#F6D2B4"/>'
    s += f'<rect x="0" y="{chao-268}" width="{W}" height="12" fill="#E9B990"/>'
    # janela à direita, quadro à esquerda (atrás dos personagens)
    s += f'<rect x="760" y="{chao-640}" width="260" height="300" rx="14" fill="#BFE6FF" stroke="#E9B990" stroke-width="14"/>'
    s += f'<path d="M890,{chao-640} L890,{chao-340} M760,{chao-490} L1020,{chao-490}" stroke="#E9B990" stroke-width="10"/>'
    s += f'<circle cx="960" cy="{chao-590}" r="28" fill="#FFE27A"/>'
    s += f'<rect x="70" y="{chao-600}" width="200" height="150" rx="8" fill="#FFF6EC" stroke="#C98F5E" stroke-width="10"/>'
    s += f'<path d="M95,{chao-480} L150,{chao-560} L190,{chao-510} L215,{chao-540} L250,{chao-480} Z" fill="#7DC4A0"/>'
    s += _chao_madeira(chao, W, H)
    s += _planta(80, chao - 40, 0.9)
    return s


def quarto(W=1080, H=1920, chao=1480):
    s = f'<rect width="{W}" height="{H}" fill="#2F3B73"/>'
    for x, y, r in ((90, 120, 4), (300, 90, 3), (520, 140, 5), (760, 80, 3), (980, 150, 4),
                    (160, 480, 3), (990, 520, 4), (60, 900, 3), (1010, 860, 3)):
        s += f'<circle cx="{x}" cy="{y}" r="{r}" fill="#FFF3B0" fill-opacity="0.8"/>'
    s += f'<rect x="740" y="{chao-650}" width="270" height="300" rx="135" fill="#1C2550" stroke="#4A5596" stroke-width="14"/>'
    s += f'<circle cx="875" cy="{chao-510}" r="60" fill="#FFF3B0"/><circle cx="900" cy="{chao-530}" r="55" fill="#1C2550"/>'
    # cama à esquerda
    s += f'<rect x="-20" y="{chao-200}" width="330" height="200" rx="20" fill="#7E8BD6" stroke="#3B4580" stroke-width="8"/>'
    s += f'<rect x="20" y="{chao-250}" width="150" height="70" rx="30" fill="#FFFFFF" stroke="#3B4580" stroke-width="6"/>'
    s += _chao_madeira(chao, W, H, "#5B4A7A", "#4A3B66")
    return s


def loja(W=1080, H=1920, chao=1480):
    s = f'<rect width="{W}" height="{H}" fill="#E3F4EF"/>'
    cores = ["#FF8A65", "#FFD54F", "#4FC3F7", "#BA68C8", "#81C784", "#F06292"]
    for j, y in enumerate((chao - 620, chao - 400, chao - 180)):
        s += f'<rect x="0" y="{y+120}" width="{W}" height="18" fill="#A7C7BE"/>'
        for i, x in enumerate(range(30 + j * 25, W, 120)):
            c = cores[(i + j) % len(cores)]
            h = 70 + (i * 37 + j * 13) % 50
            s += f'<rect x="{x}" y="{y+120-h}" width="80" height="{h}" rx="8" fill="{c}" fill-opacity="0.75"/>'
    s += f'<rect x="0" y="{chao}" width="{W}" height="{H-chao}" fill="#CFE0DA"/>'
    for x in range(0, W, 120):
        s += f'<rect x="{x}" y="{chao}" width="4" height="{H-chao}" fill="#B8CEC6"/>'
    for y in range(chao, H, 120):
        s += f'<rect x="0" y="{y}" width="{W}" height="4" fill="#B8CEC6"/>'
    return s


CENARIOS = {"sala": sala, "quarto": quarto, "loja": loja}


def cenario(nome, W=1080, H=1920, chao=1480):
    return CENARIOS.get(nome, sala)(W, H, chao)
