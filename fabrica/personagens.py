"""Personagens originais da conta, desenhados em SVG (desenho 2D plano).

- Neurão: cérebro rosa-escuro, largo, óculos retangulares e laço azul. Convencido.
- Zé Neurónio: cérebro rosa-claro, redondo, com uma "antena" de neurónio. Alegre.

Uso principal:
    desenha("neurao", x, y, escala=1.0, expressao="convencido", pose="aponta",
            boca=0.0, piscar=0.0, t=0.0)
devolve um pedaço de SVG (<g>...</g>) com o personagem de pés no ponto (x, y).
"""
import math

PERSONAGENS = {
    "neurao": {
        "nome": "Neurão",
        "corpo": "#C73E6E", "dobras": "#9A2A52", "linha": "#4A1028",
        "rx": 200, "ry": 140, "lobos": 13,
        "cor_legenda": "#C73E6E",
    },
    "ze": {
        "nome": "Zé Neurónio",
        "corpo": "#F7A9C4", "dobras": "#E07AA0", "linha": "#6E2A48",
        "rx": 170, "ry": 152, "lobos": 11,
        "cor_legenda": "#E0628F",
    },
}

EXPRESSOES = {
    #               pálpebra   sobrancelha esq (interior, exterior)  dir        sorriso torto boca_base olhar
    "neutro":       dict(lid=0.10, be=(0, 0), bd=(0, 0), sorriso=0.25, torto=0, aberta=0.0, olhar=(0, 0)),
    "convencido":   dict(lid=0.45, be=(-4, -16), bd=(6, 10), sorriso=0.55, torto=12, aberta=0.0, olhar=(0.3, 0)),
    "alegre":       dict(lid=0.0, be=(-12, -12), bd=(-12, -12), sorriso=1.0, torto=0, aberta=0.3, olhar=(0, 0)),
    "confuso":      dict(lid=0.10, be=(-18, -10), bd=(8, 2), sorriso=-0.25, torto=-10, aberta=0.0, olhar=(0.5, -0.6)),
    "surpreendido": dict(lid=0.0, be=(-24, -22), bd=(-24, -22), sorriso=0.0, torto=0, aberta=0.65, olhar=(0, 0)),
    "triste":       dict(lid=0.30, be=(-14, 6), bd=(-14, 6), sorriso=-0.8, torto=0, aberta=0.0, olhar=(0, 0.5)),
    "pensativo":    dict(lid=0.30, be=(-4, -14), bd=(4, 4), sorriso=0.0, torto=6, aberta=0.0, olhar=(0.6, -0.7)),
}

# Alvo das mãos (relativo ao centro do cérebro, em múltiplos de rx/ry) e lado do cotovelo.
POSES = {
    "normal":     dict(esq=(-1.18, 1.10), dir=(1.18, 1.10)),
    "aponta":     dict(esq=(-1.18, 1.10), dir=(1.55, -0.55), dedo=True),
    "bracos_ar":  dict(esq=(-1.40, -0.95), dir=(1.40, -0.95)),
    "mao_queixo": dict(esq=(-1.18, 1.10), dir=(0.22, 0.86)),
    "encolhe":    dict(esq=(-1.40, 0.15), dir=(1.40, 0.15)),
}

PERNA = 105  # comprimento das pernas (px, escala 1)


def _f(v):
    return f"{v:.1f}"


def _mix(a, b, k):
    if isinstance(a, (tuple, list)):
        return tuple(_mix(x, y, k) for x, y in zip(a, b))
    return a + (b - a) * k


def _expr(e):
    """Nome de expressão, ou (antiga, nova, k) para uma transição suave."""
    if isinstance(e, (tuple, list)):
        a, b, k = _expr(e[0]), _expr(e[1]), e[2]
        return {c: _mix(a[c], b[c], k) for c in a}
    return EXPRESSOES.get(e, EXPRESSOES["neutro"])


def _pose(p):
    """Nome de pose, ou (antiga, nova, k) para uma transição suave."""
    if isinstance(p, (tuple, list)):
        a, b, k = _pose(p[0]), _pose(p[1]), p[2]
        r = {lado: _mix(a[lado], b[lado], k) for lado in ("esq", "dir")}
        r["dedo"] = (b if k > 0.5 else a).get("dedo", False)
        return r
    return POSES.get(p, POSES["normal"])


