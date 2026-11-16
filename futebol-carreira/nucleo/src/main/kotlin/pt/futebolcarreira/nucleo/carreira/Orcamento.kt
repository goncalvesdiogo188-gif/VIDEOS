package pt.futebolcarreira.nucleo.carreira

import kotlinx.serialization.Serializable
import pt.futebolcarreira.nucleo.simulacao.Financas
import kotlin.math.pow
import kotlin.random.Random

/**
 * Fase 13 (G4): orçamentos, patrocinadores, preço dos bilhetes, dívida e regras de equilíbrio financeiro do clube
 * do utilizador.
 *
 * Tudo o que não for escolhido fica como era: sem orçamentos definidos não há limites, sem patrocinadores escolhidos
 * a receita de patrocínios é a habitual e o bilhete custa o preço normal. As escolhas só mexem nas contas pela
 * diferença em relação ao normal, por isso não fazem o clube falir nem enriquecer sem querer.
 */
enum class SlotPatrocinio(val nome: String, val parte: Double) {
    CAMISOLA("Camisola", 0.25),
    MATERIAL("Material desportivo", 0.08),
    ESTADIO("Nome do estádio", 0.07),
}

enum class PerfilPatrocinio(val nome: String, val descricao: String) {
    SEGURO("Seguro", "Mais dinheiro fixo, sem prémios."),
    EQUILIBRADO("Equilibrado", "Um pouco menos de fixo e um prémio por cada vitória."),
    AMBICIOSO("Ambicioso", "Pouco fixo, prémios grandes por vitória e pelo título: só compensa se ganhares muito."),
}

/** Um contrato de patrocínio (ou uma proposta, antes de assinar). Os valores são anuais, em euros. */
@Serializable
class Patrocinio(
    val id: String,
    val nome: String,
    val slot: SlotPatrocinio,
    val perfil: PerfilPatrocinio,
    val fixo: Long,
    val porVitoria: Long,
    val porTitulo: Long,
    /** Duração em épocas. */
    val anos: Int,
    /** A parte do patrocínio normal que este contrato substitui (a diferença é o que muda nas contas). */
    val base: Long,
    var anosRestantes: Int = anos,
)

@Serializable
class EmprestimoBanco(var restante: Long, var meses: Int)

enum class Saude(val nome: String) { ESTAVEL("Estável"), ATENCAO("Atenção"), CRITICA("Crítica") }

@Serializable
class EstadoFinancas(
    /** 0 = sem limite definido. */
    var orcamentoSalarios: Long = 0,
    var orcamentoTransferencias: Long = 0,
    /** 1,0 = preço normal; de 0,6 a 1,6. */
    var precoBilhete: Double = 1.0,
    val patrocinadores: MutableList<Patrocinio> = mutableListOf(),
    val emprestimos: MutableList<EmprestimoBanco> = mutableListOf(),
    /** Resultado (receitas menos despesas) das últimas épocas fechadas, a mais recente no fim. */
    val resultados: MutableList<Long> = mutableListOf(),
    val receitas: MutableList<Long> = mutableListOf(),
    /** O clube está impedido de contratar até ao fim desta época (ano), por regras de equilíbrio financeiro. */
    var embargoAteAno: Int = 0,
)

class Orcamento(private val c: Carreira, private val g: GestaoClube) {
    private val e get() = g.estado.financas
    private val meu get() = c.equipa
    private val ano get() = c.gestor.ano

    // ------------------------------------------------------------ previsões e orçamentos

    /** Receita prevista para a época (patrocínios, bilheteira em casa e prémios habituais). */
    fun receitaPrevista(): Long {
        val patrocinio = Financas.patrocinioAnual(meu)
        val casa = c.estadoLiga.totalJornadas / 2
        // Os clubes grandes enchem mais nos derbies e jogam mais em casa (taças e Europa): até +45% de bilheteira.
        val extra = 1.05 + (meu.reputacao - 60).coerceIn(0, 30) / 30.0 * 0.4
        val bilheteira = (Financas.bilheteira(meu) * casa * extra).toLong()
        return patrocinio + bilheteira + patrocinio * 15 / 100 + deltaPatrocinios()
    }

