package pt.futebolcarreira.nucleo.simulacao

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Datas da época: quando começa, em que dias se jogam as jornadas
 * e quando está aberto o mercado de transferências.
 */
object CalendarioDatas {
    /** Primeiro dia da época que começa em "ano" (1 de julho). */
    fun inicioDaEpoca(ano: Int): LocalDate = LocalDate.of(ano, 7, 1)

    /**
     * Datas das jornadas de uma liga. Joga-se ao sábado, de agosto a maio, com
     * paragens para as seleções (setembro, outubro, novembro e março).
     * Ligas com menos jornadas têm pausa de Natal e acabam mais cedo;
     * ligas com jornadas a mais jogam também algumas quartas-feiras.
     */
    fun jornadas(ano: Int, quantas: Int): List<LocalDate> {
        val sabado = TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)
        val primeiro = LocalDate.of(ano, 8, 8).with(sabado)
        val ultimo = LocalDate.of(ano + 1, 5, 24).with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
        val paragens = paragensDasSelecoes(ano)
        val datas = generateSequence(primeiro) { it.plusWeeks(1) }.takeWhile { it <= ultimo }.filter { it !in paragens }.toMutableList()

        // Jornadas a menos: primeiro tira-se o Natal e o Ano Novo, depois encurta-se a época.
        val natal = LocalDate.of(ano, 12, 22)..LocalDate.of(ano + 1, 1, 3)
        while (datas.size > quantas) {
            val pausa = datas.indexOfFirst { it in natal }
            datas.removeAt(if (pausa >= 0) pausa else datas.lastIndex)
        }
        // Jornadas a mais: acrescentam-se quartas-feiras, espalhadas pela época.
        val sabados = datas.toList()
        val extra = quantas - sabados.size
        for (k in 0 until minOf(extra, sabados.size)) {
            datas += sabados[((k + 0.5) * sabados.size / extra).toInt().coerceIn(0, sabados.lastIndex)].plusDays(4)
        }
        datas.sort()
        // Caso extremo (liga enorme): continua depois do fim, duas vezes por semana.
        while (datas.size < quantas) datas += datas.last().plusDays(if (datas.last().dayOfWeek == DayOfWeek.SATURDAY) 4 else 3)
        return datas
    }

    /** Sábados sem jornadas por causa dos jogos das seleções. */
    private fun paragensDasSelecoes(ano: Int): Set<LocalDate> {
        val sabado = TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)
        return setOf(
            LocalDate.of(ano, 9, 3).with(sabado), LocalDate.of(ano, 10, 8).with(sabado),
            LocalDate.of(ano, 11, 12).with(sabado), LocalDate.of(ano + 1, 3, 23).with(sabado),
        )
    }

    /** Dia da final da taça: o sábado a seguir à última jornada das ligas (fim de maio). */
    fun finalDaTaca(ano: Int): LocalDate =
        LocalDate.of(ano + 1, 5, 24).with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY)).plusWeeks(1)

    /** Dia da supertaça: o primeiro sábado de agosto, antes de a liga começar (a 8 de agosto ou depois). */
    fun supertaca(ano: Int): LocalDate = LocalDate.of(ano, 8, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))

    /**
     * Datas de uma taça com "rondas" rondas: cada elemento tem a data de cada mão (uma, ou duas se "idaEVolta").
     * As rondas jogam-se às quartas-feiras, espalhadas de finais de setembro a abril, e a última (a final)
     * num só jogo ao sábado, depois da liga. "ocupadas" são os dias em que os clubes já têm jogos de liga
     * (e as semanas das seleções também se evitam), para nunca haver dois jogos no mesmo dia.
     */
    fun datasTaca(ano: Int, rondas: Int, idaEVolta: Boolean, ocupadas: Set<LocalDate>): List<List<LocalDate>> {
        if (rondas <= 0) return emptyList()
        val quarta = TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY)
        val bloqueadas = HashSet(ocupadas)
        for (sabado in paragensDasSelecoes(ano)) { bloqueadas += sabado.minusDays(3); bloqueadas += sabado.plusDays(4) }
        val inicio = LocalDate.of(ano, 9, 23)
        val dias = java.time.temporal.ChronoUnit.DAYS.between(inicio, LocalDate.of(ano + 1, 4, 20))
        val normais = rondas - 1
        val resultado = mutableListOf<List<LocalDate>>()
        var anterior: LocalDate? = null
        for (i in 0 until normais) {
            val alvo = inicio.plusDays(if (normais > 1) dias * i / (normais - 1) else 0).with(quarta)
            var primeira = alvo
            while (primeira in bloqueadas || (anterior != null && primeira < anterior.plusDays(14))) primeira = primeira.plusWeeks(1)
            val datas = mutableListOf(primeira)
            if (idaEVolta) {
                var volta = primeira.plusWeeks(2)
                while (volta in bloqueadas) volta = volta.plusWeeks(1)
                datas += volta
            }
            resultado += datas
            anterior = datas.last()
        }
        var final = finalDaTaca(ano)
        while (final in ocupadas) final = final.plusWeeks(1)
        resultado += listOf(final)
        return resultado
    }

    /** O mercado de transferências está aberto no verão (julho e agosto) e em janeiro. */
    fun mercadoAberto(data: LocalDate): Boolean = data.monthValue == 7 || data.monthValue == 8 || data.monthValue == 1

    private val MESES = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    private val MESES_COMPLETOS = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")

    /** Nome do mês (1 = janeiro). */
    fun mes(numero: Int): String = MESES_COMPLETOS[(numero - 1).coerceIn(0, 11)]

    private val DIAS = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")

    /** Data por extenso, curta: "sáb, 8 ago 2026". */
    fun texto(data: LocalDate): String =
        "${DIAS[data.dayOfWeek.value - 1]}, ${data.dayOfMonth} ${MESES[data.monthValue - 1]} ${data.year}"

    /** Data sem o ano: "8 ago". */
    fun textoCurto(data: LocalDate): String = "${data.dayOfMonth} ${MESES[data.monthValue - 1]}"
}
