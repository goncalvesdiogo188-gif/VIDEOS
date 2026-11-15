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

As imagens na pasta `capturas/` do repositório mostram como cada ecrã deve aparecer.

## Como editar a base de dados
Os ficheiros estão em `app/src/main/assets/dados/`:
- `equipas/<PAÍS>.json`: nome, sigla, cidade, estádio, reputação, liga onde joga.
- `jogadores/<PAÍS>.json`: um jogador por linha, com `"equipa"` a indicar o id do clube.
- `competicoes.json`: nomes das ligas, número de descidas.

Regras do JSON: texto entre aspas `"assim"`, vírgula entre elementos, **sem** vírgula a seguir ao último. Se houver um erro, a app mostra um aviso a vermelho com o nome do ficheiro.

Cada liga deve manter o número de equipas que tem agora (se mudares uma equipa de liga, muda outra em sentido contrário).

**Por agora**, depois de editar é preciso voltar a compilar a app (Parte B). Importar ficheiros JSON diretamente no telemóvel, sem computador, chega na fase do Editor (Fase 10).

**Atenção:** a ferramenta `gerarBaseDados` apaga as edições a equipas e jogadores. Não precisas de a correr.
