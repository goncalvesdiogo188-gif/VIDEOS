package pt.futebolcarreira.nucleo.aovivo

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

// Bolas paradas jogáveis (livres, cantos, lançamentos, pontapés de baliza e penáltis): estado, comando e execução.
// Ver LEIAME.md para o resumo da API.

/** Segundos que o utilizador tem para cobrar um lance parado antes de o "adjunto" (a IA) o cobrar por ele. */
const val TEMPO_PARADA_UTILIZADOR = 10f

enum class TipoParada { PONTAPE_SAIDA, PONTAPE_BALIZA, CANTO, LIVRE, LANCAMENTO, PENALTI }

/** O que fazer com a bola num lance parado. [AUTO] escolhe: remate num livre perto da baliza ou num penálti, cruzamento num canto, passe nos outros. */
enum class AcaoParada { AUTO, PASSE, CRUZAR, REMATAR }

/**
 * Um lance parado à espera de ser cobrado. Existe em [EstadoPartida.bolaParada] enquanto a fase for
 * PONTAPE_SAIDA, PONTAPE_BALIZA, CANTO, LIVRE, LANCAMENTO ou PENALTI.
 * Os eixos são os do campo: x ao longo do campo (-52,5 a 52,5), y na largura (-34 a 34).
 */
data class BolaParada(
    val tipo: TipoParada,
    /** true se quem cobra é da equipa da casa. */
    val casa: Boolean,
    val x: Float, val y: Float,
    /** [Jogador.id][pt.futebolcarreira.nucleo.modelos.Jogador.id] de quem cobra (o mesmo id de [JogadorEmCampo.id]). */
    val cobradorId: Int,
    val cobradorNumero: Int,
    /** true se quem cobra é da equipa do utilizador: o utilizador decide com [PartidaAoVivo.executarParada]. */
    val doUtilizador: Boolean,
    /** Segundos que faltam para a IA cobrar por ele (só se [doUtilizador]; senão -1). */
    val tempoRestante: Float,
    /** Distância à baliza que quem cobra ataca. */
    val distanciaBaliza: Float,
    /** Direção (radianos, 0 = +x) da bola para o centro dessa baliza. */
    val direcaoBaliza: Float,
    /** true se faz sentido rematar (penálti, ou livre a menos de 32 m da baliza). */
    val podeRematar: Boolean,
    /** true se há barreira (livre perto da baliza). */
    val barreira: Boolean,
    /** true se é um penálti de desempate. */
    val desempate: Boolean,
    /** true se o guarda-redes que defende é do utilizador (penálti do adversário): usar [PartidaAoVivo.escolherMergulho]. */
    val guardaRedesDoUtilizador: Boolean,
)

/**
 * Comando para cobrar um lance parado. Todos os campos têm valor por omissão.
 * - livres, cantos, lançamentos, pontapés de baliza: "direcao" (radianos, 0 = +x; null = para a baliza adversária, ou para a área num canto),
 *   "forca" 0..1 (distância/velocidade), "acao". "colegaId" (id do jogador) passa diretamente a esse colega.
 * - remates (livre direto ou penálti): "mira" -1..1 é a posição na baliza (o eixo y do campo; -1 = perto do poste do lado -y,
 *   0 = centro, 1 = perto do poste do lado +y) e "altura" 0..1 (rasteiro a ao ângulo). Força demais tira pontaria.
 */
data class ComandoParada(
    val acao: AcaoParada = AcaoParada.AUTO,
    val direcao: Float? = null,
    val forca: Float = 0.5f,
    val colegaId: Int = -1,
    val mira: Float = 0f,
    val altura: Float = 0.3f,
)

internal const val SEM_MERGULHO = 99

