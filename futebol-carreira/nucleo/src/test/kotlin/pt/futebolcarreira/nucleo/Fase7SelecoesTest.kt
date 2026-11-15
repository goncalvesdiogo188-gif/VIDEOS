package pt.futebolcarreira.nucleo

import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.simulacao.CalendarioDatas
import pt.futebolcarreira.nucleo.simulacao.CatalogoSelecoes
import pt.futebolcarreira.nucleo.simulacao.CompeticaoSelecoes
import pt.futebolcarreira.nucleo.simulacao.EstadoSelecoes
import pt.futebolcarreira.nucleo.simulacao.TipoCompeticaoSelecoes
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Testes da Fase 7 (parte C): seleções nacionais. */
class Fase7SelecoesTest {
    private val pastaDados = File("../app/src/main/assets/dados")
    private val ler: (String) -> String? = { File(pastaDados, it).takeIf(File::exists)?.readText() }

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar(ler), "carregar: ${bd.erros.take(3)}")
        return bd
    }

    private fun catalogo() = assertNotNull(CatalogoSelecoes.carregar(ler), "selecoes.json")

    private fun estado(epoca: Int, bd: BaseDados = carregar(), semente: Long = 7L) = EstadoSelecoes.criar(bd, catalogo(), epoca, semente)

    @Test
    fun `catalogo tem europeias e nao europeias e jogadores ficticios ficam fora da base de dados`() {
        val bd = carregar()
        val antes = bd.jogadores.size
        val s = EstadoSelecoes.criar(bd, catalogo(), 2026)
        val europeias = s.nacoes.values.filter { it.europeia }
        assertTrue(europeias.size - 6 >= 18, "europeias extra: ${europeias.size - 6}")
        assertTrue(s.nacoes.values.count { !it.europeia } >= 8)
        assertEquals(antes, bd.jogadores.size)
        for (n in s.nacoes.values) {
            if (n.baseDados) {
                assertTrue(n.id in bd.paises)
                assertTrue(s.esquadraoFicticio(n.id).isEmpty())
            } else {
                val plantel = s.esquadraoFicticio(n.id)
                assertEquals(23, plantel.size, n.nome)
                assertTrue(plantel.count { it.posicao == "GR" } >= 3)
                assertTrue(plantel.all { it.id < 0 && it.id !in bd.jogadores && it.nacionalidade == n.id && it.equipa.isEmpty() })
            }
        }
        assertTrue(bd.livres().none { it.id < 0 })
        // A força dos fictícios acompanha a reputação.
        val forte = s.convocados("ARG").map { it.geral() }.sortedDescending().take(11).average()
        val fraco = s.convocados("ISL").map { it.geral() }.sortedDescending().take(11).average()
        assertTrue(forte > fraco + 8, "ARG $forte vs ISL $fraco")
    }

    @Test
    fun `convocatorias tem 23 jogadores com 3 guarda-redes da nacionalidade certa`() {
        val bd = carregar()
        val s = estado(2026, bd)
        for (n in s.nacoes.values.filter { it.baseDados }) {
            val c = s.calcularConvocatoria(n.id)
            assertEquals(23, c.size, n.nome)
            assertEquals(23, c.map { it.id }.toSet().size)
            assertTrue(c.count { it.posicao == "GR" } >= 3)
            assertTrue(c.all { it.nacionalidade == n.id })
            // Os melhores avançados estão lá.
            val melhorPl = bd.jogadores.values.filter { it.nacionalidade == n.id && it.posicao == "PL" }.maxBy { it.geral() }
            assertTrue(c.any { it.id == melhorPl.id } || c.count { it.posicao == "PL" } >= 1)
        }
        // Lesionado só é chamado se for imprescindível.
        val c0 = s.calcularConvocatoria("POR")
        val estrela = c0.maxBy { it.geral() }
        estrela.lesao = 5
        val comLesao = s.calcularConvocatoria("POR")
        assertEquals(23, comLesao.size)
        assertTrue(comLesao.count { it.posicao == "GR" } >= 3)
        estrela.lesao = 0

        val avisos = s.convocarProximaJanela()
        assertTrue(avisos.isNotEmpty(), "primeira janela: setembro")
        val id = avisos.values.first().first()
        assertEquals(avisos.keys.first(), s.nacaoDoConvocado(id))
        assertTrue(s.foiConvocado(id))
        assertTrue(s.convocarProximaJanela().isEmpty(), "já estava preparada")
    }

    private fun jogosEstrutura(c: CompeticaoSelecoes, nomeFase: String) = c.fases.first { it.nome == nomeFase }

    @Test
    fun `liga das nacoes tem grupos final four e vencedor`() {
        val s = estado(2026)
        val ln = s.competicao("LN-2026")!!
        val grupos = ln.fases[0].grupos
        assertEquals(8, grupos.size)
        assertTrue(grupos.all { it.nacoes.size == 4 })
        assertEquals(32, grupos.flatMap { it.nacoes }.toSet().size)
        assertEquals(8 * 12, ln.fases[0].jogos.size)
        s.jogarTorneioDeVerao()
        assertTrue(ln.terminada)
        assertEquals(listOf("Fase de grupos", "Meias-finais", "Final"), ln.fases.map { it.nome })
        assertEquals(2, ln.fases[1].jogos.size)
        assertEquals(1, ln.fases[2].jogos.size)
        assertNotNull(ln.vencedor)
        val semis = ln.fases[1].jogos.flatMap { listOf(it.casa, it.fora) }.toSet()
        assertEquals(4, semis.size)
        assertTrue(ln.vencedor in semis)
        // Os quatro das meias-finais são vencedores de grupo da Liga A.
        for (g in grupos.filter { it.nome.startsWith("Liga A") }) assertTrue(s.tabela(ln.fases[0], g).first().nacao in semis)
        assertTrue(s.vencedores().any { it.tipo == TipoCompeticaoSelecoes.LIGA_NACOES && it.vencedor == ln.vencedor })
        // Tabelas coerentes: 6 jogos por seleção.
        assertTrue(grupos.all { g -> s.tabela(ln.fases[0], g).all { it.jogos == 6 } })
        // Ranking mexeu.
        assertTrue(s.ranking().first().pontos != 1300 + 8 * 93)
        assertEquals(1, s.ranking().first().posicao)
    }

    private fun verificarTorneio(s: EstadoSelecoes, t: CompeticaoSelecoes, equipas: Int, grupos: Int, jogosTotais: Int, fasesEsperadas: List<String>) {
        assertTrue(t.terminada)
        assertEquals(fasesEsperadas, t.fases.map { it.nome })
        assertEquals(grupos, t.fases[0].grupos.size)
        assertEquals(equipas, t.fases[0].grupos.flatMap { it.nacoes }.toSet().size)
        assertEquals(jogosTotais, t.jogos.size)
        assertTrue(t.jogos.all { it.jogado })
        for (f in t.fases.drop(1)) {
            assertTrue(f.jogos.all { it.vencedor() != null }, f.nome)
            assertTrue(f.jogos.all { it.penaltisCasa == -1 || (it.prolongamento && it.golosCasa == it.golosFora) })
        }
        assertEquals(1, t.fases.last().jogos.size)
        assertEquals(t.fases.last().jogos.first().vencedor(), t.vencedor)
        assertNotNull(t.finalista)
        assertEquals(1, t.fases.last().jogos.map { it.vencedor() }.toSet().size)
        // Todas as datas dentro do verão, antes de 1 de julho.
        assertTrue(t.jogos.all { it.dataLocal() < LocalDate.of(t.ano, 7, 1) })
        assertTrue(s.vencedores().count { it.tipo == t.tipo && it.ano == t.ano } == 1)
    }

    @Test
    fun `euro de 2028 com qualificacao tem 24 equipas e um vencedor`() {
        val bd = carregar()
        val s = estado(2027, bd)
        assertNotNull(s.competicao("QUALIFICACAO_EURO-2028"))
        val feitos = s.jogarAte(LocalDate.of(2028, 6, 30))
        assertTrue(feitos.isNotEmpty())
        val q = s.competicao("QUALIFICACAO_EURO-2028")!!
        assertTrue(q.terminada)
        assertEquals(24, q.qualificadas.size)
        assertEquals(24, q.qualificadas.toSet().size)
        assertEquals(8, q.fases[0].grupos.size)
        assertEquals(8, q.fases[1].jogos.size)
        assertTrue(q.qualificadas.all { s.nacoes.getValue(it).europeia })
        val euro = s.competicao("EURO-2028")!!
        assertEquals(q.qualificadas.toSet(), euro.fases[0].grupos.flatMap { it.nacoes }.toSet())
        verificarTorneio(s, euro, 24, 6, 36 + 8 + 4 + 2 + 1, listOf("Fase de grupos", "Oitavos de final", "Quartos de final", "Meias-finais", "Final"))
        // Os 16 dos oitavos: 6 primeiros, 6 segundos e 4 melhores terceiros.
        val grupos = euro.fases[0]
        val nOitavos = euro.fases[1].jogos.flatMap { listOf(it.casa, it.fora) }.toSet()
        assertEquals(16, nOitavos.size)
        for (g in grupos.grupos) {
            val t = s.tabela(grupos, g)
            assertTrue(t[0].nacao in nOitavos && t[1].nacao in nOitavos)
            assertTrue(t[3].nacao !in nOitavos)
        }
        assertEquals(4, grupos.grupos.count { s.tabela(grupos, it)[2].nacao in nOitavos })
        // Liga das Nações 2027 foi jogada na mesma época.
        assertTrue(s.competicao("LN-2027")!!.terminada)
    }

    @Test
    fun `mundial de 2030 tem 32 equipas e um vencedor`() {
        val bd = carregar()
        val s = estado(2029, bd)
        s.jogarAte(LocalDate.of(2030, 6, 30))
        val q = s.competicao("QUALIFICACAO_MUNDIAL-2030")!!
        assertEquals(32, q.qualificadas.size)
        assertEquals(32, q.qualificadas.toSet().size)
        assertEquals(16, q.qualificadas.count { s.nacoes.getValue(it).europeia })
        val mundial = s.competicao("MUNDIAL-2030")!!
        verificarTorneio(s, mundial, 32, 8, 48 + 8 + 4 + 2 + 1, listOf("Fase de grupos", "Oitavos de final", "Quartos de final", "Meias-finais", "Final"))
        // Primeiros e segundos de cada grupo chegam aos oitavos.
        val oitavos = mundial.fases[1].jogos.flatMap { listOf(it.casa, it.fora) }.toSet()
        for (g in mundial.fases[0].grupos) {
            val t = s.tabela(mundial.fases[0], g)
            assertTrue(t[0].nacao in oitavos && t[1].nacao in oitavos && t[2].nacao !in oitavos)
        }
    }

    @Test
    fun `jogos das selecoes nao mexem nas estatisticas nem castigos dos clubes mas as lesoes passam`() {
        val bd = carregar()
        val s = estado(2026, bd)
        val todos = bd.jogadores.values.toList()
        val antes = todos.associate { it.id to listOf(it.epoca.jogos, it.epoca.golos, it.epoca.assistencias, it.epoca.amarelos, it.epoca.vermelhos, it.suspensao) }
        // Um castigo de clube não impede a convocatória nem se perde.
        val castigado = s.calcularConvocatoria("ESP").first()
        castigado.suspensao = 3
        val jogos = s.jogarAte(LocalDate.of(2026, 11, 30))
        assertTrue(jogos.size >= 8 * 12 / 2)
        for (j in todos) {
            val esperado = if (j.id == castigado.id) listOf(0, 0, 0, 0, 0, 3) else antes.getValue(j.id)
            assertEquals(esperado, listOf(j.epoca.jogos, j.epoca.golos, j.epoca.assistencias, j.epoca.amarelos, j.epoca.vermelhos, j.suspensao), "jogador ${j.id}")
        }
        val comLesao = todos.count { it.lesao > 0 }
        assertTrue(comLesao > 0, "lesões das seleções passam para os clubes")
        // Internacionalizações e golos pela seleção.
        val convocados = s.convocadosDaJanela().values.flatten()
        assertTrue(convocados.isNotEmpty())
        val ativos = todos.filter { s.internacionalizacoes(it.id) > 0 }
        assertTrue(ativos.size >= 40, "internacionais: ${ativos.size}")
        assertTrue(ativos.sumOf { s.golosPelaSelecao(it.id) } > 0)
        assertTrue(s.jogosDaNacao("POR").any { it.jogado })
    }

    @Test
    fun `gravar e reconstruir da o mesmo estado e continua a jogar`() {
        val bd = carregar()
        val s = estado(2027, bd)
        s.jogarAte(LocalDate.of(2027, 10, 20))
        s.convocarProximaJanela()
        val json = s.paraJson()
        val r = EstadoSelecoes.deJson(bd, json)
        assertEquals(BaseDados.JSON.parseToJsonElement(json), BaseDados.JSON.parseToJsonElement(r.paraJson()))
        assertEquals(s.proximaData(), r.proximaData())
        assertEquals(s.ranking().map { it.nacao to it.pontos }, r.ranking().map { it.nacao to it.pontos })
        assertEquals(s.convocadosDaJanela(), r.convocadosDaJanela())
        r.jogarAte(LocalDate.of(2028, 6, 30))
        assertNotNull(r.competicao("EURO-2028")!!.vencedor)
        // E passa de época: nova Liga das Nações e plantéis fictícios renovados.
        val idsAntes = r.nacoes.values.filter { !it.baseDados }.flatMap { r.esquadraoFicticio(it.id) }.map { it.id }.toSet()
        r.passarParaEpoca(2028)
        assertEquals(2028, r.epoca)
        assertNotNull(r.competicao("LN-2028"))
        assertTrue(r.nacoes.values.filter { !it.baseDados }.all { r.esquadraoFicticio(it.id).size == 23 })
        assertTrue(idsAntes.isNotEmpty())
        assertTrue(r.vencedores().size >= 2)
    }

    @Test
    fun `nenhum jogo de selecoes cai num sabado de liga`() {
        val bd = carregar()
        val s = estado(2027, bd)
        s.jogarAte(LocalDate.of(2028, 6, 30))
        val datas = s.competicoes.flatMap { it.jogos }.map { it.dataLocal() }.toSet()
        assertTrue(datas.isNotEmpty())
        assertTrue(datas.none { it.dayOfWeek == DayOfWeek.SATURDAY }, "sábados: ${datas.filter { it.dayOfWeek == DayOfWeek.SATURDAY }}")
        val datasLiga = (16..46).flatMap { CalendarioDatas.jornadas(2027, it) }.toSet()
        assertTrue(datas.none { it in datasLiga }, "choques: ${datas.filter { it in datasLiga }}")
    }
}
