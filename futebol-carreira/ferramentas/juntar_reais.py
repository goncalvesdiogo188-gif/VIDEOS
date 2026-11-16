#!/usr/bin/env python3
"""
Junta <PAIS>.wiki.csv (quem joga onde, nomes, idades, posições, nacionalidades: autoritário) com <PAIS>.csv
(memória: geral, potencial, contrato_ate e pé, quando o nome normalizado coincide) e escreve <PAIS>.final.csv;
depois corre gerar_jogadores_reais.py --csv <PAIS>.final.csv <PAIS>.

Uso: python3 ferramentas/juntar_reais.py POR [ESP ...]   (sem argumentos: os 6 países)
     python3 ferramentas/juntar_reais.py --sem-gerar POR     (só escreve o .final.csv)

- Sem classificação na memória: estimativa com L = 30 + 0,58*reputação (nivelDaReputacao do jogo):
  11 prováveis titulares L±2, restantes L-4±3, menores de 21 anos L-8±3 (ruído determinista a partir do nome).
  Potencial: <=21 anos +6..+14; 22-25 +1..+5; senão = geral.
- Cada clube fica com 24 a 28 jogadores e >=3 GR: se faltam, completa com jogadores do CSV de memória desse
  clube que não estejam no plantel e, em último caso, com nomes inventados ("Jogador SIGLA n", no fim); se sobram,
  tira os menos classificados/mais velhos da periferia.
"""
import csv, json, os, re, subprocess, sys, unicodedata, zlib

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REAIS = os.path.join(RAIZ, "ferramentas", "reais")
DADOS = os.path.join(RAIZ, "app", "src", "main", "assets", "dados")
CABECALHO = ["equipa", "nome", "nacionalidade", "idade", "posicao", "pe", "geral", "potencial", "contrato_ate"]
GRUPO = {"GR": "G", "DD": "D", "DC": "D", "DE": "D", "MDC": "M", "MC": "M", "MOC": "M", "ED": "A", "EE": "A", "PL": "A"}
MINIMO, MAXIMO = 24, 28


def norm(nome):
    s = unicodedata.normalize("NFKD", nome)
    s = "".join(c for c in s if not unicodedata.combining(c)).lower()
    return re.sub(r"[^a-z0-9 ]", "", s.replace("-", " ")).strip()


def chave(nome): return norm(nome).replace(" ", "")


def ruido(nome, lo, hi, sal=""):
    return lo + zlib.crc32((nome + sal).encode()) % (hi - lo + 1)


def normpe(p):
    p = (p or "").lower()
    return "esquerdo" if p.startswith("e") else "ambos" if p.startswith("a") else "direito" if p.startswith("d") else ""


def contrato(c):
    return max(2027, min(2030, int(c or 2028)))


def ler(caminho):
    if not os.path.exists(caminho): return []
    with open(caminho, encoding="utf-8") as f:
        return [{k.strip(): (v or "").strip() for k, v in r.items()} for r in csv.DictReader(f, delimiter=";")]


