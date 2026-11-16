package pt.futebolcarreira.nucleo.aovivo

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// Inteligência defensiva (E3): linha defensiva que sobe e desce, marcação, pressão com contenção e contra-pressão.
// A dificuldade (Dificuldade.kt) regula a altura da linha, o raio de pressão, a marcação e a compactação.

/** A 10 Hz: calcula a linha defensiva e a de fora de jogo de cada equipa e distribui as marcações de quem defende. */
internal fun MotorAoVivo.prepararDefesa() {
    val r = reg
    for (e in 0..1) {
        val d = dif(e); val s = sinal(e)
        val ballU = bx * s
        r.linhaDef[e] = (-26f + ballU * 0.33f + mentShift[e] + d.linhaDefensiva).coerceIn(-40f, 8f)
        r.linhaFJ[e] = linhaForaDeJogo(e)
    }
    java.util.Arrays.fill(r.marca, -1)
    java.util.Arrays.fill(r.marcado, false)
    if (fase != FaseJogo.A_JOGAR) return
    val pe = posseEq()
    for (e in 0..1) {
        if (pe == e) continue   // quem tem a bola não marca
        val d = dif(e); val s = sinal(e)
        val raio = 7f + 8f * d.marcacao
        for (p in 1..2) {
            for (i in e * 11 until e * 11 + 11) {
                if (!ativo[i] || papel[i] != p || i == presser1[e] || i == presser2[e] || i == controlado || i == receptor || atordoado[i] > 0f) continue
                zonaDefensiva(i, e, s)
                var melhor = -1; var md = raio * raio
                for (o in (1 - e) * 11 until (1 - e) * 11 + 11) {
                    if (!ativo[o] || papel[o] == 0 || r.marcado[o] || o == dono) continue
                    val dd = (px[o] - r.zonaX) * (px[o] - r.zonaX) + (py[o] - r.zonaY) * (py[o] - r.zonaY)
                    if (dd < md) { md = dd; melhor = o }
                }
                if (melhor >= 0) { r.marca[i] = melhor; r.marcado[melhor] = true }
            }
        }
    }
}

/** O ponto da "zona" de um jogador a defender (deixa o resultado em reg.zonaX/zonaY). */
private fun MotorAoVivo.zonaDefensiva(i: Int, e: Int, s: Float) {
    val r = reg
    val d = dif(e)
    val linha = r.linhaDef[e]
    val ballU = bx * s; val ballV = by * s
    val u = when (papel[i]) {
        1 -> (linha + (baseU[i] + 31f) * 0.3f).coerceIn(-50f, 14f)
        2 -> max(linha + 5f, min(linha + 13f + (baseU[i] + 6f) * 0.5f, ballU + 10f))
        else -> (baseU[i] + (ballU * 0.38f - 5f + mentShift[e])).coerceIn(-40f, 47f)
    }
    val c = d.compactacao
    val v = (baseV[i] * (0.85f - 0.3f * c) + ballV * (0.25f + 0.15f * c)).coerceIn(-31f, 31f)
    r.zonaX = u * s; r.zonaY = v * s
}

/** Posição de quem defende e não pressiona: a sua zona, puxada para o adversário que marca (do lado da própria baliza). */
internal fun MotorAoVivo.posicaoDefensiva(i: Int, e: Int, s: Float) {
    val r = reg
    val d = dif(e)
    zonaDefensiva(i, e, s)
    var ax = r.zonaX; var ay = r.zonaY
    val o = r.marca[i]
    if (o >= 0) {
        val dx = -K.MX * s - px[o]; val dy = -py[o]
        val n = max(1f, hypot(dx, dy))
        val mx = px[o] + dx / n * 2.2f; val my = py[o] + dy / n * 2.2f
        val w = d.marcacao
        ax = ax * (1f - w) + mx * w
        ay = ay * (1f - w) + my * w
        // Os defesas não saem da linha mais de 6 m: seguram-na e confiam no fora de jogo.
        if (papel[i] == 1) ax = max(ax * s, reg.linhaDef[e] - 6f) * s
    }
    alvoX[i] = ax; alvoY[i] = ay
    alvoSprint[i] = distJ(i, ax, ay) > (if (o >= 0) 7f else 12f)
}

/** Quem pressiona o portador: aproxima-se do lado da própria baliza (contenção) e, perto, vai à bola. */
internal fun MotorAoVivo.pressionarPortador(i: Int, e: Int, s: Float) {
    val c = dono
    val lx = bx + vx[c] * 0.25f; val ly = by + vy[c] * 0.25f
    val dd = distJ(i, lx, ly)
    if (dd > 4.5f) {
        val dx = -K.MX * s - lx; val dy = -ly
        val n = max(1f, hypot(dx, dy))
        alvoX[i] = lx + dx / n * 1.8f; alvoY[i] = ly + dy / n * 1.8f
    } else {
        alvoX[i] = lx; alvoY[i] = ly
    }
    alvoSprint[i] = dd > 2.5f && dd < 32f
    if (dd > 1.3f && dd < 2.6f && carrinho[i] <= 0f && atordoado[i] <= 0f && cdDesarme[i] <= 0f && rng.nextFloat() < dif(e).carrinho) {
        iniciarCarrinho(i, false)
    }
}

/** Pressão logo a seguir a perder a bola: os mais próximos fecham o portador. */
internal fun MotorAoVivo.contraPressionar(i: Int, s: Float) {
    val dx = -K.MX * s - bx; val dy = -by
    val n = max(1f, hypot(dx, dy))
    alvoX[i] = bx + dx / n * 3f; alvoY[i] = by + dy / n * 3f
    alvoSprint[i] = true
}
