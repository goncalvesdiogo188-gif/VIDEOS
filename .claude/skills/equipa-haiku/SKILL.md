---
name: equipa-haiku
description: Equipa Haiku. O Sonnet 5.5 coordena e põe vários subagentes Haiku 5.5 a trabalhar ao mesmo tempo, em tarefas pequenas e bem delimitadas. Usar quando o utilizador pede a "equipa haiku", escreve /equipa-haiku, ou quer um trabalho feito em paralelo de forma rápida e barata.
model: claude-sonnet-5-5
---

# Equipa Haiku

O **Sonnet 5.5** é o chefe de equipa: planeia, escreve os pedidos, verifica e junta o trabalho. Os **subagentes Haiku 5.5** fazem as tarefas, todos ao mesmo tempo.

O chefe não faz ele próprio o trabalho das tarefas; só junta, corrige pequenos erros e verifica.

> Se esta sessão não estiver a correr com o Sonnet 5.5 (o campo `model` acima pode não ser respeitado em todas as versões), avisar o utilizador. Dizer-lhe que pode mudar com `/model sonnet` e continuar na mesma.

## Como fazer

1. **Perceber o pedido e o projeto.** Ler o README ou o plano do projeto e os ficheiros principais, o suficiente para escrever pedidos precisos.

2. **Dividir em tarefas pequenas.** O Haiku é rápido e barato, mas precisa de tarefas mais pequenas do que o Sonnet. Fazer 4 a 8 tarefas, cada uma com:
   - um objetivo concreto, que se faça em poucos ficheiros;
   - a lista exata dos ficheiros que pode criar ou mudar, e dos que não deve tocar;
   - o resultado esperado: o que deve existir, que testes devem passar, que comando correr.

   Evitar tarefas de "desenho" abertas (escolher a arquitetura, juntar módulos grandes). Essas ficam para o chefe, ou para um subagente Sonnet se forem grandes.

3. **Lançar todas as tarefas na mesma resposta**, com a ferramenta Agent:
   - `model: "haiku"`
   - `run_in_background: true`
   - `isolation: "worktree"` em cada tarefa que mexa em código. No máximo uma tarefa trabalha na pasta principal.

4. **Cada pedido tem de bastar sozinho**, porque o subagente começa sem saber nada. Deve dizer:
   - onde está o projeto, a língua e o estilo do código e dos textos;
   - quem mais está a trabalhar ao mesmo tempo e em quê, para não mexer nos mesmos ficheiros;
   - os passos concretos, se possível com nomes de funções e de ficheiros;
   - como compilar e testar, e os problemas conhecidos do ambiente;
   - como fazer o commit (sem push) e com que linhas de autoria;
   - o que relatar no fim: ficheiros mudados, testes corridos e resultado, o que ficou por fazer.

5. **Enquanto correm**: não repetir o trabalho deles. Dizer ao utilizador, numa tabela curta, quem está a fazer o quê. Não inventar resultados antes de chegar a notificação de cada um.

6. **Verificar com mais cuidado do que com o Sonnet.** Para cada tarefa entregue:
   - ler o diff;
   - correr os testes;
   - confirmar que fez o que foi pedido, sem atalhos escondidos.

   Se algo falhar, mandar o mesmo subagente corrigir (SendMessage com o erro exato). Se falhar duas vezes, corrigir diretamente ou passar a tarefa a um subagente Sonnet.

7. **Juntar.** Juntar os ramos das cópias separadas, resolver os conflitos, ligar as partes, correr todos os testes, atualizar a documentação, fazer o commit e o push.

8. **Relatar ao utilizador** o que foi feito, o que foi verificado, o que falhou e como foi resolvido, e o que ficou de fora.
