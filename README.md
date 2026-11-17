# FÁBRICA — Reels automáticos "O teu cérebro explica a vida adulta"

Fábrica de vídeos para uma conta faceless de Instagram e TikTok, com dois personagens originais:
**Neurão** (cérebro rosa-escuro, convencido) e **Zé Neurónio** (cérebro rosa-claro, alegre, fecha a lição).

> Estado: **Fase 3 concluída** (voz real). Próximo: correr todos os dias sozinho (Fase 4).

## Pastas
| Pasta / ficheiro | Para que serve |
|---|---|
| `BIBLIA.md` | As regras da conta (tom, personagens, estrutura, proibições). Os guiões seguem isto. |
| `guioes/` | Um ficheiro `.json` por vídeo (as falas, o cenário, a legenda, as hashtags). |
| `fabrica/` | O código. |
| `out/` | Tudo o que a fábrica produz (vídeos, carrosséis). Não vai para o GitHub. |
| `assets/` | Fontes de letra (licença livre OFL) e, mais tarde, sons. |
| `arquivo/` | Projeto antigo, guardado à parte. Não é usado pela fábrica. |

### O código (`fabrica/`)
| Ficheiro | O que faz |
|---|---|
| `personagens.py` | Desenha o Neurão e o Zé em SVG: 7 expressões, 5 poses, boca que fala, piscar de olhos. |
| `cenarios.py` | O mundo: interior de um cérebro futurista (rede neuronal com sinais e chão de néon). 4 zonas: `comando` (Centro de Comando), `memorias` (Arquivo de Memórias), `dopamina` (Centro da Recompensa), `sono` (Zona do Sono). Os nomes antigos `sala`, `quarto`, `loja` ainda funcionam. |
| `guiao.py` | Lê um guião JSON. |
| `video.py` | Guião → Reel 1080x1920 com legendas palavra-a-palavra e boca animada pela voz. |
| `voz.py` | Vozes reais com edge-tts (grátis, precisa de internet). Usa a duração real do áudio e o tempo de cada palavra. |
| `carrossel.py` | Guião → carrossel de imagens 1080x1350. |
| `render.py` | Converte os desenhos SVG em imagens. |
| `verificar.py` | Diz-te o que falta instalar. |
| `guionista.py` | Escreve guiões com o Claude Code em modo não interativo (`claude -p`). |
| `validar.py` | Verifica as regras (tempo, palavras por fala, texto). |
| `pacote.py` | Junta tudo na pasta final. |
| `__main__.py` | O comando `fabrica`. |
| `fabrica.bat` / `fabrica.sh` (na raiz) | Atalhos para correr o comando no Windows / noutros sistemas. |
| `tests/` | Testes automáticos. |

## Instalação (Windows) — só uma vez
1. **Python**: instala de <https://www.python.org/downloads/> e **marca a caixa "Add Python to PATH"**.
2. **ffmpeg**: abre o *PowerShell* e escreve `winget install Gyan.FFmpeg`. Fecha e volta a abrir o PowerShell.
3. **Bibliotecas**: dentro da pasta do projeto, escreve `python -m pip install -r requirements.txt`.
4. **Claude Code** (necessário para escrever os guiões automaticamente): segue <https://code.claude.com/docs>. Depois de instalar, abre `claude` uma vez e faz login com a tua conta Claude. A fábrica chama-o com `claude -p`.
5. Confirma: `python -m fabrica.verificar` — tudo deve aparecer `[OK]`.

## Comandos
| Comando | O que faz |
|---|---|
| `fabrica novo "tema"` | O Claude escreve o guião, a fábrica valida (máx. 30 s, máx. 12 palavras por fala, texto revisto) e corrige sozinha até 3 vezes. Cria a pasta `out/AAAA-MM-DD_tema/`. |
| `fabrica novo --guiao guioes/ficheiro.json` | Usa um guião já escrito por ti. |
| `fabrica semana` | Escolhe 7 temas diferentes (evita os já feitos, guardados em `out/historico.json`) e gera os 7 pacotes. |
| `fabrica refazer out/PASTA` | Volta a gerar o pacote a partir do `guiao.json` dessa pasta (útil depois de mudares uma fala à mão). Aceita várias pastas. |
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

## Vozes
| Personagem | Voz (edge-tts) | Ajuste |
|---|---|---|
| Neurão | `pt-PT-DuarteNeural` (Portugal) | 6% mais lento, tom mais grave |
| Zé Neurónio | `pt-BR-AntonioNeural` (Brasil) | 8% mais rápido, tom mais agudo |

- Muda-se em `fabrica/voz.py`, no topo (`VOZES`).
- Os áudios ficam guardados em `out/cache_voz/` e não são pedidos duas vezes.
- Se a voz passar dos 30 s, a fábrica acelera a fala sozinha.
- O edge-tts usa um serviço gratuito da Microsoft que não é oficial: pode deixar de funcionar ou mudar sem aviso. Se falhar, o pacote não é criado e aparece uma mensagem clara.

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
- Vídeo com **no máximo 30 s** e **no máximo 12 palavras por fala**.