    /** A direção sugere gastar em salários (plantel e equipa técnica) 75% da receita prevista. */
    fun sugestaoSalarios(): Long = (receitaPrevista() * 0.75).toLong() / 10_000 * 10_000

    /** O máximo que a direção deixa definir para os salários. */
    fun maximoSalarios(): Long = ((receitaPrevista() * 0.90) + maxOf(0L, meu.orcamento) / 5).toLong() / 10_000 * 10_000

    fun sugestaoTransferencias(): Long = ((receitaPrevista() * 0.15) + maxOf(0L, meu.orcamento) * 0.3).toLong() / 10_000 * 10_000

    /** Os salários de hoje: plantel mais equipa técnica, por ano. */
    fun massaSalarial(): Long = meu.folhaSalarial() + g.equipaTecnica.custoAnual()

    fun definirOrcamentoSalarios(valor: Long): Resposta {
        if (!g.podeGerir) return Resposta.NAO_PERMITIDO
        if (valor < 0 || valor > maximoSalarios()) return Resposta.ORCAMENTO_SALARIOS
        e.orcamentoSalarios = valor
        return Resposta.ACEITE
    }

    fun definirOrcamentoTransferencias(valor: Long): Resposta {
        if (!g.podeGerir) return Resposta.NAO_PERMITIDO
        if (valor < 0) return Resposta.NAO_PERMITIDO
        e.orcamentoTransferencias = valor
        return Resposta.ACEITE
    }

    /** O que ainda se pode gastar em transferências esta época (null = sem limite definido). */
    fun disponivelTransferencias(): Long? {
        if (e.orcamentoTransferencias <= 0) return null
        return e.orcamentoTransferencias + meu.contas.vendas / 2 - meu.contas.compras - g.negociacoes.dividaEmPrestacoes()
    }

    /** null se a contratação cabe nas regras; senão o motivo. "adicional" é o salário anual que se acrescenta. */
    fun bloqueioSalarios(adicional: Long): Resposta? {
        if (e.orcamentoSalarios <= 0) return null
        return if (massaSalarial() + adicional > e.orcamentoSalarios) Resposta.ORCAMENTO_SALARIOS else null
    }

    fun bloqueioTransferencias(valor: Long): Resposta? {
        if (embargoAtivo()) return Resposta.EMBARGO
        val livre = disponivelTransferencias() ?: return null
        return if (valor > 0 && valor > livre) Resposta.ORCAMENTO_TRANSFERENCIAS else null
    }

    fun embargoAtivo(): Boolean = e.embargoAteAno >= ano && e.embargoAteAno > 0

    // ------------------------------------------------------------ bilhetes

    fun precoBilhete(): Double = e.precoBilhete

    fun definirPrecoBilhete(p: Double): Resposta {
        if (!g.podeGerir || p < 0.6 || p > 1.6) return Resposta.NAO_PERMITIDO
        e.precoBilhete = (p * 20).toInt() / 20.0
        return Resposta.ACEITE
    }

    /** Quanto a procura muda com o preço (1,0 = preço normal): mais barato enche mais, muito caro afasta adeptos. */
    fun procura(p: Double = e.precoBilhete): Double =
        if (p <= 1.0) 1.0 + 0.45 * (1.0 - p) else (1.0 - 0.8 * (p - 1.0) - 0.5 * (p - 1.0).pow(2)).coerceAtLeast(0.15)

    /** Receita de bilheteira de um jogo em casa ao preço escolhido, e a que seria ao preço normal. */
    fun bilheteira(derby: Boolean, fatorAmbiente: Double, p: Double = e.precoBilhete): Pair<Long, Long> {
        val normal = Financas.assistencia(meu, derby, fatorAmbiente)
        val precoNormal = 12.0 + meu.reputacao * 0.35
        val procuraNormal = if (derby) meu.capacidade * 1.25 else normal.toDouble()
        val novo = minOf(meu.capacidade.toDouble(), procuraNormal * procura(p))
        return (novo * precoNormal * p).toLong() to (normal * precoNormal).toLong()
    }

    // ------------------------------------------------------------ patrocinadores

    /** Receita extra (ou menos) por época dos patrocínios escolhidos, em relação ao normal. */
    fun deltaPatrocinios(): Long = e.patrocinadores.sumOf { it.fixo - it.base }

