# PLANO — Futebol Carreira

Jogo de futebol para Android com modo carreira (jogador e treinador), feito em Godot 4 (GDScript).

**Regras do projeto**
- Nomes fictícios por defeito; toda a base de dados está em JSON editável (pasta `dados/`).
- Código separado em: dados, simulação, interface e partida 3D.
- Uma fase pequena de cada vez. Só se avança depois de o utilizador testar e confirmar.
- Comentários em português; este ficheiro é atualizado no fim de cada fase.

**Legenda:** ✅ feito · 🔄 em curso · ⬜ por fazer

---

## Estado atual

| Fase | Tema | Estado |
|---|---|---|
| 0 | Ferramentas e projeto | ✅ |
| 1 | Base de dados e competições (ligas) | ✅ à espera de confirmação |
| 2 | Simulação detalhada de jogos (sem 3D) | ⬜ |
| 3 | Menus, navegação e gravação | ⬜ |
| 4 | Gestão do clube (modo treinador) | ⬜ |
| 5 | Transferências e finanças | ⬜ |
| 6 | Treino, scouting e academia | ⬜ |
| 7 | Taças, Europa e seleções | ⬜ |
| 8 | Notícias, rivalidades e ambiente | ⬜ |
| 9 | Modo carreira de jogador | ⬜ |
| 10 | Editor e importação de JSON | ⬜ |
| 11 | Partida 3D | ⬜ |
| 12 | Otimização e exportação Android | ⬜ |

---

## Fase 0 — Ferramentas e projeto ✅
- [x] Projeto Godot 4 criado (`project.godot`), orientação horizontal, renderizador "Compatibilidade" (o mais leve para telemóveis médios).
- [x] Guia de instalação e de exportação para Android: `GUIA_INSTALACAO.md`.

## Fase 1 — Base de dados e competições ✅ (à espera de confirmação)
- [x] Estrutura de pastas (dados / scripts / cenas / ferramentas / testes).
- [x] Base de dados em JSON: 6 países, 12 ligas (2 divisões por país), 228 equipas, 5472 jogadores com atributos, posição, idade, pé, potencial.
- [x] Ferramenta que gera a base de dados fictícia (`ferramentas/gerar_base_dados.gd`).
- [x] Carregador de JSON com mensagens de erro claras (`scripts/dados/base_dados.gd`).
- [x] Gerador de calendários (todos contra todos, casa e fora).
- [x] Tabela classificativa (pontos, diferença de golos, golos marcados).
- [x] Simulação rápida de resultados com base na força do melhor onze.
- [x] Fim de época: campeão, subidas e descidas, evolução simples dos jogadores (provisória).
- [x] Cena de teste: tabela, resultados, plantéis e histórico de campeões.
- [x] Testes automáticos (`testes/testar_fase1.gd`).

## Fase 2 — Simulação detalhada de jogos (sem 3D)
- [ ] Motor de jogo minuto a minuto: posse, ocasiões, golos com marcador e assistência.
- [ ] Cartões, lesões, substituições, cansaço.
- [ ] Táticas simples (formação, mentalidade) com efeito no jogo.
- [ ] Notas dos jogadores e estatísticas da época (melhor marcador, assistências).
- [ ] Ecrã de jogo em texto ("relato") para acompanhar um jogo.
- [ ] Desempate por confronto direto na tabela.

## Fase 3 — Menus, navegação e gravação
- [ ] Menu principal, novo jogo (escolher via jogador/treinador, país, clube).
- [ ] Ecrã central da carreira (próximo jogo, tabela, notícias).
- [ ] Calendário com datas reais (dias, semanas, janelas de mercado).
- [ ] Gravar e carregar carreira (ficheiro em `user://`).
- [ ] Tema visual próprio para ecrã tátil.

## Fase 4 — Gestão do clube (modo treinador)
- [ ] Plantel, onze inicial, suplentes, tática e instruções.
- [ ] Objetivos da direção e confiança (direção e adeptos).
- [ ] Despedimento e propostas de outros clubes.

## Fase 5 — Transferências e finanças
- [ ] Contratos: salário, duração, cláusula de rescisão, prémios.
- [ ] Mercado: propostas, negociação, empréstimos, jogadores livres; IA dos outros clubes.
- [ ] Finanças: orçamento, salários, bilheteira, patrocínios, prémios, obras no estádio.

## Fase 6 — Treino, scouting e academia
- [ ] Treino e evolução por atributos, potencial, forma e moral (substitui a evolução provisória).
- [ ] Lesões e recuperação.
- [ ] Olheiros, relatórios e jovens promessas; academia e equipa B/sub-23.

## Fase 7 — Taças, Europa e seleções
- [ ] Taça nacional e supertaça por país.
- [ ] Competições europeias: fase de liga + eliminatórias, com apuramento pelas ligas.
- [ ] Seleções: Euro, Mundial e Liga das Nações (convocatórias, qualificação).

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
- [ ] Campo, jogadores low-poly e câmara de transmissão.
- [ ] Controlos táteis (joystick + passe/remate/sprint) e IA da restante equipa.
- [ ] Ligação ao motor de simulação (resultado, estatísticas, lesões).
- [ ] Opção de jogar, ver resumo ou simular.

## Fase 12 — Otimização e exportação Android
- [ ] Desempenho em telemóveis médios (30/60 fps), tamanho do APK.
- [ ] Ícone, ecrã de arranque, APK/AAB assinado.

---

## Onde está cada coisa

```
futebol-carreira/
├── project.godot            configuração do projeto
├── PLANO.md                 este ficheiro
├── GUIA_INSTALACAO.md       instalar, abrir, testar, exportar
├── dados/                   BASE DE DADOS (JSON editável)
│   ├── paises.json
│   ├── competicoes.json
│   ├── nomes.json           nomes para gerar jogadores novos
│   ├── equipas/POR.json…    clubes por país
│   └── jogadores/POR.json…  jogadores por país (um por linha)
├── scripts/
│   ├── jogo.gd              ponto central (autoload "Jogo")
│   ├── modelos/             Jogador, Equipa, Competicao
│   ├── dados/               ler/gravar JSON, gerador de jogadores
│   ├── simulacao/           calendário, tabela, jogo, épocas
│   └── interface/           ecrãs
├── cenas/                   cenas do Godot (.tscn)
├── ferramentas/             utilitários (gerar base de dados)
└── testes/                  testes automáticos
```
