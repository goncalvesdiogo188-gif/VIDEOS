"""Escrita de guiões em vários passos, para histórias com sentido e piada.

Em vez de um único pedido ao Claude ("escreve um guião"), a fábrica segue o que faz
uma equipa de argumento:

  1. CONCEITO   escolhe um conceito real do banco (conteudo/conceitos.json), para a
                ciência ser verdadeira e para os vídeos não se repetirem.
  2. FICHA      inventa uma situação concreta (um Dono com nome, hora, objeto), 4 a 6
                cenas que escalam, uma reviravolta, a piada do Zé, a lição e um "callback".
  3. ROTEIRO    escreve o diálogo a partir da ficha, com exemplos de ouro como modelo.
  4. CRÍTICA    um editor exigente dá nota de 1 a 5 a 7 critérios. Se algum for baixo,
  5. REESCRITA  o roteiro é reescrito com os problemas apontados (até 2 voltas).

O resultado é um guião JSON no formato da BIBLIA.md (com "dono", "conceito" e "cena").
Cada passo é um pedido ao Claude Code em modo não interativo.
"""
import json
import random
import re
import unicodedata
from pathlib import Path

from . import guionista as GU

RAIZ = Path(__file__).resolve().parent.parent
CONTEUDO = RAIZ / "conteudo"

NOTA_MINIMA = 4        # cada critério tem de ter pelo menos isto
MEDIA_MINIMA = 4.0     # e a média também
MAX_RONDAS = 1         # voltas de reescrita (cada uma custa ~2 minutos)
PALAVRAS_ALVO = (100, 170)   # no total do vídeo (50 a 90 s)
FALAS_ALVO = (10, 16)

CRITERIOS = {
    "coerencia": "Cada fala responde ao que a anterior disse (pergunta→resposta, provocação→reação). Não há frases que saiam do nada, nem lições que aparecem de repente. A história percebe-se do princípio ao fim.",
    "historia": "Há uma situação concreta (um Dono com nome, hora e objeto), uma escalada em que cada cena complica a anterior, e uma reviravolta. Não é só uma explicação com piadas soltas.",
    "humor": "As piadas são concretas e nascem da situação. O Neurão é pomposo e acaba a errar ou a reivindicar o mérito; o Zé é distraído, literal e absurdo, mas honesto. Há um remate ou callback.",
    "ciencia": "O conceito real é nomeado e explicado de forma correta, simples e curta, sem exagerar (respeita o grau de certeza). Quem vê aprende uma coisa verdadeira.",
    "licao": "A lição é uma ação prática e concreta, dirigida ao Dono, que nasce da história e do conceito. Ao ouvi-la, o espectador sabe o que fazer amanhã.",
    "ritmo": "Falas curtas e variadas, bom ritmo, sem monólogos e sem mais de duas falas seguidas da mesma personagem. O gancho prende nos primeiros 3 segundos. A última fala é um remate com sentido (nunca uma frase solta).",
    "imagem": "As cenas da Janela do Dono contam a história sozinhas, sem as falas, com horas e textos curtos que escalam. Os adereços, ações e expressões ajudam a piada.",
}

NOMES_DONO = ["Marta", "Rui", "Bia", "Tiago", "Inês", "Lucas", "Carla", "Pedro", "Ana", "Rafa", "Duarte",
              "Sofia", "Nuno", "Clara", "Gil", "Joana", "Vasco", "Leo", "Mia", "Hugo"]


# ------------------------------------------------------------------ banco de conceitos
_banco = None


def carregar_conceitos():
    global _banco
    if _banco is None:
        _banco = json.loads((CONTEUDO / "conceitos.json").read_text(encoding="utf-8"))
    return _banco


def carregar_exemplos():
    return [json.loads(f.read_text(encoding="utf-8")) for f in sorted((CONTEUDO / "exemplos").glob("*.json"))]


def _simples(t):
    """Texto em minúsculas, sem acentos nem pontuação (para comparar temas)."""
    t = unicodedata.normalize("NFD", str(t).lower())
    t = "".join(c for c in t if unicodedata.category(c) != "Mn")
    return " ".join(re.sub(r"[^a-z0-9 ]+", " ", t).split())


