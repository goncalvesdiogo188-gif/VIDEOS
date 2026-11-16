# Futebol Carreira — Progresso até agora

Resumo do que já está feito no jogo, do que foi verificado e do que falta.
Atualizado a 16 de novembro de 2026, no fim da Fase 12 (versão 0.12 da aplicação). O plano está todo feito.

## Em que ponto estamos

| Fase | O quê | Estado |
|---|---|---|
| 0 | Ferramentas e forma de compilar | Feita |
| 1 | Base de dados e ligas | Feita |
| 2 | Simulação detalhada dos jogos | Feita |
| 3 | Menus, carreira, calendário e gravação | Feita |
| 4 | Gestão do clube (onze, direção, despedimento) | Feita |
| 5 | Transferências e finanças | Feita, à espera da tua confirmação |
| 6 | Treino, scouting e academia | Feita, à espera da tua confirmação |
| 7 | Taças, competições europeias e seleções | Feita, à espera da tua confirmação |
| 8 | Notícias, rivalidades e ambiente | Feita, à espera da tua confirmação |
| 9 | Modo carreira de jogador completo | Feita, à espera da tua confirmação |
| 10 | Editor e importação de JSON | Feita, à espera da tua confirmação |
| 11 | Jogo ao vivo em 3D | Feita, à espera da tua confirmação |
| 12 | Otimização e acabamentos | Feita, à espera da tua confirmação |

O plano completo, com o detalhe de cada fase, está em `PLANO.md`.

## Decisões que tomámos pelo caminho

- **Tecnologia**: o jogo começou em Godot, mas pediste para continuar sem Godot. É agora uma aplicação Android nativa (Kotlin, com ecrãs em Jetpack Compose). Avisei que esta escolha torna o jogo em 3D (Fase 11) mais difícil.
- **Compilação**: o APK é compilado automaticamente pelo GitHub sempre que o código muda. Não precisas de instalar nada no computador para experimentar o jogo.
- **Nomes reais**: a teu pedido, e porque o jogo é só para uso pessoal, os clubes e as ligas têm nomes reais (época 2025/26). As seleções entraram na Fase 7, também com nomes reais. Depois pediste jogadores reais (também para uso pessoal), e passaram a ser os da Wikipédia.
- **Trabalho em paralelo**: a partir da Fase 7, o trabalho é feito por vários subagentes de IA a trabalhar ao mesmo tempo; depois o resultado é juntado e verificado.
- **Equipas B**: troquei as equipas B das segundas divisões por outros clubes reais (Boavista, Mafra e Académica em Portugal; Tenerife em Espanha), para uma equipa B não poder subir à primeira divisão.

## O que o jogo já faz

### Base de dados (Fase 1)
- 6 países, 12 ligas (duas divisões por país), 234 clubes reais e os jogadores reais (ver abaixo).
- Cada clube tem cidade, estádio, lotação, cores e reputação; cada jogador tem posição, idade, pé, sete atributos e potencial.
- Tudo está em ficheiros JSON editáveis, em `app/src/main/assets/dados/`.
- Calendários (todos contra todos, casa e fora), tabelas, subidas e descidas, época após época.

### Jogos (Fase 2)
- Jogo simulado minuto a minuto: remates, golos com marcador e assistência, penáltis, cartões, lesões, substituições e cansaço.
- Castigos por expulsão e por acumulação de amarelos; lesões com jornadas de paragem.
- 5 formações e 3 mentalidades, com efeito no resultado.
- Nota de cada jogador, melhor em campo, melhores marcadores, assistências e notas médias da liga.
- Ecrã de jogo com relato em texto, estatísticas e notas, e botão «Ver em direto».
- Desempate na tabela por confronto direto.

### Carreira (Fase 3)
- Menu principal: continuar, nova carreira, carregar e apagar carreiras.
- Duas vias: treinador ou jogador. A via de jogador é ainda uma versão inicial: cria um jovem de 17 anos com o teu nome e acompanhas os jogos dele; o modo completo é a Fase 9.
- Ecrã central com cinco separadores: Início, Plantel, Mercado, Liga e Mais (finanças, calendário e opções).
- Datas reais: a época começa a 1 de julho, joga-se ao sábado de agosto a maio, com paragens para as seleções.
- Mensagens automáticas: resultados, lesões, castigos, mercado, fim de época.
- Gravação automática depois de cada jornada; várias carreiras em paralelo.

