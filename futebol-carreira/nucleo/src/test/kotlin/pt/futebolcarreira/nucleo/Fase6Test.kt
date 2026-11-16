package pt.futebolcarreira.nucleo

import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Gravador
import pt.futebolcarreira.nucleo.carreira.Instalacao
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.carreira.Olheiros
import pt.futebolcarreira.nucleo.carreira.Resposta
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.FocoTreino
import pt.futebolcarreira.nucleo.modelos.Intensidade
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.simulacao.Treino
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Testes da Fase 6: treino, forma e moral, lesões, academia e olheiros. */
class Fase6Test {
    private val pastaDados = File("../app/src/main/assets/dados")

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar { File(pastaDados, it).takeIf(File::exists)?.readText() }, "carregar: ${bd.erros.take(3)}")
        return bd
    }

    @Test
    fun `jovens evoluem e veteranos perdem qualidades`() {
        val bd = carregar()
        val todos = bd.equipas.values.flatMap { it.plantel }
        val jovens = todos.filter { it.idade <= 20 && it.potencial >= it.geral() + 8 }
        val veteranos = todos.filter { it.idade >= 33 }
        val antesJovens = jovens.associateWith { it.geral() }
        val antesVeteranos = veteranos.associateWith { it.geral() }
        val rng = Random(3)
        repeat(10) { Treino.mensal(bd, rng) }   // uma época de treino
        val ganho = jovens.map { it.geral() - antesJovens.getValue(it) }.average()
        val perda = veteranos.map { antesVeteranos.getValue(it) - it.geral() }.average()
        println("Numa época: jovens +%.1f de geral, veteranos -%.1f".format(ganho, perda))
        assertTrue(ganho in 2.0..8.0, "ganho dos jovens ($ganho)")
        assertTrue(perda in 0.5..5.0, "perda dos veteranos ($perda)")
        assertTrue(todos.all { it.geral() <= it.potencial }, "ninguém passa o potencial")
    }

    @Test
    fun `foco intensidade e instalacoes mexem no treino`() {
        fun ganho(foco: FocoTreino, intensidade: Intensidade, nivel: Int, atributo: String? = null): Double {
            val bd = carregar()
            val e = bd.equipasDaLiga("POR1").first()
            e.focoTreino = foco; e.intensidade = intensidade; e.nivelTreino = nivel
            val jovens = e.plantel.filter { it.idade <= 23 && it.potencial > it.geral() + 3 }
            val antes = jovens.associateWith { if (atributo == null) it.geral() else it.atributo(atributo) }
            val rng = Random(9)
            repeat(30) { for (j in jovens) Treino.evoluir(j, e, rng, false) }
            return jovens.map { (if (atributo == null) it.geral() else it.atributo(atributo)) - antes.getValue(it) }.average()
        }
        assertTrue(ganho(FocoTreino.EQUILIBRADO, Intensidade.FORTE, 3) > ganho(FocoTreino.EQUILIBRADO, Intensidade.LEVE, 3))
        assertTrue(ganho(FocoTreino.EQUILIBRADO, Intensidade.NORMAL, 5) > ganho(FocoTreino.EQUILIBRADO, Intensidade.NORMAL, 1))
        assertTrue(ganho(FocoTreino.FISICO, Intensidade.NORMAL, 3, "fisico") > ganho(FocoTreino.ATAQUE, Intensidade.NORMAL, 3, "fisico"))
    }

    @Test
    fun `forma moral e lesoes no treino`() {
        val bd = carregar()
        val c = Carreira.nova(bd, "t", "T", ModoCarreira.TREINADOR, bd.equipasDaLiga("POR1").first().id, semente = 4)
        val meu = c.equipa
        meu.intensidade = Intensidade.FORTE
        repeat(12) { c.avancar() }
        val formas = meu.plantel.map { it.forma }
        println("Forma: ${formas.min()}..${formas.max()} · moral: ${meu.plantel.minOf { it.moral }}..${meu.plantel.maxOf { it.moral }}")
        assertTrue(formas.distinct().size > 3, "a forma muda de jogador para jogador")
        assertTrue(c.mensagens.any { it.titulo == "Treino" }, "mensagens do treino")
        // Forma e moral contam no jogo.
        val j = Jogador().apply { forma = 90; moral = 90 }
        val k = Jogador().apply { forma = 10; moral = 10 }
        assertTrue(j.bonusForma() > 2.0 && k.bonusForma() < -2.0)
        // Ficam gravadas.
        val d = Gravador.deTexto(Gravador.paraTexto(c), Gravador.baseDadosParaTexto(c))
        assertEquals(meu.plantel.map { it.forma }, d.equipa.plantel.sortedBy { x -> meu.plantel.indexOfFirst { it.id == x.id } }.map { it.forma })
        assertEquals(Intensidade.FORTE, d.equipa.intensidade)
    }

    @Test
    fun `academia e instalacoes`() {
        val bd = carregar()
        val c = Carreira.nova(bd, "a", "A", ModoCarreira.TREINADOR, bd.equipasDaLiga("POR1")[2].id, semente = 6)
        val meu = c.equipa
        assertTrue(meu.academia.size >= 3, "a academia começa com jovens")
        assertTrue(meu.academia.all { it.idade in 16..17 && it.academia && it.equipa == meu.id && it !in meu.plantel })
        val tamanho = meu.plantel.size
        val jovem = meu.academia.maxBy { it.potencial }
        assertEquals(Resposta.ACEITE, c.academia.promover(jovem))
        assertTrue(jovem in meu.plantel && jovem !in meu.academia && !jovem.academia && meu.plantel.size == tamanho + 1)
        val outro = meu.academia.first()
        assertEquals(Resposta.ACEITE, c.academia.dispensar(outro))
        assertTrue(outro.equipa.isEmpty() && outro in bd.livres())

        // Melhorar instalações custa dinheiro e tem limite.
        meu.orcamento = 50_000_000
        val nivel = meu.nivelMedico
        val custo = c.academia.custoMelhoria(Instalacao.MEDICO)!!
        assertEquals(Resposta.ACEITE, c.academia.melhorar(Instalacao.MEDICO))
        assertEquals(nivel + 1, meu.nivelMedico)
        assertEquals(50_000_000 - custo, meu.orcamento)
        while (meu.nivelMedico < 5) c.academia.melhorar(Instalacao.MEDICO)
        assertEquals(Resposta.NIVEL_MAXIMO, c.academia.melhorar(Instalacao.MEDICO))

        // Academia gravada e carregada.
        val d = Gravador.deTexto(Gravador.paraTexto(c), Gravador.baseDadosParaTexto(c))
        assertEquals(meu.academia.map { it.id }.sorted(), d.equipa.academia.map { it.id }.sorted())
        assertEquals(5, d.equipa.nivelMedico)

        // Nova época: entra uma geração nova; quem faz 20 anos sai da academia.
        val velhos = meu.academia.onEach { it.idade = 19 }.toList()
        c.simularEpoca()
        if (c.info.despedido) return
        c.novaEpoca()
        assertTrue(velhos.none { it in meu.academia })
        assertTrue(meu.academia.isNotEmpty() && meu.academia.all { it.idade <= 18 })
        assertTrue(c.mensagens.any { it.titulo == "Academia: nova geração" })
        assertTrue(meu.plantel.size <= 30)
    }

    @Test
    fun `olheiros descobrem jovens promessas`() {
        val bd = carregar()
        val c = Carreira.nova(bd, "o", "O", ModoCarreira.TREINADOR, bd.equipasDaLiga("ESP1")[5].id, semente = 11)
        val o = c.olheiros
        val jovem = bd.equipas.values.filter { it.pais == "ITA" }.flatMap { it.plantel }.first { it.idade <= 21 }
        assertTrue(!o.conhece(jovem) && "–" in o.potencial(jovem), "potencial desconhecido aparece como intervalo")
        val caixa = c.equipa.orcamento
        assertEquals(Resposta.ACEITE, o.enviar("ITA"))
        assertEquals(caixa - o.custo("ITA"), c.equipa.orcamento)
        assertEquals(Resposta.OLHEIRO_OCUPADO, o.enviar("POR"))
        // Gravar com o olheiro em viagem.
        val d = Gravador.deTexto(Gravador.paraTexto(c), Gravador.baseDadosParaTexto(c))
        assertEquals("ITA", d.olheiros.estado.missao?.pais)
        // Avança até ele voltar.
        repeat(8) { if (o.estado.missao != null) c.avancar() }
        assertTrue(o.estado.missao == null, "o olheiro voltou")
        assertTrue(o.estado.relatorio.isNotEmpty() && o.estado.relatorio.size <= Olheiros.JOGADORES_POR_RELATORIO)
        for (id in o.estado.relatorio) {
            val j = bd.jogadores.getValue(id)
            assertTrue(j.idade <= 24 && bd.equipas.getValue(j.equipa).pais == "ITA" && o.conhece(j) && o.potencial(j) == "${j.potencial}")
        }
        assertTrue(c.mensagens.any { it.titulo.startsWith("Relatório do olheiro") })
    }
}
