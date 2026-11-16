#!/usr/bin/env python3
"""
Plantéis reais a partir da Wikipédia inglesa + Wikidata -> ferramentas/reais/<PAIS>.wiki.csv

Uso:
  python3 ferramentas/plantel_wikipedia.py paginas        # verifica paginas.json (página existe? tem plantel?)
  python3 ferramentas/plantel_wikipedia.py POR [ESP ...]  # gera <PAIS>.wiki.csv (+ <PAIS>.wiki.json com extras)
  python3 ferramentas/plantel_wikipedia.py todos

- ferramentas/reais/paginas.json: {id_do_clube: "Título do artigo"} (verificado à mão).
- Cabeçalho do CSV igual ao dos outros: equipa;nome;nacionalidade;idade;posicao;pe;geral;potencial;contrato_ate
  (geral/potencial vazios: preenchidos por juntar_reais.py). Idade a 1 de julho de 2026.
- <PAIS>.wiki.json: extras por jogador (número, título do artigo, se a posição é detalhada, se tem data de nascimento).
- Tudo o que vem da rede fica em ferramentas/reais/cache/ (ignorado pelo git): volta a correr sem repetir pedidos.
Usa só a biblioteca padrão. User-Agent obrigatório (senão 429).
"""
import csv, datetime, hashlib, json, os, re, sys, time, urllib.parse, urllib.request, urllib.error

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REAIS = os.path.join(RAIZ, "ferramentas", "reais")
CACHE = os.path.join(REAIS, "cache")
DADOS = os.path.join(RAIZ, "app", "src", "main", "assets", "dados")
UA = "FutebolCarreiraBot/0.1 (jogo pessoal)"
WP = "https://en.wikipedia.org/w/api.php"
WD = "https://www.wikidata.org/w/api.php"
REFERENCIA = datetime.date(2026, 7, 1)
CABECALHO = ["equipa", "nome", "nacionalidade", "idade", "posicao", "pe", "geral", "potencial", "contrato_ate"]

# ---------------------------------------------------------------- rede + cache
_ultimo = [0.0]


def _guardar(caminho, obj):
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    with open(caminho, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False)


def pedir(base, params):
    """GET com cache em disco (chave = pedido completo), pausa de 0,5 s e repetição com espera crescente."""
    params = dict(params, format="json", formatversion="2")
    url = base + "?" + urllib.parse.urlencode(sorted(params.items()))
    caminho = os.path.join(CACHE, "http", hashlib.sha1(url.encode()).hexdigest() + ".json")
    if os.path.exists(caminho):
        return json.load(open(caminho, encoding="utf-8"))
    for tentativa in range(8):
        espera = 0.5 - (time.time() - _ultimo[0])
        if espera > 0: time.sleep(espera)
        _ultimo[0] = time.time()
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Encoding": "identity"})
            with urllib.request.urlopen(req, timeout=60) as r:
                dados = json.loads(r.read().decode("utf-8"))
            _ultimo[0] = time.time()
            _guardar(caminho, dados)
            return dados
        except urllib.error.HTTPError as e:
            if e.code == 429 or e.code >= 500:
                ra = e.headers.get("Retry-After")
                time.sleep(int(ra) if ra and ra.isdigit() else 5 * 2 ** tentativa)
            else:
                raise
        except (urllib.error.URLError, TimeoutError, ConnectionError, json.JSONDecodeError):
            time.sleep(5 * 2 ** tentativa)
    raise RuntimeError("falhou: " + url[:200])


def lotes(seq, n):
    seq = list(seq)
    for i in range(0, len(seq), n): yield seq[i:i + n]


def resolver(titulo, norm, redirs):
    for _ in range(4):
        if titulo in norm: titulo = norm[titulo]
        elif titulo in redirs: titulo = redirs[titulo]
        else: break
    return titulo


