package pt.futebolcarreira.nucleo.carreira

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.ContasEpoca
import pt.futebolcarreira.nucleo.modelos.FocoTreino
import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.Intensidade
import pt.futebolcarreira.nucleo.modelos.Mentalidade
import pt.futebolcarreira.nucleo.simulacao.CalendarioDatas
import pt.futebolcarreira.nucleo.simulacao.Confronto
import pt.futebolcarreira.nucleo.simulacao.DefRonda
import pt.futebolcarreira.nucleo.simulacao.EstadoLiga
import pt.futebolcarreira.nucleo.simulacao.EstatisticasEquipa
import pt.futebolcarreira.nucleo.simulacao.Evento
import pt.futebolcarreira.nucleo.simulacao.FichaJogador
import pt.futebolcarreira.nucleo.simulacao.GestorEpocas
import pt.futebolcarreira.nucleo.simulacao.Jogo
import pt.futebolcarreira.nucleo.simulacao.LadoRelatorio
import pt.futebolcarreira.nucleo.simulacao.RelatorioJogo
import pt.futebolcarreira.nucleo.simulacao.Resultado
import pt.futebolcarreira.nucleo.simulacao.Ronda
import pt.futebolcarreira.nucleo.simulacao.ResumoEpoca
import pt.futebolcarreira.nucleo.simulacao.TabelaClassificativa
import pt.futebolcarreira.nucleo.simulacao.TipoEvento
import java.time.LocalDate

/** Informação curta sobre uma carreira gravada, para a lista do menu «Carregar». */
@Serializable
class ResumoGravacao(
    val id: String,
    val nome: String,
    val modo: ModoCarreira,
    val clube: String,
    val epoca: String,
    val data: String,
    /** Quando foi gravada (milésimos de segundo desde 1970), para ordenar a lista. */
    val gravadaEm: Long = 0,
)

// ---- Formato do ficheiro de gravação (só os dados; sem lógica) ----

@Serializable
private class EquipaG(
    val id: String, val titulos: Int, val formacao: Formacao, val automatica: Boolean,
    val mentalidade: Mentalidade? = null, val onze: List<Int>? = null, val contas: ContasEpoca? = null,
    val foco: FocoTreino = FocoTreino.EQUILIBRADO, val intensidade: Intensidade = Intensidade.NORMAL,
    val tacas: Int = 0, val supertacas: Int = 0,
)

@Serializable
private class JogadorG(
    val id: Int, val jogos: Int, val golos: Int, val assistencias: Int, val amarelos: Int, val vermelhos: Int,
    val somaNotas: Double, val lesao: Int, val suspensao: Int, val forma: Int = 50, val moral: Int = 50,
    val jogosTreino: Int = 0,
    val jogosTaca: Int = 0, val golosTaca: Int = 0, val assistenciasTaca: Int = 0, val somaNotasTaca: Double = 0.0,
)

/** "resultados": por jornada, por jogo, [golos da casa, golos de fora]. */
@Serializable
private class LigaG(val calendario: List<List<Jogo>>, val datas: List<String>, val resultados: List<List<List<Int>>>)

/** Um jogo de taça (casa e fora vêm do confronto: a joga em casa nas mãos pares). */
@Serializable
private class JogoElimG(
    val golosCasa: Int, val golosFora: Int, val prolongamento: Boolean = false,
    val penaltisCasa: Int = 0, val penaltisFora: Int = 0,
)

@Serializable
private class ConfrontoG(
    val a: String, val b: String, val datas: List<String>, val neutro: Boolean = false,
    val jogos: List<JogoElimG> = emptyList(), val vencedor: String? = null,
)

@Serializable
private class RondaG(val nome: String, val datas: List<String>, val neutro: Boolean, val confrontos: List<ConfrontoG>, val isentos: List<String>)

/** Estado de uma taça ou supertaça: "defs" são todas as rondas previstas, "rondas" só as já sorteadas. */
@Serializable
private class ElimG(
    val participantes: List<String>,
    val defs: List<RondaG>,
    val rondas: List<RondaG>,
    val campeao: String? = null,
    val finalista: String? = null,
    val concluida: Boolean = false,
)

@Serializable
private class EventoG(
    val minuto: Int, val tipo: TipoEvento, val casa: Boolean, val jogador: Int, val outro: Int? = null,
    val penalti: Boolean = false, val golosCasa: Int = 0, val golosFora: Int = 0,
)

@Serializable
private class FichaG(
    val jogador: Int, val lugar: String, val titular: Boolean, val entrou: Int, val saiu: Int, val golos: Int,
    val assistencias: Int, val amarelos: Int, val vermelho: Boolean, val direto: Boolean, val lesao: Int,
    val defesas: Int, val sofridos: Int, val nota: Double,
)

