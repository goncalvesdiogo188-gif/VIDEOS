package pt.futebolcarreira.nucleo.simulacao

/** Linha da tabela: jogos, vitórias, empates, derrotas, golos marcados/sofridos e pontos. */
class LinhaTabela(val id: String) {
    var j = 0
    var v = 0
    var e = 0
    var d = 0
    var gm = 0
    var gs = 0
    var pts = 0
    val diferenca get() = gm - gs
}

/** Tabela classificativa de uma liga. */
class TabelaClassificativa(idsEquipas: List<String>, private val pontosVitoria: Int = 3, private val pontosEmpate: Int = 1) {
    val linhas: Map<String, LinhaTabela> = idsEquipas.associateWith { LinhaTabela(it) }

    /** Regista o resultado de um jogo. */
    fun registar(casa: String, fora: String, golosCasa: Int, golosFora: Int) {
        somar(linhas.getValue(casa), golosCasa, golosFora)
        somar(linhas.getValue(fora), golosFora, golosCasa)
    }

    private fun somar(linha: LinhaTabela, marcados: Int, sofridos: Int) {
        linha.j++
        linha.gm += marcados
        linha.gs += sofridos
        when {
            marcados > sofridos -> { linha.v++; linha.pts += pontosVitoria }
            marcados == sofridos -> { linha.e++; linha.pts += pontosEmpate }
            else -> linha.d++
        }
    }

    /**
     * Linhas ordenadas: pontos, diferença de golos, golos marcados e, por fim, o id.
     * (O desempate por confronto direto fica para uma fase posterior.)
     */
    fun ordenada(): List<LinhaTabela> = linhas.values.sortedWith(
        compareByDescending<LinhaTabela> { it.pts }.thenByDescending { it.diferenca }.thenByDescending { it.gm }.thenBy { it.id }
    )
}