def _mapas(q):
    norm = {n["from"]: n["to"] for n in q.get("normalized", [])}
    redirs = {n["from"]: n["to"] for n in q.get("redirects", [])}
    return norm, redirs


def wikitexto(titulos):
    """{título pedido: wikitexto ou None} (segue redirecionamentos)."""
    res = {}
    for lote in lotes(titulos, 15):
        q = pedir(WP, {"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
                       "redirects": "1", "titles": "|".join(lote)})["query"]
        norm, redirs = _mapas(q)
        por_titulo = {}
        for p in q.get("pages", []):
            if p.get("missing") or "revisions" not in p: por_titulo[p["title"]] = None
            else: por_titulo[p["title"]] = p["revisions"][0]["slots"]["main"]["content"]
        for t in lote: res[t] = por_titulo.get(resolver(t, norm, redirs))
    return res


def qids_de_titulos(titulos):
    """{título: QID ou None}, com cache por título."""
    ficheiro = os.path.join(CACHE, "titulo_qid.json")
    mapa = json.load(open(ficheiro, encoding="utf-8")) if os.path.exists(ficheiro) else {}
    falta = sorted({t for t in titulos if t not in mapa})
    for lote in lotes(falta, 50):
        q = pedir(WP, {"action": "query", "prop": "pageprops", "ppprop": "wikibase_item|disambiguation",
                       "redirects": "1", "titles": "|".join(lote)})["query"]
        norm, redirs = _mapas(q)
        por_titulo = {}
        for p in q.get("pages", []):
            pp = p.get("pageprops") or {}
            por_titulo[p["title"]] = None if (p.get("missing") or "disambiguation" in pp) else pp.get("wikibase_item")
        for t in lote: mapa[t] = por_titulo.get(resolver(t, norm, redirs))
        _guardar(ficheiro, mapa)
    return {t: mapa[t] for t in titulos}


# ---------------------------------------------------------------- Wikidata
GRUPO = {"GR": "G", "DD": "D", "DC": "D", "DE": "D", "MDC": "M", "MC": "M", "MOC": "M", "ED": "A", "EE": "A", "PL": "A"}
GENERICO = {"GK": "GR", "DF": "DC", "MF": "MC", "FW": "PL"}


def posicao_de_rotulo(rotulo):
    """Rótulo inglês do item P413 -> (código, detalhado). 'D?'/'A?' = lateral/extremo sem lado."""
    r = rotulo.lower()
    if "goalkeeper" in r: return "GR", True
    if "left-back" in r or "left back" in r or "left wing-back" in r or "left wing back" in r: return "DE", True
    if "right-back" in r or "right back" in r or "right wing-back" in r or "right wing back" in r: return "DD", True
    if "full-back" in r or "fullback" in r or "full back" in r or "wing-back" in r or "wingback" in r: return "D?", True
    if "centre-back" in r or "center-back" in r or "centre back" in r or "center back" in r or "sweeper" in r or "libero" in r or "stopper" in r: return "DC", True
    if "defensive midfield" in r or "holding midfield" in r or "anchor" in r or "defensive mid" in r: return "MDC", True
    if "attacking midfield" in r or "second striker" in r or "trequartista" in r or "shadow striker" in r: return "MOC", True
    if "left midfield" in r or "left winger" in r or "left wing" in r: return "EE", True
    if "right midfield" in r or "right winger" in r or "right wing" in r: return "ED", True
    if "winger" in r or "wide midfield" in r or "inside forward" in r or "wide forward" in r or "outside forward" in r: return "A?", True
    if "central midfield" in r or "box-to-box" in r or "playmaker" in r or "regista" in r or "mezzala" in r: return "MC", True
    if "centre-forward" in r or "center-forward" in r or "striker" in r or "false nine" in r or "false 9" in r or "forward" in r and "inside" not in r and "wide" not in r: return "PL", True
    if "defender" in r: return "DC", False
    if "midfielder" in r: return "MC", False
    if "attacker" in r: return "PL", False
    return None, False


