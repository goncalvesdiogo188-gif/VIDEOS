"""Guionista: pede ao Claude Code (modo não interativo) para escrever guiões.

Usa o comando `claude -p` com as ferramentas desativadas. O texto vai por stdin
e a resposta vem por stdout. Só usa a biblioteca padrão.
"""
import json
import os
import re
import shlex
import shutil
import subprocess
import tempfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
CENARIOS = ["comando", "memorias", "dopamina", "sono", "alarme", "cofre", "social", "habitos"]


class GuionistaErro(Exception):
    """Erro ao falar com o Claude ou ao interpretar a resposta."""


def comando_claude() -> list[str]:
    """Comando usado para chamar o Claude. FABRICA_CLAUDE substitui o padrão."""
    env = os.environ.get("FABRICA_CLAUDE", "").strip()
    if env:
        return shlex.split(env, posix=(os.name != "nt"))
    return [shutil.which("claude") or "claude", "-p", "--tools", ""]


def _uma_vez(prompt: str, timeout: int) -> str:
    cmd = comando_claude()
    try:
        with tempfile.TemporaryDirectory(prefix="fabrica_claude_") as tmp:
            r = subprocess.run(cmd, input=prompt, capture_output=True, text=True,
                               encoding="utf-8", errors="replace", cwd=tmp, timeout=timeout)
    except FileNotFoundError:
        raise GuionistaErro("O Claude Code não está instalado (comando 'claude' não encontrado). "
                            "Instala-o e faz login com 'claude'.")
    except subprocess.TimeoutExpired:
        raise GuionistaErro(f"O Claude demorou mais de {timeout} segundos a responder.")
    except OSError as e:
        raise GuionistaErro(f"Não consegui executar o Claude: {e}")
    saida = (r.stdout or "").strip()
    if r.returncode != 0:
        detalhe = (r.stderr or r.stdout or "").strip()[:300]
        low = detalhe.lower()
        if any(p in low for p in ("login", "log in", "logged", "authenticat", "api key", "401")):
            raise GuionistaErro("O Claude Code não tem sessão iniciada. Corre 'claude' e faz login. "
                                f"Detalhe: {detalhe}")
        raise GuionistaErro(f"O Claude falhou (código {r.returncode}). {detalhe}")
    if not saida:
        raise GuionistaErro("O Claude devolveu uma resposta vazia.")
    return saida


def perguntar_claude(prompt: str, timeout: int = 300) -> str:
    """Envia o texto ao Claude e devolve a resposta. Tenta mais uma vez se falhar."""
    try:
        return _uma_vez(prompt, timeout)
    except GuionistaErro as e1:
        if "não está instalado" in str(e1):
            raise
        try:
            return _uma_vez(prompt, timeout)
        except GuionistaErro as e2:
            raise GuionistaErro(f"{e2} (depois de 2 tentativas)") from e2


def extrair_json(texto: str):
    """Tira o objeto/lista JSON de uma resposta (com ou sem ```json e texto à volta)."""
    if not isinstance(texto, str) or not texto.strip():
        raise GuionistaErro("Resposta vazia: não há JSON para ler.")
    candidatos = [texto.strip()]
    candidatos += [m.strip() for m in re.findall(r"```(?:json)?\s*(.*?)```", texto, re.S | re.I)]
    dec = json.JSONDecoder()
    for c in candidatos:
        try:
            obj = json.loads(c)
        except ValueError:
            continue
        if isinstance(obj, (dict, list)):
            return obj
    # procura o primeiro objeto/lista válido no texto
    for c in candidatos[::-1]:
        for i, ch in enumerate(c):
            if ch in "{[":
                try:
                    obj, _ = dec.raw_decode(c[i:])
                except ValueError:
                    continue
                if isinstance(obj, (dict, list)):
                    return obj
    raise GuionistaErro("Não encontrei JSON válido na resposta do Claude.")


def _biblia() -> str:
    try:
        return (RAIZ / "BIBLIA.md").read_text(encoding="utf-8")
    except OSError as e:
        raise GuionistaErro(f"Não consegui ler o BIBLIA.md: {e}")