### Gestão do clube (Fase 4)
- Escolha do onze inicial, lugar a lugar; lesionados e castigados são substituídos sozinhos.
- Objetivo da direção para a época, conforme a força do clube.
- Confiança da direção e dos adeptos, que sobe e desce com os resultados.
- Despedimento e propostas de outros clubes; podes mudar de clube.

### Transferências e finanças (Fase 5)
- Contratos com salário, ano de fim e cláusula de rescisão.
- Mercado em julho, agosto e janeiro: comprar (pelo preço pedido ou arriscando menos), vender, pedir e ceder por empréstimo, contratar jogadores sem clube.
- Os outros clubes também fazem negócios entre si.
- Contas do clube: bilheteira, patrocínios e televisão, prémios, salários, compras e vendas.
- Obras para aumentar o estádio.
- Ficha de cada jogador com todos os atributos.

### Treino, olheiros e academia (Fase 6)
- Treino mês a mês, atributo a atributo: os jovens aproximam-se do potencial (mais depressa se jogarem) e os veteranos perdem qualidades. Numa época de teste, os jovens ganharam em média 4 pontos de geral e os veteranos com 33 anos ou mais perderam 2.
- Foco do treino (ataque, posse, defesa, físico ou equilibrado) e intensidade (leve, normal, forte; o treino forte dá mais lesões).
- Forma e moral de cada jogador, que mudam jogo a jogo e contam no resultado.
- Lesões no treino; o departamento médico encurta as paragens.
- Instalações com 5 níveis (centro de treinos, academia, departamento médico), que se melhoram pagando.
- Academia: entram jovens todos os verões; podes subi-los ao plantel ou dispensá-los; aos 20 anos sobem ou saem.
- Olheiros: o potencial dos jovens de outros clubes aparece como intervalo; o olheiro passa 4 semanas num país e traz os 5 melhores jovens.
- Novo separador **Mais → Clube** com tudo isto.

### Taças, Europa e seleções (Fase 7)
- Taças nacionais dos 6 países, com os nomes reais: Taça de Portugal, Copa del Rey, FA Cup, Coppa Italia, DFB-Pokal e Coupe de France. Jogam todos os clubes das duas divisões, a uma só mão, às quartas-feiras entre o fim de setembro e abril. O clube da divisão inferior joga em casa e os melhores clubes folgam na primeira ronda (ou nas primeiras). Há prolongamento («a.p.») e penáltis («g.p.»); a final é em campo neutro no fim de maio. Há prémios por ronda e por ganhar.
- Supertaças (Supertaça Cândido de Oliveira, Supercopa de España, FA Community Shield, Supercoppa Italiana, DFL-Supercup, Trophée des Champions): campeão da liga contra vencedor da taça (ou o segundo classificado, se for o mesmo clube), no primeiro sábado de agosto. Na primeira época jogam os dois clubes com mais reputação.
- Liga dos Campeões (lugares 1 a 4 de cada primeira divisão) e Liga Europa (lugares 5 a 8); na primeira época, apuram-se por reputação. Cada uma tem 24 clubes e uma fase de liga com 6 jogos (3 em casa, 3 fora, sem adversários do mesmo país). Os lugares 1 a 8 vão direitos aos oitavos; do 9.º ao 24.º jogam um play-off a duas mãos; depois há oitavos, quartos e meias-finais a duas mãos e uma final a um jogo em campo neutro. A Champions joga à terça e a final é no último sábado de maio; a Liga Europa joga à quinta e a final é na quarta-feira anterior. Os prémios são grandes: o vencedor da Champions ganha cerca de 20 a 25 M€ e o da Liga Europa cerca de 8 M€.
- 56 nações com nomes reais. Nos 6 países da base de dados jogam jogadores reais do jogo dessa nacionalidade; as outras 50 têm plantéis inventados, que não estão no mercado.
- Liga das Nações todos os anos (32 nações europeias, Liga A e B, jogos nas paragens de setembro, outubro e novembro, Final Four da Liga A no fim de maio ou em junho). Euro em 2028, 2032… (qualificação na época anterior, fase final com 24 equipas em junho) e Mundial em 2030, 2034… (32 equipas).
- Convocatórias de 23 jogadores antes de cada paragem; recebes uma mensagem quando jogadores teus são chamados. Ranking ao estilo Elo, como o da FIFA. As lesões das seleções passam para os clubes; os cartões, os castigos e as estatísticas não.
- Ecrãs: o seletor do separador **Liga** também lista as taças, as supertaças e as competições europeias (eliminatórias com a.p./g.p., tabela da fase de liga com zonas, histórico de vencedores). Novo **Mais → Seleções** (torneios, grupos, eliminatórias, ranking, convocatórias, vencedores). O **Início** e o calendário (**Mais → Jogos**) mostram a competição e a ronda de cada jogo; tocar num jogo já jogado do teu clube abre o relato.
- As carreiras da versão 0.6 ainda carregam; nelas, as taças, a Europa e as seleções começam do zero na época seguinte. Para ver tudo, começa uma carreira nova.