def _contorno_cerebro(rx, ry, lobos, t):
    """Contorno ondulado (tipo nuvem) à volta de uma elipse."""
    pts = []
    for i in range(lobos):
        a = -math.pi / 2 + 2 * math.pi * i / lobos
        pts.append((rx * math.cos(a), ry * math.sin(a)))
    d = f"M{_f(pts[0][0])},{_f(pts[0][1])}"
    for i in range(lobos):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % lobos]
        mx, my = (x1 + x2) / 2, (y1 + y2) / 2
        # empurra o ponto de controlo para fora (lobos menores em baixo)
        n = math.hypot(mx, my) or 1
        baixo = max(0.0, my / ry)
        bojo = 0.30 * (1 - 0.6 * baixo) + 0.02 * math.sin(t * 2 + i)
        cx, cy = mx + mx / n * bojo * 0.55 * rx, my + my / n * bojo * 0.55 * ry
        d += f" Q{_f(cx)},{_f(cy)} {_f(x2)},{_f(y2)}"
    return d + " Z"


def _dobras(rx, ry, cor):
    """Sulcos do cérebro (só na parte de cima, para não tapar a cara)."""
    w = 7
    linhas = [
        f"M0,{_f(-ry*0.98)} C{_f(-rx*0.06)},{_f(-ry*0.75)} {_f(rx*0.06)},{_f(-ry*0.6)} 0,{_f(-ry*0.42)}",
        f"M{_f(-rx*0.78)},{_f(-ry*0.30)} C{_f(-rx*0.6)},{_f(-ry*0.62)} {_f(-rx*0.42)},{_f(-ry*0.30)} {_f(-rx*0.30)},{_f(-ry*0.62)}",
        f"M{_f(rx*0.78)},{_f(-ry*0.30)} C{_f(rx*0.6)},{_f(-ry*0.62)} {_f(rx*0.42)},{_f(-ry*0.30)} {_f(rx*0.30)},{_f(-ry*0.62)}",
        f"M{_f(-rx*0.55)},{_f(-ry*0.80)} C{_f(-rx*0.45)},{_f(-ry*0.62)} {_f(-rx*0.25)},{_f(-ry*0.85)} {_f(-rx*0.15)},{_f(-ry*0.70)}",
        f"M{_f(rx*0.55)},{_f(-ry*0.80)} C{_f(rx*0.45)},{_f(-ry*0.62)} {_f(rx*0.25)},{_f(-ry*0.85)} {_f(rx*0.15)},{_f(-ry*0.70)}",
        f"M{_f(-rx*0.95)},{_f(ry*0.15)} C{_f(-rx*0.82)},{_f(ry*0.0)} {_f(-rx*0.86)},{_f(ry*0.35)} {_f(-rx*0.72)},{_f(ry*0.30)}",
        f"M{_f(rx*0.95)},{_f(ry*0.15)} C{_f(rx*0.82)},{_f(ry*0.0)} {_f(rx*0.86)},{_f(ry*0.35)} {_f(rx*0.72)},{_f(ry*0.30)}",
    ]
    return "".join(
        f'<path d="{d}" fill="none" stroke="{cor}" stroke-width="{w}" stroke-linecap="round"/>' for d in linhas
    )


