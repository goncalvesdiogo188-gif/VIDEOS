"""A página da FÁBRICA, só para ti (corre no teu computador).

    fabrica web                 abre em http://127.0.0.1:8765  (só neste computador)
    fabrica web --rede          também acessível no telemóvel, no mesmo Wi-Fi (com uma chave)

Usa só a biblioteca padrão do Python. Os trabalhos demorados (guiões, vídeos) correm em
segundo plano, um de cada vez, e a página mostra o progresso. Nada sai do teu computador,
a não ser os pedidos que a fábrica já fazia (Claude e vozes).
"""
import datetime
import http.cookies
import json
import mimetypes
import queue
import re
import secrets
import socket
import threading
import time
import traceback
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from . import aprendizagem, config
from . import guionista as GU

RAIZ = Path(__file__).resolve().parent.parent
PAGINA = Path(__file__).resolve().parent / "pagina" / "index.html"
GUIOES = RAIZ / "guioes"
CONCEITOS = RAIZ / "conteudo" / "conceitos.json"
PORTA = 8765
NOME_OK = re.compile(r"^[A-Za-z0-9_.\-]+$")
MAX_CORPO = 1_000_000


class WebErro(Exception):
    def __init__(self, mensagem, codigo=400):
        super().__init__(mensagem)
        self.codigo = codigo


# ================================================================== trabalhos em segundo plano
class Trabalhos:
    """Fila de trabalhos: corre um de cada vez (o Claude e o render pesam no computador)."""

    def __init__(self, executar=None):
        self.lista = []
        self.fila = queue.Queue()
        self.trava = threading.Lock()
        self.proximo = 1
        self.executar = executar or executar_trabalho
        threading.Thread(target=self._corre, daemon=True).start()

    def criar(self, tipo, titulo, args):
        with self.trava:
            t = {"id": self.proximo, "tipo": tipo, "titulo": titulo, "args": args, "estado": "na fila",
                 "log": [], "resultado": None, "erro": None, "inicio": None, "fim": None,
                 "criado": time.time()}
            self.proximo += 1
            self.lista.append(t)
            del self.lista[:-40]
        self.fila.put(t)
        return t

    def _corre(self):
        while True:
            t = self.fila.get()
            t["estado"], t["inicio"] = "a correr", time.time()

            def log(msg=""):
                with self.trava:
                    t["log"].append(str(msg))
                    del t["log"][:-400]
            try:
                t["resultado"] = self.executar(t["tipo"], t["args"], log)
                t["estado"] = "feito"
            except Exception as e:  # o trabalho nunca derruba o servidor
                t["estado"], t["erro"] = "erro", _mensagem_erro(e)
                log("ERRO: " + t["erro"])
                if not isinstance(e, (GU.GuionistaErro, WebErro)) and type(e).__name__ not in ("PacoteErro", "VozErro"):
                    log(traceback.format_exc()[-1500:])
            t["fim"] = time.time()

    def ver(self, tid=None, desde=0):
        with self.trava:
            if tid is None:
                return [self._resumo(t) for t in reversed(self.lista)]
            t = next((x for x in self.lista if x["id"] == tid), None)
            if not t:
                raise WebErro("Trabalho não encontrado.", 404)
            r = self._resumo(t)
            r["log"] = t["log"][desde:]
            r["total_log"] = len(t["log"])
            return r

    @staticmethod
    def _resumo(t):
        return {k: t[k] for k in ("id", "tipo", "titulo", "estado", "resultado", "erro", "inicio", "fim", "criado")}


def _mensagem_erro(e):
    return str(e) or type(e).__name__


def base_saida() -> Path:
    b = config.pasta_saida()
    b.mkdir(parents=True, exist_ok=True)
    return b


def pasta_do_pacote(nome) -> Path:
    if not isinstance(nome, str) or not NOME_OK.match(nome) or nome.startswith("."):
        raise WebErro("Nome de pacote inválido.")
    p = base_saida() / nome
    if not (p / "guiao.json").exists():
        raise WebErro("Pacote não encontrado.", 404)
    return p