def rotulos(qids):
    ficheiro = os.path.join(CACHE, "rotulos.json")
    mapa = json.load(open(ficheiro, encoding="utf-8")) if os.path.exists(ficheiro) else {}
    falta = sorted({q for q in qids if q not in mapa})
    for lote in lotes(falta, 50):
        r = pedir(WD, {"action": "wbgetentities", "ids": "|".join(lote), "props": "labels", "languages": "en"})
        for q, e in (r.get("entities") or {}).items():
            mapa[q] = ((e.get("labels") or {}).get("en") or {}).get("value", "")
        _guardar(ficheiro, mapa)
    return mapa


def entidades(qids):
    """{QID: {"nasc": 'AAAA-MM-DD'|None, "pos": [QIDs], "pe": [QIDs]}} (só o necessário, em cache)."""
    ficheiro = os.path.join(CACHE, "entidades.json")
    mapa = json.load(open(ficheiro, encoding="utf-8")) if os.path.exists(ficheiro) else {}
    falta = sorted({q for q in qids if q and q not in mapa})
    for lote in lotes(falta, 50):
        r = pedir(WD, {"action": "wbgetentities", "ids": "|".join(lote), "props": "claims"})
        for q, e in (r.get("entities") or {}).items():
            cl = e.get("claims") or {}

            def valores(p, tipo):
                out = []
                for c in sorted(cl.get(p, []), key=lambda c: 0 if c.get("rank") == "preferred" else 1):
                    if c.get("rank") == "deprecated": continue
                    dv = (c.get("mainsnak") or {}).get("datavalue")
                    if not dv: continue
                    out.append(dv["value"]["id"] if tipo == "id" else dv["value"]["time"])
                return out
            nasc = None
            for t in valores("P569", "tempo"):
                m = re.match(r"\+?(\d{4})-(\d\d)-(\d\d)", t)
                if m and int(m.group(2)) > 0 and int(m.group(3)) > 0:
                    nasc = "%s-%s-%s" % m.groups(); break
            mapa[q] = {"nasc": nasc, "pos": valores("P413", "id"), "pe": valores("P552", "id")}
        _guardar(ficheiro, mapa)
    return mapa


def idade(nasc):
    y, m, d = map(int, nasc.split("-"))
    return REFERENCIA.year - y - ((REFERENCIA.month, REFERENCIA.day) < (m, d))


# ---------------------------------------------------------------- wikitexto
def modelos(texto):
    """Gera (posição, nome_do_modelo, [args]) de cada {{...}} de topo (e aninhados não são devolvidos)."""
    i, n = 0, len(texto)
    while True:
        i = texto.find("{{", i)
        if i < 0: return
        prof, j = 0, i
        while j < n:
            if texto.startswith("{{", j): prof += 1; j += 2
            elif texto.startswith("}}", j):
                prof -= 1; j += 2
                if prof == 0: break
            else: j += 1
        corpo = texto[i + 2:j - 2]
        args, atual, pb, pc = [], [], 0, 0
        k = 0
        while k < len(corpo):
            if corpo.startswith("[[", k): pb += 1; atual.append("[["); k += 2
            elif corpo.startswith("]]", k): pb -= 1; atual.append("]]"); k += 2
            elif corpo.startswith("{{", k): pc += 1; atual.append("{{"); k += 2
            elif corpo.startswith("}}", k): pc -= 1; atual.append("}}"); k += 2
            elif corpo[k] == "|" and pb == 0 and pc == 0: args.append("".join(atual)); atual = []; k += 1
            else: atual.append(corpo[k]); k += 1
        args.append("".join(atual))
        yield i, args[0].strip(), args[1:]
        i = j


