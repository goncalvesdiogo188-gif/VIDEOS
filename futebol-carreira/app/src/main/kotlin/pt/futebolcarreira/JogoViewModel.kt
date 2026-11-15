package pt.futebolcarreira

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.modelos.Mentalidade
import pt.futebolcarreira.nucleo.simulacao.GestorEpocas
import pt.futebolcarreira.nucleo.simulacao.LadoRelatorio
import pt.futebolcarreira.nucleo.simulacao.Relato
import pt.futebolcarreira.nucleo.simulacao.TipoEvento

// ---- O que o ecrã mostra (cópias simples dos dados, seguras para desenhar) ----

enum class Zona { NORMAL, SUBIDA, DESCIDA }

class OpcaoLiga(val id: String, val texto: String)

class LinhaUi(
    val posicao: Int, val equipaId: String, val nome: String,
    val j: Int, val v: Int, val e: Int, val d: Int, val gm: Int, val gs: Int, val pts: Int,
    val zona: Zona,
)

class ResultadoUi(val indice: Int, val casa: String, val golosCasa: Int, val golosFora: Int, val fora: String)

class EpocaUi(val epoca: String, val campeao: String, val pontos: Int, val sobem: String, val descem: String, val marcador: String)

/** Linha de uma lista de melhores jogadores (marcadores, assistências, notas). */
class LinhaJogadorUi(val nome: String, val clube: String, val jogos: Int, val valor: String)

class JogadorUi(
    val posicao: String, val nome: String, val idade: Int, val geral: Int, val potencial: Int, val valor: String,
    val jogos: Int, val golos: Int, val assistencias: Int, val nota: String,
    /** "" se está apto; senão o motivo (lesão ou castigo). */
    val estado: String,
)

class PlantelUi(
    val equipaId: String, val titulo: String, val detalhes: String, val jogadores: List<JogadorUi>,
    /** null = automática. */
    val formacao: Formacao?, val formacaoEmUso: String, val mentalidade: Mentalidade?,
)

/** Um acontecimento no relato. "tipo" null = frase do narrador (início, intervalo, fim). */
class EventoUi(val minuto: Int, val minutoTexto: String, val texto: String, val tipo: TipoEvento?, val casa: Boolean, val golosCasa: Int, val golosFora: Int)

class FichaUi(
    val lugar: String, val nome: String, val nota: String, val titular: Boolean,
    val golos: Int, val assistencias: Int, val amarelos: Int, val vermelho: Boolean, val lesionado: Boolean,
    val entrou: Int, val saiu: Int,
)

/** Um jogo aberto para ver o relato, as estatísticas e as notas. */
class JogoUi(
    val casa: String, val fora: String, val golosCasa: Int, val golosFora: Int, val duracao: Int,
    val taticaCasa: String, val taticaFora: String,
    val eventos: List<EventoUi>,
    /** nome da estatística, valor da casa, valor de fora. */
    val estatisticas: List<Triple<String, String, String>>,
    val fichasCasa: List<FichaUi>, val fichasFora: List<FichaUi>,
    val melhorEmCampo: String,
)

class EstadoEcra(
    val titulo: String = "",
    val ligas: List<OpcaoLiga> = emptyList(),
    val ligaId: String = "",
    val tabela: List<LinhaUi> = emptyList(),
    /** Jornadas já jogadas e a que está a ser mostrada nos resultados. */
    val jornada: Int = 0,
    val jornadaVista: Int = 0,
    val resultados: List<ResultadoUi> = emptyList(),
    val terminada: Boolean = false,
    val marcadores: List<LinhaJogadorUi> = emptyList(),
    val assistencias: List<LinhaJogadorUi> = emptyList(),
    val notas: List<LinhaJogadorUi> = emptyList(),
    val historico: List<EpocaUi> = emptyList(),
    val titulos: List<Pair<String, Int>> = emptyList(),
    val erros: List<String> = emptyList(),
)

/**
 * Liga os ecrãs ao núcleo do jogo. As simulações correm em segundo plano para
 * a aplicação não "congelar"; no fim, o ecrã recebe uma cópia nova dos dados.
 */
class JogoViewModel(app: Application) : AndroidViewModel(app) {
    private var bd = BaseDados()
    private var gestor: GestorEpocas? = null
    private var ligaId = "POR1"

