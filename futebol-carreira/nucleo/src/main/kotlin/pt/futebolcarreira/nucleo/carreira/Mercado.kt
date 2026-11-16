package pt.futebolcarreira.nucleo.carreira

import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.simulacao.CalendarioDatas
import pt.futebolcarreira.nucleo.simulacao.EvolucaoPlantel
import kotlin.math.pow

/** Resposta a uma tentativa de negócio. */
enum class Resposta(val frase: String) {
    ACEITE("Negócio fechado!"),
    RECUSADA("O clube recusou a proposta e não quer voltar a negociar este jogador nesta janela."),
    JA_RECUSOU("O clube já recusou uma proposta tua por este jogador."),
    SEM_DINHEIRO("O clube não tem dinheiro suficiente em caixa."),
    PLANTEL_CHEIO("O plantel já está cheio (máximo de $MAX_PLANTEL jogadores)."),
    PLANTEL_CURTO("Ficavas com jogadores a menos (mínimo de $MIN_PLANTEL, com 2 guarda-redes)."),
    MERCADO_FECHADO("O mercado de transferências está fechado. Abre em julho, agosto e janeiro."),
    JOGADOR_RECUSA("O jogador não quer mudar para um clube com menos reputação."),
    CLUBE_NAO_CEDE("O clube não o deixa sair: faz-lhe falta."),
    SEM_INTERESSADOS("Não apareceu nenhum clube interessado."),
    NAO_PERMITIDO("Só o treinador pode tratar disto."),
    NIVEL_MAXIMO("Já está no nível máximo."),
    OLHEIRO_OCUPADO("O olheiro ainda não voltou da última viagem."),
}

const val MAX_PLANTEL = EvolucaoPlantel.MAX_PLANTEL
const val MIN_PLANTEL = 18

/**
 * Mercado de transferências visto do clube do utilizador: comprar, contratar
 * jogadores livres, vender, pedir e ceder por empréstimo. Também faz mexer
 * os outros clubes, para o mundo não ficar parado.
 */
class Mercado(private val c: Carreira) {
    private val bd get() = c.bd
    private val meu get() = c.equipa
    private val rng get() = c.gestor.aleatorio

    /** Jogadores (ids) cujo clube já recusou uma proposta nesta janela. */
    val recusados = HashSet<Int>()

    val aberto get() = CalendarioDatas.mercadoAberto(c.gestor.data)

    /** Um clube pode ficar sem este jogador? (precisa de plantel mínimo e de dois guarda-redes) */
    private fun podeSair(j: Jogador, equipa: Equipa): Boolean =
        equipa.plantel.size > MIN_PLANTEL && (j.posicao != "GR" || equipa.plantel.count { it.posicao == "GR" } > 2)

    /** Quanto o clube pede pelo jogador: mais caro se for titular; nunca acima da cláusula de rescisão. */
    fun pedido(j: Jogador): Long {
        val dono = bd.equipas[j.equipa] ?: return 0
        val titular = dono.onzeParaJogo().any { it.jogador === j }
        return minOf((j.valorMercado() * (if (titular) 1.6 else 1.2)).toLong() / 10_000 * 10_000, j.clausula())
    }

    /** O jogador aceita vir? Não desce muito de nível, a não ser que esteja sem clube. */
    private fun jogadorAceita(j: Jogador): Boolean {
        val dono = bd.equipas[j.equipa] ?: return true
        return meu.reputacao >= dono.reputacao - 8
    }

    private fun verificarCompra(j: Jogador): Resposta? = when {
        c.info.modo != ModoCarreira.TREINADOR || c.info.despedido -> Resposta.NAO_PERMITIDO
        meu.plantel.size >= MAX_PLANTEL -> Resposta.PLANTEL_CHEIO
        else -> null
    }

    /** Proposta de compra. Abaixo do pedido, o clube pode aceitar ou fechar a porta. */
    fun comprar(j: Jogador, oferta: Long): Resposta {
        verificarCompra(j)?.let { return it }
        val dono = bd.equipas[j.equipa] ?: return contratarLivre(j)
        if (dono === meu || j.emprestadoPor.isNotEmpty()) return Resposta.CLUBE_NAO_CEDE
        if (!aberto) return Resposta.MERCADO_FECHADO
        if (j.id in recusados) return Resposta.JA_RECUSOU
        if (!podeSair(j, dono)) return Resposta.CLUBE_NAO_CEDE
        if (oferta > meu.orcamento) return Resposta.SEM_DINHEIRO
        if (!jogadorAceita(j)) return Resposta.JOGADOR_RECUSA
        val pedido = pedido(j)
        // Com 70% do pedido não há hipótese; com 100% é certo.
        val probabilidade = if (oferta >= pedido) 1.0 else ((oferta.toDouble() / pedido - 0.7) / 0.3).coerceAtLeast(0.0).pow(2)
        if (rng.nextDouble() >= probabilidade) {
            recusados += j.id
            return Resposta.RECUSADA
        }
        pagar(meu, dono, oferta)
        mudar(j, meu)
        c.mensagem("Contratação: ${j.nome}", "${j.nome} (${j.posicao}, geral ${j.geral()}) chega de ${dono.nome} por ${euros(oferta)}. Contrato até ${j.contratoAte}.")
        return Resposta.ACEITE
    }

