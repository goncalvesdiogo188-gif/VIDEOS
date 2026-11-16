# Godot dentro da aplicação — resultado do protótipo C0

Escrito a 16 de novembro de 2026. Em linguagem simples.

## Resumo

**A integração compila e o APK é pequeno (25,7 MB). O que ainda não foi possível provar é o arranque e a fluidez num telemóvel a sério**, porque aqui não há telemóvel nem emulador. Isso só se vê no teu telemóvel, e o critério de decisão da fase C depende disso.

## O que funcionou (e foi verificado aqui)

1. **O projeto Godot existe** (pasta `godot/`): campo verde às riscas com linhas, duas balizas, uma bola, um jogador (cápsula com cabeça), luz com sombras e uma câmara de transmissão que segue a bola. Há um joystick virtual (toca em qualquer sítio e arrasta).
2. **O Godot 4.5.1 que está na máquina corre o projeto num ecrã virtual** e desenhou o campo (imagem: `capturas/godot-c0.png`). Corre em software, por isso os «9 im/s» que aparecem na imagem não dizem nada sobre o telemóvel.
3. **O ficheiro do jogo (`.pck`, 12 KB) exporta-se sem descarregar nada.** O comando `godot --headless --export-pack` não precisa dos pacotes de exportação de 1,2 GB, porque um `.pck` é só uma caixa com os ficheiros do projeto. Testei: o `.pck` sozinho arranca no Godot de secretária. Está guardado em `app/src/main/assets/godot/ao_vivo.pck`.
4. **A biblioteca do Godot para Android** (`org.godotengine:godot:4.5.1.stable`, Maven Central, a assinatura SHA-256 confere) entra na aplicação sem problemas. Nota: a versão tem de ser igual à do Godot que exporta o `.pck` (4.5.1, que é o que está em `/opt/godot`; o plano dizia 4.4.1, mas a 4.5.1 também existe no Maven e é a que combina com a máquina).
5. **A ponte Kotlin ↔ Godot está escrita** (`aovivo/PonteGodot.kt`), usando o mecanismo oficial de plugins do Godot:
   - o Kotlin manda a posição da bola e do jogador 60 vezes por segundo (sinal `estado`);
   - o Godot devolve o joystick (`joystick(x, z)`), o seu número de imagens por segundo (`fps`) e o tempo até à primeira imagem (`arranque`).
   - Com o joystick, o Kotlin move o jogador e a bola. Ou seja, quem «decide» é o Kotlin e o Godot só desenha e lê o toque, como no plano.
6. **O ecrã novo** `aovivo/EcraGodot.kt` arranca o Godot com esse `.pck`. Corre num processo à parte (`:godot`), por isso ao sair o Godot desaparece por inteiro.
7. **O botão «Protótipo Godot»** está em Opções → Jogos (`aovivo/BotaoPrototipoGodot.kt`, ligado em `EcraCarreira.kt` com uma linha).
8. **Compila:** `assembleDebug` e `assembleRelease` passam. Os testes (`:nucleo:test :app:testDebugUnitTest`) passam, incluindo 4 testes novos da lógica da ponte (`EstadoPrototipoTest`).
9. **O GitHub continua a compilar** sem passos novos: o `.pck` já está no repositório, o GitHub só tem de descarregar a biblioteca do Maven Central. Acrescentei um passo que verifica que o `.pck` existe, e protegi a captura `godot-c0.png` para o passo das capturas não a apagar.

## Tamanho do APK

| Versão | Tamanho |
|---|---|
| Antes (sem Godot) | 1,9 MB |
| Release com Godot, só 64 bits (arm64), bibliotecas comprimidas | **25,7 MB** |
| Release com Godot sem compressão das bibliotecas | 148 MB (por isso comprimi) |
| Debug (arm64 + x86_64 para o emulador) | cerca de 158 MB sem compressão |

O Godot em si (`libgodot_android.so`) tem 70 MB descomprimidos e ~24 MB comprimidos. Comprimir as bibliotecas dentro do APK poupa download mas ocupa mais espaço depois de instalado (cerca de 72 MB).

Só telemóveis de 64 bits (arm64) ficam suportados no release. Quase todos os telemóveis de há 5 anos para cá são.

## O que não funcionou ou ficou diferente

- **Não consegui arrancar o Godot da aplicação** (sem telemóvel nem emulador aqui). Isto quer dizer que a ligação ao nível do Android (o `.pck` ser aceite, o sinal chegar ao Godot, o toque chegar ao Kotlin) está escrita de acordo com o código-fonte da biblioteca, mas **nunca foi executada**.
- A assinatura PGP do Maven não pude verificá-la (o servidor de chaves não responde daqui); verifiquei o SHA-256 contra o publicado no Maven Central, que coincide.
- Houve dois ajustes necessários para compilar: a biblioteca declara um `FileProvider` que chocava com o da aplicação (resolvido com uma classe própria `RelatoriosFileProvider`, sem mudar o comportamento dos relatórios) e foi preciso acrescentar a biblioteca `androidx.fragment`.
- O release usa regras do ProGuard novas para o Godot (`proguard-rules.pro`). Se o release falhar no telemóvel e o debug não, o suspeito número um é isto.

## Riscos

- **Arranque lento:** o Godot carrega 70 MB de código nativo; pode passar dos 3 s em telemóveis mais fracos. É o critério que falta medir.
- **Fluidez:** escolhi o renderizador «Compatibilidade» (OpenGL), o mais seguro. Em telemóveis fracos 30 im/s com sombras deve chegar, mas não está medido.
- **Memória:** o Godot corre num processo próprio, por isso a aplicação não fica mais pesada fora do ecrã 3D, mas dentro dele somam-se os dois.
- **Tamanho:** 25,7 MB é muito mais do que 1,9 MB; está dentro do que o plano previa (30–40 MB).
- **Uma vez só por processo:** o Godot não gosta de ser aberto, fechado e reaberto no mesmo processo. O processo à parte resolve isso, mas tem de se ver no telemóvel que sair e voltar a entrar funciona.

## O que falta testar no telemóvel

1. Instalar o APK do GitHub, ir a Opções → Jogos → «Protótipo Godot».
2. Ver se aparece o campo com o jogador a vermelho e a bola, e se ao tocar e arrastar o jogador se move (isto prova a ida e volta Kotlin ↔ Godot).
3. **Arranque:** o tempo entre o toque no botão e a primeira imagem deve ser inferior a 3 s. O ecrã mostra em cima «Godot N im/s»; o tempo interno do Godot fica no registo (`adb logcat -s FutebolPonte`).
4. **Fluidez:** o número «im/s» deve manter-se em 30 ou mais durante um minuto a mexer.
5. Sair com o botão «Voltar», voltar a entrar, e confirmar que a aplicação principal continua bem.
6. Instalar por cima da versão anterior (a chave de teste é a mesma) para confirmar que não se perde a carreira.

## Se algo falhar: plano B

Se o Godot não arrancar, ou arrancar em mais de 3 s, ou ficar abaixo de 30 im/s no teu telemóvel, recomendo o **plano B: melhorar o motor OpenGL atual** (que já funciona e pesa 2 MB), em vez do Filament, que traria quase tantas dependências como o Godot. Se funcionar tudo, avançamos para C1 (a compilação já está pronta: basta voltar a exportar o `.pck` com `godot --headless --path godot --export-pack pck ../app/src/main/assets/godot/ao_vivo.pck` sempre que o projeto Godot mude).
