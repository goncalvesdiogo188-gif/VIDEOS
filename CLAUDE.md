# FÁBRICA — contexto para o Claude

Fábrica de Reels totalmente automatizada para uma conta faceless de Instagram e TikTok, para Portugal e Brasil.
Personagens originais: **Neurão** (cérebro rosa-escuro, convencido) e **Zé Neurónio** (cérebro rosa-claro, atrapalhado, fecha a lição), que vivem dentro da cabeça de uma pessoa (o Dono). Meta: 10 000 seguidores, orçamento 0 €, só ferramentas gratuitas.

## Quem é o utilizador
- O Diogo, em Portugal, escreve em **português europeu**. **Não é programador.**
- Explica tudo em linguagem simples e diz-lhe **exatamente** o que fazer, passo a passo (com os cliques ou comandos). Usa Windows 11.
- Antes de decisões grandes, faz perguntas curtas. Não inventes: se não testaste uma coisa, diz que não testaste. No fim de cada tarefa diz o que foi feito, onde estão os ficheiros e qual o próximo passo.
- Trabalha diretamente, sem subagentes. Não gastes tempo a montar vídeos sem ele pedir: um vídeo demora minutos. Para julgar histórias usa `fabrica guiao "tema"` (só texto).
- Não consegues ouvir áudio nem ver movimento: diz isso, e pede-lhe a opinião sobre vozes, música e ritmo.

## Regras da conta (a BIBLIA.md manda)
Lê `BIBLIA.md` antes de escrever ou mudar conteúdo. Resumo: português neutro (sem palavras que diferem entre Portugal e Brasil), humor leve, nada de promessas de ganhos, conselhos de investimento específicos ou conselhos médicos, nunca copiar personagens, nomes ou estilos de séries/estúdios/marcas.

## Como está organizado
| Pasta/ficheiro | O que é |
|---|---|
| `fabrica/` | O código (Python). `__main__.py` é o comando `fabrica`. |
| `fabrica/historia.py` | Escreve guiões em vários passos: conceito → ficha → roteiro → correção → editor → reescrita. |
| `fabrica/guionista.py` | Chama o Claude Code (`claude -p`, prompt por stdin, sem ferramentas). |
| `fabrica/validar.py` | Regras do guião (120 s máx., 12 palavras por fala, vocabulário neutro...). |
| `fabrica/video.py`, `voz.py`, `musica.py`, `sfx.py` | Vídeo 1080x1920, vozes edge-tts, música e efeitos feitos em código. |
| `fabrica/personagens.py`, `cenarios.py`, `janela.py`, `aderecos.py` | Desenho em SVG: personagens, 8 zonas do cérebro, Janela do Dono, objetos. |
| `fabrica/web.py`, `fabrica/pagina/index.html` | A página da fábrica (`fabrica web`): criar, biblioteca, agenda, conceitos, evolução. Só biblioteca padrão; trabalhos em segundo plano. |
| `fabrica/aprendizagem.py` | Análise de cada vídeo + notas do Diogo → regras que entram nos prompts seguintes (`out/aprendizagem.json`). O Claude não vê nem ouve o vídeo. |
| `fabrica/pacote.py`, `diario.py` | O pacote final em `out/` e o agendamento diário. |
| `conteudo/` | Banco de 26 conceitos reais e exemplos de ouro (modelo de qualidade). |
| `guioes/` | Guiões `.json`. `out/` é o resultado (não vai para o Git). `arquivo/` é o projeto antigo (ignorar). |

Comandos: `fabrica guiao "tema"`, `fabrica novo "tema"`, `fabrica novo --guiao ficheiro.json`, `fabrica semana`, `fabrica diario`, `fabrica refazer out/PASTA`, `fabrica agendar --hora 09:00`, `fabrica web` (`--rede` para o telemóvel), `fabrica analisar out/PASTA`, `fabrica verificar`. No Windows: `py -m fabrica ...` (o `.\fabrica` também serve, mas o Windows pode bloquear `.bat`).

## Testes
`python -m unittest discover -s tests` (no Windows: `py -m unittest discover -s tests`). Corre-os depois de qualquer mudança. Os testes não usam internet, nem Claude, nem render (há um Claude falso em `tests/fake_claude.py`).

## Armadilhas já encontradas
- **Windows 11, Controlo Inteligente de Aplicações** bloqueia ficheiros `.bat` da internet. Nunca sugerir desligá-lo. O agendamento chama o Python diretamente.
- Os `.bat` têm de ficar com finais de linha CRLF e só ASCII (`.gitattributes` trata disso).
- O Claude Code instala em `~/.local/bin`, que pode não estar no PATH: `guionista.achar_claude()` procura lá.
- O edge-tts é um serviço gratuito não oficial; pode mudar ou falhar. Precisa de internet.
- A estimativa de duração (`video.planear`) está calibrada com as vozes reais (0,124 s por sílaba, 0,77 s por pontuação, pausas 0,4 s + 0,25 s após ?/!); se mudares as vozes, volta a calibrar.
- Layout 1080x1920: título (máx. 2 linhas) e legenda (máx. 3 linhas) encolhem a letra; a Janela do Dono fica fixa em y=745..975; a fita do conceito em y≈1585.
- Só o Instagram permite publicação automática oficial (ver `PUBLICACAO.md`); nada de publicação foi implementado sem aprovação.

## Git
Desenvolver no ramo atual e fazer commit com mensagens em português. Não criar pull requests sem o utilizador pedir.
