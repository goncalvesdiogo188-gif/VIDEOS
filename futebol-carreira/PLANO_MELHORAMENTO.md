# Futebol Carreira 2.0 — plano de melhoramento

Escrito a 16 de novembro de 2026, depois de a versão 0.12 fechar o plano original.
O que pediste: **jogar o 3D a sério, animações 3D, um visual muito mais moderno, gráficos e texturas do campo, mais emoção fora de campo, mais profundidade na gestão, e corrigir o que não funciona.**
O que escolheste: estilo **estilizado e polido**; o jogo ao vivo passa para o **Godot**; tudo o resto fica na aplicação Android.

## 1. Como vai ficar por dentro

```
Aplicação Android (Kotlin + Compose)         Godot 4 (só o ecrã do jogo ao vivo)
────────────────────────────────────         ───────────────────────────────────
menus, carreira, gestão, notícias,           desenha o campo, o estádio, os
mercado, gravação, simulação                 jogadores animados e as câmaras
motor do jogo ao vivo (Kotlin, já testado) ─► recebe a posição de todos, 60×/s
recebe os comandos do toque               ◄─ envia o joystick e os botões
```

- **O Godot só desenha e lê o toque.** Quem decide o jogo continua a ser o motor em Kotlin, que já tem 16 testes. Assim não se perde nada do que está feito e o resultado do jogo entra na carreira como hoje.
- **Os menus ficam em Compose** (Material 3), mas redesenhados de raiz. O Godot não serve para menus de gestão, e não há razão para os refazer lá.
- **Contrapartida a saber:** com o Godot dentro, o APK passa de cerca de 2 MB para algo como 30 a 40 MB, e o arranque do ecrã 3D demora um pouco mais. Pode ser preciso limitar-nos aos telemóveis de 64 bits.
- **Plano B, se o Godot não encaixar** (ver a fase C): melhorar o motor OpenGL atual ou passar a um motor 3D do Android (Filament). A passagem fica decidida com números, na fase C.

## 2. As fases

Cada fase termina num APK que podes instalar. A ordem tem em conta o que depende do quê.

### Fase A — Fundações (primeiro, porque tudo assenta nisto)
- [ ] **A1. Auditoria do que não funciona.** Percorrer todos os ecrãs automaticamente, com imagens, e anotar erros, textos cortados, botões difíceis de tocar e coisas confusas. Mais um botão **«Relatar problema»** dentro do jogo que guarda uma nota com o estado do jogo e uma imagem, para me mandares sem escreveres muito.
- [ ] **A2. Medidor de desempenho** no 3D (imagens por segundo, memória), que se liga em Opções, para me dares números reais do teu telemóvel.
- [ ] **A3. Sistema de design:** cores, tipos de letra, formas, sombras e ícones definidos num só sítio.

### Fase B — Visual moderno dos menus (Compose)
- [ ] **B1. Sistema de design aplicado:** tipografia moderna (letra livre incluída na aplicação), ícones desenhados no lugar dos emojis da barra de baixo, modo escuro e claro, e cada clube dá a cor de destaque a todo o jogo.
- [ ] **B2. Ecrãs principais refeitos:**
  - **Início:** cartão grande do próximo jogo, forma recente (V/E/D), notícias e estado do clube.
  - **Plantel:** cartões de jogador ao estilo de cartas (nível, posição, forma, estado).
  - **Ficha de jogador:** gráfico radar dos atributos e evolução ao longo das épocas.
  - **Tática:** o campo desenhado, onde se arrastam os jogadores para os lugares.
  - **Mercado, Liga e Clube:** novos cartões, tabelas mais claras e filtros melhores.
- [ ] **B3. Animações e transições:** números que contam, cartões que se abrem, deslizar entre separadores, vibração suave nos toques importantes, ecrã de carregamento.
- [ ] **B4. Emblemas e camisolas melhores,** desenhados por programa a partir das cores de cada clube (formas e padrões), em vez do círculo com letras.

