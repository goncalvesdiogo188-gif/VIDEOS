# BÍBLIA DA CONTA — "O teu cérebro explica a vida adulta"

Este ficheiro é a regra de ouro. Tudo o que a fábrica escreve tem de cumprir isto.

## 1. A conta
- **Nicho:** o cérebro explica a vida adulta — dinheiro, hábitos, procrastinação, sono, compras por impulso, redes sociais, motivação, rotinas.
- **Tom:** humor leve, situações do dia a dia em que toda a gente se revê. Primeiro rir, depois aprender.
- **Público:** Portugal e Brasil. Escrever em **português neutro**: frases curtas, sem calão de um só país.
  - Evitar: "fixe", "bué", "giro" (PT) e "legal", "cara", "a gente vai" (BR).
  - Preferir palavras comuns aos dois: "ótimo", "incrível", "vamos", "dinheiro".
  - Em vez de "telemóvel" (PT) ou "celular" (BR), dizer **"o telefone"**. Em vez de "ecrã"/"tela", evitar a palavra.
  - Tratar o espectador por **"tu"** só em frases sem verbo conjugado difícil; preferir construções neutras ("Já te aconteceu?", "Experimenta isto").
- **Meta:** 10 000 seguidores com Reels de 20–30 s.

## 2. As personagens (originais — nunca copiar séries, estúdios ou estilos existentes)
| | **Neurão** | **Zé Neurónio** |
|---|---|---|
| Aspeto | Cérebro rosa-escuro, óculos retangulares, laço azul | Cérebro rosa-claro, redondo, uma "antena" de neurónio na cabeça |
| Personalidade | Convencido, acha-se génio, explica tudo com ar superior | Atrapalhado, alegre, distraído |
| Papel | Começa a explicação, muitas vezes exagera ou engana-se | **Fecha sempre a lição** com uma frase simples e certeira |
| Voz | Mais grave, segura | Mais aguda, animada |

## O mundo
Os dois personagens vivem **dentro de um cérebro futurista**: uma rede neuronal com sinais de luz a viajar e um chão de grelha de néon. O mundo tem 8 zonas. Cada vídeo passa-se numa só.

| Zona (`cenario`) | Como é | Serve para temas como |
|---|---|---|
| `comando` | Centro de Comando: ecrãs holográficos com medidores FOCO e ENERGIA | procrastinação, foco, produtividade, motivação, rotinas |
| `memorias` | Arquivo de Memórias: prateleiras de esferas de memória a brilhar | lembranças, aprender, esquecer, estudar |
| `dopamina` | Centro da Recompensa: estrelas a subir e letreiro de néon "RECOMPENSA" | compras por impulso, vícios, recompensas, scroll |
| `sono` | Zona do Sono: luz fraca, lua holográfica, Zzz | sono, cansaço, noite, insónias |
| `alarme` | Centro de Alarme: sirenes a girar, sinal de alerta, luz vermelha a pulsar | ansiedade, stress, medo, preocupações, pensar demais |
| `cofre` | Cofre do Cérebro: porta de cofre, moedas, medidor SALDO a descer | dinheiro, poupar, gastos, salário (nunca conselhos de investimento) |
| `social` | Praça Social: balões de conversa, corações, notificações "99+" | redes sociais, comparação, opinião dos outros, amizades |
| `habitos` | Fábrica de Hábitos: engrenagens a rodar e passadeira com caixas | hábitos, rotinas, disciplina, mudar comportamentos |

- Os nomes antigos `sala`, `quarto` e `loja` ainda funcionam, mas usa os novos.
- Escolher a zona que combina com o tema e variar entre vídeos. Em caso de dúvida: `comando`.

## 3. Estrutura de cada Reel (20–30 s)
1. **Gancho (0–3 s):** pergunta ou situação que prende. Ex.: "Porque é que compras coisas que não precisas?"
2. **Explicação do Neurão (2–3 falas):** a "ciência" com ar de génio (dopamina, recompensa, hábito...).
3. **Reviravolta cómica do Zé (1–2 falas):** confusão, exemplo absurdo, piada.
4. **Lição final do Zé (1 fala):** dica prática e simples. É a frase que as pessoas guardam.
- Total: **5 a 7 falas**, no máximo **12 palavras por fala**, vídeo com **no máximo 30 s**.

## 4. Regras de conteúdo
- **Proibido:** promessas de ganhos ("vais ficar rico"), conselhos de investimento específicos (ações, cripto, produtos financeiros), conselhos médicos, diagnósticos.
- Ciência simples e verdadeira. Se não houver certeza, falar de forma geral ("o cérebro gosta de recompensas rápidas").
- Nada de política, religião, insultos ou humor que goze com grupos de pessoas.
- Nunca usar nomes, personagens, frases ou músicas de séries, filmes ou marcas.

## 5. Carrossel (extra, 1080x1350)
- 5 a 7 slides a partir do mesmo guião: capa (gancho) → 3 a 5 slides de explicação → slide final com a lição + "Guarda para não esquecer".

## 6. Legenda e hashtags
- Legenda: 1 frase de gancho + 1 pergunta para comentários + "Segue para mais".
- **5 hashtags:** 2 do nicho em português, 2 do tema, 1 geral. Ex.: `#cerebro #psicologia #procrastinacao #habitos #vidaadulta`.

## 7. Formato do guião (ficheiro JSON em `guioes/`)
```json
{
  "tema": "compras por impulso",
  "titulo": "Porque compras o que não precisas?",
  "cenario": "dopamina",
  "falas": [
    {"quem": "neurao", "texto": "...", "expressao": "convencido", "pose": "aponta"},
    {"quem": "ze", "texto": "...", "expressao": "alegre", "pose": "segura", "adereco": "carrinho", "acao": "salta"}
  ],
  "carrossel": [{"titulo": "...", "texto": "..."}],
  "legenda": "...",
  "hashtags": ["#...", "#...", "#...", "#...", "#..."]
}
```
- `quem`: `neurao` ou `ze`. A **última fala é sempre do `ze`**.
- `expressao`: neutro, convencido, alegre, confuso, surpreendido, triste, pensativo.
- `pose`: normal, aponta, bracos_ar, mao_queixo, encolhe, bracos_cruzados, mao_testa, vitoria, acena, segura.
- `adereco` (opcional): objeto na mão de quem fala: telefone, carrinho, almofada, moeda, despertador, lampada, cafe, livro. Usar em 1 a 3 falas, quando ajuda a piada.
- `acao` (opcional): salta (alegria, ideia), treme (medo, susto), aproxima (anda até ao outro). No máximo 2 por vídeo.
- `cenario`: comando, memorias, dopamina, sono, alarme, cofre, social, habitos.

### Limites obrigatórios (a fábrica verifica)
- `titulo`: no máximo **60 caracteres**.
- `carrossel`: **3 a 5 itens**; o `texto` de cada item tem no máximo **120 caracteres**.
- `hashtags`: **exatamente 5**, em minúsculas, sem acentos e sem espaços (ex.: `#procrastinacao`).
- Cada `fala` termina com `.` `!` `?` ou `…`.
- Vídeo entre **15 e 30 s**. Apontar a **22–27 s**, cerca de **44 a 52 palavras** no total (com a voz real, cada palavra demora cerca de 0,5 s, contando as pausas; mais de 54 palavras passa dos 30 s).