def juntar(pais):
    equipas = json.load(open(os.path.join(DADOS, "equipas", pais + ".json"), encoding="utf-8"))
    wiki = ler(os.path.join(REAIS, pais + ".wiki.csv"))
    extras_f = os.path.join(REAIS, pais + ".wiki.json")
    extras = json.load(open(extras_f, encoding="utf-8")) if os.path.exists(extras_f) else [{}] * len(wiki)
    memoria = ler(os.path.join(REAIS, pais + ".csv"))
    por_club_mem, por_nome_mem = {}, {}
    for m in memoria:
        por_club_mem.setdefault(m["equipa"], []).append(m)
        por_nome_mem.setdefault(chave(m["nome"]), []).append(m)
    por_club_wiki = {}
    for w, x in zip(wiki, extras):
        w = dict(w); w["_detalhe"] = x.get("detalhe", True); w["_ordem"] = len(por_club_wiki.get(w["equipa"], []))
        por_club_wiki.setdefault(w["equipa"], []).append(w)
    final, estat = [], dict(wiki=0, memoria=0, estimados=0, de_memoria_extra=0, inventados=0, cortados=0)
    problemas = []
    for e in equipas:
        cid = e["id"]
        L = 30 + 0.58 * e["reputacao"]
        plantel = por_club_wiki.get(cid, [])
        mem_clube = por_club_mem.get(cid, [])
        usados = set()  # ids (id()) de linhas de memória já ligadas
        jogadores = []
        for w in plantel:
            k = chave(w["nome"])
            m = next((x for x in mem_clube if chave(x["nome"]) == k and id(x) not in usados), None)
            if not m:
                ult = norm(w["nome"]).split(" ")[-1]
                cand = [x for x in mem_clube if norm(x["nome"]).split(" ")[-1] == ult and id(x) not in usados]
                if len(cand) == 1 and GRUPO.get(cand[0]["posicao"]) == GRUPO.get(w["posicao"]): m = cand[0]
            if not m:
                cand = [x for x in por_nome_mem.get(k, []) if id(x) not in usados]
                if cand: m = cand[0]
            j = {"equipa": cid, "nome": w["nome"], "nacionalidade": w["nacionalidade"], "idade": int(w["idade"]),
                 "posicao": w["posicao"], "pe": w["pe"], "geral": None, "potencial": None, "contrato_ate": 2028,
                 "ordem": w["_ordem"], "origem": "wiki"}
            if m:
                usados.add(id(m))
                j["geral"], j["potencial"] = int(m["geral"]), max(int(m["geral"]), int(m["potencial"]))
                j["contrato_ate"] = contrato(m["contrato_ate"])
                if normpe(m["pe"]): j["pe"] = normpe(m["pe"])
                if not w["_detalhe"] and GRUPO.get(m["posicao"]) == GRUPO.get(w["posicao"]): j["posicao"] = m["posicao"]
                estat["memoria"] += 1
            jogadores.append(j)
        estat["wiki"] += len(plantel)
        # completar com a memória: primeiro guarda-redes, depois os melhores
        nomes = {chave(j["nome"]) for j in jogadores}
        livres = sorted((x for x in mem_clube if id(x) not in usados and chave(x["nome"]) not in nomes),
                        key=lambda x: -int(x["geral"]))

        def puxar(pred):
            for x in livres:
                if pred(x):
                    livres.remove(x)
                    jogadores.append({"equipa": cid, "nome": x["nome"], "nacionalidade": x["nacionalidade"], "idade": int(x["idade"]),
                                      "posicao": x["posicao"], "pe": normpe(x["pe"]) or "direito", "geral": int(x["geral"]),
                                      "potencial": max(int(x["geral"]), int(x["potencial"])), "contrato_ate": contrato(x["contrato_ate"]),
                                      "ordem": 900 + len(jogadores), "origem": "memoria"})
                    estat["de_memoria_extra"] += 1
                    return True
            return False
        gr = lambda: sum(1 for j in jogadores if j["posicao"] == "GR")
        while gr() < 3 and puxar(lambda x: x["posicao"] == "GR"): pass
        while len(jogadores) < MINIMO and puxar(lambda x: True): pass
        n_inv = 0
        ciclo = ["DC", "MC", "PL", "DD", "DE", "MDC", "EE", "ED"]
        while gr() < 3 or len(jogadores) < MINIMO:
            n_inv += 1
            pos = "GR" if gr() < 3 else ciclo[n_inv % len(ciclo)]
            nome = f"Jogador {e['abreviatura']} {n_inv}"
            jogadores.append({"equipa": cid, "nome": nome, "nacionalidade": pais if pais != "ALE" else "ALE", "idade": 19 + n_inv % 3,
                              "posicao": pos, "pe": "direito", "geral": None, "potencial": None, "contrato_ate": 2028,
                              "ordem": 990 + n_inv, "origem": "inventado"})
            estat["inventados"] += 1
        if n_inv: problemas.append(f"{cid} {e['nome']}: {len(plantel)} da Wikipédia, {n_inv} inventados")
        # estimativas para quem não tem classificação
        sem = [j for j in jogadores if j["geral"] is None]
        com = [j for j in jogadores if j["geral"] is not None]
        precisa = max(0, 11 - sum(1 for j in com if j["geral"] >= L - 4))
        tem_gr = any(j["posicao"] == "GR" and j["geral"] >= L - 4 for j in com)

        def prioridade(j):
            return (0 if 23 <= j["idade"] <= 31 else 1, j["ordem"])
        candidatos = []
        gr_visto = tem_gr
        for j in sorted(sem, key=prioridade):
            if j["posicao"] == "GR":
                if gr_visto: continue
                gr_visto = True
            candidatos.append(j)
        titulares = {id(j) for j in candidatos[:precisa]}
        for j in sem:
            if id(j) in titulares: g = L + ruido(j["nome"], -2, 2, "t")
            elif j["idade"] < 21: g = L - 8 + ruido(j["nome"], -3, 3, "j")
            else: g = L - 4 + ruido(j["nome"], -3, 3, "r")
            g = max(25, min(90, int(round(g))))
            if j["idade"] <= 21: p = g + ruido(j["nome"], 6, 14, "p")
            elif j["idade"] <= 25: p = g + ruido(j["nome"], 1, 5, "p")
            else: p = g
            j["geral"], j["potencial"] = g, min(99, p)
            estat["estimados"] += 1
        # cortar excedentes
        while len(jogadores) > MAXIMO:
            ngr = sum(1 for j in jogadores if j["posicao"] == "GR")
            c = [j for j in jogadores if not (j["posicao"] == "GR" and ngr <= 3)]
            c.sort(key=lambda j: (j["geral"] + (3 if j["idade"] <= 21 else 0) - (2 if j["idade"] >= 34 else 0), -j["ordem"]))
            jogadores.remove(c[0]); estat["cortados"] += 1
        jogadores.sort(key=lambda j: j["ordem"])
        final += jogadores
    saida = os.path.join(REAIS, pais + ".final.csv")
    with open(saida, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter=";", lineterminator="\n")
        w.writerow(CABECALHO)
        for j in final: w.writerow([j[c] for c in CABECALHO])
    print(f"{pais}: {len(final)} jogadores | Wikipédia {estat['wiki']}, com classificação da memória {estat['memoria']}, "
          f"estimados {estat['estimados']}, cortados {estat['cortados']}, acrescentados da memória {estat['de_memoria_extra']}, inventados {estat['inventados']}")
    for p in problemas: print("  ", p)
    return saida


def main():
    args = sys.argv[1:]
    gerar = "--sem-gerar" not in args
    args = [a for a in args if a != "--sem-gerar"] or ["POR", "ESP", "ING", "ITA", "ALE", "FRA"]
    ok = True
    for p in args:
        saida = juntar(p)
        if gerar:
            r = subprocess.run([sys.executable, os.path.join(RAIZ, "ferramentas", "gerar_jogadores_reais.py"), "--csv", saida, p])
            ok = ok and r.returncode == 0
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