def _olho(cx, cy, rx, ry, lid, olhar, cor_corpo, cor_linha):
    px = cx + olhar[0] * rx * 0.35
    py = cy + olhar[1] * ry * 0.30 + 6
    s = f'<ellipse cx="{_f(cx)}" cy="{_f(cy)}" rx="{rx}" ry="{ry}" fill="#FFFFFF" stroke="{cor_linha}" stroke-width="5"/>'
    s += f'<circle cx="{_f(px)}" cy="{_f(py)}" r="{rx*0.42:.1f}" fill="#1E1420"/>'
    s += f'<circle cx="{_f(px - rx*0.14)}" cy="{_f(py - rx*0.16)}" r="{rx*0.13:.1f}" fill="#FFFFFF"/>'
    lid = max(0.0, min(1.0, lid))
    if lid > 0.01:
        if lid >= 0.98:
            s += f'<ellipse cx="{_f(cx)}" cy="{_f(cy)}" rx="{rx+1}" ry="{ry+1}" fill="{cor_corpo}" stroke="{cor_linha}" stroke-width="5"/>'
            s += f'<path d="M{_f(cx-rx*0.8)},{_f(cy+2)} Q{_f(cx)},{_f(cy+ry*0.35)} {_f(cx+rx*0.8)},{_f(cy+2)}" fill="none" stroke="{cor_linha}" stroke-width="6" stroke-linecap="round"/>'
        else:
            yl = -ry + 2 * ry * lid  # altura da pálpebra (relativa ao centro)
            xc = rx * math.sqrt(max(0.0, 1 - (yl / ry) ** 2))
            grande = 1 if yl > 0 else 0
            d = (f"M{_f(cx-xc)},{_f(cy+yl)} A{rx},{ry} 0 {grande} 1 {_f(cx+xc)},{_f(cy+yl)} Z")
            s += f'<path d="{d}" fill="{cor_corpo}" stroke="{cor_linha}" stroke-width="5" stroke-linejoin="round"/>'
    return s


def _boca(w, sorriso, torto, aberta, cor_linha):
    ye, yd = -sorriso * 14 + torto * 0.5, -sorriso * 14 - torto * 0.5
    if aberta < 0.06:
        cy = sorriso * 26
        return (f'<path d="M{-w},{_f(ye)} Q0,{_f(cy)} {w},{_f(yd)}" fill="none" '
                f'stroke="{cor_linha}" stroke-width="7" stroke-linecap="round"/>')
    w2 = w * (1 - 0.25 * aberta * (1 - max(sorriso, 0)))
    ctop = -aberta * 18 - sorriso * 4
    cbot = sorriso * 20 + aberta * 62
    d = (f"M{_f(-w2)},{_f(ye)} Q0,{_f(ctop)} {_f(w2)},{_f(yd)} "
         f"Q0,{_f(cbot)} {_f(-w2)},{_f(ye)} Z")
    s = f'<path d="{d}" fill="#5A0F2C" stroke="{cor_linha}" stroke-width="6" stroke-linejoin="round"/>'
    if aberta > 0.25:
        ty = cbot * 0.42
        s += f'<ellipse cx="0" cy="{_f(ty)}" rx="{_f(w2*0.42)}" ry="{_f(aberta*12)}" fill="#E8607E"/>'
    return s


def _braco(ombro, mao, comp, cotovelo_baixo, cor, cor_linha, dedo=False):
    """Braço de dois segmentos com cinemática inversa simples."""
    (sx, sy), (hx, hy) = ombro, mao
    dx, dy = hx - sx, hy - sy
    dist = min(math.hypot(dx, dy), 2 * comp - 1)
    ang = math.atan2(dy, dx)
    ex_ang = math.acos(max(-1.0, min(1.0, dist / (2 * comp))))
    sinal = 1 if (cotovelo_baixo ^ (dx < 0)) else -1
    ex = sx + comp * math.cos(ang + sinal * ex_ang)
    ey = sy + comp * math.sin(ang + sinal * ex_ang)
    hx, hy = sx + dist * math.cos(ang), sy + dist * math.sin(ang)
    d = f"M{_f(sx)},{_f(sy)} Q{_f(ex)},{_f(ey)} {_f(hx)},{_f(hy)}"
    s = f'<path d="{d}" fill="none" stroke="{cor_linha}" stroke-width="22" stroke-linecap="round"/>'
    s += f'<path d="{d}" fill="none" stroke="{cor}" stroke-width="12" stroke-linecap="round"/>'
    if dedo:
        fx, fy = hx + 30 * math.cos(ang), hy + 30 * math.sin(ang)
        s += f'<path d="M{_f(hx)},{_f(hy)} L{_f(fx)},{_f(fy)}" stroke="{cor_linha}" stroke-width="16" stroke-linecap="round"/>'
        s += f'<path d="M{_f(hx)},{_f(hy)} L{_f(fx)},{_f(fy)}" stroke="#FFFFFF" stroke-width="7" stroke-linecap="round"/>'
    s += f'<circle cx="{_f(hx)}" cy="{_f(hy)}" r="19" fill="#FFFFFF" stroke="{cor_linha}" stroke-width="5"/>'
    return s


