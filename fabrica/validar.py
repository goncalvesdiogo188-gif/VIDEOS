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
from .janela import ICONES
from .personagens import ADERECOS, EXPRESSOES, POSES

ACOES = ("nenhuma", "salta", "treme", "aproxima")

DURACAO_MAX = 120.0
DURACAO_MIN = 15.0
PALAVRAS_MAX = 12

FALAS_MIN = 5
FALAS_MAX = 24
CENA_TEXTO_MAX = 40
DONO_MAX = 20
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


def _cena(c, i, e, textos):
    """Valida o campo "cena" da fala i."""
    if not isinstance(c, dict):
        e.append(f"Fala {i}: a \"cena\" tem de ser um objeto com icone, hora e texto.")
        return
    if c.get("icone") not in ICONES:
        e.append(f"Fala {i}: o icone da cena {c.get('icone')!r} não existe. Usa um destes: {', '.join(ICONES)}.")
    t = c.get("texto")
    if not isinstance(t, str) or not t.strip():
        e.append(f"Fala {i}: a cena precisa de um \"texto\" curto que diga o que a pessoa está a fazer.")
    else:
        if len(t) > CENA_TEXTO_MAX:
            e.append(f"Fala {i}: o texto da cena (\"{t}\") tem {len(t)} caracteres; o máximo é {CENA_TEXTO_MAX}.")
        textos.append((f"O texto da cena da fala {i}", t))
    h = c.get("hora")
    if h not in (None, "") and not (isinstance(h, str) and len(h) <= 16):
        e.append(f"Fala {i}: a hora da cena tem de ser um texto curto (ex.: \"22:40\" ou \"3 dias depois\").")


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
                if f.get("adereco") not in (None, "") and f.get("adereco") not in ADERECOS:
                    e.append(f"Fala {i}: o adereco {f.get('adereco')!r} não existe. Usa um destes ou nenhum: {', '.join(ADERECOS)}.")
                if f.get("acao") not in (None, "") and f.get("acao") not in ACOES:
                    e.append(f"Fala {i}: a acao {f.get('acao')!r} não existe. Usa uma destas: {', '.join(ACOES)}.")
                if "cena" in f and f.get("cena") is not None:
                    _cena(f["cena"], i, e, textos)
            if "dono" in guiao:  # formato com Janela do Dono: a história tem de ser contada pelas cenas
                cenas = [f for f in falas if isinstance(f, dict) and isinstance(f.get("cena"), dict)]
                if falas and isinstance(falas[0], dict) and not isinstance(falas[0].get("cena"), dict):
                    e.append("A primeira fala tem de ter uma \"cena\" (icone, hora, texto), para o espectador ver a situação logo no início.")
                if len(cenas) < 3:
                    e.append(f"Só há {len(cenas)} cenas; a história tem de ter pelo menos 3 cenas (situação, escalada, reviravolta) na Janela do Dono.")
                if not any(f.get("mostra_conceito") for f in falas if isinstance(f, dict)):
                    e.append("Nenhuma fala tem \"mostra_conceito\": true. Põe-no na fala em que o Neurão diz o nome do conceito.")
            n_conc = sum(1 for f in falas if isinstance(f, dict) and f.get("mostra_conceito"))
            if n_conc > 1:
                e.append(f"Só uma fala pode ter \"mostra_conceito\": true (tens {n_conc}).")
            quems = [f.get("quem") for f in falas if isinstance(f, dict)]
            if quems:
                for p in PERSONAGENS_VALIDOS:
                    if p not in quems:
                        e.append(f"A personagem \"{p}\" não fala em nenhuma fala. As duas personagens têm de falar.")
                if quems[-1] != "ze":
                    e.append("A última fala tem de ser do \"ze\" (o remate). Muda a última fala para o Zé Neurónio.")

    # 3b. dono e conceito (formato com Janela do Dono)
    if "dono" in guiao:
        d = g.get("dono")
        if not isinstance(d, str) or not d.strip() or len(d) > DONO_MAX:
            e.append(f"O \"dono\" tem de ser um nome próprio curto (até {DONO_MAX} caracteres), por exemplo \"Marta\".")
        c = g.get("conceito")
        if not isinstance(c, dict) or not isinstance(c.get("nome"), str) or not c.get("nome").strip():
            e.append("O \"conceito\" tem de ser um objeto com o \"nome\" do conceito (ex.: {\"id\": \"dor_de_pagar\", \"nome\": \"Dor de pagar\"}).")
        elif len(c["nome"]) > 40:
            e.append("O nome do conceito tem mais de 40 caracteres; encurta-o.")

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
