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

    /** Todos os jogos registados: casa, fora, golos da casa, golos de fora (para o confronto direto). */
    private val jogos = mutableListOf<Resultado>()

    /** Regista o resultado de um jogo. */
    fun registar(casa: String, fora: String, golosCasa: Int, golosFora: Int) {
        somar(linhas.getValue(casa), golosCasa, golosFora)
        somar(linhas.getValue(fora), golosFora, golosCasa)
        jogos += Resultado(casa, fora, golosCasa, golosFora)
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
     * Linhas ordenadas por pontos. Entre equipas com os mesmos pontos decide o
     * confronto direto (pontos e diferença de golos nos jogos entre elas); depois
     * a diferença de golos geral, os golos marcados e, por fim, o id.
     */
    fun ordenada(): List<LinhaTabela> {
        val geral = compareByDescending<LinhaTabela> { it.diferenca }.thenByDescending { it.gm }.thenBy { it.id }
        return linhas.values.groupBy { it.pts }.toSortedMap(reverseOrder()).values.flatMap { empatadas ->
            if (empatadas.size == 1) return@flatMap empatadas
            // Mini-campeonato só com os jogos entre as equipas empatadas.
            val ids = empatadas.map { it.id }.toSet()
            val pontos = HashMap<String, Int>()
            val diferenca = HashMap<String, Int>()
            for (jogo in jogos) {
                if (jogo.casa !in ids || jogo.fora !in ids) continue
                val saldo = jogo.golosCasa - jogo.golosFora
                diferenca.merge(jogo.casa, saldo, Int::plus)
                diferenca.merge(jogo.fora, -saldo, Int::plus)
                when {
                    saldo > 0 -> pontos.merge(jogo.casa, pontosVitoria, Int::plus)
                    saldo < 0 -> pontos.merge(jogo.fora, pontosVitoria, Int::plus)
                    else -> {
                        pontos.merge(jogo.casa, pontosEmpate, Int::plus)
                        pontos.merge(jogo.fora, pontosEmpate, Int::plus)
                    }
                }
            }
            empatadas.sortedWith(
                compareByDescending<LinhaTabela> { pontos[it.id] ?: 0 }.thenByDescending { diferenca[it.id] ?: 0 }.then(geral)
            )
        }
    }
}
