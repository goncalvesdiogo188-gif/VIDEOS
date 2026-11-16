package pt.futebolcarreira.nucleo.aovivo

import pt.futebolcarreira.nucleo.modelos.Equipa
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

// SUBSTITUÍDO PELA PARTE A NA JUNÇÃO (serve só para desenvolver e testar o ecrã 3D).
/** Motor falso: jogadores à volta das posições da formação, bola a passar entre eles e um golo de vez em quando. */
class PartidaFalsa(
    casa: Equipa, fora: Equipa, private val utilizadorCasa: Boolean,
    private val rng: Random = Random(1), private val duracaoReal: Float = 480f,
) : PartidaAoVivo {
    private class Fig(val casa: Boolean, val indice: Int, val pos: String, val nome: String, val bx: Float, val by: Float, val fase: Float) {
        var x = bx
        var y = by
        var dir = if (casa) 0f else PI.toFloat()
        var anim = Animacao.PARADO
        var gesto = 0f   // segundos que faltam para acabar um passe ou remate
    }

    // Posições base (fração do comprimento a partir da própria baliza, fração da largura) de um 4-4-2.
    private val base = listOf(
        0.04f to 0f, 0.2f to -0.7f, 0.17f to -0.25f, 0.17f to 0.25f, 0.2f to 0.7f,
        0.45f to -0.7f, 0.4f to -0.25f, 0.4f to 0.25f, 0.45f to 0.7f, 0.7f to -0.2f, 0.72f to 0.2f,
    )
    private val posicoes = listOf("GR", "DD", "DC", "DC", "DE", "MD", "MC", "MC", "ME", "PL", "PL")
    private val figuras = ArrayList<Fig>()
    private var tempo = 0f
    private var golosC = 0
    private var golosF = 0
    private var minuto = 0
    private var bolaX = 0f
    private var bolaY = 0f
    private var bolaZ = 0f
    private var alvo = 10
    private var posse = 9
    private var faseAtual = FaseJogo.PONTAPE_SAIDA
    private var msg: String? = null
    private var msgAte = 0f
    private var festejoAte = 0f
    private var ultimoGolo = true
    private val cc = cor(casa.cores.getOrNull(0), 0xFFD32F2F)
    private val cs = cor(casa.cores.getOrNull(1), 0xFFFFFFFF)
    private val fc = cor(fora.cores.getOrNull(0), 0xFF1976D2)
    private val fs = cor(fora.cores.getOrNull(1), 0xFFFFFFFF)
    private val sigC = casa.abreviatura.ifEmpty { casa.nome.take(3).uppercase() }
    private val sigF = fora.abreviatura.ifEmpty { fora.nome.take(3).uppercase() }
    private var controlado = 9   // índice em "figuras"

    override var terminou = false
        private set
    override var estado: EstadoPartida
        private set

    init {
        for ((lado, eq) in listOf(true to casa, false to fora)) {
            val onze = eq.plantel.take(11)
            val sinal = if (lado) 1f else -1f
            for (i in 0 until 11) {
                val (fx, fy) = base[i]
                figuras += Fig(lado, i, posicoes[i], onze.getOrNull(i)?.nome ?: "Jogador ${i + 1}", (-52.5f + fx * 105f) * sinal, fy * 30f * sinal, rng.nextFloat() * 6f)
            }
        }
        controlado = if (utilizadorCasa) 9 else 20
        estado = fotografia(false)
    }

    private fun cor(hex: String?, padrao: Long): Long =
        try { 0xFF000000L or hex!!.removePrefix("#").toLong(16) } catch (e: Exception) { padrao }

    override fun passo(dt: Float, comando: Comando) {
        if (terminou) return
        tempo += dt
        val minutosTotais = 90f * tempo / duracaoReal
        minuto = minutosTotais.toInt().coerceAtMost(90)
        val segunda = minutosTotais >= 45f
        if (minutosTotais >= 90f) {
            faseAtual = FaseJogo.FIM
            terminou = true
            msg = "Fim do jogo"
            estado = fotografia(true)
            return
        }
        faseAtual = when {
            tempo < festejoAte -> FaseJogo.GOLO
            tempo < 1.5f -> FaseJogo.PONTAPE_SAIDA
            else -> FaseJogo.A_JOGAR
        }
        if (tempo > msgAte) msg = null

        // A bola vai de um jogador para outro; de vez em quando é golo.
        val destino = figuras[alvo]
        val dx = destino.x - bolaX
        val dy = destino.y - bolaY
        val d = hypot(dx, dy)
        val v = 18f * dt
        if (d < v + 0.3f) {
            val dono = figuras[alvo]
            posse = alvo
            val mesmoLado = rng.nextFloat() > 0.3f
            val lado = if (mesmoLado == dono.casa) 0 else 11
            alvo = lado + rng.nextInt(11)
            if (rng.nextFloat() < 0.04f && tempo > 3f && festejoAte < tempo) golo(dono.casa)
        } else {
            bolaX += dx / d * v
            bolaY += dy / d * v
        }
        bolaZ = if (d > 8f) 0.4f + 2.5f * sin(PI.toFloat() * (d % 20f) / 20f) else 0f

        for ((i, f) in figuras.withIndex()) {
            if (f.gesto > 0f) f.gesto -= dt
            val sinal = if (f.casa xor segunda) 1f else -1f
            val puxaX = ((bolaX - f.bx * sinal) * 0.25f).coerceIn(-10f, 10f)
            val puxaY = ((bolaY - f.by * sinal) * 0.15f).coerceIn(-6f, 6f)
            val tx = f.bx * sinal + (if (f.pos == "GR") 0f else puxaX) + cos(tempo * 0.7f + f.fase) * 2f
            val ty = f.by * sinal + puxaY + sin(tempo * 0.9f + f.fase) * 2f
            if (i == controlado && (comando.movX != 0f || comando.movY != 0f)) {
                val vel = if (comando.sprint) 9f else 6f
                f.x = (f.x + comando.movX * vel * dt).coerceIn(-52f, 52f)
                f.y = (f.y + comando.movY * vel * dt).coerceIn(-33f, 33f)
                f.dir = atan2(comando.movY, comando.movX)
                f.anim = if (comando.sprint) Animacao.SPRINT else Animacao.CORRER
            } else {
                val ex = tx - f.x
                val ey = ty - f.y
                val e = hypot(ex, ey)
                if (e > 0.4f) {
                    val vel = minOf(e * 1.2f, 6.5f)
                    f.x += ex / e * vel * dt
                    f.y += ey / e * vel * dt
                    f.dir = atan2(ey, ex)
                    f.anim = if (vel > 5f) Animacao.SPRINT else Animacao.CORRER
                } else f.anim = Animacao.PARADO
            }
            if (festejoAte > tempo && f.casa == ultimoGolo) f.anim = Animacao.FESTEJAR
        }
        val meu = figuras[controlado]
        if (comando.remate) { meu.gesto = 0.4f }
        if (comando.passe) { meu.gesto = 0.4f; meu.anim = Animacao.PASSAR }
        if (comando.remate) meu.anim = Animacao.REMATAR
        else if (meu.gesto > 0f && meu.anim != Animacao.PASSAR) meu.anim = Animacao.REMATAR
        estado = fotografia(segunda)
    }

    private fun golo(casa: Boolean) {
        if (casa) golosC++ else golosF++
        ultimoGolo = casa
        festejoAte = tempo + 3f
        msgAte = tempo + 3f
        msg = "GOLO! ${if (casa) sigC else sigF} $minuto'"
        bolaX = 0f; bolaY = 0f; bolaZ = 0f
        alvo = if (casa) 11 + 9 else 9
    }

    private fun fotografia(segunda: Boolean): EstadoPartida {
        val lista = ArrayList<JogadorEmCampo>(22)
        for ((i, f) in figuras.withIndex()) {
            lista += JogadorEmCampo(if (f.casa) f.indice else 100 + f.indice, f.casa, f.indice + 1, f.pos, f.nome, f.x, f.y, f.dir, f.anim, i == controlado)
        }
        return EstadoPartida(
            minuto, (90f * tempo / duracaoReal * 60f) % 60f, golosC, golosF, faseAtual, segunda, lista,
            Bola(bolaX, bolaY, bolaZ), cc to cs, fc to fs, sigC, sigF, msg,
        )
    }
}
