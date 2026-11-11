# Pinky & The Brain — *O Plano Perfeito*

Curta-metragem de animação 2D (≈90 s) de sátira política, gerada **inteiramente
por código**: personagens vetoriais com rig, expressões faciais independentes,
sincronização labial automática, vozes neurais em português de Portugal, música
e efeitos sonoros originais sintetizados, legendas e exportação MP4.

| Entregável | Ficheiro |
|---|---|
| Vídeo final 16:9 (1920×1080, 24 fps, H.264 + AAC) | `output/plano_perfeito_16x9.mp4` |
| Versão vertical 9:16 (1080×1920) — Reels / TikTok / Shorts | `output/plano_perfeito_9x16.mp4` |
| Legendas pt-PT (também gravadas no vídeo) | `output/legendas_pt-PT.srt` |

---

## 1. Análise e abordagem técnica

O repositório estava vazio, por isso a escolha das ferramentas partiu do que
existe no ambiente: **Python 3 + Cairo** (desenho vetorial com antialiasing),
**NumPy/SciPy** (síntese e mistura de áudio), **FFmpeg/libx264** (codificação) e
**edge-tts** (vozes neurais `pt-PT`).

Por que gerar tudo por código em vez de usar imagens estáticas:

* **Personagens como rigs**, não desenhos fixos: cada parte (orelhas, olhos,
  pálpebras, sobrancelhas, pupilas, boca, braços, pernas, cauda) é um parâmetro
  animável, o que permite atuação real (gestos, olhares, reações).
* **Tudo sincronizado com a voz**: os tempos de cada cena derivam da duração
  real de cada fala; mudar uma fala reorganiza o filme automaticamente.
* **Duas versões do mesmo filme**: o cenário existe num "mundo" maior do que o
  ecrã e cada plano tem uma câmara para 16:9 e outra para 9:16, por isso a
  versão vertical é **reenquadrada**, não cortada às cegas.
* **Sem recursos protegidos**: personagens desenhados de raiz, música e efeitos
  sintetizados com osciladores e ruído — nenhum excerto, sample ou banda sonora
  da série original.

## 2. Estrutura do projeto

```
render.py            CLI: vozes → legendas → mistura de áudio → vídeo
requirements.txt
pab/
  script.py          ★ O GUIÃO: falas, texto das legendas, pronúncia e prosódia
  episode.py         ★ A REALIZAÇÃO: encenação, atuação, câmaras, som, música
  characters.py      Rigs do Brain e do Pinky (expressões, braços com IK, boca)
  lab.py             Cenário: laboratório, mapa-mundo, ecrãs animados, quadro
  compositor.py      Composição do frame: câmara, luz, legendas, títulos, fades
  voices.py          TTS com cache + análise de áudio para lip-sync
  audio.py           Sintetizador (instrumentos, efeitos) e mistura final
  anim.py            Motor de keyframes e easing
  draw.py            Utilitários de desenho (contornos, cel-shading)
  fonts.py           Registo das fontes incluídas
assets/
  fonts/             Bangers e Sniglet (licença SIL OFL, incluída)
  voices/            Vozes geradas (cache .mp3 + tempos de palavra .json)
output/              Vídeos, legendas
```

## 3. Como foi construído

**Animação.** `anim.Timeline` guarda faixas de keyframes (com *easing*: `inout`,
`back` para "snap" de cartoon, `elastic`, …). O `Director` em `episode.py` lê-se
como um guião técnico: `d.say("b02")` coloca a fala, `d.act("brain", t, 0.3,
arm_f=..., smile=0.6)` anima pose e expressão, `d.cam("brain_cu", t, 1.8)` move a
câmara, `d.sfx(...)`/`d.music(...)` marcam som.

**Expressões independentes.** Por personagem: `brow_l`, `brow_r`, `brow_tilt`,
`lid` (pálpebra superior), `lid_low`, `blink`, `look` (direção do olhar),
`pupil` (tamanho — o "cérebro bloqueado" do final usa pupilas minúsculas),
`smile`, `smirk`, `jaw`, `head_tilt`, `head_turn`, `sweat`, `twitch`, `squash`.
Piscar de olhos é gerado automaticamente (e suprimido no olhar vazio final).

