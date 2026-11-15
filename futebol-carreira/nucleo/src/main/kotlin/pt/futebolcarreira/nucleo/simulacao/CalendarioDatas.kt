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
        val paragens = setOf(
            LocalDate.of(ano, 9, 3).with(sabado), LocalDate.of(ano, 10, 8).with(sabado),
            LocalDate.of(ano, 11, 12).with(sabado), LocalDate.of(ano + 1, 3, 23).with(sabado),
        )
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

    /** O mercado de transferências está aberto no verão (julho e agosto) e em janeiro. */
    fun mercadoAberto(data: LocalDate): Boolean = data.monthValue == 7 || data.monthValue == 8 || data.monthValue == 1

    private val MESES = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    private val DIAS = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")

    /** Data por extenso, curta: "sáb, 8 ago 2026". */
    fun texto(data: LocalDate): String =
        "${DIAS[data.dayOfWeek.value - 1]}, ${data.dayOfMonth} ${MESES[data.monthValue - 1]} ${data.year}"

    /** Data sem o ano: "8 ago". */
    fun textoCurto(data: LocalDate): String = "${data.dayOfMonth} ${MESES[data.monthValue - 1]}"
}