    fun patrocinadores(): List<Patrocinio> = e.patrocinadores

    fun contrato(slot: SlotPatrocinio): Patrocinio? = e.patrocinadores.firstOrNull { it.slot == slot }

    /** As três propostas desta época para um lugar (iguais para o mesmo clube e a mesma época). */
    fun propostas(slot: SlotPatrocinio): List<Patrocinio> {
        val base = (Financas.patrocinioAnual(meu) * slot.parte).toLong()
        return PerfilPatrocinio.entries.map { perfil ->
            val rng = Random(ano * 1009L + meu.id.hashCode() * 17L + slot.ordinal * 131L + perfil.ordinal * 7L)
            val marca = MARCAS[slot]!![rng.nextInt(MARCAS.getValue(slot).size)]
            val v = 0.95 + rng.nextDouble() * 0.10
            val (fixo, vitoria, titulo, anos) = when (perfil) {
                PerfilPatrocinio.SEGURO -> listOf(1.10 * v, 0.0, 0.0, 2.0)
                PerfilPatrocinio.EQUILIBRADO -> listOf(0.95 * v, 0.012 * v, 0.0, 1.0)
                PerfilPatrocinio.AMBICIOSO -> listOf(0.65 * v, 0.025 * v, 0.30 * v, 1.0)
            }
            Patrocinio(
                "$ano-${slot.name}-${perfil.name}", marca, slot, perfil,
                (base * fixo).toLong() / 1000 * 1000, (base * vitoria).toLong() / 1000 * 1000, (base * titulo).toLong() / 1000 * 1000,
                anos.toInt(), base,
            )
        }
    }

    fun assinar(slot: SlotPatrocinio, perfil: PerfilPatrocinio): Resposta {
        if (!g.podeGerir) return Resposta.NAO_PERMITIDO
        if (contrato(slot) != null) return Resposta.JA_TEM_PATROCINIO
        val p = propostas(slot).first { it.perfil == perfil }
        e.patrocinadores += Patrocinio(p.id, p.nome, p.slot, p.perfil, p.fixo, p.porVitoria, p.porTitulo, p.anos, p.base)
        c.mensagem("Patrocínio: ${p.nome}", "${p.nome} é o novo patrocinador (${slot.nome.lowercase()}) por ${p.anos} ${if (p.anos == 1) "época" else "épocas"}: ${Mercado.euros(p.fixo)} fixos por ano" +
            (if (p.porVitoria > 0) ", ${Mercado.euros(p.porVitoria)} por vitória na liga" else "") + (if (p.porTitulo > 0) " e ${Mercado.euros(p.porTitulo)} se fores campeão" else "") + ".")
        return Resposta.ACEITE
    }

    /** Rescindir paga 30% da receita fixa anual do contrato. */
    fun rescindir(slot: SlotPatrocinio): Resposta {
        val p = contrato(slot) ?: return Resposta.NAO_PERMITIDO
        if (!g.podeGerir) return Resposta.NAO_PERMITIDO
        val multa = p.fixo * 3 / 10
        if (multa > meu.orcamento) return Resposta.SEM_DINHEIRO
        meu.orcamento -= multa
        meu.contas.comissoes += multa
        e.patrocinadores.remove(p)
        c.mensagem("Patrocínio rescindido", "Rescindiste o contrato com ${p.nome}. Indemnização: ${Mercado.euros(multa)}.")
        return Resposta.ACEITE
    }

    // ------------------------------------------------------------ dívida

    fun divida(): Long = e.emprestimos.sumOf { it.restante }

    fun limiteCredito(): Long = ((receitaPrevista() * 0.4).toLong() - divida()).coerceAtLeast(0) / 10_000 * 10_000

    fun jurosAnuais(): Double = if (saude() == Saude.ESTAVEL) 0.06 else 0.09

    /** Pede dinheiro ao banco: paga-se em 24 meses com juros. */
    fun pedirCredito(valor: Long): Resposta {
        if (!g.podeGerir) return Resposta.NAO_PERMITIDO
        if (embargoAtivo()) return Resposta.EMBARGO
        if (valor < 100_000 || valor > limiteCredito()) return Resposta.SEM_CREDITO
        meu.orcamento += valor
        e.emprestimos += EmprestimoBanco(valor, 24)
        c.mensagem("Crédito bancário", "O banco emprestou ${Mercado.euros(valor)} ao clube: paga-se em 24 meses, com juros de ${(jurosAnuais() * 100).toInt()}% ao ano.")
        return Resposta.ACEITE
    }

