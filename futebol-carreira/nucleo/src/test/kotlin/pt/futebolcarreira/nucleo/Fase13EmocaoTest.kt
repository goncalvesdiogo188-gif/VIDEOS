package pt.futebolcarreira.nucleo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import pt.futebolcarreira.nucleo.carreira.Balneario
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Conversas
import pt.futebolcarreira.nucleo.carreira.Efeitos
import pt.futebolcarreira.nucleo.carreira.FichaHistorica
import pt.futebolcarreira.nucleo.carreira.Gravador
import pt.futebolcarreira.nucleo.carreira.JogoDoClube
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.carreira.Momentos
import pt.futebolcarreira.nucleo.carreira.OpcaoDecisao
import pt.futebolcarreira.nucleo.carreira.TomPalestra
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.simulacao.Resultado
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Testes da Fase 13 (F1 a F4): imprensa, conversas e palestras, balneário e adeptos, momentos e sala de história. */
class Fase13EmocaoTest {
    private val pastaDados = File("../app/src/main/assets/dados")
    private val ler = { caminho: String -> File(pastaDados, caminho).takeIf(File::exists)?.readText() }

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar(ler), "carregar: ${bd.erros.take(3)}")
        return bd
    }

    private fun benfica(semente: Long = 5, modo: ModoCarreira = ModoCarreira.TREINADOR) =
        Carreira.nova(carregar(), "f13", "Rui", modo, "POR_02", semente = semente)

    /** Um jogo do clube com o resultado dado (sem relatório). */
    private fun jogoFalso(c: Carreira, meus: Int, deles: Int, adiante: Long = 0): JogoDoClube {
        val base = c.jogosDeLiga().first()
        val r = if (base.emCasa) Resultado(base.clubeId, base.adversario.id, meus, deles) else Resultado(base.adversario.id, base.clubeId, deles, meus)
        return JogoDoClube(
            base.jornada, base.indice, base.data.plusDays(adiante), base.adversario, base.emCasa, r, base.competicaoId, base.competicaoNome,
            clubeId = base.clubeId,
        )
    }

    private fun moralMedia(c: Carreira) = c.equipa.plantel.map { it.moral }.average()

    @Test
    fun `conferencia de imprensa depois de uma goleada tem tres respostas e mexe nos adeptos e nas manchetes`() {
        val c = benfica()
        val e = c.emocao
        c.info.confiancaAdeptos = 50
        e.atualizar(jogoFalso(c, 5, 0))
        val d = e.pendentes.firstOrNull { it.tipo == "imprensa" }
        assertNotNull(d, "devia haver conferência de imprensa")
        assertEquals(3, d.opcoes.size)
        assertTrue(d.opcoes.all { it.rotulo.isNotBlank() && it.dica.isNotBlank() })
        val antes = c.info.confiancaAdeptos
        val texto = e.responder(d.id, 1)   // a resposta ousada
        assertTrue(texto.isNotBlank() && texto.contains("Efeitos"), texto)
        assertTrue(e.pendentes.none { it.id == d.id }, "a decisão respondida sai da lista")
        assertTrue(c.info.confiancaAdeptos != antes, "os adeptos têm de reagir")
        assertTrue(c.manchetes.any { it.tipo == "imprensa" && it.doClube }, "falta a manchete da imprensa")
        assertEquals("Esta decisão já não está disponível.", e.responder(d.id, 0))
    }

    @Test
    fun `resposta calma depois de uma derrota pesada sobe a moral do plantel`() {
        val c = benfica()
        val e = c.emocao
        for (j in c.equipa.plantel) j.moral = 40
        e.atualizar(jogoFalso(c, 0, 4))
        val d = e.pendentes.first { it.tipo == "imprensa" }
        e.responder(d.id, 0)
        assertTrue(c.equipa.plantel.all { it.moral == 43 }, "a moral devia subir 3 pontos")
    }

    @Test
    fun `a imprensa antes de um derby e espacada e nao repete`() {
        val c = benfica()
        val e = c.emocao
        val derby = c.jogosDeLiga().first { it.derby == "Derby de Lisboa" }
        // Fingir que o derby é o próximo jogo: a importância depende só do jogo.
        assertEquals("derby", pt.futebolcarreira.nucleo.carreira.Imprensa.importancia(c, derby))
        val normal = c.jogosDeLiga().first { it.derby == null }
        assertEquals(null, pt.futebolcarreira.nucleo.carreira.Imprensa.importancia(c, normal))
        // Duas vezes seguidas só dá uma conferência.
        e.atualizar(jogoFalso(c, 3, 0))
        e.atualizar(jogoFalso(c, 3, 0))
        assertTrue(e.pendentes.count { it.tipo == "imprensa" } <= 1)
    }

    @Test
    fun `palestra antes do jogo com moral baixo tem tres tons e efeito pequeno`() {
        val c = benfica()
        val e = c.emocao
        for (j in c.equipa.plantel) j.moral = 38
        e.atualizar(null)
        val d = e.pendentes.firstOrNull { it.tipo == "palestra" }
        assertNotNull(d, "com o moral em baixo devia haver palestra")
        assertEquals(3, d.opcoes.size)
        assertTrue(d.opcoes.any { it.rotulo.startsWith("Motivar") } && d.opcoes.any { it.rotulo.startsWith("Pressionar") } && d.opcoes.any { it.rotulo.startsWith("Acalmar") })
        e.responder(d.id, 0)   // motivar
        assertTrue(c.equipa.plantel.all { it.moral in 41..43 }, "motivar devia dar 3 a 4 pontos")
    }

    @Test
    fun `palestra ao intervalo mexe pouco na moral e depende do resultado`() {
        val c = benfica()
        val e = c.emocao
        for (j in c.equipa.plantel) j.moral = 50
        val texto = e.palestraIntervalo(TomPalestra.ACALMAR, 1, 0)
        assertTrue(texto.contains("Moral da equipa"), texto)
        assertTrue(c.equipa.plantel.filter { it.disponivel }.all { it.moral == 53 })
        for (tom in TomPalestra.entries) {
            for ((a, b) in listOf(0 to 2, 1 to 1, 2 to 0)) {
                for (j in c.equipa.plantel) j.moral = 50
                e.palestraIntervalo(tom, a, b)
                assertTrue(c.equipa.plantel.filter { it.disponivel }.all { it.moral in 45..55 }, "efeito demasiado grande: $tom $a-$b")
            }
        }
    }

    @Test
    fun `conversa individual com um suplente descontente e promessa de minutos`() {
        val c = benfica()
        val e = c.emocao
        val reserva = c.equipa.plantel.minByOrNull { it.geral() }!!
        val d = e.nova(
            "conversa", "Teste", "Teste",
            listOf(OpcaoDecisao("Prometer", efeitos = Efeitos(alvo = 9), promessaJogos = 2)), jogadorId = reserva.id,
        )
        reserva.moral = 30
        val antes = reserva.moral
        val texto = e.responder(d.id, 0)
        assertEquals(antes + 9, reserva.moral)
        assertTrue(texto.contains("Prometeste"), texto)
        assertEquals(1, e.estado.promessas.size)
        assertEquals(reserva.id, e.estado.promessas.first().jogadorId)
    }

    @Test
    fun `conversas geram-se sozinhas com jogadores descontentes`() {
        val c = benfica(6)
        val e = c.emocao
        repeat(10) { c.avancar() }
        val alvo = c.equipa.plantel.filter { it.epoca.jogos >= 2 }.minByOrNull { it.geral() }!!
        alvo.moral = 20
        var achou = false
        for (k in 1..60) {
            e.estado.ultima.remove("conversa")
            e.estado.conversas.clear()
            e.estado.decisoes.clear()
            c.gestor.data = c.gestor.data.plusDays(1)
            Conversas.aposJogo(e, jogoFalso(c, 1, 0, k.toLong()))
            if (e.pendentes.any { it.tipo == "conversa" }) { achou = true; break }
        }
        assertTrue(achou, "nunca apareceu uma conversa individual")
        val d = e.pendentes.first { it.tipo == "conversa" }
        assertNotNull(d.jogadorId)
        assertEquals(3, d.opcoes.size)
    }

    @Test
    fun `promessa de minutos cumpre-se ou quebra-se conforme joga ou nao`() {
        val c = benfica(7)
        val e = c.emocao
        val onze = c.equipa.onzeParaJogo().mapNotNull { it.jogador }
        val titular = onze.first()
        val fora = c.equipa.plantel.filter { j -> j.posicao != "GR" && onze.none { it.id == j.id } }.minByOrNull { it.geral() }!!
        val ids = listOf(titular, fora)
        for (j in ids) {
            val d = e.nova("conversa", "P", "P", listOf(OpcaoDecisao("Prometer", efeitos = Efeitos(alvo = 5), promessaJogos = 2)), jogadorId = j.id)
            e.responder(d.id, 0)
        }
        assertEquals(2, e.estado.promessas.size)
        val vistos = LinkedHashSet<String>()
        repeat(8) { c.avancar(); vistos += e.estado.registo }
        val registo = vistos.joinToString("\n")
        assertTrue(e.estado.promessas.none { it.jogadorId == titular.id }, "a promessa ao titular devia ter acabado")
        assertTrue(registo.contains("Cumpriste a promessa a ${titular.nome}") || titular.lesao > 0 || registo.contains("Quebraste a promessa a ${titular.nome}") && !titular.disponivel, registo)
        if (fora.disponivel && fora.epoca.jogos == 0) {
            assertTrue(registo.contains("Quebraste a promessa a ${fora.nome}"), registo)
        }
    }

    @Test
    fun `capitao e lideres escolhem-se sozinhos e o treinador pode nomear outro`() {
        val c = benfica()
        val e = c.emocao
        e.atualizar(null)
        val plantel = c.equipa.plantel
        val cap = e.estado.capitaoId
        assertNotNull(cap)
        assertTrue(plantel.any { it.id == cap })
        assertEquals(4, e.estado.lideres.size)
        assertEquals(cap, e.estado.lideres.first())
        val outro = Balneario.candidatos(e).first { it.id != cap }
        val moral = outro.moral
        val texto = e.nomearCapitao(outro.id)
        assertTrue(texto.contains(outro.nome), texto)
        assertEquals(outro.id, e.estado.capitaoId)
        assertEquals(minOf(100, moral + 5), outro.moral)
        // Não muda sozinho depois de nomeado.
        e.atualizar(null)
        assertEquals(outro.id, e.estado.capitaoId)
        // Se o capitão sai, escolhe-se outro.
        outro.equipa = "POR_01"
        c.equipa.plantel.remove(outro)
        e.atualizar(null)
        assertTrue(e.estado.capitaoId != outro.id && c.equipa.plantel.any { it.id == e.estado.capitaoId })
        assertEquals("Esse jogador já não está no plantel.", e.nomearCapitao(outro.id))
    }

    @Test
    fun `conflitos no balneario aparecem com o plantel em baixo e tem tres desfechos`() {
        val c = benfica(8)
        val e = c.emocao
        var achou = false
        for (k in 1..80) {
            for (j in c.equipa.plantel) j.moral = 30
            e.estado.ultima.remove("conflito")
            e.estado.decisoes.clear()
            c.gestor.data = c.gestor.data.plusDays(1)
            Balneario.aposJogo(e, jogoFalso(c, 0, 1, k.toLong()))
            if (e.pendentes.any { it.tipo == "conflito" }) { achou = true; break }
        }
        assertTrue(achou, "nunca apareceu um conflito")
        val d = e.pendentes.first { it.tipo == "conflito" }
        assertNotNull(d.jogadorId); assertNotNull(d.jogador2Id)
        assertEquals(3, d.opcoes.size)
        val a = c.bd.jogadores.getValue(d.jogadorId!!)
        val b = c.bd.jogadores.getValue(d.jogador2Id!!)
        val ma = a.moral; val mb = b.moral
        e.responder(d.id, 0)   // falar com os dois
        assertEquals(ma + 3, a.moral)
        assertEquals(mb + 3, b.moral)
    }

    @Test
    fun `protesto dos adeptos depois de uma derrota com a confianca em baixo`() {
        val c = benfica()
        val e = c.emocao
        c.info.confiancaAdeptos = 20
        Balneario.aposJogo(e, jogoFalso(c, 0, 2))
        val d = e.pendentes.firstOrNull { it.tipo == "protesto" }
        assertNotNull(d, "devia haver protesto")
        assertTrue(e.estado.bancadas.any { it.contains("Protesto") })
        val antes = c.info.confiancaAdeptos
        e.responder(d.id, 2)   // ignorar
        assertEquals(antes - 4, c.info.confiancaAdeptos)
        // Só um protesto de cada vez.
        Balneario.aposJogo(e, jogoFalso(c, 0, 2, 1))
        assertTrue(e.pendentes.none { it.tipo == "protesto" })
    }

    @Test
    fun `canticos das bancadas ficam registados em texto`() {
        val c = benfica(9)
        c.info.confiancaAdeptos = 80
        val e = c.emocao
        for (k in 1..12) {
            c.gestor.data = c.gestor.data.plusDays(1)
            Balneario.aposJogo(e, jogoFalso(c, 3, 0, k.toLong()))
        }
        assertTrue(e.estado.bancadas.isNotEmpty() && e.estado.bancadas.size <= 15)
        assertTrue(e.estado.bancadas.all { it.contains("«") })
    }

    @Test
    fun `homenagem a uma lenda que sai do clube`() {
        val c = benfica()
        val e = c.emocao
        val j = c.equipa.plantel.first()
        e.historia.jogadores[j.id] = FichaHistorica(j.id, j.nome, j.posicao, jogos = 200, golos = 70, epocas = 8, ultimoAno = 2025, lenda = true)
        // Ainda no clube: nada acontece.
        Momentos.novaEpoca(e)
        assertTrue(e.pendentes.none { it.tipo == "homenagem" })
        // Sai do clube.
        j.equipa = "POR_01"
        c.equipa.plantel.remove(j)
        Momentos.novaEpoca(e)
        val d = e.pendentes.firstOrNull { it.tipo == "homenagem" }
        assertNotNull(d)
        val antes = c.info.confiancaAdeptos
        e.responder(d.id, 0)
        assertEquals(minOf(100, antes + 5), c.info.confiancaAdeptos)
        assertTrue(c.manchetes.any { it.titulo.contains("homenagem") })
        // Só uma vez.
        Momentos.novaEpoca(e)
        assertTrue(e.pendentes.none { it.tipo == "homenagem" })
        assertEquals(1, Momentos.lendas(e).count { it.id == j.id })
    }

    @Test
    fun `sala de historia enche-se ao fim da epoca com premios recordes e marcadores`() {
        val c = benfica(5)
        c.info.confiancaDirecao = 100
        c.simularEpoca()
        assertTrue(c.gestor.epocaTerminada(), "a época devia ter acabado")
        val e = c.emocao
        val h = e.historia
        assertEquals(1, h.epocas.size)
        assertTrue(h.epocas.first().pontos > 0)
        assertTrue(h.recordes.containsKey("epoca"))
        assertTrue(h.recordes.containsKey("vitoria") || h.recordes.isNotEmpty())
        assertTrue(h.momentos.any { it.titulo == "Jogador do Ano do clube" }, h.momentos.joinToString { it.titulo })
        assertTrue(Momentos.melhoresMarcadores(e).isNotEmpty())
        assertTrue(Momentos.melhoresMarcadores(e).zipWithNext().all { (a, b) -> a.golos >= b.golos })
        // Os momentos chegam também à caixa de entrada.
        assertTrue(c.mensagens.any { it.titulo == "Jogador do Ano do clube" })
        // Fechar duas vezes não duplica.
        e.atualizar(null)
        assertEquals(1, h.epocas.size)
        if (!c.info.despedido) {
            c.novaEpoca()
            assertTrue(e.pendentes.all { it.validaAte.length == 10 })
        }
    }

    @Test
    fun `gravar e carregar guarda decisoes capitao promessas e historia`() {
        val c = benfica(10)
        repeat(6) { c.avancar() }
        val e = c.emocao
        e.atualizar(jogoFalso(c, 4, 0))
        val d = e.nova("conversa", "T", "T", listOf(OpcaoDecisao("Prometer", promessaJogos = 2)), jogadorId = c.equipa.plantel.first().id)
        e.responder(d.id, 0)
        e.historia.recordes["vitoria"] = pt.futebolcarreira.nucleo.carreira.Recorde("Maior vitória", 405, "4-0", "2026/27")
        e.nova("imprensa", "Outra", "Outra", listOf(OpcaoDecisao("a")))
        val x = Gravador.deTexto(Gravador.paraTexto(c), Gravador.baseDadosParaTexto(c))
        val f = x.emocao
        assertEquals(e.estado.capitaoId, f.estado.capitaoId)
        assertEquals(e.estado.decisoes.map { it.id to it.titulo }, f.estado.decisoes.map { it.id to it.titulo })
        assertEquals(e.estado.promessas.map { it.jogadorId to it.jogosAlvo }, f.estado.promessas.map { it.jogadorId to it.jogosAlvo })
        assertEquals(e.estado.registo, f.estado.registo)
        assertEquals(405, f.historia.recordes["vitoria"]?.valor)
        // E o jogo continua.
        if (!x.info.despedido) assertNotNull(x.avancar())
    }

    @Test
    fun `gravacao antiga sem a fase 13 carrega com valores por omissao e continua`() {
        val c = benfica(4)
        repeat(8) { c.avancar() }
        val estado = Json.parseToJsonElement(Gravador.paraTexto(c)).jsonObject
        assertTrue("emocao" in estado)
        val antigo = JsonObject(estado.filterKeys { it != "emocao" }).toString()
        val x = Gravador.deTexto(antigo, Gravador.baseDadosParaTexto(c))
        assertTrue(x.emocao.estado.decisoes.isEmpty() && x.emocao.estado.promessas.isEmpty())
        assertEquals(null, x.emocao.estado.capitaoId)
        assertTrue(x.emocao.historia.epocas.isEmpty())
        repeat(6) { if (!x.info.despedido) x.avancar() }
        if (!x.info.despedido) {
            assertNotNull(x.emocao.estado.capitaoId, "o capitão escolhe-se sozinho")
        }
    }

    @Test
    fun `a via de jogador nao tem decisoes mas a historia do clube enche-se`() {
        val c = benfica(3, ModoCarreira.JOGADOR)
        c.simularEpoca()
        assertTrue(c.emocao.pendentes.isEmpty())
        assertTrue(c.emocao.historia.epocas.isNotEmpty() || !c.gestor.epocaTerminada())
    }

    @Test
    fun `mudar de clube guarda a historia de cada um e limpa o resto`() {
        val c = benfica(5)
        c.info.confiancaDirecao = 100
        c.simularEpoca()
        val e = c.emocao
        val benficaId = c.info.equipaId
        val epocasBenfica = e.historia.epocas.size
        assertTrue(epocasBenfica == 1)
        c.info.propostas += "POR_01"
        c.info.despedido = false
        c.aceitarProposta("POR_01")
        e.sincronizar()
        assertTrue(e.historia.epocas.isEmpty(), "o novo clube começa sem história")
        assertTrue(e.estado.promessas.isEmpty() && e.estado.decisoes.isEmpty())
        assertEquals(epocasBenfica, e.estado.historias.getValue(benficaId).epocas.size)
    }

    @Test
    fun `ligar a fase 13 mantem a simulacao deterministica`() {
        val a = benfica(11)
        val b = benfica(11)
        a.simularEpoca(); b.simularEpoca()
        assertEquals(a.posicao(), b.posicao())
        assertEquals(a.estadoLiga.tabela.ordenada().map { it.id to it.pts }, b.estadoLiga.tabela.ordenada().map { it.id to it.pts })
    }
}
