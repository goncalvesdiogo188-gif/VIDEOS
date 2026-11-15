package pt.futebolcarreira.nucleo.simulacao

import kotlinx.serialization.Serializable
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.modelos.ListaNomes
import java.time.LocalDate

/** Uma seleção nacional. */
@Serializable
class Nacao(
    /** Código de 3 letras. Nas seleções da base de dados é a nacionalidade dos jogadores (POR, ESP...). */
    val id: String,
    val nome: String,
    val abreviatura: String = id,
    /** EUR, SUL (América do Sul), NORTE (América do Norte e Central), AFR ou ASI. */
    val continente: String = "EUR",
    /** 1 a 99, serve para o ranking inicial e para o nível dos jogadores fictícios. */
    val reputacao: Int = 50,
    /** Grupo de nomes usado para gerar os jogadores (só nas seleções fictícias). */
    val nomes: String = "",
    /** true = convoca jogadores reais da base de dados; false = plantel fictício guardado só neste módulo. */
    var baseDados: Boolean = false,
) {
    val europeia get() = continente == "EUR"
}

/** Conteúdo de selecoes.json: as seleções e listas de nomes extra para as seleções fictícias. */
@Serializable
class CatalogoSelecoes(val nacoes: List<Nacao> = emptyList(), val nomes: Map<String, ListaNomes> = emptyMap()) {
    companion object {
        /** Lê "selecoes.json" da pasta de dados (mesmo leitor que BaseDados.carregar). null se faltar ou estiver mal. */
        fun carregar(ler: (String) -> String?): CatalogoSelecoes? {
            val texto = ler("selecoes.json") ?: return null
            return try {
                BaseDados.JSON.decodeFromString(serializer(), texto)
            } catch (e: Exception) {
                null
            }
        }
    }
}

enum class TipoCompeticaoSelecoes(val nome: String) {
    LIGA_NACOES("Liga das Nações"),
    QUALIFICACAO_EURO("Qualificação para o Campeonato da Europa"),
    QUALIFICACAO_MUNDIAL("Qualificação para o Campeonato do Mundo"),
    EURO("Campeonato da Europa"),
    MUNDIAL("Campeonato do Mundo"),
}

/** Um jogo entre seleções. Nos torneios e nas finais o campo é neutro (quem é "casa" é sorteado). */
@Serializable
class JogoSelecao(
    val casa: String,
    val fora: String,
    /** Data ISO (aaaa-mm-dd). */
    val data: String,
    /** Nome do grupo ou da zona; vazio nas eliminatórias dos torneios. */
    val grupo: String = "",
    /** Jornada dentro do grupo (1, 2, 3...); 0 nas eliminatórias. */
    val ronda: Int = 0,
) {
    var jogado = false
    var golosCasa = 0
    var golosFora = 0
    var prolongamento = false
    var penaltisCasa = -1
    var penaltisFora = -1

    fun dataLocal(): LocalDate = LocalDate.parse(data)

    /** Quem ganhou (também nos penáltis); null se ainda não se jogou ou se empatou. */
    fun vencedor(): String? = when {
        !jogado -> null
        golosCasa > golosFora -> casa
        golosFora > golosCasa -> fora
        penaltisCasa > penaltisFora -> casa
        penaltisFora > penaltisCasa -> fora
        else -> null
    }
}

@Serializable
class GrupoSelecoes(val nome: String, val nacoes: List<String>)

/** Uma fase: fase de grupos (com grupos) ou uma ronda a eliminar (sem grupos). */
@Serializable
class FaseSelecoes(
    val nome: String,
    val grupos: List<GrupoSelecoes> = emptyList(),
    val jogos: MutableList<JogoSelecao> = mutableListOf(),
) {
    val completa get() = jogos.all { it.jogado }
    val eliminatoria get() = grupos.isEmpty()
}

/** Uma competição de seleções (uma edição). As fases vão sendo criadas à medida que as anteriores acabam. */
@Serializable
class CompeticaoSelecoes(
    val id: String,
    val tipo: TipoCompeticaoSelecoes,
    val nome: String,
    /** Ano em que acaba (o do torneio, ou o ano civil de junho da Liga das Nações). */
    val ano: Int,
    /** Ano de início da época a que pertence (para as datas). */
    val epoca: Int,
) {
    val fases = mutableListOf<FaseSelecoes>()
    var vencedor: String? = null
    var finalista: String? = null
    var terminada = false

    /** Qualificação: seleções já apuradas (diretas e, depois do play-off, as restantes). */
    val qualificadas = mutableListOf<String>()

    val jogos: List<JogoSelecao> get() = fases.flatMap { it.jogos }
}

/** Uma linha da tabela de um grupo. */
class LinhaTabelaSelecao(val nacao: String) {
    var jogos = 0
    var vitorias = 0
    var empates = 0
    var derrotas = 0
    var golosMarcados = 0
    var golosSofridos = 0
    val pontos get() = vitorias * 3 + empates
    val diferenca get() = golosMarcados - golosSofridos
}

/** Uma linha do ranking mundial. */
class LinhaRanking(val posicao: Int, val nacao: String, val nome: String, val pontos: Int)

/** Vencedor de uma edição de uma competição. */
@Serializable
class EntradaHistorico(
    val ano: Int,
    val tipo: TipoCompeticaoSelecoes,
    val nome: String,
    val vencedor: String,
    val finalista: String = "",
)

/** Um jogo acabado de jogar (devolvido por jogarAte), com o relatório detalhado. */
class JogoJogado(val competicaoId: String, val competicao: String, val fase: String, val jogo: JogoSelecao, val relatorio: RelatorioJogo)

/** Tudo o que o módulo grava (vê EstadoSelecoes.snapshot e EstadoSelecoes.reconstruir). */
@Serializable
class SnapshotSelecoes(
    val epoca: Int,
    val semente: Long,
    val jogosJogados: Int,
    val nacoes: List<Nacao>,
    /** Plantéis das seleções fictícias (jogadores com id negativo). */
    val esquadroes: Map<String, List<Jogador>>,
    val lesoes: Map<Int, Int>,
    val ratings: Map<String, Double>,
    val competicoes: List<CompeticaoSelecoes>,
    val historico: List<EntradaHistorico>,
    val internacionalizacoes: Map<Int, Int>,
    val golosSelecao: Map<Int, Int>,
    val suspensoes: Map<Int, Int>,
    val convocatorias: Map<String, List<Int>>,
    val janelaAtual: String? = null,
    val ultimaData: String? = null,
    val proximoIdFicticio: Int = -1,
    val nomesExtra: Map<String, ListaNomes> = emptyMap(),
)