def ficheiro_do_guiao(nome) -> Path:
    if not isinstance(nome, str) or not NOME_OK.match(nome) or not nome.endswith(".json"):
        raise WebErro("Nome de guião inválido.")
    f = GUIOES / nome
    if not f.exists():
        raise WebErro("Guião não encontrado.", 404)
    return f


def executar_trabalho(tipo, args, log):
    """Corre um trabalho. Devolve um dicionário com o resultado (ou levanta uma exceção)."""
    from . import diario, pacote
    base = base_saida()
    if tipo == "guiao":
        f = pacote.escrever_so_guiao(args["tema"], pasta=GUIOES, log=log)
        return {"guiao": Path(f).name}
    if tipo == "video":
        if args.get("guiao"):
            p = pacote.criar_pacote(guiao_ficheiro=str(ficheiro_do_guiao(args["guiao"])), base=base, log=log)
        else:
            p = pacote.criar_pacote(args["tema"], base=base, log=log)
        _analisar_sem_falhar(p, base, log)
        return {"pacote": Path(p).name}
    if tipo == "semana":
        pastas = pacote.semana(int(args.get("n", 7)), base=base, log=log)
        for p in pastas:
            _analisar_sem_falhar(p, base, log)
        return {"pacotes": [Path(p).name for p in pastas]}
    if tipo == "diario":
        p = diario.diario(base=base, forcar=bool(args.get("forcar")), log=log)
        if p:
            _analisar_sem_falhar(p, base, log)
        return {"pacote": Path(p).name if p else None}
    if tipo == "refazer":
        p = pacote.refazer_pacote(pasta_do_pacote(args["pacote"]), log=log)
        return {"pacote": Path(p).name}
    if tipo == "analisar":
        pasta = pasta_do_pacote(args["pacote"])
        log(f"A analisar «{pasta.name}»...")
        aprendizagem.analisar_pacote(pasta, base=base, log=log)
        return {"pacote": pasta.name}
    if tipo == "destilar":
        log("A juntar as lições em regras...")
        return {"regras": aprendizagem.destilar(base, log=log)}
    raise WebErro(f"Tipo de trabalho desconhecido: {tipo}")


def _analisar_sem_falhar(pasta, base, log):
    log("A analisar o vídeo para a fábrica aprender (só texto e estrutura)...")
    try:
        aprendizagem.analisar_pacote(pasta, base=base, log=log)
    except Exception as e:
        log(f"   (a análise falhou, o vídeo está bom na mesma: {e})")


