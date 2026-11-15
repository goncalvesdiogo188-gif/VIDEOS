# Futebol Carreira — Progresso até agora

Resumo do que já está feito no jogo, do que foi verificado e do que falta.
Atualizado a 15 de novembro de 2026, no fim da Fase 7 (versão 0.7 da aplicação).

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
| 8 | Notícias e rivalidades | Por fazer (é a próxima) |
| 9 | Modo carreira de jogador completo | Por fazer |
| 10 | Editor e importação de JSON | Por fazer |
| 11 | Jogo em 3D | Por fazer |
| 12 | Otimização e versão final | Por fazer |

O plano completo, com o detalhe de cada fase, está em `PLANO.md`.

## Decisões que tomámos pelo caminho

- **Tecnologia**: o jogo começou em Godot, mas pediste para continuar sem Godot. É agora uma aplicação Android nativa (Kotlin, com ecrãs em Jetpack Compose). Avisei que esta escolha torna o jogo em 3D (Fase 11) mais difícil.
- **Compilação**: o APK é compilado automaticamente pelo GitHub sempre que o código muda. Não precisas de instalar nada no computador para experimentar o jogo.
- **Nomes reais**: a teu pedido, e porque o jogo é só para uso pessoal, os clubes e as ligas têm nomes reais (época 2025/26). Os jogadores continuam inventados. As seleções entraram na Fase 7, também com nomes reais.
- **Trabalho em paralelo**: a partir da Fase 7, o trabalho é feito por vários subagentes de IA a trabalhar ao mesmo tempo; depois o resultado é juntado e verificado.
- **Equipas B**: troquei as equipas B das segundas divisões por outros clubes reais (Boavista, Mafra e Académica em Portugal; Tenerife em Espanha), para uma equipa B não poder subir à primeira divisão.

## O que o jogo já faz

### Base de dados (Fase 1)
- 6 países, 12 ligas (duas divisões por país), 234 clubes reais e 5616 jogadores inventados.
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

## O que foi verificado e o que não foi

- **Testes automáticos do núcleo** (regras do jogo, sem ecrãs): 57 testes, todos a passar. Cobrem a base de dados, os calendários, os jogos, as táticas, as datas, a carreira, a gravação, o onze, a direção, as contas, o mercado, o treino, a forma e a moral, a academia, as instalações, os olheiros, as taças, as supertaças, as competições europeias e as seleções (ficheiros `Fase7Test.kt`, `Fase7EuropaTest.kt` e `Fase7SelecoesTest.kt`), mais um teste que junta tudo.
- **Teste dos ecrãs**: um teste percorre a aplicação num telemóvel simulado (criar carreira, comprar e vender, jogar, mudar tática, mudar o treino, promover um jovem, enviar o olheiro, ver as taças, a Europa e as seleções, acabar a época, gravar e voltar a carregar) e passa. Guarda 40 imagens dos ecrãs na pasta `capturas/`.
- **Num telemóvel verdadeiro**: ainda não tenho confirmação tua de nenhuma fase. Tens dito «Avança» sem relatar o teste, por isso não sei como o jogo se comporta no teu aparelho (velocidade, texto cortado, botões difíceis de tocar).
- **Dados dos clubes reais**: vêm da minha memória, não de uma fonte verificada. Pode haver um estádio, uma lotação ou uma cor errada.

## O que ficou simples ou por fazer dentro das fases feitas

- Substituições durante o jogo: o jogo é calculado de uma vez e só depois mostrado; fica para a Fase 11.
- Os contratos do teu clube renovam-se sozinhos; não há negociação de salário nem prémios.
- Os outros clubes não fazem propostas pelos teus jogadores: és tu que os pões à venda.
- Na via de jogador não há tática, objetivos nem mercado.
- Não há equipa B/sub-23 a jogar uma competição própria; a academia faz esse papel. Só o teu clube tem academia; os outros renovam o plantel com jovens da formação, como antes.
- O olheiro procura só jovens (até 23 anos) e um país de cada vez.
- Só fica gravado o relato dos jogos do teu clube; dos outros fica o resultado.
- As seleções são simuladas em segundo plano: não treinas nenhuma. Na via de jogador ainda não há carreira na seleção (fica para a Fase 9).
- Na Liga das Nações não há subidas nem descidas entre a Liga A e a Liga B.
- Os anfitriões do Euro e do Mundial são ignorados: os jogos são em campo neutro.
- As nações não europeias só jogam nas épocas de Mundial.
- Nos sorteios das eliminatórias europeias não há proteção de países (podem calhar clubes do mesmo país).
- As carreiras criadas em versões antigas mantêm os dados antigos (clubes inventados, sem contratos). Para ver tudo o que há de novo, começa uma carreira nova.

## Onde estão as coisas

Tudo está no repositório `goncalvesdiogo188-gif/VIDEOS`, no ramo `ccr-c95fc920-6su81c`, dentro da pasta `futebol-carreira/`:

| Ficheiro ou pasta | Para que serve |
|---|---|
| `apk/futebol-carreira.apk` | A aplicação pronta a instalar no telemóvel |
| `capturas/` | Imagens de todos os ecrãs, refeitas a cada compilação |
| `.claude/skills/` | Skills para trabalhar com subagentes (`subagentes` e `equipa-haiku`) |
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

1. Experimentar a versão 0.7 no telemóvel e dizer-me o que funciona e o que não funciona.
2. Dizer-me se os preços, os salários, a velocidade de evolução dos jogadores e a dificuldade te parecem bem.
3. Dizer-me se queres jogadores reais (é um trabalho grande: mais de 5000 nomes e atributos).
4. Confirmar para eu começar a Fase 8 (notícias e rivalidades).
