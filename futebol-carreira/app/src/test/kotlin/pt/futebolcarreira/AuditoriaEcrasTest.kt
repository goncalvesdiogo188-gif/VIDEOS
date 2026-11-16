package pt.futebolcarreira

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import pt.futebolcarreira.aovivo.EcraAoVivo
import pt.futebolcarreira.aovivo.SessaoAoVivo
import pt.futebolcarreira.ecras.Tema
import pt.futebolcarreira.nucleo.aovivo.PartidaFalsa
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.modelos.Equipa
import java.io.File
import kotlin.random.Random

/**
 * Auditoria dos ecrãs: percorre a aplicação num telemóvel pequeno (360x640 dp) e num grande (411x914 dp),
 * sempre com a letra a 130 %, e olha para a árvore semântica de cada ecrã à procura de
 *  - texto cortado (reticências ou fora da caixa),
 *  - alvos de toque com menos de 48 dp,
 *  - conteúdo que sai do ecrã sem haver scroll.
 * O que encontra vai para a parte automática do ficheiro AUDITORIA.md (na raiz de futebol-carreira/);
 * as imagens ficam em build/outputs/roborazzi/auditoria-*.png.
 */
internal class Registo(private val perfil: String, private val rotulo: String) {
    private val achados = LinkedHashSet<String>()
    private var alvos = 0
    private var textos = 0
    private val densidade get() = RuntimeEnvironment.getApplication().resources.displayMetrics.density

    /** Vê a árvore semântica de todas as janelas visíveis (ecrã e diálogos) e regista os problemas. */
    fun analisar(regra: ComposeTestRule, ecra: String, esperar: Boolean = true) {
        if (esperar) regra.waitForIdle()
        regra.runOnUiThread {
            val raizes = regra.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
            for (raiz in raizes) {
                val foraFundo = booleanArrayOf(false)
                val foraDireita = booleanArrayOf(false)
                percorrer(raiz, raiz, ecra, false, false, foraFundo, foraDireita)
            }
        }
    }

