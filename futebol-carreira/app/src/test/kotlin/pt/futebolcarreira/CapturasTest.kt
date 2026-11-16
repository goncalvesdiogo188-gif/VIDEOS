package pt.futebolcarreira

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
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
        regra.onNodeWithText("Jogar ao vivo (3D)").assertExists()

        // ---- Mercado (está aberto em julho): procurar, abrir a ficha de um jogador e fazer uma proposta ----
        tocar("Mercado")
        capturar("04b-mercado")
        jogo.filtrarMercado("PL", FiltroMercado.POSSO_PAGAR)
        regra.waitForIdle()
        val alvo = jogo.ui!!.mercado.jogadores.last()
        jogo.abrirJogador(alvo.id)
        capturar("04c-ficha-jogador")
        val caixa = jogo.ui!!.financas.caixa
        jogo.negociar(AcaoMercado.PAGAR_PEDIDO)
        esperar { !jogo.ocupado }
        assertTrue(jogo.aviso != null)
        if (jogo.aviso == "Negócio fechado!") {
            assertTrue(jogo.ui!!.plantel.jogadores.any { it.id == alvo.id })
            assertTrue(jogo.ui!!.financas.caixa != caixa)
        }
        jogo.fecharAviso()
        jogo.fecharJogador()
        // Vender um paraVender.
        val paraVender = jogo.ui!!.plantel.jogadores.filter { it.posicao == "MC" }.minBy { it.geral }
        jogo.abrirJogador(paraVender.id)
        jogo.negociar(AcaoMercado.VENDER)
        esperar { !jogo.ocupado }
        jogo.fecharAviso()
        jogo.fecharJogador()
        tocar("Início")

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
        tocar("Mais")
        // ---- Clube: treino, instalações, academia e olheiros ----
        capturar("15a-clube")
        tocarComScroll("Forte")
        assertEquals(pt.futebolcarreira.nucleo.modelos.Intensidade.FORTE, jogo.ui?.gestao?.intensidade)
        val jovem = jogo.ui!!.gestao!!.academia.first()
        jogo.abrirJogador(jovem.id)
        capturar("15b-ficha-academia")
        jogo.negociar(AcaoMercado.PROMOVER)
        esperar { !jogo.ocupado }
        assertEquals("Negócio fechado!", jogo.aviso)
        jogo.fecharAviso()
        jogo.fecharJogador()
        assertTrue(jogo.ui!!.plantel.jogadores.any { it.id == jovem.id })
        jogo.enviarOlheiro("ESP", "")
        esperar { !jogo.ocupado }
        jogo.fecharAviso()
        assertTrue(jogo.ui!!.gestao!!.olheiroEmViagem.isNotEmpty())
        capturar("15c-clube-olheiro")
        tocar("Finanças")
        capturar("15d-financas")
        tocar("Jogos")
        capturar("15e-calendario")
        tocar("Opções")
        capturar("16-mais")
        // Jogo 3D (o ecrã com OpenGL não corre aqui): opção guardada e ligação provisória ao fim do jogo.
        tocar("Ver em 3D")
        assertEquals(pt.futebolcarreira.aovivo.ModoJogos.VER_3D, jogo.modoJogos)
        tocar("Simular")
        assertEquals(pt.futebolcarreira.aovivo.ModoJogos.SIMULAR, jogo.modoJogos)
        jogo.registarJogoAoVivo(pt.futebolcarreira.nucleo.aovivo.PartidaFalsa(pt.futebolcarreira.nucleo.modelos.Equipa(id = "a", nome = "A"), pt.futebolcarreira.nucleo.modelos.Equipa(id = "b", nome = "B"), true))
        assertEquals("Resultado ao vivo será ligado na junção", jogo.aviso)
        jogo.fecharAviso()

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
        tocar("Opções")
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

        // ---- Taças e Europa com dados reais: joga-se a época toda para os quadros terem resultados ----
        jogo.simularEpoca()
        esperar { !jogo.ocupado }
        if (jogo.ui?.despedido == true) {
            jogo.aceitarProposta(jogo.ui!!.propostas.first().equipaId)
            esperar { !jogo.ocupado }
            jogo.simularEpoca()
            esperar { !jogo.ocupado }
        }
        val taca = jogo.fase7.competicoes.first { it.tipo == pt.futebolcarreira.TipoCompeticao.TACA && jogo.ui!!.liga.ligaId.startsWith(it.id.substringBefore("_")) }
        esperar { !jogo.ocupado }
        tocar("Liga")
        jogo.escolherCompeticao(taca.id)
        regra.waitForIdle()
        assertEquals(taca.id, jogo.fase7.competicaoId)
        capturar("22-taca-real")
        jogo.escolherCompeticao("UCL")
        regra.waitForIdle()
        assertEquals("UCL", jogo.fase7.competicaoId)
        capturar("23-europa-real")
        tocar("Eliminatórias")
        capturar("23b-europa-eliminatorias-real")
        tocar("Mais")
        tocar("Jogos")
        capturar("24-calendario-completo")

        // ---- Seleções com dados reais do núcleo ----
        tocar("Mais")
        tocar("Seleções")
        capturar("25-selecoes-real")
        tocar("Convocados")
        capturar("26-convocados-real")
        tocar("Argentina")
        capturar("26b-convocados-argentina")

        // ---- Fase 8: notícias no Início e lista completa ----
        assertTrue(jogo.ui!!.noticias.isNotEmpty())
        tocar("Início")
        regra.onNode(hasScrollAction()).performScrollToNode(hasText("Ver todas"))
        capturar("27-noticias")
        tocar("Ver todas")
        capturar("28-noticias-todas")
        tocar("Fechar")
    }

    /** Fase 9: carreira de jogador (criação, perfil, propostas e prémios) depois de uma época simulada. */
    @Test
    fun carreiraDeJogador() {
        esperar { !jogo.ocupado }
        tocar("Nova carreira")
        esperar { jogo.paises.isNotEmpty() && !jogo.ocupado }
        assertTrue(jogo.nacoes.any { it.id == "BRA" } && jogo.nacoes.any { it.id == "POR" })
        tocar("Jogador")
        regra.onNode(hasSetTextAction()).performTextInput("Rui Craque")
        capturar("40a-jogador-criacao")
        // O clube mais fraco da primeira divisão: há mais clubes interessados em quem joga bem.
        val clube = jogo.paises[0].ligas[0].clubes.last()
        jogo.comecarCarreira(pt.futebolcarreira.nucleo.carreira.ModoCarreira.JOGADOR, "Rui Craque", clube.id, "MC", "NED", "esquerdo")
        esperar { jogo.ecra == Ecra.CARREIRA && !jogo.ocupado }
        assertEquals(pt.futebolcarreira.nucleo.carreira.ModoCarreira.JOGADOR, jogo.ui?.modo)
        val inicial = jogo.ui!!.jogadorCarreira!!
        assertTrue(inicial.linha1.contains("Países Baixos") && inicial.linha1.contains("esquerdo") && inicial.linha1.startsWith("MC"))
        // Para os ecrãs terem dados a sério, o jogador é uma estrela (joga sempre e aparece nos prémios).
        jogo.carreiraAberta!!.jogador!!.let { eu ->
            for (atributo in pt.futebolcarreira.nucleo.modelos.Jogador.ATRIBUTOS) eu.atributos[atributo] = 80
            eu.potencial = 99
            jogo.carreiraAberta!!.equipa.invalidarForca()
        }
        // A primeira época inteira (os pedidos do treinador, os minutos e os prémios da liga ficam em dados).
        jogo.simularEpoca()
        esperar { !jogo.ocupado }
        assertTrue(jogo.ui?.terminada == true)
        jogo.novaEpoca()
        esperar { !jogo.ocupado }
        assertEquals("2027/28", jogo.ui?.epoca)
        if (jogo.ui!!.jogadorCarreira!!.ofertas.isEmpty()) {
            jogo.pedirSaida(false)
            jogo.fecharAviso()
        }
        val j = jogo.ui!!.jogadorCarreira!!
        assertTrue("devia ter a primeira época registada", j.temporadas.isNotEmpty())
        assertTrue(j.bolaDeOuro.isNotEmpty() && j.premiosDaLiga.isNotEmpty())

        tocar("Início")
        capturar("40c-jogador-inicio")
        regra.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("O TEU JOGADOR"))
        capturar("40e-jogador-inicio-baixo")
        tocar("Carreira")
        capturar("40-jogador-perfil")
        regra.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Pedir mais minutos ao treinador"))
        capturar("40b-jogador-perfil-minutos")
        regra.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Equilibrado"))
        capturar("40d-jogador-perfil-treino")
        tocar("Percurso")
        capturar("43-jogador-percurso")
        tocar("Propostas")
        capturar("41-jogador-propostas")
        regra.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Pedir transferência"))
        capturar("41b-jogador-propostas-baixo")
        tocar("Prémios")
        capturar("42-jogador-premios")

        // Pedir minutos e mudar o foco do treino funcionam e ficam guardados.
        tocar("Perfil")
        jogo.escolherFocoJogador(pt.futebolcarreira.nucleo.modelos.FocoTreino.FISICO)
        regra.waitForIdle()
        assertEquals(pt.futebolcarreira.nucleo.modelos.FocoTreino.FISICO, jogo.ui!!.jogadorCarreira!!.foco)
        jogo.pedirMinutos()
        assertTrue(jogo.aviso != null)
        jogo.fecharAviso()
        // Aceitar uma proposta muda o clube.
        val oferta = jogo.ui!!.jogadorCarreira!!.ofertas.firstOrNull()
        if (oferta != null) {
            jogo.aceitarOferta(oferta.id)
            regra.waitForIdle()
            assertTrue(jogo.aviso != null)
            jogo.fecharAviso()
            assertEquals(oferta.clube, jogo.ui!!.clube)
        }
        // Gravar, sair e voltar a carregar: o estado do jogador mantém-se.
        val clubeAtual = jogo.ui!!.clube
        jogo.sairParaMenu()
        esperar { jogo.ecra == Ecra.MENU && !jogo.ocupado }
        jogo.continuar()
        esperar { jogo.ecra == Ecra.CARREIRA && !jogo.ocupado }
        assertEquals(clubeAtual, jogo.ui!!.clube)
        assertEquals(pt.futebolcarreira.nucleo.modelos.FocoTreino.FISICO, jogo.ui!!.jogadorCarreira!!.foco)
        assertEquals(j.temporadas.size, jogo.ui!!.jogadorCarreira!!.temporadas.size)
        // No histórico da liga aparecem os prémios individuais.
        tocar("Liga")
        tocar("Histórico")
        capturar("44-liga-historico-premios")

        // Apaga a carreira criada, para não interferir com os outros testes (partilham a pasta das gravações).
        jogo.sairParaMenu()
        esperar { jogo.ecra == Ecra.MENU && !jogo.ocupado }
        for (g in jogo.gravacoes.toList()) {
            jogo.apagarCarreira(g.id)
            esperar { !jogo.ocupado }
        }
        assertTrue(jogo.gravacoes.isEmpty())
    }

    /** Ecrãs da fase 7 (taças, Europa, seleções) com dados de demonstração. */
    @Test
    fun ecrasDaFase7() {
        esperar { !jogo.ocupado }
        jogo.abrirNovaCarreira()
        esperar { jogo.paises.isNotEmpty() && !jogo.ocupado }
        val clube = jogo.paises[0].ligas[0].clubes[4]
        jogo.comecarCarreira(pt.futebolcarreira.nucleo.carreira.ModoCarreira.TREINADOR, "Zé Mister", clube.id, "")
        esperar { jogo.ecra == Ecra.CARREIRA && !jogo.ocupado }
        jogo.mostrarDemonstracaoFase7()
        regra.waitForIdle()

        tocar("Liga")
        capturar("30-taca")
        jogo.escolherCompeticao("lc")
        regra.waitForIdle()
        capturar("31-europa-fase-liga")
        tocar("Eliminatórias")
        capturar("32-europa-eliminatorias")

        tocar("Mais")
        tocar("Seleções")
        capturar("33-selecoes")
        tocar("Convocados")
        capturar("34-selecoes-convocados")

        tocar("Início")
        capturar("35-inicio-taca")
    }

    /** Fase 10: abre o editor a partir do menu e captura a lista de clubes, a ficha de um jogador e o relatório de uma importação. */
    @Test
    fun editor() {
        esperar { !jogo.ocupado }
        assertEquals(Ecra.MENU, jogo.ecra)
        tocar("Editor")
        esperar { jogo.editor.pronto }
        assertEquals(Ecra.EDITOR, jogo.ecra)
        capturar("50-editor-clubes")

        tocar("Jogadores")
        regra.waitForIdle()
        val melhor = jogo.editor.jogadores.first()
        jogo.editor.abrirJogador(melhor.id)
        capturar("51-editor-jogador")
        jogo.editor.fecharJogador()

        val clube = jogo.editor.clubes.first()
        jogo.editor.abrirClube(clube.id)
        capturar("53-editor-clube")
        jogo.editor.fecharClube()
        tocar("Ficheiros")
        capturar("54-editor-ficheiros")

        // O seletor de ficheiros do Android não funciona aqui: o texto do ficheiro vai direto para o editor.
        jogo.editor.importarTexto(
            """{"equipa":{"id":"${clube.id}","nome":"Clube Teste","abreviatura":"CT","reputacao":150,"capacidade":1000,"orcamento":1,"cores":["#fff"]},
            "jogadores":[
              {"id":5,"nome":"Jogador A","nacionalidade":"ZZZ","idade":20,"posicao":"GR","atributos":{"ritmo":120,"remate":50,"passe":50,"drible":50,"defesa":50,"fisico":50,"guarda_redes":50}},
              {"id":5,"nome":"Jogador B","nacionalidade":"POR","idade":60,"posicao":"XX","atributos":{"ritmo":50}}]}""",
        )
        esperar { jogo.editor.relatorio != null && !jogo.editor.ocupado }
        assertTrue(jogo.editor.relatorio!!.erros.isNotEmpty())
        capturar("52-importar-relatorio")
    }
}
