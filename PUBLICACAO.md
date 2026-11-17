# Publicação automática — investigação (Fase 5)

Investigação feita a 17/11/2026. **Nada de publicação foi implementado.** As regras das plataformas mudam com frequência: confirmar antes de implementar.

## Resumo
| | Instagram | TikTok |
|---|---|---|
| Publicar sozinho, oficial e grátis? | **Sim, é possível**, com trabalho de configuração | **Na prática, não** (só em modo privado) |
| O que exige | Conta Profissional (Criador ou Empresa, grátis) + app de programador da Meta em "modo de desenvolvimento" + o vídeo num endereço público na internet | Auditoria do TikTok, que **recusa** ferramentas para publicar só nas tuas próprias contas |
| Alternativa segura e grátis | Agendar no telemóvel (app do Instagram) ou no Meta Business Suite | Agendar no TikTok Studio (site) ou no Buffer (plano grátis) |

## Instagram (API oficial da Meta)
- Publica Reels: cria-se um "contentor" com `media_type=REELS` e um `video_url`, e depois publica-se.
- **O vídeo tem de estar num endereço público**: a Meta vai buscá-lo à internet ("the media must be hosted on a publicly accessible server"). O envio direto do ficheiro só existe para apps com "Facebook Login for Business". Seria preciso alojar o vídeo grátis em algum lado (por exemplo, um link público temporário).
- Só funciona com **contas profissionais** (Criador ou Empresa). Mudar é grátis, nas definições do Instagram.
- Com o "Instagram Login" não é preciso Página de Facebook. Permissão necessária: `instagram_business_content_publish`.
- **Para a tua própria conta não é preciso aprovação (App Review)**: a app fica em modo de desenvolvimento e a tua conta é adicionada como "Instagram Tester". A aprovação só é exigida para apps usadas por outras pessoas. *Isto vem de guias de terceiros e da página de App Review da Meta, que só consegui ler em parte.*
- Limite: 100 publicações por API em 24 h (a mesma página da Meta diz 50 noutro sítio). Para 1 Reel por dia, não importa.
- O acesso dura cerca de 60 dias e tem de ser renovado. A renovação pode ser automática. *Não confirmei nas páginas lidas.*
- **Risco para a conta: baixo.** É o caminho oficial.

## TikTok (Content Posting API oficial)
- Sem auditoria, tudo o que se publica fica **privado** (`SELF_ONLY`), e a conta tem de estar em modo privado. Não serve para crescer.
- A auditoria exige uma app "para um público amplo". O TikTok dá como exemplo do que **não** é aceite: *"uma ferramenta para carregar conteúdos na(s) conta(s) que tu ou a tua equipa gerem"*. É exatamente o nosso caso.
- Existe o modo "Upload": o vídeo vai para a caixa de entrada da app do TikTok como rascunho e tu acabas a publicação no telemóvel. Precisa de aprovação da permissão `video.upload`. Não consegui confirmar se uma app pessoal a obtém.
- **Conclusão:** publicar no TikTok de forma automática, oficial e grátis não é realista. Fica manual ou agendado.

## O que NÃO é seguro fazer
- **Bibliotecas não oficiais** (ex.: `instagrapi`) e **robôs que controlam o navegador ou o telemóvel**: violam os termos da Meta e do TikTok. Podem levar a bloqueios, perda de alcance ("shadowban") ou perda da conta. Os próprios autores do `instagrapi` dizem que põe a conta em risco.
- **Dar a password** a ferramentas "grátis" desconhecidas.
- Publicar muitas vezes por dia para "forçar" crescimento.

## Alternativas grátis já disponíveis (sem programar)
- **Instagram:** agendar Reels na app do Instagram, no telemóvel, ou no **Meta Business Suite**. O Business Suite precisa de uma Página de Facebook ligada. Fontes falam de agendamento até 30 ou 75 dias; não confirmei.
- **TikTok:** o **TikTok Studio** (site) tem agendador próprio. Não confirmei até quantos dias.
- **Buffer, plano grátis:** 3 canais e até 10 publicações em fila por canal. Inclui TikTok e Instagram. As fontes não concordam sobre se agenda Reels grátis.
- **Rotina sugerida:** 1 vez por semana, 20 a 30 minutos para agendar os 7 vídeos do `fabrica semana` nas duas redes.

## Fontes
- Meta — Content Publishing: https://developers.facebook.com/docs/instagram-platform/content-publishing/
- Meta — App Review (Instagram): https://developers.facebook.com/docs/instagram-platform/app-review
- TikTok — Content Sharing Guidelines: https://developers.tiktok.com/docs/en/content-sharing-guidelines
- TikTok — Content Posting API, Get Started: https://developers.tiktok.com/doc/content-posting-api-get-started
- TikTok — Upload (rascunho): https://developers.tiktok.com/doc/content-posting-api-get-started-upload-content
- Guia de terceiros (modo de desenvolvimento, tester): https://www.blotato.com/blog/instagram-posting-api
- Guia de terceiros (Standard Access): https://timetopost.co/blog/how-to-auto-post-to-instagram-api/
- Buffer, plano grátis (terceiros): https://use-apify.com/blog/buffer-free-plan-guide
- Agendar Reels grátis (terceiros): https://sociality.io/blog/schedule-instagram-reels/
- Riscos das APIs não oficiais: https://subzeroid.github.io/blog/instagrapi-vs-instaloader/