RE_JOGADOR = re.compile(r"^(fs2? player|football squad2? player|fs player2|fb squad player)\b", re.I)
RE_EXCLUI = re.compile(r"loan|reserve|academy|youth|\bb team\b|\bb-team\b|under[- ]?\d|\bu-?\d\d\b|women|former|retired|notable|honou?rs|staff|history|coach|record|captain|\bout\b|released|departed|transfer|international|manager|sponsor|kit|stadium|rivalr|supporter", re.I)
RE_INCLUI = re.compile(r"squad|players|\bteam\b|roster|first[- ]team", re.I)


def linhas_de_jogadores(texto):
    """Percorre o wikitexto por secções: devolve lista de (modo, args) dos modelos de jogador.
    modo: incluir (secção de plantel), excluir (cedidos/reservas/...), outro, incluir_se_so (antes do 1.º cabeçalho)."""
    res = []
    nivel_exc = None
    cabecalhos = [(m.start(), len(m.group(1)), m.group(2)) for m in re.finditer(r"^(={2,5})\s*(.*?)\s*\1\s*$", texto, re.M)]
    limites = [(0, 0, "")] + cabecalhos
    for idx, (ini, nivel, titulo) in enumerate(limites):
        fim = limites[idx + 1][0] if idx + 1 < len(limites) else len(texto)
        exc = RE_EXCLUI.search(re.sub(r"under contract|and staff|& staff", "", titulo, flags=re.I))
        if idx == 0: m = "incluir_se_so"
        elif exc: m = "excluir"; nivel_exc = nivel
        elif RE_INCLUI.search(titulo): m = "incluir"; nivel_exc = None
        elif nivel_exc is not None and nivel > nivel_exc: m = "excluir"
        else: m = "outro"; nivel_exc = None
        for _, nome, args in modelos(texto[ini:fim]):
            if RE_JOGADOR.match(nome): res.append((m, args))
    return res


def campo(args, chave, ordem=None):
    for a in args:
        if "=" in a:
            k, v = a.split("=", 1)
            if k.strip().lower() == chave: return v.strip()
    if ordem is not None:
        soltos = [a for a in args if "=" not in a.split("[[")[0].split("{{")[0]]
        if ordem < len(soltos): return soltos[ordem].strip()
    return ""


def limpar_nome(txt):
    txt = re.sub(r"<ref.*?(</ref>|/>)", "", txt, flags=re.S)
    txt = re.sub(r"<[^>]+>", "", txt)
    m = re.search(r"\[\[([^\]|]*)(?:\|([^\]]*))?\]\]", txt)
    titulo = mostrado = None
    if m:
        titulo = m.group(1).split("#")[0].strip().lstrip(":").replace("_", " ")
        mostrado = (m.group(2) or m.group(1)).strip()
    else:
        mostrado = txt
    mostrado = re.sub(r"\{\{[^}]*\}\}", "", mostrado)
    mostrado = re.sub(r"\s*\(.*?\)\s*$", "", mostrado).replace("''", "").replace(";", ",").strip()
    return titulo or None, mostrado


def extrair_plantel(texto):
    """Lista de dicts {numero, nat, pos, nome, titulo} do plantel principal."""
    todos = linhas_de_jogadores(texto)
    usar = [a for m, a in todos if m == "incluir"]
    if not usar:
        usar = [a for m, a in todos if m in ("incluir_se_so", "outro")]
    res, vistos = [], set()
    for a in usar:
        titulo, nome = limpar_nome(campo(a, "name", 3))
        if not nome: continue
        chave = titulo or nome
        if chave in vistos: continue
        vistos.add(chave)
        nat = re.findall(r"\b[A-Za-z]{3}\b", re.sub(r"\{\{|\}\}|\[\[|\]\]", " ", campo(a, "nat", 1)))
        res.append({"numero": re.sub(r"\D", "", campo(a, "no", 0)) or "", "nat": (nat[0].upper() if nat else ""),
                    "pos": campo(a, "pos", 2).upper()[:2], "nome": nome, "titulo": titulo})
    return res


