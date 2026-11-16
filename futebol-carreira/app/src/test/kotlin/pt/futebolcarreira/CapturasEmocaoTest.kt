package pt.futebolcarreira

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import pt.futebolcarreira.ecras.CaixaDecisoes
import pt.futebolcarreira.ecras.PainelDecisao
import pt.futebolcarreira.ecras.SeparadorBalneario
import pt.futebolcarreira.ecras.SeparadorHistoria
import pt.futebolcarreira.ecras.Tema
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.dados.BaseDados
import java.io.File

/** Capturas dos ecrãs da Fase 13: decisões pendentes (imprensa, palestra...), balneário e sala de história. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-normal-long-notround-any-xhdpi-keyshidden-nonav")
class CapturasEmocaoTest {
    @get:Rule
    val regra = createComposeRule()

    /** Uma carreira de treinador com uma época feita e uns jogos da seguinte, até haver decisões pendentes. */
    private fun carreiraComDecisoes(): Carreira {
        val bd = BaseDados()
        check(bd.carregar { File("src/main/assets/dados", it).takeIf(File::exists)?.readText() }) { "base de dados: ${bd.erros.take(3)}" }
        val c = Carreira.nova(bd, "capturas13", "Zé Mister", ModoCarreira.TREINADOR, "POR_02", semente = 5)
        c.info.confiancaDirecao = 100
        c.simularEpoca()
        if (c.info.despedido) error("despedido na época de teste")
        c.novaEpoca()
        for (j in c.equipa.plantel) j.moral = 36
        var k = 0
        while (c.emocao.pendentes.isEmpty() && k++ < 30) c.avancar()
        return c
    }

    @Test
    fun decisoesBalneirioEHistoria() {
        val c = carreiraComDecisoes()
        val ui = criarEmocaoUi(c)
        assertTrue("devia haver decisões pendentes", ui.decisoes.isNotEmpty())
        assertTrue(ui.decisoes.all { it.opcoes.size == 3 && it.titulo.isNotBlank() })
        val escolhidas = mutableListOf<Pair<Int, Int>>()
        regra.setContent {
            Tema {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(12.dp)) { CaixaDecisoes(ui.decisoes, true) { id, o -> escolhidas += id to o } }
                }
            }
        }
        regra.waitForIdle()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/70-decisoes-pendentes.png")
        // Tocar num cartão abre o diálogo com as três respostas; escolher uma entrega-a a quem chamou.
        regra.onAllNodesWithText("Responder")[0].performClick()
        regra.waitForIdle()
        regra.onNodeWithText(ui.decisoes[0].opcoes[1].rotulo).performClick()
        regra.waitForIdle()
        assertEquals(listOf(ui.decisoes[0].id to 1), escolhidas)
    }

    @Test
    fun painelDaConferenciaDeImprensa() {
        val c = carreiraComDecisoes()
        val d = criarEmocaoUi(c).decisoes.first()
        regra.setContent {
            Tema { Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(16.dp)) { PainelDecisao(d) {} } } }
        }
        regra.waitForIdle()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/71-conferencia-imprensa.png")
    }

    @Test
    fun balneario() {
        val c = carreiraComDecisoes()
        val b = criarEmocaoUi(c).balneario!!
        assertTrue(b.lideres.size >= 4 && b.lideres.count { it.capitao } == 1)
        val nomeados = mutableListOf<Int>()
        regra.setContent {
            Tema { Surface(Modifier.fillMaxSize()) { SeparadorBalneario(b, true) { nomeados += it } } }
        }
        regra.waitForIdle()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/72-balneario.png")
        regra.onAllNodesWithText("Nomear")[0].performClick()
        regra.waitForIdle()
        assertEquals(1, nomeados.size)
    }

    @Test
    fun salaDeHistoria() {
        val c = carreiraComDecisoes()
        val h = criarEmocaoUi(c).historia
        assertTrue(h.epocas.isNotEmpty() && h.marcadores.isNotEmpty() && h.recordes.isNotEmpty() && h.momentos.isNotEmpty())
        regra.setContent {
            Tema { Surface(Modifier.fillMaxSize()) { SeparadorHistoria(h) } }
        }
        regra.waitForIdle()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/73-sala-de-historia.png")
        regra.onNodeWithText("MELHORES MARCADORES DE SEMPRE").assertExists()
    }
}

/** Navegação real: Início → «Decisões pendentes» e Mais → Clube → Balneário / História. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-normal-long-notround-any-xhdpi-keyshidden-nonav")
class NavegacaoEmocaoTest {
    @get:Rule
    val regra = createAndroidComposeRule<MainActivity>()

    private val jogo get() = ViewModelProvider(regra.activity)[JogoViewModel::class.java]

    private fun esperar(condicao: () -> Boolean) {
        regra.waitUntil(180_000) {
            ShadowLooper.idleMainLooper()
            condicao()
        }
    }

    @Test
    fun decisoesNoInicioEBalneirioNoClube() {
        esperar { !jogo.ocupado }
        jogo.abrirNovaCarreira()
        esperar { jogo.paises.isNotEmpty() && !jogo.ocupado }
        val clube = jogo.paises[0].ligas[0].clubes[1]
        jogo.comecarCarreira(ModoCarreira.TREINADOR, "Zé Mister", clube.id, "")
        esperar { jogo.ecra == Ecra.CARREIRA && !jogo.ocupado }
        for (j in jogo.carreiraAberta!!.equipa.plantel) j.moral = 34
        var k = 0
        while (jogo.ui!!.emocao.decisoes.isEmpty() && k++ < 12 && jogo.ui?.despedido != true) {
            jogo.avancar()
            esperar { !jogo.ocupado }
            jogo.fecharJogo()
            regra.waitForIdle()
        }
        val decisoes = jogo.ui!!.emocao.decisoes
        assertTrue("devia haver decisões pendentes", decisoes.isNotEmpty())
        regra.onNodeWithText("DECISÕES PENDENTES · ${decisoes.size}").assertExists()
        regra.onRoot().captureRoboImage("build/outputs/roborazzi/74-inicio-decisoes.png")
        // Responder pelo diálogo mexe na carreira e tira a decisão da lista.
        val antes = decisoes.size
        regra.onAllNodesWithText("Responder")[0].performClick()
        regra.waitForIdle()
        regra.onNodeWithText(decisoes[0].opcoes[0].rotulo).performClick()
        esperar { !jogo.ocupado }
        assertEquals(antes - 1, jogo.ui!!.emocao.decisoes.size)
        assertTrue(jogo.aviso != null && jogo.aviso!!.contains("Efeitos"))
        jogo.fecharAviso()
        regra.waitForIdle()
        // Mais → Clube → Balneário e História.
        regra.onNodeWithText("Mais").performClick()
        regra.waitForIdle()
        regra.onNodeWithText("Balneário").performClick()
        regra.waitForIdle()
        regra.onNodeWithText("CAPITÃO E LÍDERES").assertExists()
        regra.onNodeWithText("História").performClick()
        regra.waitForIdle()
        regra.onNodeWithText("LENDAS DO CLUBE").assertExists()
    }
}
