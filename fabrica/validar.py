"""Validar guiões: verificações automáticas (sem IA) antes de gastar tempo a desenhar o vídeo.

Cada erro é uma frase clara em português que diz o que está mal e como corrigir,
para poder ser devolvida a uma IA que corrige o guião.

Uso na linha de comandos:
    python -m fabrica.validar guioes/exemplo_compras_impulso.json
"""
import json
import re
import sys
import unicodedata

from . import guiao as G
from .cenarios import ALIASES, ZONAS
from .personagens import EXPRESSOES, POSES

DURACAO_MAX = 30.0
DURACAO_MIN = 15.0
PALAVRAS_MAX = 12

FALAS_MIN = 5
FALAS_MAX = 7
TITULO_MAX = 60
LEGENDA_MAX = 400
CARROSSEL_MIN = 3
CARROSSEL_MAX = 5
CARROSSEL_TEXTO_MAX = 120
N_HASHTAGS = 5
PERSONAGENS_VALIDOS = ("neurao", "ze")

# Listas fáceis de editar (a comparação ignora maiúsculas e acentos).
PROMESSAS_DINHEIRO = [
    "ficar rico", "enriquecer", "lucro garantido", "rendimento garantido",
    "dinheiro fácil", "investe em", "invista em", "compra ações",
    "criptomoeda", "bitcoin", "bolsa de valores", "renda passiva garantida",
]
CONSELHOS_MEDICOS = ["diagnóstico", "medicamento", "toma comprimidos"]
GIRIAS_PROIBIDAS = [
    "fixe", "bué", "giro", "legal", "cara", "a gente",
    "telemóvel", "celular", "ecrã", "tela",
]
CATEGORIAS_PROIBIDAS = [
    ("promessa de dinheiro ou conselho de investimento", PROMESSAS_DINHEIRO),
    ("conselho médico", CONSELHOS_MEDICOS),
    ("gíria proibida", GIRIAS_PROIBIDAS),
]

RE_HASHTAG = re.compile(r"^#[a-z0-9_]+$")
MAIUSC = "A-ZÁÀÂÃÉÊÍÓÔÕÚÇ"


def _sem_acentos(t: str) -> str:
    t = unicodedata.normalize("NFD", t.lower())
    return "".join(c for c in t if unicodedata.category(c) != "Mn")


def _palavras_simples(t: str) -> str:
    """Minúsculas, sem acentos, só palavras separadas por um espaço (com espaços nas pontas)."""
    return " " + " ".join(re.findall(r"\w+", _sem_acentos(t))) + " "


def normalizar(guiao: dict) -> dict:
    """Cópia do guião com os valores em falta preenchidos e espaços limpos.
    Não corrige valores inválidos: isso é trabalho do validar()."""
    if not isinstance(guiao, dict):
        return {}
    g = dict(guiao)
    for campo in ("titulo", "legenda", "tema"):
        if isinstance(g.get(campo), str):
            g[campo] = g[campo].strip()
    if g.get("tema") is None:
        g["tema"] = g["titulo"] if isinstance(g.get("titulo"), str) else ""
    if g.get("cenario") is None:
        g["cenario"] = "comando"
    elif isinstance(g["cenario"], str):
        g["cenario"] = g["cenario"].strip()
    if isinstance(g.get("falas"), list):
        falas = []
        for f in g["falas"]:
            if isinstance(f, dict):
                f = dict(f)
                if isinstance(f.get("texto"), str):
                    f["texto"] = f["texto"].strip()
                if f.get("expressao") is None:
                    f["expressao"] = "neutro"
                if f.get("pose") is None:
                    f["pose"] = "normal"
            falas.append(f)
        g["falas"] = falas
    if isinstance(g.get("carrossel"), list):
        car = []
        for c in g["carrossel"]:
            if isinstance(c, dict):
                c = dict(c)
                for k in ("titulo", "texto"):
                    if isinstance(c.get(k), str):
                        c[k] = c[k].strip()
            car.append(c)
        g["carrossel"] = car
    if isinstance(g.get("hashtags"), list):
        g["hashtags"] = [h.strip() if isinstance(h, str) else h for h in g["hashtags"]]
    return g


def duracao_estimada(guiao: dict) -> float:
    """Duração prevista do vídeo, em segundos (sem desenhar nada)."""
    from .video import planear
    _, dur = planear(normalizar(guiao))
    return float(dur)


def _falas_utilizaveis(g) -> bool:
    falas = g.get("falas")
    return isinstance(falas, list) and bool(falas) and all(
        isinstance(f, dict) and isinstance(f.get("texto"), str) and f["texto"].strip()
        for f in falas)