def so_transclusoes(texto):
    """Nomes de modelos sem argumentos usados como plantel ({{Real Madrid CF squad}})."""
    return [nome for _, nome, args in modelos(texto) if not args and re.search(r"squad|players|plantel", nome, re.I)]


def plantel_do_clube(titulo, texto):
    """Plantel do artigo do clube; se vier pequeno, tenta modelos transcluídos e depois o artigo da época."""
    pl = extrair_plantel(texto) if texto else []
    if len(pl) >= 15: return pl
    for nome in so_transclusoes(texto or ""):
        t = nome if ":" in nome else "Template:" + nome
        sub = wikitexto([t])[t]
        if sub:
            pl = extrair_plantel(sub) or pl
            if len(pl) >= 15: return pl
    base = re.sub(r"\s*\(.*?\)$", "", titulo)
    for epoca in ("2026\u201327", "2025\u201326"):
        t = f"{epoca} {base} season"
        sub = wikitexto([t])[t]
        if sub:
            outro = extrair_plantel(sub)
            if len(outro) >= 15: return outro
    return pl


# ---------------------------------------------------------------- nacionalidades
NAC = {
    "ENG": "ING", "GER": "ALE", "SCO": "ESC", "WAL": "GAL", "NED": "NED", "SUI": "SUI", "DEN": "DIN", "SWE": "SUE",
    "CZE": "RCH", "KOR": "COR", "JPN": "JAP", "CMR": "CAM", "NGA": "NIG", "KSA": "ARS", "IRN": "IRA", "ECU": "EQU",
    "UKR": "UCR", "SRB": "SER", "BIH": "BOS", "SVN": "SLO", "ROU": "ROM", "NIR": "NIR", "GHA": "GAN", "COD": "RDC",
    "MLI": "MAL", "KVX": "KOS", "RSA": "AFS", "ZIM": "ZIM", "ZAM": "ZAM", "CGO": "CGO", "EQG": "GUE", "GAM": "GAM",
    "SLE": "SLE", "LBR": "LBR", "MTN": "MRT", "COM": "COM", "MAD": "MAD", "BDI": "BDI", "RWA": "RUA", "UGA": "UGA",
    "KEN": "QUE", "TAN": "TAN", "ETH": "ETI", "SDN": "SUD", "LBY": "LBA", "IRQ": "IRQ", "SYR": "SIR", "LBN": "LIB",
    "JOR": "JOR", "UAE": "EAU", "QAT": "CAT", "UZB": "UZB", "CHN": "CHN", "IND": "IND", "THA": "TAI", "NZL": "NZL",
    "HAI": "HAI", "CUB": "CUB", "DOM": "DOM", "HON": "HON", "PAN": "PAN", "SLV": "SAL", "GUA": "GUA", "TRI": "TRI",
    "CUW": "CUR", "SUR": "SUR", "BOL": "BOL", "BLR": "BIE", "LVA": "LET", "LTU": "LIT", "EST": "EST", "MDA": "MOL",
    "AZE": "AZE", "KAZ": "CAZ", "CYP": "CHP", "MLT": "MLT", "FRO": "FER", "BUL": "BUL", "NMI": "NMI", "GNQ": "GUE",
    "GIB": "GIB", "AND": "AND", "LIE": "LIE", "SMR": "SMR", "MNE": "MNE", "LUX": "LUX", "ISR": "ISR", "CTA": "RCA",
    "CHA": "CHA", "NIG": "NER", "NGR": "NIG", "DRC": "RDC", "GBS": "GNB", "SPA": "ESP", "CAF": "RCA", "DNK": "DIN", "SWI": "SUI", "TGO": "TOG", "NED ": "NED", "NIC": "NIC", "NCL": "NCL", "TAH": "TAH", "PHI": "FIL", "IDN": "INA", "VIE": "VIE",
    "CGO ": "CGO",
}
# códigos FIFA iguais aos do jogo (ou aceites tal como estão)
IGUAIS = "POR ESP ITA FRA BEL CRO NOR POL AUT FIN ISL IRL TUR HUN GRE BRA ARG URU COL PER PAR CHI USA CAN MEX CRC MAR EGI ALG SEN CIV AUS SVK ALB MKD".split()
NAC.update({k: k for k in IGUAIS})
NAC.update({"EGY": "EGI", "TUN": "TUN", "GEO": "GEO", "VEN": "VEN", "JAM": "JAM", "GAB": "GAB", "BFA": "BFA", "TOG": "TOG",
            "BEN": "BEN", "GUI": "GUI", "GNB": "GNB", "CPV": "CPV", "ANG": "ANG", "MOZ": "MOZ", "RUS": "RUS", "ARM": "ARM",
            })