### Notícias e rivalidades (Fase 8)
- **Rivalidades**: 44 derbies reais entre clubes que existem na base de dados, em `app/src/main/assets/dados/rivalidades.json` (Derby de Lisboa, O Clássico, Derby do Minho, El Clásico, Derbi madrileño, Derbi sevillano, Derby do Norte de Londres, Merseyside, Manchester, Madonnina, Derby d'Italia, Capitale, Der Klassiker, Revierderby, Le Classique, Derby du Rhône e outros). Cada derby tem dois clubes, um nome e uma intensidade de 1 a 3. O ficheiro é opcional e fica gravado com a carreira; as carreiras antigas usam o da aplicação.
- **Efeitos de um derby**: mais cartões, uma pequena ajuda do público a quem joga em casa, estádio cheio, moral do plantel que sobe com a vitória e desce com a derrota (mais num clássico) e confiança da direção e dos adeptos a mexer 1,5 a 2 vezes mais. O Início, o calendário e as mensagens mostram o nome do derby.
- **Notícias**: manchete e texto curto, com vários modelos de frase por tipo de acontecimento, escolhidos ao acaso. Há notícias do teu clube e do resto do mundo: goleadas, surpresas, derbies, hat-tricks, sequências de golos, mudanças de liderança, título e descida decididos, surpresas e finais das taças, noites europeias dos clubes do teu país e finais europeias, grandes transferências de outros clubes, lesões de figuras, convocatórias e vencedores das seleções, e o estado de espírito dos adeptos e da direção («Adeptos contestam o treinador», «Direção renova confiança no treinador»). No máximo 5 por dia de jogos; ficam gravadas as últimas 80. Só se noticia o que o jogo simula: não há notícias de despedimentos de outros treinadores.
- **Ambiente**: Eufórico, Bom, Normal, Tenso ou Hostil, conforme a confiança dos adeptos e da direção, a forma nos últimos 5 jogos e um derby à porta. Mexe até 6% para cima ou 10% para baixo na assistência dos jogos de liga em casa do teu clube.
- **Ecrãs**: bloco **Notícias** no Início (as 3 mais recentes e «Ver todas»), nome do derby no cartão do próximo jogo e linha **Ambiente** no bloco «Objetivo da direção».

### Carreira de jogador (Fase 9)
- Crias o teu jogador com nome, nacionalidade, posição, pé e clube. O treinador escolhe o onze por qualidade e forma, e o teu estatuto (titular, rotação, suplente) muda com isso.
- Mensagens depois de cada jogo (minutos, nota, estatuto) e um resumo mensal do treino. Podes escolher um foco de treino só teu.
- O treinador dá-te pedidos (golos, assistências, baliza inviolada, nota média, físico) e podes «Pedir mais minutos».
- Nas janelas de mercado chegam propostas (transferência ou empréstimo); também podes pedir para sair e renovar o contrato.
- Podes ser convocado para a seleção do teu país (qualquer nacionalidade) e vês jogos e golos pela seleção.
- Prémios individuais de todas as ligas (melhor jogador, marcador, jovem, onze da época) e Bola de Ouro.
- Novo separador **Carreira** (perfil, percurso, propostas, prémios). A carreira acaba entre os 35 e os 38 anos e és convidado para treinador.

### Editor (Fase 10)
- **Editor** no menu principal: mudas clubes e jogadores da base das carreiras novas (nome, cores, estádio, atributos, contrato, clube, liga…), com «Repor dados originais». Também há **Mais → Opções → Editar esta carreira**.
- Exportar e importar ficheiros .json (a base, um país ou um clube), com relatório de erros: um ficheiro estragado é recusado.

### Jogo ao vivo em 3D (Fase 11)
- **Jogar ao vivo (3D)** no Início: um jogo de 90 minutos em cerca de 8 minutos reais, em ecrã horizontal, com campo, bancadas, 22 jogadores e bola em 3D, câmara de transmissão e controlos tácteis (joystick, passe, remate, sprint, trocar de jogador).
- O motor tem faltas, cartões, penáltis, lesões, bolas paradas e uma IA por posição; os atributos dos jogadores contam. No fim, o resultado entra na carreira como um jogo normal (tabela, estatísticas, mensagens, relatório), nas ligas, taças e Europa.
- Em **Mais → Opções → Jogos** escolhes simular (por defeito), ver em 3D (as duas equipas pela IA) ou jogar em 3D. Há «Simular o resto» e, se abandonares, o jogo simula-se.

### Otimização e acabamentos (Fase 12)
- A publicação na Play Store foi cancelada a teu pedido: o jogo é só para ti. Por isso não há chave privada nem pacote assinado; as atualizações continuam a instalar por cima com a chave de teste.
- Ícone novo (bola de futebol, também em versão de uma só cor para o Android 13) e ecrã de arranque sem clarão branco.
- Medições no núcleo, num computador: uma jornada 44 ms, uma época inteira 2 s, gravar 0,2 s, carregar uma gravação 0,3 s. O APK tem 1,8 MB.
- 3D mais leve: as rotações já não criam lixo a cada imagem, o 3D pára de redesenhar em pausa e há menos objetos criados por imagem nos controlos.
- Por fazer: juntar a geometria fixa do campo num só desenho e reutilizar o estado dos jogadores no motor (reduziria o trabalho do GL), mas só faz sentido depois de saber como o 3D corre no teu telemóvel.

### Jogadores reais e nacionalidades
- Os plantéis são reais: nomes, clubes e nacionalidades vêm da Wikipédia, e idades e posições do Wikidata. As notas são em parte de listas minhas (jogadores conhecidos) e o resto estimado pelo nível do clube.
- Todas as nacionalidades têm nome em português. As seleções usam jogadores reais (16 ou mais, com pelo menos 2 guarda-redes) e os inventados só completam.

## O que foi verificado e o que não foi

- **Testes automáticos do núcleo** (regras do jogo, sem ecrãs): 124 testes, todos a passar (0 falhas), incluindo as medições de desempenho (`Fase12DesempenhoTest.kt`). Cobrem a base de dados, os calendários, os jogos, as táticas, as datas, a carreira, a gravação, o onze, a direção, as contas, o mercado, o treino, a forma e a moral, a academia, as instalações, os olheiros, as taças, as supertaças, as competições europeias e as seleções (ficheiros `Fase7Test.kt`, `Fase7EuropaTest.kt` e `Fase7SelecoesTest.kt`), um teste que junta tudo, e as rivalidades, os efeitos dos derbies, as notícias, a gravação (também de uma gravação antiga sem notícias) e o ambiente (`Fase8Test.kt`), a carreira de jogador (`Fase9Test.kt`), o editor e o validador (`Fase10Test.kt`) as nacionalidades e convocatórias reais (`NacionalidadesTest.kt`) e o motor do jogo ao vivo e a sua ligação à carreira (`Fase11Test.kt`).
- **Teste dos ecrãs**: um teste percorre a aplicação num telemóvel simulado (criar carreira, comprar e vender, jogar, mudar tática, mudar o treino, promover um jovem, enviar o olheiro, ver as taças, a Europa e as seleções, acabar a época, gravar e voltar a carregar) e passa. Há 32 testes de ecrãs e do 3D (a aplicação toda, incluindo um jogo ao vivo jogado até ao fim, os ecrãs da Fase 7, o editor, a carreira de jogador, o HUD do 3D e a matemática da câmara e dos controlos), todos a passar. Guardam 62 imagens dos ecrãs na pasta `capturas/` (incluindo as notícias do Início e a lista completa).
- **O 3D nunca correu num telemóvel nem num emulador.** Os testes automáticos não executam o OpenGL: só verifiquei o motor, a matemática, o HUD e a ligação à carreira. A fluidez, o aspeto e a facilidade dos controlos são desconhecidos: tens de me dizer.
- **Num telemóvel verdadeiro**: ainda não tenho confirmação tua de nenhuma fase. Tens dito «Avança» sem relatar o teste, por isso não sei como o jogo se comporta no teu aparelho (velocidade, texto cortado, botões difíceis de tocar).
- **Dados reais**: os clubes (estádio, lotação, cores) vêm da minha memória, não de uma fonte verificada. Os jogadores vêm da Wikipédia e do Wikidata e podem estar uma época desatualizados; as notas são estimadas. Pode haver erros.

## O que ficou simples ou por fazer dentro das fases feitas

- Substituições durante o jogo: ainda não há, nem simulado nem ao vivo.
- Jogo ao vivo: sem fora de jogo, sem substituições tácticas nem cabeceamentos; o prolongamento não se joga ao vivo (nas taças, um empate vai aos penáltis simulados); os jogadores são figuras simples de caixas; o controlo começa num jogador escolhido pelo motor (não no teu jogador na carreira de jogador).
- Os contratos do teu clube renovam-se sozinhos; não há negociação de salário nem prémios.
- Os outros clubes não fazem propostas pelos teus jogadores: és tu que os pões à venda.
- Carreira de jogador: a renovação é opcional (se recusares, renova nas condições mínimas); o clube nunca recusa vender o jogador; o preço das propostas está limitado ao orçamento de quem compra; as seleções fora da Europa só jogam qualificações e Mundial; valores e salários disparam para jogadores com geral acima de 90.
- Editor: o seletor de ficheiros não foi testado automaticamente; só há as nacionalidades que já existem; a lista mostra 150 resultados; na carreira não se troca de liga e só se importam clubes; a base editada é uma cópia completa (as versões futuras do jogo não a atualizam: usa «Repor dados originais»); não se criam nem apagam clubes.
- Seleções: nas nações com poucos jogadores reais, um jogador do teu clube só entra se for melhor que o inventado; os inventados aparecem como «Clube estrangeiro»; o ecrã Convocados mostra só as seleções do utilizador, os 12 melhores com reais e as 6 primeiras do ranking.
- Não há equipa B/sub-23 a jogar uma competição própria; a academia faz esse papel. Só o teu clube tem academia; os outros renovam o plantel com jovens da formação, como antes.
- O olheiro procura só jovens (até 23 anos) e um país de cada vez.
- Só fica gravado o relato dos jogos do teu clube; dos outros fica o resultado.
- As seleções são simuladas em segundo plano: não treinas nenhuma. Na via de jogador a seleção foi feita na Fase 9.
- Na Liga das Nações não há subidas nem descidas entre a Liga A e a Liga B.
- Os anfitriões do Euro e do Mundial são ignorados: os jogos são em campo neutro.
- As nações não europeias só jogam nas épocas de Mundial.
- Nos sorteios das eliminatórias europeias não há proteção de países (podem calhar clubes do mesmo país).
- O ambiente só mexe na assistência dos jogos de liga do teu clube; nas taças e na Europa só o derby enche o estádio.
- As sequências de golos e a liderança (para as notícias) recomeçam a contar quando se carrega uma gravação.
- Não há derbies entre clubes de países diferentes (na Europa).
- As carreiras criadas em versões antigas mantêm os dados antigos (clubes inventados, sem contratos). Para ver tudo o que há de novo, começa uma carreira nova.

## Onde estão as coisas

Tudo está no repositório `goncalvesdiogo188-gif/VIDEOS`, no ramo `ccr-c95fc920-6su81c`, dentro da pasta `futebol-carreira/`:

| Ficheiro ou pasta | Para que serve |
|---|---|
| `apk/futebol-carreira.apk` | A aplicação pronta a instalar no telemóvel |
| `capturas/` | Imagens de todos os ecrãs, refeitas a cada compilação |
| `PLANO.md` | Plano completo, fase a fase |
| `GUIA_INSTALACAO.md` | Como instalar e como testar cada fase |
| `PROGRESSO.md` | Este resumo |
| `app/src/main/assets/dados/` | Base de dados em JSON |
| `nucleo/` | Regras do jogo e testes |
| `app/` | Ecrãs da aplicação |

## Como instalar a versão atual

1. No telemóvel Android, abre o ficheiro `futebol-carreira.apk` (o que te enviei, ou o que está na pasta `apk/` do repositório).
2. Se o telemóvel avisar sobre «fontes desconhecidas», autoriza e toca em **Instalar**. Instala por cima da versão anterior.
3. Abre **Futebol Carreira** e começa uma **Nova carreira**.

## O que preciso de ti

1. Experimentar o jogo ao vivo em 3D no telemóvel (**Jogar ao vivo (3D)**) e dizer-me se corre fluido, se os controlos são fáceis e se a câmara está boa; e se o telemóvel aquece muito; e o que não funciona no resto da versão 0.12.
2. Dizer-me se os preços, os salários, a velocidade de evolução dos jogadores e a dificuldade te parecem bem.
3. Dizer-me o que queres a seguir: o plano está todo feito. Podemos corrigir o que encontrares, melhorar o 3D (fora de jogo, substituições, cabeceamentos, geometria mais leve) ou mudar a dificuldade.
