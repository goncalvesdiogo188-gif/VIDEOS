# Jogo ao vivo: regras (E1) e inteligência/dificuldade (E3), API para a app

Tudo isto é Kotlin puro em `nucleo/aovivo/`. Os campos e métodos novos têm valor por omissão: o código antigo
(e as partidas falsas dos testes da app) continua a compilar sem mudanças.

## Criar o jogo

```kotlin
FabricaPartida.criar(
    casa, fora, utilizadorCasa, rng, duracaoReal = 480f, semUtilizador = false,
    dificuldade = Dificuldade.NORMAL,          // FACIL, NORMAL, DIFICIL, LENDARIO (Dificuldade.deTexto("difícil") para gravar/ler)
    exigencia = carreira.exigenciaDoProximoJogo(), // null = pode acabar empatado (liga); senão prolongamento + penáltis ao vivo
)
```

- `Dificuldade` mexe na IA da equipa que o utilizador **não** comanda (e só nela; os colegas do utilizador jogam sempre como em Normal):
  altura da linha defensiva, raio de pressão e contra-pressão, marcação, velocidade, desarmes, defesas do guarda-redes, erro nos
  passes e remates, tempo de decisão, respeito pelo fora de jogo, saída de bola curta. `Dificuldade.ajudaMira` (0..1) é para
  os **controlos do utilizador**: a app pode usá-la para mostrar/ajustar a ajuda de mira, e o motor já a aplica ao erro dos
  passes/remates/cruzamentos/penáltis do jogador comandado (Fácil 0,6 ... Lendário 0).
- `Carreira.exigenciaDoProximoJogo()` (em `LigacaoCarreira.kt`) devolve `ExigenciaVencedor(golosAntesCasa, golosAntesFora)` para
  jogos únicos e últimas mãos de taças/competições europeias, e null nos outros. **Quem cria a partida deve passá-la.**
  Sem ela o comportamento é o de antes: o jogo acaba aos 90' e a carreira sorteia os penáltis.
- No fim, `FabricaPartida.relatorio(partida)` e `carreira.registarJogoAoVivo(relatorio)` funcionam como antes. Se houve prolongamento,
  `relatorio.prolongamento = true`, os golos do prolongamento estão em `golosCasa`/`golosFora`, nos `eventos` e nas fichas, e
  `relatorio.duracao >= 120`. Se houve penáltis, `relatorio.penaltisCasa/penaltisFora` (marcados; -1 se não houve) e a carreira usa-os
  no lugar do sorteio (`Resultado.penaltisCasa/penaltisFora`, `Confronto.vencedor`).

## `EstadoPartida` (campos novos, no fim)

| campo | significado |
|---|---|
| `periodo` | 0 = 1.ª parte, 1 = 2.ª parte, 2 = 1.º tempo do prolongamento, 3 = 2.º tempo. Na fase INTERVALO é o período que acabou de acabar. |
| `bolaParada: BolaParada?` | existe nas fases PONTAPE_SAIDA, PONTAPE_BALIZA, CANTO, LIVRE, LANCAMENTO e PENALTI |
| `desempate: EstadoDesempate?` | marcação de penáltis de desempate (golos de cada lado, resultado de cada chute, morte súbita, decidido) |
| `vantagem` | true enquanto o árbitro deixa seguir a vantagem (mostrar "Vantagem!") |
| `dificuldade` | a dificuldade do jogo |
| `numeroDeLances` | tamanho de `partida.lances`: se aumentou, há lances novos para relatar |

`segundaParte` continua a ser a direção de ataque da equipa da casa (true = ataca para -x) e inverte-se também no prolongamento;
na marcação de penáltis muda de chute para chute (cada equipa chuta para a mesma baliza).

## Fases e fluxo

- Depois dos 90' empatados (só com `exigencia`): fase INTERVALO com `mensagem` "Prolongamento!" (`periodo` = 1), pontapé de saída,
  15' + intervalo (INTERVALO, `periodo` = 2) + 15'. O relógio vai de 90' a 120' (`minuto`).
- Empate no fim do prolongamento: fase INTERVALO ("Penáltis!", 2,5 s) e depois cada chute é uma fase **PENALTI** (a fase fica PENALTI
  durante toda a marcação, também com a bola no ar; usar `estado.desempate` e `estado.bolaParada` para saber o resto). No fim a fase é FIM.
  O jogo ao vivo não conta estes chutes como golos nem remates.
- Tudo o resto (golo, intervalo, FIM) como antes.

## Lances parados jogáveis

`estado.bolaParada` (`BolaParada`): `tipo` (`TipoParada`: PONTAPE_SAIDA, PONTAPE_BALIZA, CANTO, LIVRE, LANCAMENTO, PENALTI),
`casa` (true = quem cobra é a casa), `x`/`y` da bola, `cobradorId` e `cobradorNumero` (o mesmo `id` de `JogadorEmCampo`),
`doUtilizador` (a equipa do utilizador cobra), `tempoRestante` (s; passados `TEMPO_PARADA_UTILIZADOR` = 10 s a IA cobra por ele),
`distanciaBaliza`, `direcaoBaliza` (radianos), `podeRematar` (penálti, ou livre a menos de 32 m), `barreira`, `desempate`,
`guardaRedesDoUtilizador` (penálti do adversário: o utilizador escolhe o mergulho).