def codigo_nac(c):
    if not c: return "POR"
    return NAC.get(c, c)


# ---------------------------------------------------------------- posições e plantel final
def construir_clube(equipa, jogadores, info_wd, rot):
    """Converte os jogadores em linhas (posição, idade, pé) e equilibra laterais/extremos."""
    linhas = []
    for j in jogadores:
        e = info_wd.get(j.get("qid")) if j.get("qid") else None
        nasc = e["nasc"] if e else None
        grupo_tpl = {"GK": "G", "DF": "D", "MF": "M", "FW": "A"}.get(j["pos"][:2], None)
        # posições do Wikidata
        det, detalhado = None, False
        if e:
            cands = [posicao_de_rotulo(rot.get(q, "")) for q in e["pos"]]
            cands = [c for c in cands if c[0]]
            esp = [c for c in cands if c[1]]
            for cod, _ in esp:
                g = {"D?": "D", "A?": "A"}.get(cod) or GRUPO[cod]
                if grupo_tpl is None or g == grupo_tpl:
                    det, detalhado = cod, True; break
        pe = "direito"
        if e and e["pe"]:
            r = rot.get(e["pe"][0], "").lower()
            pe = "ambos" if "ambi" in r or "both" in r else "esquerdo" if "left" in r else "direito"
        if det is None:
            det = {"GK": "GR", "DF": "DC", "MF": "MC", "FW": "PL"}.get(j["pos"][:2], "MC")
        linhas.append({"equipa": equipa, "nome": j["nome"], "nacionalidade": codigo_nac(j["nat"]),
                       "idade": idade(nasc) if nasc else 25, "posicao": det, "pe": pe, "numero": j["numero"],
                       "titulo": j["titulo"], "detalhe": detalhado, "nasc": bool(nasc)})
    equilibrar(linhas)
    return linhas


def _num(l):
    return int(l["numero"]) if l["numero"].isdigit() else 99


def equilibrar(linhas):
    # laterais sem lado e extremos sem lado
    for l in linhas:
        if l["posicao"] == "D?": l["posicao"] = "DE" if l["pe"] == "esquerdo" else "DD"
        if l["posicao"] == "A?": l["posicao"] = "EE" if l["pe"] == "esquerdo" else "ED"
    grosso = [l for l in linhas if not l["detalhe"]]

    def n(p): return sum(1 for l in linhas if l["posicao"] == p)

    def converter(origem, destino, minimo, pref_num, pref_pe):
        while n(destino) < minimo:
            c = [l for l in grosso if l["posicao"] == origem]
            if not c: return
            c.sort(key=lambda l: (l["pe"] != pref_pe, _num(l) not in pref_num, _num(l)))
            c[0]["posicao"] = destino
            grosso.remove(c[0])
    converter("DC", "DD", 2, (2, 12, 22), "direito")
    converter("DC", "DE", 2, (3, 13, 23), "esquerdo")
    converter("PL", "ED", 2, (7, 17, 27), "direito")
    converter("PL", "EE", 2, (11, 21, 77), "esquerdo")
    converter("MC", "MDC", 1, (4, 5, 6, 14, 16), "direito")
    converter("MC", "MOC", 1, (10, 8, 20), "direito")