### Fase C — Godot: prova de viabilidade (decide se avançamos)
- [ ] **C0. Protótipo pequeno:** um ecrã Godot (campo, bola e um jogador) dentro da aplicação, com o Kotlin a mandar o estado e a receber o toque. Medir: arranque, imagens por segundo, tamanho do APK e se compila no GitHub.
- [ ] **Critério de decisão:** se compila no GitHub, arranca em menos de 3 s e mantém 30 imagens por segundo no teu telemóvel, **avançamos**. Se não, usamos o plano B.
- [ ] **C1. Compilação automática:** o projeto Godot fica na pasta `godot/` do repositório e o GitHub compila-o com a aplicação.
- [ ] **C2. A ponte Kotlin ↔ Godot:** formato do estado do jogo e dos comandos, testado com testes automáticos.

### Fase D — O mundo 3D, estilizado e polido (Godot)
- [ ] **D1. Campo:** relva com texturas e riscas de corte, desgaste junto às balizas, linhas, balizas com rede, bola com sombra.
- [ ] **D2. Estádio:** bancadas, público animado (levanta-se nos golos, agita bandeiras), holofotes, dia e noite, publicidade luminosa, céu.
- [ ] **D3. Jogadores:** modelo estilizado com esqueleto; camisola, calções e meias com as cores do clube e padrões; número e nome nas costas; variações de altura, cabelo e tom de pele; equipamento do guarda-redes.
- [ ] **D4. Animações:** parado, andar, correr, sprint, passe, remate, cabeceamento, carrinho, queda, mergulho do guarda-redes, lançamento, festejos de golo e reações; a cabeça acompanha a bola.
- [ ] **D5. Câmaras:** transmissão de televisão, tática, atrás da baliza e do jogador; corte automático nos golos e **repetição em câmara lenta**; marcador e informações estilo televisão.
- [ ] **D6. Efeitos e qualidade:** poeira e relva a voar, rasto da bola, luz suave; níveis **Baixo, Médio e Alto** para o telemóvel aguentar.

### Fase E — Jogar o 3D (motor em Kotlin)
- [ ] **E1. Regras:** fora de jogo, árbitro e vantagem, cartões, lançamentos, cantos e livres que se jogam (com mira), penáltis (mira e guarda-redes), prolongamento e penáltis de desempate **ao vivo**.
- [ ] **E2. Controlo:** botões configuráveis, passe dirigido com seta, passe em profundidade, cruzamento, remate colocado e por cima, finta, proteger a bola, e ajuda de mira conforme a dificuldade.
- [ ] **E3. Inteligência:** linha defensiva, marcação, pressão, saída de bola; **dificuldade Fácil, Normal, Difícil e Lendário** (hoje a defesa é fraca de mais).
- [ ] **E4. Táticas ao vivo:** pausar para mudar jogadores, formação e mentalidade; palestra ao intervalo; cansaço e lesões a contarem.
- [ ] **E5. Modos de jogo:** jogo completo, **só os momentos-chave** (jogas só os golos e as oportunidades, o resto passa depressa: ideal para quando tens pouco tempo), ver, ou simular; duração do jogo configurável.

### Fase F — Mais emoção fora do campo
- [ ] **F1. Conferências de imprensa:** perguntas com respostas à escolha, que mexem na moral, nos adeptos, na direção e nas manchetes.
- [ ] **F2. Conversas com jogadores e palestras** (antes do jogo e ao intervalo): motivar, pressionar ou acalmar, com efeitos reais.
- [ ] **F3. Balneário e adeptos:** capitão e líderes, conflitos e promessas, reações dos adeptos, protestos e cânticos.
- [ ] **F4. Momentos de carreira:** prémios, homenagens, lendas, despedidas e uma sala de história do clube.

