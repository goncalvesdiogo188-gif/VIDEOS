# FÁBRICA — Reels automáticos "O teu cérebro explica a vida adulta"

Fábrica de vídeos para uma conta faceless de Instagram e TikTok, com dois personagens originais:
**Neurão** (cérebro rosa-escuro, convencido) e **Zé Neurónio** (cérebro rosa-claro, alegre, fecha a lição).

> Estado: **Fase 1 concluída** (base). O comando `fabrica novo "tema"` chega na Fase 2.

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
| `cenarios.py` | Fundos: `sala`, `quarto`, `loja`. |
| `guiao.py` | Lê um guião JSON. |
| `video.py` | Guião → Reel 1080x1920 com legendas palavra-a-palavra, boca animada e som provisório. |
| `carrossel.py` | Guião → carrossel de imagens 1080x1350. |
| `render.py` | Converte os desenhos SVG em imagens. |
| `verificar.py` | Diz-te o que falta instalar. |

## Instalação (Windows) — só uma vez
1. **Python**: instala de <https://www.python.org/downloads/> e **marca a caixa "Add Python to PATH"**.
2. **ffmpeg**: abre o *PowerShell* e escreve `winget install Gyan.FFmpeg`. Fecha e volta a abrir o PowerShell.
3. **Bibliotecas**: dentro da pasta do projeto, escreve `python -m pip install -r requirements.txt`.
4. **Claude Code** (para a Fase 2, escrever guiões sozinho): segue <https://code.claude.com/docs> e faz login com a tua conta Claude.
5. Confirma: `python -m fabrica.verificar` — tudo deve aparecer `[OK]`.

## Como usar (por agora)
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