    /** Jornada escolhida nos resultados; null = a última jogada. */
    private var jornadaEscolhida: Int? = null

    /** O que o ecrã mostra neste momento. */
    var ecra by mutableStateOf(EstadoEcra())
        private set

    /** true enquanto se carrega ou simula (os botões ficam desligados). */
    var ocupado by mutableStateOf(false)
        private set

    /** Plantel aberto (null = nenhum). */
    var plantel by mutableStateOf<PlantelUi?>(null)
        private set

    /** Jogo aberto (null = nenhum). */
    var jogo by mutableStateOf<JogoUi?>(null)
        private set

    init {
        novoJogo()
    }

    /** Carrega a base de dados de raiz e começa na primeira época. */
    fun novoJogo() = executar {
        val assets = getApplication<Application>().assets
        bd = BaseDados()
        bd.carregar { caminho ->
            try {
                assets.open("dados/$caminho").bufferedReader().use { it.readText() }
            } catch (e: java.io.IOException) {
                null  // ficheiro não existe
            }
        }
        gestor = GestorEpocas(bd)
    }

    /** Simula uma jornada (ou começa a época seguinte, se esta já acabou). */
    fun jornadaSeguinte() = executar {
        gestor?.let { if (it.epocaTerminada()) it.avancarParaEpocaSeguinte() else it.simularJornada() }
    }

    /** Simula até ao fim da época (começando a seguinte, se esta já acabou). */
    fun simularEpocas(quantas: Int) = executar {
        gestor?.let { g ->
            repeat(quantas) {
                if (g.epocaTerminada()) g.avancarParaEpocaSeguinte()
                g.simularAteAoFim()
            }
        }
    }

    fun escolherLiga(id: String) {
        if (ocupado) return
        ligaId = id
        ecra = criarEstado()
    }

    /** Anda para a jornada anterior (-1) ou seguinte (+1) nos resultados. */
    fun mudarJornada(passo: Int) {
        if (ocupado) return
        val total = ecra.jornada
        if (total == 0) return
        jornadaEscolhida = (ecra.jornadaVista + passo).coerceIn(1, total).takeIf { it < total }
        ecra = criarEstado()
    }

    fun abrirPlantel(equipaId: String) {
        if (ocupado) return
        val equipa = bd.equipas[equipaId] ?: return
        val forca = equipa.forca()
        plantel = PlantelUi(
            equipaId = equipaId,
            titulo = "${equipa.nome} (${equipa.abreviatura})",
            detalhes = "${equipa.estadio} · ${equipa.capacidade} lugares\n" +
                "Reputação ${equipa.reputacao} · Ataque ${forca.ataque.toInt()} · Defesa ${forca.defesa.toInt()} · Títulos ${equipa.titulos}",
            jogadores = equipa.plantel
                .sortedWith(compareBy<Jogador> { Jogador.POSICOES.indexOf(it.posicao) }.thenByDescending { it.geral() })
                .map {
                    JogadorUi(
                        it.posicao, it.nome, it.idade, it.geral(), it.potencial, formatarEuros(it.valorMercado()),
                        it.epoca.jogos, it.epoca.golos, it.epoca.assistencias,
                        if (it.epoca.jogos == 0) "-" else "%.1f".format(it.epoca.notaMedia()),
                        when {
                            it.lesao > 0 -> "Lesionado: falha ${jogos(it.lesao)}"
                            it.suspensao > 0 -> "Castigado: falha ${jogos(it.suspensao)}"
                            else -> ""
                        },
                    )
                },
            formacao = equipa.formacao.takeIf { !equipa.formacaoAutomatica },
            formacaoEmUso = equipa.formacao.nome,
            mentalidade = equipa.mentalidade,
        )
    }

    private fun jogos(n: Int) = if (n == 1) "1 jogo" else "$n jogos"

    fun fecharPlantel() {
        plantel = null
    }

    /** Escolhe a formação de uma equipa (null = o treinador decide). Conta a partir do jogo seguinte. */
    fun mudarFormacao(equipaId: String, formacao: Formacao?) {
        if (ocupado) return
        val equipa = bd.equipas[equipaId] ?: return
        equipa.formacaoAutomatica = formacao == null
        if (formacao == null) equipa.escolherFormacao() else equipa.formacao = formacao
        abrirPlantel(equipaId)
    }