    /** Contrata um jogador sem clube (não custa transferência, só o salário). Pode ser feito em qualquer altura. */
    fun contratarLivre(j: Jogador): Resposta {
        verificarCompra(j)?.let { return it }
        if (j.equipa.isNotEmpty()) return Resposta.CLUBE_NAO_CEDE
        mudar(j, meu)
        c.mensagem("Contratação: ${j.nome}", "${j.nome} (${j.posicao}, geral ${j.geral()}) estava sem clube e assinou até ${j.contratoAte}.")
        return Resposta.ACEITE
    }

    /** Põe um jogador à venda: se houver um clube interessado e com dinheiro, o negócio faz-se logo. */
    fun vender(j: Jogador): Resposta {
        if (c.info.modo != ModoCarreira.TREINADOR || c.info.despedido || j.equipa != meu.id) return Resposta.NAO_PERMITIDO
        if (!aberto) return Resposta.MERCADO_FECHADO
        if (j.emprestadoPor.isNotEmpty()) return Resposta.CLUBE_NAO_CEDE
        if (!podeSair(j, meu)) return Resposta.PLANTEL_CURTO
        val preco = (j.valorMercado() * (0.85 + rng.nextDouble() * 0.25)).toLong() / 10_000 * 10_000
        val comprador = interessados(j).filter { it.orcamento >= preco }.randomOrNull(rng) ?: return Resposta.SEM_INTERESSADOS
        pagar(comprador, meu, preco)
        mudar(j, comprador)
        c.mensagem("Venda: ${j.nome}", "${j.nome} foi vendido a ${comprador.nome} por ${euros(preco)}.")
        return Resposta.ACEITE
    }

    /** Clubes onde o jogador seria útil: melhor do que a média do onze menos uns pontos. */
    private fun interessados(j: Jogador): List<Equipa> = bd.equipas.values.filter {
        it !== meu && it.plantel.size < MAX_PLANTEL && j.geral() >= it.forca().geral - 4 && j.geral() <= it.forca().geral + 9
    }

    /** Pede um jogador emprestado até ao fim da época (paga-se só o salário). */
    fun pedirEmprestimo(j: Jogador): Resposta {
        verificarCompra(j)?.let { return it }
        val dono = bd.equipas[j.equipa] ?: return Resposta.CLUBE_NAO_CEDE
        if (!aberto) return Resposta.MERCADO_FECHADO
        if (dono === meu || j.emprestadoPor.isNotEmpty()) return Resposta.CLUBE_NAO_CEDE
        // Só emprestam quem não é titular, e só se ficarem com gente suficiente.
        if (dono.onzeParaJogo().any { it.jogador === j } || dono.plantel.size <= 20 || !podeSair(j, dono)) return Resposta.CLUBE_NAO_CEDE
        if (meu.reputacao < dono.reputacao - 15) return Resposta.JOGADOR_RECUSA
        j.emprestadoPor = dono.id
        bd.mudarDeEquipa(j, meu.id)
        c.baseDadosPorGravar = true
        c.mensagem("Empréstimo: ${j.nome}", "${j.nome} (${j.posicao}, geral ${j.geral()}) chega emprestado por ${dono.nome} até ao fim da época.")
        return Resposta.ACEITE
    }

    /** Cede um jogador por empréstimo a um clube mais modesto, para ganhar minutos. Volta no fim da época. */
    fun emprestar(j: Jogador): Resposta {
        if (c.info.modo != ModoCarreira.TREINADOR || c.info.despedido || j.equipa != meu.id) return Resposta.NAO_PERMITIDO
        if (!aberto) return Resposta.MERCADO_FECHADO
        if (j.emprestadoPor.isNotEmpty()) return Resposta.CLUBE_NAO_CEDE
        if (!podeSair(j, meu)) return Resposta.PLANTEL_CURTO
        val destino = bd.equipas.values.filter {
            it !== meu && it.plantel.size < MAX_PLANTEL && it.reputacao < meu.reputacao && j.geral() >= it.forca().geral - 3
        }.maxByOrNull { (if (it.pais == meu.pais) 100 else 0) + it.reputacao } ?: return Resposta.SEM_INTERESSADOS
        j.emprestadoPor = meu.id
        bd.mudarDeEquipa(j, destino.id)
        c.baseDadosPorGravar = true
        c.mensagem("Empréstimo: ${j.nome}", "${j.nome} foi emprestado a ${destino.nome} até ao fim da época.")
        return Resposta.ACEITE
    }

