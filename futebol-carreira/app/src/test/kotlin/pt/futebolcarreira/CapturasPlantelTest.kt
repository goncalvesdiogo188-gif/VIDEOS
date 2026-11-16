package pt.futebolcarreira

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
import pt.futebolcarreira.ecras.Camisola
import pt.futebolcarreira.ecras.Emblema
import pt.futebolcarreira.ecras.Tema

/** Capturas do plantel (campo e cartas), da ficha com radar e evolução, e dos emblemas e camisolas. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-normal-long-notround-any-xhdpi-keyshidden-nonav")
class CapturasPlantelTest {
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

    @Test
    fun plantelCampoCartasEFicha() {
        esperar { !jogo.ocupado }
        jogo.abrirNovaCarreira()
        esperar { jogo.paises.isNotEmpty() && !jogo.ocupado }
        val clube = jogo.paises[0].ligas[0].clubes[4]
        jogo.comecarCarreira(pt.futebolcarreira.nucleo.carreira.ModoCarreira.TREINADOR, "Zé Mister", clube.id, "")
        esperar { jogo.ecra == Ecra.CARREIRA && !jogo.ocupado }
        regra.onNodeWithText("Plantel").performClick()
        regra.waitForIdle()
        capturar("60-plantel-campo")
        regra.onNode(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performTouchInput { swipeUp(startY = bottom * 0.9f, endY = top, durationMillis = 400) }
        capturar("60b-plantel-cartas")
        regra.onNode(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performTouchInput { swipeDown(startY = top, endY = bottom * 0.9f, durationMillis = 400) }
        // A troca de dois jogadores do onze (o que o arrastar faz) mexe no onze manual.
        val antes = jogo.ui!!.plantel.onze
        jogo.trocarLugares(2, 3)
        regra.waitForIdle()
        assertEquals(antes[2].nome, jogo.ui!!.plantel.onze[3].nome)
        assertEquals(antes[3].nome, jogo.ui!!.plantel.onze[2].nome)
        assertTrue(jogo.ui!!.plantel.onzeManual)
        jogo.mudarFormacao(pt.futebolcarreira.nucleo.modelos.Formacao.F352)
        regra.waitForIdle()
        capturar("61-plantel-352")
        // Ficha com radar e evolução (uma época simulada para haver histórico).
        jogo.simularEpoca()
        esperar { !jogo.ocupado }
        if (jogo.ui?.despedido == true) {
            jogo.aceitarProposta(jogo.ui!!.propostas.first().equipaId)
            esperar { !jogo.ocupado }
            jogo.simularEpoca()
            esperar { !jogo.ocupado }
        }
        jogo.novaEpoca()
        esperar { !jogo.ocupado }
        jogo.abrirJogador(jogo.ui!!.plantel.jogadores.first().id)
        regra.waitForIdle()
        assertTrue(jogo.ficha!!.evolucao.size >= 2)
        capturar("62-ficha-radar")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-normal-long-notround-any-xhdpi-keyshidden-nonav")
class CapturasEmblemasTest {
    @get:Rule
    val regra = createComposeRule()

    @Test
    fun galeriaDeEmblemasECamisolas() {
        val clubes = listOf(
            EmblemaUi("SLB", 0xFFC62828, 0xFFFFFFFF), EmblemaUi("FCP", 0xFF1565C0, 0xFFFFFFFF), EmblemaUi("SCP", 0xFF2E7D32, 0xFFFFFFFF),
            EmblemaUi("SCB", 0xFFB71C1C, 0xFFFFD54F), EmblemaUi("VSC", 0xFFFAFAFA, 0xFF212121), EmblemaUi("GDG", 0xFF6A1B9A, 0xFFFFEB3B),
            EmblemaUi("RIO", 0xFFEF6C00, 0xFF1B1B1B), EmblemaUi("MAR", 0xFF00695C, 0xFFFFFFFF), EmblemaUi("FAR", 0xFF263238, 0xFFFFC107),
            EmblemaUi("NAC", 0xFF0D47A1, 0xFFFFCDD2), EmblemaUi("BOA", 0xFF000000, 0xFFFFFFFF), EmblemaUi("EST", 0xFF880E4F, 0xFFE0E0E0),
        )
        regra.setContent {
            Tema {
                Surface {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (linha in clubes.chunked(4)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { for (c in linha) Emblema(c, 72.dp) }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { for (c in linha) Camisola(c, 72.dp, 10) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { for (c in clubes.take(5)) Emblema(c, 28.dp) }
                    }
                }
            }
        }
        regra.waitForIdle()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/63-emblemas-camisolas.png")
    }
}
