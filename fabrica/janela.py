"""A Janela do Dono: um painel holográfico que mostra a situação da pessoa em cuja
cabeça vivem o Neurão e o Zé (a hora, um desenho e uma legenda curta).

A imagem conta o contexto da história; as falas só têm de dar a piada e a ciência.

Campo "cena" de uma fala:
    {"icone": "portatil", "hora": "22:40", "texto": "A Marta abre o relatório."}
A janela mostra a última cena definida até ao momento. `hora` é opcional.
Campo "mostra_conceito": true numa fala faz aparecer a fita dourada com o nome do conceito.
"""
import math

from .aderecos import ADERECOS, desenha_adereco

L = "#2A1A2E"
FONTE = "Sniglet"

# janela (no mundo 1080x1920, entre a legenda e as cabeças dos personagens)
JX, JY, JW, JH = 240, 745, 600, 230
TEXTO_MAX = 40  # caracteres da legenda da cena


def _f(v):
    return f"{v:.1f}"


# ------------------------------------------------------------------ ícones novos
# desenhados com os "pés" em y=0 e até ~110 de altura (como os adereços)
def portatil(t):
    cursor = 1.0 if math.sin(t * 6) > 0 else 0.0
    s = f'<rect x="-64" y="-104" width="128" height="80" rx="9" fill="#2B2B3A" stroke="{L}" stroke-width="5"/>'
    s += '<rect x="-54" y="-94" width="108" height="60" rx="4" fill="#7DF9FF" fill-opacity="0.85"/>'
    for k, w in enumerate((70, 50, 62)):
        s += f'<rect x="-44" y="{-84 + k*14}" width="{w}" height="6" rx="3" fill="#1C2E5A" fill-opacity="0.5"/>'
    s += f'<rect x="{-44+62+4}" y="-44" width="6" height="12" fill="#1C2E5A" fill-opacity="{cursor}"/>'
    s += f'<path d="M-80,-24 L80,-24 L68,-8 L-68,-8 Z" fill="#B0B0C0" stroke="{L}" stroke-width="5" stroke-linejoin="round"/>'
    return s


def calendario(t):
    s = f'<rect x="-58" y="-104" width="116" height="100" rx="10" fill="#FFFFFF" stroke="{L}" stroke-width="5"/>'
    s += f'<rect x="-58" y="-104" width="116" height="28" rx="10" fill="#FF5A5A" stroke="{L}" stroke-width="5"/>'
    for sx in (-30, 30):
        s += f'<rect x="{sx-4}" y="-114" width="8" height="20" rx="4" fill="#2B2B3A"/>'
    for r in range(3):
        for c in range(4):
            s += f'<circle cx="{-36 + c*24}" cy="{-56 + r*20}" r="5" fill="#9A8FA6"/>'
    s += f'<circle cx="{-36 + 3*24}" cy="{-56 + 2*20}" r="{_f(11 + 2*math.sin(t*5))}" fill="none" stroke="#FF3B5C" stroke-width="5"/>'
    return s


def cama(t):
    s = f'<rect x="-76" y="-84" width="14" height="80" rx="5" fill="#8A6A4A" stroke="{L}" stroke-width="4"/>'
    s += f'<rect x="-70" y="-44" width="146" height="30" rx="8" fill="#7E8BD6" stroke="{L}" stroke-width="5"/>'
    s += f'<rect x="-66" y="-62" width="140" height="24" rx="10" fill="#B9C2FF" stroke="{L}" stroke-width="4"/>'
    s += f'<ellipse cx="-42" cy="-70" rx="26" ry="12" fill="#FFFFFF" stroke="{L}" stroke-width="4"/>'
    for i in range(3):
        fase = (t * 0.5 + i / 3) % 1.0
        s += (f'<text x="{_f(10 + 24*i)}" y="{_f(-84 - 28*fase)}" font-family="{FONTE}" font-size="{26 + 8*i}" '
              f'fill="#FFFFFF" fill-opacity="{1 - fase:.2f}">Z</text>')
    return s


def papeis(t):
    s = ""
    for k, (dx, dy, r) in enumerate(((-18, 0, -6), (14, -4, 5), (-2, -8, -1))):
        s += (f'<g transform="translate({dx},{dy}) rotate({r} 0 -50)">'
              f'<rect x="-42" y="-100" width="84" height="94" rx="5" fill="#FFFFFF" stroke="{L}" stroke-width="4"/>')
        for j in range(5):
            s += f'<rect x="-30" y="{-86 + j*14}" width="{60 - (j % 2)*18}" height="5" rx="2.5" fill="#9A8FA6"/>'
        s += '</g>'
    return s


