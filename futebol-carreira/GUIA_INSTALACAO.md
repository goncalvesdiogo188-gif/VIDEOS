# Guia: instalar, abrir, testar e exportar

Escrito para quem nunca programou. Segue pela ordem.

## Parte A — O mínimo para testar no computador (10 minutos)

### 1. Instalar o Godot 4
1. Vai a <https://godotengine.org/download> e descarrega o **Godot Engine 4.5 ou mais recente** (versão **normal**, NÃO a ".NET").
2. Vem num ficheiro ZIP. Extrai-o para uma pasta à tua escolha (ex.: `Documentos/Godot`). Não há instalador: o programa é o próprio ficheiro `Godot_v4.x-stable_...`.
3. Faz duplo clique para abrir. No Windows, se aparecer "O Windows protegeu o seu PC", clica em **Mais informações → Executar assim mesmo**.

### 2. Descarregar o projeto
- **Sem git (mais simples):** na página do repositório no GitHub, escolhe o ramo `ccr-c95fc920-6su81c`, clica em **Code → Download ZIP** e extrai.
- **Com git:** `git clone` do repositório e `git checkout ccr-c95fc920-6su81c`.

O jogo está na pasta **`futebol-carreira`**.

### 3. Abrir o projeto
1. No Godot aparece o **Gestor de Projetos**. Clica em **Importar**.
2. Navega até à pasta `futebol-carreira`, escolhe o ficheiro `project.godot` e confirma.
3. Na primeira vez o Godot demora uns segundos a preparar o projeto.
4. (Opcional) Para pôr o Godot em português: **Editor → Editor Settings → Interface → Editor → Editor Language**.

### 4. Correr o jogo
- Carrega em **F5** (ou no botão ▶ no canto superior direito).
- Para fechar, fecha a janela do jogo ou carrega em **F8**.

## Parte B — Preparar a exportação para Android (faz-se uma vez)

Podes deixar isto para mais tarde; só é mesmo preciso quando quiseres experimentar no telemóvel.

### 1. Instalar o JDK 17 (Java)
1. Vai a <https://adoptium.net/temurin/releases/?version=17>.
2. Descarrega o **JDK 17** para o teu sistema (no Windows, o ficheiro `.msi`) e instala com as opções por defeito.
3. Toma nota da pasta onde ficou (Windows: algo como `C:\Program Files\Eclipse Adoptium\jdk-17...`).

### 2. Instalar o Android SDK (através do Android Studio)
1. Descarrega e instala o **Android Studio**: <https://developer.android.com/studio>.
2. Abre-o e deixa o assistente inicial instalar os componentes "Standard".
3. No ecrã de entrada: **More Actions → SDK Manager**.
4. No separador **SDK Platforms**, marca **Android 15 (API 35)**.
5. No separador **SDK Tools**, marca **Show Package Details** (em baixo) e depois:
   - Android SDK Build-Tools **35.0.0**
   - Android SDK Platform-Tools
   - Android SDK Command-line Tools (latest)
   - NDK (Side by side) e CMake (só são precisos para opções avançadas; podem ficar instalados)
6. Clica em **Apply** e espera.
7. Toma nota do **Android SDK Location** que aparece no topo dessa janela (Windows: `C:\Users\<tu>\AppData\Local\Android\Sdk`).

### 3. Dizer ao Godot onde estão o Java e o Android SDK
1. No Godot: **Editor → Editor Settings** (Definições do Editor).
2. Na lista da esquerda: **Export → Android**.
3. Preenche **Java SDK Path** com a pasta do JDK 17 e **Android SDK Path** com a pasta do SDK.

### 4. Instalar os modelos de exportação
1. No Godot: **Editor → Manage Export Templates** (Gerir Modelos de Exportação).
2. Clica em **Download and Install** e espera (cerca de 1 GB).

### 5. Criar a configuração de exportação
1. **Project → Export…** (Projeto → Exportar).
2. **Add… → Android**.
3. Se aparecerem avisos a vermelho em baixo, lê-os: normalmente indicam um caminho errado nos passos anteriores.
4. No separador **Resources**, em "Filters to export non-resource files", escreve `*.json` (para a base de dados ir dentro do jogo).
5. Para testes, não é preciso mexer em mais nada (o Godot usa uma "chave de depuração" automática).

### 6. Experimentar no telemóvel
**Forma rápida (cabo USB):**
1. No telemóvel: **Definições → Acerca do telefone** e toca 7 vezes em **Número de compilação** para ativar as "Opções de programador".
2. Em **Opções de programador**, liga a **Depuração USB**.
3. Liga o telemóvel ao computador por cabo e aceita a pergunta "Permitir depuração USB?".
4. No Godot aparece um ícone de Android no canto superior direito: clica nele e o jogo é instalado e aberto no telemóvel.

**Forma manual (ficheiro APK):**
1. **Project → Export… → Android → Export Project**, guarda como `futebol.apk` (deixa "Export With Debug" ligado).
2. Copia o APK para o telemóvel e abre-o (terás de permitir "instalar apps de fontes desconhecidas").

> Publicar na Play Store exige uma chave de assinatura própria e um ficheiro AAB. Isso fica para a Fase 12.

## Parte C — Como testar cada fase

### Fase 1
1. Abre o projeto e carrega em **F5**.
2. Carrega em **Jornada seguinte** algumas vezes: a tabela e os resultados mudam.
3. Carrega em **Época inteira**: a época vai até ao fim. A verde está o campeão (ou quem sobe), a vermelho quem desce.
4. Carrega em **10 épocas** e vê o separador **Histórico**.
5. Muda de liga na caixa de escolha (ex.: "Portugal – Liga Lusitana 2") e confirma que as equipas que subiram/desceram trocaram de divisão.
6. Toca numa equipa da tabela para ver o plantel.
7. **Teste de edição:** fecha o jogo, abre `dados/equipas/POR.json` num editor de texto (ex.: Bloco de Notas), muda o `"nome"` de uma equipa, guarda e volta a carregar em F5. O nome novo deve aparecer.

Se alguma coisa falhar, olha para o painel **Output** (Saída) em baixo no Godot e copia-me as mensagens a vermelho.

## Como editar a base de dados
- `dados/equipas/<PAÍS>.json`: nome, sigla, cidade, estádio, reputação, liga onde joga.
- `dados/jogadores/<PAÍS>.json`: um jogador por linha, com `"equipa"` a indicar o id do clube.
- `dados/competicoes.json`: nomes das ligas, número de descidas.
- Regras do JSON: texto entre aspas `"assim"`, vírgula entre elementos, **sem** vírgula a seguir ao último. Se houver um erro, o jogo avisa no painel Output com o ficheiro e a linha.
- Cada liga deve ter o mesmo número de equipas que tem agora (se mudares uma equipa de liga, muda outra em sentido contrário).
- **Atenção:** a ferramenta `ferramentas/gerar_base_dados.gd` apaga as tuas edições a equipas e jogadores. Não precisas de a correr.
