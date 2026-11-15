"""O mundo: o interior de um cérebro futurista (desenho plano em SVG, animado).

Zonas (campo "cenario" do guião):
  comando   - Centro de Comando: ecrãs holográficos com medidores (foco, energia...)
  memorias  - Arquivo de Memórias: prateleiras de esferas de memória a brilhar
  dopamina  - Centro da Recompensa: estrelas a subir, letreiro de néon
  sono      - Zona do Sono: luzes baixas, lua holográfica, "Zzz"

Em todas: rede de neurónios ao fundo com sinais a viajar, e uma plataforma
com grelha de néon. A decoração fica nas margens e atrás dos personagens.
"""
import math
import random

# nomes antigos continuam a funcionar
ALIASES = {"sala": "comando", "quarto": "sono", "loja": "dopamina"}

TEMAS = {
    #            fundo topo  fundo base  néon 1     néon 2     chão
    "comando":  ("#070B24", "#141A4A", "#2EE6FF", "#8A7CFF", "#0D1236"),
    "memorias": ("#120726", "#2A0F4D", "#C77DFF", "#FF5FC8", "#1A0B33"),
    "dopamina": ("#1A0820", "#3D0F3A", "#FFC93C", "#FF4FA3", "#240A26"),
    "sono":     ("#04061A", "#0C1640", "#6FA8FF", "#B9A7FF", "#070C26"),
}


def _f(v):
    return f"{v:.1f}"


def _rede(W, chao, cor1, cor2, t, semente=3):
    """Rede de neurónios com sinais luminosos a viajar pelas ligações."""
    rnd = random.Random(semente)
    nos = []
    for i in range(26):
        x = rnd.uniform(-40, W + 40)
        y = rnd.uniform(40, chao - 120)
        nos.append((x, y, rnd.uniform(5, 10), rnd.random() * 6.28))
    ligacoes = []
    for i, (x, y, _, _) in enumerate(nos):
        dist = sorted(range(len(nos)), key=lambda j: (nos[j][0] - x) ** 2 + (nos[j][1] - y) ** 2)
        for j in dist[1:3]:
            if (j, i) not in ligacoes:
                ligacoes.append((i, j))
    s = ""
    for k, (i, j) in enumerate(ligacoes):
        x1, y1 = nos[i][:2]
        x2, y2 = nos[j][:2]
        cx, cy = (x1 + x2) / 2 + 30 * math.sin(k), (y1 + y2) / 2 - 30 * math.cos(k)
        cor = cor1 if k % 2 else cor2
        s += (f'<path d="M{_f(x1)},{_f(y1)} Q{_f(cx)},{_f(cy)} {_f(x2)},{_f(y2)}" fill="none" '
              f'stroke="{cor}" stroke-opacity="0.22" stroke-width="3"/>')
        # sinal a viajar (só em algumas ligações)
        if k % 3 == 0:
            u = ((t * 0.45 + k * 0.137) % 1.0)
            px = (1 - u) ** 2 * x1 + 2 * (1 - u) * u * cx + u ** 2 * x2
            py = (1 - u) ** 2 * y1 + 2 * (1 - u) * u * cy + u ** 2 * y2
            s += f'<circle cx="{_f(px)}" cy="{_f(py)}" r="14" fill="{cor}" fill-opacity="0.18"/>'
            s += f'<circle cx="{_f(px)}" cy="{_f(py)}" r="5" fill="#FFFFFF"/>'
    for i, (x, y, r, ph) in enumerate(nos):
        cor = cor1 if i % 2 else cor2
        b = 0.5 + 0.5 * math.sin(t * 2.2 + ph)
        s += f'<circle cx="{_f(x)}" cy="{_f(y)}" r="{_f(r*2.6)}" fill="{cor}" fill-opacity="{0.08 + 0.12*b:.2f}"/>'
        s += f'<circle cx="{_f(x)}" cy="{_f(y)}" r="{_f(r)}" fill="{cor}" fill-opacity="0.85"/>'
    return s


