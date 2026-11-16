package pt.futebolcarreira.nucleo.modelos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Um lugar do onze inicial: quem joga lá e que nota tem nesse lugar. */
class LugarOnze(val lugar: String, val jogador: Jogador?, val nota: Int)

/** Força de uma equipa (escala 1 a 99). */
class Forca(val ataque: Double, val defesa: Double, val geral: Double)

/** Receitas e despesas de um clube numa época, em euros. */
@Serializable
class ContasEpoca {
    var bilheteira = 0L
    var patrocinios = 0L
    var premios = 0L
    var vendas = 0L
    var salarios = 0L
    var compras = 0L
    var obras = 0L
    var olheiros = 0L

    fun receitas() = bilheteira + patrocinios + premios + vendas
    fun despesas() = salarios + compras + obras + olheiros
}

/** Um clube: dados gerais e plantel. */
@Serializable
class Equipa(
    var id: String = "",
    var nome: String = "",
    var abreviatura: String = "",
    var pais: String = "",
    /** Id da liga onde joga esta época. */
    var liga: String = "",
    var cidade: String = "",
    var estadio: String = "",
    var capacidade: Int = 5000,
    /** 1 a 99. */
    var reputacao: Int = 50,
    var orcamento: Long = 1_000_000,
    var cores: List<String> = listOf("#ffffff", "#000000"),
    /** Instalações, de 1 a 5 (0 = por definir: fica de acordo com a reputação). */
    @SerialName("nivel_treino") var nivelTreino: Int = 0,
    @SerialName("nivel_academia") var nivelAcademia: Int = 0,
    @SerialName("nivel_medico") var nivelMedico: Int = 0,
) {
    @Transient
    val plantel: MutableList<Jogador> = mutableListOf()

    /** Jovens da academia: treinam, mas não jogam nem contam para o plantel. */
    @Transient
    val academia: MutableList<Jogador> = mutableListOf()

    /** O que se treina mais e com que intensidade. */
    @Transient
    var focoTreino = FocoTreino.EQUILIBRADO

    @Transient
    var intensidade = Intensidade.NORMAL

    /** Dá às instalações por definir o nível habitual de um clube com esta reputação. */
    fun normalizar() {
        val habitual = ((reputacao - 20) / 15).coerceIn(1, MAX_NIVEL)
        if (nivelTreino !in 1..MAX_NIVEL) nivelTreino = habitual
        if (nivelAcademia !in 1..MAX_NIVEL) nivelAcademia = habitual
        if (nivelMedico !in 1..MAX_NIVEL) nivelMedico = habitual
    }

    /** Receitas e despesas da época em curso. */
    @Transient
    var contas = ContasEpoca()

    /** Soma dos salários anuais do plantel. */
    fun folhaSalarial(): Long = plantel.sumOf { it.salario.toLong() }

    /** Campeonatos ganhos durante o jogo. */
    @Transient
    var titulos: Int = 0

    /** Taças nacionais ganhas durante o jogo. */
    @Transient
    var tacas: Int = 0

    /** Supertaças ganhas durante o jogo. */
    @Transient
    var supertacas: Int = 0

    /** Ligas dos Campeões e Ligas Europa ganhas durante o jogo. */
    @Transient
    var europeias: Int = 0

    /** Formação usada nos jogos. */
    @Transient
    var formacao: Formacao = Formacao.F433
        set(valor) {
            // Outra formação tem outros lugares: o onze escolhido à mão deixa de servir.
            if (valor != field) onzeManual = null
            field = valor
            invalidarForca()
        }

    /**
     * Onze escolhido pelo treinador: o id do jogador de cada lugar da formação, pela mesma ordem.
     * null = o adjunto escolhe sempre os melhores.
     */
    @Transient
    var onzeManual: List<Int>? = null

    /** true = o treinador (computador) escolhe a formação que melhor serve o plantel. */
    @Transient
    var formacaoAutomatica = true

    /** Mentalidade fixa; null = o treinador decide jogo a jogo. */
    @Transient
    var mentalidade: Mentalidade? = null

    @Transient
    private var forcaGuardada: Forca? = null

    /** Deve ser chamado sempre que o plantel ou os atributos mudam. */
    fun invalidarForca() {
        forcaGuardada = null
    }

    /**
     * Escolhe os melhores jogadores para os 11 lugares da formação.
     * "candidatos" permite deixar de fora lesionados e castigados.
     */
    fun melhorOnze(formacao: Formacao = this.formacao, candidatos: List<Jogador> = plantel, paraJogo: Boolean = false): List<LugarOnze> {
        val usados = HashSet<Int>()
        return formacao.lugares.map { lugar ->
            val melhor = candidatos.filter { it.id !in usados }.maxByOrNull { if (paraJogo) pontuacaoDeEscolha(it, lugar) else notaNoLugar(it, lugar).toDouble() }
            if (melhor == null) {
                // Menos de 11 jogadores: o lugar fica muito fraco.
                LugarOnze(lugar, null, 20)
            } else {
                usados += melhor.id
                LugarOnze(lugar, melhor, notaNoLugar(melhor, lugar))
            }
        }
    }

    /**
     * O onze para o próximo jogo, só com jogadores aptos. Respeita as escolhas do
     * treinador; os lugares de quem não pode jogar são dados ao melhor que sobra.
     */
    fun onzeParaJogo(): List<LugarOnze> {
        val aptos = plantel.filter { it.disponivel }
        val manual = onzeManual ?: return melhorOnze(formacao, aptos, paraJogo = true)
        val lugares = formacao.lugares
        val escolhidos = arrayOfNulls<Jogador>(lugares.size)
        val usados = HashSet<Int>()
        for (i in lugares.indices) {
            val j = aptos.firstOrNull { it.id == manual.getOrNull(i) } ?: continue
            if (usados.add(j.id)) escolhidos[i] = j
        }
        for (i in lugares.indices) {
            if (escolhidos[i] != null) continue
            escolhidos[i] = aptos.filter { it.id !in usados }.maxByOrNull { pontuacaoDeEscolha(it, lugares[i]) }?.also { usados += it.id }
        }
        return lugares.mapIndexed { i, lugar ->
            val j = escolhidos[i]
            if (j == null) LugarOnze(lugar, null, 20) else LugarOnze(lugar, j, notaNoLugar(j, lugar))
        }
    }

    /**
     * Pontos de ajuda dados a certos jogadores na escolha do onze (id -> pontos), por exemplo ao jogador do
     * utilizador depois de pedir mais minutos. Não conta para a força da equipa nem para as notas.
     */
    @Transient
    val ajudaNaEscolha: MutableMap<Int, Double> = mutableMapOf()

    /** Como o treinador escolhe: a qualidade no lugar, mais um pouco a forma e a moral do momento. */
    private fun pontuacaoDeEscolha(j: Jogador, lugar: String): Double =
        notaNoLugar(j, lugar) + j.bonusForma() * PESO_FORMA_NA_ESCOLHA + (ajudaNaEscolha[j.id] ?: 0.0)

    /** O banco para o próximo jogo: um guarda-redes e os melhores jogadores de campo que sobram. */
    fun suplentesParaJogo(onze: List<LugarOnze>, quantos: Int = 7): List<Jogador> {
        val restantes = plantel.filter { j -> j.disponivel && onze.none { it.jogador === j } }.sortedByDescending { it.geral() }
        val banco = mutableListOf<Jogador>()
        restantes.firstOrNull { it.posicao == "GR" }?.let { banco += it }
        banco += restantes.filter { it.posicao != "GR" }.take(quantos - banco.size)
        return banco
    }

    /** O treinador põe um jogador num lugar do onze. Se ele já lá estava noutro lugar, os dois trocam. */
    fun definirTitular(indice: Int, jogador: Jogador) {
        val atual = onzeParaJogo().map { it.jogador?.id ?: 0 }.toMutableList()
        if (indice !in atual.indices) return
        val outro = atual.indexOf(jogador.id)
        if (outro >= 0) atual[outro] = atual[indice]
        atual[indice] = jogador.id
        onzeManual = atual
    }

    /** Força da equipa com todos os jogadores, na formação atual (inclui o bónus da formação). */
    fun forca(): Forca {
        forcaGuardada?.let { return it }
        return calcularForca(formacao).also { forcaGuardada = it }
    }

    private fun calcularForca(formacao: Formacao): Forca {
        var somaAtaque = 0.0
        var pesoAtaque = 0.0
        var somaDefesa = 0.0
        var pesoDefesa = 0.0
        var soma = 0.0
        for (entrada in melhorOnze(formacao)) {
            val pa = PESO_ATAQUE.getValue(entrada.lugar)
            val pd = PESO_DEFESA.getValue(entrada.lugar)
            somaAtaque += entrada.nota * pa
            pesoAtaque += pa
            somaDefesa += entrada.nota * pd
            pesoDefesa += pd
            soma += entrada.nota
        }
        return Forca(somaAtaque / pesoAtaque + formacao.ataque, somaDefesa / pesoDefesa + formacao.defesa, soma / formacao.lugares.size)
    }

    /**
     * Escolhe a formação que melhor aproveita o plantel (só se estiver em modo automático).
     * Entre formações quase iguais, cada clube tem a sua preferência, para haver variedade.
     */
    fun escolherFormacao() {
        if (!formacaoAutomatica) return
        val notas = Formacao.entries.associateWith { calcularForca(it).geral }
        val melhor = notas.values.max()
        val boas = notas.filterValues { it >= melhor - 0.7 }.keys.toList()
        formacao = boas[Math.floorMod(id.hashCode(), boas.size)]
    }

    companion object {
        const val MAX_NIVEL = 5

        /** Quanto a forma e a moral pesam na escolha do onze (em pontos de "geral" por ponto de bónus). */
        const val PESO_FORMA_NA_ESCOLHA = 0.6

        /** Nota de um jogador num certo lugar (penaliza quem joga fora de posição). */
        fun notaNoLugar(j: Jogador, lugar: String): Int = when {
            j.posicao == lugar -> j.geral()
            lugar == "GR" || j.posicao == "GR" -> maxOf(1, j.geral() - 40)
            j.posicao in POSICOES_PARECIDAS.getValue(lugar) -> maxOf(1, j.geral() - 5)
            else -> maxOf(1, j.geral() - 12)
        }

        /** Posições parecidas: um jogador pode tapar um buraco com pequena penalização. */
        val POSICOES_PARECIDAS = mapOf(
            "DD" to listOf("DE", "DC"), "DE" to listOf("DD", "DC"), "DC" to listOf("MDC", "DD", "DE"),
            "MDC" to listOf("MC", "DC"), "MC" to listOf("MOC", "MDC"), "MOC" to listOf("MC", "ED", "EE"),
            "ED" to listOf("EE", "MOC"), "EE" to listOf("ED", "MOC"), "PL" to listOf("ED", "EE", "MOC"),
            "GR" to emptyList(),
        )

        /** Quanto cada lugar do onze conta para o ataque e para a defesa. */
        val PESO_ATAQUE = mapOf("GR" to 0.0, "DD" to 0.5, "DE" to 0.5, "DC" to 0.0, "MDC" to 0.5, "MC" to 1.5, "MOC" to 2.0, "ED" to 2.0, "EE" to 2.0, "PL" to 3.0)
        val PESO_DEFESA = mapOf("GR" to 3.0, "DD" to 1.5, "DE" to 1.5, "DC" to 2.0, "MDC" to 1.5, "MC" to 0.5, "MOC" to 0.0, "ED" to 0.0, "EE" to 0.0, "PL" to 0.0)
    }
}