def _listas() -> tuple[str, str]:
    from . import personagens
    return ", ".join(personagens.EXPRESSOES), ", ".join(personagens.POSES)


def _aderecos():
    from . import aderecos
    return list(aderecos.ADERECOS)


def _como_dict(obj, o_que="guião") -> dict:
    if isinstance(obj, list) and len(obj) == 1 and isinstance(obj[0], dict):
        obj = obj[0]
    if not isinstance(obj, dict):
        raise GuionistaErro(f"A resposta do Claude não é um {o_que} (esperava um objeto JSON).")
    return obj


def escrever_guiao(tema: str) -> dict:
    """Pede um guião completo, no formato da BÍBLIA, sobre o tema."""
    expr, poses = _listas()
    prompt = f"""És o guionista de uma conta de Reels com duas personagens cartoon originais (Neurão e Zé Neurónio).
Escreve em português simples e neutro (percebido em Portugal e no Brasil).

=== BÍBLIA DA CONTA (regras de ouro) ===
{_biblia()}
=== FIM DA BÍBLIA ===

NOTA: o mundo é agora um cérebro futurista com 8 zonas. Em vez de sala/quarto/loja, usa em "cenario" um destes valores: {", ".join(CENARIOS)}.
(comando = centro de comando, foco e energia; memorias = arquivo de memórias, aprender; dopamina = recompensas, compras por impulso, vícios; sono = descanso, cansaço; alarme = ansiedade, stress, medo; cofre = dinheiro, poupar, gastos; social = redes sociais, comparação, opinião dos outros; habitos = hábitos, rotinas, disciplina.) Escolhe o que melhor encaixa no tema e varia entre zonas.

TEMA: {tema}

REGRAS OBRIGATÓRIAS:
- 5 a 7 falas; no máximo 12 palavras por fala.
- Gancho logo na primeira fala; humor leve.
- As duas personagens falam ("neurao" e "ze").
- A última fala é do "ze" e contém a lição prática.
- Total falado: cerca de 50 a 65 palavras (20 a 27 segundos).
- Português neutro (Portugal e Brasil), sem calão de um só país.
- Proibido: promessas de ganhos, conselhos de investimento específicos, conselhos médicos, referências a séries, marcas ou personagens existentes.
- "expressao" só pode ser: {expr}.
- "pose" só pode ser: {poses}.
- Opcional em cada fala: "adereco" (objeto na mão de quem fala), um de: {", ".join(_aderecos())}. Usa em 1 a 3 falas, quando o objeto ajuda a piada ou a explicação (ex.: telefone no scroll, carrinho nas compras, almofada no sono, moeda no dinheiro, lâmpada numa ideia).
- Opcional em cada fala: "acao", uma de: salta (alegria, ideia), treme (medo, susto), aproxima (anda até ao outro para explicar ou confrontar). Usa no máximo 2 ações por vídeo.
- Varia as poses: usa também bracos_cruzados (convencido, a desafiar), mao_testa (vergonha, "que vergonha"), vitoria (celebrar), acena (cumprimentar no início).
- "cenario" só pode ser: {", ".join(CENARIOS)}.
- "titulo" com no máximo 60 caracteres.
- "carrossel": 3 a 5 itens {{"titulo": "...", "texto": "..."}}.
- "legenda": frase de gancho + pergunta para comentários + "Segue para mais!".
- "hashtags": exatamente 5, cada uma a começar por #, em minúsculas, sem acentos e sem espaços.
- Campos: tema, titulo, cenario, falas (quem, texto, expressao, pose, e opcionalmente adereco e acao), carrossel, legenda, hashtags.

Responde APENAS com o JSON, sem mais texto."""
    guiao = _como_dict(extrair_json(perguntar_claude(prompt)))
    if not guiao.get("tema"):
        guiao["tema"] = tema
    return guiao