    private fun texto(n: SemanticsNode): String {
        val proprio = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        if (!proprio.isNullOrBlank()) return proprio
        return n.children.map { texto(it) }.filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun regista(ecra: String, tipo: String, detalhe: String) {
        achados += "$ecra|$tipo|${detalhe.replace('\n', ' ').replace('|', '/')}"
    }

    private fun percorrer(n: SemanticsNode, raiz: SemanticsNode, ecra: String, sv: Boolean, sh: Boolean, foraFundo: BooleanArray, foraDireita: BooleanArray) {
        val cfg = n.config
        val scrollV = sv || cfg.contains(SemanticsProperties.VerticalScrollAxisRange)
        val scrollH = sh || cfg.contains(SemanticsProperties.HorizontalScrollAxisRange)
        val b = n.boundsInRoot
        val d = densidade
        val visivel = b.width > 0f && b.height > 0f
        // Elementos meio escondidos por um scroll (cortados pela janela) não contam para o tamanho do alvo.
        val inteiro = n.boundsInWindow.let { it.width + 1.5f >= b.width && it.height + 1.5f >= b.height }
        // Também não contam os que encostam à borda dentro de um scroll (a parte visível é só um bocado do alvo).
        // (margem de 24 dp ao lado e 140 dp em baixo, por causa do enchimento e da barra de navegação).
        val aBorda = (scrollH && (b.left <= 24f * d || b.right >= raiz.boundsInRoot.width - 24f * d)) ||
            (scrollV && (b.top <= 24f * d || b.bottom >= raiz.boundsInRoot.height - 140f * d))
        if (visivel && inteiro && !aBorda && cfg.contains(SemanticsActions.OnClick)) {
            alvos++
            val t = n.touchBoundsInRoot
            val w = t.width / d
            val h = t.height / d
            if (w < 47.5f || h < 47.5f) regista(ecra, "alvo de toque pequeno", "%.0fx%.0f dp: «%s»".format(w, h, texto(n).take(40)))
        }
        val lista = ArrayList<TextLayoutResult>()
        if (visivel && cfg.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(lista) == true) {
            val r = lista.firstOrNull()
            if (r != null) {
                textos++
                // Cortado = linhas escondidas pelo máximo de linhas, reticências, ou a parte visível (já cortada
                // pelos contentores) mais estreita do que o texto. A altura só conta se não houver scroll.
                val w = n.boundsInWindow
                val cortadoLado = w.width + 1.5f < b.width
                val cortadoAlto = !scrollV && w.height + 1.5f < b.height && w.bottom < raiz.boundsInRoot.height - 2f
                val escondido = r.multiParagraph.didExceedMaxLines || (0 until r.lineCount).any { r.isLineEllipsized(it) }
                if (escondido || cortadoLado || cortadoAlto) regista(ecra, "texto cortado", "«${r.layoutInput.text.text.take(50)}»")
            }
        }
        if (visivel && !scrollV && !foraFundo[0] && b.bottom > raiz.boundsInRoot.height + 2f) {
            foraFundo[0] = true
            regista(ecra, "sem scroll (corta em baixo)", "por exemplo «${texto(n).take(40)}»")
        }
        if (visivel && !scrollH && !foraDireita[0] && b.right > raiz.boundsInRoot.width + 2f) {
            foraDireita[0] = true
            regista(ecra, "sem scroll (corta à direita)", "por exemplo «${texto(n).take(40)}»")
        }
        for (f in n.children) percorrer(f, raiz, ecra, scrollV, scrollH, foraFundo, foraDireita)
    }

    /** Guarda os achados deste perfil e reescreve a parte automática de AUDITORIA.md. */
    fun guardar() {
        val pasta = File("build/auditoria").apply { mkdirs() }
        File(pasta, "$perfil.txt").writeText("$rotulo ($alvos alvos de toque e $textos textos verificados)\n" + achados.joinToString("\n"))
        val partes = pasta.listFiles { f -> f.name.endsWith(".txt") }.orEmpty().sortedBy { it.name }
        val auto = buildString {
            appendLine("_Gerado por `AuditoriaEcrasTest` (`./gradlew :app:testDebugUnitTest --tests '*Auditoria*'`). Não editar à mão: é reescrito de cada vez que o teste corre._")
            appendLine()
            for (p in partes) {
                val linhas = p.readLines()
                appendLine("### ${linhas.firstOrNull().orEmpty()}")
                appendLine()
                val dados = linhas.drop(1).filter { it.isNotBlank() }
                if (dados.isEmpty()) appendLine("Nada a assinalar.")
                for ((ecra, grupo) in dados.groupBy { it.substringBefore('|') }) {
                    appendLine("- **$ecra**")
                    for (g in grupo) {
                        val (_, tipo, detalhe) = g.split('|', limit = 3)
                        appendLine("  - $tipo: $detalhe")
                    }
                }
                appendLine()
            }
        }
        val ficheiro = File("../AUDITORIA.md")
        val inicio = "<!-- AUTO:INICIO -->"
        val fim = "<!-- AUTO:FIM -->"
        val atual = if (ficheiro.exists()) ficheiro.readText() else "# Auditoria dos ecrãs\n\n$inicio\n$fim\n"
        val i = atual.indexOf(inicio)
        val f = atual.indexOf(fim)
        val novo = if (i >= 0 && f > i) atual.substring(0, i + inicio.length) + "\n" + auto + atual.substring(f) else atual + "\n$inicio\n$auto$fim\n"
        ficheiro.writeText(novo)
    }
}

/** O percurso completo pela aplicação; as subclasses só mudam o tamanho do ecrã. */
abstract class AuditoriaBase(private val perfil: String, rotulo: String) {
    init {
        RuntimeEnvironment.setFontScale(1.3f)
    }

    @get:Rule
    val regra = createAndroidComposeRule<MainActivity>()

    private val registo = Registo(perfil, rotulo)
    private val jogo get() = ViewModelProvider(regra.activity)[JogoViewModel::class.java]

    private fun esperar(condicao: () -> Boolean) {
        regra.waitUntil(180_000) {
            ShadowLooper.idleMainLooper()
            condicao()
        }
    }

