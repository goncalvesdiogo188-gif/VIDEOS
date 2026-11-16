# Guia: instalar, abrir, testar

Escrito para quem nunca programou. O jogo é uma aplicação Android nativa (Kotlin).

## Parte A — Experimentar no telemóvel (sem instalar ferramentas)

Sempre que o código muda, o GitHub compila o jogo sozinho (separador **Actions**, tarefa "Compilar APK") e guarda o APK em `apk/futebol-carreira.apk`.

1. No telemóvel Android, abre o repositório no GitHub (com a tua conta), escolhe o ramo `ccr-c95fc920-6su81c` e vai a `futebol-carreira/apk/futebol-carreira.apk`.
2. Toca em **Download** (ou "Download raw file").
3. Abre o ficheiro descarregado. O Android pergunta se permites instalar apps desta origem: aceita (**Definições → Permitir desta fonte**) e toca em **Instalar**.
4. Se aparecer um aviso do Play Protect ("app desconhecida"), escolhe **Instalar na mesma**. É normal em apps que não vêm da Play Store.
5. Abre a app **Futebol Carreira**.

Para atualizar numa fase seguinte basta repetir: o APK novo instala por cima do antigo.
Requisito: Android 8.0 ou mais recente.

## Parte B — Abrir o projeto no computador (opcional)

Só precisas disto se quiseres ver/alterar o código ou editar a base de dados.

### 1. Instalar o Android Studio
1. Vai a <https://developer.android.com/studio> e descarrega o **Android Studio**.
2. Instala com as opções por defeito. No primeiro arranque escolhe **Standard**: ele descarrega sozinho o Android SDK e o Java (JDK). Não é preciso instalar mais nada à parte.

### 2. Descarregar o projeto
- **Sem git (mais simples):** no GitHub, escolhe o ramo `ccr-c95fc920-6su81c`, clica em **Code → Download ZIP** e extrai.
- **Com git:** `git clone` do repositório e `git checkout ccr-c95fc920-6su81c`.

### 3. Abrir e correr
1. No Android Studio: **Open** e escolhe a pasta **`futebol-carreira`** (a que tem o ficheiro `settings.gradle.kts`).
2. Espera pelo "Gradle sync" (barra em baixo). Na primeira vez descarrega bibliotecas e pode demorar 5 a 15 minutos.
3. Para correr no **telemóvel**:
   - No telemóvel: **Definições → Acerca do telefone**, toca 7 vezes em **Número de compilação**; depois em **Opções de programador** liga a **Depuração USB**.
   - Liga o telemóvel por cabo e aceita "Permitir depuração USB?".
   - No Android Studio, escolhe o teu telemóvel na lista do topo e carrega no botão verde ▶ (**Run**).
4. Para correr num **telemóvel virtual**: **Device Manager → Create Virtual Device**, escolhe um modelo (ex.: Pixel 7), descarrega uma imagem do sistema, e carrega em ▶.

### 4. Criar um APK no computador
**Build → Generate App Bundles or APKs → Generate APKs**. O ficheiro aparece em `app/build/outputs/apk/debug/`.

### 5. Correr os testes automáticos
No separador **Terminal** do Android Studio (em baixo):
- Windows: `gradlew.bat :nucleo:test`
- Mac/Linux: `./gradlew :nucleo:test`

No fim deve aparecer `BUILD SUCCESSFUL`.

## Parte C — Como testar cada fase

As Fases 1 e 2 (ligas, tabelas, jogos em detalhe, táticas) testam-se agora dentro da carreira: está tudo nos separadores **Liga** e **Plantel**.