def cartao(t):
    brilho = 0.5 + 0.5 * math.sin(t * 3)
    s = f'<rect x="-70" y="-84" width="140" height="88" rx="12" fill="#3B7BE0" stroke="{L}" stroke-width="5"/>'
    s += f'<rect x="-70" y="-66" width="140" height="18" fill="#1C2E5A"/>'
    s += f'<rect x="-52" y="-38" width="30" height="22" rx="4" fill="#FFD447" stroke="{L}" stroke-width="3"/>'
    s += f'<rect x="-4" y="-30" width="54" height="6" rx="3" fill="#FFFFFF" fill-opacity="0.8"/>'
    s += f'<path d="M-70,-84 L{_f(-70 + 140*brilho)},-84 L{_f(-40 + 140*brilho)},4 L-70,4 Z" fill="#FFFFFF" fill-opacity="0.10"/>'
    return s


def pizza(t):
    s = f'<path d="M0,-4 L-62,-92 Q0,-116 62,-92 Z" fill="#FFC857" stroke="{L}" stroke-width="5" stroke-linejoin="round"/>'
    s += f'<path d="M-62,-92 Q0,-116 62,-92" fill="none" stroke="#C9822B" stroke-width="10" stroke-linecap="round"/>'
    for x, y in ((-18, -66), (14, -78), (8, -46), (-4, -30)):
        s += f'<circle cx="{x}" cy="{y}" r="9" fill="#E8453C" stroke="{L}" stroke-width="3"/>'
    return s


def frigorifico(t):
    s = f'<rect x="-44" y="-112" width="88" height="112" rx="10" fill="#E8F1F5" stroke="{L}" stroke-width="5"/>'
    s += f'<path d="M-44,-70 L44,-70" stroke="{L}" stroke-width="5"/>'
    s += f'<rect x="28" y="-100" width="6" height="22" rx="3" fill="#8A8AA0"/><rect x="28" y="-62" width="6" height="30" rx="3" fill="#8A8AA0"/>'
    for i, c in enumerate(("#FF5A5A", "#FFD447", "#3DFF9A")):
        s += f'<rect x="{-32 + i*14}" y="{-96 + 5*(i % 2)}" width="10" height="10" rx="2" fill="{c}"/>'
    brilho = 0.5 + 0.5 * math.sin(t * 4)
    s += f'<path d="M-30,-28 L-10,-52 M-20,-22 L10,-46" stroke="#FFFFFF" stroke-width="6" stroke-linecap="round" stroke-opacity="{0.4 + 0.5*brilho:.2f}"/>'
    return s


def notificacao(t):
    k = 1 + 0.12 * max(0.0, math.sin(t * 7))
    s = f'<g transform="translate(0,-56) scale({k:.3f})"><circle r="46" fill="#FF3B5C" stroke="{L}" stroke-width="5"/>'
    s += f'<text x="0" y="12" font-family="{FONTE}" font-size="36" fill="#FFFFFF" text-anchor="middle">99+</text></g>'
    return s


def relogio(t):
    s = f'<circle cx="0" cy="-56" r="52" fill="#FFFFFF" stroke="{L}" stroke-width="6"/>'
    for i in range(12):
        a = i * math.pi / 6
        s += (f'<path d="M{_f(42*math.sin(a))},{_f(-56-42*math.cos(a))} L{_f(48*math.sin(a))},{_f(-56-48*math.cos(a))}" '
              f'stroke="{L}" stroke-width="4"/>')
    ah, am = t * 0.8, t * 9.6  # ponteiros a girar depressa: o tempo a passar
    s += f'<path d="M0,-56 L{_f(26*math.sin(ah))},{_f(-56-26*math.cos(ah))}" stroke="{L}" stroke-width="7" stroke-linecap="round"/>'
    s += f'<path d="M0,-56 L{_f(40*math.sin(am))},{_f(-56-40*math.cos(am))}" stroke="#FF5A5A" stroke-width="5" stroke-linecap="round"/>'
    s += f'<circle cx="0" cy="-56" r="6" fill="{L}"/>'
    return s


NOVOS = {"portatil": portatil, "calendario": calendario, "cama": cama, "papeis": papeis, "cartao": cartao,
         "pizza": pizza, "frigorifico": frigorifico, "notificacao": notificacao, "relogio": relogio}
ICONES = list(ADERECOS) + list(NOVOS)


def desenha_icone(nome, t=0.0):
    if nome in NOVOS:
        return NOVOS[nome](t)
    return desenha_adereco(nome, t)


# ------------------------------------------------------------------ janela
def _suave(k):
    k = max(0.0, min(1.0, k))
    return k * k * (3 - 2 * k)