def _higiene(texto: str, onde: str, e: list, fala: bool = False):
    """Verificações básicas de escrita num texto."""
    if "  " in texto:
        e.append(f"{onde}: tem espaços duplos. Deixa só um espaço entre palavras.")
    if re.search(r"\s[,.;:!?…]", texto):
        e.append(f"{onde}: tem um espaço antes de pontuação. Tira o espaço antes de , . ; : ! ? …")
    m = re.search(r"\b(\w+)\s+\1\b", texto, re.IGNORECASE)
    if m and not m.group(1).isdigit():
        e.append(f"{onde}: a palavra \"{m.group(1)}\" aparece repetida seguida. Apaga uma das duas.")
    if re.search(rf"\b[{MAIUSC}]{{2,}}(?:\s+[{MAIUSC}]+){{3,}}", texto):
        e.append(f"{onde}: tem mais de 3 palavras seguidas em MAIÚSCULAS. Escreve em minúsculas normais.")
    if texto.count("(") != texto.count(")"):
        e.append(f"{onde}: os parênteses não estão balanceados. Fecha ou remove o parêntese em falta.")
    if (texto.count('"') % 2 or texto.count("“") != texto.count("”")
            or texto.count("«") != texto.count("»")):
        e.append(f"{onde}: as aspas não estão balanceadas. Fecha ou remove as aspas em falta.")
    if fala and texto and not re.search(r"[.!?…][\"”»)]*$", texto):
        e.append(f"{onde}: não acaba com pontuação final. Termina com . ! ? ou …")


def _proibidos(texto: str, onde: str, e: list):
    t = _palavras_simples(texto)
    for categoria, termos in CATEGORIAS_PROIBIDAS:
        for termo in termos:
            if _palavras_simples(termo) in t:
                e.append(f"{onde}: contém \"{termo}\" ({categoria}). Reescreve sem esta expressão.")


def validar(guiao: dict) -> list[str]:
    """Devolve a lista de erros ([] = guião válido). Nunca dá exceção."""
    try:
        return _validar(guiao)
    except Exception as ex:  # rede de segurança: nunca rebentar
        return [f"O guião tem uma estrutura inesperada ({type(ex).__name__}). "
                "Confirma que segue exatamente o formato do guião JSON."]


