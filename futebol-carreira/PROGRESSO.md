# Futebol Carreira — Progresso até agora

Resumo do que já está feito no jogo, do que foi verificado e do que falta.
Atualizado a 15 de novembro de 2026, no fim da Fase 5 (versão 0.5 da aplicação).

## Em que ponto estamos

| Fase | O quê | Estado |
|---|---|---|
| 0 | Ferramentas e forma de compilar | Feita |
| 1 | Base de dados e ligas | Feita |
| 2 | Simulação detalhada dos jogos | Feita |
| 3 | Menus, carreira, calendário e gravação | Feita |
| 4 | Gestão do clube (onze, direção, despedimento) | Feita |
| 5 | Transferências e finanças | Feita, à espera da tua confirmação |
| 6 | Treino, scouting e academia | Por fazer (é a próxima) |
| 7 | Taças, competições europeias e seleções | Por fazer |
| 8 | Notícias e rivalidades | Por fazer |
| 9 | Modo carreira de jogador completo | Por fazer |
| 10 | Editor e importação de JSON | Por fazer |
| 11 | Jogo em 3D | Por fazer |
| 12 | Otimização e versão final | Por fazer |

O plano completo, com o detalhe de cada fase, está em `PLANO.md`.

## Decisões que tomámos pelo caminho

- **Tecnologia**: o jogo começou em Godot, mas pediste para continuar sem Godot. É agora uma aplicação Android nativa (Kotlin, com ecrãs em Jetpack Compose). Avisei que esta escolha torna o jogo em 3D (Fase 11) mais difícil.
- **Compilação**: o APK é compilado automaticamente pelo GitHub sempre que o código muda. Não precisas de instalar nada no computador para experimentar o jogo.
- **Nomes reais**: a teu pedido, e porque o jogo é só para uso pessoal, os clubes e as ligas têm nomes reais (época 2025/26). Os jogadores continuam inventados. As seleções ainda não existem; entram com nomes reais na Fase 7.
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

## O que foi verificado e o que não foi

- **Testes automáticos do núcleo** (regras do jogo, sem ecrãs): 16 testes, todos a passar. Cobrem a base de dados, os calendários, os jogos, as táticas, as datas, a carreira, a gravação, o onze, a direção, as contas e o mercado.
- **Teste dos ecrãs**: um teste percorre a aplicação num telemóvel simulado (criar carreira, comprar e vender, jogar, mudar tática, acabar a época, gravar e voltar a carregar) e passa. Guarda 25 imagens dos ecrãs na pasta `capturas/`.
- **Num telemóvel verdadeiro**: ainda não tenho confirmação tua de nenhuma fase. Tens dito «Avança» sem relatar o teste, por isso não sei como o jogo se comporta no teu aparelho (velocidade, texto cortado, botões difíceis de tocar).
- **Dados dos clubes reais**: vêm da minha memória, não de uma fonte verificada. Pode haver um estádio, uma lotação ou uma cor errada.

## O que ficou simples ou por fazer dentro das fases feitas

- Substituições durante o jogo: o jogo é calculado de uma vez e só depois mostrado; fica para a Fase 11.
- Os contratos do teu clube renovam-se sozinhos; não há negociação de salário nem prémios.
- Os outros clubes não fazem propostas pelos teus jogadores: és tu que os pões à venda.
- Na via de jogador não há tática, objetivos nem mercado.
- A evolução dos jogadores de época para época é provisória (a Fase 6 trata disso).
- Só fica gravado o relato dos jogos do teu clube; dos outros fica o resultado.
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

1. Experimentar a versão 0.5 no telemóvel e dizer-me o que funciona e o que não funciona.
2. Dizer-me se os preços, os salários e a dificuldade te parecem bem.
3. Dizer-me se queres jogadores reais (é um trabalho grande: mais de 5000 nomes e atributos).
4. Confirmar para eu começar a Fase 6 (treino, scouting e academia).