@Serializable
private class LadoG(
    val equipa: String, val formacao: Formacao, val mentalidade: Mentalidade, val fichas: List<FichaG>,
    val estatisticas: EstatisticasEquipa, val indisponiveis: List<Int>,
)

@Serializable
private class RelatorioG(
    val casa: LadoG, val fora: LadoG, val golosCasa: Int, val golosFora: Int, val duracao: Int, val eventos: List<EventoG>,
    val prolongamento: Boolean = false,
)

@Serializable
private class Gravacao(
    val versao: Int = VERSAO,
    val info: InfoCarreira,
    val ano: Int,
    val data: String,
    val equipas: List<EquipaG>,
    /** Só os jogadores com alguma coisa para guardar (jogos, lesão ou castigo). */
    val jogadores: List<JogadorG>,
    val ligas: Map<String, LigaG>,
    /**
     * Taças e supertaças (id -> estado). null = gravação antiga, de antes das taças: nesse caso a época em curso
     * continua sem taças e elas começam na época seguinte.
     */
    val eliminatorias: Map<String, ElimG>? = null,
    /**
     * Relatórios dos jogos do clube do utilizador: "liga/jornada/jogo" -> relatório;
     * nas taças "T/competição/ronda/confronto/mão".
     */
    val relatorios: Map<String, RelatorioG> = emptyMap(),
    val historico: List<ResumoEpoca> = emptyList(),
    val mensagens: List<Mensagem> = emptyList(),
    val olheiros: EstadoOlheiros = EstadoOlheiros(),
)

private const val VERSAO = 1

/**
 * Transforma uma carreira em texto JSON (para gravar em ficheiros) e vice-versa.
 *
 * Uma carreira gravada tem duas partes:
 *  - a base de dados (equipas e jogadores), grande, que só muda de época para época;
 *  - o estado da época (calendário, resultados, estatísticas, mensagens), pequeno, que muda todos os jogos.
 * Assim, depois de cada jogo só é preciso voltar a escrever a parte pequena.
 *
 * Dos jogos já jogados guardam-se os resultados; o relato completo só se guarda
 * para os jogos do clube do utilizador, para o ficheiro não ficar enorme.
 */
object Gravador {
    private val JSON = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun resumo(c: Carreira, agora: Long = System.currentTimeMillis()) = ResumoGravacao(
        c.info.id, c.info.nome, c.info.modo, c.equipa.nome, c.gestor.nomeEpoca(), CalendarioDatas.texto(c.gestor.data), agora,
    )

    fun resumoParaTexto(r: ResumoGravacao): String = JSON.encodeToString(ResumoGravacao.serializer(), r)

    /** Devolve null se o texto não for um resumo válido. */
    fun resumoDeTexto(texto: String): ResumoGravacao? =
        try { JSON.decodeFromString(ResumoGravacao.serializer(), texto) } catch (e: Exception) { null }

    /** A base de dados inteira, no mesmo formato dos ficheiros JSON da aplicação (caminho -> conteúdo). */
    fun baseDadosParaTexto(c: Carreira): String =
        JSON.encodeToString(MapSerializer(String.serializer(), String.serializer()), c.bd.paraJsonCompleto())

