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

### Telemóvel grande (411x914 dp, letra 130 %) (475 alvos de toque e 1382 textos verificados)

- **nova-carreira-jogador**
  - alvo de toque pequeno: 145x44 dp: «Liga Portugal»
  - alvo de toque pequeno: 160x44 dp: «Liga Portugal 2»
- **plantel**
  - texto cortado: «Mosquera»
  - texto cortado: «Jakupović»
- **mercado**
  - alvo de toque pequeno: 43x48 dp: «30 ou mais»
- **liga-tabela**
  - texto cortado: «Jogadores»
  - alvo de toque pequeno: 411x35 dp: «13 CD Nacional 0 0 0 0 0 0»
- **liga-jogos**
  - texto cortado: «Jogadores»
- **liga-jogadores**
  - texto cortado: «Jogadores»
- **liga-historico**
  - texto cortado: «Jogadores»
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
- **jogador-percurso**
  - texto cortado: «Nota»

### Telemóvel pequeno (360x640 dp, letra 130 %) (390 alvos de toque e 963 textos verificados)

- **menu**
  - alvo de toque pequeno: 146x31 dp: «Relatar problema»
- **inicio**
  - alvo de toque pequeno: 162x36 dp: «CLASSIFICAÇÃO 5.º lugar 0 pontos · Liga »
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **plantel**
  - alvo de toque pequeno: 28x48 dp: «Ofensiva»
  - texto cortado: «Jakupović»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **mercado**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **liga-tabela**
  - texto cortado: «Jogadores»
  - texto cortado: «Histórico»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **liga-jogos**
  - texto cortado: «Jogadores»
  - texto cortado: «Histórico»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **liga-jogadores**
  - texto cortado: «Jogadores»
  - texto cortado: «Histórico»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **liga-historico**
  - texto cortado: «Jogadores»
  - texto cortado: «Histórico»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **mais-clube**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **mais-financas**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **mais-calendario**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **mais-selecoes**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **mais-convocados**
  - alvo de toque pequeno: 38x48 dp: «Espanha»
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **mais-opcoes**
  - texto cortado: «Sistema»
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
- **menu-com-gravacao**
  - alvo de toque pequeno: 146x31 dp: «Relatar problema»
- **editor-jogadores**
  - texto cortado: «Clube: Todos os clubes»
  - alvo de toque pequeno: 33x48 dp: «MDC»
- **editor-clube**
  - texto cortado: «Abreviatura»
- **jogador-inicio**
  - alvo de toque pequeno: 162x36 dp: «CLASSIFICAÇÃO 18.º lugar 0 pontos · Liga»
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
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»
- **jogador-premios**
  - texto cortado: «Época 2026/27 · qua, 1 jul 2026»

### Jogo 3D (640x360 dp na horizontal, letra 130 %) (6 alvos de toque e 39 textos verificados)

Nada a assinalar.

<!-- AUTO:FIM -->
