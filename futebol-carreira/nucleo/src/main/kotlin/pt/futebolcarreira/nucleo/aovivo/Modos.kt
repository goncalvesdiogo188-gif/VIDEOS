package pt.futebolcarreira.nucleo.aovivo

import kotlin.math.abs

// Modos de jogo (fase E5): jogo completo, só os momentos-chave, ver (IA contra IA) ou simular; duração configurável.

enum class ModoPartida(val nome: String, val descricao: String, val usa3D: Boolean, val controla: Boolean) {
    COMPLETO("Jogo completo", "Jogas o jogo todo em 3D.", true, true),
    MOMENTOS_CHAVE("Só os momentos-chave", "Jogas os golos, as oportunidades e as bolas paradas perigosas; o resto passa depressa.", true, true),
    VER("Ver", "A IA joga pelas duas equipas e tu vês.", true, false),
    SIMULAR("Simular", "O jogo é simulado sem ecrã 3D.", false, false);

    val saltaTempoMorto: Boolean get() = this == MOMENTOS_CHAVE
}

/** Durações de jogo reais (do apito inicial ao final, sem contar pausas). */
object DuracaoJogo {
    val OPCOES_MINUTOS = listOf(4, 8, 12)
    const val PADRAO_MINUTOS = 8

    fun segundos(minutos: Int): Float = minutos.coerceIn(1, 30) * 60f

    /** Ajusta um valor qualquer (guardado, antigo ou estragado) à opção válida mais perto. */
    fun valida(minutos: Int): Int = OPCOES_MINUTOS.minByOrNull { abs(it - minutos) } ?: PADRAO_MINUTOS

    fun texto(minutos: Int) = "$minutos min"
}

/**
 * Decide, a cada imagem, se o jogo está num momento a jogar ("true") ou se pode avançar depressa ("false").
 * É um momento-chave: golo, penálti, bola parada perigosa (canto ou livre perto da área), a bola perto de uma baliza
 * (qualquer das duas, para também se defender) e os últimos minutos de um jogo equilibrado.
 * Depois de começar, um momento dura pelo menos "retencao" segundos reais para se ver o desfecho (remate, defesa, festejo).
 */
class DetetorMomentos(
    private val retencao: Float = 4.5f,
    private val xPerigo: Float = 27f,
    private val yPerigo: Float = 25f,
) {
    private var restante = 0f
    var emMomento = false
        private set

    fun reiniciar() { restante = 0f; emMomento = false }

    /** "dt" em segundos reais desde a última chamada. */
    fun atualizar(e: EstadoPartida, dt: Float): Boolean {
        val agora = ehMomento(e, xPerigo, yPerigo)
        if (agora) restante = retencao else if (restante > 0f) restante -= dt
        emMomento = agora || restante > 0f
        return emMomento
    }

    companion object {
        fun ehMomento(e: EstadoPartida, xPerigo: Float = 27f, yPerigo: Float = 25f): Boolean {
            when (e.fase) {
                FaseJogo.GOLO, FaseJogo.PENALTI, FaseJogo.FIM, FaseJogo.CANTO -> return true
                FaseJogo.INTERVALO -> return true   // o intervalo não se salta (palestra e substituições)
                FaseJogo.LIVRE -> return abs(e.bola.x) > xPerigo - 4f && abs(e.bola.y) < yPerigo
                FaseJogo.LANCAMENTO, FaseJogo.PONTAPE_BALIZA, FaseJogo.PONTAPE_SAIDA -> return false
                FaseJogo.A_JOGAR -> {}
            }
            if (e.minuto >= 88 && abs(e.golosCasa - e.golosFora) <= 1) return true
            return abs(e.bola.x) > xPerigo && abs(e.bola.y) < yPerigo
        }
    }
}

/** Quantos passos rápidos se dão por imagem quando o jogo avança depressa (e o tamanho de cada um). */
object RitmoRapido {
    const val PASSOS_POR_IMAGEM = 8
    const val PASSO = 0.05f
    /** Orçamento de tempo por imagem, em nanossegundos, para não prender o fio do desenho. */
    const val ORCAMENTO_NS = 9_000_000L
}

/**
 * Contrato opcional do motor: com "iaControlaUtilizador" ligado, a equipa do utilizador joga pela IA (usado quando o jogo
 * avança depressa nos momentos sem interesse). A implementação por omissão não existe: o motor que a quiser implementa-a.
 */
interface IaPeloUtilizador {
    var iaControlaUtilizador: Boolean
}