def _esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def _linhas(texto, largura, tam):
    max_c = max(8, int(largura / (tam * 0.60)))
    linhas, atual = [], ""
    for p in texto.split():
        teste = (atual + " " + p).strip()
        if atual and len(teste) > max_c:
            linhas.append(atual)
            atual = p
        else:
            atual = teste
    if atual:
        linhas.append(atual)
    return linhas


def janela(cena, dono, t, t0):
    """SVG da janela para a cena (dict) que começou no instante t0."""
    k = _suave((t - t0) / 0.35)
    if k <= 0:
        return ""
    cx, cy = JX + JW / 2, JY + JH / 2
    s = (f'<g opacity="{k:.2f}" transform="translate({_f(cx)},{_f(cy)}) scale({0.9 + 0.1*k:.3f}) '
         f'translate({_f(-cx)},{_f(-cy)})">')
    s += f'<rect x="{JX}" y="{JY}" width="{JW}" height="{JH}" rx="26" fill="#0B1230" fill-opacity="0.94" stroke="#2EE6FF" stroke-width="5"/>'
    # cabeçalho
    s += f'<path d="M{JX+2},{JY+48} L{JX+JW-2},{JY+48}" stroke="#2EE6FF" stroke-opacity="0.5" stroke-width="3"/>'
    rec = 1.0 if math.sin(t * 5) > -0.2 else 0.15
    s += f'<circle cx="{JX+30}" cy="{JY+25}" r="8" fill="#FF3B5C" fill-opacity="{rec}"/>'
    nome = _esc(f"DONO · {dono}".upper())
    s += f'<text x="{JX+50}" y="{JY+34}" font-family="{FONTE}" font-size="26" fill="#7DF9FF">{nome}</text>'
    hora = cena.get("hora")
    if hora:
        s += f'<rect x="{JX+JW-150}" y="{JY+8}" width="132" height="34" rx="17" fill="#2EE6FF"/>'
        s += f'<text x="{JX+JW-84}" y="{JY+34}" font-family="{FONTE}" font-size="28" fill="#0B1230" text-anchor="middle">{_esc(hora)}</text>'
    # ícone (com ligeira flutuação)
    icone = cena.get("icone")
    if icone in ICONES:
        bob = 3 * math.sin(t * 3)
        s += (f'<g transform="translate({JX+105},{_f(JY+JH-38+bob)}) scale(1.2)">'
              + desenha_icone(icone, t) + "</g>")
    # legenda da cena: a letra encolhe até o texto caber em 3 linhas
    largura = JW - 215
    for tam in (40, 36, 32, 28):
        linhas = _linhas(cena.get("texto", ""), largura, tam)
        if len(linhas) <= 3:
            break
    linhas = linhas[:3]
    lh = tam + 7
    y0 = JY + 52 + tam + (3 - len(linhas)) * lh / 2
    for i, ln in enumerate(linhas):
        s += (f'<text x="{JX+195}" y="{_f(y0 + i*lh)}" font-family="{FONTE}" font-size="{tam}" '
              f'fill="#FFFFFF">{_esc(ln)}</text>')
    return s + "</g>"


def fita_conceito(nome, t, t0, y=1585):
    """Fita dourada com o nome do conceito, no espaço livre por baixo dos personagens."""
    k = _suave((t - t0) / 0.3)
    if k <= 0:
        return ""
    nome = _esc(nome)
    larg = min(960, 80 + len(nome) * 25 + 270)
    cx = 540
    s = (f'<g opacity="{k:.2f}" transform="translate({cx},{y+36}) scale({0.7 + 0.3*k:.3f}) translate({-cx},{-(y+36)})">')
    s += f'<rect x="{_f(cx - larg/2)}" y="{y}" width="{_f(larg)}" height="72" rx="36" fill="#FFD447" stroke="#7A5A00" stroke-width="6"/>'
    s += (f'<text x="{_f(cx)}" y="{y+49}" font-family="{FONTE}" font-size="40" fill="#2A1A2E" '
          f'text-anchor="middle">CONCEITO · {nome}</text>')
    return s + "</g>"


def cena_em(plano, t):
    """(cena, instante em que começou) válida no instante t, ou (None, 0)."""
    atual, t0 = None, 0.0
    for f in plano:
        c = f.get("cena")
        if isinstance(c, dict) and f["inicio"] - 0.15 <= t:
            atual, t0 = c, f["inicio"] - 0.15
    return atual, t0


def conceito_em(plano, g, t):
    """(nome, instante) do conceito a mostrar no instante t, ou (None, 0)."""
    c = g.get("conceito")
    nome = c.get("nome") if isinstance(c, dict) else None
    if not nome:
        return None, 0.0
    for f in plano:
        if f.get("mostra_conceito"):
            if f["inicio"] <= t <= f["fim"] + 2.5:
                return nome, f["inicio"]
    return None, 0.0
