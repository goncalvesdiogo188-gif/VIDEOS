# PLANO — Futebol Carreira

Jogo de futebol para Android com modo carreira (jogador e treinador).

**Tecnologia:** Android nativo — Kotlin, Jetpack Compose (ecrãs) e Android Studio.
(O projeto começou em Godot 4; a pedido do utilizador passou para Android nativo e a Fase 1 foi reescrita.)

**Regras do projeto**
- Clubes e ligas com nomes reais (época 2025/26), a pedido do utilizador, porque o jogo é só para uso pessoal e não vai ser publicado. Os jogadores são, por agora, inventados. Toda a base de dados está em JSON editável (pasta `dados/`).
- Código separado em: dados e simulação (módulo `nucleo`, Kotlin puro), interface (módulo `app`) e, mais tarde, partida 3D.
- Uma fase pequena de cada vez. Só se avança depois de o utilizador testar e confirmar.
- Comentários em português; este ficheiro é atualizado no fim de cada fase.

**Legenda:** ✅ feito · 🔄 em curso · ⬜ por fazer

---

## Estado atual

| Fase | Tema | Estado |
|---|---|---|
| 0 | Ferramentas e projeto (Android nativo) | ✅ |
| 1 | Base de dados e competições (ligas) | ✅ à espera de confirmação |
| 2 | Simulação detalhada de jogos (sem 3D) | ⬜ |
| 3 | Menus, navegação e gravação | ⬜ |
| 4 | Gestão do clube (modo treinador) | ⬜ |
| 5 | Transferências e finanças | ⬜ |
| 6 | Treino, scouting e academia | ⬜ |
| 7 | Taças, Europa e seleções | ✅ à espera de confirmação |
| 8 | Notícias, rivalidades e ambiente | ⬜ |
| 9 | Modo carreira de jogador | ⬜ |
| 10 | Editor e importação de JSON | ⬜ |
| 11 | Partida 3D | ⬜ |
| 12 | Otimização e exportação Android | ⬜ |

---

## Fase 0 — Ferramentas e projeto ✅
- [x] Projeto Android (Kotlin + Jetpack Compose) com dois módulos: `nucleo` e `app`.
- [x] APK de teste pronto a instalar em `apk/futebol-carreira.apk` (não é preciso instalar ferramentas para experimentar).
- [x] Guia de instalação: `GUIA_INSTALACAO.md`.

## Fase 1 — Base de dados e competições ✅
- [x] Estrutura de pastas (dados / scripts / cenas / ferramentas / testes).
- [x] Base de dados em JSON: 6 países, 12 ligas (2 divisões por país), 234 clubes reais, 5616 jogadores inventados com atributos, posição, idade, pé, potencial.
- [x] Ferramenta que gera a base de dados (`./gradlew :nucleo:gerarBaseDados`).
- [x] Carregador de JSON com mensagens de erro claras (`BaseDados.kt`).
- [x] Gerador de calendários (todos contra todos, casa e fora).
- [x] Tabela classificativa (pontos, diferença de golos, golos marcados).
- [x] Simulação rápida de resultados com base na força do melhor onze.
- [x] Fim de época: campeão, subidas e descidas, evolução simples dos jogadores (provisória).
- [x] Ecrã de teste: tabela, resultados, plantéis e histórico de campeões.
- [x] Testes automáticos (`./gradlew :nucleo:test`).

## Fase 2 — Simulação detalhada de jogos (sem 3D) ✅
- [x] Motor de jogo minuto a minuto (`MotorJogo.kt`): remates, golos com marcador e assistência, penáltis.
- [x] Cartões (com castigos), lesões (com jornadas de paragem), substituições e cansaço.
- [x] Táticas: 5 formações e 3 mentalidades com efeito no jogo; o computador escolhe sozinho ou fica fixo.
- [x] Notas dos jogadores, melhor em campo e estatísticas da época (marcadores, assistências, notas médias).
- [x] Ecrã de jogo: relato em texto, estatísticas e notas, com botão «Ver em direto».
- [x] Desempate por confronto direto na tabela.
- [x] Testes automáticos da Fase 2 (`Fase2Test.kt`).

