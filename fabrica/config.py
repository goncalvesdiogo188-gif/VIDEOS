"""Definições da fábrica, guardadas em config.json (ao lado do código; não vai para o Git).

Por agora só há uma: a pasta onde os pacotes são gravados ("saida"). Para os pacotes
chegarem ao telemóvel, aponta-a para uma pasta do Google Drive no computador:

    fabrica saida "G:\\O meu disco\\FABRICA"

A variável de ambiente FABRICA_OUT tem prioridade sobre o config.json.
"""
import json
import os
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
FICHEIRO = RAIZ / "config.json"
PADRAO = RAIZ / "out"


def ler() -> dict:
    try:
        d = json.loads(FICHEIRO.read_text(encoding="utf-8"))
        return d if isinstance(d, dict) else {}
    except (OSError, ValueError):
        return {}


def gravar(dados: dict) -> None:
    FICHEIRO.write_text(json.dumps(dados, ensure_ascii=False, indent=2), encoding="utf-8")


def pasta_saida() -> Path:
    """Pasta onde os pacotes são gravados: FABRICA_OUT, ou a do config.json, ou ./out."""
    env = os.environ.get("FABRICA_OUT", "").strip()
    if env:
        return Path(env).expanduser()
    s = ler().get("saida")
    return Path(s).expanduser() if isinstance(s, str) and s.strip() else PADRAO


def definir_saida(pasta) -> Path:
    """Guarda a pasta de saída (cria-a se não existir e confirma que dá para escrever nela)."""
    p = Path(str(pasta).strip().strip('"')).expanduser()
    try:
        p.mkdir(parents=True, exist_ok=True)
        teste = p / ".fabrica_teste"
        teste.write_text("ok", encoding="utf-8")
        teste.unlink()
    except OSError as e:
        raise ValueError(f"Não consigo escrever na pasta '{p}': {e}") from e
    d = ler()
    d["saida"] = str(p)
    gravar(d)
    return p


def repor_saida() -> Path:
    d = ler()
    d.pop("saida", None)
    gravar(d)
    return PADRAO
