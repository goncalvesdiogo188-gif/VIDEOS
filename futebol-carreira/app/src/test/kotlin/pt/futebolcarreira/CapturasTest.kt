package pt.futebolcarreira

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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

/**
 * Percorre a aplicação como um utilizador (menu, nova carreira, jogar, separadores, gravar)
 * num telemóvel simulado, e guarda uma imagem de cada ecrã em build/outputs/roborazzi.
 * Serve para confirmar que os ecrãs abrem e para os ver sem ter um telemóvel à mão.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-normal-long-notround-any-xhdpi-keyshidden-nonav")
class CapturasTest {
    @get:Rule
    val regra = createAndroidComposeRule<MainActivity>()

    private val jogo get() = ViewModelProvider(regra.activity)[JogoViewModel::class.java]

    /** Espera que o trabalho em segundo plano acabe. */
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
        regra.onNodeWithText(texto).performClick()
        regra.waitForIdle()
    }

    /** Para botões dentro de uma lista que desliza: primeiro traz-se o botão para o ecrã. */
    private fun tocarComScroll(texto: String) {
        regra.onNodeWithText(texto).performScrollTo().performClick()
        regra.waitForIdle()
    }

    @Test
    fun percorrerAAplicacao() {
        // ---- Menu e nova carreira ----
        esperar { !jogo.ocupado }
        assertEquals(Ecra.MENU, jogo.ecra)
        capturar("01-menu")

        tocar("Nova carreira")
        esperar { jogo.paises.isNotEmpty() && !jogo.ocupado }
        tocar("Jogador")
        capturar("02-nova-carreira-jogador")
        tocar("Treinador")
        regra.onNode(hasSetTextAction()).performTextInput("Zé Mister")
        val clube = jogo.paises[0].ligas[0].clubes[4]
        tocarComScroll(clube.nome)
        capturar("03-nova-carreira")

        tocar("Começar carreira")
        esperar { jogo.ecra == Ecra.CARREIRA && !jogo.ocupado }
        assertEquals(clube.nome, jogo.ui?.clube)
        capturar("04-inicio")

        // ---- Primeiro jogo: relógio parado para apanhar o direto a meio ----
        regra.mainClock.autoAdvance = false
        regra.onNodeWithText("Jogar").performClick()
        esperar { jogo.jogo != null && !jogo.ocupado }
        regra.mainClock.advanceTimeBy(350L * 40)
        capturar("05-jogo-em-direto")
        regra.onNodeWithText("Saltar para o fim").performClick()
        regra.mainClock.advanceTimeByFrame()
        regra.mainClock.autoAdvance = true
        capturar("06-jogo-relato")
        tocar("Estatísticas")
        capturar("07-jogo-estatisticas")
        tocar("Notas")
        capturar("08-jogo-notas")
        tocar("Fechar")
        assertTrue(jogo.jogo == null)

        // ---- Mais alguns jogos, para os ecrãs terem dados ----
        repeat(7) {
            jogo.avancar()
            esperar { !jogo.ocupado }
            jogo.fecharJogo()
        }
        assertEquals(8, jogo.ui?.calendario?.count { it.desfecho.isNotEmpty() })
        capturar("09-inicio-com-jogos")

        tocar("Plantel")
        capturar("10-plantel")
        tocarComScroll("5-3-2")
        tocarComScroll("Defensiva")
        assertEquals("5-3-2", jogo.ui?.plantel?.formacaoEmUso)
        capturar("11-plantel-tatica")
        // Trocar o guarda-redes titular (a janela de escolha é aberta e respondida sem captura).
        jogo.abrirEscolha(0)
        val suplente = jogo.escolha!!.candidatos.first { it.posicao == "GR" && it.aviso.isEmpty() }
        jogo.escolherTitular(suplente.jogadorId)
        regra.waitForIdle()
        assertEquals(suplente.nome, jogo.ui?.plantel?.onze?.first()?.nome)
        assertTrue(jogo.ui?.plantel?.onzeManual == true)
        capturar("11b-plantel-onze-manual")
        tocar("Liga")
        capturar("12-liga-tabela")
        tocar("Jogos")
        capturar("13-liga-jogos")
        tocar("Jogadores")
        capturar("14-liga-jogadores")
        tocar("Calendário")
        capturar("15-calendario")
        tocar("Mais")
        capturar("16-mais")

        // ---- Fim de época e época seguinte ----
        jogo.simularEpoca()
        esperar { !jogo.ocupado }
        tocar("Início")
        capturar("17-fim-de-epoca")
        // Se a direção despediu o treinador (ou se há propostas), aceita-se a primeira para poder continuar.
        if (jogo.ui?.despedido == true) {
            jogo.aceitarProposta(jogo.ui!!.propostas.first().equipaId)
            esperar { !jogo.ocupado }
            jogo.simularEpoca()
            esperar { !jogo.ocupado }
        }
        assertTrue(jogo.ui?.terminada == true)
        val clubeFinal = jogo.ui!!.clube
        jogo.novaEpoca()
        esperar { !jogo.ocupado }
        assertEquals("2027/28", jogo.ui?.epoca)
        tocar("Liga")
        tocar("Histórico")
        capturar("18-liga-historico")

        // ---- Gravar, sair e voltar a carregar ----
        tocar("Mais")
        tocar("Gravar e sair para o menu")
        esperar { jogo.ecra == Ecra.MENU && !jogo.ocupado }
        assertEquals(1, jogo.gravacoes.size)
        capturar("19-menu-com-gravacao")
        tocar("Carregar carreira")
        capturar("20-carregar")
        tocar(clubeFinal)
        esperar { jogo.ecra == Ecra.CARREIRA && !jogo.ocupado }
        assertEquals("2027/28", jogo.ui?.epoca)
        assertEquals(clubeFinal, jogo.ui?.clube)
        capturar("21-carreira-carregada")
    }
}
