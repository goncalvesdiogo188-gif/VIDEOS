package pt.futebolcarreira.aovivo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.futebolcarreira.ecras.Tema
import pt.futebolcarreira.nucleo.aovivo.PartidaAoVivo
import pt.futebolcarreira.nucleo.aovivo.PartidaFalsa
import pt.futebolcarreira.nucleo.modelos.Equipa
import kotlin.random.Random

/** O HUD e os controlos do jogo 3D, com o motor falso e um marcador de posição no lugar do OpenGL. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w914dp-h411dp-normal-long-notround-any-xhdpi-keyshidden-nonav")
class EcraAoVivoTest {
    @get:Rule
    val regra = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun partida(duracao: Float = 600f): PartidaFalsa {
        val casa = Equipa(id = "ben", nome = "Benfica", abreviatura = "SLB", cores = listOf("#e30613", "#ffffff"))
        val fora = Equipa(id = "por", nome = "Porto", abreviatura = "FCP", cores = listOf("#0033a0", "#ffffff"))
        return PartidaFalsa(casa, fora, true, Random(7), duracao)
    }

    private var entregue: PartidaAoVivo? = null
    private var saiu = false

    private fun mostrar(sessao: SessaoAoVivo) {
        regra.setContent {
            Tema {
                EcraAoVivo(sessao, { entregue = it }, { saiu = true }, fixarOrientacao = false) { m, _ ->
                    Box(m.background(Color(0xFF2E7D32)), contentAlignment = Alignment.Center) { Text("(vista 3D)", color = Color.White) }
                }
            }
        }
        regra.waitForIdle()
    }

    @Test
    fun hudComMarcadorMensagemEControlos() {
        val p = partida()
        val sessao = SessaoAoVivo(p, true)
        // Joga até haver um golo, para a mensagem aparecer.
        var n = 0
        while (p.estado.mensagem == null && n++ < 40_000) sessao.avancar(0.05f)
        assertNotNull("o motor falso devia marcar um golo", p.estado.mensagem)
        mostrar(sessao)
        regra.onNodeWithText("SLB").assertExists()
        regra.onNodeWithText("FCP").assertExists()
        regra.onNodeWithText("REMATE").assertExists()
        regra.onNodeWithText("PASSE").assertExists()
        regra.onNodeWithText("SPRINT").assertExists()
        regra.onNodeWithText("TROCAR").assertExists()
        regra.onNodeWithText(p.estado.mensagem!!).assertExists()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/60-jogo-3d-hud.png")
    }

    @Test
    fun botoesEnviamComandos() {
        val sessao = SessaoAoVivo(partida(), true)
        mostrar(sessao)
        regra.onNodeWithText("TROCAR").performClick()
        regra.waitForIdle()
        assertTrue(sessao.controlo.consumir(1).trocar)
    }

    @Test
    fun pausaContinuarESimularORestoAteAoCartaoFinal() {
        val p = partida(30f)
        val sessao = SessaoAoVivo(p, true)
        mostrar(sessao)
        regra.onNodeWithText("II").performClick()
        regra.waitForIdle()
        regra.onNodeWithText("Jogo em pausa").assertExists()
        assertTrue(sessao.pausado)
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/60b-jogo-3d-pausa.png")
        regra.onNodeWithText("Continuar").performClick()
        regra.waitForIdle()
        assertFalse(sessao.pausado)
        regra.onNodeWithText("II").performClick()
        regra.onNodeWithText("Simular o resto").performClick()
        regra.waitUntil(30_000) { p.terminou }
        regra.waitUntil(10_000) { regra.onAllNodesWithTextCount("Fim do jogo") > 0 }
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/60c-jogo-3d-fim.png")
        regra.onNodeWithText("Continuar").performClick()
        assertTrue(entregue === p)
    }

    @Test
    fun abandonarSai() {
        val sessao = SessaoAoVivo(partida(), true)
        mostrar(sessao)
        regra.onNodeWithText("II").performClick()
        regra.onNodeWithText("Abandonar o jogo").performClick()
        assertTrue(saiu)
        assertEquals(null, entregue)
    }

    @Test
    fun aVerNaoMostraControlos() {
        val sessao = SessaoAoVivo(partida(), true, controlar = false)
        mostrar(sessao)
        assertEquals(0, regra.onAllNodesWithTextCount("REMATE"))
        regra.onNodeWithText("SLB").assertExists()
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(t: String): Int =
    onAllNodes(androidx.compose.ui.test.hasText(t)).fetchSemanticsNodes().size
