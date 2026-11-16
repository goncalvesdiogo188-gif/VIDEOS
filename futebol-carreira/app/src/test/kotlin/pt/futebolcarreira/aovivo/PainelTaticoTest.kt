package pt.futebolcarreira.aovivo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.futebolcarreira.ecras.Tema
import pt.futebolcarreira.nucleo.aovivo.FaseJogo
import pt.futebolcarreira.nucleo.aovivo.NivelMira
import pt.futebolcarreira.nucleo.aovivo.Palestra
import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.Mentalidade

/** O painel de táticas, a palestra ao intervalo e as opções guardadas, com uma partida de teste. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w914dp-h411dp-normal-long-notround-any-xhdpi-keyshidden-nonav")
class PainelTaticoTest {
    @get:Rule
    val regra = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun mostrar(sessao: SessaoAoVivo) {
        regra.setContent {
            Tema {
                EcraAoVivo(sessao, {}, {}, fixarOrientacao = false, disposicao = DisposicaoBotoes()) { m, _ ->
                    Box(m.background(Color(0xFF2E7D32)), contentAlignment = Alignment.Center) { Text("(vista 3D)", color = Color.White) }
                }
            }
        }
        regra.waitForIdle()
    }

    @Test
    fun pausaAbrePainelEFazSubstituicaoFormacaoEMentalidade() {
        val p = PartidaTaticaFalsa()
        val sessao = SessaoAoVivo(p, true)
        mostrar(sessao)
        regra.onNodeWithText("II").performClick()
        regra.waitForIdle()
        regra.onNodeWithText("Jogadores e tática").performClick()
        regra.waitForIdle()
        regra.onNodeWithText("Substituições: 0/5").assertExists()
        // Sai o cansado, entra o suplente.
        regra.onNodeWithText("Cansado").performClick()
        regra.onNodeWithText("Suplente").performClick()
        regra.waitForIdle()
        assertEquals(1, p.feitas)
        regra.onNodeWithText("Substituições: 1/5").assertExists()
        assertTrue(p.onze.any { it.nome == "Suplente" })
        // O guarda-redes só por guarda-redes.
        regra.onNodeWithText("Guarda").performClick()
        regra.onNodeWithText("Guarda B").performClick()
        assertEquals(2, p.feitas)
        // Formação e mentalidade.
        regra.onNodeWithText("Formação").performClick()
        regra.onNodeWithText("4-4-2").performClick()
        assertEquals(Formacao.F442, p.formacao)
        regra.onNodeWithText("Mentalidade").performClick()
        regra.onNodeWithText("Ofensiva").performClick()
        assertEquals(Mentalidade.OFENSIVA, p.mentalidade)
        regra.onNodeWithText("Voltar ao jogo").performClick()
        regra.waitForIdle()
        regra.onNodeWithText("Jogo em pausa").assertExists()
    }

    @Test
    fun palestraAoIntervaloEscolheEARetoma() {
        val p = PartidaTaticaFalsa()
        val sessao = SessaoAoVivo(p, true)
        p.fase = FaseJogo.INTERVALO
        sessao.avancar(0.05f)
        mostrar(sessao)
        regra.waitUntil(5_000) { regra.onAllNodesWithTextCount("O que dizes ao balneário?", substring = true) > 0 }
        regra.onNodeWithText("Motivar").performClick()
        regra.waitForIdle()
        assertEquals(listOf(Palestra.MOTIVAR), p.palestras)
        assertFalse(sessao.aguardaPalestra)
    }

    @Test
    fun opcoesGuardamDuracaoMiraEBotoes() {
        val prefs = OpcoesAoVivo.prefs(ApplicationProvider.getApplicationContext())
        prefs.edit().clear().commit()
        assertEquals(8, OpcoesAoVivo.duracaoMinutos(prefs))
        OpcoesAoVivo.definirDuracao(prefs, 12)
        assertEquals(12, OpcoesAoVivo.duracaoMinutos(prefs))
        OpcoesAoVivo.definirDuracao(prefs, 5)   // valor fora das opções: vai para a mais perto
        assertEquals(4, OpcoesAoVivo.duracaoMinutos(prefs))
        assertNull(OpcoesAoVivo.miraEscolhida(prefs))
        assertEquals(NivelMira.FORTE, OpcoesAoVivo.nivelMira(prefs, "FACIL"))
        OpcoesAoVivo.definirMira(prefs, NivelMira.FRACA)
        assertEquals(NivelMira.FRACA, OpcoesAoVivo.nivelMira(prefs, "FACIL"))
        val d = DisposicaoBotoes().com(ConfigBotao(BotaoId.SPRINT, 0.5f, 0.5f, 1.2f, false))
        OpcoesAoVivo.guardarDisposicao(prefs, d)
        assertEquals(d, OpcoesAoVivo.disposicao(prefs))
        OpcoesAoVivo.repor(prefs)
        assertEquals(DisposicaoBotoes(), OpcoesAoVivo.disposicao(prefs))
    }

    @Test
    fun blocoDeOpcoesMostraDuracoesEMira() {
        val prefs = OpcoesAoVivo.prefs(ApplicationProvider.getApplicationContext())
        prefs.edit().clear().commit()
        regra.setContent { Tema { BlocoOpcoesAoVivo() } }
        regra.waitForIdle()
        regra.onNodeWithText("12 min").performClick()
        assertEquals(12, OpcoesAoVivo.duracaoMinutos(prefs))
        regra.onNodeWithText("Forte").performClick()
        assertEquals(NivelMira.FORTE, OpcoesAoVivo.miraEscolhida(prefs))
        regra.onNodeWithText("Personalizar botões").performClick()
        regra.waitForIdle()
        regra.onNodeWithText("Botões do jogo 3D").assertExists()
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(t: String, substring: Boolean = false): Int =
    onAllNodes(androidx.compose.ui.test.hasText(t, substring = substring)).fetchSemanticsNodes().size