Comando: `partida.executarParada(ComandoParada(...)): Boolean` (false se não há lance do utilizador ou ainda não passaram 0,4 s).

```kotlin
ComandoParada(
    acao = AcaoParada.AUTO,   // PASSE, CRUZAR, REMATAR; AUTO: canto = cruzar, livre perto = rematar, penálti = rematar, o resto = passe
    direcao = null,           // radianos (0 = +x); null = para a baliza (ou para a área, no canto)
    forca = 0.5f,             // 0..1: distância/velocidade; no penálti, força a mais tira pontaria
    colegaId = -1,            // id de um colega: passa/cruza diretamente para ele
    mira = 0f,                // -1..1: posição na baliza ao longo do eixo y (livre direto e penálti)
    altura = 0.3f,            // 0..1: rasteiro ... ao ângulo
)
```

Penálti do adversário com guarda-redes do utilizador: `partida.escolherMergulho(-1 | 0 | +1)` antes do chute (eixo y; 0 = fica ao centro).
Vale para o próximo penálti e escolhe-se de novo a cada um (também nos de desempate).

Os controlos antigos (`Comando.passe`/`remate` com joystick e força) continuam a cobrar os lances parados do utilizador.
Livres a menos de 32 m têm barreira (2 a 4 jogadores a 9,15 m). Lançamentos e cantos são cobrados pelo jogador mais perto; cantos de IA
cruzam para a área.

## Lances de regras (relato)

`partida.lances: List<Lance>` (todos, por ordem; `Lance(minuto, tipo, casa, jogadorId, texto)`; `casa` = equipa a quem favorece, ou null).
`TipoLance`: FALTA, VANTAGEM, VANTAGEM_ANULADA, FORA_DE_JOGO, LANCAMENTO, CANTO, LIVRE, PENALTI, PONTAPE_BALIZA, FIM_DOS_90,
INICIO_PROLONGAMENTO, INTERVALO_PROLONGAMENTO, FIM_PROLONGAMENTO, INICIO_PENALTIS, PENALTI_MARCADO, PENALTI_DEFENDIDO, PENALTI_FORA,
FIM_PENALTIS. Os golos, cartões, lesões e substituições continuam nos `eventos` do relatório; `estado.mensagem` mostra o texto
curto de cada lance durante uns segundos ("Fora de jogo: X", "Vantagem!", "Penálti de ABC: X"...).

## Regras

- **Fora de jogo**: regista-se quem estava adiantado (para lá do penúltimo adversário, da bola e do meio-campo) quando a bola é jogada;
  se esse jogador for o primeiro a tocar na bola, apita-se livre para o adversário no sítio onde recebeu. Não há fora de jogo em
  pontapés de baliza, lançamentos e cantos, nem se um adversário tocar na bola antes. A IA evita-o conforme a dificuldade
  (Fácil 0,55 ... Lendário 1,0).
- **Vantagem**: numa falta fora da área, se a equipa que a sofreu fica com a bola e a jogada é prometedora, o árbitro deixa seguir
  3 s. Se perder a bola nesse tempo, volta-se à falta (livre no sítio dela, `VANTAGEM_ANULADA`). Falta na área = penálti, sem vantagem.
  Os cartões registam-se logo (eventos).
- **Penáltis**: mira e altura (utilizador) ou escolha da IA; o guarda-redes decide o lado antes do remate (a IA tem ~45% de acertar,
  mais com bom guarda-redes/dificuldade); a física decide o resto (poste, defesa, fora).
- **Prolongamento e desempate**: só com `exigencia`. Série de 5 chutes alternados, termina logo que já não há como virar; depois
  morte súbita ronda a ronda. A equipa que começa é sorteada; chuta-se sempre para a mesma baliza.

## IA e dificuldade (E3)

Linha defensiva comum que sobe e desce com a bola (mais alta com a dificuldade e a mentalidade), marcação individual do adversário
mais próximo da zona (do lado da própria baliza), pressão só dentro de um raio com contenção (aproxima-se do lado da baliza) e
contra-pressão nos Difícil/Lendário, avançados que esperam na linha de fora de jogo, abertura na largura na saída de bola
e pontapés de baliza curtos (conforme `saidaCurta`). Detalhes em `Defesa.kt`, `Ia.kt` e `Dificuldade.kt`.

## Para a app (resumo do que ligar)

1. Passar `dificuldade` e `exigencia` em `FabricaPartida.criar`.
2. Ler `estado.bolaParada` e, se `doUtilizador`, mostrar a mira (direção/força, e mira/altura nos remates) e chamar `executarParada`.
3. Mostrar o guarda-redes a escolher o lado quando `guardaRedesDoUtilizador` e chamar `escolherMergulho`.
4. Mostrar `estado.desempate` (bolinhas verdes/vermelhas), `estado.vantagem` e, se `numeroDeLances` aumentou, os novos `partida.lances`.
5. Tratar `periodo` (prolongamento) e a fase INTERVALO entre períodos.