internal fun MotorAoVivo.construirBolaParada(): BolaParada? {
    val tipo = when (fase) {
        FaseJogo.PONTAPE_SAIDA -> TipoParada.PONTAPE_SAIDA
        FaseJogo.PONTAPE_BALIZA -> TipoParada.PONTAPE_BALIZA
        FaseJogo.CANTO -> TipoParada.CANTO
        FaseJogo.LIVRE -> TipoParada.LIVRE
        FaseJogo.LANCAMENTO -> TipoParada.LANCAMENTO
        FaseJogo.PENALTI -> TipoParada.PENALTI
        else -> return null
    }
    val c = cobrador
    if (c < 0 || !ativo[c]) return null
    val e = eq(c)
    val gx = K.MX * sinal(e)
    val dG = hypot(gx - bx, by)
    val user = e == eqUser
    return BolaParada(
        tipo, e == 0, bx, by, jog[c]!!.id, numero[c], user,
        if (user) max(0f, TEMPO_PARADA_UTILIZADOR - (if (cobChegou) tCobranca else 0f)) else -1f,
        dG, atan2(-by, gx - bx),
        podeRematar = tipo == TipoParada.PENALTI || (tipo == TipoParada.LIVRE && dG < 32f),
        barreira = tipo == TipoParada.LIVRE && reg.muro[0] >= 0,
        desempate = reg.desempate != null,
        guardaRedesDoUtilizador = tipo == TipoParada.PENALTI && eqUser >= 0 && e != eqUser,
    )
}

// ---------------------------------------------------------------- barreira

/** Põe a barreira nos livres perto da baliza: os defesas mais perto do ponto a 9,15 m da bola na linha da baliza. */
internal fun MotorAoVivo.calcularBarreira(nova: FaseJogo, e: Int, x: Float, y: Float) {
    java.util.Arrays.fill(reg.muro, -1)
    if (nova != FaseJogo.LIVRE) return
    val gx = K.MX * sinal(e)
    val d = hypot(gx - x, y)
    if (d > 32f || d < 11f) return
    val n = if (d < 20f) 4 else if (d < 26f) 3 else 2
    val g = 1 - e
    val px0 = x + (gx - x) / d * 9.2f; val py0 = y + (0f - y) / d * 9.2f
    val cand = (g * 11 until g * 11 + 11).filter { ativo[it] && papel[it] != 0 }.sortedBy { hypot(px[it] - px0, py[it] - py0) }.take(n)
    for ((k, i) in cand.withIndex()) reg.muro[k] = i
}

/** Ponto onde o jogador "i" deve estar na barreira, ou null se não faz parte dela. */
internal fun MotorAoVivo.pontoDaBarreira(i: Int): Pair<Float, Float>? {
    val k = reg.muro.indexOf(i)
    if (k < 0) return null
    val n = reg.muro.count { it >= 0 }
    val e = eqReinicio
    val gx = K.MX * sinal(e)
    val d = max(1f, hypot(gx - bx, by))
    val ux = (gx - bx) / d; val uy = -by / d
    val off = (k - (n - 1) / 2f) * 0.75f
    return (bx + ux * 9.2f - uy * off) to (by + uy * 9.2f + ux * off)
}

// ---------------------------------------------------------------- execução pelo utilizador