    /** O estado da época e da carreira. */
    fun paraTexto(c: Carreira): String {
        val bd = c.bd
        val g = c.gestor
        val relatorios = LinkedHashMap<String, RelatorioG>()
        val ligas = g.estado.mapValues { (ligaId, e) ->
            e.resultados.forEachIndexed { jornada, jogos ->
                jogos.forEachIndexed { indice, r ->
                    val relatorio = r.relatorio
                    if (relatorio != null && (r.casa == c.info.equipaId || r.fora == c.info.equipaId)) {
                        relatorios["$ligaId/$jornada/$indice"] = paraG(relatorio)
                    }
                }
            }
            LigaG(e.calendario, e.datas.map { it.toString() }, e.resultados.map { jogos -> jogos.map { listOf(it.golosCasa, it.golosFora) } })
        }
        val elims = g.eliminatorias.mapValues { (id, t) ->
            t.rondas.forEachIndexed { r, ronda ->
                ronda.confrontos.forEachIndexed { k, conf ->
                    if (!conf.envolve(c.info.equipaId)) return@forEachIndexed
                    conf.jogos.forEachIndexed { i, jogo ->
                        jogo.relatorio?.let { relatorios["T/$id/$r/$k/$i"] = paraG(it) }
                    }
                }
            }
            ElimG(
                t.participantes, t.defs.map { paraG(it, emptyList(), emptyList()) },
                t.rondas.map { paraG(it.def, it.confrontos, it.isentos) }, t.campeao, t.finalista, t.concluida,
            )
        }
        val gravacao = Gravacao(
            info = c.info,
            ano = g.ano,
            data = g.data.toString(),
            equipas = bd.equipas.values.map { EquipaG(it.id, it.titulos, it.formacao, it.formacaoAutomatica, it.mentalidade, it.onzeManual, it.contas, it.focoTreino, it.intensidade, it.tacas, it.supertacas) },
            jogadores = bd.jogadores.values.filter { it.epoca.jogos > 0 || it.epocaTaca.jogos > 0 || !it.disponivel || it.forma != 50 || it.moral != 50 }.sortedBy { it.id }.map {
                val e = it.epoca
                JogadorG(it.id, e.jogos, e.golos, e.assistencias, e.amarelos, e.vermelhos, e.somaNotas, it.lesao, it.suspensao, it.forma, it.moral, it.jogosNoUltimoTreino, it.epocaTaca.jogos, it.epocaTaca.golos, it.epocaTaca.assistencias, it.epocaTaca.somaNotas)
            },
            ligas = ligas,
            eliminatorias = elims,
            relatorios = relatorios,
            historico = g.historico.toList(),
            mensagens = c.mensagens.toList(),
            olheiros = c.olheiros.estado,
        )
        return JSON.encodeToString(Gravacao.serializer(), gravacao)
    }

    /** Reconstrói a carreira a partir dos dois textos gravados. Lança uma exceção se estiverem estragados. */
    fun deTexto(texto: String, baseDados: String): Carreira {
        val gravacao = JSON.decodeFromString(Gravacao.serializer(), texto)
        val ficheiros = JSON.decodeFromString(MapSerializer(String.serializer(), String.serializer()), baseDados)
        val bd = BaseDados()
        bd.carregar { ficheiros[it] }
        require(bd.equipas.isNotEmpty()) { "Gravação sem equipas: ${bd.erros.take(3)}" }
        bd.garantirTacas()   // gravações antigas não tinham taças: ficam definidas para a época seguinte
        require(gravacao.info.equipaId in bd.equipas) { "O clube da carreira não existe na gravação" }

        for (e in gravacao.equipas) {
            val equipa = bd.equipas[e.id] ?: continue
            equipa.titulos = e.titulos
            equipa.tacas = e.tacas
            equipa.supertacas = e.supertacas
            equipa.formacaoAutomatica = e.automatica
            equipa.formacao = e.formacao
            equipa.mentalidade = e.mentalidade
            equipa.onzeManual = e.onze
            e.contas?.let { equipa.contas = it }
            equipa.focoTreino = e.foco
            equipa.intensidade = e.intensidade
        }
        for (g in gravacao.jogadores) {
            val j = bd.jogadores[g.id] ?: continue
            j.epoca.jogos = g.jogos
            j.epoca.golos = g.golos
            j.epoca.assistencias = g.assistencias
            j.epoca.amarelos = g.amarelos
            j.epoca.vermelhos = g.vermelhos
            j.epoca.somaNotas = g.somaNotas
            j.epocaTaca.jogos = g.jogosTaca
            j.epocaTaca.golos = g.golosTaca
            j.epocaTaca.assistencias = g.assistenciasTaca
            j.epocaTaca.somaNotas = g.somaNotasTaca
            j.lesao = g.lesao
            j.suspensao = g.suspensao
            j.forma = g.forma
            j.moral = g.moral
            j.jogosNoUltimoTreino = g.jogosTreino
        }

        val gestor = GestorEpocas(bd, anoInicial = gravacao.ano, iniciar = false)
        gestor.data = LocalDate.parse(gravacao.data)
        for ((ligaId, l) in gravacao.ligas) {
            val liga = bd.competicoes[ligaId] ?: continue
            val ids = l.calendario.flatten().flatMap { listOf(it.casa, it.fora) }.distinct().sorted()
            val estado = EstadoLiga(l.calendario, TabelaClassificativa(ids, liga.pontosVitoria, liga.pontosEmpate), l.datas.map { LocalDate.parse(it) })
            l.resultados.forEachIndexed { jornada, jogos ->
                estado.resultados += jogos.mapIndexed { indice, golos ->
                    val jogo = l.calendario[jornada][indice]
                    estado.tabela.registar(jogo.casa, jogo.fora, golos[0], golos[1])
                    val relatorio = gravacao.relatorios["$ligaId/$jornada/$indice"]?.let { deG(it, bd) }
                    Resultado(jogo.casa, jogo.fora, golos[0], golos[1], relatorio)
                }
            }
            gestor.estado[ligaId] = estado
        }
        for ((id, g) in gravacao.eliminatorias.orEmpty()) {
            val comp = bd.competicoes[id] ?: continue
            val defs = g.defs.map { DefRonda(it.nome, it.datas.map(LocalDate::parse), it.neutro) }
            val t = gestor.eliminatoriaVazia(comp, g.participantes, defs)
            val rondas = g.rondas.mapIndexed { r, rg ->
                val confrontos = rg.confrontos.mapIndexed { k, cg ->
                    Confronto(cg.a, cg.b, cg.datas.map(LocalDate::parse), cg.neutro).also { conf ->
                        cg.jogos.forEachIndexed { i, jg ->
                            val relatorio = gravacao.relatorios["T/$id/$r/$k/$i"]?.let { deG(it, bd) }
                            conf.jogos += Resultado(conf.casaDoJogo(i), conf.foraDoJogo(i), jg.golosCasa, jg.golosFora, relatorio, jg.prolongamento, jg.penaltisCasa, jg.penaltisFora)
                        }
                        conf.vencedor = cg.vencedor
                    }
                }
                Ronda(defs[r], confrontos, rg.isentos)
            }
            t.restaurar(rondas, g.campeao, g.finalista, g.concluida)
            gestor.eliminatorias[id] = t
        }
        gestor.historico += gravacao.historico
        gestor.epocaFechada = gestor.epocaTerminada()

        return Carreira(bd, gestor, gravacao.info, gravacao.olheiros).also {
            it.mensagens += gravacao.mensagens
            it.baseDadosPorGravar = false
        }
    }

