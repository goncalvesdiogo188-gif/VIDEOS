---
name: subagentes-paralelos
description: Executa um trabalho grande (uma fase do jogo, uma funcionalidade com várias partes) dividindo-o em tarefas e pondo subagentes com o modelo Sonnet a fazê-las todas ao mesmo tempo. Usar quando o utilizador diz "avança", pede uma fase nova, ou pede para usar subagentes; e sempre que um pedido tenha várias partes que possam ser feitas em paralelo.
---

# Subagentes em paralelo (dica do utilizador)

O utilizador quer que o trabalho seja feito **por subagentes com o modelo Sonnet, todos ao mesmo tempo**, e não uma tarefa de cada vez. O agente principal planeia, lança, verifica e junta; não faz ele próprio o trabalho das tarefas.

## Como fazer

1. **Dividir o trabalho** em 3 a 5 tarefas com fronteiras claras (por exemplo: núcleo da parte A, núcleo da parte B, dados, ecrãs, documentação). Ler primeiro o plano (`PLANO.md`, `PROGRESSO.md`) e os ficheiros principais, o suficiente para escrever bons pedidos.
2. **Lançar todas as tarefas na mesma resposta**, com a ferramenta Agent:
   - `model: "sonnet"`
   - `run_in_background: true`
   - `isolation: "worktree"` para cada tarefa que mexa em código. Pode haver no máximo uma tarefa a trabalhar na pasta principal.
3. **Evitar conflitos ao juntar**: em cada pedido, dizer quem mais está a trabalhar ao mesmo tempo e em quê, e pedir que:
   - ponha o trabalho em ficheiros novos sempre que puder;
   - não mexa nos ficheiros centrais que outra tarefa está a mudar (nomeá-los);
   - exponha uma API pequena e clara para ser ligada na junção.
   A tarefa dos ecrãs define os seus próprios modelos de dados para os ecrãs, com valores por omissão, e um modo de demonstração com dados falsos para as capturas. Na junção, esses modelos são ligados ao núcleo.
4. **Cada pedido tem de bastar sozinho**, porque o subagente começa sem saber nada. Deve dizer:
   - onde está o projeto, a língua e o estilo (português europeu, com acentos);
   - que ficheiros ler e os requisitos concretos;
   - os testes a criar e a correr;
   - como compilar, e os problemas conhecidos do ambiente;
   - como fazer o commit (sem push) e com que linhas de autoria;
   - o que deve relatar no fim: ficheiros mudados, API, decisões, testes e atalhos que tomou.
5. **Enquanto correm**: não repetir o trabalho deles. Avisar o utilizador, numa tabela curta, de quem está a fazer o quê. Não inventar resultados antes de chegar a notificação de cada um.
6. **Juntar e verificar** quando todos acabarem:
   - juntar os ramos das cópias separadas e resolver os conflitos;
   - ligar as APIs (calendário, carreira, gravação, ecrãs);
   - correr todos os testes e ver as capturas;
   - atualizar a documentação, fazer o commit e o push, e enviar o APK.
   A junção também pode ser entregue a um subagente Sonnet, se for grande.
7. **Relatar ao utilizador** o que foi feito, o que foi verificado e o que ficou de fora, sem esconder falhas.

## Neste projeto (Futebol Carreira)

- O código está em `futebol-carreira/`: o núcleo em `nucleo/`, que tem testes `FaseNTest.kt`, e os ecrãs em `app/`.
- Comandos de compilação e testes: `./gradlew --console=plain --max-workers=2 :nucleo:test`, `:app:compileDebugKotlin` e `:app:testDebugUnitTest` (este tira as capturas).
- O Maven responde por vezes 429 (demasiados pedidos). Se aparecer "Could not find …jar" ou "Could not resolve", repetir uma vez com `--max-workers=1 --refresh-dependencies`.
- A gravação (`Gravador`) tem de continuar a ler as carreiras antigas: os campos novos levam sempre um valor por omissão.