## Fase 3 — Menus, navegação e gravação ✅
- [x] Menu principal: continuar, nova carreira, carregar carreira (com apagar).
- [x] Nova carreira: via de treinador ou de jogador, nome, país, liga e clube.
- [x] Via de jogador (versão inicial): cria-se um jovem de 17 anos no clube escolhido; quando se reforma passa a treinador. O modo completo chega na Fase 9.
- [x] Ecrã central da carreira com 5 separadores: Início (próximo jogo, classificação, mensagens), Plantel, Liga, Calendário e Mais.
- [x] Calendário com datas reais: jornadas ao sábado de agosto a maio, paragens para seleções, janelas de mercado (julho, agosto e janeiro).
- [x] Mensagens automáticas: resultado de cada jogo, lesões, castigos, mercado, fim de época.
- [x] Gravação automática depois de cada jornada (`Gravador.kt`, `Armazem.kt`); várias carreiras em paralelo.
- [x] Tema visual próprio (escuro, verde relvado), emblemas com as cores dos clubes, botões grandes.
- [x] Testes automáticos da Fase 3 (`Fase3Test.kt`) e teste dos ecrãs com capturas (`CapturasTest.kt`, pasta `capturas/`).

## Fase 4 — Gestão do clube (modo treinador) ✅
- [x] Onze inicial escolhido pelo treinador (tocar num lugar para trocar o jogador); suplentes e lesionados tratados automaticamente.
- [x] Objetivo da direção para a época, conforme a força do clube na liga.
- [x] Confiança da direção e dos adeptos, que sobe e desce com os resultados e com a distância ao objetivo.
- [x] Despedimento (a meio ou no fim da época) e propostas de outros clubes; mudar de clube.
- [x] Testes automáticos da Fase 4 (`Fase4Test.kt`).
- [ ] Substituições feitas pelo treinador durante o jogo: ficam para a fase do jogo em 3D (Fase 11), quando o jogo passar a ser jogado ao vivo.

## Fase 5 — Transferências e finanças ✅ (à espera de confirmação)
- [x] Contratos: salário anual, ano em que o contrato acaba e cláusula de rescisão (três vezes o valor de mercado).
- [x] Mercado (julho, agosto e janeiro): comprar pelo preço pedido ou arriscar uma proposta mais baixa, vender, pedir e ceder por empréstimo, contratar jogadores sem clube.
- [x] Os outros clubes também compram e vendem entre si.
- [x] Fim de época: emprestados voltam, contratos renovam-se ou os jogadores ficam livres, plantéis curtos são completados.
- [x] Finanças: dinheiro em caixa, bilheteira, patrocínios e televisão, prémios de classificação, salários; obras para aumentar o estádio.
- [x] Ficha de cada jogador com todos os atributos.
- [x] Testes automáticos da Fase 5 (`Fase5Test.kt`).
- [ ] Ainda simples, a melhorar mais tarde: os contratos do teu clube renovam-se sozinhos; não há negociação de salário nem prémios; os outros clubes não fazem propostas pelos teus jogadores (és tu que os pões à venda).

