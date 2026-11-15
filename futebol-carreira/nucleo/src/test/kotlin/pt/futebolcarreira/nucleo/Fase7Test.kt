package pt.futebolcarreira.nucleo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Gravador
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.simulacao.CalendarioDatas
import pt.futebolcarreira.nucleo.simulacao.DefRonda
import pt.futebolcarreira.nucleo.simulacao.EstadoEliminatoria
import pt.futebolcarreira.nucleo.simulacao.GestorEpocas
import pt.futebolcarreira.nucleo.simulacao.MotorJogo
import pt.futebolcarreira.nucleo.simulacao.Penaltis
import pt.futebolcarreira.nucleo.simulacao.Resultado
import pt.futebolcarreira.nucleo.simulacao.SimuladorEliminatoria
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Testes da Fase 7 (parte A): eliminatórias, taças nacionais e supertaças. */
class Fase7Test {
    private val pastaDados = File("../app/src/main/assets/dados")
    private val paises = listOf("POR", "ESP", "ING", "ITA", "ALE", "FRA")

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar { File(pastaDados, it).takeIf(File::exists)?.readText() }, "carregar: ${bd.erros.take(3)}")
        return bd
    }

    // ---------------------------------------------------------------- motor genérico (sem base de dados)

    private fun datas(rondas: Int, idaEVolta: Boolean = false): List<DefRonda> {
        val nomes = EstadoEliminatoria.nomesDasRondas(rondas)
        var d = LocalDate.of(2026, 9, 23)
        return List(rondas) { i ->
            val legs = if (idaEVolta && i < rondas - 1) listOf(d, d.plusWeeks(1)) else listOf(d)
            d = d.plusWeeks(4)
            DefRonda(nomes[i], legs, neutro = i == rondas - 1)
        }
    }

    /** Simulador de brincar: golos ao acaso; no jogo decisivo trata de prolongamento e penáltis como o jogo a sério. */
    private fun simuladorAcaso(rng: Random) = SimuladorEliminatoria { casa, fora, _, antesCasa, antesFora, decisivo ->
        var gc = rng.nextInt(0, 3)
        var gf = rng.nextInt(0, 3)
        var prolongamento = false
        var pc = 0
        var pf = 0
        if (decisivo && gc + antesCasa == gf + antesFora) {
            prolongamento = true
            gc += rng.nextInt(0, 2)
            gf += rng.nextInt(0, 2)
            if (gc + antesCasa == gf + antesFora) {
                val p = Penaltis.sortear(0.75, 0.75, rng)
                pc = p.first; pf = p.second
            }
        }
        Resultado(casa, fora, gc, gf, null, prolongamento, pc, pf)
    }

    private fun correr(t: EstadoEliminatoria, sim: SimuladorEliminatoria) {
        var guarda = 0
        while (!t.concluida && guarda++ < 100) {
            val dia = assertNotNull(t.proximaData)
            t.jogarDia(dia, sim)
        }
        assertTrue(t.concluida)
    }

    @Test
    fun `eliminatoria com potencia de dois nao tem isentos`() {
        val equipas = (1..16).map { "E$it" }
        repeat(20) { semente ->
            val rng = Random(semente)
            val t = EstadoEliminatoria("T", "Taça", equipas, datas(4), { it.drop(1).toInt() }, { 1 }, rng)
            assertEquals(listOf("1.ª eliminatória", "Quartos de final", "Meias-finais", "Final").size, t.defs.size)
            assertEquals(listOf("Oitavos de final", "Quartos de final", "Meias-finais", "Final"), t.defs.map { it.nome })
            correr(t, simuladorAcaso(rng))
            assertEquals(listOf(8, 4, 2, 1), t.rondas.map { it.confrontos.size })
            assertTrue(t.rondas.all { it.isentos.isEmpty() })
            val campeao = assertNotNull(t.campeao)
            assertTrue(campeao in equipas)
            assertNotNull(t.finalista)
            assertTrue(t.finalista != campeao)
            // Cada equipa só joga uma vez por ronda.
            for (r in t.rondas) {
                val jogam = r.confrontos.flatMap { listOf(it.a, it.b) }
                assertEquals(jogam.size, jogam.toSet().size)
                assertTrue(r.confrontos.all { it.vencedor != null })
            }
        }
    }

    @Test
    fun `eliminatoria com isentos para as de maior reputacao`() {
        assertEquals(32, EstadoEliminatoria.potenciaDeDois(20))
        assertEquals(5, EstadoEliminatoria.numeroDeRondas(20))
        assertEquals(1, EstadoEliminatoria.numeroDeRondas(2))
        assertEquals(listOf("1.ª eliminatória", "2.ª eliminatória", "Oitavos de final", "Quartos de final", "Meias-finais", "Final"), EstadoEliminatoria.nomesDasRondas(6))
        for (n in listOf(3, 5, 20, 36, 44)) {
            val equipas = (1..n).map { "E$it" }
            val rng = Random(n)
            val reputacao = { id: String -> id.drop(1).toInt() }   // E<n> tem a maior reputação
            val rondas = EstadoEliminatoria.numeroDeRondas(n)
            val t = EstadoEliminatoria("T", "Taça", equipas, datas(rondas), reputacao, { 1 }, rng)
            val isentas = EstadoEliminatoria.potenciaDeDois(n) - n
            assertEquals(isentas, t.rondas[0].isentos.size, "n=$n")
            // As isentas são as de maior reputação.
            assertEquals(equipas.sortedByDescending(reputacao).take(isentas).toSet(), t.rondas[0].isentos.toSet())
            assertEquals((n - isentas) / 2, t.rondas[0].confrontos.size)
            correr(t, simuladorAcaso(rng))
            assertEquals(rondas, t.rondas.size)
            // Depois da primeira ronda joga-se sempre com potências de dois.
            for (r in t.rondas.drop(1)) assertEquals(0, r.isentos.size)
            assertEquals(EstadoEliminatoria.potenciaDeDois(n) / 2, t.rondas[1].confrontos.size * 2)
            assertTrue(t.campeao in equipas)
        }
    }

    @Test
    fun `ida e volta conta o agregado sem golos fora`() {
        val equipas = (1..8).map { "E$it" }
        // A ganha 1-2 em casa; B joga a segunda mão em casa e perde 0-1: agregado 2-2, B marcou mais fora, mas não conta.
        val rng = Random(3)
        val t = EstadoEliminatoria("T", "Taça", equipas, datas(3, idaEVolta = true), { 1 }, { 1 }, rng)
        val primeira = t.rondas[0]
        assertTrue(primeira.confrontos.all { it.idaEVolta && it.datas.size == 2 })
        val sim = SimuladorEliminatoria { casa, fora, neutro, antesCasa, antesFora, decisivo ->
            if (!decisivo) Resultado(casa, fora, 1, 2)                   // ida: a casa perde 1-2
            else {
                // volta: quem recebe (o B do confronto) perde 0-1 -> agregado 2-2
                assertEquals(2, antesCasa)   // a casa da volta marcou 2 na ida
                assertEquals(1, antesFora)
                Resultado(casa, fora, 0, 1, null, true)   // prolongamento sem golos: vão aos penáltis
            }
        }
        // Só a primeira ronda com este simulador; a "volta" tem de acabar nos penáltis (decididos pelo motor).
        t.jogarDia(primeira.confrontos[0].datas[0], sim)
        val c = primeira.confrontos[0]
        assertEquals(1, c.jogos.size)
        assertNull(c.vencedor)
        t.jogarDia(c.datas[1], sim)
        assertEquals(2, c.jogos.size)
        assertEquals(2, c.golos(c.a))
        assertEquals(2, c.golos(c.b))
        assertTrue(c.penaltis && c.prolongamento, "empate no agregado: prolongamento e penáltis")
        val (pa, pb) = assertNotNull(c.resultadoPenaltis)
        assertEquals(if (pa > pb) c.a else c.b, c.vencedor)

        // Com vários simuladores ao acaso, o vencedor é sempre quem tem mais golos no agregado (ou ganha nos penáltis).
        repeat(30) { s ->
            val r = Random(s)
            val e = EstadoEliminatoria("T", "Taça", equipas, datas(3, idaEVolta = true), { 1 }, { 1 }, r)
            correr(e, simuladorAcaso(r))
            for (ronda in e.rondas) for (cf in ronda.confrontos) {
                val ga = cf.golos(cf.a)
                val gb = cf.golos(cf.b)
                if (cf.idaEVolta) assertEquals(2, cf.jogos.size)
                when {
                    ga > gb -> assertEquals(cf.a, cf.vencedor)
                    gb > ga -> assertEquals(cf.b, cf.vencedor)
                    else -> {
                        assertTrue(cf.penaltis && cf.prolongamento)
                        val (x, y) = cf.resultadoPenaltis!!
                        assertTrue(x != y)
                        assertEquals(if (x > y) cf.a else cf.b, cf.vencedor)
                    }
                }
                // Só o último jogo pode ter prolongamento.
                assertTrue(cf.jogos.dropLast(1).none { it.prolongamento || it.penaltis })
            }
        }
    }

    @Test
    fun `penaltis e prolongamento dao sempre vencedor`() {
        val rng = Random(1)
        repeat(5000) {
            val (c, f) = Penaltis.sortear(0.5 + rng.nextDouble() * 0.4, 0.5 + rng.nextDouble() * 0.4, rng)
            assertTrue(c != f)
        }
        // Mesmo com ninguém a marcar nos primeiros pontapés, nunca fica empatado.
        val (c, f) = Penaltis.sortear(0.0, 0.0, Random(5))
        assertTrue(c != f)

        // Motor detalhado: jogos que acabam empatados aos 90 minutos vão a prolongamento e o relatório é coerente.
        val bd = carregar()
        val equipas = bd.equipasDaLiga("POR1")
        var comProlongamento = 0
        repeat(250) { n ->
            val casa = equipas[n % equipas.size]
            val fora = equipas[(n * 5 + 1) % equipas.size].let { if (it === casa) equipas[(n + 2) % equipas.size] else it }
            val r = MotorJogo.simular(casa, fora, rng, precisaProlongamento = { gc, gf -> gc == gf })
            val casaAos90 = r.eventos.count { it.tipo == pt.futebolcarreira.nucleo.simulacao.TipoEvento.GOLO && it.casa && it.minuto <= 90 }
            val foraAos90 = r.eventos.count { it.tipo == pt.futebolcarreira.nucleo.simulacao.TipoEvento.GOLO && !it.casa && it.minuto <= 90 }
            // O marcador do relatório é o oficial (inclui o prolongamento) e bate com os marcadores.
            assertEquals(r.golosCasa, r.casa.fichas.sumOf { it.golos })
            assertEquals(r.golosFora, r.fora.fichas.sumOf { it.golos })
            assertEquals(r.golosCasa, r.eventos.count { it.tipo == pt.futebolcarreira.nucleo.simulacao.TipoEvento.GOLO && it.casa })
            assertTrue(r.casa.fichas.size <= 11 + MotorJogo.SUBSTITUICOES + 1)
            if (r.prolongamento) {
                comProlongamento++
                assertEquals(casaAos90, foraAos90, "só vai a prolongamento quem está empatado aos 90'")
                assertTrue(r.duracao in 121..123)
                assertTrue(r.eventos.all { it.minuto <= r.duracao })
            } else {
                assertTrue(casaAos90 != foraAos90 || r.golosCasa != r.golosFora || false)
                assertTrue(r.duracao in 91..96)
            }
        }
        assertTrue(comProlongamento in 10..120, "empates aos 90 minutos: $comProlongamento de 250")
        // Em campo neutro joga-se na mesma.
        val neutro = MotorJogo.simular(equipas[0], equipas[1], rng, neutro = true, precisaProlongamento = { gc, gf -> gc == gf })
        assertEquals(neutro.golosCasa, neutro.casa.fichas.sumOf { it.golos })
    }

    // ---------------------------------------------------------------- época completa

    @Test
    fun `calendario das tacas`() {
        for (ano in 2026..2040) {
            val ocupadas = CalendarioDatas.jornadas(ano, 38).toSet()
            for (rondas in 1..7) for (ida in listOf(false, true)) {
                val d = CalendarioDatas.datasTaca(ano, rondas, ida, ocupadas)
                assertEquals(rondas, d.size)
                val todas = d.flatten()
                assertEquals(todas.sorted(), todas, "datas por ordem")
                assertEquals(todas.distinct(), todas)
                assertTrue(todas.none { it in ocupadas })
                for ((i, r) in d.withIndex()) {
                    if (i < rondas - 1) assertTrue(r.all { it.dayOfWeek == DayOfWeek.WEDNESDAY }, "$ano: quarta-feira")
                    assertEquals(if (ida && i < rondas - 1) 2 else 1, r.size)
                }
                assertTrue(d.last().single().dayOfWeek == DayOfWeek.SATURDAY)
                assertTrue(d.last().single().monthValue == 5 && d.last().single().dayOfMonth >= 25, "final no fim de maio")
            }
            val st = CalendarioDatas.supertaca(ano)
            assertTrue(st.dayOfWeek == DayOfWeek.SATURDAY && st.monthValue == 8 && st.dayOfMonth <= 7)
            assertTrue(st < CalendarioDatas.jornadas(ano, 34).first())
        }
    }

    @Test
    fun `epoca completa com as seis tacas e supertacas`() {
        val bd = carregar()
        // Os países têm taça e supertaça, só as ligas contam como ligas.
        for (p in paises) {
            assertEquals(2, bd.ligasDoPais(p).size)
            assertEquals(2, bd.tacasDoPais(p).size)
        }
        assertTrue(bd.ligasDoPais("POR").all { it.eLiga })
        assertEquals(
            setOf("Taça de Portugal", "Copa del Rey", "FA Cup", "Coppa Italia", "DFB-Pokal", "Coupe de France"),
            bd.competicoes.values.filter { it.eTaca }.map { it.nome }.toSet(),
        )
        assertEquals(
            setOf("Supertaça Cândido de Oliveira", "Supercopa de España", "FA Community Shield", "Supercoppa Italiana", "DFL-Supercup", "Trophée des Champions"),
            bd.competicoes.values.filter { it.eSupertaca }.map { it.nome }.toSet(),
        )

        val gestor = GestorEpocas(bd, semente = 77)
        assertEquals(12, gestor.eliminatorias.size)
        // Primeira época: a supertaça é entre os dois clubes de maior reputação da primeira divisão.
        for (p in paises) {
            val st = assertNotNull(gestor.tacaDoPais(p, supertaca = true))
            val esperados = bd.equipasDaLiga("${p}1").sortedByDescending { it.reputacao }.take(2).map { it.id }.toSet()
            assertEquals(esperados, st.participantes.toSet(), "$p: supertaça da primeira época")
            assertEquals(CalendarioDatas.supertaca(2026), st.proximaData)
            val taca = assertNotNull(gestor.tacaDoPais(p))
            assertEquals(bd.ligasDoPais(p).sumOf { bd.equipasDaLiga(it.id).size }, taca.participantes.size)
        }
        assertEquals(6, gestor.tacaDoPais("POR")!!.defs.size)   // 36 clubes: 28 isentos, 6 rondas
        assertEquals(28, gestor.tacaDoPais("POR")!!.rondas[0].isentos.size)

        gestor.simularAteAoFim()
        assertTrue(gestor.epocaTerminada())
        val resumo = gestor.historico.single()
        for (p in paises) {
            val clubes = bd.equipas.values.filter { it.pais == p }.map { it.id }.toSet()
            val taca = gestor.tacaDoPais(p)!!
            val super_ = gestor.tacaDoPais(p, supertaca = true)!!
            assertTrue(taca.concluida && super_.concluida)
            assertTrue(taca.campeao in clubes, "$p: vencedor da taça é do país")
            assertTrue(super_.campeao in clubes, "$p: vencedor da supertaça é do país")
            assertTrue(taca.finalista in clubes)
            assertEquals(taca.campeao, resumo.tacas.getValue("${p}_TACA").vencedor)
            assertEquals(taca.finalista, resumo.tacas.getValue("${p}_TACA").finalista)
            assertEquals(super_.campeao, resumo.supertacas.getValue("${p}_SUPER").vencedor)
            assertTrue(bd.equipas.getValue(taca.campeao!!).tacas >= 1)
            assertTrue(bd.equipas.getValue(super_.campeao!!).supertacas >= 1)
            assertTrue(taca.rondas.last().def.neutro && taca.rondas.last().nome == "Final")
            // Nenhum jogo da taça ficou por jogar nem sem vencedor.
            assertTrue(taca.rondas.all { r -> r.confrontos.all { c -> c.vencedor != null && c.jogos.size == c.datas.size } })
        }
        // Os títulos de liga e os de taça são contados à parte.
        assertEquals(6, bd.equipas.values.sumOf { it.titulos })
        assertEquals(6, bd.equipas.values.sumOf { it.tacas })
        assertEquals(6, bd.equipas.values.sumOf { it.supertacas })
        // Premios e bilheteira das taças chegaram aos clubes.
        val campeaoTaca = bd.equipas.getValue(gestor.tacaDoPais("POR")!!.campeao!!)
        assertTrue(campeaoTaca.contas.premios >= 400_000)
        // As estatísticas da liga continuam a bater certo com a tabela (os golos de taça contam à parte).
        for ((ligaId, estado) in gestor.estado) {
            assertEquals(estado.tabela.ordenada().sumOf { it.gm }, gestor.jogadoresDaLiga(ligaId).sumOf { it.epoca.golos })
        }
        assertTrue(bd.jogadores.values.sumOf { it.epocaTaca.golos } > 0)
    }

    @Test
    fun `jogos de taca nunca coincidem com jogos de liga do mesmo clube`() {
        val bd = carregar()
        val gestor = GestorEpocas(bd, semente = 5)
        gestor.simularAteAoFim()
        // Dias de liga de cada clube.
        val ligaDe = HashMap<String, MutableSet<LocalDate>>()
        for ((_, e) in gestor.estado) {
            e.calendario.forEachIndexed { i, jogos ->
                for (j in jogos) {
                    ligaDe.getOrPut(j.casa) { HashSet() } += e.datas[i]
                    ligaDe.getOrPut(j.fora) { HashSet() } += e.datas[i]
                }
            }
        }
        var jogosDeTaca = 0
        for (t in gestor.eliminatorias.values) {
            val diasDoClube = HashMap<String, MutableSet<LocalDate>>()
            for (r in t.rondas) for (c in r.confrontos) {
                for ((i, jogo) in c.jogos.withIndex()) {
                    jogosDeTaca++
                    val dia = c.datas[i]
                    for (clube in listOf(jogo.casa, jogo.fora)) {
                        assertTrue(dia !in ligaDe.getValue(clube), "${t.id}: $clube joga liga e taça no mesmo dia $dia")
                        assertTrue(diasDoClube.getOrPut(clube) { HashSet() }.add(dia), "$clube joga duas vezes no dia $dia")
                    }
                }
            }
        }
        assertTrue(jogosDeTaca > 150)
        // Dias de jogo da taça: quartas-feiras (finais e supertaças ao sábado), e a ordem das rondas respeita o calendário.
        for (t in gestor.eliminatorias.values) {
            val dias = t.rondas.map { it.def.datas.first() }
            assertEquals(dias.sorted(), dias)
            assertTrue(t.rondas.dropLast(1).all { it.def.datas.all { d -> d.dayOfWeek == DayOfWeek.WEDNESDAY } } || t.rondas.size == 1)
        }
    }

    @Test
    fun `segunda epoca com a supertaca entre campeao e vencedor da taca`() {
        val bd = carregar()
        val gestor = GestorEpocas(bd, semente = 21)
        gestor.simularAteAoFim()
        val resumo = gestor.historico.last()
        gestor.avancarParaEpocaSeguinte()
        assertEquals(2027, gestor.ano)
        for (p in paises) {
            val st = assertNotNull(gestor.tacaDoPais(p, supertaca = true))
            val campeao = resumo.ligas.getValue("${p}1").campeao
            val taca = resumo.tacas.getValue("${p}_TACA")
            val esperado = if (taca.vencedor != campeao) taca.vencedor else taca.finalista
            assertEquals(listOf(campeao, esperado), st.participantes, "$p: supertaça de 2027")
            assertEquals(CalendarioDatas.supertaca(2027), st.proximaData)
            assertNull(st.campeao)
            // A taça nova tem todos os clubes do país (depois das subidas e descidas) e começa do zero.
            val nova = gestor.tacaDoPais(p)!!
            assertEquals(bd.equipas.values.count { it.pais == p }, nova.participantes.size)
            assertNull(nova.campeao)
        }
        gestor.simularAteAoFim()
        assertEquals(2, gestor.historico.size)
        assertTrue(gestor.historico.last().supertacas.size == 6)
        // Os troféus acumulam-se de uma época para a outra.
        assertEquals(12, bd.equipas.values.sumOf { it.tacas })
        assertEquals(12, bd.equipas.values.sumOf { it.supertacas })
    }

    // ---------------------------------------------------------------- carreira

    @Test
    fun `carreira avanca pelos jogos de taca com mensagens`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("POR1").sortedBy { it.reputacao }[9].id
        val c = Carreira.nova(bd, "taca", "Rita Treinadora", ModoCarreira.TREINADOR, clube, semente = 8)
        assertEquals("Taça de Portugal", c.taca?.nome)
        assertTrue(c.jogosDoClube().map { it.data }.let { it == it.sorted() }, "calendário por ordem de data")
        var jogosTaca = 0
        var ultimaMensagem = c.mensagens.size
        val competicoes = HashSet<String>()
        while (!c.gestor.epocaTerminada()) {
            val proximo = c.proximoJogo()
            if (proximo == null) {
                // Sem mais jogos do clube (eliminado e liga acabada): avança até ao fim da época.
                assertNull(c.avancar())
                assertTrue(c.gestor.epocaTerminada())
                break
            }
            val jogado = assertNotNull(c.avancar())
            assertEquals(proximo.competicaoId, jogado.competicaoId)
            assertEquals(proximo.data, jogado.data)
            assertNotNull(jogado.resultado?.relatorio)
            competicoes += jogado.competicaoId
            if (!jogado.eLiga) {
                jogosTaca++
                assertEquals("taca", jogado.tipo)
                assertTrue(jogado.ronda.isNotEmpty() && jogado.competicaoNome == "Taça de Portugal")
                assertEquals(jogado.confronto?.casaDoJogo(0) == c.info.equipaId, jogado.emCasa)
                // O jogo foi decidido (jogo único): passou ou foi eliminado.
                assertNotNull(jogado.passou)
                val mensagem = c.mensagens.take(3).first { it.titulo.startsWith("Taça de Portugal") }
                assertTrue(mensagem.titulo.contains(jogado.ronda) && mensagem.titulo.contains(jogado.adversario.nome))
                assertTrue(
                    if (jogado.passou == true) mensagem.texto.contains("passa à próxima eliminatória") || mensagem.texto.contains("vence a Taça de Portugal")
                    else mensagem.texto.contains("eliminado") || mensagem.texto.contains("perde a final"),
                    mensagem.texto,
                )
                if (jogado.resultado!!.penaltis) assertTrue(mensagem.titulo.contains("g.p."))
                if (jogado.passou == false) {
                    // Eliminado: não há mais jogos desta taça.
                    assertTrue(c.jogosDoClube().none { it.tipo == "taca" && it.resultado == null })
                }
            }
            assertTrue(c.mensagens.size >= minOf(ultimaMensagem, Carreira.MAX_MENSAGENS - 3))
            ultimaMensagem = c.mensagens.size
        }
        assertTrue(jogosTaca >= 1, "o clube jogou pelo menos um jogo de taça")
        assertTrue("POR1" in competicoes && "POR_TACA" in competicoes)
        assertEquals(34, c.jogosDeLiga().count { it.resultado != null })
        assertTrue(c.mensagens.any { it.titulo.startsWith("Fim da época") && it.texto.contains("Taça de Portugal") })
        assertTrue(c.info.confiancaDirecao in 0..100 && c.info.confiancaAdeptos in 0..100)
    }

    @Test
    fun `um clube grande joga a supertaca e pode ganhar a taca`() {
        val bd = carregar()
        val gigante = bd.equipasDaLiga("ESP1").maxByOrNull { it.reputacao }!!
        val c = Carreira.nova(bd, "super", "Teste", ModoCarreira.TREINADOR, gigante.id, semente = 3)
        val primeiro = assertNotNull(c.proximoJogo())
        assertEquals("supertaca", primeiro.tipo)
        assertEquals(CalendarioDatas.supertaca(2026), primeiro.data)
        assertTrue(primeiro.neutro && primeiro.eFinal)
        val jogado = assertNotNull(c.avancar())
        assertEquals(CalendarioDatas.supertaca(2026), jogado.data)
        assertTrue(c.mensagens.any { it.titulo.startsWith("Supercopa de España") })
        assertNotNull(jogado.passou)

        // Muitos clubes seguem até ao fim: ao fim da época a mensagem de fim de época diz quem ganhou a taça.
        c.simularEpoca()
        val tacaVencida = c.gestor.historico.last().tacas.getValue("ESP_TACA").vencedor == gigante.id
        assertEquals(tacaVencida, c.mensagens.any { it.titulo.startsWith("Fim da época") && it.texto.contains("O teu clube venceu a Copa del Rey") })
        if (tacaVencida) {
            assertTrue(c.mensagens.any { it.texto.contains("O teu clube vence a Copa del Rey!") })
            assertEquals(1, gigante.tacas)
        }
    }

    // ---------------------------------------------------------------- gravação

    @Test
    fun `gravar e carregar a meio da epoca com tacas`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("ITA1").sortedBy { it.reputacao }[12].id
        val c = Carreira.nova(bd, "gravar7", "Marco", ModoCarreira.TREINADOR, clube, semente = 14)
        // Avança até o clube ter jogado vários jogos de taça (ou até ao fim da taça para ele).
        var guarda = 0
        while (c.jogosDoClube().count { it.tipo != "liga" && it.resultado != null } < 2 && guarda++ < 60 && !c.gestor.epocaTerminada()) c.avancar()
        val d = Gravador.deTexto(Gravador.paraTexto(c), Gravador.baseDadosParaTexto(c))

        assertEquals(c.gestor.eliminatorias.keys, d.gestor.eliminatorias.keys)
        for ((id, t) in c.gestor.eliminatorias) {
            val u = d.gestor.eliminatorias.getValue(id)
            assertEquals(t.participantes, u.participantes, id)
            assertEquals(t.concluida, u.concluida, id)
            assertEquals(t.campeao, u.campeao, id)
            assertEquals(t.finalista, u.finalista, id)
            assertEquals(t.defs.map { it.nome to it.datas }, u.defs.map { it.nome to it.datas })
            assertEquals(t.rondas.size, u.rondas.size, id)
            assertEquals(t.proximaData, u.proximaData, id)
            t.rondas.zip(u.rondas).forEach { (r, s) ->
                assertEquals(r.nome, s.nome)
                assertEquals(r.isentos, s.isentos)
                assertEquals(r.confrontos.size, s.confrontos.size)
                r.confrontos.zip(s.confrontos).forEach { (x, y) ->
                    assertEquals(listOf(x.a, x.b, x.vencedor, x.neutro, x.datas), listOf(y.a, y.b, y.vencedor, y.neutro, y.datas))
                    assertEquals(x.jogos.map { listOf(it.casa, it.fora, it.golosCasa, it.golosFora, it.prolongamento, it.penaltisCasa, it.penaltisFora) },
                        y.jogos.map { listOf(it.casa, it.fora, it.golosCasa, it.golosFora, it.prolongamento, it.penaltisCasa, it.penaltisFora) })
                }
            }
        }
        // Relatos dos jogos do clube nas taças ficam gravados; os dos outros jogos não.
        val meusOriginais = c.jogosDoClube().filter { it.tipo != "liga" && it.resultado != null }
        val meusLidos = d.jogosDoClube().filter { it.tipo != "liga" && it.resultado != null }
        assertTrue(meusOriginais.isNotEmpty())
        assertEquals(meusOriginais.size, meusLidos.size)
        for ((x, y) in meusOriginais.zip(meusLidos)) {
            val rx = assertNotNull(x.resultado?.relatorio)
            val ry = assertNotNull(y.resultado?.relatorio, "relato gravado de ${y.competicaoNome} ${y.ronda}")
            assertEquals(rx.eventos.map { "${it.minuto} ${it.tipo} ${it.jogador.id}" }, ry.eventos.map { "${it.minuto} ${it.tipo} ${it.jogador.id}" })
            assertEquals(rx.prolongamento, ry.prolongamento)
            assertEquals(rx.golosCasa, ry.golosCasa)
            assertEquals(x.passou, y.passou)
        }
        for (e in d.gestor.eliminatorias.values) {
            for (r in e.rondas) for (cf in r.confrontos) if (!cf.envolve(clube)) assertTrue(cf.jogos.all { it.relatorio == null })
        }
        // Estatísticas de taça dos jogadores e troféus também voltam.
        for (j in c.bd.jogadores.values) {
            val k = d.bd.jogadores.getValue(j.id)
            assertEquals(listOf(j.epocaTaca.jogos, j.epocaTaca.golos, j.epocaTaca.assistencias), listOf(k.epocaTaca.jogos, k.epocaTaca.golos, k.epocaTaca.assistencias))
        }
        // A carreira carregada continua até ao fim e dá os mesmos tipos de resultados.
        d.simularEpoca()
        // Um clube fraco pode despedir o treinador a meio da época: aceita-se uma proposta e continua-se.
        if (d.info.despedido) {
            d.aceitarProposta(d.info.propostas.first())
            d.simularEpoca()
        }
        assertTrue(d.gestor.epocaTerminada())
        assertEquals(6, d.gestor.historico.single().tacas.size)
        val fim = Gravador.deTexto(Gravador.paraTexto(d), Gravador.baseDadosParaTexto(d))
        assertTrue(fim.gestor.epocaTerminada())
        assertEquals(d.gestor.historico.single().tacas.mapValues { it.value.vencedor }, fim.gestor.historico.single().tacas.mapValues { it.value.vencedor })
        assertEquals(d.bd.equipas.values.sumOf { it.tacas }, fim.bd.equipas.values.sumOf { it.tacas })
        fim.novaEpoca()
        assertEquals(12, fim.gestor.eliminatorias.size)
        assertNotNull(fim.avancar())
    }

    @Test
    fun `gravacao antiga sem tacas carrega e as tacas comecam na epoca seguinte`() {
        val bd = carregar()
        val clube = bd.equipasDaLiga("POR1").first().id
        val c = Carreira.nova(bd, "antiga", "Ana", ModoCarreira.TREINADOR, clube, semente = 2)
        repeat(10) { c.avancar() }
        // Simula um ficheiro antigo: sem os campos das taças e da Europa, e sem taças em competicoes.json.
        val estado = Json.parseToJsonElement(Gravador.paraTexto(c)).jsonObject
        val semTacas = JsonObject(estado.filterKeys { it != "eliminatorias" && it != "europa" })
        val ficheiros = Json.parseToJsonElement(Gravador.baseDadosParaTexto(c)).jsonObject
        val competicoes = Json.parseToJsonElement(ficheiros.getValue("competicoes.json").jsonPrimitive.content).jsonArray
        val soLigas = buildJsonArray { competicoes.filter { it.jsonObject.getValue("tipo").jsonPrimitive.content == "liga" }.forEach { add(it) } }
        val antigaBd = JsonObject(ficheiros + ("competicoes.json" to JsonPrimitive(soLigas.toString())))

        val d = Gravador.deTexto(semTacas.toString(), antigaBd.toString())
        // Escolha: a época em curso continua sem taças (já passaram datas), mas as taças ficam definidas.
        assertTrue(d.gestor.eliminatorias.isEmpty())
        assertEquals(12, d.bd.competicoes.values.count { it.eLiga })
        assertEquals(12, d.bd.competicoes.values.count { it.eTaca || it.eSupertaca })
        assertEquals(c.jogosDeLiga().count { it.resultado != null }, d.jogosDoClube().count { it.resultado != null })
        assertTrue(d.jogosDoClube().all { it.eLiga })
        d.simularEpoca()
        assertTrue(d.gestor.epocaTerminada())
        assertTrue(d.gestor.historico.single().tacas.isEmpty())
        // Na época seguinte as taças aparecem; a supertaça usa o campeão e o clube de maior reputação a seguir.
        d.novaEpoca()
        assertEquals(12, d.gestor.eliminatorias.size)
        val st = assertNotNull(d.gestor.tacaDoPais("POR", supertaca = true))
        assertEquals(d.gestor.historico.single().ligas.getValue("POR1").campeao, st.participantes.first())
        assertEquals(2, st.participantes.toSet().size)
        d.simularEpoca()
        assertTrue(d.gestor.epocaTerminada())
        assertEquals(6, d.gestor.historico.last().tacas.size)
    }
}