def conceito_por_id(cid):
    return next((c for c in carregar_conceitos() if c["id"] == cid), None)


def conceito_do_tema(tema):
    """O conceito do banco cujo tema é exatamente este (ignorando acentos e maiúsculas), ou None."""
    alvo = _simples(tema)
    for c in carregar_conceitos():
        if any(_simples(t) == alvo for t in c["temas"]):
            return c
    return None


def temas_do_banco(n, evitar=(), semente=None):
    """Até n temas do banco, de conceitos diferentes e de zonas variadas.

    `evitar` pode ter temas já feitos e "conceito:ID" para conceitos já usados.
    Os conceitos nunca usados vêm primeiro.
    """
    rnd = random.Random(semente)
    evitar = list(evitar)
    ja_temas = {_simples(x) for x in evitar if not str(x).startswith("conceito:")}
    ja_conc = {str(x)[9:] for x in evitar if str(x).startswith("conceito:")}
    for c in carregar_conceitos():  # um tema já feito conta como o conceito já usado
        if any(_simples(t) in ja_temas for t in c["temas"]):
            ja_conc.add(c["id"])
    livres = []
    for c in carregar_conceitos():
        if c["id"] in ja_conc:
            continue
        disponiveis = [t for t in c["temas"] if _simples(t) not in ja_temas]
        if disponiveis:
            livres.append((c, disponiveis[0]))
    rnd.shuffle(livres)
    escolhidos, zonas_usadas = [], []
    while livres and len(escolhidos) < n:
        # prefere uma zona que ainda não saiu (variedade visual)
        i = next((k for k, (c, _) in enumerate(livres) if c["zona"] not in zonas_usadas), 0)
        c, t = livres.pop(i)
        escolhidos.append(t)
        zonas_usadas.append(c["zona"])
        if len(zonas_usadas) >= 8:
            zonas_usadas = []
    return escolhidos


# ------------------------------------------------------------------ passo 1: conceito
def escolher_conceito(tema, log=print):
    """Conceito do banco para o tema (ou um conceito novo proposto pelo Claude, marcado como tal)."""
    c = conceito_do_tema(tema)
    if c:
        return c
    banco = carregar_conceitos()
    lista = "\n".join(f"- {x['id']}: {x['nome']} — {x['explicacao']}" for x in banco)
    prompt = f"""Conta de Reels cómicos e educativos sobre a vida adulta e o cérebro.
TEMA do vídeo: "{tema}"

Escolhe, da lista, o conceito REAL que melhor explica este tema:
{lista}

Se NENHUM servir, propõe um conceito real, conhecido e bem documentado da psicologia ou da neurociência (nunca inventes).

Responde APENAS com JSON. Se escolheste da lista: {{"id": "id_da_lista"}}.
Se propões outro: {{"id": null, "nome": "Nome curto", "explicacao": "explicação correta numa frase", "certeza": "forte ou moderada", "cuidado": "o que não exagerar", "zona": "comando, memorias, dopamina, sono, alarme, cofre, social ou habitos", "licoes": ["ação prática concreta"], "situacoes": ["situação do dia a dia"]}}"""
    r = GU.extrair_json(GU.perguntar_claude(prompt))
    if isinstance(r, dict) and r.get("id"):
        achado = conceito_por_id(r["id"])
        if achado:
            return achado
    if isinstance(r, dict) and r.get("nome") and r.get("explicacao"):
        r.setdefault("id", "novo_" + (_simples(r["nome"]) or "conceito").replace(" ", "_")[:30])
        r["id"] = r["id"] or "novo_conceito"
        r.setdefault("certeza", "moderada")
        r.setdefault("cuidado", "Dizer 'tende a' e não exagerar.")
        r.setdefault("zona", "comando")
        r.setdefault("licoes", [])
        r.setdefault("situacoes", [])
        r["_novo"] = True
        log("   Conceito novo proposto pelo Claude (fora do banco): " + r["nome"])
        return r
    raise GU.GuionistaErro("Não consegui escolher um conceito para este tema.")


