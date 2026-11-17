# FÁBRICA — Reels automáticos "O teu cérebro explica a vida adulta"

Fábrica de vídeos para uma conta faceless de Instagram e TikTok, com dois personagens originais:
**Neurão** (cérebro rosa-escuro, convencido) e **Zé Neurónio** (cérebro rosa-claro, alegre, fecha a lição).

> Estado: **Fase 6 concluída**: música de fundo, 8 zonas, poses novas, objetos e movimento. Publicação: ver `PUBLICACAO.md` (por agora publicas tu, a partir das pastas em `out/`).

## A página da fábrica (só para ti)
`py -m fabrica web` abre http://127.0.0.1:8765 no browser. Com `--rede` também abre no telemóvel (mesmo Wi-Fi; o endereço leva uma chave). Tem cinco separadores: **Criar** (guião ou vídeo a partir de um tema), **Biblioteca** (ver o vídeo, copiar a legenda, dar nota), **Agenda**, **Conceitos** (editar o banco) e **Evolução** (o que a fábrica aprendeu). Depois de cada vídeo a IA analisa o guião e tira regras que entram nos guiões seguintes; as tuas notas e comentários valem mais. A IA não vê nem ouve o vídeo.

## Pastas
| Pasta / ficheiro | Para que serve |
|---|---|
| `BIBLIA.md` | As regras da conta (tom, personagens, estrutura, proibições). Os guiões seguem isto. |
| `guioes/` | Um ficheiro `.json` por vídeo (as falas, o cenário, a legenda, as hashtags). |
| `fabrica/` | O código. |
| `out/` | Tudo o que a fábrica produz (vídeos, carrosséis). Não vai para o GitHub. |
| `conteudo/` | O banco de conceitos reais (`conceitos.json`) e os exemplos de ouro (`exemplos/`) que servem de modelo de qualidade ao Claude. |
| `assets/` | Fontes de letra (licença livre OFL) e, mais tarde, sons. |
| `arquivo/` | Projeto antigo, guardado à parte. Não é usado pela fábrica. |

### O código (`fabrica/`)
| Ficheiro | O que faz |
|---|---|
| `personagens.py` | Desenha o Neurão e o Zé em SVG: 7 expressões, 10 poses, boca que fala, piscar de olhos, andar. |
| `aderecos.py` | Objetos que os personagens seguram: telefone, carrinho, almofada, moeda, despertador, lâmpada, café, livro. |
| `cenarios.py` | O mundo: interior de um cérebro futurista (rede neuronal com sinais e chão de néon). 8 zonas: `comando` (Centro de Comando), `memorias` (Arquivo de Memórias), `dopamina` (Centro da Recompensa), `sono` (Zona do Sono), `alarme` (Centro de Alarme), `cofre` (Cofre do Cérebro), `social` (Praça Social), `habitos` (Fábrica de Hábitos). Os nomes antigos `sala`, `quarto`, `loja` ainda funcionam. |
| `guiao.py` | Lê um guião JSON. |
| `video.py` | Guião → Reel 1080x1920 com legendas palavra-a-palavra e boca animada pela voz. |
| `voz.py` | Vozes reais com edge-tts (grátis, precisa de internet). Usa a duração real do áudio e o tempo de cada palavra. |
| `carrossel.py` | Guião → carrossel de imagens 1080x1350. |
| `render.py` | Converte os desenhos SVG em imagens. |
| `verificar.py` | Diz-te o que falta instalar. |
| `historia.py` | Escreve o guião em vários passos: conceito real, ficha, roteiro, editor, reescrita. |
| `janela.py` | A Janela do Dono: painel com a situação da pessoa e fita do conceito. |
| `guionista.py` | Escreve guiões com o Claude Code em modo não interativo (`claude -p`). |
| `validar.py` | Verifica as regras (tempo, palavras por fala, texto). |
| `pacote.py` | Junta tudo na pasta final. |
| `musica.py` | Música de fundo original feita em código, um estilo por zona. Baixa sozinha enquanto alguém fala. |
| `diario.py` | O pacote do dia e o agendamento no Windows. |
| `__main__.py` | O comando `fabrica`. |
| `fabrica.bat` / `fabrica.sh` (na raiz) | Atalhos para correr o comando no Windows / noutros sistemas. |
| `tests/` | Testes automáticos. |

## Instalação (Windows) — só uma vez
**Antes:** precisas de uma conta Claude paga (Pro ou Max). O plano gratuito não inclui o Claude Code.