    /** Escolhe a mentalidade de uma equipa (null = o treinador decide jogo a jogo). */
    fun mudarMentalidade(equipaId: String, mentalidade: Mentalidade?) {
        if (ocupado) return
        val equipa = bd.equipas[equipaId] ?: return
        equipa.mentalidade = mentalidade
        abrirPlantel(equipaId)
    }

    /** Abre um jogo da jornada que está à vista. */
    fun abrirJogo(indice: Int) {
        if (ocupado) return
        val estado = gestor?.estado?.get(ligaId) ?: return
        val r = estado.resultados.getOrNull(ecra.jornadaVista - 1)?.getOrNull(indice)?.relatorio ?: return
        val casa = bd.equipas[r.casa.equipa]?.nome ?: r.casa.equipa
        val fora = bd.equipas[r.fora.equipa]?.nome ?: r.fora.equipa

        val eventos = mutableListOf(EventoUi(0, "", "Apita o árbitro: começa o jogo!", null, true, 0, 0))
        var golosCasa = 0
        var golosFora = 0
        var intervalo = false
        for (e in r.eventos) {
            if (!intervalo && e.minuto > 45) {
                intervalo = true
                eventos += EventoUi(45, "", "Intervalo: $casa $golosCasa - $golosFora $fora.", null, true, golosCasa, golosFora)
            }
            if (e.tipo == TipoEvento.GOLO) {
                golosCasa = e.golosCasa
                golosFora = e.golosFora
            }
            eventos += EventoUi(e.minuto, Relato.minuto(e.minuto), Relato.frase(e, if (e.casa) casa else fora), e.tipo, e.casa, golosCasa, golosFora)
        }
        if (!intervalo) eventos += EventoUi(45, "", "Intervalo: $casa $golosCasa - $golosFora $fora.", null, true, golosCasa, golosFora)
        eventos += EventoUi(r.duracao, "", "Fim do jogo: $casa ${r.golosCasa} - ${r.golosFora} $fora.", null, true, r.golosCasa, r.golosFora)

        val ec = r.casa.estatisticas
        val ef = r.fora.estatisticas
        val melhor = r.melhorEmCampo
        jogo = JogoUi(
            casa, fora, r.golosCasa, r.golosFora, r.duracao,
            "${r.casa.formacao.nome} · ${r.casa.mentalidade.nome}", "${r.fora.formacao.nome} · ${r.fora.mentalidade.nome}",
            eventos,
            listOf(
                Triple("Posse de bola", "${ec.posse}%", "${ef.posse}%"),
                Triple("Remates", "${ec.remates}", "${ef.remates}"),
                Triple("Remates à baliza", "${ec.rematesBaliza}", "${ef.rematesBaliza}"),
                Triple("Cantos", "${ec.cantos}", "${ef.cantos}"),
                Triple("Faltas", "${ec.faltas}", "${ef.faltas}"),
                Triple("Cartões amarelos", "${ec.amarelos}", "${ef.amarelos}"),
                Triple("Cartões vermelhos", "${ec.vermelhos}", "${ef.vermelhos}"),
            ),
            fichas(r.casa), fichas(r.fora),
            melhor?.let { "${it.jogador.nome} (%.1f)".format(it.nota) } ?: "",
        )
    }

    private fun fichas(lado: LadoRelatorio): List<FichaUi> = lado.fichas
        .sortedWith(compareByDescending<pt.futebolcarreira.nucleo.simulacao.FichaJogador> { it.titular }.thenBy { it.entrou })
        .map {
            FichaUi(
                it.lugar, it.jogador.nome, "%.1f".format(it.nota), it.titular,
                it.golos, it.assistencias, it.amarelos, it.vermelho, it.lesao > 0, it.entrou, it.saiu,
            )
        }

    fun fecharJogo() {
        jogo = null
    }

    /** Corre "acao" em segundo plano e depois atualiza o ecrã. */
    private fun executar(acao: () -> Unit) {
        if (ocupado) return
        ocupado = true
        jornadaEscolhida = null
        viewModelScope.launch {
            val novo = withContext(Dispatchers.Default) {
                acao()
                criarEstado()
            }
            ecra = novo
            ocupado = false
        }
    }