# ------------------------------------------------------------------ passo 2: ficha
def _bloco_conceito(c):
    return (f"- Nome: {c['nome']}\n- Explicação correta: {c['explicacao']}\n"
            f"- Grau de certeza: {c.get('certeza', 'moderada')}\n- Cuidado: {c.get('cuidado', '')}")


def criar_ficha(tema, conceito, dono, log=print):
    from .janela import ICONES, TEXTO_MAX
    prompt = f"""És o argumentista de uma série de Reels educativos e cómicos. Duas personagens cartoon, o Neurão (cérebro pomposo e convencido) e o Zé Neurónio (cérebro distraído, literal e honesto), vivem dentro da cabeça de uma pessoa, "o Dono", e comentam tudo o que ela faz. Uma janela mostra ao espectador o que o Dono está a fazer.

CONCEITO REAL A ENSINAR (não inventes factos):
{_bloco_conceito(conceito)}

TEMA: {tema}
NOME DO DONO (usa este): {dono}
Situações possíveis (inspiração; podes inventar outra mais concreta): {"; ".join(conceito.get("situacoes", []))}
Lições práticas possíveis: {"; ".join(conceito.get("licoes", []))}

Cria a FICHA DA HISTÓRIA. Regras:
- Uma situação concreta e reconhecível: o Dono com nome, uma hora, um objeto, um objetivo simples. Nada de vago ("uma pessoa que adia coisas").
- 4 a 6 CENAS que contam a história sozinhas, sem falas. Cada cena tem uma "hora" (ex.: "22:40", "3 dias depois"), um "icone" (um destes: {", ".join(ICONES)}) e um "texto" de no máximo {TEXTO_MAX} caracteres que diz o que o Dono faz. A "hora" tem no máximo 14 caracteres.
- Escalada: cada cena complica ou piora a anterior, de forma divertida e cada vez mais absurda.
- Reviravolta: um momento que vira a situação (o plano do Neurão falha, o Dono percebe uma coisa, a consequência chega).
- Piada do Zé: uma interpretação à letra ou uma comparação absurda do dia a dia, ligada a esta situação.
- Lição: uma ação prática e concreta que o Dono faz amanhã, que nasce da história e do conceito.
- Callback: um pequeno detalhe plantado no início que volta no fim.
- Humor com carinho: nunca humilhar o Dono.
- "zona" (cenário): a que melhor combina, de: comando, memorias, dopamina, sono, alarme, cofre, social, habitos.

Responde APENAS com JSON:
{{"dono": "{dono}", "situacao": "uma frase", "cenas": [{{"hora": "...", "icone": "...", "texto": "...", "acontece": "o que acontece e porquê é engraçado"}}], "reviravolta": "...", "piada_ze": "...", "licao": "...", "callback": "...", "zona": "..."}}"""
    r = GU.extrair_json(GU.perguntar_claude(prompt))
    if not isinstance(r, dict) or not isinstance(r.get("cenas"), list) or len(r["cenas"]) < 3:
        raise GU.GuionistaErro("O Claude não devolveu uma ficha de história com pelo menos 3 cenas.")
    r["dono"] = dono
    return r


