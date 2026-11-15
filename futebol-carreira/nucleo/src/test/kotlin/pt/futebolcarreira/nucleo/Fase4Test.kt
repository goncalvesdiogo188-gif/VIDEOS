package pt.futebolcarreira.nucleo

import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Gravador
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.Formacao
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Testes da Fase 4: onze escolhido pelo treinador, objetivos, confiança, despedimento e propostas. */
class Fase4Test {
    private val pastaDados = File("../app/src/main/assets/dados")

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar { File(pastaDados, it).takeIf(File::exists)?.readText() }, "carregar: ${bd.erros.take(3)}")
        return bd
    }

    @Test
    fun `onze escolhido pelo treinador`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("POR1")[6]
        val c = Carreira.nova(bd, "onze", "Mister", ModoCarreira.TREINADOR, clube.id, semente = 4)
        val auto = clube.onzeParaJogo()
        // Põe o guarda-redes suplente a titular e troca os dois centrais de lugar.
        val suplente = clube.plantel.filter { it.posicao == "GR" && it !== auto[0].jogador }.maxBy { it.geral() }
        clube.definirTitular(0, suplente)
        val centrais = auto.indices.filter { auto[it].lugar == "DC" }
        clube.definirTitular(centrais[0], auto[centrais[1]].jogador!!)
        val onze = clube.onzeParaJogo()
        assertEquals(suplente, onze[0].jogador)
        assertEquals(auto[centrais[1]].jogador, onze[centrais[0]].jogador)
        assertEquals(auto[centrais[0]].jogador, onze[centrais[1]].jogador)
        assertEquals(11, onze.mapNotNull { it.jogador?.id }.toSet().size)
        assertTrue(clube.suplentesParaJogo(onze).none { s -> onze.any { it.jogador === s } })

        // O onze escolhido é o que entra em campo, e fica gravado.
        val jogo = assertNotNull(c.avancar())
        val relatorio = jogo.resultado!!.relatorio!!
        val lado = if (jogo.emCasa) relatorio.casa else relatorio.fora
        assertEquals(onze.map { it.jogador!!.id }, lado.fichas.filter { it.titular }.map { it.jogador.id })
        val d = Gravador.deTexto(Gravador.paraTexto(c), Gravador.baseDadosParaTexto(c))
        assertEquals(clube.onzeManual, d.equipa.onzeManual)

        // Um titular lesionado é substituído automaticamente; mudar de formação apaga o onze manual.
        suplente.lesao = 3
        assertTrue(clube.onzeParaJogo()[0].jogador !== suplente && clube.onzeParaJogo()[0].jogador?.posicao == "GR")
        clube.formacao = if (clube.formacao == Formacao.F532) Formacao.F433 else Formacao.F532
        assertEquals(null, clube.onzeManual)
    }

    @Test
    fun `objetivos confianca despedimento e propostas`() {
        val bd = carregar()
        val forte = bd.equipasDaLiga("ESP1").maxBy { it.reputacao }
        val fraco = bd.equipasDaLiga("ESP1").minBy { it.reputacao }
        assertEquals("Ser campeão", Carreira.nova(carregar(), "a", "A", ModoCarreira.TREINADOR, forte.id).info.objetivoTexto)
        val c = Carreira.nova(bd, "b", "B", ModoCarreira.TREINADOR, fraco.id, semente = 21)
        assertEquals("Evitar a descida", c.info.objetivoTexto)
        assertEquals(17, c.info.objetivoPosicao)
        assertEquals("Subir de divisão", Carreira.nova(carregar(), "c", "C", ModoCarreira.TREINADOR, bd.equipasDaLiga("ESP2").maxBy { it.reputacao }.id).info.objetivoTexto)

        // A confiança mexe com os resultados e fica gravada.
        repeat(5) { c.avancar() }
        assertTrue(c.info.confiancaDirecao != 60 || c.info.confiancaAdeptos != 60)
        val d = Gravador.deTexto(Gravador.paraTexto(c), Gravador.baseDadosParaTexto(c))
        assertEquals(c.info.confiancaDirecao, d.info.confiancaDirecao)
        assertEquals(c.info.objetivoTexto, d.info.objetivoTexto)

        // Despedimento: com a confiança no fundo, a direção despede e aparecem propostas.
        repeat(4) { c.avancar() }
        c.info.confiancaDirecao = 5
        c.info.confiancaAdeptos = 50
        var tentativas = 0
        while (!c.info.despedido && tentativas++ < 6) { c.avancar(); if (!c.info.despedido) c.info.confiancaDirecao = 5 }
        assertTrue(c.info.despedido, "despedido")
        assertTrue(c.info.propostas.isNotEmpty() && fraco.id !in c.info.propostas)
        val data = c.gestor.data
        assertEquals(null, c.avancar())                 // sem clube não se joga
        assertEquals(data, c.gestor.data)
        val novo = c.info.propostas.first()
        c.aceitarProposta(novo)
        assertEquals(novo, c.info.equipaId)
        assertTrue(!c.info.despedido && c.info.propostas.isEmpty() && c.info.confiancaDirecao == 60)
        assertTrue(c.info.objetivoTexto.isNotEmpty())
        assertNotNull(c.avancar())

        // Fim de época: há balanço e a carreira continua.
        c.simularEpoca()
        assertTrue(c.mensagens.any { it.titulo == "Balanço da direção" })
        if (c.info.despedido) c.aceitarProposta(c.info.propostas.first())
        c.novaEpoca()
        assertTrue(c.info.propostas.isEmpty())
        assertNotNull(c.avancar())

        // Na via de jogador não há objetivos de treinador nem despedimentos.
        val j = Carreira.nova(carregar(), "j", "J", ModoCarreira.JOGADOR, fraco.id, semente = 2)
        j.simularEpoca()
        assertTrue(!j.info.despedido && j.info.confiancaDirecao == 60 && j.mensagens.none { it.titulo == "Balanço da direção" })
    }
}