    /** Paga antecipadamente uma parte da dívida (do empréstimo mais antigo para o mais recente). */
    fun amortizar(valor: Long): Resposta {
        if (!g.podeGerir) return Resposta.NAO_PERMITIDO
        val v = minOf(valor, divida(), maxOf(0L, meu.orcamento))
        if (v <= 0) return Resposta.SEM_DINHEIRO
        var resto = v
        for (em in e.emprestimos) {
            val parte = minOf(em.restante, resto)
            em.restante -= parte
            resto -= parte
            if (resto <= 0) break
        }
        e.emprestimos.removeAll { it.restante <= 0 }
        meu.orcamento -= v
        return Resposta.ACEITE
    }

    /** Todos os meses: juros e prestação dos créditos. */
    fun processarMes() {
        val taxa = jurosAnuais() / 12
        for (em in e.emprestimos) {
            val juros = (em.restante * taxa).toLong()
            val amortizacao = if (em.meses <= 1) em.restante else em.restante / em.meses
            meu.orcamento -= juros + amortizacao
            meu.contas.juros += juros
            em.restante -= amortizacao
            em.meses--
        }
        e.emprestimos.removeAll { it.restante <= 0 || it.meses <= 0 }
    }

    // ------------------------------------------------------------ saúde financeira e regras

    /** Salários e equipa técnica a dividir pela receita da última época (ou da prevista). */
    fun racioSalarial(): Double {
        val receita = e.receitas.lastOrNull()?.takeIf { it > 0 } ?: receitaPrevista()
        return massaSalarial().toDouble() / receita.coerceAtLeast(1)
    }

    fun saude(): Saude {
        val receita = receitaPrevista().coerceAtLeast(1)
        return when {
            embargoAtivo() || (meu.orcamento < 0 && divida() > receita * 0.3) -> Saude.CRITICA
            meu.orcamento < 0 || racioSalarial() > 0.9 || divida() > receita * 0.25 -> Saude.ATENCAO
            else -> Saude.ESTAVEL
        }
    }

    /** As regras de equilíbrio financeiro, em frases (para o ecrã). */
    fun regras(): List<String> = listOf(
        "Os salários (plantel e equipa técnica) não devem passar de 90% da receita.",
        "Se as perdas das últimas três épocas passarem de 25% da receita anual e a caixa estiver curta, o clube fica impedido de comprar jogadores durante uma época.",
        "O crédito bancário tem de caber em 40% da receita prevista e não se pede durante um embargo.",
        "Caixa negativa faz a direção perder confiança em ti a cada jornada.",
    )

    // ------------------------------------------------------------ durante a época

    /** Depois de um jogo do clube: patrocínios (a diferença para o normal), bilhetes e prémios de vitória. */
    fun aposJogo(jogo: JogoDoClube) {
        val liga = jogo.eLiga
        if (liga) {
            val jornadas = c.estadoLiga.totalJornadas.coerceAtLeast(1)
            val custoStaff = g.equipaTecnica.custoAnual() / jornadas
            if (custoStaff > 0) {
                meu.orcamento -= custoStaff
                meu.contas.equipaTecnica += custoStaff
            }
            val delta = deltaPatrocinios() / jornadas
            if (delta != 0L) {
                meu.orcamento += delta
                meu.contas.patrocinios += delta
            }
            if (jogo.golosMarcados > jogo.golosSofridos) {
                val premio = e.patrocinadores.sumOf { it.porVitoria }
                if (premio > 0) { meu.orcamento += premio; meu.contas.patrocinios += premio }
            }
        }
        if ((liga || jogo.tipo == "taca") && jogo.emCasa && !jogo.neutro && e.precoBilhete != 1.0) {
            val ambiente = if (liga) c.gestor.fatorAmbiente else 1.0
            val (novo, normal) = bilheteira(jogo.intensidadeDerby > 0, ambiente)
            val diferenca = novo - normal
            meu.orcamento += diferenca
            meu.contas.bilheteira += diferenca
            // Preços muito altos custam apoio; preços baixos agradam aos adeptos.
            if (e.precoBilhete >= 1.3) c.info.confiancaAdeptos = (c.info.confiancaAdeptos - 1).coerceAtLeast(0)
            if (e.precoBilhete <= 0.85) c.info.confiancaAdeptos = (c.info.confiancaAdeptos + 1).coerceAtMost(100)
        }
    }