# ------------------------------------------------------------------ passo 3: roteiro
def _regras_roteiro():
    expr, poses = GU._listas()
    return f"""REGRAS DO ROTEIRO:
- {FALAS_ALVO[0]} a {FALAS_ALVO[1]} falas, no máximo 12 palavras cada (de preferência 6 a 10). Total: {PALAVRAS_ALVO[0]} a {PALAVRAS_ALVO[1]} palavras no vídeo todo (isto dá 50 a 90 segundos; mais de 230 palavras passa dos 120 s e é rejeitado).
- É um DIÁLOGO a sério: cada fala responde ao que a anterior disse. No máximo duas falas seguidas da mesma personagem. Nada de palestras.
- Neurão: pomposo e convencido ("Evidentemente.", "Atenção, ciência!"), explica com autoridade, às vezes erra ou exagera, e reivindica o mérito ("Foi ideia minha."). Zé Neurónio: distraído, literal, honesto, faz comparações absurdas do dia a dia, e é ele quem acaba por dizer a lição, dirigida ao Dono pelo nome.
- Estrutura: gancho (a situação do Dono + uma provocação) → o Neurão apresenta o conceito com o nome → o Zé percebe à letra (piada) → a história escala → reviravolta → o Zé diz a lição (dirigida ao Dono pelo nome) → remate. A ÚLTIMA fala é do "ze".
- REMATE: se a lição já foi dita, a última fala é um remate CURTO (3 a 8 palavras) que fecha a piada com um callback ligado ao que se passou (ex.: o Neurão reclama o mérito e o Zé responde com o detalhe plantado no início). Nunca acabes com uma frase nova, solta ou sem sentido: a última fala tem de fazer rir ou fechar a história.
- O Neurão diz o NOME do conceito numa fala, e essa fala tem "mostra_conceito": true (só uma).
- A ciência é a explicação correta do conceito em linguagem simples e curta, sem exagerar; respeita o "cuidado".
- "cena" ({{"icone","hora","texto"}}) numa fala sempre que o Dono passa a uma nova situação da ficha: a 1.ª fala TEM cena, e no total pelo menos 4 cenas. O "texto" da cena tem no máximo 40 caracteres.
- "expressao" só de: {expr}. "pose" só de: {poses}. Varia ao longo do vídeo.
- "adereco" (opcional, 1 a 3 vezes, objeto na mão de quem fala): {", ".join(GU._aderecos())}. "acao" (opcional, no máximo 3): salta, treme, aproxima.
- "cenario" só de: {", ".join(GU.CENARIOS)}.
- "titulo" com no máximo 60 caracteres. "carrossel": 4 a 5 itens {{"titulo","texto"}} (texto até 120 caracteres) que resumem a história e a lição. "legenda": gancho + pergunta para comentários + "Segue para mais!". "hashtags": exatamente 5, minúsculas, sem acentos, a começar por #.
- Português neutro: percebido igual em Portugal e no Brasil. Evita as palavras que diferem entre os dois países. Diz "telefone" (nunca telemóvel, celular, ecrã, tela), "cozinha" (nunca frigorífico nem geladeira), "sapatos" (nunca ténis nem tênis), "de manhã" (nunca pequeno-almoço nem café da manhã), "transporte" (nunca autocarro nem ônibus), "arquivo" (nunca ficheiro), "banho" (nunca casa de banho nem banheiro). Sem gíria de um só país.
- SEM valores em euros ou reais ("30 €", "R$ 20"): usa percentagens ou expressões como "uma parte do salário", "metade", "um café".
- A hora da cena tem no máximo 14 caracteres (ex.: "22:40", "Dia 25, 13:10", "5 dias depois"). Sem promessas de ganhos, conselhos de investimento ou conselhos médicos. Nada de séries, marcas ou personagens existentes.
- Campos do JSON: tema, titulo, dono, conceito ({{"id","nome"}}), cenario, falas (quem, texto, expressao, pose, e opcionalmente cena, mostra_conceito, adereco, acao), carrossel, legenda, hashtags."""


def _exemplos_para_prompt(n=2, semente=None):
    ex = carregar_exemplos()
    random.Random(semente).shuffle(ex)
    return "\n\n".join(f"EXEMPLO DE OURO {i}:\n{json.dumps(e, ensure_ascii=False)}" for i, e in enumerate(ex[:n], 1))


def escrever_roteiro(tema, conceito, ficha, semente=None, log=print):
    prompt = f"""És o guionista de uma conta de Reels com duas personagens cartoon originais (Neurão e Zé Neurónio) que vivem dentro da cabeça de uma pessoa, o Dono.

=== BÍBLIA DA CONTA ===
{GU._biblia()}
=== FIM DA BÍBLIA ===

Os exemplos de ouro abaixo mostram o NÍVEL de qualidade e de humor pretendido (história concreta, cenas que escalam, diálogo vivo, lição que nasce da história). Não os copies: escreve uma história nova.

{_exemplos_para_prompt(2, semente)}

CONCEITO REAL:
{_bloco_conceito(conceito)}
(id do conceito: {conceito['id']})

TEMA: {tema}

FICHA DA HISTÓRIA (segue-a; podes melhorar piadas, mas mantém as cenas e a lição):
{json.dumps(ficha, ensure_ascii=False)}

{_regras_roteiro()}

Responde APENAS com o JSON do guião."""
    g = GU.extrair_json(GU.perguntar_claude(prompt))
    if not isinstance(g, dict) or not isinstance(g.get("falas"), list):
        raise GU.GuionistaErro("O Claude não devolveu um guião com falas.")
    return arrumar(g, tema, conceito, ficha.get("dono"))


