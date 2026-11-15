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

### Fase 1
1. Abre a app. Aparece a tabela da "Portugal – Liga Lusitana" a zeros.
2. Toca em **Jornada** algumas vezes: a tabela muda. No separador **Jogos** vês os jogos da jornada.
3. Toca em **Época**: a época vai até ao fim. A verde está o campeão (ou quem sobe), a vermelho quem desce.
4. Toca em **10 épocas** e vê o separador **Histórico**.
5. Toca no nome da liga (em cima) e escolhe "Portugal – Liga Lusitana 2": confirma que as equipas que subiram/desceram trocaram de divisão.
6. Toca numa equipa da tabela para ver o plantel.
7. Roda o telemóvel: deitado, a tabela mostra também os golos marcados e sofridos.
8. **Recomeçar** volta à primeira época.

### Fase 2
1. Toca em **Jornada** e abre o separador **Jogos**. Toca num jogo: abre o relato, com golos, cartões, lesões e substituições.
2. No jogo aberto, vê os separadores **Estatísticas** (posse, remates, cantos...) e **Notas** (nota de cada jogador).
3. Toca em **▶ Ver em direto**: o jogo volta a passar minuto a minuto, com o marcador a mudar. **Saltar para o fim** acaba logo.
4. Fecha o jogo e usa as setas **◀ ▶** para ver jornadas anteriores.
5. Simula mais jornadas e abre o separador **Jogadores**: melhores marcadores, assistências e notas médias da liga.
6. Toca numa equipa da tabela: cada jogador mostra jogos (J), golos (G), assistências (A) e nota. Quem está lesionado ou castigado aparece a vermelho.
7. Na mesma janela, muda a **Formação** e a **Mentalidade** da equipa (por exemplo 5-3-2 e Defensiva), fecha e simula uma época: essa equipa deve marcar e sofrer menos golos. **Auto** devolve a decisão ao computador.
8. No **Histórico** aparece agora também o melhor marcador de cada época.

## Como editar a base de dados
Os ficheiros estão em `app/src/main/assets/dados/`:
- `equipas/<PAÍS>.json`: nome, sigla, cidade, estádio, reputação, liga onde joga.
- `jogadores/<PAÍS>.json`: um jogador por linha, com `"equipa"` a indicar o id do clube.
- `competicoes.json`: nomes das ligas, número de descidas.

Regras do JSON: texto entre aspas `"assim"`, vírgula entre elementos, **sem** vírgula a seguir ao último. Se houver um erro, a app mostra um aviso a vermelho com o nome do ficheiro.

Cada liga deve manter o número de equipas que tem agora (se mudares uma equipa de liga, muda outra em sentido contrário).

**Por agora**, depois de editar é preciso voltar a compilar a app (Parte B). Importar ficheiros JSON diretamente no telemóvel, sem computador, chega na fase do Editor (Fase 10).

**Atenção:** a ferramenta `gerarBaseDados` apaga as edições a equipas e jogadores. Não precisas de a correr.