## Fase 6 — Treino, scouting e academia ✅ (à espera de confirmação)
- [x] Treino mês a mês, atributo a atributo: os jovens aproximam-se do potencial (mais depressa se jogarem), os veteranos perdem qualidades a partir dos 31 anos (33 nos guarda-redes). Substitui a evolução provisória.
- [x] Foco do treino (equilibrado, ataque, posse, defesa, físico) e intensidade (leve, normal, forte).
- [x] Forma e moral de cada jogador, que mudam jogo a jogo e contam no resultado.
- [x] Lesões no treino (mais com treino forte) e departamento médico que encurta as paragens.
- [x] Instalações com 5 níveis (centro de treinos, academia, departamento médico), que se podem melhorar pagando.
- [x] Academia: uma geração nova de jovens todos os verões; promover ao plantel ou dispensar; aos 20 anos sobem ou saem.
- [x] Olheiros: o potencial dos jovens de outros clubes só se vê aproximado; o olheiro viaja 4 semanas a um país e traz um relatório com os melhores.
- [x] Testes automáticos da Fase 6 (`Fase6Test.kt`).
- [ ] Ficou de fora: a equipa B/sub-23 a jogar a sua própria competição. A academia faz esse papel por agora; os outros clubes ainda não têm academia própria (renovam o plantel com jovens da formação, como antes).

## Fase 7 — Taças, Europa e seleções ✅ (à espera de confirmação)
- [x] Taças nacionais dos 6 países, com os nomes reais (Taça de Portugal, Copa del Rey, FA Cup, Coppa Italia, DFB-Pokal, Coupe de France): todos os clubes das duas divisões, eliminatórias a uma só mão às quartas-feiras (fim de setembro a abril), o clube da divisão inferior joga em casa, os melhores clubes folgam na(s) primeira(s) ronda(s), prolongamento («a.p.») e penáltis («g.p.»), final em campo neutro no fim de maio. Prémios por ronda e por ganhar a taça.
- [x] Supertaças (Supertaça Cândido de Oliveira, Supercopa de España, FA Community Shield, Supercoppa Italiana, DFL-Supercup, Trophée des Champions): campeão da liga contra vencedor da taça (o segundo classificado, se for o mesmo clube), no primeiro sábado de agosto; na primeira época, os dois clubes com mais reputação.
- [x] Competições europeias: Liga dos Campeões (lugares 1 a 4 da primeira divisão de cada país) e Liga Europa (lugares 5 a 8); na primeira época, por reputação. 24 clubes cada, fase de liga com 6 jogos (3 em casa, 3 fora, sem adversários do mesmo país), os lugares 1 a 8 vão direitos aos oitavos, do 9.º ao 24.º jogam um play-off a duas mãos, depois oitavos, quartos e meias-finais a duas mãos e final a um jogo em campo neutro (Campeões no último sábado de maio, Liga Europa na quarta-feira anterior). Campeões à terça, Liga Europa à quinta. Prémios grandes (vencedor da Champions cerca de 20 a 25 M€, da Liga Europa cerca de 8 M€).
- [x] Seleções: 56 nações com nomes reais (nos 6 países da base de dados jogam jogadores reais do jogo dessa nacionalidade; as outras 50 têm plantéis inventados que não estão no mercado). Liga das Nações todos os anos (32 nações europeias, Liga A e B, grupos nas paragens de setembro, outubro e novembro, Final Four da Liga A no fim de maio/junho), Euro 2028, 2032… (qualificação na época anterior, fase final com 24 equipas em junho) e Mundial 2030, 2034… (32 equipas). Convocatórias de 23 jogadores antes de cada janela, com mensagem quando os teus jogadores são chamados. Ranking ao estilo Elo (como o da FIFA). Lesões nas seleções passam para os clubes; cartões, castigos e estatísticas não.
- [x] Ecrãs: o seletor do separador Liga passa a listar também taças, supertaças e competições europeias (eliminatórias com a.p./g.p., tabela da fase de liga com zonas, histórico de vencedores); **Mais → Seleções** (torneios, grupos, eliminatórias, ranking, convocatórias, vencedores); o Início e o calendário mostram a competição e a ronda de cada jogo; tocar num jogo já jogado do teu clube abre o relato.
- [x] Gravações da versão 0.6 continuam a carregar; taças, Europa e seleções começam do zero na época seguinte nas carreiras antigas.
- [x] Testes automáticos da Fase 7 (`Fase7Test.kt`, `Fase7EuropaTest.kt`, `Fase7SelecoesTest.kt` e um teste de integração).
- [ ] Ficou de fora ou simples: as seleções são simuladas em segundo plano (não treinas nenhuma); na via de jogador ainda não há carreira na seleção (Fase 9); não há subidas e descidas na Liga das Nações; os anfitriões do Euro e do Mundial são ignorados (campo neutro); as nações não europeias só jogam nas épocas de Mundial; os sorteios das eliminatórias europeias não têm proteção de países.

