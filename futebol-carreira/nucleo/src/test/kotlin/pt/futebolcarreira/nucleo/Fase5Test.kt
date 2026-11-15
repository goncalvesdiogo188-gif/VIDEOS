package pt.futebolcarreira.nucleo

import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Gravador
import pt.futebolcarreira.nucleo.carreira.MIN_PLANTEL
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.carreira.Resposta
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.simulacao.Financas
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Testes da Fase 5: contratos, finanças e mercado de transferências. */
class Fase5Test {
    private val pastaDados = File("../app/src/main/assets/dados")

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar { File(pastaDados, it).takeIf(File::exists)?.readText() }, "carregar: ${bd.erros.take(3)}")
        return bd
    }

    @Test
    fun `contratos e contas de uma epoca`() {
        val bd = carregar()
        assertTrue(bd.jogadores.values.all { it.salario >= 12_000 && it.contratoAte in 2027..2030 })
        val grande = bd.equipasDaLiga("POR1").maxBy { it.reputacao }
        val pequeno = bd.equipasDaLiga("POR2").minBy { it.reputacao }
        println("Folha salarial: ${grande.nome} ${grande.folhaSalarial() / 1_000_000} M€, ${pequeno.nome} ${pequeno.folhaSalarial() / 1000} mil €")
        assertTrue(grande.folhaSalarial() > 10 * pequeno.folhaSalarial())
        assertTrue(Financas.bilheteira(grande) > Financas.bilheteira(pequeno))

        val c = Carreira.nova(bd, "f", "F", ModoCarreira.TREINADOR, grande.id, semente = 8)
        val caixa = bd.equipas.values.associate { it.id to it.orcamento }
        c.simularEpoca()
        for (e in listOf(grande, pequeno)) {
            val k = e.contas
            println("${e.nome}: bilheteira ${k.bilheteira / 1000}k, patrocínios ${k.patrocinios / 1000}k, prémios ${k.premios / 1000}k, vendas ${k.vendas / 1000}k | salários ${k.salarios / 1000}k, compras ${k.compras / 1000}k | caixa ${e.orcamento / 1000}k")
            assertTrue(k.bilheteira > 0 && k.patrocinios > 0 && k.salarios > 0 && k.premios > 0)
            // O dinheiro em caixa mexe exatamente o que dizem as contas.
            assertEquals(caixa.getValue(e.id) + k.receitas() - k.despesas(), e.orcamento)
        }
        // A economia não descamba: a maioria dos clubes não acaba a época no vermelho.
        val negativos = bd.equipas.values.count { it.orcamento < 0 }
        println("Clubes com caixa negativa no fim da época: $negativos de ${bd.equipas.size}")
        assertTrue(negativos < bd.equipas.size / 5)

        // Mudança de época: emprestados voltam, contratos renovam ou acabam, ninguém fica com plantel curto.
        c.novaEpoca()
        assertTrue(bd.equipas.values.all { it.plantel.size >= 22 }, "plantéis completos")
        assertTrue(bd.equipas.values.all { e -> e.plantel.all { it.contratoAte >= 2028 && it.emprestadoPor.isEmpty() } }, "contratos válidos")
        assertTrue(grande.plantel.all { it.contratoAte >= 2028 })
        println("Jogadores livres: ${bd.livres().size}")
        assertTrue(bd.livres().isNotEmpty())
        assertEquals(0L, grande.contas.receitas())
        // Os jogadores livres ficam gravados.
        val d = Gravador.deTexto(Gravador.paraTexto(c), Gravador.baseDadosParaTexto(c))
        assertEquals(bd.livres().map { it.id }.sorted(), d.bd.livres().map { it.id }.sorted())
        assertEquals(bd.jogadores.size, d.bd.jogadores.size)
        // Cinco épocas seguidas sem problemas.
        repeat(4) { c.simularEpoca(); if (c.info.despedido) c.aceitarProposta(c.info.propostas.first()); c.novaEpoca() }
        assertTrue(bd.equipas.values.all { it.plantel.size in 22..32 })
        println("Ao fim de 5 épocas: ${bd.jogadores.size} jogadores, ${bd.livres().size} livres, caixa negativa em ${bd.equipas.values.count { it.orcamento < 0 }} clubes")
    }

    @Test
    fun `comprar vender e emprestar`() {
        val bd = carregar()
        val meu = bd.equipasDaLiga("ING1").sortedByDescending { it.reputacao }[3]
        val c = Carreira.nova(bd, "m", "M", ModoCarreira.TREINADOR, meu.id, semente = 31)
        val m = c.mercado
        assertTrue(m.aberto)   // a época começa em julho, com o mercado aberto

        // Comprar pelo preço pedido: o dinheiro e o jogador mudam de mãos.
        val alvo = bd.equipasDaLiga("POR1").maxBy { it.reputacao }.onzeParaJogo().first { it.lugar == "PL" }.jogador!!
        val vendedor = bd.equipas.getValue(alvo.equipa)
        val pedido = m.pedido(alvo)
        assertTrue(pedido > alvo.valorMercado() && pedido <= alvo.clausula())
        val caixa = meu.orcamento
        val caixaVendedor = vendedor.orcamento
        assertEquals(Resposta.ACEITE, m.comprar(alvo, pedido))
        assertTrue(alvo in meu.plantel && alvo !in vendedor.plantel && alvo.equipa == meu.id)
        assertEquals(caixa - pedido, meu.orcamento)
        assertEquals(caixaVendedor + pedido, vendedor.orcamento)
        assertEquals(pedido, meu.contas.compras)
        assertEquals(2030, alvo.contratoAte)
        assertTrue(c.mensagens.first().titulo.startsWith("Contratação"))

        // Propostas muito baixas são recusadas, e depois o clube não volta a negociar.
        val outro = bd.equipasDaLiga("ESP2").first().plantel.maxBy { it.geral() }
        assertEquals(Resposta.RECUSADA, m.comprar(outro, m.pedido(outro) / 2))
        assertEquals(Resposta.JA_RECUSOU, m.comprar(outro, m.pedido(outro)))
        // Sem dinheiro não há negócio; estrelas de clubes maiores não querem vir.
        val caro = bd.equipasDaLiga("ESP2")[1].plantel.maxBy { it.geral() }
        val guardado = meu.orcamento
        meu.orcamento = 1000
        assertEquals(Resposta.SEM_DINHEIRO, m.comprar(caro, m.pedido(caro)))
        meu.orcamento = guardado
        val estrela = bd.equipasDaLiga("ESP1").maxBy { it.reputacao }.plantel.minBy { it.geral() }
        meu.reputacao -= 30
        assertEquals(Resposta.JOGADOR_RECUSA, m.comprar(estrela, m.pedido(estrela)))
        meu.reputacao += 30

        // Vender: entra dinheiro e o jogador sai.
        val suplente = meu.plantel.filter { it.posicao != "GR" }.sortedBy { it.geral() }[5]
        val antes = meu.orcamento
        assertEquals(Resposta.ACEITE, m.vender(suplente))
        assertTrue(suplente !in meu.plantel && suplente.equipa.isNotEmpty() && meu.orcamento > antes)
        assertEquals(meu.orcamento - antes, meu.contas.vendas)

        // Empréstimos: quem entra e quem sai volta no fim da época.
        val emprestado = bd.equipasDaLiga("ITA1").maxBy { it.reputacao }.let { e -> e.plantel.first { j -> e.onzeParaJogo().none { it.jogador === j } && j.posicao == "MC" } }
        val dono = emprestado.equipa
        assertEquals(Resposta.ACEITE, m.pedirEmprestimo(emprestado))
        assertTrue(emprestado in meu.plantel && emprestado.emprestadoPor == dono)
        assertEquals(Resposta.CLUBE_NAO_CEDE, m.vender(emprestado))
        val cedido = meu.plantel.filter { it.posicao != "GR" && it.emprestadoPor.isEmpty() }.minBy { it.geral() }
        assertEquals(Resposta.ACEITE, m.emprestar(cedido))
        assertTrue(cedido !in meu.plantel && cedido.emprestadoPor == meu.id)

        // Não se pode vender até ficar sem jogadores.
        var vendas = 0
        while (meu.plantel.size > MIN_PLANTEL && vendas++ < 20) m.vender(meu.plantel.filter { it.posicao != "GR" && it.emprestadoPor.isEmpty() }.minBy { it.geral() })
        assertEquals(Resposta.PLANTEL_CURTO, m.vender(meu.plantel.first { it.posicao == "MC" && it.emprestadoPor.isEmpty() }))

        // Gravar a meio guarda transferências, empréstimos e contas.
        val d = Gravador.deTexto(Gravador.paraTexto(c), Gravador.baseDadosParaTexto(c))
        assertEquals(meu.plantel.map { it.id }.sorted(), d.equipa.plantel.map { it.id }.sorted())
        assertEquals(meu.orcamento, d.equipa.orcamento)
        assertEquals(meu.contas.compras, d.equipa.contas.compras)
        assertEquals(dono, d.bd.jogadores.getValue(emprestado.id).emprestadoPor)

        // Com o mercado fechado não há negócios, mas pode contratar-se quem está livre.
        while (m.aberto) c.avancar()
        assertEquals(Resposta.MERCADO_FECHADO, m.comprar(caro, m.pedido(caro)))
        assertEquals(Resposta.MERCADO_FECHADO, m.vender(meu.plantel.first { it.posicao == "MC" }))
        assertTrue(c.mensagens.any { it.titulo.startsWith("Mercado: negócios") }, "os outros clubes também fazem negócios")

        // Obras no estádio.
        val lugares = meu.capacidade
        meu.orcamento = maxOf(meu.orcamento, m.custoAmpliacao())
        assertEquals(Resposta.ACEITE, m.ampliarEstadio())
        assertEquals(lugares + m.lugaresNovos().let { meu.capacidade - lugares }, meu.capacidade)
        assertTrue(meu.capacidade > lugares && meu.contas.obras > 0)

        // Fim da época: os emprestados voltam a casa (contratos longos, para não ficarem livres por acaso).
        emprestado.contratoAte = 2035
        cedido.contratoAte = 2035
        c.simularEpoca()
        if (c.info.despedido) {
            c.aceitarProposta(c.info.propostas.first())
            c.simularEpoca()
        }
        c.novaEpoca()
        assertEquals(dono, emprestado.equipa)
        assertEquals(meu.id, cedido.equipa)
        assertTrue(emprestado.emprestadoPor.isEmpty() && cedido.emprestadoPor.isEmpty())
        val livre = bd.livres().maxBy { it.geral() }
        if (c.info.equipaId == meu.id) {
            assertEquals(Resposta.ACEITE, m.contratarLivre(livre))
            assertTrue(livre in meu.plantel)
        }

        // Na via de jogador não se mexe no mercado.
        val j = Carreira.nova(carregar(), "j", "J", ModoCarreira.JOGADOR, meu.id, semente = 2)
        assertEquals(Resposta.NAO_PERMITIDO, j.mercado.comprar(j.bd.equipasDaLiga("POR2").first().plantel.first(), 1))
    }
}