**Lip-sync.** Para cada fala, `voices.Clip.mouth_curves()` mede a energia (RMS)
e o centróide espectral a cada frame: a energia abre a boca, o centróide decide
entre vogais abertas/redondas e sibilantes (boca mais fechada e larga). Os tempos
de cada palavra (fornecidos pelo TTS) alinham as legendas.

**Câmara e montagem.** Planos gerais, dois-planos, médios, grandes planos,
inserts do mapa e dos monitores, *push-ins* lentos, *punch-in* no clímax,
tremor de câmara, *letterbox* dramático, *flash* de relâmpago, corte seco para
preto e cartão final.

**Som.** Tema original "conspirativo" em Ré menor (pizzicato + fagote/clarinete
+ escovas + glockenspiel), introdução com rufo de tímpanos e acorde de metais,
*drone* sinistro, golpe orquestral + trovão em "ENTRAREI EM AÇÃO!", *sting*
"dun-dun-DUUUN" na pergunta decisiva, **silêncio absoluto** antes da reação do
Brain, trombone triste cómico quando ele percebe a contradição, e *outro* suave.
Efeitos: passos, *swishes* dos gestos, toques do ponteiro no mapa, coçar de
cabeça, estática dos ecrãs a morrer, bolhas do frasco. A música é *ducked*
automaticamente sob o diálogo; a mistura é normalizada a −14 LUFS (redes sociais).

## 4. Instalar

```bash
pip install -r requirements.txt
```

Precisa também de `ffmpeg` com `libx264` no PATH. As vozes já estão em
`assets/voices/`, por isso **re-renderizar não precisa de internet** — só é
preciso ligação quando se altera uma fala (o TTS é refeito apenas para essa).

## 5. Renderizar

```bash
python3 render.py
```

Gera as duas versões finais (≈5–8 min num portátil de 4 núcleos).

```bash
python3 render.py --preview
```

Rascunho rápido a meia resolução (≈1 min por versão).

```bash
python3 render.py --format vertical
```

Só uma das versões (`horizontal` ou `vertical`).

```bash
python3 render.py --still 12.5 55 84
```

Imagens PNG nesses segundos, em `output/stills/` — ideal para afinar poses.

```bash
python3 render.py --preview --start 50 --end 60
```

Renderiza apenas um intervalo de tempo.

## 6. Editar

* **Mudar uma fala ou a pronúncia** → `pab/script.py`. `text` é o que aparece na
  legenda; `tts` é a grafia enviada à voz (ex.: "Brêin" para soar "Brain").
  `rate`/`pitch`/`volume` ajustam a entrega de cada fala. Toda a montagem se
  reajusta à nova duração.
* **Mudar as vozes** → `VOICES` em `pab/script.py` (qualquer voz do edge-tts;
  `edge-tts --list-voices | grep pt-PT`).
* **Mudar a atuação, câmaras, tempos, música ou efeitos** → `pab/episode.py`.
  Poses de braços estão em `BR`/`PK`; enquadramentos em `CAM` (cada um tem a
  versão 16:9 e a 9:16).
* **Mudar o aspeto das personagens** → `pab/characters.py`.
* **Mudar o cenário, manchetes, mapa, monitores** → `pab/lab.py`.
* **Mudar o texto final** → `END_TEXT` em `pab/script.py`.
* **Legendas** (tamanho, cor por personagem, posição) → `pab/compositor.py`.

## 7. Nota sobre direitos

Todos os recursos deste projeto (desenhos, música, efeitos, código) são
originais; as fontes são SIL OFL. Os **personagens** Pinky e Brain e o nome da
série pertencem, porém, à Warner Bros. Uma paródia/sátira pode ser aceite, mas
plataformas como YouTube, TikTok e Instagram podem aplicar reclamações de
direitos de autor ou limitar a monetização. Se o objetivo for monetizar, vale a
pena considerar renomear e redesenhar ligeiramente os dois ratos — o motor de
animação, o guião e a realização funcionam igualmente.

A sátira não usa imagens nem nomes de políticos reais: incide sobre o
funcionamento do poder em abstrato (cimeiras sem acordo, comissões que estudam
comissões, promessas recicladas).