# ================================================================== dados para a página
def _ler_json(f, padrao=None):
    try:
        return json.loads(Path(f).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return padrao


def listar_pacotes() -> list:
    base = base_saida()
    apr = {r["pasta"]: r for r in aprendizagem.carregar(base)["registos"]}
    out = []
    for p in sorted(base.iterdir(), reverse=True):
        if not p.is_dir() or not (p / "guiao.json").exists():
            continue
        g = _ler_json(p / "guiao.json", {}) or {}
        m = _ler_json(p / "pacote.json", {}) or {}
        r = apr.get(p.name, {})
        q = g.get("qualidade") or {}
        out.append({
            "nome": p.name, "titulo": g.get("titulo", p.name), "tema": g.get("tema", ""),
            "data": m.get("data") or p.name[:10], "duracao": m.get("duracao"),
            "conceito": (g.get("conceito") or {}).get("nome", "") if isinstance(g.get("conceito"), dict) else "",
            "tem_video": (p / "video.mp4").exists(), "tem_capa": (p / "capa.png").exists(),
            "editor": q.get("media"), "analise": (r.get("analise") or {}).get("nota"),
            "diogo": (r.get("diogo") or {}).get("nota"),
            "publicado": bool((r.get("diogo") or {}).get("publicado")),
        })
    return out


def detalhe_pacote(nome) -> dict:
    from . import pacote
    p = pasta_do_pacote(nome)
    g = _ler_json(p / "guiao.json", {})
    m = _ler_json(p / "pacote.json", {}) or {}
    legenda = ""
    try:
        legenda = (p / "legenda.txt").read_text(encoding="utf-8")
    except OSError:
        pass
    r = next((x for x in aprendizagem.carregar(base_saida())["registos"] if x["pasta"] == nome), {})
    try:
        texto = pacote.mostrar_guiao(g)
    except Exception:
        texto = json.dumps(g, ensure_ascii=False, indent=2)
    return {"nome": nome, "guiao": g, "texto": texto, "manifesto": m, "legenda": legenda,
            "tem_video": (p / "video.mp4").exists(), "tem_zip": (p / "pacote.zip").exists(),
            "slides": m.get("carrossel", []), "registo": r}


def listar_guioes() -> list:
    from . import pacote
    out = []
    if GUIOES.exists():
        for f in sorted(GUIOES.glob("*.json"), reverse=True):
            g = _ler_json(f, {})
            if not isinstance(g, dict) or "falas" not in g:
                continue
            try:
                texto = pacote.mostrar_guiao(g)
            except Exception:
                texto = ""
            out.append({"nome": f.name, "titulo": g.get("titulo", f.name), "tema": g.get("tema", ""), "texto": texto})
    return out


def agenda() -> dict:
    """Próximos 7 dias: o que já existe e uma sugestão de tema para os dias vazios."""
    from . import diario, pacote
    base = base_saida()
    hist = pacote.ler_historico(base)
    por_dia = {}
    for h in hist:
        por_dia.setdefault(str(h.get("data", ""))[:10], []).append(h)
    reg = diario.ler_registo(base)
    for dia, pasta in reg.items():
        if not any(h.get("pasta") == pasta for h in por_dia.get(dia, [])):
            por_dia.setdefault(dia, []).append({"titulo": Path(pasta).name, "tema": Path(pasta).name, "pasta": pasta})
    from . import historia
    sugestoes = historia.temas_do_banco(7, evitar=pacote.historico_para_evitar(base))
    hoje = datetime.date.today()
    dias, k = [], 0
    for i in range(7):
        d = (hoje + datetime.timedelta(days=i)).isoformat()
        feitos = [{"titulo": h.get("titulo"), "tema": h.get("tema"), "pasta": Path(h.get("pasta", "")).name}
                  for h in por_dia.get(d, [])]
        sug = None
        if not feitos and k < len(sugestoes):
            sug, k = sugestoes[k], k + 1
        dias.append({"data": d, "feitos": feitos, "sugestao": sug})
    passados = sorted((d for d in por_dia if d < hoje.isoformat()), reverse=True)[:5]
    return {"dias": dias, "ultimos": [{"data": d, "feitos": [h.get("titulo") for h in por_dia[d]]} for d in passados]}


# ------------------------------------------------------------------ conceitos
CAMPOS_LISTA = ("situacoes", "licoes", "temas")


def listar_conceitos() -> dict:
    from .cenarios import ZONAS
    from . import historia
    return {"conceitos": historia.carregar_conceitos(), "zonas": list(ZONAS)}


def _lista_de_texto(v):
    if isinstance(v, str):
        v = v.splitlines()
    return [str(x).strip() for x in (v or []) if str(x).strip()]


def guardar_conceito(c: dict) -> dict:
    from .cenarios import ZONAS
    from . import historia, validar
    if not isinstance(c, dict):
        raise WebErro("Conceito inválido.")
    novo = {"id": re.sub(r"[^a-z0-9_]+", "_", str(c.get("id") or c.get("nome", "")).lower()).strip("_"),
            "nome": str(c.get("nome", "")).strip(), "zona": str(c.get("zona", "")).strip(),
            "certeza": str(c.get("certeza", "moderada")).strip(),
            "explicacao": str(c.get("explicacao", "")).strip(), "cuidado": str(c.get("cuidado", "")).strip()}
    for k in CAMPOS_LISTA:
        novo[k] = _lista_de_texto(c.get(k))
    erros = []
    if not novo["id"]:
        erros.append("Falta o nome.")
    if not 3 <= len(novo["nome"]) <= 40:
        erros.append("O nome tem de ter entre 3 e 40 letras.")
    if novo["zona"] not in ZONAS:
        erros.append("Zona inválida (" + ", ".join(ZONAS) + ").")
    if novo["certeza"] not in ("forte", "moderada"):
        erros.append("A certeza é 'forte' ou 'moderada'.")
    if len(novo["explicacao"]) < 20:
        erros.append("A explicação é demasiado curta.")
    if not novo["temas"]:
        erros.append("Põe pelo menos um tema (um por linha).")
    if not novo["licoes"] or not novo["situacoes"]:
        erros.append("Põe pelo menos uma situação e uma lição.")
    for campo in ("nome", "explicacao", "cuidado", "situacoes", "licoes", "temas"):
        v = novo[campo] if isinstance(novo[campo], list) else [novo[campo]]
        for x in v:
            validar._proibidos(x, campo, erros)
    if erros:
        raise WebErro(" ".join(dict.fromkeys(erros)))
    banco = list(historia.carregar_conceitos())
    for i, x in enumerate(banco):
        if x["id"] == novo["id"]:
            banco[i] = novo
            break
    else:
        banco.append(novo)
    _gravar_conceitos(banco)
    return novo


def apagar_conceito(cid):
    from . import historia
    banco = [x for x in historia.carregar_conceitos() if x["id"] != cid]
    if len(banco) == len(historia.carregar_conceitos()):
        raise WebErro("Conceito não encontrado.", 404)
    if len(banco) < 5:
        raise WebErro("Tem de ficar pelo menos 5 conceitos.")
    _gravar_conceitos(banco)


def _gravar_conceitos(banco):
    from . import historia
    tmp = CONCEITOS.with_suffix(".tmp")
    tmp.write_text(json.dumps(banco, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(CONCEITOS)
    historia._banco = None


# ------------------------------------------------------------------ estado geral
def estado() -> dict:
    from . import diario
    claude = GU.achar_claude() is not None
    import platform
    return {"saida": str(base_saida()), "claude": claude, "windows": platform.system() == "Windows",
            "regras": len(aprendizagem.carregar(base_saida())["regras"]), "hoje": datetime.date.today().isoformat(),
            "diario_hoje": diario.ler_registo(base_saida()).get(datetime.date.today().isoformat())}


# ================================================================== servidor HTTP
class Servidor(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, endereco, token=None, trabalhos=None, hosts_ok=()):
        super().__init__(endereco, Manipulador)
        self.token = token
        self.trabalhos = trabalhos or Trabalhos()
        self.hosts_ok = set(hosts_ok)


class Manipulador(BaseHTTPRequestHandler):
    server_version = "Fabrica"
    protocol_version = "HTTP/1.1"

    def log_message(self, formato, *args):  # silêncio: a consola é para o utilizador
        pass

    # ---- respostas
    def _enviar(self, codigo, corpo: bytes, tipo="application/json; charset=utf-8", extra=None):
        self.send_response(codigo)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(corpo)))
        self.send_header("Cache-Control", "no-store")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(corpo)

    def _json(self, dados, codigo=200, extra=None):
        self._enviar(codigo, json.dumps(dados, ensure_ascii=False).encode("utf-8"), extra=extra)

    # ---- segurança: só este computador (ou o telemóvel com a chave)
    def _autorizado(self, parsed):
        host = (self.headers.get("Host") or "").rsplit(":", 1)[0].strip("[]").lower()
        if host not in {"127.0.0.1", "localhost", "::1"} | self.server.hosts_ok:
            return False, None
        tok = self.server.token
        if not tok:
            return True, None
        galetas = http.cookies.SimpleCookie(self.headers.get("Cookie", ""))
        if "fabrica_t" in galetas and secrets.compare_digest(galetas["fabrica_t"].value, tok):
            return True, None
        q = urllib.parse.parse_qs(parsed.query)
        if "t" in q and secrets.compare_digest(q["t"][0], tok):
            return True, {"Set-Cookie": f"fabrica_t={tok}; Path=/; HttpOnly; SameSite=Strict; Max-Age=31536000"}
        return False, None

    def _tratar(self):
        parsed = urllib.parse.urlparse(self.path)
        ok, extra = self._autorizado(parsed)
        if not ok:
            return self._enviar(403, "Acesso negado. Abre o endereço completo que a fábrica mostrou (com a chave).".encode("utf-8"),
                                "text/plain; charset=utf-8")
        if self.command == "POST" and self.headers.get("X-Fabrica") != "1":
            return self._json({"erro": "Pedido não autorizado."}, 403)
        try:
            self._rota(parsed, extra)
        except WebErro as e:
            self._json({"erro": str(e)}, e.codigo)
        except (GU.GuionistaErro, ValueError, KeyError) as e:
            self._json({"erro": _mensagem_erro(e)}, 400)
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception as e:
            self._json({"erro": "Erro interno: " + _mensagem_erro(e)}, 500)

    do_GET = do_POST = do_HEAD = _tratar

    def _corpo(self) -> dict:
        n = int(self.headers.get("Content-Length") or 0)
        if n > MAX_CORPO:
            raise WebErro("Pedido demasiado grande.", 413)
        if not n:
            return {}
        try:
            d = json.loads(self.rfile.read(n).decode("utf-8"))
        except ValueError:
            raise WebErro("JSON inválido.")
        return d if isinstance(d, dict) else {}

    # ---- rotas
    def _rota(self, parsed, extra):
        caminho = parsed.path
        q = urllib.parse.parse_qs(parsed.query)
        if self.command in ("GET", "HEAD"):
            if caminho == "/":
                return self._enviar(200, PAGINA.read_bytes(), "text/html; charset=utf-8", extra)
            if caminho.startswith("/ficheiro/"):
                return self._ficheiro(caminho[len("/ficheiro/"):])
            if caminho == "/api/estado":
                return self._json(estado(), extra=extra)
            if caminho == "/api/pacotes":
                return self._json(listar_pacotes())
            if caminho.startswith("/api/pacotes/"):
                return self._json(detalhe_pacote(urllib.parse.unquote(caminho.split("/")[3])))
            if caminho == "/api/guioes":
                return self._json(listar_guioes())
            if caminho == "/api/trabalhos":
                return self._json(self.server.trabalhos.ver())
            if caminho.startswith("/api/trabalhos/"):
                desde = int((q.get("desde") or ["0"])[0])
                return self._json(self.server.trabalhos.ver(int(caminho.split("/")[3]), desde))
            if caminho == "/api/conceitos":
                return self._json(listar_conceitos())
            if caminho == "/api/agenda":
                return self._json(agenda())
            if caminho == "/api/aprendizagem":
                return self._json(aprendizagem.estado(base_saida()))
            raise WebErro("Não existe.", 404)

        d = self._corpo()
        if caminho == "/api/trabalhos":
            return self._json(self._novo_trabalho(d), 201)
        if caminho == "/api/feedback":
            pasta = pasta_do_pacote(d.get("pacote"))
            return self._json(aprendizagem.registar_feedback(
                pasta, nota=d.get("nota"), comentario=d.get("comentario", ""), publicado=d.get("publicado"),
                visualizacoes=d.get("visualizacoes"), retencao=d.get("retencao"), base=base_saida()))
        if caminho == "/api/conceitos":
            return self._json(guardar_conceito(d))
        if caminho == "/api/conceitos/apagar":
            apagar_conceito(str(d.get("id", "")))
            return self._json({"ok": True})
        if caminho == "/api/saida":
            if not d.get("pasta"):
                raise WebErro("Diz a pasta.")
            return self._json({"saida": str(config.definir_saida(d["pasta"]))})
        if caminho == "/api/agendar":
            from . import diario
            linhas = []
            ok = diario.desagendar(log=linhas.append) if d.get("desligar") else diario.agendar(d.get("hora", "09:00"), log=linhas.append)
            return self._json({"ok": bool(ok), "mensagem": "\n".join(linhas)})
        raise WebErro("Não existe.", 404)

    def _novo_trabalho(self, d):
        tipo = d.get("tipo")
        tema = str(d.get("tema", "")).strip()
        if tipo in ("guiao", "video") and not (tema or d.get("guiao")):
            raise WebErro("Escreve um tema.")
        if tipo == "guiao" and not tema:
            raise WebErro("Escreve um tema.")
        if len(tema) > 120:
            raise WebErro("O tema é demasiado longo (máx. 120 letras).")
        titulos = {"guiao": f"Guião: {tema}", "video": f"Vídeo: {tema or d.get('guiao')}",
                   "semana": f"Semana ({int(d.get('n', 7))} vídeos)", "diario": "Pacote do dia",
                   "refazer": f"Refazer: {d.get('pacote')}", "analisar": f"Analisar: {d.get('pacote')}",
                   "destilar": "Atualizar regras aprendidas"}
        if tipo not in titulos:
            raise WebErro("Tipo de trabalho desconhecido.")
        if tipo == "semana" and not 1 <= int(d.get("n", 7)) <= 10:
            raise WebErro("De 1 a 10 vídeos.")
        if d.get("guiao"):
            ficheiro_do_guiao(d["guiao"])
        if tipo in ("refazer", "analisar"):
            pasta_do_pacote(d.get("pacote"))
        args = {k: d[k] for k in ("tema", "guiao", "n", "pacote", "forcar") if k in d}
        args["tema"] = tema
        return Trabalhos._resumo(self.server.trabalhos.criar(tipo, titulos[tipo], args))

    def _ficheiro(self, resto):
        partes = [urllib.parse.unquote(x) for x in resto.split("/")]
        if len(partes) not in (2, 3) or any(not NOME_OK.match(x) for x in partes):
            raise WebErro("Ficheiro inválido.")
        pasta = pasta_do_pacote(partes[0])
        f = pasta.joinpath(*partes[1:])
        if not f.is_file() or pasta.resolve() not in f.resolve().parents:
            raise WebErro("Ficheiro não encontrado.", 404)
        tipo = mimetypes.guess_type(f.name)[0] or "application/octet-stream"
        tamanho = f.stat().st_size
        inicio, fim, codigo = 0, tamanho - 1, 200
        r = self.headers.get("Range")
        if r:
            m = re.fullmatch(r"bytes=(\d*)-(\d*)", r.strip())
            if m and (m.group(1) or m.group(2)):
                if m.group(1):
                    inicio = int(m.group(1))
                    fim = int(m.group(2)) if m.group(2) else tamanho - 1
                else:
                    inicio = max(0, tamanho - int(m.group(2)))
                fim = min(fim, tamanho - 1)
                if inicio > fim:
                    self.send_response(416)
                    self.send_header("Content-Range", f"bytes */{tamanho}")
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    return
                codigo = 206
        self.send_response(codigo)
        self.send_header("Content-Type", tipo)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(fim - inicio + 1))
        if codigo == 206:
            self.send_header("Content-Range", f"bytes {inicio}-{fim}/{tamanho}")
        if f.suffix == ".zip":
            self.send_header("Content-Disposition", f'attachment; filename="{pasta.name}.zip"')
        self.end_headers()
        if self.command == "HEAD":
            return
        with open(f, "rb") as fh:
            fh.seek(inicio)
            restante = fim - inicio + 1
            while restante > 0:
                bloco = fh.read(min(65536, restante))
                if not bloco:
                    break
                self.wfile.write(bloco)
                restante -= len(bloco)