## Fase 8 — Notícias, rivalidades e ambiente
- [ ] Manchetes geradas a partir dos acontecimentos.
- [ ] Rivalidades e derbies (ficheiro JSON), ambiente com adeptos e direção.

## Fase 9 — Modo carreira de jogador
- [ ] Criar jogador, evolução, pedidos do treinador, titularidade.
- [ ] Transferências do ponto de vista do jogador, seleção, prémios individuais.
- [ ] Passagem de jogador a treinador no fim da carreira.

## Fase 10 — Editor e importação de JSON
- [ ] Editor de equipas e jogadores dentro do jogo.
- [ ] Importar/exportar plantéis em JSON a partir do armazenamento do telemóvel.
- [ ] Validação dos ficheiros importados.

## Fase 11 — Partida 3D
- [ ] Escolher a biblioteca 3D (OpenGL ES através de Filament/SceneView, ou motor próprio simples) com um protótipo.
- [ ] Campo, jogadores low-poly e câmara de transmissão.
- [ ] Controlos táteis (joystick + passe/remate/sprint) e IA da restante equipa.
- [ ] Ligação ao motor de simulação (resultado, estatísticas, lesões).
- [ ] Opção de jogar, ver resumo ou simular.

## Fase 12 — Otimização e exportação Android
- [ ] Desempenho em telemóveis médios (30/60 fps), tamanho do APK.
- [ ] Ícone, ecrã de arranque, chave privada e AAB assinado para a Play Store.

---

## Onde está cada coisa

```
futebol-carreira/
├── PLANO.md                 este ficheiro
├── GUIA_INSTALACAO.md       instalar, abrir, testar
├── apk/futebol-carreira.apk APK de teste, pronto a instalar no telemóvel
├── capturas/                imagens dos ecrãs, tiradas automaticamente a cada compilação
├── chaves/teste.keystore    chave de TESTE (não serve para a Play Store)
├── settings.gradle.kts …    configuração da compilação (Gradle)
├── nucleo/                  DADOS E SIMULAÇÃO (Kotlin puro, sem Android)
│   └── src/main/kotlin/pt/futebolcarreira/nucleo/
│       ├── modelos/         Jogador, Equipa, Competicao, Tatica
│       ├── dados/           ler/gravar JSON, gerador de jogadores
│       ├── simulacao/       calendário e datas, tabela, motor de jogo, épocas
│       ├── carreira/        a carreira do utilizador e a gravação
│       └── ferramentas/     gerar a base de dados
│   └── src/test/            testes automáticos
└── app/                     APLICAÇÃO ANDROID
    └── src/main/
        ├── assets/dados/    BASE DE DADOS (JSON editável)
        │   ├── paises.json, competicoes.json, nomes.json
        │   ├── equipas/POR.json…    clubes por país
        │   └── jogadores/POR.json…  jogadores por país (um por linha)
        ├── kotlin/pt/futebolcarreira/
        │   ├── MainActivity.kt      arranque da aplicação
        │   ├── JogoViewModel.kt     liga os ecrãs ao núcleo
        │   ├── ModelosUi.kt         o que cada ecrã mostra
        │   ├── Armazem.kt           ficheiros das carreiras gravadas
        │   └── ecras/               ecrãs (Jetpack Compose): menu, nova carreira, carreira, liga, plantel, jogo
        └── res/                     ícone e textos
```