### Fase 3
1. Abre a app: aparece o menu principal. Toca em **Nova carreira**.
2. Escolhe **Treinador**, escreve o teu nome, escolhe país, liga e clube, e toca em **Começar carreira**.
3. No separador **Início** vês o próximo jogo (adversário, data, casa ou fora). Toca em **Jogar**: o jogo passa em direto; **Saltar para o fim** acaba logo. Fecha o jogo.
4. De volta ao **Início**: a data avançou, aparece o último resultado, a tua classificação e uma mensagem com o resumo do jogo.
5. **Plantel**: muda a formação e a mentalidade (valem a partir do jogo seguinte).
6. **Liga**: tabela (o teu clube tem uma seta), jogos de cada jornada, melhores jogadores e histórico. No topo podes escolher outra liga.
7. **Mais → Calendário**: todos os jogos do teu clube com datas; toca num já jogado para rever o relato.
8. **Mais → Simular o resto da época**: a época vai até ao fim. No **Início** aparece o resumo e o botão para começar a época seguinte.
9. Fecha a aplicação por completo e volta a abri-la: toca em **Continuar** e confirma que está tudo como deixaste.
10. **Mais → Gravar e sair para o menu**, depois **Nova carreira** com **Jogador**: escolhe posição e clube. No **Início** aparece o cartão «O teu jogador» e no plantel o teu nome está destacado. (Nesta via ainda não mexes na tática; o modo completo chega na Fase 9.)
11. No menu, **Carregar carreira** mostra as duas carreiras; **Apagar** elimina uma.

### Fase 4
1. Começa uma **Nova carreira** como treinador (as carreiras antigas continuam com os clubes inventados).
2. No **Início** aparece o cartão **Objetivo da direção**, com as barras de confiança da direção e dos adeptos. Joga alguns jogos e vê as barras mexer.
3. Em **Plantel**, no «Onze para o próximo jogo», toca num lugar e escolhe outro jogador. Joga e confirma nas **Notas** do jogo que ele foi titular. **Escolha automática** devolve a escolha ao adjunto.
4. Para ver um despedimento depressa: escolhe um clube grande, põe os piores jogadores no onze e joga várias jornadas. Quando fores despedido, o **Início** mostra propostas de outros clubes; aceita uma para continuar.
5. No fim de uma boa época podem aparecer propostas de clubes maiores: podes **Aceitar** ou **Recusar e ficar**.

### Fase 5
1. Começa uma **Nova carreira** como treinador (as antigas não têm contratos nem contas a sério). A época começa em julho, com o mercado aberto.
2. Abre **Mercado**: em cima está o dinheiro em caixa. Usa os filtros (**Posso pagar**, **Sem clube**, posição) e toca num jogador para abrir a ficha com os atributos.
3. Na ficha, toca em **Pagar o que pedem**: o jogador aparece no teu **Plantel** e o dinheiro sai da caixa. Experimenta também **Oferecer** menos noutro jogador: o clube pode recusar e fechar a porta.
4. Em **Plantel**, toca num jogador teu e escolhe **Pôr à venda** ou **Emprestar**.
5. Em **Mais → Finanças** vês as receitas e as despesas da época; **Aumentar lugares** faz obras no estádio.
6. Joga até setembro: o mercado fecha (só podes contratar jogadores sem clube) e volta a abrir em janeiro. Nas mensagens aparecem negócios dos outros clubes.
7. Simula até ao fim da época e começa a seguinte: a mensagem «Contratos e empréstimos» diz quem renovou e quem voltou de empréstimo.

### Fase 6
1. Começa uma **Nova carreira** como treinador (as antigas não têm academia nem instalações).
2. Abre **Mais → Clube**: escolhe o foco e a intensidade do treino, vê o nível das instalações e melhora uma, se tiveres dinheiro.
3. Na **Academia**, toca num jovem e escolhe **Subir ao plantel principal**: passa a aparecer em **Plantel**.
4. Em **Olheiros**, escolhe um país e toca em **Enviar olheiro**. Joga 4 ou 5 jornadas: chega a mensagem «Relatório do olheiro» e a lista aparece em **Mais → Clube**.
5. Abre a ficha de um jovem de outro clube: o potencial aparece como intervalo (por exemplo «72–80») até um olheiro o observar.
6. Em **Plantel**, por baixo de cada jogador aparece a forma e a moral. Joga uns meses: no início de cada mês chega uma mensagem «Treino» com quem evoluiu.
7. Começa a época seguinte: entra uma geração nova na academia.