    private fun paraG(def: DefRonda, confrontos: List<Confronto>, isentos: List<String>) = RondaG(
        def.nome, def.datas.map { it.toString() }, def.neutro,
        confrontos.map { c ->
            ConfrontoG(
                c.a, c.b, c.datas.map { it.toString() }, c.neutro,
                c.jogos.map { JogoElimG(it.golosCasa, it.golosFora, it.prolongamento, it.penaltisCasa, it.penaltisFora) }, c.vencedor,
            )
        },
        isentos,
    )

    private fun paraG(r: RelatorioJogo) = RelatorioG(
        paraG(r.casa), paraG(r.fora), r.golosCasa, r.golosFora, r.duracao,
        r.eventos.map { EventoG(it.minuto, it.tipo, it.casa, it.jogador.id, it.outro?.id, it.penalti, it.golosCasa, it.golosFora) },
        r.prolongamento,
    )

    private fun paraG(l: LadoRelatorio) = LadoG(
        l.equipa, l.formacao, l.mentalidade,
        l.fichas.map {
            FichaG(
                it.jogador.id, it.lugar, it.titular, it.entrou, it.saiu, it.golos, it.assistencias, it.amarelos,
                it.vermelho, it.vermelhoDireto, it.lesao, it.defesas, it.golosSofridos, it.nota,
            )
        },
        l.estatisticas, l.indisponiveis.map { it.id },
    )

    /** Devolve null se algum jogador do relatório já não existir. */
    private fun deG(r: RelatorioG, bd: BaseDados): RelatorioJogo? {
        val casa = deG(r.casa, bd) ?: return null
        val fora = deG(r.fora, bd) ?: return null
        val eventos = r.eventos.map {
            Evento(
                it.minuto, it.tipo, it.casa, bd.jogadores[it.jogador] ?: return null, it.outro?.let { id -> bd.jogadores[id] },
                it.penalti, it.golosCasa, it.golosFora,
            )
        }
        return RelatorioJogo(casa, fora, r.golosCasa, r.golosFora, r.duracao, eventos, r.prolongamento)
    }

    private fun deG(l: LadoG, bd: BaseDados): LadoRelatorio? {
        val fichas = l.fichas.map { g ->
            FichaJogador(bd.jogadores[g.jogador] ?: return null, g.lugar, g.titular, g.entrou).also {
                it.saiu = g.saiu
                it.golos = g.golos
                it.assistencias = g.assistencias
                it.amarelos = g.amarelos
                it.vermelho = g.vermelho
                it.vermelhoDireto = g.direto
                it.lesao = g.lesao
                it.defesas = g.defesas
                it.golosSofridos = g.sofridos
                it.nota = g.nota
            }
        }
        return LadoRelatorio(l.equipa, l.formacao, l.mentalidade, fichas, l.estatisticas, l.indisponiveis.mapNotNull { bd.jogadores[it] })
    }
}