def _plataforma(W, H, chao, cor_chao, neon):
    """Chão com grelha em perspetiva e linha de horizonte a brilhar."""
    s = f'<rect x="0" y="{chao-40}" width="{W}" height="{H-chao+40}" fill="{cor_chao}"/>'
    fuga_x, fuga_y = W / 2, chao - 380
    for i in range(-8, 9):
        xb = W / 2 + i * 190
        # recorta a linha a partir do horizonte
        k = 40 / (H - fuga_y)
        x0 = fuga_x + (xb - fuga_x) * ((chao - 40 - fuga_y) / (H - fuga_y))
        s += (f'<path d="M{_f(x0)},{chao-40} L{_f(xb)},{H}" stroke="{neon}" '
              f'stroke-opacity="0.35" stroke-width="3"/>')
    y, passo = chao - 40, 18
    while y < H:
        s += f'<rect x="0" y="{_f(y)}" width="{W}" height="3" fill="{neon}" fill-opacity="0.30"/>'
        y += passo
        passo *= 1.35
    s += f'<rect x="0" y="{chao-46}" width="{W}" height="12" fill="{neon}" fill-opacity="0.18"/>'
    s += f'<rect x="0" y="{chao-42}" width="{W}" height="4" fill="{neon}" fill-opacity="0.9"/>'
    return s


def _holograma(x, y, w, h, neon, conteudo):
    s = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="{neon}" fill-opacity="0.08" stroke="{neon}" stroke-opacity="0.7" stroke-width="4"/>'
    s += f'<path d="M{x+w*0.3},{y+h} L{x+w*0.5},{y+h+50} L{x+w*0.7},{y+h}" fill="{neon}" fill-opacity="0.10"/>'
    return s + conteudo


def _texto(x, y, txt, tam, cor, op=1.0, ancora="middle"):
    return (f'<text x="{x}" y="{y}" font-family="Sniglet" font-size="{tam}" fill="{cor}" '
            f'fill-opacity="{op}" text-anchor="{ancora}">{txt}</text>')


def _zona_comando(W, chao, n1, n2, t):
    s = ""
    # ecrã esquerdo: medidor de foco a descer
    foco = 12 + 6 * math.sin(t * 0.8)
    c = _texto(150, chao - 640, "FOCO", 30, n1, 0.9)
    c += f'<rect x="70" y="{chao-615}" width="160" height="22" rx="11" fill="{n1}" fill-opacity="0.2"/>'
    c += f'<rect x="70" y="{chao-615}" width="{_f(160*foco/100)}" height="22" rx="11" fill="#FF4F6D"/>'
    c += _texto(150, chao - 545, f"{foco:.0f}%", 44, "#FFFFFF", 0.9)
    s += _holograma(50, chao - 690, 200, 180, n1, c)
    # ecrã direito: onda cerebral
    pts = " ".join(f"{_f(840 + i*8)},{_f(chao - 580 + 28*math.sin(i*0.6 + t*5))}" for i in range(23))
    c = f'<polyline points="{pts}" fill="none" stroke="{n2}" stroke-width="5"/>'
    c += _texto(930, chao - 645, "ENERGIA", 28, n2, 0.9)
    s += _holograma(825, chao - 690, 210, 180, n2, c)
    return s


def _zona_memorias(W, chao, n1, n2, t):
    s = ""
    for lado in (0, 1):
        x0 = 30 if lado == 0 else W - 200
        for j in range(3):
            y = chao - 560 + j * 150
            s += f'<rect x="{x0}" y="{y+40}" width="170" height="10" rx="5" fill="{n1}" fill-opacity="0.5"/>'
            for k in range(3):
                cx = x0 + 32 + k * 53
                b = 0.5 + 0.5 * math.sin(t * 2 + j + k * 1.7 + lado)
                cor = [n1, n2, "#7DF9FF"][(j + k + lado) % 3]
                s += f'<circle cx="{cx}" cy="{y+14}" r="{_f(30 + 6*b)}" fill="{cor}" fill-opacity="{0.10 + 0.15*b:.2f}"/>'
                s += f'<circle cx="{cx}" cy="{y+14}" r="22" fill="{cor}" fill-opacity="0.75"/>'
                s += f'<circle cx="{cx-7}" cy="{y+7}" r="6" fill="#FFFFFF" fill-opacity="0.7"/>'
    return s