def _acessorios_frente(quem, p, rx, ry, eye_y, eye_dx, eye_rx, eye_ry):
    s = ""
    if quem == "neurao":
        L = p["linha"]
        for sx in (-1, 1):
            x0 = sx * eye_dx - eye_rx - 12
            s += (f'<rect x="{_f(x0)}" y="{_f(eye_y-eye_ry-8)}" width="{_f(2*eye_rx+24)}" '
                  f'height="{_f(2*eye_ry+16)}" rx="16" fill="#FFFFFF" fill-opacity="0.12" '
                  f'stroke="{L}" stroke-width="9"/>')
        s += (f'<path d="M{_f(-eye_dx+eye_rx+12)},{_f(eye_y-6)} Q0,{_f(eye_y-22)} '
              f'{_f(eye_dx-eye_rx-12)},{_f(eye_y-6)}" fill="none" stroke="{L}" stroke-width="8"/>')
    return s


def _acessorios_tras(quem, p, rx, ry, t):
    s = ""
    if quem == "ze":
        L = p["linha"]
        bal = 10 * math.sin(t * 3.1)
        tx, ty = 18 + bal, -ry - 85
        d = f"M8,{_f(-ry*0.95)} C-20,{_f(-ry-30)} 40,{_f(-ry-45)} {_f(tx)},{_f(ty)}"
        s += f'<path d="{d}" fill="none" stroke="{L}" stroke-width="13" stroke-linecap="round"/>'
        s += f'<path d="{d}" fill="none" stroke="{p["dobras"]}" stroke-width="6" stroke-linecap="round"/>'
        for a in (-0.9, 0.0, 0.9):
            bx, by = tx + 26 * math.sin(a), ty - 26 * math.cos(a)
            s += f'<path d="M{_f(tx)},{_f(ty)} L{_f(bx)},{_f(by)}" stroke="{L}" stroke-width="7" stroke-linecap="round"/>'
            s += f'<circle cx="{_f(bx)}" cy="{_f(by)}" r="7" fill="#FFE27A" stroke="{L}" stroke-width="4"/>'
        s += f'<circle cx="{_f(tx)}" cy="{_f(ty)}" r="13" fill="#FFE27A" stroke="{L}" stroke-width="5"/>'
    return s


def _laco(p, ry):
    L = "#1C2E5A"
    y = ry * 1.12
    return (f'<path d="M0,{_f(y)} L-38,{_f(y-22)} L-38,{_f(y+22)} Z M0,{_f(y)} L38,{_f(y-22)} L38,{_f(y+22)} Z" '
            f'fill="#3B7BE0" stroke="{L}" stroke-width="5" stroke-linejoin="round"/>'
            f'<circle cx="0" cy="{_f(y)}" r="11" fill="#3B7BE0" stroke="{L}" stroke-width="5"/>')


