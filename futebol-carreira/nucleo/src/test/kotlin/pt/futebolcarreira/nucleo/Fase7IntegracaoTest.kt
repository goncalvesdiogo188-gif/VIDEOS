package pt.futebolcarreira.nucleo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Gravador
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.simulacao.CalendarioDatas
import pt.futebolcarreira.nucleo.simulacao.FasesEuropa
import pt.futebolcarreira.nucleo.simulacao.GestorEpocas
import pt.futebolcarreira.nucleo.simulacao.TipoCompeticaoSelecoes
import pt.futebolcarreira.nucleo.simulacao.TipoEuropa
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Testes da Fase 7 (integração): ligas, taças e competições europeias na mesma época. */
class Fase7IntegracaoTest {
    private val pastaDados = File("../app/src/main/assets/dados")

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar { File(pastaDados, it).takeIf(File::exists)?.readText() }, "carregar: ${bd.erros.take(3)}")
        return bd
    }

    /** Todos os dias de jogo de cada clube numa época (liga, taças, supertaças e Europa), com repetições. */
    private fun diasDosClubes(g: GestorEpocas): Map<String, List<LocalDate>> {
        val dias = HashMap<String, MutableList<LocalDate>>()
        for (e in g.estado.values) e.calendario.forEachIndexed { j, jogos ->
            for (jogo in jogos) for (c in listOf(jogo.casa, jogo.fora)) dias.getOrPut(c) { mutableListOf() } += e.datas[j]
        }
        for (t in g.eliminatorias.values) for (r in t.rondas) for (c in r.confrontos) {
            c.jogos.forEachIndexed { i, _ -> for (id in listOf(c.a, c.b)) dias.getOrPut(id) { mutableListOf() } += c.datas[i] }
        }
        val eu = assertNotNull(g.europa)
        for (tipo in TipoEuropa.values()) {
            val e = eu.estado(tipo)
            for (j in e.jogosLiga + e.eliminatorias.flatMap { it.jogos }) for (id in listOf(j.casa, j.fora)) dias.getOrPut(id) { mutableListOf() } += j.dia
        }
        return dias
    }

    @Test
    fun `epoca completa com ligas tacas e Europa sem dois jogos no mesmo dia`() {
        for (semente in listOf(3L, 4L)) {
            val bd = carregar()
            val g = GestorEpocas(bd, semente = semente)
            assertNotNull(g.europa)
            repeat(2) { epoca ->
                g.simularAteAoFim()
                assertTrue(g.epocaTerminada())
                val resumo = g.historico.last()
                assertEquals(setOf("UCL", "UEL"), resumo.europa.keys)
                for (tipo in TipoEuropa.values()) {
                    val e = assertNotNull(g.europa).estado(tipo)
                    assertTrue(e.terminou)
                    assertEquals(e.campeao, resumo.europa.getValue(tipo.id).vencedor)
                    assertTrue(resumo.europa.getValue(tipo.id).finalista in e.clubes)
                }
                assertEquals(2 * (epoca + 1), bd.equipas.values.sumOf { it.europeias })
                // Nenhum clube joga duas vezes no mesmo dia, em qualquer competição.
                for ((clube, dias) in diasDosClubes(g)) {
                    assertEquals(dias.size, dias.toSet().size, "$clube tem dois jogos no mesmo dia: ${dias.groupBy { it }.filter { it.value.size > 1 }.keys}")
                }
                // Finais: a da taça, a da Liga Europa e a dos Campeões em dias diferentes, e depois das ligas.
                val eu = assertNotNull(g.europa)
                val finalUcl = eu.campeoes.datas.final
                val finalUel = eu.europa.datas.final
                val finaisTacas = g.eliminatorias.values.filter { bd.competicoes.getValue(it.id).eTaca }.map { it.rondas.last().def.datas.single() }
                assertTrue(finaisTacas.none { it == finalUcl || it == finalUel })
                assertTrue(finalUcl != finalUel)
                // As estatísticas dos marcadores da liga continuam a bater certo com as tabelas.
                for ((ligaId, estado) in g.estado) {
                    assertEquals(estado.tabela.ordenada().sumOf { it.gm }, g.jogadoresDaLiga(ligaId).sumOf { it.epoca.golos })
                }
                if (epoca == 0) g.avancarParaEpocaSeguinte()
            }
        }
    }

    @Test
    fun `segunda epoca apura pelas classificacoes finais da primeira`() {
        val bd = carregar()
        val g = GestorEpocas(bd, semente = 12)
        // Primeira época: por reputação.
        val ucl1 = assertNotNull(g.europa).campeoes.clubes.toSet()
        for (liga in bd.competicoes.values.filter { it.nivel == 1 }) {
            assertEquals(bd.equipasDaLiga(liga.id).sortedByDescending { it.reputacao }.take(4).map { it.id }.toSet(), ucl1.filter { bd.equipas.getValue(it).liga == liga.id }.toSet())
        }
        g.simularAteAoFim()
        val tabelas = g.estado.mapValues { (_, e) -> e.tabela.ordenada().map { it.id } }
        g.avancarParaEpocaSeguinte()
        val eu = assertNotNull(g.europa)
        assertEquals(2027, eu.campeoes.ano)
        for (liga in bd.competicoes.values.filter { it.nivel == 1 }) {
            val ordem = tabelas.getValue(liga.id)
            assertEquals(ordem.take(4).toSet(), eu.campeoes.clubes.filter { it in ordem }.toSet(), "Campeões ${liga.id}")
            assertEquals(ordem.drop(4).take(4).toSet(), eu.europa.clubes.filter { it in ordem }.toSet(), "Liga Europa ${liga.id}")
        }
        assertEquals(CalendarioDatas.inicioDaEpoca(2027).year, eu.campeoes.datas.liga.first().year)
    }

    @Test
    fun `carreira de um clube grande avanca pelos jogos europeus`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("POR1").maxByOrNull { it.reputacao }!!.id
        val c = Carreira.nova(bd, "europa", "Rui Treinador", ModoCarreira.TREINADOR, clube, semente = 31)
        val europeus = c.jogosEuropeus()
        assertEquals(6, europeus.size, "fase de liga: 6 jogos")
        assertTrue(europeus.all { it.tipo == "europa" && it.competicaoNome == "Liga dos Campeões" && it.ronda.startsWith("${FasesEuropa.LIGA} · ") })
        assertEquals(europeus.map { it.data }, europeus.map { it.data }.sorted())
        assertTrue(c.jogosDoClube().map { it.data }.let { it == it.sorted() })
        val fases = LinkedHashSet<String>()
        var europeusJogados = 0
        while (!c.gestor.epocaTerminada()) {
            val proximo = c.proximoJogo() ?: break
            val jogado = assertNotNull(c.avancar())
            assertEquals(proximo.competicaoId, jogado.competicaoId)
            assertEquals(proximo.data, jogado.data)
            if (jogado.tipo == "europa") {
                europeusJogados++
                fases += jogado.ronda.substringBefore(" · ")
                assertNotNull(jogado.resultado?.relatorio, "relatório do jogo europeu do utilizador")
                assertEquals(jogado.neutro, jogado.eFinal)
                if (jogado.confronto != null && jogado.perna == 2) assertTrue(jogado.confronto.jogos.size == 2)
                assertTrue(c.mensagens.first().titulo.startsWith("Liga dos Campeões (${jogado.ronda}"), c.mensagens.first().titulo)
            }
        }
        c.simularEpoca()
        assertTrue(europeusJogados >= 6)
        assertTrue(FasesEuropa.LIGA in fases && FasesEuropa.PLAYOFF in fases || FasesEuropa.OITAVOS in fases)
        val todas = c.mensagens.joinToString("\n") { it.titulo + " " + it.texto }
        assertTrue("fase de liga" in todas)
        assertTrue("apurado" in todas)
        // Mensagem de fim de época com os vencedores europeus.
        assertTrue("Liga dos Campeões" in c.mensagens.first { it.titulo.startsWith("Fim da época") }.texto)
        assertTrue(c.gestor.epocaTerminada())
        assertTrue(c.info.confiancaDirecao in 0..100)
    }

    @Test
    fun `gravar e carregar a meio da Europa mantem estado e relatorios`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("POR1").maxByOrNull { it.reputacao }!!.id
        val c = Carreira.nova(bd, "gravar", "Rui", ModoCarreira.TREINADOR, clube, semente = 44)
        // Até haver eliminatórias europeias já sorteadas.
        while (c.jogosEuropeus().none { it.confronto != null } && !c.gestor.epocaTerminada()) c.avancar()
        repeat(14) { c.avancar() }
        val antes = c.jogosEuropeus()
        assertTrue(antes.any { it.resultado != null })
        val texto = Gravador.paraTexto(c)
        val d = Gravador.deTexto(texto, Gravador.baseDadosParaTexto(c))
        val depois = d.jogosEuropeus()
        assertEquals(antes.size, depois.size)
        for ((x, y) in antes.zip(depois)) {
            assertEquals(x.data, y.data); assertEquals(x.adversario.id, y.adversario.id); assertEquals(x.ronda, y.ronda)
            assertEquals(x.resultado?.golosCasa, y.resultado?.golosCasa); assertEquals(x.resultado?.golosFora, y.resultado?.golosFora)
            assertEquals(x.resultado?.penaltisCasa, y.resultado?.penaltisCasa)
            assertEquals(x.resultado?.relatorio != null, y.resultado?.relatorio != null, "relatório do jogo de ${x.data}")
            assertEquals(x.passou, y.passou)
        }
        assertEquals(c.bd.equipas.values.sumOf { it.europeias }, d.bd.equipas.values.sumOf { it.europeias })
        for (tipo in TipoEuropa.values()) {
            val a = assertNotNull(c.gestor.europa).estado(tipo)
            val b = assertNotNull(d.gestor.europa).estado(tipo)
            assertEquals(a.tabelaLiga().map { it.id to it.pts }, b.tabelaLiga().map { it.id to it.pts })
            assertEquals(a.proximaData(), b.proximaData())
            assertEquals(a.premiosDe(clube), b.premiosDe(clube))
        }
        // A carreira carregada acaba a época e passa à seguinte com Europa nova.
        d.simularEpoca()
        assertTrue(d.gestor.epocaTerminada())
        assertEquals(setOf("UCL", "UEL"), d.gestor.historico.single().europa.keys)
        d.novaEpoca()
        assertNotNull(d.gestor.europa)
        assertTrue(d.jogosDoClube().isNotEmpty())
    }

    @Test
    fun `gravacao antiga sem Europa carrega e a Europa comeca na epoca seguinte`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("POR1").maxByOrNull { it.reputacao }!!.id
        val c = Carreira.nova(bd, "antiga", "Ana", ModoCarreira.TREINADOR, clube, semente = 6)
        repeat(8) { c.avancar() }
        val estado = Json.parseToJsonElement(Gravador.paraTexto(c)).jsonObject
        val semEuropa = JsonObject(estado.filterKeys { it != "europa" })
        val d = Gravador.deTexto(semEuropa.toString(), Gravador.baseDadosParaTexto(c))
        assertNull(d.gestor.europa)
        assertTrue(d.jogosEuropeus().isEmpty())
        d.simularEpoca()
        assertTrue(d.gestor.epocaTerminada())
        assertTrue(d.gestor.historico.single().europa.isEmpty())
        d.novaEpoca()
        val eu = assertNotNull(d.gestor.europa)
        // Apurados pelas classificações finais: o clube grande volta à Europa.
        assertTrue(eu.campeoes.clubes.size == 24 && eu.europa.clubes.size == 24)
        assertNotNull(d.avancar())
    }

    // ------------------------------------------------------------------ seleções nacionais

    /** Dias em que cada seleção joga (nação -> dias, com repetições) e todos os dias de jogo de seleções. */
    private fun diasDasSelecoes(g: GestorEpocas): Map<String, List<LocalDate>> {
        val dias = HashMap<String, MutableList<LocalDate>>()
        for (c in assertNotNull(g.selecoes).competicoes) for (j in c.jogos) {
            for (n in listOf(j.casa, j.fora)) dias.getOrPut(n) { mutableListOf() } += j.dataLocal()
        }
        return dias
    }

    /** Acaba a época e passa à seguinte; uma carreira carregada joga com sorteios novos e o treinador pode ser despedido. */
    private fun terminarEpocaENovaEpoca(c: Carreira) {
        c.simularEpoca()
        if (c.info.despedido) {
            c.aceitarProposta(c.info.propostas.first())
            c.simularEpoca()
        }
        c.novaEpoca()
    }

    @Test
    fun `seleccoes nunca jogam no dia de um jogo de clube em quatro epocas`() {
        val bd = carregar()
        val g = GestorEpocas(bd, semente = 5)
        val s = assertNotNull(g.selecoes)
        repeat(4) {
            g.simularAteAoFim()
            assertTrue(g.epocaTerminada())
            // Termina já o verão (Final Four, Euro ou Mundial) para ver todas as datas da época.
            s.jogarTorneioDeVerao()
            val clubes = diasDosClubes(g).values.flatten().toSet()
            val selecoes = diasDasSelecoes(g)
            for ((nacao, dias) in selecoes) {
                assertEquals(dias.size, dias.toSet().size, "$nacao joga duas vezes no mesmo dia na época ${g.ano}")
                val choques = dias.filter { it in clubes }
                assertTrue(choques.isEmpty(), "${g.ano}: $nacao joga no dia de jogos de clubes: $choques")
            }
            assertTrue(selecoes.isNotEmpty())
            g.avancarParaEpocaSeguinte()
        }
        // Euro 2028 e Mundial 2030 (verão de 2030 ainda por jogar) e a Liga das Nações de cada ano.
        val vencidos = s.vencedores().map { it.tipo to it.ano }
        assertTrue(TipoCompeticaoSelecoes.EURO to 2028 in vencidos, "Euro 2028: $vencidos")
        for (ano in 2027..2029) assertTrue(TipoCompeticaoSelecoes.LIGA_NACOES to ano in vencidos, "Liga das Nações $ano: $vencidos")
    }

    @Test
    fun `carreira com seleccoes recebe convocatorias resultados e vencedores no verao`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("POR1").maxByOrNull { it.reputacao }!!.id
        val c = Carreira.nova(bd, "selecoes", "Rui", ModoCarreira.TREINADOR, clube, semente = 21)
        assertNotNull(c.gestor.selecoes)
        c.simularEpoca()
        val titulos = c.mensagens.map { it.titulo }
        assertTrue("Convocatórias" in titulos, "convocatórias: $titulos")
        assertTrue("Seleções: resultados" in titulos, "resultados: $titulos")
        val conv = c.mensagens.last { it.titulo == "Convocatórias" }
        assertTrue("Jogadores do teu clube" in conv.texto)
        // Poucas mensagens de seleções numa época (3 janelas de 3 jogos + março).
        assertTrue(c.mensagens.count { it.titulo.startsWith("Seleções") || it.titulo.startsWith("Convoc") } <= 30)
        // Ninguém fica "convocado" de um dia para o outro: a janela só tem convocados enquanto decorre.
        val s = assertNotNull(c.gestor.selecoes)
        assertTrue(s.vencedores().none { it.tipo == TipoCompeticaoSelecoes.LIGA_NACOES })
        c.novaEpoca()
        val ln = s.vencedores().single { it.tipo == TipoCompeticaoSelecoes.LIGA_NACOES }
        assertEquals(2027, ln.ano)
        assertTrue(c.mensagens.any { it.titulo.startsWith("Seleções: Liga das Nações") && s.nome(ln.vencedor) in it.texto })
        // Segunda época: Euro 2028 no verão.
        c.simularEpoca()
        c.novaEpoca()
        val euro = s.vencedores().single { it.tipo == TipoCompeticaoSelecoes.EURO }
        assertEquals(2028, euro.ano)
        assertTrue(c.mensagens.any { it.titulo.startsWith("Seleções: Campeonato da Europa") && s.nome(euro.vencedor) in it.texto })
        assertEquals(2028, c.gestor.ano)
        assertEquals(2028, s.epoca)
        assertTrue(c.gestor.convocatoriasPendentes.isEmpty() && c.gestor.jogosSelecoesPendentes.isEmpty())
    }

    @Test
    fun `gravar e carregar a meio da epoca mantem o estado das seleccoes`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("POR1").maxByOrNull { it.reputacao }!!.id
        val c = Carreira.nova(bd, "gravar-sel", "Ana", ModoCarreira.TREINADOR, clube, semente = 9)
        while (c.gestor.data < LocalDate.of(2026, 11, 20)) c.avancar()
        val s = assertNotNull(c.gestor.selecoes)
        assertTrue(s.competicoesAtivas().flatMap { it.jogos }.any { it.jogado })
        val d = Gravador.deTexto(Gravador.paraTexto(c), Gravador.baseDadosParaTexto(c))
        val t = assertNotNull(d.gestor.selecoes)
        assertEquals(s.epoca, t.epoca)
        assertEquals(s.proximaData(), t.proximaData())
        assertEquals(s.ranking().map { it.nacao to it.pontos }, t.ranking().map { it.nacao to it.pontos })
        assertEquals(s.convocadosDaJanela(), t.convocadosDaJanela())
        assertEquals(s.competicoes.map { it.id to it.jogos.count { j -> j.jogado } }, t.competicoes.map { it.id to it.jogos.count { j -> j.jogado } })
        assertEquals(s.nacoes.keys, t.nacoes.keys)
        for (n in s.nacoes.values.filter { !it.baseDados }.take(3)) assertEquals(s.esquadraoFicticio(n.id).map { it.id }, t.esquadraoFicticio(n.id).map { it.id })
        // A carreira carregada continua: acaba a época e passa à seguinte com as seleções em dia.
        terminarEpocaENovaEpoca(d)
        assertEquals(2027, t.epoca)
        assertTrue(t.vencedores().any { it.tipo == TipoCompeticaoSelecoes.LIGA_NACOES })
        assertNotNull(d.avancar())
    }

    @Test
    fun `gravacao antiga sem seleccoes carrega com estado novo e sem catalogo continua a jogar`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("POR1").maxByOrNull { it.reputacao }!!.id
        val c = Carreira.nova(bd, "antiga-sel", "Rui", ModoCarreira.TREINADOR, clube, semente = 3)
        while (c.gestor.data < LocalDate.of(2026, 10, 20)) c.avancar()
        val estado = Json.parseToJsonElement(Gravador.paraTexto(c)).jsonObject
        assertTrue("selecoes" in estado)
        val semSelecoes = JsonObject(estado.filterKeys { it != "selecoes" }).toString()
        val ficheiros = Json.parseToJsonElement(Gravador.baseDadosParaTexto(c)).jsonObject
        assertTrue("selecoes.json" in ficheiros, "o catálogo vai na base de dados gravada")
        val antigaBd = JsonObject(ficheiros.filterKeys { it != "selecoes.json" }).toString()
        // Com o catálogo da aplicação: estado novo para a época em curso, com os jogos de setembro já jogados.
        val d = Gravador.deTexto(semSelecoes, antigaBd) { File(pastaDados, it).takeIf(File::exists)?.readText() }
        val s = assertNotNull(d.gestor.selecoes)
        assertEquals(2026, s.epoca)
        assertTrue(s.competicoesAtivas().flatMap { it.jogos }.any { it.jogado })
        assertTrue(s.proximaData()!! > d.gestor.data)
        terminarEpocaENovaEpoca(d)
        assertTrue(assertNotNull(d.gestor.selecoes).vencedores().any { it.tipo == TipoCompeticaoSelecoes.LIGA_NACOES })
        // Sem catálogo nenhum: o jogo funciona, só que sem seleções.
        val e = Gravador.deTexto(semSelecoes, antigaBd)
        assertNull(e.gestor.selecoes)
        terminarEpocaENovaEpoca(e)
        assertNull(e.gestor.selecoes)
        assertNotNull(e.avancar())
    }
}
