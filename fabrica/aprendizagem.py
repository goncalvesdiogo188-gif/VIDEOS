"""A fábrica aprende com o que já fez.

Depois de cada vídeo há duas fontes de aprendizagem:

  1. ANÁLISE   o Claude lê o guião, o plano das cenas, a duração, as notas do editor e as
               tuas notas, e escreve pontos fortes, fraquezas e REGRAS para os próximos.
  2. FEEDBACK  tu dás uma nota de 1 a 5 e (se quiseres) um comentário, e mais tarde os
               números reais da publicação (visualizações, retenção).

As regras são juntadas (destiladas) numa lista curta e entram em todos os pedidos de escrita
seguintes (ficha, roteiro, reescrita e editor), por isso os vídeos novos evitam os erros dos
anteriores. Tudo fica em  aprendizagem.json  dentro da pasta de saída (junto do historico.json).

Importante: o Claude NÃO vê nem ouve o vídeo. Analisa o texto, a estrutura, os tempos e o que
tu lhe contas. A voz, a música e o ritmo só tu os podes julgar; escreve-o no comentário.
"""
import datetime
import json
import re
from pathlib import Path

from . import config
from . import guionista as GU

FICHEIRO = "aprendizagem.json"
MAX_REGRAS = 12          # regras ativas que entram nos pedidos
DESTILAR_A_CADA = 3      # volta a destilar quando há tantas lições novas


def _base(base=None) -> Path:
    return Path(base) if base else config.pasta_saida()


def _agora() -> str:
    return datetime.datetime.now().strftime("%Y-%m-%d %H:%M")


# ------------------------------------------------------------------ ficheiro
def vazio() -> dict:
    return {"registos": [], "licoes": [], "regras": [], "licoes_na_ultima_destilacao": 0}