def _validar(guiao) -> list[str]:
    if not isinstance(guiao, dict):
        return ["O guião tem de ser um objeto JSON com titulo, falas, legenda, hashtags e carrossel."]
    g = normalizar(guiao)
    e: list[str] = []
    textos: list[tuple[str, str]] = []  # (onde, texto) para o conteúdo proibido

    # 1. campos obrigatórios
    for campo in ("titulo", "falas", "legenda", "hashtags", "carrossel"):
        if campo not in guiao:
            e.append(f"Falta o campo obrigatório \"{campo}\". Acrescenta-o ao guião.")
    titulo = g.get("titulo")
    if "titulo" in guiao:
        if not isinstance(titulo, str) or not titulo:
            e.append("O \"titulo\" está vazio ou não é texto. Escreve um título com até 60 caracteres.")
        else:
            if len(titulo) > TITULO_MAX:
                e.append(f"O título tem {len(titulo)} caracteres e o máximo é {TITULO_MAX}. Encurta o título.")
            textos.append(("O título", titulo))
            _higiene(titulo, "O título", e)
    legenda = g.get("legenda")
    if "legenda" in guiao:
        if not isinstance(legenda, str) or not legenda:
            e.append("A \"legenda\" está vazia ou não é texto. Escreve uma legenda com até 400 caracteres.")
        else:
            if len(legenda) > LEGENDA_MAX:
                e.append(f"A legenda tem {len(legenda)} caracteres e o máximo é {LEGENDA_MAX}. Encurta a legenda.")
            textos.append(("A legenda", legenda))
            _higiene(legenda, "A legenda", e)

    # 2 e 3. falas
    falas = g.get("falas")
    if "falas" in guiao:
        if not isinstance(falas, list):
            e.append("O campo \"falas\" tem de ser uma lista de falas.")
        else:
            if not FALAS_MIN <= len(falas) <= FALAS_MAX:
                e.append(f"O guião tem {len(falas)} falas; tem de ter entre {FALAS_MIN} e {FALAS_MAX}. "
                         + ("Acrescenta mais falas." if len(falas) < FALAS_MIN else "Corta algumas falas."))
            vistos = {}
            for i, f in enumerate(falas, 1):
                if not isinstance(f, dict):
                    e.append(f"A fala {i} não é um objeto com quem, texto, expressao e pose.")
                    continue
                quem = f.get("quem")
                if quem not in PERSONAGENS_VALIDOS:
                    e.append(f"Fala {i}: o campo \"quem\" é {quem!r}; tem de ser \"neurao\" ou \"ze\".")
                texto = f.get("texto")
                if not isinstance(texto, str) or not texto:
                    e.append(f"Fala {i}: o \"texto\" está vazio ou em falta. Escreve a frase que a personagem diz.")
                else:
                    onde = f"Fala {i} (\"{texto}\")"
                    n = len(G.palavras(texto))
                    if n > PALAVRAS_MAX:
                        e.append(f"{onde}: tem {n} palavras e o máximo é {PALAVRAS_MAX}. Encurta ou divide a fala.")
                    textos.append((onde, texto))
                    _higiene(texto, onde, e, fala=True)
                    chave = _palavras_simples(texto)
                    if chave in vistos:
                        e.append(f"{onde}: repete o texto da fala {vistos[chave]}. Escreve algo diferente.")
                    else:
                        vistos[chave] = i
                if f.get("expressao") not in EXPRESSOES:
                    e.append(f"Fala {i}: a expressão {f.get('expressao')!r} não existe. "
                             f"Usa uma destas: {', '.join(EXPRESSOES)}.")
                if f.get("pose") not in POSES:
                    e.append(f"Fala {i}: a pose {f.get('pose')!r} não existe. Usa uma destas: {', '.join(POSES)}.")
            quems = [f.get("quem") for f in falas if isinstance(f, dict)]
            if quems:
                for p in PERSONAGENS_VALIDOS:
                    if p not in quems:
                        e.append(f"A personagem \"{p}\" não fala em nenhuma fala. As duas personagens têm de falar.")
                if quems[-1] != "ze":
                    e.append("A última fala tem de ser do \"ze\" (o remate). Muda a última fala para o Zé Neurónio.")

    # 4. cenário
    cen = g.get("cenario")
    if not isinstance(cen, str) or (cen not in ZONAS and cen not in ALIASES):
        e.append(f"O cenário {cen!r} não existe. Usa um destes: {', '.join(ZONAS)}.")

    # 5. duração
    if _falas_utilizaveis(g):
        try:
            dur = duracao_estimada(g)
        except Exception:
            dur = None
        if dur is not None:
            if dur > DURACAO_MAX:
                e.append(f"A duração prevista é {dur:.1f} s e o máximo é {DURACAO_MAX:.0f} s. "
                         "Encurta ou corta falas.")
            elif dur < DURACAO_MIN:
                e.append(f"A duração prevista é {dur:.1f} s e o mínimo é {DURACAO_MIN:.0f} s. "
                         "Alonga as falas ou acrescenta uma fala.")

    # 6. hashtags
    h = g.get("hashtags")
    if "hashtags" in guiao:
        if not isinstance(h, list):
            e.append("O campo \"hashtags\" tem de ser uma lista de 5 hashtags.")
        else:
            if len(h) != N_HASHTAGS:
                e.append(f"Há {len(h)} hashtags; têm de ser exatamente {N_HASHTAGS}.")
            vis = set()
            for x in h:
                if not isinstance(x, str) or not RE_HASHTAG.match(x):
                    e.append(f"A hashtag {x!r} é inválida. Tem de começar por # e ter só letras minúsculas "
                             "sem acentos, números ou _ (sem espaços).")
                    continue
                if x.lower() in vis:
                    e.append(f"A hashtag {x} está repetida. Cada hashtag só pode aparecer uma vez.")
                vis.add(x.lower())

    # 7. carrossel
    c = g.get("carrossel")
    if "carrossel" in guiao:
        if not isinstance(c, list):
            e.append("O campo \"carrossel\" tem de ser uma lista de 3 a 5 slides.")
        else:
            if not CARROSSEL_MIN <= len(c) <= CARROSSEL_MAX:
                e.append(f"O carrossel tem {len(c)} slides; tem de ter entre {CARROSSEL_MIN} e {CARROSSEL_MAX}.")
            for i, s in enumerate(c, 1):
                if not isinstance(s, dict):
                    e.append(f"Slide {i} do carrossel: tem de ser um objeto com \"titulo\" e \"texto\".")
                    continue
                for k in ("titulo", "texto"):
                    v = s.get(k)
                    if not isinstance(v, str) or not v:
                        e.append(f"Slide {i} do carrossel: o campo \"{k}\" está vazio ou em falta. Preenche-o.")
                    else:
                        onde = f"Slide {i} do carrossel ({k})"
                        textos.append((onde, v))
                        _higiene(v, onde, e)
                t = s.get("texto")
                if isinstance(t, str) and len(t) > CARROSSEL_TEXTO_MAX:
                    e.append(f"Slide {i} do carrossel: o texto tem {len(t)} caracteres e o máximo é "
                             f"{CARROSSEL_TEXTO_MAX}. Encurta-o.")

    # 8. conteúdo proibido
    for onde, t in textos:
        _proibidos(t, onde, e)
    return e


def main(argv=None) -> int:
    argv = sys.argv[1:] if argv is None else argv
    if len(argv) != 1:
        print("Uso: python -m fabrica.validar guioes/ficheiro.json")
        return 1
    try:
        with open(argv[0], encoding="utf-8") as f:
            g = json.load(f)
    except FileNotFoundError:
        print(f"Não encontrei o ficheiro {argv[0]}.")
        return 1
    except (ValueError, OSError) as ex:
        print(f"Não consegui ler o guião {argv[0]}: {ex}")
        return 1
    erros = validar(g)
    if erros:
        print(f"O guião tem {len(erros)} erro(s):")
        for i, x in enumerate(erros, 1):
            print(f"{i}. {x}")
        return 1
    print(f"Guião válido (duração prevista {duracao_estimada(g):.1f} s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
