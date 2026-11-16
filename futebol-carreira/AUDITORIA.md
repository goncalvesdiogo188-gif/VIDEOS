# Auditoria dos ecrãs

Percurso automático (`AuditoriaEcrasTest`) por todos os ecrãs principais, num telemóvel pequeno (360x640 dp) e num grande (411x914 dp), sempre com a letra a 130 %, mais o HUD do jogo 3D na horizontal. As imagens ficam em `app/build/outputs/roborazzi/auditoria-*.png`. A lista de baixo (entre os marcadores) é reescrita pelo teste; esta parte de cima é escrita à mão.

## Já corrigido (ecrãs que ninguém está a redesenhar)
- **Menu principal**: não deslizava; com letra grande o botão «Relatar problema» (e os de baixo) ficavam fora do ecrã. Agora desliza.
- **Mais → Opções**: lista sem scroll; os três botões «Simular / Ver em 3D / Jogar em 3D» cortavam o texto, agora estão empilhados com 48 dp; o interruptor novo «Mostrar medidor» tem 48 dp.
- **Mais (separadores Clube/Finanças/Jogos/Seleções/Opções)**, **Seleções**, **Carreira do jogador (Perfil/Percurso/Propostas/Prémios)**: as linhas de separadores cortavam «Finanças», «Seleções», «Propostas»; passaram a deslizar (`ScrollableTabRow`).
- **Jogador → Propostas**: «Pedir transferência» / «Pedir empréstimo» cortados; agora um por linha, 48 dp.
- **Seleções**: «Ver jogos» com 44 dp passou a 48 dp.
- **Editor**: botão «Nacionalidade: …» cortava; deixou de ter limite de uma linha.
- **Jogo em direto (relato)**: nomes de equipas longos («Moreirense FC») partiam a palavra; letra do nome 14 sp e marcador 28 sp.

## Por fazer (fica para quem redesenha esses ecrãs ou para mais tarde)
- **Barra de navegação** (agente do Tema): «Mercado» aparece como «Mercad» com letra grande; o texto tem de caber em 360 dp.
- **Cabeçalho do clube**: «Época 2026/27 · qua, 1 jul 2026» corta com letra grande em ecrã pequeno (uma linha só).
- **Liga** (separadores «Jogadores», «Histórico» cortados em 360 dp; mesmo problema em `EcraCompeticoes`: usar `ScrollableTabRow`).
- **Mercado**: colunas «Ger» e «MOC» cortadas.
- **Plantel**: chip «Ofensiva» e filtro de posição com alvo de toque estreito (28 dp de largura).
- **Convocados (Seleções)**: chips de país com 38 dp de largura.
- **Editor**: chips «MDC» com 33 dp de largura; coluna «Nota» no percurso do jogador (40 dp) corta.
- **Falsos positivos conhecidos**: elementos meio escondidos pelo scroll aparecem como «alvo de toque pequeno» (por exemplo «Simular o resto da época 387x32»): o botão tem 54 dp, só se vê uma parte.
- **O diálogo «Relatar problema»** (tem caixa de texto) não é percorrido pelo teste: no Robolectric o Compose nunca fica «parado» com uma caixa de texto num diálogo. Falta confirmar à mão no telemóvel (abre, guarda, partilha).
- **O que o teste não vê**: o desenho OpenGL (no teste é um marcador), vibração, rotação do ecrã e desempenho real.

<!-- AUTO:INICIO -->
_Gerado por `AuditoriaEcrasTest` (`./gradlew :app:testDebugUnitTest --tests '*Auditoria*'`). Não editar à mão: é reescrito de cada vez que o teste corre._

### Telemóvel grande (411x914 dp, letra 130 %) (464 alvos de toque e 1495 textos verificados)

- **inicio**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **plantel**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **mercado**
  - texto cortado: «Ger»
  - texto cortado: «417.9 M€»
  - texto cortado: «453.3 M€»
  - texto cortado: «354.2 M€»
  - texto cortado: «194.8 M€»
  - texto cortado: «MOC»
  - texto cortado: «336.2 M€»
  - texto cortado: «300.1 M€»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **liga-tabela**
  - texto cortado: «Jogadores»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **liga-jogos**
  - texto cortado: «Jogadores»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **liga-jogadores**
  - texto cortado: «Jogadores»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **liga-historico**
  - texto cortado: «Jogadores»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **mais-clube**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **mais-financas**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **mais-calendario**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **mais-selecoes**
  - alvo de toque pequeno: 98x27 dp: «Ver jogos»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **mais-convocados**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **mais-opcoes**
  - alvo de toque pequeno: 387x32 dp: «Simular o resto da época»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **ficha-jogador**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **jogo-em-direto**
  - texto cortado: «Época 2026/27 · sáb, 8 ago 2026»