def gerar_pais(pais, paginas):
    equipas = json.load(open(os.path.join(DADOS, "equipas", pais + ".json"), encoding="utf-8"))
    ids = [e["id"] for e in equipas if e["id"] in paginas]
    textos = wikitexto([paginas[i] for i in ids])
    plantis = {}
    for i in ids:
        plantis[i] = plantel_do_clube(paginas[i], textos[paginas[i]])
    titulos = sorted({j["titulo"] for p in plantis.values() for j in p if j["titulo"]})
    q = qids_de_titulos(titulos)
    for p in plantis.values():
        for j in p: j["qid"] = q.get(j["titulo"]) if j["titulo"] else None
    info = entidades({j["qid"] for p in plantis.values() for j in p if j["qid"]})
    qpos = {q for e in info.values() for q in e["pos"] + e["pe"]}
    rot = rotulos(qpos)
    saida, extras = [], []
    estat = {"clubes": 0, "jogadores": 0, "nasc": 0, "detalhe": 0, "sem_artigo": 0}
    problemas = []
    for e in equipas:
        i = e["id"]
        if i not in plantis: continue
        linhas = construir_clube(i, plantis[i], info, rot)
        estat["clubes"] += 1
        if len(linhas) < 18 or sum(1 for l in linhas if l["posicao"] == "GR") < 2:
            problemas.append(f"{i} {paginas[i]}: {len(linhas)} jogadores, {sum(1 for l in linhas if l['posicao'] == 'GR')} GR")
        for l in linhas:
            estat["jogadores"] += 1
            estat["nasc"] += l["nasc"]
            estat["detalhe"] += l["detalhe"]
            estat["sem_artigo"] += (l["titulo"] is None)
            saida.append([l["equipa"], l["nome"], l["nacionalidade"], l["idade"], l["posicao"], l["pe"], "", "", 2028])
            extras.append({k: l[k] for k in ("equipa", "nome", "numero", "titulo", "detalhe", "nasc")})
    with open(os.path.join(REAIS, pais + ".wiki.csv"), "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter=";", lineterminator="\n")
        w.writerow(CABECALHO)
        w.writerows(saida)
    with open(os.path.join(REAIS, pais + ".wiki.json"), "w", encoding="utf-8") as f:
        json.dump(extras, f, ensure_ascii=False, indent=0)
    print(f"{pais}: clubes {estat['clubes']}/{len(equipas)}, jogadores {estat['jogadores']}, com data de nascimento {estat['nasc']}, "
          f"posição detalhada {estat['detalhe']}, sem artigo {estat['sem_artigo']}")
    for p in problemas: print("  PROBLEMA", p)
    desconhecidas = sorted({r[2] for r in saida} - {n["id"] for n in json.load(open(os.path.join(DADOS, "selecoes.json"), encoding="utf-8"))["nacoes"]})
    if desconhecidas: print("  nacionalidades fora de selecoes.json:", " ".join(desconhecidas))


def verificar_paginas(paginas):
    ids = list(paginas)
    textos = wikitexto([paginas[i] for i in ids])
    for i in ids:
        t = textos[paginas[i]]
        if t is None: print(f"FALTA   {i} {paginas[i]}"); continue
        n = len(plantel_do_clube(paginas[i], t))
        if n < 15: print(f"POUCOS  {i} {paginas[i]}: {n} jogadores (transclusões: {so_transclusoes(t)})")
    print("verificação concluída")


def main():
    paginas = json.load(open(os.path.join(REAIS, "paginas.json"), encoding="utf-8"))
    args = sys.argv[1:]
    if not args: print(__doc__); return
    if args[0] == "paginas": verificar_paginas(paginas); return
    paises = ["POR", "ESP", "ING", "ITA", "ALE", "FRA"] if args[0] == "todos" else args
    for p in paises: gerar_pais(p, paginas)


if __name__ == "__main__":
    main()
