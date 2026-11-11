"""The dialogue.  Edit text here, then re-render (changed lines are re-voiced).

Fields:
  id       unique key (also the cached audio file name)
  who      "BRAIN" or "PINKY"
  text     what appears in the subtitles (português de Portugal)
  tts      optional spelling used only for the synthetic voice (pronunciation)
  rate / pitch / volume   optional per-line prosody overrides for the voice
"""

VOICES = {
    # Same neural pt-PT voice, two very different prosodies.
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
    dict(id="b03", who="BRAIN", text="Mas desta vez tenho um plano diferente."),
    dict(id="b04", who="BRAIN", text="Um plano verdadeiramente genial.", rate="-16%"),
    dict(id="p02", who="PINKY", text="E qual é?"),
    # CENA 2 — A ESTRATÉGIA
    dict(id="b05", who="BRAIN", text="É muito simples, Pinky.", tts="É muito simples, Pínqui."),
    dict(id="b06", who="BRAIN",
         text="Desta vez não precisamos de invadir países, construir exércitos ou desenvolver armas secretas."),
    dict(id="p03", who="PINKY", text="Então vamos conquistar o mundo sem fazer nada?"),
    dict(id="b07", who="BRAIN", text="Finalmente estás a começar a compreender alguma coisa.", rate="-12%"),
    dict(id="b08", who="BRAIN",
         text="A estratégia é deixar os líderes mundiais continuarem exatamente como estão."),
    dict(id="b09", who="BRAIN",
         text="As suas decisões, os seus conflitos, as suas disputas pelo poder e a sua incapacidade "
              "de resolver os problemas vão fazer o trabalho por nós.", rate="-4%"),
    dict(id="b10", who="BRAIN",
         text="Quanto mais insistirem nas mesmas estratégias, mais o mundo se vai enfraquecer."),
    dict(id="b11", who="BRAIN",
         text="Economias fragilizadas, conflitos intermináveis, instabilidade e uma população "
              "cada vez mais cansada.", rate="-4%"),
    dict(id="b12", who="BRAIN", text="E quando o mundo estiver suficientemente enfraquecido...",
         rate="-14%", pitch="-18Hz"),
    dict(id="b13", who="BRAIN", text="ENTRAREI EM AÇÃO!", tts="Entrarei... em ação!",
         rate="-6%", pitch="-6Hz", volume="+20%"),
    # CENA 3 — A PERGUNTA DE PINKY
    dict(id="p04", who="PINKY", text="Mas Brain...?", tts="Mas, Brêin...?", rate="-6%"),
    dict(id="b14", who="BRAIN", text="O que foi agora, Pinky?", tts="O que foi agora, Pínqui?",
         rate="-2%"),
    dict(id="p05", who="PINKY",
         text="Se os líderes continuarem a fazer o seu trabalho tão bem que o mundo acabe por "
              "ficar completamente destruído...", rate="+0%"),
    dict(id="p06", who="PINKY",
         text="...quando chegares ao poder, não vais conquistar um mundo que já não funciona?",
         tts="Quando chegares ao poder, não vais conquistar um mundo que já não funciona?",
         rate="-2%"),
    dict(id="p07", who="PINKY",
         text="Quer dizer... se tudo ficar destruído, também não vais ficar a viver nesse mesmo mundo?"),
    # CENA 4 — O FINAL
    dict(id="p08", who="PINKY", text="Então... afinal também vais ficar sem mundo para conquistar?",
         rate="-4%"),
]

END_TEXT = "Às vezes, o maior obstáculo à conquista do mundo é o próprio mundo."