def _zona_dopamina(W, chao, n1, n2, t):
    s = ""
    rnd = random.Random(11)
    for i in range(14):
        x = rnd.uniform(30, W - 30)
        vel = rnd.uniform(40, 90)
        y = chao - 60 - ((t * vel + rnd.uniform(0, 700)) % 700)
        r = rnd.uniform(10, 20)
        cor = n1 if i % 2 else n2
        op = max(0.0, min(1.0, (chao - 60 - y) / 120)) * 0.85
        s += _estrela(x, y, r, cor, op, t * 1.5 + i)
    b = 0.75 + 0.25 * (math.sin(t * 7) > -0.7)
    s += f'<g opacity="{b:.2f}">'
    s += f'<rect x="30" y="{chao-610}" width="330" height="84" rx="42" fill="#120A2E" fill-opacity="0.6" stroke="{n2}" stroke-width="7"/>'
    s += _texto(195, chao - 554, "RECOMPENSA", 38, n1)
    return s + "</g>"


def _estrela(x, y, r, cor, op, rot):
    pts = []
    for i in range(10):
        a = rot + i * math.pi / 5
        rr = r if i % 2 == 0 else r * 0.45
        pts.append(f"{_f(x + rr*math.sin(a))},{_f(y - rr*math.cos(a))}")
    return f'<polygon points="{" ".join(pts)}" fill="{cor}" fill-opacity="{op:.2f}"/>'


def _zona_sono(W, chao, n1, n2, t):
    s = f'<circle cx="900" cy="{chao-600}" r="80" fill="{n2}" fill-opacity="0.12"/>'
    s += f'<circle cx="900" cy="{chao-600}" r="56" fill="{n2}" fill-opacity="0.8"/>'
    s += f'<circle cx="926" cy="{chao-620}" r="52" fill="#0C1640"/>'
    for i in range(3):
        fase = (t * 0.5 + i / 3) % 1.0
        s += _texto(_f(120 + 40 * i + 20 * fase), _f(chao - 520 - 160 * fase), "Z", 40 + 14 * i, n1, 1 - fase)
    return s


ZONAS = {"comando": _zona_comando, "memorias": _zona_memorias,
         "dopamina": _zona_dopamina, "sono": _zona_sono}


def cenario(nome, W=1080, H=1920, chao=1480, t=0.0, focos=(300, 780)):
    nome = ALIASES.get(nome, nome)
    if nome not in TEMAS:
        nome = "comando"
    topo, base, n1, n2, cor_chao = TEMAS[nome]
    s = (f'<defs><linearGradient id="ceu" x1="0" y1="0" x2="0" y2="1">'
         f'<stop offset="0" stop-color="{topo}"/><stop offset="1" stop-color="{base}"/></linearGradient>'
         f'<radialGradient id="foco"><stop offset="0" stop-color="{n1}" stop-opacity="0.45"/>'
         f'<stop offset="1" stop-color="{n1}" stop-opacity="0"/></radialGradient></defs>')
    s += f'<rect width="{W}" height="{H}" fill="url(#ceu)"/>'
    s += _rede(W, chao, n1, n2, t, semente=len(nome))
    s += _plataforma(W, H, chao, cor_chao, n1)
    dy = 80 if nome == "comando" else 0
    s += f'<g transform="translate(0,{dy})">' + ZONAS[nome](W, chao, n1, n2, t) + "</g>"
    # luz atrás de cada personagem (dá contraste com o fundo escuro)
    for x in focos:
        s += f'<ellipse cx="{x}" cy="{chao-230}" rx="300" ry="330" fill="url(#foco)"/>'
        s += f'<ellipse cx="{x}" cy="{chao}" rx="220" ry="40" fill="{n1}" fill-opacity="0.25"/>'
    return s


def zonas():
    return list(ZONAS)