    /** Copia do núcleo tudo o que o ecrã precisa de mostrar para a liga escolhida. */
    private fun criarEstado(): EstadoEcra {
        val g = gestor ?: return EstadoEcra(erros = bd.erros.toList())
        if (ligaId !in g.estado) ligaId = g.estado.keys.firstOrNull() ?: return EstadoEcra(erros = bd.erros.toList())
        val liga = bd.competicoes.getValue(ligaId)
        val estado = g.estado.getValue(ligaId)
        val nome = { id: String -> bd.equipas[id]?.nome ?: id }

        // Quantas sobem desta liga = quantas descem da liga de cima.
        val sobem = bd.competicoes.values.firstOrNull { it.descePara == ligaId }?.descidas ?: 0
        val ordem = estado.tabela.ordenada()
        val tabela = ordem.mapIndexed { i, l ->
            val zona = when {
                (liga.nivel == 1 && i == 0) || i < sobem -> Zona.SUBIDA
                liga.descePara.isNotEmpty() && i >= ordem.size - liga.descidas -> Zona.DESCIDA
                else -> Zona.NORMAL
            }
            LinhaUi(i + 1, l.id, nome(l.id), l.j, l.v, l.e, l.d, l.gm, l.gs, l.pts, zona)
        }

        val historico = g.historico.reversed().mapNotNull { resumo ->
            resumo.ligas[ligaId]?.let {
                EpocaUi(
                    g.nomeEpoca(resumo.ano), nome(it.campeao), it.pontos,
                    it.sobem.joinToString { id -> nome(id) }, it.descem.joinToString { id -> nome(id) },
                    if (it.melhorMarcador.isEmpty()) "" else "${it.melhorMarcador} (${it.clubeMarcador}), ${it.golosMarcador} golos",
                )
            }
        }
        // Jornada à vista nos resultados (a última, se não houver outra escolhida).
        val vista = (jornadaEscolhida ?: estado.jornada).coerceIn(0, estado.jornada)
        if (vista == estado.jornada) jornadaEscolhida = null
        val linhaJogador = { j: Jogador, valor: String -> LinhaJogadorUi(j.nome, bd.equipas[j.equipa]?.abreviatura ?: "", j.epoca.jogos, valor) }
        // Para a nota média só conta quem jogou pelo menos metade dos jogos.
        val minimoJogos = maxOf(1, estado.jornada / 2)
        val situacao = if (g.epocaTerminada()) "Época terminada" else "Jornada ${estado.jornada}/${estado.totalJornadas}"
        return EstadoEcra(
            titulo = "Época ${g.nomeEpoca()} · $situacao",
            ligas = bd.paises.values.flatMap { pais -> bd.ligasDoPais(pais.id).map { OpcaoLiga(it.id, "${pais.nome} – ${it.nome}") } },
            ligaId = ligaId,
            tabela = tabela,
            jornada = estado.jornada,
            jornadaVista = vista,
            resultados = estado.resultados.getOrNull(vista - 1).orEmpty()
                .mapIndexed { i, r -> ResultadoUi(i, nome(r.casa), r.golosCasa, r.golosFora, nome(r.fora)) },
            terminada = g.epocaTerminada(),
            marcadores = g.melhores(ligaId, 10) { it.epoca.golos.toDouble() }.map { linhaJogador(it, "${it.epoca.golos}") },
            assistencias = g.melhores(ligaId, 10) { it.epoca.assistencias.toDouble() }.map { linhaJogador(it, "${it.epoca.assistencias}") },
            notas = g.melhores(ligaId, 10, minimoJogos) { it.epoca.notaMedia() }.map { linhaJogador(it, "%.2f".format(it.epoca.notaMedia())) },
            historico = historico,
            titulos = historico.groupingBy { it.campeao }.eachCount().toList().sortedByDescending { it.second },
            erros = bd.erros.toList(),
        )
    }

    private fun formatarEuros(valor: Int): String =
        if (valor >= 1_000_000) "%.1f M€".format(valor / 1_000_000.0) else "${valor / 1000} m€"
}