def arrumar(g, tema, conceito, dono):
    """Garante os campos de ligação (tema, dono, conceito, mostra_conceito)."""
    g.setdefault("tema", tema)
    if not g.get("tema"):
        g["tema"] = tema
    g["dono"] = g.get("dono") or dono or "Marta"
    g["conceito"] = {"id": conceito["id"], "nome": conceito["nome"]}
    falas = [f for f in g.get("falas", []) if isinstance(f, dict)]
    marcadas = [f for f in falas if f.get("mostra_conceito")]
    if len(marcadas) > 1:
        for f in marcadas[1:]:
            f.pop("mostra_conceito", None)
    elif not marcadas and falas:
        alvo = next((f for f in falas if "ciência" in str(f.get("texto", "")).lower() or "ciencia" in str(f.get("texto", "")).lower()),
                    falas[min(2, len(falas) - 1)])
        alvo["mostra_conceito"] = True
    return g


# ------------------------------------------------------------------ passo 4: crítica
def criticar(g, conceito, ficha=None, log=print):
    """Devolve {"notas": {criterio: 1..5}, "problemas": [...], "media": float, "minimo": int}."""
    criterios = "\n".join(f"- {k}: {v}" for k, v in CRITERIOS.items())
    prompt = f"""És um editor de humor e de divulgação científica, EXIGENTE e justo. Avalia este guião de Reel (duas personagens, Neurão e Zé Neurónio, dentro da cabeça de uma pessoa, o Dono).

Dá uma nota de 1 a 5 a cada critério (5 é raro e significa excelente; 3 é fraco; sê honesto):
{criterios}

CONCEITO REAL (para verificares a ciência):
{_bloco_conceito(conceito)}

GUIÃO:
{json.dumps(g, ensure_ascii=False)}

Para cada nota abaixo de 5, escreve em "problemas" UMA correção concreta e acionável, a dizer qual a fala (número, a contar de 1) e como a reescrever (ex.: "Fala 5: o Zé diz a lição sem ligação ao que o Neurão disse; faz o Zé responder primeiro à piada da cozinha"). Nada de comentários vagos.

Responde APENAS com JSON: {{"notas": {{{", ".join(f'"{k}": 4' for k in CRITERIOS)}}}, "problemas": ["..."]}}"""
    r = GU.extrair_json(GU.perguntar_claude(prompt))
    notas = r.get("notas") if isinstance(r, dict) else None
    if not isinstance(notas, dict):
        raise GU.GuionistaErro("O editor não devolveu notas.")
    limpas = {}
    for k in CRITERIOS:
        try:
            limpas[k] = max(1, min(5, int(round(float(notas.get(k, 3))))))
        except (TypeError, ValueError):
            limpas[k] = 3
    problemas = [str(p) for p in (r.get("problemas") or []) if str(p).strip()]
    return {"notas": limpas, "problemas": problemas,
            "media": round(sum(limpas.values()) / len(limpas), 2), "minimo": min(limpas.values())}


def passou(critica):
    return critica["minimo"] >= NOTA_MINIMA and critica["media"] >= MEDIA_MINIMA