    // ------------------------------------------------------------ fim de época

    /** Bónus de título dos patrocinadores, regras de equilíbrio financeiro e avisos. */
    fun fimDeEpoca() {
        val liga = c.liga
        val campeao = c.posicao() == 1
        val premioTitulo = if (campeao) e.patrocinadores.sumOf { it.porTitulo } else 0L
        if (premioTitulo > 0) {
            meu.orcamento += premioTitulo
            meu.contas.patrocinios += premioTitulo
            c.mensagem("Patrocinadores: bónus de título", "Os patrocinadores pagaram ${Mercado.euros(premioTitulo)} pelo título de ${liga.nome}.")
        }
        val receitas = meu.contas.receitas()
        val resultado = receitas - meu.contas.despesas()
        e.receitas += receitas
        e.resultados += resultado
        while (e.resultados.size > 3) e.resultados.removeAt(0)
        while (e.receitas.size > 3) e.receitas.removeAt(0)
        val acumulado = e.resultados.sum()
        val limite = -(receitas * 25 / 100)
        val linhas = mutableListOf("Resultado da época: ${Mercado.euros(resultado)}.")
        // Quem tem reservas grandes aguenta perdas: só há embargo se a caixa já está curta (ou as perdas são enormes).
        val reservasCurtas = meu.orcamento < receitas / 2
        if (e.resultados.size >= 2 && acumulado < limite && (reservasCurtas || acumulado < limite * 2)) {
            e.embargoAteAno = ano + 1
            linhas += "As perdas das últimas ${e.resultados.size} épocas (${Mercado.euros(acumulado)}) passam o limite: o clube fica impedido de contratar até ao fim da próxima época."
        } else if (resultado < 0) {
            linhas += "O clube perdeu dinheiro: cuidado com as perdas acumuladas (limite: ${Mercado.euros(limite)})."
        }
        if (massaSalarial() > receitas * 0.9) linhas += "Os salários passam de 90% da receita: a direção pede-te que os reduzas."
        if (meu.orcamento < 0) {
            linhas += "O clube está com a caixa negativa (${Mercado.euros(meu.orcamento)})."
            c.info.confiancaDirecao = (c.info.confiancaDirecao - 3).coerceAtLeast(0)
        }
        c.mensagem("Balanço financeiro", linhas.joinToString(" "))
    }

    /** No arranque da época nova: os contratos de patrocínio que acabaram saem. */
    fun novaEpoca() {
        val acabados = e.patrocinadores.filter { it.anosRestantes <= 1 }
        for (p in e.patrocinadores) p.anosRestantes--
        e.patrocinadores.removeAll { it.anosRestantes <= 0 }
        if (acabados.isNotEmpty()) c.mensagem("Patrocínios: contratos terminados", acabados.joinToString { it.nome } + ": escolhe novos patrocinadores em Mais › Gestão › Orçamento.")
    }

    companion object {
        private val MARCAS = mapOf(
            SlotPatrocinio.CAMISOLA to listOf("Banco Atlântico", "Seguros Lusitana", "Telecom Nova", "Energias do Sul", "Cervejas do Norte", "Construções Ibéria", "Aviação Horizonte", "Grupo Mondego"),
            SlotPatrocinio.MATERIAL to listOf("Sportline", "Kicko", "Velocita", "Atletika", "Campo Aberto", "Prisma Sport", "Ronda Desportos", "Corrida Viva"),
            SlotPatrocinio.ESTADIO to listOf("Águas da Serra", "Autos Meridional", "Farmácias Vida", "Supermercados Boa Mesa", "Cimentos do Tejo", "Móveis Alvorada", "Viagens Rota", "Digital Faro"),
        )
    }
}
