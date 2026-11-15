"""Ler guiões (ficheiros JSON em guioes/) e calcular os tempos de cada fala."""
import json
import re
from pathlib import Path

VOGAIS = "aeiouáàâãéêíóôõúü"


def carregar(caminho) -> dict:
    with open(caminho, encoding="utf-8") as f:
        g = json.load(f)
    for campo in ("titulo", "falas"):
        if campo not in g:
            raise ValueError(f"O guião {caminho} não tem o campo '{campo}'.")
    g.setdefault("tema", Path(caminho).stem)
    g.setdefault("cenario", "sala")
    for fala in g["falas"]:
        fala.setdefault("expressao", "neutro")
        fala.setdefault("pose", "normal")
    return g


def palavras(texto: str):
    return texto.split()


def silabas(palavra: str) -> int:
    """Contagem aproximada de sílabas (grupos de vogais). Números contam como 3."""
    p = palavra.lower()
    if re.fullmatch(r"\W*\d+\W*", p):
        return 3
    return max(1, len(re.findall(f"[{VOGAIS}]+", p)))