- **jogo-relato**
  - texto cortado: «Época 2026/27 · sáb, 8 ago 2026»
- **jogo-estatisticas**
  - texto cortado: «Época 2026/27 · sáb, 8 ago 2026»
- **jogo-notas**
  - texto cortado: «Época 2026/27 · sáb, 8 ago 2026»
- **editor-jogador**
  - texto cortado: «Guarda-redes»
  - alvo de toque pequeno: 387x45 dp: «Clube: Real Madrid»
- **jogador-inicio**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **jogador-inicio-baixo**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **jogador-perfil**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **jogador-percurso**
  - texto cortado: «Nota»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **jogador-propostas**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **jogador-premios**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»

### Telemóvel pequeno (360x640 dp, letra 130 %) (394 alvos de toque e 1108 textos verificados)

- **inicio**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
  - texto cortado: «Mercado»
- **plantel**
  - alvo de toque pequeno: 28x48 dp: «Ofensiva»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
  - texto cortado: «Mercado»
- **mercado**
  - texto cortado: «Ger»
  - texto cortado: «417.9 M€»
  - texto cortado: «453.3 M€»
  - texto cortado: «354.2 M€»
  - texto cortado: «194.8 M€»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
  - texto cortado: «Mercado»
- **liga-tabela**
  - texto cortado: «Jogadores»
  - texto cortado: «Histórico»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
  - texto cortado: «Mercado»
- **liga-jogos**
  - texto cortado: «Jogadores»
  - texto cortado: «Histórico»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
  - texto cortado: «Mercado»
- **liga-jogadores**
  - texto cortado: «Jogadores»
  - texto cortado: «Histórico»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
  - texto cortado: «Mercado»
- **liga-historico**
  - texto cortado: «Jogadores»
  - texto cortado: «Histórico»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
  - texto cortado: «Mercado»
- **mais-clube**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
  - texto cortado: «Mercado»
- **mais-financas**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
  - texto cortado: «Mercado»
- **mais-calendario**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
  - texto cortado: «Mercado»
- **mais-selecoes**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
  - texto cortado: «Mercado»
- **mais-convocados**
  - alvo de toque pequeno: 38x48 dp: «Espanha»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
  - texto cortado: «Mercado»
- **mais-opcoes**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
  - texto cortado: «Mercado»
- **ficha-jogador**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
  - texto cortado: «Mercado»
- **jogo-em-direto**
  - texto cortado: «Época 2026/27 · sáb, 8 ago 2026»
  - texto cortado: «Mercado»
- **jogo-relato**
  - texto cortado: «Época 2026/27 · sáb, 8 ago 2026»
  - texto cortado: «Mercado»
- **jogo-estatisticas**
  - texto cortado: «Época 2026/27 · sáb, 8 ago 2026»
  - texto cortado: «Mercado»
- **jogo-notas**
  - texto cortado: «Época 2026/27 · sáb, 8 ago 2026»
  - texto cortado: «Mercado»
- **editor-clubes**
  - alvo de toque pequeno: 336x32 dp: «B04 Bayer Leverkusen Bundesliga · 27 jog»
- **editor-jogadores**
  - texto cortado: «Clube: Todos os clubes»
  - alvo de toque pequeno: 33x48 dp: «MDC»
- **editor-clube**
  - texto cortado: «Abreviatura»
- **jogador-inicio**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **jogador-inicio-baixo**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **jogador-perfil**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **jogador-percurso**
  - texto cortado: «Nota»
  - texto cortado: «FC Alverca»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **jogador-propostas**
  - alvo de toque pequeno: 308x32 dp: «Pedir empréstimo»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **jogador-premios**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»

### Jogo 3D (640x360 dp na horizontal, letra 130 %) (6 alvos de toque e 39 textos verificados)

Nada a assinalar.

<!-- AUTO:FIM -->
