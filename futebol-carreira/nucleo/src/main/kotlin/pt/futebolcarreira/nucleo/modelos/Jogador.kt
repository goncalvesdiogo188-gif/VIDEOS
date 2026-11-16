package pt.futebolcarreira.nucleo.modelos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.pow
import kotlin.math.roundToInt

/** O que um jogador fez na época em curso (não é gravado no JSON da base de dados). */
class EstatisticasEpoca {
    var jogos = 0
    var golos = 0
    var assistencias = 0
    var amarelos = 0
    var vermelhos = 0
    var somaNotas = 0.0

    /** Nota média nos jogos em que participou (0 se ainda não jogou). */
    fun notaMedia(): Double = if (jogos == 0) 0.0 else somaNotas / jogos

    fun limpar() {
        jogos = 0; golos = 0; assistencias = 0; amarelos = 0; vermelhos = 0; somaNotas = 0.0
    }
}

/**
 * Um jogador de futebol: identidade, posição e atributos.
 * Os campos têm os mesmos nomes que no ficheiro JSON.
 */
@Serializable
class Jogador(
    var id: Int = 0,
    var nome: String = "Sem Nome",
    var nacionalidade: String = "",
    var idade: Int = 20,
    var posicao: String = "MC",
    /** "direito", "esquerdo" ou "ambos". */
    var pe: String = "direito",
    /** Id da equipa ("" = sem clube). */
    var equipa: String = "",
    /** Geral máximo que pode atingir. */
    var potencial: Int = 0,
    /** Nome do atributo -> valor (1 a 99). */
    val atributos: MutableMap<String, Int> = mutableMapOf(),
    /** Salário por ano, em euros (0 = ainda por definir). */
    var salario: Int = 0,
    /** Ano em que o contrato acaba (no verão desse ano). */
    @SerialName("contrato_ate") var contratoAte: Int = 0,
    /** Id do clube dono do jogador, se estiver emprestado ("" = não está emprestado). */
    @SerialName("emprestado_por") var emprestadoPor: String = "",
    /** true = está na academia do clube (não conta para o plantel nem joga). */
    var academia: Boolean = false,
    /** Geral no fim de cada época já jogada, por idade que tinha nessa época (serve para o gráfico de evolução). */
    @SerialName("geral_por_idade") val geralPorIdade: MutableMap<Int, Int> = mutableMapOf(),
) {
    /** Estatísticas da época em curso. */
    @Transient
    val epoca = EstatisticasEpoca()

    /** Jogos, golos, assistências e notas nas taças e supertaças (os cartões contam sempre em "epoca", por causa dos castigos). */
    @Transient
    val epocaTaca = EstatisticasEpoca()

    /** Jornadas que ainda falta para recuperar de lesão (0 = apto). */
    @Transient
    var lesao = 0

    /** Jogos de castigo que ainda falta cumprir (0 = pode jogar). */
    @Transient
    var suspensao = 0

    /** Forma (0 a 100; 50 = normal): sobe com boas exibições e desce com as más ou com a falta de jogos. */
    @Transient
    var forma = 50

    /** Moral (0 a 100; 50 = normal): sobe com as vitórias, desce com as derrotas e com a falta de minutos. */
    @Transient
    var moral = 50

    /** Jogos que tinha quando foi feita a última conta do treino (para saber se tem jogado). */
    @Transient
    var jogosNoUltimoTreino = 0

    /** Pontos de "geral" que a forma e a moral dão ou tiram num jogo (cerca de -4 a +4). */
    fun bonusForma(): Double = (forma - 50) / 20.0 + (moral - 50) / 40.0

    /** Pode ser convocado para o próximo jogo? */
    val disponivel get() = lesao == 0 && suspensao == 0

    fun atributo(nome: String): Int = atributos[nome] ?: 1

    /** Valor global do jogador na sua posição (1 a 99). */
    fun geral(): Int {
        val pesos = PESOS[posicao] ?: PESOS.getValue("MC")
        val soma = pesos.entries.sumOf { (atributo, peso) -> (atributos[atributo] ?: 1) * peso }
        return soma.roundToInt().coerceIn(1, 99)
    }

    /** Soma "delta" aos atributos que contam para a posição (usado na evolução). */
    fun ajustarAtributos(delta: Int) {
        val pesos = PESOS[posicao] ?: PESOS.getValue("MC")
        for (atributo in pesos.keys) {
            atributos[atributo] = ((atributos[atributo] ?: 1) + delta).coerceIn(1, 99)
        }
    }

    /** Valor de mercado estimado, em euros (provisório até à fase das transferências). */
    fun valorMercado(): Int {
        val base = 1.18.pow(geral() - 50) * 250_000.0
        val fatorIdade = when {
            idade <= 23 -> 1.0 + (potencial - geral()) * 0.04
            idade >= 30 -> maxOf(0.15, 1.0 - (idade - 29) * 0.15)
            else -> 1.0
        }
        return (base * fatorIdade / 10_000.0).roundToInt() * 10_000
    }

    /** Salário anual que um jogador com esta qualidade costuma ganhar. */
    fun salarioJusto(): Int = (1.17.pow(geral() - 50) * 60.0).roundToInt().coerceAtLeast(12) * 1000

    /** Cláusula de rescisão: pagando este valor, o clube não pode recusar a venda. */
    fun clausula(): Long = valorMercado() * 3L

    /** Corrige valores em falta ou inválidos depois de ler do JSON. */
    fun normalizar() {
        if (posicao !in POSICOES) posicao = "MC"
        for (atributo in ATRIBUTOS) {
            atributos[atributo] = (atributos[atributo] ?: 30).coerceIn(1, 99)
        }
        potencial = (if (potencial <= 0) geral() else potencial).coerceIn(geral(), 99)
    }

    companion object {
        /**
         * Posições (siglas portuguesas): GR guarda-redes, DD/DE defesas laterais, DC defesa central,
         * MDC médio defensivo, MC médio centro, MOC médio ofensivo, ED/EE extremos, PL ponta de lança.
         */
        val POSICOES = listOf("GR", "DD", "DC", "DE", "MDC", "MC", "MOC", "ED", "EE", "PL")

        /** Atributos de cada jogador (valores de 1 a 99). */
        val ATRIBUTOS = listOf("ritmo", "remate", "passe", "drible", "defesa", "fisico", "guarda_redes")

        private val LATERAL = mapOf("defesa" to 0.35, "ritmo" to 0.25, "passe" to 0.15, "fisico" to 0.15, "drible" to 0.10)
        private val EXTREMO = mapOf("ritmo" to 0.30, "drible" to 0.30, "remate" to 0.20, "passe" to 0.15, "fisico" to 0.05)

        /** Peso de cada atributo no "geral", conforme a posição (cada linha soma 1). */
        val PESOS: Map<String, Map<String, Double>> = mapOf(
            "GR" to mapOf("guarda_redes" to 0.80, "fisico" to 0.12, "passe" to 0.08),
            "DC" to mapOf("defesa" to 0.55, "fisico" to 0.25, "ritmo" to 0.10, "passe" to 0.10),
            "DD" to LATERAL,
            "DE" to LATERAL,
            "MDC" to mapOf("defesa" to 0.35, "passe" to 0.30, "fisico" to 0.20, "drible" to 0.10, "ritmo" to 0.05),
            "MC" to mapOf("passe" to 0.40, "drible" to 0.20, "defesa" to 0.15, "fisico" to 0.10, "remate" to 0.10, "ritmo" to 0.05),
            "MOC" to mapOf("passe" to 0.30, "drible" to 0.30, "remate" to 0.20, "ritmo" to 0.15, "fisico" to 0.05),
            "ED" to EXTREMO,
            "EE" to EXTREMO,
            "PL" to mapOf("remate" to 0.45, "ritmo" to 0.20, "drible" to 0.15, "fisico" to 0.15, "passe" to 0.05),
        )
    }
}