def desenha(quem, x, y, escala=1.0, expressao="neutro", pose="normal",
            boca=0.0, piscar=0.0, t=0.0, virado=1, olhar=None):
    """Devolve SVG do personagem com os pés no ponto (x, y).

    boca: 0..1, abertura extra da boca (fala).  piscar: 0..1 (1 = olhos fechados).
    virado: 1 = olha para a direita do ecrã, -1 = espelhado.
    """
    p = PERSONAGENS[quem]
    e = _expr(expressao)
    po = _pose(pose)
    rx, ry = p["rx"], p["ry"]

    resp = 1 + 0.012 * math.sin(t * 2.4 + (0 if quem == "ze" else 1.5))
    salto = -abs(math.sin(t * 9)) * 8 * min(1.0, boca * 2)
    cy = -(ry + PERNA) + salto  # centro do cérebro

    L, C = p["linha"], p["corpo"]
    s = f'<g transform="translate({_f(x)},{_f(y)}) scale({_f(escala*virado)},{_f(escala)})">'
    # sombra
    s += f'<ellipse cx="0" cy="4" rx="{_f(rx*0.75)}" ry="18" fill="#000000" fill-opacity="0.16"/>'
    # pernas e pés
    for sx in (-1, 1):
        hx = sx * rx * 0.30
        s += f'<path d="M{_f(hx)},{_f(cy+ry*0.8)} L{_f(hx)},-14" stroke="{L}" stroke-width="22" stroke-linecap="round"/>'
        s += f'<path d="M{_f(hx)},{_f(cy+ry*0.8)} L{_f(hx)},-14" stroke="{C}" stroke-width="12" stroke-linecap="round"/>'
        s += f'<ellipse cx="{_f(hx + sx*14)}" cy="-10" rx="38" ry="17" fill="#2B2B3A" stroke="{L}" stroke-width="4"/>'

    s += f'<g transform="translate(0,{_f(cy)}) scale(1,{resp:.4f})">'
    s += _acessorios_tras(quem, p, rx, ry, t)
    s += f'<path d="{_contorno_cerebro(rx, ry, p["lobos"], t)}" fill="{C}" stroke="{L}" stroke-width="8" stroke-linejoin="round"/>'
    s += _dobras(rx, ry, p["dobras"])
    # bochechas
    for sx in (-1, 1):
        s += f'<ellipse cx="{_f(sx*rx*0.58)}" cy="{_f(ry*0.36)}" rx="26" ry="15" fill="#FF6F91" fill-opacity="0.35"/>'

    eye_y, eye_dx, eye_rx, eye_ry = -ry * 0.10, rx * 0.30, 40, 48
    olhar = olhar if olhar is not None else e["olhar"]
    lid = max(e["lid"], piscar)
    for sx, b in ((-1, e["be"]), (1, e["bd"])):
        s += _olho(sx * eye_dx, eye_y, eye_rx, eye_ry, lid, olhar, C, L)
        # sobrancelha: b = (interior, exterior)
        by = eye_y - eye_ry - 22
        xi, xo = sx * (eye_dx - eye_rx * 0.7), sx * (eye_dx + eye_rx * 0.9)
        s += (f'<path d="M{_f(xi)},{_f(by+b[0])} L{_f(xo)},{_f(by+b[1])}" stroke="{L}" '
              f'stroke-width="12" stroke-linecap="round"/>')
    s += _acessorios_frente(quem, p, rx, ry, eye_y, eye_dx, eye_rx, eye_ry)

    aberta = max(e["aberta"], 0) + boca * 0.85
    s += f'<g transform="translate(0,{_f(ry*0.50)})">{_boca(46, e["sorriso"], e["torto"], min(aberta, 1.0), L)}</g>'
    if quem == "neurao":
        s += _laco(p, ry)

    # braços (desenhados por cima, para a pose "mão no queixo")
    oscila = 4 * math.sin(t * 2.0)
    ombros = {"esq": (-rx * 0.86, ry * 0.30), "dir": (rx * 0.86, ry * 0.30)}
    for lado in ("esq", "dir"):
        mx, my = po[lado]
        alvo = (mx * rx, my * ry + oscila)
        s += _braco(ombros[lado], alvo, 82, True, C, L, dedo=(po.get("dedo") and lado == "dir"))
    s += "</g></g>"
    return s


def folha_de_modelo():
    """SVG com todas as expressões e poses dos dois personagens (para conferir o desenho)."""
    W, H = 1800, 2600
    s = f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}"><rect width="{W}" height="{H}" fill="#FFF6EC"/>'
    exprs = list(EXPRESSOES)
    for i, ex in enumerate(exprs):
        col, lin = i % 4, i // 4
        s += desenha("neurao", 225 + col * 450, 520 + lin * 620, 0.62, ex, "normal", t=i)
        s += f'<text x="{225+col*450}" y="{580+lin*620}" font-family="Sniglet" font-size="34" text-anchor="middle">{ex}</text>'
    for i, ex in enumerate(exprs):
        col, lin = i % 4, i // 4
        s += desenha("ze", 225 + col * 450, 1780 + lin * 620, 0.62, ex, list(POSES)[i % len(POSES)], t=i)
        s += f'<text x="{225+col*450}" y="{1840+lin*620}" font-family="Sniglet" font-size="30" text-anchor="middle">{ex} / {list(POSES)[i % len(POSES)]}</text>'
    return s + "</svg>"


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
    from fabrica.render import svg_para_png
    destino = Path("out/folha_de_modelo.png")
    destino.parent.mkdir(exist_ok=True)
    destino.write_bytes(svg_para_png(folha_de_modelo()))
    print("Folha de modelo gravada em", destino)