1. **Descarregar a fábrica.** Com a sessão do GitHub iniciada, abre
   <https://github.com/goncalvesdiogo188-gif/VIDEOS/archive/refs/heads/ccr-c4ae36fc-0x3vrf.zip>.
   Descarrega um ficheiro `.zip`.
2. **Desbloquear o zip.** Botão direito no `.zip` > **Propriedades**. Se aparecer a caixa **Desbloquear**, marca-a e carrega OK.
3. **Extrair.** Botão direito > **Extrair tudo**. Põe a pasta num caminho simples, sem espaços nem acentos, por exemplo `C:\fabrica`.
   O `instalar.bat` tem de ficar diretamente dentro dessa pasta.
4. **Instalar.** Clica duas vezes em **`instalar.bat`**. Ele:
   - instala o Python, o ffmpeg e o Claude Code, se faltarem (usa o `winget`, que já vem no Windows 10 e 11);
   - instala as bibliotecas (desenho e vozes);
   - verifica tudo, e no fim pergunta se queres agendar o vídeo diário.

   Se o Windows mostrar "O Windows protegeu o computador", carrega em **Mais informações** e depois **Executar mesmo assim**.

   Se aparecer **"O Controlo Inteligente de Aplicações bloqueou um ficheiro"** (Windows 11):
   - **não desligues** essa proteção;
   - abre o PowerShell nesta pasta (barra de endereço > escreve `powershell` > Enter);
   - escreve `Get-ChildItem -Recurse | Unblock-File` e carrega Enter;
   - clica outra vez no `instalar.bat`.

   Se continuar bloqueado, usa a "Instalação à mão" abaixo e corre os comandos com `py -m fabrica ...` em vez de `.\fabrica ...`.
5. **Quando ele pedir para fechar e voltar a abrir**, fecha a janela e clica outra vez no `instalar.bat`. Pode ser preciso 2 ou 3 vezes, porque o Windows só reconhece programas novos numa janela nova.
6. **Login do Claude (só uma vez).** Abre o PowerShell, escreve `claude` e segue o login no navegador. Depois escreve `/exit`.
7. No fim, o `instalar.bat` deve mostrar tudo `[OK]` e "Tudo pronto!".

Para confirmar mais tarde: `.\fabrica verificar`. Este comando também testa se o login do Claude continua feito.

<details><summary>Instalação à mão (se o instalar.bat falhar)</summary>

