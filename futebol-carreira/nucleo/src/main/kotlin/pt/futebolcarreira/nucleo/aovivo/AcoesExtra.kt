package pt.futebolcarreira.nucleo.aovivo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Controlos avançados (fase E2): passe dirigido, em profundidade, cruzamento, remate colocado e por cima,
// finta, proteger a bola e a ajuda de mira. O contrato com o motor é "PartidaComAcoes"; enquanto o motor
// não o implementar, o "AdaptadorAcoes" traduz tudo para o Comando básico (que o motor já percebe).

enum class TipoPasse { NORMAL, DIRIGIDO, PROFUNDIDADE, CRUZAMENTO }

enum class TipoRemate { NORMAL, COLOCADO, POR_CIMA }

/**
 * O que o comando básico não consegue dizer. Os ângulos são no referencial do campo (radianos, 0 = +x), nunca do ecrã.
 * "anguloPasse" = para onde vai o passe (null = o motor escolhe pelo joystick); "anguloRemate" = para onde vai o remate
 * (null = o motor escolhe). "fintaLado" é +1/-1 (para que lado se faz o toque de finta).
 */
data class ComandoExtra(
    val tipoPasse: TipoPasse = TipoPasse.NORMAL,
    val anguloPasse: Float? = null,
    val tipoRemate: TipoRemate = TipoRemate.NORMAL,
    val anguloRemate: Float? = null,
    val finta: Boolean = false,
    val fintaLado: Int = 1,
    val proteger: Boolean = false,
) {
    val vazio: Boolean get() = this == SEM_EXTRA

    companion object {
        val SEM_EXTRA = ComandoExtra()
    }
}

/** Contrato: um motor que percebe os controlos avançados. A implementação por omissão ignora o extra. */
interface PartidaComAcoes : PartidaAoVivo {
    fun passo(dt: Float, comando: Comando, extra: ComandoExtra) = passo(dt, comando)
}

/** Passa um passo à partida pelo caminho certo: com extras se o motor os percebe, senão pelo comando básico adaptado. */
fun PartidaAoVivo.passoComExtra(dt: Float, comando: Comando, extra: ComandoExtra, sentidoAtaque: Int) {
    if (extra.vazio) { passo(dt, comando); return }
    if (this is PartidaComAcoes) passo(dt, comando, extra)
    else passo(dt, AdaptadorAcoes.paraBasico(comando, extra, sentidoAtaque))
}

object AdaptadorAcoes {
    const val FORCA_PROFUNDIDADE = 0.72f
    const val FORCA_CRUZAMENTO = 1f
    const val FORCA_COLOCADO_MAX = 0.8f
    const val FORCA_COLOCADO_MIN = 0.55f
    const val ESCALA_PROTEGER = 0.55f

    /** Tradução para o Comando que o motor atual percebe. "sentidoAtaque" +1 = o utilizador ataca para +x. */
    fun paraBasico(c: Comando, e: ComandoExtra, sentidoAtaque: Int): Comando {
        var mx = c.movX; var my = c.movY; var forca = c.forca; var sprint = c.sprint
        if (c.passe) {
            when (e.tipoPasse) {
                TipoPasse.NORMAL -> {}
                TipoPasse.DIRIGIDO -> e.anguloPasse?.let { mx = cos(it); my = sin(it) }
                TipoPasse.PROFUNDIDADE -> {
                    val a = e.anguloPasse ?: if (sentidoAtaque >= 0) 0f else PI.toFloat()
                    mx = cos(a); my = sin(a); forca = maxOf(forca, FORCA_PROFUNDIDADE)
                }
                TipoPasse.CRUZAMENTO -> {
                    e.anguloPasse?.let { mx = cos(it); my = sin(it) }
                    forca = FORCA_CRUZAMENTO
                }
            }
        }
        if (c.remate) {
            when (e.tipoRemate) {
                TipoRemate.NORMAL -> {}
                TipoRemate.COLOCADO -> {
                    // O motor escolhe o lado pelo sinal de movY: aponta para o canto pedido.
                    e.anguloRemate?.let { mx = cos(it); my = sin(it) }
                    forca = forca.coerceIn(FORCA_COLOCADO_MIN, FORCA_COLOCADO_MAX)
                }
                TipoRemate.POR_CIMA -> {
                    e.anguloRemate?.let { mx = cos(it); my = sin(it) }
                    forca = 1f
                }
            }
        }
        if (e.finta) {
            // Toque de lado: roda o movimento 70 graus e arranca.
            val base = if (hypot(mx, my) > 0.1f) atan2(my, mx) else if (sentidoAtaque >= 0) 0f else PI.toFloat()
            val a = base + e.fintaLado * (70f * PI.toFloat() / 180f)
            mx = cos(a); my = sin(a); sprint = true
        }
        if (e.proteger) {
            mx *= ESCALA_PROTEGER; my *= ESCALA_PROTEGER; sprint = false
        }
        return c.copy(movX = mx, movY = my, forca = forca, sprint = sprint)
    }
}

