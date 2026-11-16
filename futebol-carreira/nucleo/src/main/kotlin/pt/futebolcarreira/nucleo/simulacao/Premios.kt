package pt.futebolcarreira.nucleo.simulacao

import kotlinx.serialization.Serializable
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.Jogador

/** Um jogador premiado numa época: uma "fotografia" dos números dele (o jogador pode deixar o jogo mais tarde). */
@Serializable
class Premiado(
    val jogadorId: Int,
    val nome: String,
    /** Sigla do clube. */
    val clube: String,
    val posicao: String,
    val idade: Int,
    val jogos: Int,
    val golos: Int,
    val assistencias: Int,
    val nota: Double,
)

/** Prémios individuais de uma liga numa época. */
@Serializable
class PremiosLiga(
    val melhorJogador: Premiado? = null,
    val melhorMarcador: Premiado? = null,
    /** Melhor jogador com 21 anos ou menos. */
    val melhorJovem: Premiado? = null,
    /** Onze da época (4-3-3). */
    val onze: List<Premiado> = emptyList(),
) {
    /** Todos os ids premiados (para ver se o jogador do utilizador ganhou alguma coisa). */
    fun ids(): Set<Int> = (listOfNotNull(melhorJogador, melhorMarcador, melhorJovem) + onze).map { it.jogadorId }.toSet()
}

/**
 * Prémios individuais no fim da época, para todas as ligas: melhor jogador (nota média), melhor marcador,
 * melhor jovem (até 21 anos), onze da época e, entre todas as ligas, a "Bola de Ouro".
 * Só contam os números da liga (jogos, golos, assistências, nota média) e só entra quem jogou o suficiente.
 */
object Premios {
    const val IDADE_JOVEM = 21

    /** Resultado dos prémios de uma época. */
    class Resultado(val ligas: Map<String, PremiosLiga>, val bolaDeOuro: Premiado?, val podio: List<Premiado>)

    fun fotografia(bd: BaseDados, j: Jogador) = Premiado(
        j.id, j.nome, bd.equipas[j.equipa]?.abreviatura ?: "", j.posicao, j.idade,
        j.epoca.jogos, j.epoca.golos, j.epoca.assistencias, j.epoca.notaMedia(),
    )

    /** Calcula os prémios com as estatísticas dos jogadores (a época que acabou de terminar). */
    fun calcular(
        bd: BaseDados, estado: Map<String, EstadoLiga>, resumos: Map<String, ResumoLiga>, europeias: Map<String, ResumoTaca>,
    ): Resultado {
        val ligas = LinkedHashMap<String, PremiosLiga>()
        val candidatosOuro = mutableListOf<Pair<Jogador, Double>>()
        for ((ligaId, e) in estado) {
            val liga = bd.competicoes[ligaId] ?: continue
            val jogadores = bd.equipasDaLiga(ligaId).flatMap { it.plantel }.filter { it.epoca.jogos > 0 }
            if (jogadores.isEmpty()) continue
            val total = e.totalJornadas
            val minimo = maxOf(3, total / 2)
            val elegiveis = jogadores.filter { it.epoca.jogos >= minimo }
            val ordemNota = compareByDescending<Jogador> { it.epoca.notaMedia() }
                .thenByDescending { it.epoca.golos + it.epoca.assistencias }.thenByDescending { it.epoca.jogos }.thenBy { it.id }
            val melhor = elegiveis.sortedWith(ordemNota).firstOrNull()
            val marcador = jogadores.filter { it.epoca.golos > 0 }
                .sortedWith(compareByDescending<Jogador> { it.epoca.golos }.thenByDescending { it.epoca.assistencias }.thenBy { it.epoca.jogos }.thenBy { it.id })
                .firstOrNull()
            val jovem = jogadores.filter { it.idade <= IDADE_JOVEM && it.epoca.jogos >= maxOf(3, total * 3 / 10) }
                .sortedWith(ordemNota).firstOrNull()
            ligas[ligaId] = PremiosLiga(
                melhor?.let { fotografia(bd, it) }, marcador?.let { fotografia(bd, it) }, jovem?.let { fotografia(bd, it) },
                onzeDaEpoca(bd, elegiveis, ordemNota),
            )
            // Bola de Ouro: nota, golos e assistências, o peso da liga e o sucesso do clube.
            val campeao = resumos[ligaId]?.campeao
            for (j in jogadores.filter { it.epoca.jogos >= maxOf(3, total * 6 / 10) }) {
                val clube = bd.equipas[j.equipa] ?: continue
                var pontos = (j.epoca.notaMedia() - 6.0) * 10.0 + j.epoca.golos * 0.45 + j.epoca.assistencias * 0.3
                pontos += if (liga.nivel <= 1) 4.0 else 0.0
                if (clube.id == campeao) pontos += 3.0
                if (europeias[TipoEuropa.CAMPEOES.id]?.vencedor == clube.id) pontos += 7.0
                if (europeias[TipoEuropa.EUROPA.id]?.vencedor == clube.id) pontos += 3.0
                pontos += clube.reputacao * 0.04 + j.epoca.jogos.toDouble() / total * 2.0
                candidatosOuro += j to pontos
            }
        }
        val ordenados = candidatosOuro.sortedWith(compareByDescending<Pair<Jogador, Double>> { it.second }.thenBy { it.first.id })
        val podio = ordenados.take(3).map { fotografia(bd, it.first) }
        return Resultado(ligas, podio.firstOrNull(), podio)
    }

    /** O melhor de cada lugar de um 4-3-3, por nota média (quem não chega a um lugar, tapa-o um jogador de posição parecida). */
    private fun onzeDaEpoca(bd: BaseDados, elegiveis: List<Jogador>, ordem: Comparator<Jogador>): List<Premiado> {
        val usados = HashSet<Int>()
        val onze = mutableListOf<Premiado>()
        for (lugar in Formacao.F433.lugares) {
            val parecidas = Equipa.POSICOES_PARECIDAS.getValue(lugar)
            val escolhido = elegiveis.filter { it.id !in usados && it.posicao == lugar }.sortedWith(ordem).firstOrNull()
                ?: elegiveis.filter { it.id !in usados && it.posicao in parecidas }.sortedWith(ordem).firstOrNull()
                ?: continue
            usados += escolhido.id
            onze += fotografia(bd, escolhido).let { Premiado(it.jogadorId, it.nome, it.clube, lugar, it.idade, it.jogos, it.golos, it.assistencias, it.nota) }
        }
        return onze
    }
}
