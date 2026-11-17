"""Objetos que os personagens podem segurar na mão (campo "adereco" de uma fala).

Cada desenho é feito à volta da mão, no ponto (0, 0), em SVG plano.
"""
import math

L = "#2A1A2E"  # cor do contorno


def _f(v):
    return f"{v:.1f}"


def telefone(t):
    brilho = 0.75 + 0.25 * math.sin(t * 6)
    s = f'<rect x="-30" y="-110" width="60" height="108" rx="12" fill="#2B2B3A" stroke="{L}" stroke-width="5"/>'
    s += f'<rect x="-23" y="-100" width="46" height="84" rx="6" fill="#7DF9FF" fill-opacity="{brilho:.2f}"/>'
    s += '<rect x="-16" y="-90" width="32" height="8" rx="4" fill="#FFFFFF" fill-opacity="0.8"/>'
    s += '<rect x="-16" y="-76" width="22" height="8" rx="4" fill="#FFFFFF" fill-opacity="0.6"/>'
    s += f'<circle cx="24" cy="-108" r="11" fill="#FF3B5C" stroke="{L}" stroke-width="3"/>'
    return s


def carrinho(t):
    s = (f'<path d="M-70,-90 L-50,-90 L-30,-20 L50,-20 L62,-70 L-42,-70" fill="none" stroke="{L}" '
         f'stroke-width="7" stroke-linejoin="round" stroke-linecap="round"/>')
    for i, c in enumerate(("#FF8A65", "#4FC3F7", "#FFD54F")):
        s += f'<rect x="{-30 + i*26}" y="{-98 + (i % 2) * 8}" width="24" height="{30 - (i % 2) * 8}" rx="4" fill="{c}" stroke="{L}" stroke-width="3"/>'
    s += f'<path d="M-36,-46 L54,-46" stroke="{L}" stroke-width="4"/>'
    for x in (-22, 40):
        s += f'<circle cx="{x}" cy="-6" r="11" fill="#2B2B3A" stroke="{L}" stroke-width="4"/>'
    return s


def almofada(t):
    s = (f'<path d="M-70,-90 Q0,-104 70,-90 Q80,-50 70,-10 Q0,4 -70,-10 Q-80,-50 -70,-90 Z" '
         f'fill="#E8E2FF" stroke="{L}" stroke-width="5"/>')
    s += f'<path d="M-50,-50 Q0,-38 50,-50" fill="none" stroke="#B9A7FF" stroke-width="4" stroke-dasharray="8 8"/>'
    return s


def moeda(t):
    larg = 34 * abs(math.cos(t * 3)) + 6  # a rodar
    s = f'<ellipse cx="0" cy="-40" rx="{_f(larg)}" ry="34" fill="#FFD447" stroke="#7A5A00" stroke-width="5"/>'
    s += f'<ellipse cx="0" cy="-40" rx="{_f(larg*0.62)}" ry="21" fill="none" stroke="#C99A00" stroke-width="4"/>'
    return s


def despertador(t):
    rot = 9 * math.sin(t * 34)  # a tremer
    s = f'<g transform="rotate({rot:.1f} 0 -50)">'
    for sx in (-1, 1):
        s += f'<circle cx="{sx*26}" cy="-88" r="14" fill="#FF5A5A" stroke="{L}" stroke-width="4"/>'
    s += f'<circle cx="0" cy="-50" r="40" fill="#FF5A5A" stroke="{L}" stroke-width="5"/>'
    s += f'<circle cx="0" cy="-50" r="30" fill="#FFFFFF" stroke="{L}" stroke-width="3"/>'
    s += f'<path d="M0,-50 L0,-72 M0,-50 L15,-42" stroke="{L}" stroke-width="5" stroke-linecap="round"/>'
    return s + "</g>"


def lampada(t):
    b = 0.5 + 0.5 * math.sin(t * 5)
    s = f'<circle cx="0" cy="-80" r="{_f(60 + 8*b)}" fill="#FFE27A" fill-opacity="{0.18 + 0.12*b:.2f}"/>'
    s += f'<path d="M-30,-70 A38,38 0 1 1 30,-70 Q18,-52 16,-36 L-16,-36 Q-18,-52 -30,-70 Z" fill="#FFE27A" stroke="{L}" stroke-width="5"/>'
    s += f'<rect x="-17" y="-36" width="34" height="22" rx="5" fill="#B0B0C0" stroke="{L}" stroke-width="4"/>'
    s += f'<path d="M-8,-60 Q0,-76 8,-60" fill="none" stroke="#FFA62B" stroke-width="4"/>'
    for i in range(6):
        a = i * math.pi / 3 + t
        s += (f'<path d="M{_f(52*math.cos(a))},{_f(-80+52*math.sin(a))} L{_f(66*math.cos(a))},{_f(-80+66*math.sin(a))}" '
              f'stroke="#FFE27A" stroke-width="5" stroke-linecap="round" stroke-opacity="{b:.2f}"/>')
    return s


def cafe(t):
    s = f'<path d="M-32,-70 L32,-70 L26,-8 Q0,2 -26,-8 Z" fill="#FFFFFF" stroke="{L}" stroke-width="5" stroke-linejoin="round"/>'
    s += f'<path d="M32,-58 Q56,-50 30,-26" fill="none" stroke="{L}" stroke-width="6"/>'
    s += '<rect x="-26" y="-62" width="52" height="10" fill="#7A4A2A"/>'
    for i in range(3):
        fase = (t * 0.8 + i / 3) % 1.0
        x = -14 + i * 14
        y = -80 - 50 * fase
        s += (f'<path d="M{x},{_f(y)} q8,-10 0,-20 q-8,-10 0,-20" fill="none" stroke="#FFFFFF" '
              f'stroke-width="4" stroke-linecap="round" stroke-opacity="{0.8*(1-fase):.2f}"/>')
    return s


def livro(t):
    s = f'<path d="M0,-20 L-66,-34 L-66,-100 L0,-86 Z" fill="#FFFFFF" stroke="{L}" stroke-width="5" stroke-linejoin="round"/>'
    s += f'<path d="M0,-20 L66,-34 L66,-100 L0,-86 Z" fill="#FFFFFF" stroke="{L}" stroke-width="5" stroke-linejoin="round"/>'
    for k in range(3):
        y = -84 + k * 16
        s += f'<path d="M-54,{y-4} L-12,{y+5} M12,{y+5} L54,{y-4}" stroke="#9A8FA6" stroke-width="4"/>'
    s += f'<path d="M-66,-34 L-70,-28 L0,-12 L70,-28 L66,-34" fill="#3B7BE0" stroke="{L}" stroke-width="4"/>'
    return s


ADERECOS = {
    "telefone": telefone, "carrinho": carrinho, "almofada": almofada, "moeda": moeda,
    "despertador": despertador, "lampada": lampada, "cafe": cafe, "livro": livro,
}


def desenha_adereco(nome, t=0.0):
    f = ADERECOS.get(nome)
    return f(t) if f else ""
