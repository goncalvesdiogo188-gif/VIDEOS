package pt.futebolcarreira

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import pt.futebolcarreira.nucleo.carreira.Funcao
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.carreira.PerfilPatrocinio
import pt.futebolcarreira.nucleo.carreira.SlotPatrocinio

/** Fase 13: capturas e teste dos ecrãs de gestão (Mais › Gestão): equipa técnica, contratos, orçamento e treino. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-normal-long-notround-any-xhdpi-keyshidden-nonav")
class CapturasGestaoTest {
    @get:Rule
    val regra = createAndroidComposeRule<MainActivity>()

    private val jogo get() = ViewModelProvider(regra.activity)[JogoViewModel::class.java]

    private fun esperar(condicao: () -> Boolean) {
        regra.waitUntil(180_000) {
            ShadowLooper.idleMainLooper()
            condicao()
        }
    }

    private fun capturar(nome: String) {
        regra.waitForIdle()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/$nome.png")
    }

    private fun tocar(texto: String) {
        val no = regra.onNodeWithText(texto)
        // Nos separadores que deslizam traz-se o botão para o ecrã; na barra de baixo não há nada para deslizar.
        try { no.performScrollTo() } catch (e: AssertionError) { }
        no.performClick()
        regra.waitForIdle()
    }

    /** Corre uma ação de gestão e espera que acabe. */
    private fun gerir(acao: (pt.futebolcarreira.nucleo.carreira.Carreira) -> String?) {
        jogo.gerir(acao = acao)
        esperar { !jogo.ocupado }
        jogo.fecharAviso()
        regra.waitForIdle()
    }

    private fun deslizar() {
        regra.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performTouchInput { swipeUp(startY = bottom * 0.9f, endY = top, durationMillis = 400) }
    }

    @Test
    fun ecrasDeGestao() {
        esperar { !jogo.ocupado }
        jogo.abrirNovaCarreira()
        esperar { jogo.paises.isNotEmpty() && !jogo.ocupado }
        val clube = jogo.paises[0].ligas[0].clubes[2]
        jogo.comecarCarreira(ModoCarreira.TREINADOR, "Zé Mister", clube.id, "")
        esperar { jogo.ecra == Ecra.CARREIRA && !jogo.ocupado }
        val c = jogo.carreiraAberta!!
        // Alguns contratos a acabar, para haver o que negociar.
        c.equipa.plantel.sortedByDescending { it.geral() }.take(4).forEach { it.contratoAte = c.gestor.ano + 1 }
        tocar("Mais")
        tocar("Gestão")

        // ---- Equipa técnica ----
        capturar("70-gestao-equipa-tecnica")
        gerir { cc ->
            val et = cc.gestaoClube.equipaTecnica
            et.contratar(et.candidatos(Funcao.ADJUNTO).maxBy { it.nivel })
            et.contratar(et.candidatos(Funcao.MEDICO).maxBy { it.nivel })
            et.contratar(et.candidatos(Funcao.OLHEIRO).maxBy { it.nivel })
            null
        }
        assertEquals(3, c.gestaoClube.equipaTecnica.membros.size)
        assertTrue(c.equipa.ajustes.crescimento > 1.0)
        capturar("70b-gestao-equipa-contratada")

        // ---- Contratos ----
        tocar("Contratos")
        capturar("71-gestao-contratos")
        regra.onAllNodesWithText("Renovar")[0].performClick()
        regra.waitForIdle()
        capturar("72-gestao-negociacao")
        tocar("Propor")
        esperar { !jogo.ocupado }
        regra.waitForIdle()
        assertTrue("o jogador aceitou a renovação pedida", c.mensagens.any { it.titulo.startsWith("Renovação:") })
        jogo.fecharAviso()
        capturar("73-gestao-contratos-depois")

        // ---- Orçamento ----
        tocar("Orçamento")
        capturar("74-gestao-orcamento")
        gerir { cc ->
            val o = cc.gestaoClube.orcamento
            o.definirOrcamentoSalarios(o.sugestaoSalarios().coerceAtMost(o.maximoSalarios()))
            o.definirOrcamentoTransferencias(o.sugestaoTransferencias())
            o.definirPrecoBilhete(0.9)
            o.assinar(SlotPatrocinio.CAMISOLA, PerfilPatrocinio.EQUILIBRADO)
            null
        }
        assertEquals(0.9, c.gestaoClube.orcamento.precoBilhete(), 0.001)
        capturar("74b-gestao-orcamento-definido")
        deslizar()
        capturar("75-gestao-patrocinadores")

        // ---- Treino ----
        tocar("Treino")
        capturar("76-gestao-treino")
        tocar("Intenso")
        esperar { !jogo.ocupado }
        assertTrue(c.equipa.ajustes.riscoLesao > 1.0)
        capturar("77-gestao-plano-intenso")
        deslizar()
        capturar("78-gestao-carga-individual")

        // O jogo continua a funcionar com tudo isto ligado (grava e volta a abrir).
        jogo.jogar()
        esperar { !jogo.ocupado }
        assertTrue(c.gestaoClube.treino.plano().dias.size == 7)
    }
}