### Fase G — Mais profundidade na gestão
- [ ] **G1. Táticas:** funções e instruções por jogador, bolas paradas, preparação para cada adversário.
- [ ] **G2. Equipa técnica:** adjunto, preparador físico, médicos e vários olheiros, com níveis e salários.
- [ ] **G3. Contratos e negociações:** salário, prémios, cláusulas, comissões, empréstimos com opção de compra e pagamentos a prestações.
- [ ] **G4. Finanças:** orçamento de salários e de transferências, escolha de patrocinadores, preço dos bilhetes, dívida e regras de equilíbrio financeiro.
- [ ] **G5. Treino detalhado:** planos semanais, treino individual, mudança de posição e gestão da carga.
- [ ] **G6. Equipas B e sub-23** a jogarem a sua própria competição (que ficou por fazer na Fase 6).

### Fase H — Polimento final
- [ ] Texturas e materiais finais, otimização para o teu telemóvel, acessibilidade (letra maior, daltonismo), revisão de todos os textos e uma versão 2.0 final.

## 3. Marcos (cada um é um APK para instalares)

| Marco | O que traz | Fases |
|---|---|---|
| 1 | **Visual novo** da aplicação e o botão de relatar problemas | A, B |
| 2 | Decisão sobre o Godot, com números do teu telemóvel | C |
| 3 | **3D bonito:** campo, estádio, jogadores animados, câmaras e repetições | D |
| 4 | **Jogar a sério:** regras, controlos, dificuldade e modo «só momentos-chave» | E |
| 5 | Conferências de imprensa, conversas e balneário | F |
| 6 | Gestão a fundo | G |
| 7 | Versão 2.0 final | H |

As fases **E, F e G** só mexem no Kotlin e trabalham em paralelo com a **B** e a **D**; a **D** só começa depois de a **C** decidir. A **A** vem primeiro de tudo.

## 4. Como se trabalha (e o que posso e não posso verificar)

- Continuo a trabalhar com equipas de subagentes em paralelo, como até aqui, e verifico cada tarefa antes de a juntar.
- **O que consigo verificar aqui:** testes das regras e da gestão, imagens dos menus no telemóvel simulado e, se me deixares correr o Godot nesta máquina, imagens do 3D desenhadas em software.
- **O que só tu consegues verificar:** se o 3D corre fluido e como se sente a jogar no teu telemóvel. Por isso o medidor de desempenho (A2) vem logo no início e cada marco traz uma lista curta do que testar.
- **Animações e modelos:** vêm de pacotes gratuitos de modelos, de licença livre (Kenney, Quaternius, ambientCG, Poly Haven), adaptados às cores dos clubes. Se faltarem animações de futebol, crio-as por programa. O resultado depende do que existir; ficará bom, mas não ao nível de um jogo de estúdio.
- **Gravações:** as carreiras que já tens devem continuar a abrir em todas as versões.

## 5. Riscos

1. **O Godot embutido na aplicação pode dar problemas** de tamanho, de arranque ou de compilação no GitHub. É para isso que existe a fase C, com plano B.
2. **O 3D só se vê num telemóvel real.** Quanto mais tarde souber como corre, mais caro sai corrigir. Daí o medidor de desempenho e os marcos curtos.
3. **É muito trabalho.** O limite de utilização de 5 horas vai parar o trabalho várias vezes; posso voltar a pôr a retoma automática.
4. **Os modelos gratuitos podem não ter todas as animações de futebol.**

## 6. O que preciso de ti

1. **Experimentares a 0.12** e dizeres-me o que não funciona (basta uma lista curta ou imagens do ecrã). Sem isto, a auditoria A1 só apanha o que se vê no telemóvel simulado.
2. **Autorizares-me a correr o Godot nesta máquina** (descarregar o programa oficial, confirmar a assinatura e correr com um ecrã virtual) para eu ver o 3D antes de to enviar. O sistema bloqueou a minha primeira tentativa, e só tu o podes autorizar.
3. **Confirmares a ordem dos marcos**, ou dizeres o que queres mudar.