    /** Analisa o ecrã atual e guarda uma imagem (se só houver uma janela). */
    private fun auditar(nome: String) {
        regra.waitForIdle()
        registo.analisar(regra, nome)
        if (regra.onAllNodes(isRoot()).fetchSemanticsNodes().size == 1) {
            regra.onNode(isRoot()).captureRoboImage("build/outputs/roborazzi/auditoria-$perfil-$nome.png")
        }
    }

    /** Traz o elemento para o ecrã (se houver scroll) e toca-lhe. */
    private fun tocar(texto: String) {
        val n = regra.onNodeWithText(texto)
        try { n.performScrollTo() } catch (e: AssertionError) { /* não está numa lista que deslize */ }
        n.performClick()
        regra.waitForIdle()
    }

    /** O botão «Relatar problema» existe e está acessível (o diálogo tem uma caixa de texto e o Compose nunca fica «parado» com ela no Robolectric, por isso não se abre aqui). */
    private fun verificarBotaoRelatar() {
        val n = regra.onNodeWithText("Relatar problema")
        try { n.performScrollTo() } catch (e: AssertionError) { /* sem scroll */ }
        n.assertIsDisplayed()
    }

    @Test
    fun percorrerTodosOsEcras() {
        try {
            percurso()
        } finally {
            registo.guardar()
            // Apaga o que o teste gravou, para não interferir com os outros (partilham a pasta das gravações).
            for (g in jogo.gravacoes.toList()) {
                jogo.apagarCarreira(g.id)
                esperar { !jogo.ocupado }
            }
        }
    }

