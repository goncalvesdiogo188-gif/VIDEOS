"""Episódio: "O SENHORIO DO MUNDO" — sátira à crise da habitação.

Substitui o conteúdo de pab/script.py (mesma estrutura do episódio de referência).
Satiriza sistemas e comportamentos; não há políticos nem pessoas reais nomeados.
"""

EPISODE = dict(
    title="O SENHORIO DO MUNDO",
    slug="senhorio_do_mundo",
)

SET = dict(
    map_title="OPERAÇÃO: SENHORIO",
    board_title="O PLANO PERFEITO",
    notes=["1. COMPRAR TUDO", "2. SUBIR A RENDA", "3. DOMINAR!"],
    chart_title="RENDAS vs ORDENADOS",
    clippings=[("RENDAS SOBEM", "OUTRA VEZ"),
               ("T0 DE 14 M²", "POR 1.100 €"),
               ("80 CANDIDATOS", "PARA 1 QUARTO")],
    news_headline=("CRISE", "HABITAÇÃO"),
    ticker=("ÚLTIMA HORA: RENDA MÉDIA SOBE PELO 37.º MÊS SEGUIDO  •  JOVENS PROCURAM CASA "
            "COM VISTA PARA OUTRA CASA  •  ARMÁRIO COM JANELA CLASSIFICADO COMO T1  •  "
            "GOVERNO PROMETE ESTUDAR O ASSUNTO  •  "),
    economy_title="MERCADO IMOBILIÁRIO",
    alert_title="ALERTA HABITACIONAL",
    alert_bars=["RENDAS", "CAUÇÕES", "FIADORES", "CASAS"],   # a última é a barra "boa"
    month="NOVEMBRO",
)

VOICES = {
    "BRAIN": dict(voice="pt-PT-DuarteNeural", rate="-9%", pitch="-14Hz", volume="+0%"),
    "PINKY": dict(voice="pt-PT-DuarteNeural", rate="+6%", pitch="+42Hz", volume="+0%"),
}

LINES = [
    # CENA 1 — O NOVO PLANO
    dict(id="p01", who="PINKY", text="Brain, o que vamos fazer esta noite?",
         tts="Brêin, o que vamos fazer esta noite?"),
    dict(id="b01", who="BRAIN", text="O mesmo que fazemos todas as noites, Pinky.",
         tts="O mesmo que fazemos todas as noites, Pínqui."),
    dict(id="b02", who="BRAIN", text="Tentar conquistar o mundo!", rate="-4%", pitch="-10Hz"),
    dict(id="b03", who="BRAIN", text="Mas hoje descobri o negócio mais poderoso do planeta."),
    dict(id="p02", who="PINKY", text="Narf! É queijo?", tts="Narf! É queijo?"),
    dict(id="b04", who="BRAIN", text="Pior, Pinky. É imobiliário.", tts="Pior, Pínqui. É imobiliário.",
         rate="-14%"),
    # CENA 2 — A ESTRATÉGIA
    dict(id="b05", who="BRAIN",
         text="Vou comprar todas as casas do mundo e arrendá-las a preços absolutamente razoáveis."),
    dict(id="p03", who="PINKY", text="E quanto é razoável, Brain?", tts="E quanto é razoável, Brêin?"),
    dict(id="b06", who="BRAIN",
         text="Mil e cem euros por catorze metros quadrados. Cozinha, sala e quarto. Ao mesmo tempo.",
         rate="-4%"),
    dict(id="p04", who="PINKY", text="E a casa de banho?"),
    dict(id="b07", who="BRAIN", text="Serviço opcional, Pinky. Mediante subscrição.",
         tts="Serviço opcional, Pínqui. Mediante subscrição.", rate="-10%"),
    dict(id="b08", who="BRAIN",
         text="Caução de três rendas, fiador, e uma visita com oitenta candidatos ao mesmo armário."),
    dict(id="b09", who="BRAIN",
         text="Quando toda a gente depender de mim para ter um teto, o mundo será meu.", rate="-4%"),
    dict(id="b10", who="BRAIN", text="E quando o último inquilino pagar a última renda...",
         rate="-14%", pitch="-18Hz"),
    dict(id="b11", who="BRAIN", text="SERÁ MEU!", tts="Será... meu!", rate="-6%", pitch="-6Hz",
         volume="+20%"),
    # CENA 3 — A PERGUNTA DE PINKY
    dict(id="p05", who="PINKY", text="Mas Brain...?", tts="Mas, Brêin...?", rate="-6%"),
    dict(id="b12", who="BRAIN", text="O que foi agora, Pinky?", tts="O que foi agora, Pínqui?",
         rate="-2%"),
    dict(id="p06", who="PINKY",
         text="Se as rendas forem tão altas que ninguém consiga pagar...", tts="Se as rendas forem tão altas que ninguém consiga pagar..."),
    dict(id="p07", who="PINKY", text="...quem é que vai viver nas casas todas?",
         tts="Quem é que vai viver nas casas todas?"),
    dict(id="p08", who="PINKY",
         text="Quer dizer... tu vais ser dono do mundo inteiro, mas sem ninguém lá dentro?"),
    # CENA 4 — O FINAL
    dict(id="p09", who="PINKY", text="Ao menos tens vista, Brain.", tts="Ao menos tens vista, Brêin.",
         rate="-4%"),
]

END_TEXT = "Conquistar o mundo é fácil. Difícil é arranjar casa."