1. **Python 3.10 ou mais recente**: instala de <https://www.python.org/downloads/> e **marca a caixa "Add Python to PATH"**.
2. **ffmpeg**: no PowerShell, `winget install Gyan.FFmpeg`. Fecha e volta a abrir o PowerShell.
3. **Bibliotecas**: dentro da pasta do projeto, `python -m pip install -r requirements.txt`.
4. **Claude Code**: no PowerShell, `irm https://claude.ai/install.ps1 | iex` (instalador oficial; ver <https://code.claude.com/docs/en/setup>). Depois escreve `claude` e faz login.
5. Confirma: `.\fabrica verificar`.
</details>

## Comandos
| Comando | O que faz |
|---|---|
| `fabrica novo "tema"` | O Claude escreve o guião em vários passos (ver "Como o guião é escrito"), a fábrica valida (máx. 120 s, máx. 12 palavras por fala, texto revisto) e corrige sozinha até 3 vezes. Cria a pasta `out/AAAA-MM-DD_tema/`. |
| `fabrica novo --guiao guioes/ficheiro.json` | Usa um guião já escrito por ti. |
| `fabrica semana` | Escolhe 7 temas diferentes (evita os já feitos, guardados em `out/historico.json`) e gera os 7 pacotes. |
| `fabrica refazer out/PASTA` | Volta a gerar o pacote a partir do `guiao.json` dessa pasta (útil depois de mudares uma fala à mão). Aceita várias pastas. |
| `fabrica diario` | Cria o pacote do dia (só um por dia; se já existir, não faz nada). Pensado para correr agendado. `--forcar` cria outro. |
| `fabrica agendar --hora 09:00` | Windows: põe o `fabrica diario` a correr sozinho todos os dias a essa hora. |
| `fabrica desagendar` | Windows: deixa de correr sozinho. |
| `fabrica verificar` | Diz o que falta instalar. |

Cada pacote (`out/AAAA-MM-DD_tema/`) tem:
- `video.mp4` e `capa.png`
- `carrossel/` (as imagens)
- `legenda.txt` (legenda + 5 hashtags, pronta a copiar)
- `guiao.json` e `info.txt`

### Como correr no Windows
1. Abre a pasta do projeto no Explorador de Ficheiros.
2. Clica na barra de endereço, escreve `powershell` e carrega Enter.
3. No PowerShell escreve: `.\fabrica novo "procrastinação"` (o PowerShell exige o `.\`).
4. No Prompt de Comandos clássico (cmd) funciona sem o `.\`: `fabrica novo "procrastinação"`.

### Quanto tempo demora
- Cada pacote demora alguns minutos: escrever o guião com o Claude ~1 min, vozes ~10 s, vídeo ~1–2 min (depende do computador).
- Uma semana inteira: ~15–25 min.

## Todos os dias, sozinho (Windows)
1. Confirma primeiro que `fabrica novo "teste"` funciona bem no teu computador.
2. No PowerShell, na pasta do projeto: `.\fabrica agendar --hora 09:00` (escolhe a hora que quiseres).
3. Para confirmar: Iniciar > escreve **Agendador de Tarefas** > Biblioteca do Agendador de Tarefas > tarefa **FABRICA diario**. Clica com o botão direito > **Executar** para testar já.
4. Todos os dias aparece uma pasta nova `out/AAAA-MM-DD_tema/`. Tu só abres, vês e publicas.
5. O que aconteceu fica escrito em `out/logs/diario_AAAA-MM-DD.log`. Se não aparecer o pacote do dia, é aí que se vê porquê.

Para parar: `.\fabrica desagendar`.

**Limitações (importante):**
- O computador tem de estar **ligado** e com a tua **sessão iniciada** (pode estar bloqueado). Desligado não corre.
- Se estiver desligado à hora marcada, corre **assim que o ligares** nesse dia ou depois.
- Se estiver em **suspensão**, o Windows tenta acordá-lo à hora marcada. Só funciona se os "temporizadores de ativação" estiverem ligados nas Opções de Energia. Não funciona com o computador desligado nem em hibernação, em muitos portáteis.
- Precisa de **internet** (Claude e vozes) e de o **Claude Code** continuar com o login feito.
- Durante 2 a 3 minutos abre-se uma janela preta e o computador fica mais lento. É normal.
- Cada dia gasta uma pequena parte do teu limite de utilização do Claude (3 a 5 pedidos: escolher o tema, escrever, rever e, se preciso, corrigir).
- Se o Claude ou as vozes falharem, a fábrica tenta outro tema uma vez. Se falhar de novo, não há pacote nesse dia (fica no registo).

## Como o guião é escrito (histórias com sentido)
Em vez de pedir ao Claude "escreve um guião" de uma vez, a fábrica segue o que faz uma equipa de argumento (`fabrica/historia.py`). Cada passo é um pedido ao Claude:
1. **Conceito:** escolhe um conceito real do banco `conteudo/conceitos.json` (26 conceitos, cada um com a explicação correta, o grau de certeza e o que não exagerar). Assim a ciência é verdadeira e os vídeos não se repetem. `fabrica semana` e `fabrica diario` escolhem sempre conceitos ainda não usados e de zonas variadas.
2. **Ficha da história:** um Dono com nome, 4 a 6 cenas que escalam (hora, desenho, legenda curta), reviravolta, piada do Zé, lição e *callback*.
3. **Roteiro:** o diálogo, a partir da ficha, com dois exemplos de ouro (`conteudo/exemplos/`) como modelo de qualidade.
4. **Correção automática** das regras (palavras por fala, horas, vocabulário neutro).
5. **Editor:** dá nota de 1 a 5 a 7 critérios (coerência, história, humor, ciência, lição, ritmo, imagem). Se alguma nota for inferior a 4 ou a média a 4,0, o roteiro é reescrito uma vez com as correções pedidas. Fica a melhor versão.

O `info.txt` de cada pacote mostra o Dono, o conceito e as notas do editor. Cada vídeo gasta cerca de 5 a 7 pedidos ao Claude e demora 2 a 4 minutos a escrever.
Para voltar ao método antigo, de um só pedido: variável de ambiente `FABRICA_GUIAO_SIMPLES=1`.

### Janela do Dono
Os personagens vivem na cabeça de uma pessoa e comentam o que ela faz. Uma janela holográfica mostra a situação (hora, desenho, legenda) e a imagem conta o contexto, para as falas ficarem só com a piada e a ciência. Quando o Neurão diz o nome do conceito, aparece uma fita dourada por baixo dos personagens. Há 17 desenhos para as cenas (`fabrica/janela.py`).

## Vozes
| Personagem | Voz (edge-tts) | Ajuste |
|---|---|---|
| Neurão | `pt-PT-DuarteNeural` (Portugal) | 12% mais lento, tom mais grave |
| Zé Neurónio | `pt-BR-AntonioNeural` (Brasil) | 3% mais lento, tom mais agudo |

- Muda-se em `fabrica/voz.py`, no topo (`VOZES`).
- Os áudios ficam guardados em `out/cache_voz/` e não são pedidos duas vezes.
- **Entoação por emoção:** cada fala é dita de forma diferente conforme a `expressao` (convencido = mais grave e lento, alegre = mais agudo e vivo, confuso = a subir, triste = lento e baixo, etc.). Ajusta-se em `PROSODIA`, no topo de `fabrica/voz.py`.
- **Pausas:** 0,4 s entre falas, mais 0,25 s depois de `?` ou `!`.
- **Ritmo:** cerca de 0,5 s por palavra, contando as pausas. Por isso 100 a 170 palavras dão 50 a 90 s.
- Se a voz passar dos 120 s, a fábrica acelera a fala até 12%. Se mesmo assim não couber, o pacote não é criado.
- O edge-tts usa um serviço gratuito da Microsoft que não é oficial: pode deixar de funcionar ou mudar sem aviso. Se falhar, o pacote não é criado e aparece uma mensagem clara.

## Poses, objetos e movimento
- **Poses:** normal, aponta, bracos_ar, mao_queixo, encolhe, bracos_cruzados, mao_testa, vitoria (dança), acena, segura.
- **Objetos** (campo `"adereco"` numa fala): telefone, carrinho, almofada, moeda, despertador, lampada, cafe, livro. Aparecem na mão de quem fala só durante essa fala.
- **Ações** (campo `"acao"` numa fala): `salta`, `treme`, `aproxima` (anda até ao outro e volta depois).
- No início de cada vídeo os dois entram a andar dos lados.
- O Zé aparece espelhado, para os gestos e objetos dele ficarem virados para o centro.
- Ver tudo numa imagem: `python -m fabrica.personagens` (grava `out/folha_poses_aderecos.png`).

## Vida dos personagens e efeitos sonoros
- **Quem fala:** a cabeça inclina-se e dá um "toque" a cada palavra (mais forte nas longas), as sobrancelhas sobem nas sílabas fortes e os braços gesticulam.
- **Quem ouve:** reage a meio da fala do outro (surpresa depois de "!", dúvida depois de "?", espanto perante o Neurão convencido) e acena de leve no fim de cada frase.
- **Em repouso:** os dois balançam; o Zé nunca pára quieto.
- **Câmara:** aproxima-se mais na última fala (a lição) e treme um pouco nos saltos.
- **Efeitos sonoros** (`fabrica/sfx.py`, feitos em código): salto = boing, aproximar = whoosh, lâmpada = ding, despertador = toque, mão na testa = bonk, vitória = fanfarra, olá = pip, surpresa = "uh".

## Música de fundo
| Zona | Estilo |
|---|---|
| `comando` | eletrónica com energia (116 bpm) |
| `memorias` | sonhadora, acordes suaves e sinos (90 bpm) |
| `dopamina` | saltitante, palmas e acordes a contratempo (124 bpm) |
| `sono` | calma, sem bateria (66 bpm) |
| `alarme` | tensa, baixo insistente (100 bpm) |
| `cofre` | funk leve (108 bpm) |
| `social` | pop alegre com palmas (120 bpm) |
| `habitos` | mecânica, arpejo tipo máquina (104 bpm) |

- É composta e sintetizada em código: não usa músicas de ninguém, por isso não há problemas de direitos de autor.
- Fica cerca de 10 a 15 dB abaixo das vozes e baixa ainda mais enquanto alguém fala.
- O volume final do vídeo é normalizado para cerca de -14 LUFS, o nível habitual nas redes sociais.
- Ouvir as 8 músicas: `python -m fabrica.musica` (grava em `out/teste/`).
- Desligar num vídeo: `"musica": false` no guião. Desligar em tudo: variável de ambiente `FABRICA_SEM_MUSICA=1`.
- Volume da música: `VOLUME` no topo de `fabrica/musica.py`.

## Modo manual (peças soltas, para testes)
Na pasta do projeto:
```
python -m fabrica.video guioes/exemplo_compras_impulso.json
python -m fabrica.carrossel guioes/exemplo_compras_impulso.json
python -m fabrica.personagens          (folha com todas as expressões e poses)
```
Os resultados aparecem em `out/teste/`.

## Regras importantes
- Personagens, cenários, sons e música são **feitos de raiz em código**. Nunca copiar séries, estúdios ou marcas.
- Nada de promessas de ganhos nem conselhos de investimento.
- Vídeo com **no máximo 120 s** (o normal são 50 a 90 s) e **no máximo 12 palavras por fala**.