### Fase 7
1. Começa uma **Nova carreira** como treinador (as carreiras antigas não têm taças, Europa nem seleções; só aparecem a partir da época seguinte). Para ver a Europa logo, escolhe um clube grande.
2. Joga até ao início de agosto: no primeiro sábado há a **Supertaça** do teu país (na primeira época, entre os dois clubes com mais reputação). Se o teu clube for um deles, o jogo aparece no **Início**.
3. Abre **Liga** e, no topo, escolhe a **Taça** do teu país (por exemplo, Taça de Portugal). Vês as rondas, quem joga e quem passou.
4. Joga até uma quarta-feira de fim de setembro: aparece um jogo da taça. No **Início** e em **Mais → Jogos** o jogo mostra o nome da competição e a ronda. Se acabar empatado, há prolongamento («a.p.») e, se for preciso, penáltis («g.p.»).
5. Toca num jogo já jogado do teu clube (em **Mais → Jogos**): abre o relato.
6. Se o teu clube se apurou, vê a **Liga dos Campeões** ou a **Liga Europa** no seletor da **Liga**: a tabela da fase de liga tem zonas de cores (oitavos direitos, play-off, eliminado). Os jogos são à terça (Campeões) ou à quinta (Liga Europa).
7. Abre **Mais → Seleções**: vês o ranking, os torneios, os grupos e as convocatórias. Em setembro joga-se a **Liga das Nações**; quando um jogador teu é chamado, chega uma mensagem.
8. Usa **Mais → Simular o resto da época** e vê o fim: finais das taças em maio, final da Champions no último sábado de maio, e o histórico de vencedores em cada competição.
9. Começa mais uma época e confirma que a taça, a Europa e a Liga das Nações voltam a começar. Em 2028 há o **Euro** e em 2030 o **Mundial**, em **Mais → Seleções**.

### Fase 8
1. Começa uma **Nova carreira** como treinador num clube com derbies (por exemplo, SL Benfica, FC Porto, Sporting CP, Real Madrid, Liverpool ou AC Milan). As carreiras antigas também carregam: só ficam sem notícias até ao primeiro jogo.
2. No **Início**, o bloco **Objetivo da direção** tem agora uma linha **Ambiente** (Eufórico, Bom, Normal, Tenso ou Hostil). Joga alguns jogos e vê-o mudar com os resultados e a confiança dos adeptos.
3. Joga a primeira jornada: aparece o bloco **Notícias** com as manchetes mais recentes (do teu clube e do resto do mundo). Toca em **Ver todas** para a lista completa (as últimas 80) e em **Fechar** para voltar.
4. Procura em **Mais → Jogos** um jogo contra um rival (por exemplo, o Derby de Lisboa). Quando é o próximo jogo, o cartão do Início mostra o nome do derby a laranja.
5. Joga o derby: a mensagem do jogo começa com o nome do derby, o estádio fica cheio, e a confiança dos adeptos e da direção sobe (vitória) ou desce (derrota) mais do que num jogo normal. A moral dos jogadores também (vê em **Plantel**).
6. Usa **Mais → Simular o resto da época** e abre as notícias: devem aparecer goleadas, surpresas, hat-tricks, mudanças de liderança, o título decidido, descidas, finais das taças e noites europeias, e mercado.
7. Grava, sai para o menu e volta a carregar a carreira: as notícias continuam lá.

As imagens na pasta `capturas/` do repositório mostram como cada ecrã deve aparecer.

## Como editar a base de dados
Os ficheiros estão em `app/src/main/assets/dados/`:
- `equipas/<PAÍS>.json`: nome, sigla, cidade, estádio, reputação, liga onde joga.
- `jogadores/<PAÍS>.json`: um jogador por linha, com `"equipa"` a indicar o id do clube.
- `competicoes.json`: nomes das ligas, número de descidas.
- `rivalidades.json`: os derbies, um por linha, com `"a"` e `"b"` (ids dos dois clubes), `"nome"` e `"intensidade"` de 1 a 3. Se um id não existir, o derby é ignorado.

Regras do JSON: texto entre aspas `"assim"`, vírgula entre elementos, **sem** vírgula a seguir ao último. Se houver um erro, a app mostra um aviso a vermelho com o nome do ficheiro.

Cada liga deve manter o número de equipas que tem agora (se mudares uma equipa de liga, muda outra em sentido contrário).

**Por agora**, depois de editar é preciso voltar a compilar a app (Parte B). Importar ficheiros JSON diretamente no telemóvel, sem computador, chega na fase do Editor (Fase 10).

**Atenção:** a ferramenta `gerarBaseDados` apaga as edições a equipas e jogadores. Não precisas de a correr.