def carregar(base=None) -> dict:
    try:
        d = json.loads((_base(base) / FICHEIRO).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return vazio()
    if not isinstance(d, dict):
        return vazio()
    for k, v in vazio().items():
        d.setdefault(k, v)
    return d


def guardar(dados: dict, base=None) -> None:
    b = _base(base)
    b.mkdir(parents=True, exist_ok=True)
    (b / FICHEIRO).write_text(json.dumps(dados, ensure_ascii=False, indent=2), encoding="utf-8")


def _registo(dados: dict, nome: str) -> dict:
    for r in dados["registos"]:
        if r.get("pasta") == nome:
            return r
    novo = {"pasta": nome}
    dados["registos"].append(novo)
    return novo


def _limpar_lista(v, maximo=6, tamanho=220) -> list:
    if not isinstance(v, list):
        return []
    return [str(x).strip()[:tamanho] for x in v if str(x).strip()][:maximo]


def _acrescentar_licoes(dados: dict, textos, origem: str) -> int:
    """Acrescenta lições novas (ou soma 1 às repetidas). Devolve quantas eram novas."""
    novas = 0
    for t in textos:
        chave = _chave(t)
        for l in dados["licoes"]:
            if _chave(l["texto"]) == chave:
                l["vezes"] = l.get("vezes", 1) + 1
                break
        else:
            dados["licoes"].append({"texto": t, "origem": origem, "vezes": 1, "criada": _agora()})
            novas += 1
    return novas


def _chave(t: str) -> str:
    return re.sub(r"\W+", " ", str(t).lower()).strip()


# ------------------------------------------------------------------ análise de um pacote
def _resumo_pacote(pasta: Path) -> dict:
    """Tudo o que o Claude pode saber do pacote (sem ver o vídeo)."""
    g = json.loads((pasta / "guiao.json").read_text(encoding="utf-8"))
    info = ""
    try:
        info = (pasta / "info.txt").read_text(encoding="utf-8")
    except OSError:
        pass
    return {"guiao": g, "info": info}


def analisar_pacote(pasta, base=None, log=print) -> dict:
    """Pede ao Claude uma análise do pacote e guarda-a. Devolve o registo."""
    pasta = Path(pasta)
    base = _base(base or pasta.parent)
    try:
        resumo = _resumo_pacote(pasta)
    except (OSError, ValueError) as e:
        raise GU.GuionistaErro(f"Não consegui ler o guião de {pasta.name}: {e}")
    dados = carregar(base)
    reg = _registo(dados, pasta.name)
    g = resumo["guiao"]
    mao = reg.get("diogo") or {}
    feedback = ""
    if mao:
        feedback = ("\nO DIOGO (dono da conta) DISSE: nota " + str(mao.get("nota", "?")) + "/5"
                    + (f"; comentário: {mao['comentario']}" if mao.get("comentario") else "")
                    + (f"; visualizações: {mao['visualizacoes']}" if mao.get("visualizacoes") else "")
                    + (f"; retenção média: {mao['retencao']}%" if mao.get("retencao") else "")
                    + ". Dá muito peso a isto.")
    prompt = f"""És o analista de uma conta de Reels cómicos e educativos (Neurão e Zé Neurónio, dentro da cabeça do Dono). Analisa este vídeo JÁ FEITO para a fábrica aprender e melhorar os próximos.

IMPORTANTE: tu não vês nem ouves o vídeo. Analisa só o que tens: o guião (falas, cenas da Janela do Dono, legenda), as notas do editor automático e as informações técnicas. Não inventes opiniões sobre voz, música ou animação.

REGRAS DA CONTA:
{GU._biblia()}

GUIÃO:
{json.dumps(g, ensure_ascii=False)}

INFORMAÇÕES TÉCNICAS:
{resumo['info']}{feedback}

Faz uma análise honesta e específica (cita falas pelo número, a contar de 1). Procura: gancho fraco nos primeiros 3 segundos; falas que não respondem à anterior; explicação sem piada ou piada sem história; lição vaga; piadas repetidas de vídeos comuns (cozinha, frigorífico a limpar, etc.); remate fraco; texto da Janela que não escala; duração demasiado longa ou curta; legenda sem gancho.

Depois escreve de 1 a 3 REGRAS novas e GERAIS para os próximos guiões (não sobre este tema!), no formato "Faz X" ou "Evita Y", curtas e acionáveis. Se o vídeo foi bom, regras sobre o que repetir.

Responde APENAS com JSON:
{{"nota": 1 a 10 (a tua nota geral, exigente), "pontos_fortes": ["..."], "fraquezas": ["..."], "regras": ["..."], "resumo": "uma frase"}}"""
    r = GU.extrair_json(GU.perguntar_claude(prompt))
    r = GU._como_dict(r, "análise")
    try:
        nota = max(1, min(10, int(round(float(r.get("nota", 5))))))
    except (TypeError, ValueError):
        nota = 5
    analise = {"nota": nota, "pontos_fortes": _limpar_lista(r.get("pontos_fortes")),
               "fraquezas": _limpar_lista(r.get("fraquezas")), "regras": _limpar_lista(r.get("regras"), 3),
               "resumo": str(r.get("resumo", "")).strip()[:300], "quando": _agora()}
    dados = carregar(base)  # recarrega: pode ter mudado enquanto o Claude pensava
    reg = _registo(dados, pasta.name)
    reg.update({"tema": g.get("tema", ""), "titulo": g.get("titulo", ""),
                "conceito": (g.get("conceito") or {}).get("nome", "") if isinstance(g.get("conceito"), dict) else "",
                "data": reg.get("data") or _agora(), "analise": analise})
    q = g.get("qualidade")
    if isinstance(q, dict):
        reg["editor"] = {"media": q.get("media"), "notas": q.get("notas")}
    novas = _acrescentar_licoes(dados, analise["regras"], "analise")
    guardar(dados, base)
    log(f"   Análise guardada ({novas} lição(ões) nova(s)).")
    destilar_se_preciso(base, log=log)
    return reg


# ------------------------------------------------------------------ feedback do Diogo
def registar_feedback(pasta, nota=None, comentario="", publicado=None, visualizacoes=None,
                      retencao=None, base=None) -> dict:
    """Guarda o que o Diogo achou do vídeo. Notas 1 a 5; o comentário vira lição."""
    nome = Path(pasta).name
    dados = carregar(_base(base))
    reg = _registo(dados, nome)
    mao = reg.setdefault("diogo", {})
    if nota not in (None, ""):
        n = int(nota)
        if not 1 <= n <= 5:
            raise ValueError("A nota é de 1 a 5.")
        mao["nota"] = n
    comentario = str(comentario or "").strip()[:600]
    if comentario and comentario != mao.get("comentario"):
        mao["comentario"] = comentario
        _acrescentar_licoes(dados, [f"O Diogo disse sobre «{reg.get('titulo') or nome}»: {comentario}"], "diogo")
    if publicado is not None:
        mao["publicado"] = bool(publicado)
    for chave, v in (("visualizacoes", visualizacoes), ("retencao", retencao)):
        if v not in (None, ""):
            try:
                mao[chave] = float(v) if chave == "retencao" else int(v)
            except (TypeError, ValueError):
                raise ValueError(f"Valor inválido para {chave}.")
    mao["quando"] = _agora()
    guardar(dados, _base(base))
    return reg


# ------------------------------------------------------------------ destilar as regras
def destilar(base=None, log=print) -> list:
    """Junta todas as lições e feedback numa lista curta de regras. Devolve as regras ativas."""
    base = _base(base)
    dados = carregar(base)
    licoes = dados["licoes"]
    if not licoes:
        return dados["regras"]
    texto = "\n".join(f"- ({l.get('origem')}, vista {l.get('vezes', 1)}x) {l['texto']}" for l in licoes[-60:])
    notas = [f"{r.get('titulo') or r['pasta']}: Diogo {r['diogo']['nota']}/5" for r in dados["registos"]
             if isinstance(r.get("diogo"), dict) and r["diogo"].get("nota")][-15:]
    prompt = f"""Estas são as lições aprendidas pela fábrica de Reels (Neurão e Zé Neurónio) com os vídeos já feitos. As do "diogo" são do dono da conta e valem mais; as repetidas muitas vezes são mais importantes.

LIÇÕES:
{texto}

NOTAS DO DIOGO AOS VÍDEOS: {"; ".join(notas) or "(ainda nenhuma)"}

REGRAS ATUAIS: {json.dumps(dados['regras'], ensure_ascii=False)}

Junta tudo em NO MÁXIMO {MAX_REGRAS} regras curtas (até 160 caracteres), gerais, acionáveis e sem repetições ("Faz..." / "Evita..."). Funde as parecidas, tira as que se contradizem (as do Diogo ganham) e as que são só sobre um tema específico.

Responde APENAS com uma lista JSON de textos."""
    try:
        r = GU.extrair_json(GU.perguntar_claude(prompt))
        if isinstance(r, dict):
            r = next((v for v in r.values() if isinstance(v, list)), [])
        regras = _limpar_lista(r, MAX_REGRAS, 200)
    except GU.GuionistaErro as e:
        log(f"   Não consegui destilar as regras ({e}); fico com as lições mais repetidas.")
        ordenadas = sorted(licoes, key=lambda l: (l.get("origem") == "diogo", l.get("vezes", 1)), reverse=True)
        regras = [l["texto"][:200] for l in ordenadas[:MAX_REGRAS]]
    if regras:
        dados["regras"] = regras
    dados["licoes_na_ultima_destilacao"] = len(licoes)
    guardar(dados, base)
    return dados["regras"]


def destilar_se_preciso(base=None, log=print) -> None:
    dados = carregar(base)
    if len(dados["licoes"]) - dados.get("licoes_na_ultima_destilacao", 0) >= DESTILAR_A_CADA or \
            (dados["licoes"] and not dados["regras"]):
        try:
            destilar(base, log=log)
        except Exception as e:  # nunca estraga o que se estava a fazer
            log(f"   (a destilação das regras falhou: {e})")


# ------------------------------------------------------------------ o que entra nos pedidos
def bloco_para_prompt(base=None) -> str:
    """Texto com as regras aprendidas, para colar nos pedidos de escrita ("" se ainda não há)."""
    try:
        regras = carregar(base)["regras"]
    except Exception:
        return ""
    if not regras:
        return ""
    lista = "\n".join(f"- {r}" for r in regras[:MAX_REGRAS])
    return ("APRENDIZAGENS DESTA CONTA (tiradas dos vídeos anteriores e das opiniões do Diogo; "
            "segue-as, têm prioridade sobre os teus hábitos):\n" + lista + "\n")


def estado(base=None) -> dict:
    """Resumo para a página: regras, lições recentes e evolução das notas."""
    d = carregar(base)
    evolucao = []
    for r in d["registos"]:
        evolucao.append({"pasta": r["pasta"], "titulo": r.get("titulo") or r["pasta"],
                         "editor": (r.get("editor") or {}).get("media"),
                         "analise": (r.get("analise") or {}).get("nota"),
                         "diogo": (r.get("diogo") or {}).get("nota")})
    return {"regras": d["regras"], "licoes": d["licoes"][-30:], "registos": d["registos"], "evolucao": evolucao}