    /** Aumenta o estádio em 10% (2500 € por lugar novo). */
    fun custoAmpliacao(): Long = lugaresNovos() * 2500L

    fun lugaresNovos(): Int = maxOf(500, meu.capacidade / 10 / 100 * 100)

    fun ampliarEstadio(): Resposta {
        if (c.info.modo != ModoCarreira.TREINADOR || c.info.despedido) return Resposta.NAO_PERMITIDO
        val custo = custoAmpliacao()
        if (custo > meu.orcamento) return Resposta.SEM_DINHEIRO
        val lugares = lugaresNovos()
        meu.orcamento -= custo
        meu.contas.obras += custo
        meu.capacidade += lugares
        c.baseDadosPorGravar = true
        c.mensagem("Obras no estádio", "O ${meu.estadio} ganhou $lugares lugares e passa a ter ${meu.capacidade}. Custo: ${euros(custo)}.")
        return Resposta.ACEITE
    }

    /**
     * Negócios entre os outros clubes, enquanto o mercado está aberto: quem tem
     * dinheiro vai buscar um jogador melhor do que os seus a um clube mais modesto.
     * Devolve frases sobre os negócios feitos.
     */
    fun negociosDosOutros(tentativas: Int): List<String> {
        val feitos = mutableListOf<String>()
        if (!aberto) return feitos
        val clubes = bd.equipas.values.filter { it !== meu }
        repeat(tentativas) {
            val comprador = clubes.random(rng)
            if (comprador.plantel.size >= MAX_PLANTEL - 2) return@repeat
            val onze = comprador.onzeParaJogo()
            val fraco = onze.filter { it.jogador != null }.minByOrNull { it.nota } ?: return@repeat
            val alvo = clubes.asSequence()
                .filter { it !== comprador && it.reputacao <= comprador.reputacao - 3 }
                .flatMap { it.plantel }
                .filter { it.posicao == fraco.lugar && it.geral() >= fraco.nota + 2 && it.emprestadoPor.isEmpty() && it.idade <= 30 }
                .filter { val dono = bd.equipas.getValue(it.equipa); podeSair(it, dono) && pedido(it) <= comprador.orcamento / 2 }
                .maxByOrNull { it.geral() } ?: return@repeat
            val dono = bd.equipas.getValue(alvo.equipa)
            val preco = pedido(alvo)
            pagar(comprador, dono, preco)
            mudar(alvo, comprador)
            feitos += "${alvo.nome} (${alvo.posicao}, ${alvo.geral()}) trocou ${dono.nome} por ${comprador.nome}: ${euros(preco)}."
        }
        return feitos
    }

    /**
     * Via de jogador: muda o jogador do utilizador para outro clube. Numa transferência o clube novo paga ao antigo
     * ("preco") e o jogador assina "anos" épocas pelo salário combinado; num empréstimo não há pagamento e o
     * jogador volta ao clube de origem no fim da época.
     */
    fun transferirJogador(j: Jogador, destino: Equipa, preco: Long, salario: Int, anos: Int, emprestimo: Boolean = false) {
        val origem = bd.equipas[j.equipa]
        if (emprestimo) {
            j.emprestadoPor = origem?.id ?: ""
            bd.mudarDeEquipa(j, destino.id)
        } else {
            if (origem != null && preco > 0) pagar(destino, origem, preco)
            j.emprestadoPor = ""
            mudar(j, destino)
            j.contratoAte = c.gestor.ano + anos
            j.salario = salario
        }
        c.baseDadosPorGravar = true
    }

    private fun pagar(comprador: Equipa, vendedor: Equipa, valor: Long) {
        comprador.orcamento -= valor
        comprador.contas.compras += valor
        vendedor.orcamento += valor
        vendedor.contas.vendas += valor
    }

    /** Muda o jogador de clube com um contrato novo de três anos. */
    private fun mudar(j: Jogador, destino: Equipa) {
        bd.mudarDeEquipa(j, destino.id)
        j.contratoAte = c.gestor.ano + 1 + 3
        j.salario = maxOf(j.salario, j.salarioJusto())
        c.baseDadosPorGravar = true
    }

    companion object {
        /** Dinheiro por extenso: 12,5 M€, 350 mil €. */
        fun euros(valor: Long): String = when {
            valor >= 1_000_000 || valor <= -1_000_000 -> "%.1f M€".format(valor / 1_000_000.0)
            else -> "${valor / 1000} mil €"
        }
    }
}
