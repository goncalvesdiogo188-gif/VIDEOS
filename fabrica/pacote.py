"""A linha de montagem da FÁBRICA: do tema ao pacote pronto a publicar.

Um pacote é uma pasta  out/AAAA-MM-DD_tema/  com tudo o que precisas:
guiao.json, video.mp4, capa.png, carrossel/, legenda.txt e info.txt.
"""
import datetime
import json
import re
import unicodedata
from pathlib import Path

from . import config

from . import guiao as G


class PacoteErro(Exception):
    """Não foi possível criar o pacote (mensagem em português simples)."""

    def __init__(self, mensagem, guiao=None):
        super().__init__(mensagem)
        self.guiao = guiao


# ---------------------------------------------------------------- nomes de pastas
def slug(texto: str) -> str:
    """'Compras por impulso!' -> 'compras-por-impulso' (sem acentos, máx. 40 letras)."""
    t = unicodedata.normalize("NFKD", texto or "")
    t = t.encode("ascii", "ignore").decode("ascii").lower()
    t = re.sub(r"[^a-z0-9]+", "-", t).strip("-")
    t = t[:40].strip("-")
    return t or "video"


def pasta_pacote(tema: str, data=None, base=None) -> Path:
    """Devolve out/AAAA-MM-DD_slug; se já existir acrescenta _2, _3..."""
    data = data or datetime.date.today()
    base = Path(base) if base else config.pasta_saida()
    nome = f"{data.isoformat()}_{slug(tema)}"
    pasta, n = base / nome, 2
    while pasta.exists():
        pasta = base / f"{nome}_{n}"
        n += 1
    return pasta


# ---------------------------------------------------------------- histórico
def _ficheiro_historico(base) -> Path:
    return Path(base) / "historico.json"