def corrigir_guiao(guiao: dict, erros: list[str]) -> dict:
    """Pede ao Claude para corrigir os erros indicados e devolve o guião completo."""
    expr, poses = _listas()
    lista = "\n".join(f"- {e}" for e in erros)
    prompt = f"""Tens um guião de Reel (JSON) com erros de validação. Corrige-o.

REGRAS DA CONTA:
{_biblia()}

GUIÃO ATUAL:
{json.dumps(guiao, ensure_ascii=False, indent=1)}

ERROS A CORRIGIR:
{lista}

Mantém tudo o que está certo. Valores permitidos: expressao: {expr}; pose: {poses}; cenario: {", ".join(CENARIOS)}.
Máximo 12 palavras por fala, 5 a 7 falas, última fala do "ze".
Responde APENAS com o guião completo corrigido, em JSON, sem mais texto."""
    novo = _como_dict(extrair_json(perguntar_claude(prompt)))
    if not novo.get("tema") and guiao.get("tema"):
        novo["tema"] = guiao["tema"]
    return novo


def rever_texto(guiao: dict) -> tuple[dict, list[str]]:
    """Revisão ortográfica e gramatical. Devolve (guião corrigido, lista de alterações)."""
    prompt = f"""Faz a revisão de texto deste guião de Reel: ortografia, gramática, acentos e pontuação, em português neutro (Portugal e Brasil).
NÃO mudes o sentido, NÃO tornes nenhuma frase mais longa e NÃO alteres a estrutura (mesmas falas, mesmos valores de quem, expressao, pose, adereco, acao, cenario).
Corrige só o que estiver mesmo errado.

GUIÃO:
{json.dumps(guiao, ensure_ascii=False, indent=1)}

Responde APENAS com um JSON neste formato:
{{"guiao": {{...guião completo corrigido...}}, "alteracoes": ["antes -> depois", ...]}}
Se não houver nada a corrigir, devolve o guião igual e "alteracoes": []."""
    r = extrair_json(perguntar_claude(prompt))
    if isinstance(r, dict) and isinstance(r.get("guiao"), dict):
        novo, alt = r["guiao"], r.get("alteracoes") or []
    elif isinstance(r, dict) and "falas" in r:
        novo, alt = r, []
    else:
        raise GuionistaErro("A revisão do Claude veio num formato inesperado.")
    alt = [str(a) for a in alt if str(a).strip()]
    if novo == guiao:
        return guiao, []
    if not alt:
        alt = ["(texto ajustado pela revisão)"]
    return novo, alt


def _normal(a: str) -> str:
    return re.sub(r"\W+", " ", a.lower()).strip()


def _limpar_temas(lista, evitar) -> list[str]:
    vistos = [_normal(str(e)) for e in evitar]
    out = []
    for t in lista:
        if isinstance(t, dict):
            t = t.get("tema") or t.get("titulo") or ""
        t = str(t).strip()
        if t and _normal(t) not in vistos:
            out.append(t)
            vistos.append(_normal(t))
    return out


def escolher_temas(n: int, evitar: list[str] = ()) -> list[str]:
    """Devolve exatamente n temas diferentes do nicho, longe dos que estão em `evitar`."""
    evitar = list(evitar)
    achados: list[str] = []
    for _ in range(2):
        falta = n - len(achados)
        if falta <= 0:
            break
        bloco = "\n".join(f"- {t}" for t in evitar + achados) or "(nenhum)"
        prompt = f"""Conta de Reels: "O teu cérebro explica a vida adulta" (dinheiro, hábitos, procrastinação, sono, compras por impulso, redes sociais, motivação, rotinas).
Sugere {falta} temas curtos e diferentes (2 a 5 palavras cada) para novos Reels, em português neutro.
NÃO repitas nem sugiras temas parecidos com estes:
{bloco}

Responde APENAS com uma lista JSON de {falta} textos, por exemplo ["tema um", "tema dois"]."""
        r = extrair_json(perguntar_claude(prompt))
        if isinstance(r, dict):
            r = r.get("temas") or next((v for v in r.values() if isinstance(v, list)), [])
        if not isinstance(r, list):
            raise GuionistaErro("O Claude não devolveu uma lista de temas.")
        achados += _limpar_temas(r, evitar + achados)
    if len(achados) < n:
        raise GuionistaErro(f"O Claude só deu {len(achados)} temas novos em vez de {n}.")
    return achados[:n]