/**
 * Ajuda de mira: puxa a direção escolhida pelo jogador para o colega (ou o canto da baliza) mais próximo.
 * "FORTE" no Fácil, nenhuma no Lendário. Nomes de dificuldade como texto, para não depender do ficheiro da dificuldade.
 */
enum class NivelMira(val nome: String, val cone: Float, val forca: Float) {
    FORTE("Forte", 40f * PI.toFloat() / 180f, 0.85f),
    MEDIA("Média", 28f * PI.toFloat() / 180f, 0.6f),
    FRACA("Fraca", 16f * PI.toFloat() / 180f, 0.35f),
    NENHUMA("Nenhuma", 0f, 0f);

    companion object {
        fun deNome(texto: String?): NivelMira? = entries.firstOrNull { it.name == texto }

        /** Fácil → forte; Normal → média; Difícil → fraca; Lendário → nenhuma. Desconhecida → média. */
        fun daDificuldade(nome: String?): NivelMira = when (nome?.trim()?.uppercase()?.replace("Á", "A")) {
            "FACIL" -> FORTE
            "DIFICIL" -> FRACA
            "LENDARIO" -> NENHUMA
            else -> MEDIA
        }
    }
}

object Mira {
    /** Posição relativa de um colega (em relação a quem passa). */
    class Alvo(val dx: Float, val dy: Float)

    /** Diferença entre dois ângulos, em (-PI, PI]. */
    fun difAngulo(a: Float, b: Float): Float {
        val dois = 2f * PI.toFloat()
        var d = (a - b) % dois
        if (d > PI) d -= dois
        if (d <= -PI) d += dois
        return d
    }

    /**
     * Ajusta "angulo" para o colega mais bem alinhado dentro do cone do nível (a distância mínima/máxima evita passes
     * ao pé ou impossíveis). Devolve o ângulo original se ninguém estiver no cone ou se o nível for NENHUMA.
     */
    fun ajustarPasse(angulo: Float, alvos: List<Alvo>, nivel: NivelMira, distMin: Float = 4f, distMax: Float = 45f): Float {
        if (nivel.cone <= 0f) return angulo
        var melhor: Float? = null
        var melhorPont = Float.MAX_VALUE
        for (t in alvos) {
            val d = hypot(t.dx, t.dy)
            if (d < distMin || d > distMax) continue
            val a = atan2(t.dy, t.dx)
            val dif = abs(difAngulo(a, angulo))
            if (dif > nivel.cone) continue
            val pont = dif + d * 0.004f   // a igualdade desfaz-se a favor do mais perto
            if (pont < melhorPont) { melhorPont = pont; melhor = a }
        }
        val m = melhor ?: return angulo
        return angulo + difAngulo(m, angulo) * nivel.forca
    }

    /**
     * Ângulo para o canto da baliza longe do guarda-redes. "golX" é o x da baliza que se ataca (+-52,5),
     * "grY" a posição lateral do guarda-redes (ou null se não se sabe); "lado" +1/-1 força o canto.
     */
    fun anguloCanto(x: Float, y: Float, golX: Float, grY: Float?, lado: Int = 0): Float {
        val canto = when {
            lado != 0 -> lado * CANTO_Y
            grY != null && abs(grY) > 0.4f -> if (grY > 0f) -CANTO_Y else CANTO_Y
            else -> if (y > 0f) -CANTO_Y else CANTO_Y   // sem informação: o canto mais afastado do lado em que se está
        }
        return atan2(canto - y, golX - x)
    }

    /** Para onde vai um passe em profundidade: para a baliza, ligeiramente para o lado do joystick. */
    fun anguloProfundidade(sentidoAtaque: Int, movY: Float): Float {
        val base = if (sentidoAtaque >= 0) 0f else PI.toFloat()
        val desvio = movY.coerceIn(-1f, 1f) * 0.35f * (if (sentidoAtaque >= 0) 1f else -1f)
        return base + desvio
    }

    /** Cruzamento: do corredor para a zona do ponto de penálti da baliza que se ataca. */
    fun anguloCruzamento(x: Float, y: Float, golX: Float): Float = atan2(0f - y, (golX - (if (golX > 0f) 11f else -11f)) - x)

    const val CANTO_Y = 2.9f
}