/** Implementa [PartidaAoVivo.executarParada]. */
internal fun MotorAoVivo.executarParadaUtilizador(cmd: ComandoParada): Boolean {
    if (reg.desempate != null && reg.desempate!!.estado != EstadoChute.PREPARAR) return false
    val k = cobrador
    if (k < 0 || !ativo[k] || eqUser < 0 || eq(k) != eqUser || !cobChegou || tCobranca < 0.4f) return false
    val f = cmd.forca.coerceIn(0f, 1f)
    when (fase) {
        FaseJogo.PENALTI -> penaltiUtilizador(k, cmd.mira, cmd.altura, f)
        FaseJogo.LIVRE, FaseJogo.CANTO, FaseJogo.LANCAMENTO, FaseJogo.PONTAPE_BALIZA, FaseJogo.PONTAPE_SAIDA -> {
            val s = sinal(eq(k))
            val gx = K.MX * s
            val dG = hypot(gx - bx, by)
            var acao = cmd.acao
            if (acao == AcaoParada.AUTO) {
                acao = when (fase) {
                    FaseJogo.CANTO -> AcaoParada.CRUZAR
                    FaseJogo.LIVRE -> if (dG < 28f) AcaoParada.REMATAR else AcaoParada.PASSE
                    else -> AcaoParada.PASSE
                }
            }
            if ((acao == AcaoParada.REMATAR) && fase != FaseJogo.LIVRE && fase != FaseJogo.CANTO) acao = AcaoParada.PASSE
            val dir = cmd.direcao ?: if (fase == FaseJogo.CANTO) atan2(-by, gx - s * 11f - bx) else atan2(-by, gx - bx)
            when (acao) {
                AcaoParada.REMATAR -> remateDeParada(k, cmd.mira, cmd.altura, f)
                AcaoParada.CRUZAR -> cruzamentoUtilizador(k, dir, f, cmd.colegaId)
                else -> passeDeParada(k, dir, f, cmd.colegaId, cmd.direcao != null)
            }
        }
        else -> return false
    }
    return true
}

private fun MotorAoVivo.indiceDoColega(id: Int, e: Int, excluir: Int): Int {
    if (id == -1) return -1
    for (j in e * 11 until e * 11 + 11) if (j != excluir && ativo[j] && jog[j]?.id == id) return j
    return -1
}

private fun MotorAoVivo.passeDeParada(k: Int, dir: Float, f: Float, colegaId: Int, direcaoPedida: Boolean) {
    val e = eq(k)
    val j = indiceDoColega(colegaId, e, k)
    if (j >= 0) {
        passar(k, j, f > 0.62f || hypot(px[j] - bx, py[j] - by) > 34f)
        return
    }
    val salvaX = comX; val salvaY = comY; val salvaF = cmdForca
    comX = cos(dir); comY = sin(dir); cmdForca = f
    passeUtilizador(k)
    comX = salvaX; comY = salvaY; cmdForca = salvaF
}

private fun MotorAoVivo.cruzamentoUtilizador(k: Int, dir: Float, f: Float, colegaId: Int) {
    val e = eq(k)
    val j = indiceDoColega(colegaId, e, k)
    if (j >= 0) { passar(k, j, true); return }
    val dist = 10f + 32f * f
    val tx = (bx + cos(dir) * dist).coerceIn(-K.MX + 1f, K.MX - 1f); val ty = (by + sin(dir) * dist).coerceIn(-K.MY + 1f, K.MY - 1f)
    var melhor = -1; var md = 7f
    for (c in e * 11 until e * 11 + 11) {
        if (c == k || !ativo[c] || papel[c] == 0) continue
        val d = hypot(px[c] - tx, py[c] - ty)
        if (d < md) { md = d; melhor = c }
    }
    val ajuda = 1f - 0.5f * dificuldade.ajudaMira
    val sigma = (0.012f + (100f - aPasse[k]) * 0.0012f) * ajuda
    val t = 0.6f + hypot(tx - bx, ty - by) / 28f
    receptor = -1
    chutarAlto(k, tx, ty, t, sigma)
    if (melhor >= 0) receptor = melhor
    registarPasse(k)
}

/** Remate num livre direto: "mira" (-1..1) escolhe o lado, "altura" (0..1) a altura. */
private fun MotorAoVivo.remateDeParada(k: Int, mira: Float, altura: Float, f: Float) {
    val ajuda = 1f - 0.5f * dificuldade.ajudaMira
    val aim = mira.coerceIn(-1f, 1f) * 3.0f
    val z = 0.2f + altura.coerceIn(0f, 1f) * 2.0f
    val v = 17f + 14f * f * (0.6f + 0.4f * aRemate[k] / 100f)
    val dG = hypot(K.MX * sinal(eq(k)) - bx, by)
    val sigma = ((0.016f + (100f - aRemate[k]) * 0.0009f) * (0.8f + 0.9f * f) + dG * 0.0008f) * ajuda
    rematar(k, aim, z, v, sigma)
}