def ler_historico(base) -> list:
    f = _ficheiro_historico(base)
    if not f.exists():
        return []
    try:
        dados = json.loads(f.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return []
    return dados if isinstance(dados, list) else []


def registar_historico(base, entrada: dict) -> None:
    historico = ler_historico(base)
    historico.append(entrada)
    f = _ficheiro_historico(base)
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(json.dumps(historico, ensure_ascii=False, indent=2), encoding="utf-8")


# ---------------------------------------------------------------- guião válido
def obter_guiao_valido(tema=None, guiao=None, max_tentativas=3, log=print):
    """Escreve (ou recebe) um guião e garante que passa na validação.

    Devolve (guião, lista_de_correções_feitas).
    """
    from . import guionista, validar

    if guiao is None:
        if not tema:
            raise PacoteErro("Preciso de um tema ou de um guião.")
        import inspect
        if "log" in inspect.signature(guionista.escrever_guiao).parameters:
            guiao = guionista.escrever_guiao(tema, log=log)
        else:
            guiao = guionista.escrever_guiao(tema)

    correcoes = []
    g = guiao
    erros = []

    def corrigir(g_, erros_):
        try:
            return guionista.corrigir_guiao(g_, erros_)
        except guionista.GuionistaErro as e:
            raise PacoteErro(
                "O guião tem erros e não consegui corrigi-lo (" + str(e) + "):\n- "
                + "\n- ".join(erros_), g_) from e

    for tentativa in range(1, max_tentativas + 1):
        erros = validar.validar(g)
        if erros:
            log(f"   O guião tem {len(erros)} problema(s) (tentativa {tentativa}/{max_tentativas}):")
            for e in erros:
                log(f"     - {e}")
            correcoes.append("Correção: " + "; ".join(erros))
            if tentativa == max_tentativas:
                break
            g = corrigir(g, erros)
            continue

        # Guião válido: pedir uma revisão final do texto (português, naturalidade).
        try:
            g2, mudancas = guionista.rever_texto(g)
        except guionista.GuionistaErro as e:
            log(f"   Não foi possível rever o texto ({e}); fica como está.")
            return g, correcoes
        if not mudancas:
            return g, correcoes
        log("   Revisão do texto:")
        for m in mudancas:
            log(f"     - {m}")
        correcoes.append("Revisão: " + "; ".join(mudancas))
        ultimo_valido, g = g, g2
        for _ in range(max_tentativas - tentativa + 1):
            erros2 = validar.validar(g)
            if not erros2:
                return g, correcoes
            log("   A revisão estragou o guião; a tentar arranjar: " + "; ".join(erros2))
            try:
                g = guionista.corrigir_guiao(g, erros2)
            except guionista.GuionistaErro:
                break
        log("   Não consegui aproveitar a revisão; uso o guião anterior.")
        correcoes.append("Revisão descartada (ficou inválida)")
        return ultimo_valido, correcoes

    raise PacoteErro(
        f"O guião continua inválido depois de {max_tentativas} tentativas:\n- "
        + "\n- ".join(erros), g)


# ---------------------------------------------------------------- pacote
def _normalizar(g: dict) -> dict:
    """Preenche os campos opcionais, como o guiao.carregar."""
    g.setdefault("cenario", "sala")
    for fala in g["falas"]:
        fala.setdefault("expressao", "neutro")
        fala.setdefault("pose", "normal")
    return g


def _escrever_json(caminho, dados):
    Path(caminho).write_text(json.dumps(dados, ensure_ascii=False, indent=2), encoding="utf-8")


def criar_pacote(tema=None, guiao_ficheiro=None, base=None, data=None, log=print) -> Path:
    from . import video, carrossel
    from .render import svg_para_png

    base = Path(base) if base else config.pasta_saida()
    agora = datetime.datetime.now()
    guiao = None
    if guiao_ficheiro:
        try:
            guiao = G.carregar(guiao_ficheiro)
        except (OSError, ValueError) as e:
            raise PacoteErro(f"Não consegui ler o guião '{guiao_ficheiro}': {e}") from e
        tema = tema or guiao.get("tema")
    if not tema:
        raise PacoteErro("Preciso de um tema (ex.: fabrica novo \"compras por impulso\").")

    log(f"1/5 A escrever o guião sobre '{tema}'..." if guiao is None
        else f"1/5 A verificar o guião '{guiao_ficheiro}'...")
    try:
        g, correcoes = obter_guiao_valido(tema=tema, guiao=guiao, log=log)
    except PacoteErro as e:
        if e.guiao:
            pasta = pasta_pacote(tema, data, base)
            pasta.mkdir(parents=True, exist_ok=True)
            _escrever_json(pasta / "guiao_rejeitado.json", e.guiao)
            log(f"   O guião rejeitado ficou guardado em {pasta / 'guiao_rejeitado.json'}")
        raise
    g = _normalizar(g)

    pasta = pasta_pacote(tema, data, base)
    pasta.mkdir(parents=True, exist_ok=True)
    _escrever_json(pasta / "guiao.json", g)
    return _produzir(g, pasta, tema, correcoes, agora, base, log)


def _produzir(g, pasta, tema, correcoes, agora, base, log, registar=True) -> Path:
    """Gera vídeo, capa, carrossel, legenda e info.txt dentro da pasta."""
    from . import video, carrossel
    from .render import svg_para_png


    log("2/5 A gravar as vozes...")
    from . import voz
    try:
        plano, duracao, wav, tipo_voz = video.preparar_audio(g, pasta / "voz.wav", log=log)
    except voz.VozErro as e:
        raise PacoteErro(str(e), g)
    log(f"   A criar o vídeo ({duracao:.1f} segundos; demora um pouco)...")
    video.gerar_video(g, pasta / "video.mp4", plano=plano, duracao=duracao, audio=wav)
    Path(wav).unlink(missing_ok=True)

    log("3/5 A criar a capa...")
    i = 1 if len(plano) > 1 else 0
    t = (plano[i]["inicio"] + plano[i]["fim"]) / 2
    (pasta / "capa.png").write_bytes(svg_para_png(video.frame_svg(g, plano, duracao, t)))

    log("4/5 A criar o carrossel...")
    slides = carrossel.gerar_carrossel(g, pasta / "carrossel")

    log("5/5 A escrever a legenda e as informações...")
    hashtags = " ".join(g.get("hashtags", []))
    (pasta / "legenda.txt").write_text(f"{g.get('legenda', '')}\n\n{hashtags}\n", encoding="utf-8")
    tentativas = 1 + sum(1 for c in correcoes if c.startswith("Correção"))
    linhas_corr = [f"  - {c}" for c in correcoes] or ["  (nenhuma)"]
    info = [
        f"Tema: {tema}",
        f"Título: {g['titulo']}",
        f"Duração do vídeo: {duracao:.1f} segundos",
        f"Voz: {tipo_voz}",
        f"Número de falas: {len(g['falas'])}",
        *_linhas_historia(g),
        f"Slides do carrossel: {len(slides)}",
        f"Tentativas até o guião ficar válido: {tentativas}",
        "Correções feitas:", *linhas_corr,
        f"Criado em: {agora.strftime('%d/%m/%Y %H:%M')}",
    ]
    (pasta / "info.txt").write_text("\n".join(info) + "\n", encoding="utf-8")

    escrever_manifesto(g, pasta, tema, duracao, agora, slides)
    log("   A juntar o pacote.zip (para abrir no telemóvel)...")
    juntar_zip(pasta)

    if registar:
        entrada = {"data": agora.strftime("%Y-%m-%d %H:%M"), "tema": tema,
                   "titulo": g["titulo"], "pasta": str(pasta)}
        if isinstance(g.get("conceito"), dict) and g["conceito"].get("id"):
            entrada["conceito"] = g["conceito"]["id"]
        registar_historico(base, entrada)
    log(f"Pacote criado em: {pasta}")
    return pasta


MANIFESTO = "pacote.json"
ZIP = "pacote.zip"


def escrever_manifesto(g, pasta, tema, duracao, agora, slides) -> dict:
    """Escreve pacote.json: tudo o que a aplicação do telemóvel precisa de saber do pacote."""
    pasta = Path(pasta)
    hashtags = [str(h) for h in g.get("hashtags", [])]
    legenda = str(g.get("legenda", ""))
    m = {
        "versao": 1,
        "titulo": g.get("titulo", ""),
        "tema": tema,
        "dono": g.get("dono", ""),
        "conceito": g["conceito"].get("nome", "") if isinstance(g.get("conceito"), dict) else "",
        "cenario": g.get("cenario", ""),
        "data": agora.strftime("%Y-%m-%d"),
        "hora": agora.strftime("%H:%M"),
        "duracao": round(float(duracao), 1),
        "video": "video.mp4",
        "capa": "capa.png",
        "carrossel": [f"carrossel/{Path(x).name}" for x in slides],
        "legenda": legenda,
        "hashtags": hashtags,
        "texto_para_publicar": f"{legenda}\n\n{' '.join(hashtags)}".strip(),
    }
    _escrever_json(pasta / MANIFESTO, m)
    return m


def juntar_zip(pasta) -> Path:
    """Junta o pacote num único pacote.zip (um só ficheiro abre-se bem no Google Drive do telemóvel)."""
    import zipfile
    pasta = Path(pasta)
    destino = pasta / ZIP
    destino.unlink(missing_ok=True)
    ficheiros = [pasta / MANIFESTO, pasta / "video.mp4", pasta / "capa.png", pasta / "legenda.txt"]
    ficheiros += sorted((pasta / "carrossel").glob("*.png"))
    with zipfile.ZipFile(destino, "w", zipfile.ZIP_STORED) as z:  # vídeo e PNG já vão comprimidos
        for f in ficheiros:
            if f.exists():
                z.write(f, f.relative_to(pasta).as_posix())
    return destino


def mostrar_guiao(g) -> str:
    """O guião em texto simples, para ler e julgar sem montar o vídeo."""
    nomes = {"neurao": "NEURÃO", "ze": "ZÉ"}
    out = [f"TÍTULO: {g.get('titulo', '')}"]
    if g.get("dono"):
        out.append(f"DONO: {g['dono']}   |   ZONA: {g.get('cenario', '')}")
    if isinstance(g.get("conceito"), dict):
        out.append(f"CONCEITO: {g['conceito'].get('nome', '')}")
    q = g.get("qualidade")
    if isinstance(q, dict) and q.get("notas"):
        out.append("NOTA DO EDITOR: média " + str(q.get("media")) + "  (" + ", ".join(f"{k} {v}" for k, v in q["notas"].items()) + ")")
    out.append("")
    for f in g.get("falas", []):
        c = f.get("cena")
        if isinstance(c, dict):
            out.append(f"   [JANELA · {c.get('hora', '')} · {c.get('icone', '')}] {c.get('texto', '')}")
        extra = " ".join(x for x in (f.get("adereco"), f.get("acao")) if x)
        marca = "  ★ CONCEITO" if f.get("mostra_conceito") else ""
        out.append(f"{nomes.get(f.get('quem'), f.get('quem', '?')):<7} {f.get('texto', '')}" + (f"   ({extra})" if extra else "") + marca)
    palavras = sum(len(str(f.get("texto", "")).split()) for f in g.get("falas", []))
    try:
        from . import video
        dur = f"{video.planear(g)[1]:.0f} s"
    except Exception:
        dur = "?"
    out += ["", f"{len(g.get('falas', []))} falas, {palavras} palavras, duração prevista {dur}.", "", "LEGENDA:", g.get("legenda", ""),
            " ".join(g.get("hashtags", []))]
    return "\n".join(out)


def escrever_so_guiao(tema, pasta=Path("guioes"), data=None, log=print) -> Path:
    """Escreve e valida o guião (sem vídeo) e grava-o em guioes/AAAA-MM-DD_tema.json."""
    if not tema:
        raise PacoteErro("Preciso de um tema (ex.: fabrica guiao \"compras por impulso\").")
    log(f"A escrever o guião sobre '{tema}' (só texto, sem vídeo)...")
    g, correcoes = obter_guiao_valido(tema=tema, log=log)
    g = _normalizar(g)
    pasta = Path(pasta)
    pasta.mkdir(parents=True, exist_ok=True)
    data = data or datetime.date.today()
    destino, k = pasta / f"{data.isoformat()}_{slug(tema)}.json", 2
    while destino.exists():
        destino = pasta / f"{data.isoformat()}_{slug(tema)}_{k}.json"
        k += 1
    _escrever_json(destino, g)
    log("")
    log(mostrar_guiao(g))
    log("")
    log(f"Guião gravado em {destino}")
    return destino


def _linhas_historia(g) -> list:
    """Linhas do info.txt sobre a história (dono, conceito e notas do editor), se existirem."""
    out = []
    if g.get("dono"):
        out.append(f"Dono: {g['dono']}")
    if isinstance(g.get("conceito"), dict) and g["conceito"].get("nome"):
        out.append(f"Conceito: {g['conceito']['nome']}")
    q = g.get("qualidade")
    if isinstance(q, dict) and q.get("notas"):
        notas = ", ".join(f"{k} {v}" for k, v in q["notas"].items())
        out.append(f"Nota do editor: média {q.get('media')} ({notas})")
    return out


def historico_para_evitar(base) -> list:
    """Temas já feitos e "conceito:ID" dos conceitos já usados (para não repetir)."""
    out = []
    for h in ler_historico(base):
        out.append(h.get("tema", ""))
        if h.get("conceito"):
            out.append("conceito:" + h["conceito"])
    return out


def refazer_pacote(pasta, log=print) -> Path:
    """Volta a gerar um pacote a partir do guiao.json da pasta (ex.: depois de o editares à mão)."""
    from . import validar
    pasta = Path(pasta)
    f = pasta / "guiao.json"
    if not f.exists():
        raise PacoteErro(f"Não encontrei {f}.")
    try:
        g = _normalizar(G.carregar(f))
    except (OSError, ValueError) as e:
        raise PacoteErro(f"Não consegui ler {f}: {e}") from e
    # a duração é só uma estimativa; com a voz real a fábrica garante os 30 s
    erros = [e for e in validar.validar(g) if "duração prevista" not in e]
    if erros:
        raise PacoteErro("O guião tem problemas:\n" + "\n".join(f"  - {e}" for e in erros))
    log(f"1/5 A refazer o pacote {pasta}...")
    return _produzir(g, pasta, g.get("tema", pasta.name), ["(pacote refeito a partir do guiao.json)"],
                     datetime.datetime.now(), pasta.parent, log, registar=False)


def semana(n=7, base=None, log=print) -> list:
    """Cria n pacotes de uma vez, com temas escolhidos pelo Claude."""
    from . import guionista

    base = Path(base) if base else config.pasta_saida()
    evitar = historico_para_evitar(base)
    log(f"A escolher {n} temas...")
    temas = guionista.escolher_temas(n, evitar=evitar)
    resultados, pastas = [], []
    for k, tema in enumerate(temas, start=1):
        log(f"\n===== Vídeo {k}/{len(temas)}: {tema} =====")
        try:
            p = criar_pacote(tema, base=base, log=log)
            pastas.append(p)
            resultados.append((tema, str(p)))
        except Exception as e:  # um tema falhar não pára os outros
            log(f"   Falhou: {e}")
            resultados.append((tema, "ERRO: " + str(e).splitlines()[0]))
    log("\n===== Resumo =====")
    for tema, res in resultados:
        log(f"{tema:<40} -> {res}")
    log(f"{len(pastas)} de {len(temas)} pacotes criados.")
    return pastas