# ================================================================== arranque
def ip_local() -> str:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("10.255.255.255", 1))
            return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"


def chave_guardada() -> str:
    """A chave de acesso (para o telemóvel) fica no config.json, para não mudar de cada vez."""
    d = config.ler()
    if not isinstance(d.get("chave_web"), str) or len(d["chave_web"]) < 16:
        d["chave_web"] = secrets.token_urlsafe(16)
        config.gravar(d)
    return d["chave_web"]


def servir(porta=PORTA, rede=False, abrir=True, log=print):
    if rede:
        ip, token = ip_local(), chave_guardada()
        servidor = Servidor(("0.0.0.0", porta), token=token, hosts_ok={ip})
        link_pc = f"http://127.0.0.1:{porta}/?t={token}"
        log("A página está aberta também na tua rede Wi-Fi.")
        log(f"  No telemóvel (mesmo Wi-Fi), abre:  http://{ip}:{porta}/?t={token}")
        log("  Quem não tiver a chave (o ?t=... do endereço) não consegue entrar.")
    else:
        servidor = Servidor(("127.0.0.1", porta))
        link_pc = f"http://127.0.0.1:{porta}/"
    log(f"No computador, abre:  {link_pc}")
    log("Para fechar a fábrica, carrega Ctrl+C nesta janela.")
    if abrir:
        import webbrowser
        threading.Timer(0.8, lambda: webbrowser.open(link_pc)).start()
    try:
        servidor.serve_forever()
    except KeyboardInterrupt:
        log("\nA fechar.")
    finally:
        servidor.server_close()