    private fun percurso() {
        assertEquals(1.3f, regra.activity.resources.configuration.fontScale, 0.001f)
        // ---- Menu e nova carreira ----
        esperar { !jogo.ocupado }
        auditar("menu")
        verificarBotaoRelatar()
        tocar("Nova carreira")
        esperar { jogo.paises.isNotEmpty() && !jogo.ocupado }
        tocar("Jogador")
        auditar("nova-carreira-jogador")
        tocar("Treinador")
        regra.onNode(hasSetTextAction()).performTextInput("Zé Mister")
        val clube = jogo.paises[0].ligas[0].clubes[4]
        tocar(clube.nome)
        auditar("nova-carreira-treinador")
        tocar("Começar carreira")
        esperar { jogo.ecra == Ecra.CARREIRA && !jogo.ocupado }

        // ---- Separadores principais ----
        auditar("inicio")
        tocar("Plantel"); auditar("plantel")
        tocar("Mercado"); auditar("mercado")
        tocar("Liga"); auditar("liga-tabela")
        tocar("Jogos"); auditar("liga-jogos")
        tocar("Jogadores"); auditar("liga-jogadores")
        tocar("Histórico"); auditar("liga-historico")
        tocar("Mais"); auditar("mais-clube")
        tocar("Finanças"); auditar("mais-financas")
        tocar("Jogos"); auditar("mais-calendario")
        tocar("Seleções"); auditar("mais-selecoes")
        tocar("Convocados"); auditar("mais-convocados")
        tocar("Opções"); auditar("mais-opcoes")
        verificarBotaoRelatar()

        // ---- Ficha de jogador ----
        jogo.abrirJogador(jogo.ui!!.plantel.jogadores.first().id)
        auditar("ficha-jogador")
        jogo.fecharJogador()
        regra.waitForIdle()

        // ---- Jogo em direto: relógio parado para apanhar o direto a meio ----
        tocar("Início")
        regra.mainClock.autoAdvance = false
        tocar("Jogar")
        esperar { jogo.jogo != null && !jogo.ocupado }
        regra.mainClock.advanceTimeBy(350L * 40)
        auditar("jogo-em-direto")
        tocar("Saltar para o fim")
        regra.mainClock.advanceTimeByFrame()
        regra.mainClock.autoAdvance = true
        auditar("jogo-relato")
        tocar("Estatísticas"); auditar("jogo-estatisticas")
        tocar("Notas"); auditar("jogo-notas")
        tocar("Fechar")
        assertTrue(jogo.jogo == null)

        // ---- Gravar, menu com gravação, carregar, editor ----
        tocar("Mais")
        tocar("Opções")
        tocar("Gravar e sair para o menu")
        esperar { jogo.ecra == Ecra.MENU && !jogo.ocupado }
        auditar("menu-com-gravacao")
        tocar("Carregar carreira"); auditar("carregar")
        jogo.abrirMenu()
        regra.waitForIdle()
        tocar("Editor")
        esperar { jogo.editor.pronto }
        auditar("editor-clubes")
        tocar("Jogadores"); auditar("editor-jogadores")
        jogo.editor.abrirJogador(jogo.editor.jogadores.first().id)
        auditar("editor-jogador")
        jogo.editor.fecharJogador()
        jogo.editor.abrirClube(jogo.editor.clubes.first().id)
        auditar("editor-clube")
        jogo.editor.fecharClube()
        tocar("Ficheiros"); auditar("editor-ficheiros")
        jogo.fecharEditor()
        regra.waitForIdle()

        // ---- Carreira de jogador ----
        val clubeJ = jogo.paises[0].ligas[0].clubes.last()
        jogo.comecarCarreira(ModoCarreira.JOGADOR, "Rui Craque", clubeJ.id, "MC", "POR", "direito")
        esperar { jogo.ecra == Ecra.CARREIRA && !jogo.ocupado }
        auditar("jogador-inicio")
        regra.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("O TEU JOGADOR"))
        auditar("jogador-inicio-baixo")
        tocar("Carreira"); auditar("jogador-perfil")
        tocar("Percurso"); auditar("jogador-percurso")
        tocar("Propostas"); auditar("jogador-propostas")
        tocar("Prémios"); auditar("jogador-premios")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-normal-long-notround-any-xhdpi-keyshidden-nonav")
class AuditoriaTelemovelPequenoTest : AuditoriaBase("pequeno", "Telemóvel pequeno (360x640 dp, letra 130 %)")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-normal-long-notround-any-xhdpi-keyshidden-nonav")
class AuditoriaTelemovelGrandeTest : AuditoriaBase("grande", "Telemóvel grande (411x914 dp, letra 130 %)")

/** O jogo 3D (só o HUD, com o motor falso) num telemóvel pequeno na horizontal, com letra a 130 %. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-normal-long-notround-any-xhdpi-keyshidden-nonav")
class AuditoriaAoVivoTest {
    init {
        RuntimeEnvironment.setFontScale(1.3f)
    }

    @get:Rule
    val regra = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun hudEMenuDePausa() {
        val registo = Registo("zz-aovivo", "Jogo 3D (640x360 dp na horizontal, letra 130 %)")
        try {
            val casa = Equipa(id = "ben", nome = "Benfica", abreviatura = "SLB", cores = listOf("#e30613", "#ffffff"))
            val fora = Equipa(id = "por", nome = "Porto", abreviatura = "FCP", cores = listOf("#0033a0", "#ffffff"))
            val p = PartidaFalsa(casa, fora, true, Random(7), 600f)
            val sessao = SessaoAoVivo(p, true)
            var n = 0
            while (p.estado.mensagem == null && n++ < 40_000) sessao.avancar(0.05f)
            regra.setContent {
                Tema {
                    EcraAoVivo(sessao, {}, {}, fixarOrientacao = false, mostrarMedidor = true) { m, _ ->
                        Box(m.background(Color(0xFF2E7D32)), contentAlignment = Alignment.Center) { Text("(vista 3D)", color = Color.White) }
                    }
                }
            }
            regra.waitForIdle()
            registo.analisar(regra, "aovivo-hud")
            regra.onNode(isRoot()).captureRoboImage("build/outputs/roborazzi/auditoria-aovivo-hud.png")
            regra.onNodeWithText("II").performClick()
            regra.waitForIdle()
            registo.analisar(regra, "aovivo-pausa")
            regra.onNode(isRoot()).captureRoboImage("build/outputs/roborazzi/auditoria-aovivo-pausa.png")
            regra.onNodeWithText("Simular o resto").performClick()
            regra.waitUntil(30_000) { p.terminou }
            regra.waitUntil(10_000) { regra.onAllNodes(hasText("Fim do jogo")).fetchSemanticsNodes().isNotEmpty() }
            registo.analisar(regra, "aovivo-fim")
            regra.onNode(isRoot()).captureRoboImage("build/outputs/roborazzi/auditoria-aovivo-fim.png")
        } finally {
            registo.guardar()
        }
    }
}