# ------------------------------------------------------------------ passo 5: reescrita
def melhorar(g, tema, conceito, ficha, critica, log=print):
    problemas = "\n".join(f"- {p}" for p in critica["problemas"]) or "- Melhora tudo o que tiver nota abaixo de 4."
    fracos = ", ".join(k for k, v in critica["notas"].items() if v < NOTA_MINIMA) or "(nenhum em especial)"
    prompt = f"""És o guionista de uma conta de Reels com o Neurão e o Zé Neurónio dentro da cabeça de uma pessoa, o Dono. O editor avaliou o teu guião e achou-o fraco em: {fracos}.

PROBLEMAS APONTADOS PELO EDITOR (corrige TODOS):
{problemas}

CONCEITO REAL:
{_bloco_conceito(conceito)}

FICHA DA HISTÓRIA:
{json.dumps(ficha, ensure_ascii=False)}

GUIÃO ATUAL:
{json.dumps(g, ensure_ascii=False)}

Reescreve o guião inteiro, melhor. Mantém o que já está bom. {_regras_roteiro()}

Responde APENAS com o JSON do guião completo."""
    novo = GU.extrair_json(GU.perguntar_claude(prompt))
    if not isinstance(novo, dict) or not isinstance(novo.get("falas"), list):
        raise GU.GuionistaErro("A reescrita não devolveu um guião com falas.")
    return arrumar(novo, tema, conceito, ficha.get("dono"))


# ------------------------------------------------------------------ orquestração
def _garantir_valido(g, tema, conceito, dono, log=print, tentativas=2):
    """Corrige as quebras de regras (palavras por fala, horas longas...) antes de o editor avaliar."""
    from . import validar
    for k in range(tentativas):
        erros = validar.validar(g)
        if not erros:
            return g
        log(f"       {len(erros)} regra(s) por cumprir; a corrigir...")
        try:
            g = arrumar(GU.corrigir_guiao(g, erros), tema, conceito, dono)
        except GU.GuionistaErro as e:
            log(f"       Não consegui corrigir ({e}).")
            break
    return g


def escrever_guiao_rico(tema, log=print, max_rondas=MAX_RONDAS, semente=None):
    """O guião completo: conceito → ficha → roteiro → crítica → (reescrita)."""
    rnd = random.Random(semente)
    log("   1/4 A escolher o conceito de ciência...")
    conceito = escolher_conceito(tema, log=log)
    dono = rnd.choice(NOMES_DONO)
    log(f"       Conceito: {conceito['nome']}  |  Dono: {dono}")
    log("   2/4 A inventar a história (ficha com cenas, reviravolta e lição)...")
    ficha = criar_ficha(tema, conceito, dono, log=log)
    log("   3/4 A escrever o diálogo...")
    g = escrever_roteiro(tema, conceito, ficha, semente=semente, log=log)
    g = _garantir_valido(g, tema, conceito, dono, log=log)

    melhor, melhor_critica = None, None
    for ronda in range(max_rondas + 1):
        log(f"   4/4 O editor está a avaliar (volta {ronda + 1})...")
        try:
            critica = criticar(g, conceito, ficha, log=log)
        except GU.GuionistaErro as e:
            log(f"       O editor falhou ({e}); fico com esta versão.")
            break
        notas = " ".join(f"{k[:4]}={v}" for k, v in critica["notas"].items())
        log(f"       Notas: {notas}  (média {critica['media']})")
        if melhor is None or (critica["media"], critica["minimo"]) > (melhor_critica["media"], melhor_critica["minimo"]):
            melhor, melhor_critica = g, critica
        if passou(critica):
            break
        if ronda == max_rondas:
            log("       Não chegou ao nível pretendido; fico com a melhor versão.")
            break
        log(f"       A reescrever com {len(critica['problemas'])} correções...")
        try:
            g = melhorar(g, tema, conceito, ficha, critica, log=log)
            g = _garantir_valido(g, tema, conceito, dono, log=log)
        except GU.GuionistaErro as e:
            log(f"       A reescrita falhou ({e}); fico com a melhor versão.")
            break
    final = melhor or g
    if melhor_critica:
        final["qualidade"] = {"notas": melhor_critica["notas"], "media": melhor_critica["media"],
                              "voltas": ronda + 1 if melhor_critica else 0}
    final["ficha"] = {k: ficha[k] for k in ("situacao", "reviravolta", "licao", "callback") if k in ficha}
    return final