// ---------------------------------------------------------------- penáltis

/** Penálti do utilizador: mira (-1..1), altura (0..1) e força (0..1). */
internal fun MotorAoVivo.penaltiUtilizador(c: Int, mira: Float, altura: Float, forca: Float) {
    val ajuda = 1f - 0.5f * dificuldade.ajudaMira
    val aim = mira.coerceIn(-1f, 1f) * 3.2f
    val z = 0.15f + altura.coerceIn(0f, 1f) * 2.0f
    val v = 18f + 10f * forca
    val sigma = (0.012f + (100f - aRemate[c]) * 0.0004f + max(0f, forca - 0.8f) * 0.1f) * ajuda
    executarPenalti(c, aim, z, v, sigma)
}

/** Penálti com os controlos antigos: o joystick (cima/baixo) escolhe o lado e a força dá a velocidade. */
internal fun MotorAoVivo.penaltiComJoystick(c: Int) {
    val mira = if (abs(comY) > 0.3f) sign(comY) * (0.7f + rng.nextFloat() * 0.25f) else (rng.nextFloat() - 0.5f) * 1.2f
    penaltiUtilizador(c, mira, 0.2f + rng.nextFloat() * 0.5f, cmdForca)
}

/** Penálti da IA: escolhe um canto (mais certeiro e mais forte com bons rematadores). */
internal fun MotorAoVivo.cobrarPenaltiIA(c: Int) {
    val d = dif(eq(c))
    val lado = if (rng.nextBoolean()) 1f else -1f
    val sigma = (0.02f + (100f - aRemate[c]) * 0.0004f) * d.erroRemate
    executarPenalti(c, lado * (2.0f + rng.nextFloat() * 1.0f), 0.3f + rng.nextFloat() * 1.3f, 21f + 5f * aRemate[c] / 100f, sigma)
}

/** Remata o penálti e decide para onde se lança o guarda-redes. Nos penáltis de desempate não conta como remate nem golo do jogo. */
internal fun MotorAoVivo.executarPenalti(c: Int, aimY: Float, z: Float, v: Float, sigma: Float) {
    val e = eq(c)
    val d = reg.desempate
    val r0 = remates[e]; val n0 = noAlvo[e]
    val p = parte[c]; val pr0 = p?.remates ?: 0; val pn0 = p?.noAlvo ?: 0
    rematar(c, aimY, z, v, sigma)
    decidirMergulho(c, aimY)
    if (d != null) {
        remates[e] = r0; noAlvo[e] = n0
        if (p != null) { p.remates = pr0; p.noAlvo = pn0 }
        fase = FaseJogo.PENALTI
        cobrador = -1
        d.estado = EstadoChute.EM_VOO
        d.tVoo = 0f; d.defendeu = false; d.trave = false; d.cobradorChute = c
        tFase = 0f
    }
}

private fun MotorAoVivo.decidirMergulho(taker: Int, aimY: Float) {
    val g = 1 - eq(taker)
    val gr = (g * 11 until g * 11 + 11).firstOrNull { ativo[it] && papel[it] == 0 } ?: return
    val ms = reg.mergulhoUser
    if (g == eqUser && ms != SEM_MERGULHO) {
        gkAlvoY[g] = ms.coerceIn(-1, 1) * 2.3f
    } else {
        val pCerta = (0.40f + 0.15f * aGR[gr] / 100f + dif(g).bonusGuardaRedes * 0.8f).coerceIn(0.25f, 0.65f)
        val r = rng.nextFloat()
        gkAlvoY[g] = when {
            r < 0.07f -> 0f
            r < 0.07f + 0.93f * pCerta -> sign(aimY) * 2.3f
            else -> -sign(aimY) * 2.3f
        }
    }
    if (g == eqUser) reg.mergulhoUser = SEM_MERGULHO
    gkAtraso[g] = 0f
    gkAtivo[g] = true
}
