#!/usr/bin/env python3
"""
Gera app/src/main/assets/dados/jogadores/<PAIS>.json a partir de ferramentas/reais/<PAIS>.csv.

Cada linha do CSV (separador ";", com cabeçalho):
  equipa;nome;nacionalidade;idade;posicao;pe;geral;potencial;contrato_ate
- equipa: id do clube (ex.: POR_01), tem de existir em equipas/<PAIS>.json
- nacionalidade: código das seleções (POR, BRA, ...), ver selecoes.json
- idade: idade a 1 de julho de 2026
- posicao: GR DD DC DE MDC MC MOC ED EE PL
- pe: direito / esquerdo / ambos
- geral e potencial: 1 a 99 (potencial >= geral)
- contrato_ate: ano em que o contrato acaba (fica entre 2027 e 2031)
Cada clube fica com 28 jogadores no máximo (saem os de menor "geral").

Os atributos são gerados à volta do "geral", com os pesos da posição (iguais aos do jogo),
de forma determinista (o mesmo nome dá sempre os mesmos atributos).
Uso: python3 ferramentas/gerar_jogadores_reais.py POR [ESP ...]   (sem argumentos: todos os CSV)
"""
import csv, json, math, os, random, sys, zlib

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DADOS = os.path.join(RAIZ, "app", "src", "main", "assets", "dados")
ATRIBUTOS = ["ritmo", "remate", "passe", "drible", "defesa", "fisico", "guarda_redes"]
LATERAL = {"defesa": .35, "ritmo": .25, "passe": .15, "fisico": .15, "drible": .10}
EXTREMO = {"ritmo": .30, "drible": .30, "remate": .20, "passe": .15, "fisico": .05}
PESOS = {
    "GR": {"guarda_redes": .80, "fisico": .12, "passe": .08},
    "DC": {"defesa": .55, "fisico": .25, "ritmo": .10, "passe": .10},
    "DD": LATERAL, "DE": LATERAL,
    "MDC": {"defesa": .35, "passe": .30, "fisico": .20, "drible": .10, "ritmo": .05},
    "MC": {"passe": .40, "drible": .20, "defesa": .15, "fisico": .10, "remate": .10, "ritmo": .05},
    "MOC": {"passe": .30, "drible": .30, "remate": .20, "ritmo": .15, "fisico": .05},
    "ED": EXTREMO, "EE": EXTREMO,
    "PL": {"remate": .45, "ritmo": .20, "drible": .15, "fisico": .15, "passe": .05},
}
BASE_ID = {"POR": 100001, "ESP": 200001, "ING": 300001, "ITA": 400001, "ALE": 500001, "FRA": 600001}


def arred(x):  # como roundToInt do Kotlin
    return int(math.floor(x + 0.5))


def geral(attr, pos):
    return max(1, min(99, arred(sum(attr[a] * p for a, p in PESOS[pos].items()))))


def salario_justo(g):
    return max(12, arred(1.17 ** (g - 50) * 60.0)) * 1000


def gerar(pais):
    caminho = os.path.join(RAIZ, "ferramentas", "reais", pais + ".csv")
    equipas = {e["id"] for e in json.load(open(os.path.join(DADOS, "equipas", pais + ".json"), encoding="utf-8"))}
    nacoes = {n["id"] for n in json.load(open(os.path.join(DADOS, "selecoes.json"), encoding="utf-8"))["nacoes"]}
    erros, linhas = [], []
    with open(caminho, encoding="utf-8") as f:
        for i, r in enumerate(csv.DictReader(f, delimiter=";"), start=2):
            try:
                r = {k.strip(): (v or "").strip() for k, v in r.items()}
                pos, eq = r["posicao"], r["equipa"]
                if pos not in PESOS: raise ValueError("posição " + pos)
                if eq not in equipas: raise ValueError("clube " + eq)
                alvo, pot = int(r["geral"]), int(r["potencial"])
                rng = random.Random(zlib.crc32((r["nome"] + eq).encode()))
                pesos = PESOS[pos]
                attr = {}
                for a in ATRIBUTOS:
                    if a in pesos: v = alvo + rng.gauss(0, 4)
                    elif a == "guarda_redes": v = rng.randint(5, 15)
                    else: v = alvo - 22 + rng.gauss(0, 6)
                    attr[a] = max(1, min(99, arred(v)))
                for _ in range(3):
                    d = alvo - geral(attr, pos)
                    if d:
                        for a in pesos: attr[a] = max(1, min(99, attr[a] + d))
                g = geral(attr, pos)
                nac = r["nacionalidade"].upper()
                if nac not in nacoes: erros.append(f"linha {i}: nacionalidade {nac} não está em selecoes.json (aceite)")
                pe = r["pe"] if r["pe"] in ("direito", "esquerdo", "ambos") else "direito"
                linhas.append({
                    "id": 0, "nome": r["nome"], "nacionalidade": nac, "idade": int(r["idade"]), "posicao": pos, "pe": pe,
                    "equipa": eq, "potencial": max(g, min(99, pot)),
                    "atributos": {a: attr[a] for a in ATRIBUTOS},
                    "salario": salario_justo(g), "contrato_ate": max(2027, min(2031, int(r["contrato_ate"] or 2028))),
                    "emprestado_por": "", "academia": False,
                })
            except Exception as e:
                erros.append(f"linha {i}: ERRO {e}")
    # Cada clube fica com 28 jogadores no máximo (deixa lugar para subir jovens da academia):
    # saem os de menor "geral", mas nunca abaixo de 3 guarda-redes.
    MAXIMO = 28
    por_equipa = {}
    for j in linhas: por_equipa.setdefault(j["equipa"], []).append(j)
    for eq, js in por_equipa.items():
        while len(js) > MAXIMO:
            grs = sum(1 for j in js if j["posicao"] == "GR")
            candidatos = [j for j in js if j["posicao"] != "GR" or grs > 3]
            fora = min(candidatos, key=lambda j: geral(j["atributos"], j["posicao"]))
            js.remove(fora); linhas.remove(fora)
    # Mesma ordem que o jogo grava: por clube (ordem do ficheiro de equipas), depois pela ordem do CSV.
    ordem = [e["id"] for e in json.load(open(os.path.join(DADOS, "equipas", pais + ".json"), encoding="utf-8"))]
    linhas.sort(key=lambda j: ordem.index(j["equipa"]))
    for k, j in enumerate(linhas): j["id"] = BASE_ID[pais] + k
    texto = ",\n".join("\t" + json.dumps(j, ensure_ascii=False, separators=(",", ":")) for j in linhas)
    open(os.path.join(DADOS, "jogadores", pais + ".json"), "w", encoding="utf-8").write("[\n" + texto + "\n]\n")
    por_clube = {e: sum(1 for j in linhas if j["equipa"] == e) for e in ordem}
    print(f"{pais}: {len(linhas)} jogadores; por clube min {min(por_clube.values())} max {max(por_clube.values())}")
    for e, n in por_clube.items():
        gr = sum(1 for j in linhas if j["equipa"] == e and j["posicao"] == "GR")
        if n < 22 or gr < 3: erros.append(f"{e}: {n} jogadores, {gr} GR (mínimo 22 e 3 GR)")
    for x in erros: print("  " + x)
    return not any("ERRO" in x or "mínimo" in x for x in erros)


if __name__ == "__main__":
    paises = sys.argv[1:] or sorted(p[:-4] for p in os.listdir(os.path.join(RAIZ, "ferramentas", "reais")) if p.endswith(".csv"))
    ok = all([gerar(p) for p in paises])
    sys.exit(0 if ok else 1)
