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
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.simulacao.GestorEpocas

// ---- O que o ecrã mostra (cópias simples dos dados, seguras para desenhar) ----

enum class Zona { NORMAL, SUBIDA, DESCIDA }

class OpcaoLiga(val id: String, val texto: String)

class LinhaUi(
    val posicao: Int, val equipaId: String, val nome: String,
    val j: Int, val v: Int, val e: Int, val d: Int, val gm: Int, val gs: Int, val pts: Int,
    val zona: Zona,
)

class ResultadoUi(val casa: String, val golosCasa: Int, val golosFora: Int, val fora: String)

class EpocaUi(val epoca: String, val campeao: String, val pontos: Int, val sobem: String, val descem: String)

class JogadorUi(val posicao: String, val nome: String, val idade: Int, val geral: Int, val potencial: Int, val valor: String)

class PlantelUi(val titulo: String, val detalhes: String, val jogadores: List<JogadorUi>)

class EstadoEcra(
    val titulo: String = "",
    val ligas: List<OpcaoLiga> = emptyList(),
    val ligaId: String = "",
    val tabela: List<LinhaUi> = emptyList(),
    val jornada: Int = 0,
    val resultados: List<ResultadoUi> = emptyList(),
    val terminada: Boolean = false,
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

    /** O que o ecrã mostra neste momento. */
    var ecra by mutableStateOf(EstadoEcra())
        private set

    /** true enquanto se carrega ou simula (os botões ficam desligados). */
    var ocupado by mutableStateOf(false)
        private set

    /** Plantel aberto (null = nenhum). */
    var plantel by mutableStateOf<PlantelUi?>(null)
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

    fun abrirPlantel(equipaId: String) {
        if (ocupado) return
        val equipa = bd.equipas[equipaId] ?: return
        val forca = equipa.forca()
        plantel = PlantelUi(
            titulo = "${equipa.nome} (${equipa.abreviatura})",
            detalhes = "${equipa.estadio} · ${equipa.capacidade} lugares\n" +
                "Reputação ${equipa.reputacao} · Ataque ${forca.ataque.toInt()} · Defesa ${forca.defesa.toInt()} · Títulos ${equipa.titulos}",
            jogadores = equipa.plantel
                .sortedWith(compareBy<Jogador> { Jogador.POSICOES.indexOf(it.posicao) }.thenByDescending { it.geral() })
                .map { JogadorUi(it.posicao, it.nome, it.idade, it.geral(), it.potencial, formatarEuros(it.valorMercado())) },
        )
    }

    fun fecharPlantel() {
        plantel = null
    }

    /** Corre "acao" em segundo plano e depois atualiza o ecrã. */
    private fun executar(acao: () -> Unit) {
        if (ocupado) return
        ocupado = true
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
                EpocaUi(g.nomeEpoca(resumo.ano), nome(it.campeao), it.pontos, it.sobem.joinToString { id -> nome(id) }, it.descem.joinToString { id -> nome(id) })
            }
        }
        val situacao = if (g.epocaTerminada()) "Época terminada" else "Jornada ${estado.jornada}/${estado.totalJornadas}"
        return EstadoEcra(
            titulo = "Época ${g.nomeEpoca()} · $situacao",
            ligas = bd.paises.values.flatMap { pais -> bd.ligasDoPais(pais.id).map { OpcaoLiga(it.id, "${pais.nome} – ${it.nome}") } },
            ligaId = ligaId,
            tabela = tabela,
            jornada = estado.jornada,
            resultados = estado.resultados.lastOrNull().orEmpty().map { ResultadoUi(nome(it.casa), it.golosCasa, it.golosFora, nome(it.fora)) },
            terminada = g.epocaTerminada(),
            historico = historico,
            titulos = historico.groupingBy { it.campeao }.eachCount().toList().sortedByDescending { it.second },
            erros = bd.erros.toList(),
        )
    }

    private fun formatarEuros(valor: Int): String =
        if (valor >= 1_000_000) "%.1f M€".format(valor / 1_000_000.0) else "${valor / 1000} m€"
}
