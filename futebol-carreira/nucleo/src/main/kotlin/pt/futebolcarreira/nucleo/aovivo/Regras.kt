package pt.futebolcarreira.nucleo.aovivo

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// Regras do jogo ao vivo: fora de jogo, vantagem, lances (relato) e períodos (intervalo, prolongamento, fim).

enum class TipoLance {
    FALTA, VANTAGEM, VANTAGEM_ANULADA, FORA_DE_JOGO,
    LANCAMENTO, CANTO, LIVRE, PENALTI, PONTAPE_BALIZA,
    FIM_DOS_90, INICIO_PROLONGAMENTO, INTERVALO_PROLONGAMENTO, FIM_PROLONGAMENTO,
    INICIO_PENALTIS, PENALTI_MARCADO, PENALTI_DEFENDIDO, PENALTI_FORA, FIM_PENALTIS,
}

/**
 * Um acontecimento de regras para o relato. "casa" é a equipa a quem o lance favorece (quem cobra, quem tem a vantagem, quem marca)
 * ou null se não favorece ninguém; "jogadorId" é o id do jogador principal (-1 se não há).
 */
data class Lance(val minuto: Int, val tipo: TipoLance, val casa: Boolean?, val jogadorId: Int, val texto: String)

/**
 * Quando a competição exige vencedor (taças e eliminatórias europeias), o jogo ao vivo desempata: se no fim dos 90 minutos o
 * resultado somado ao que já havia (primeira mão) está empatado, joga-se prolongamento (2 x 15 minutos) e depois penáltis.
 * "golosAntesCasa"/"golosAntesFora" são os golos que a equipa da casa/de fora já tem na eliminatória (0 num jogo único).
 */
class ExigenciaVencedor(val golosAntesCasa: Int = 0, val golosAntesFora: Int = 0)

/** Estado de todas as regras novas (para não encher o MotorAoVivo de campos). */
internal class EstadoRegras {
    // Fora de jogo: quem estava em posição irregular quando a bola foi jogada
    val foraPos = BooleanArray(22)
    var foraEq = -1
    var foraIdade = 0f
    val foraDeJogo = IntArray(2)
    // Vantagem
    var vantagemEq = -1
    var vantagemT = 0f
    var vantagemX = 0f; var vantagemY = 0f
    var vantagemInfrator = -1
    // Períodos
    var periodo = 0
    var prolongamento = false
    var saidaProlongamento = 0
    // Relato
    val lances = ArrayList<Lance>()
    // Defesa
    val linhaDef = FloatArray(2)
    val linhaFJ = FloatArray(2)
    val marca = IntArray(22) { -1 }
    val marcado = BooleanArray(22)
    val contraPressaoT = FloatArray(2)
    var zonaX = 0f; var zonaY = 0f
    // Barreira
    val muro = IntArray(4) { -1 }
    // Penáltis
    var desempate: Desempate? = null
    var mergulhoUser = SEM_MERGULHO

    fun limparForaDeJogo() {
        foraEq = -1; foraIdade = 0f
        java.util.Arrays.fill(foraPos, false)
    }
}

internal fun MotorAoVivo.dif(e: Int): Dificuldade = if (e == eqUser) Dificuldade.NORMAL else dificuldade

internal fun MotorAoVivo.lance(tipo: TipoLance, eqFavorecida: Int, jogador: Int, texto: String) {
    val id = if (jogador in 0 until N) jog[jogador]?.id ?: -1 else -1
    reg.lances += Lance(minutoEvento(), tipo, if (eqFavorecida in 0..1) eqFavorecida == 0 else null, id, texto)
}

// ---------------------------------------------------------------- fora de jogo

/**
 * Linha de fora de jogo para a equipa "e" que ataca (em "u", o eixo de ataque dessa equipa): o mais atrasado dos adversários
 * a seguir ao penúltimo, a bola e o meio-campo. Quem passar desta linha no momento do passe está em posição irregular.
 */
internal fun MotorAoVivo.linhaForaDeJogo(e: Int): Float {
    val s = sinal(e)
    var m1 = -99f; var m2 = -99f
    for (o in (1 - e) * 11 until (1 - e) * 11 + 11) {
        if (!ativo[o]) continue
        val u = px[o] * s
        if (u > m1) { m2 = m1; m1 = u } else if (u > m2) m2 = u
    }
    return max(max(m2, bx * s), 0f)
}

/** Chamado quando "i" larga a bola (passe ou remate): regista quem dos colegas estava em posição de fora de jogo. */
internal fun MotorAoVivo.registarForaDeJogo(i: Int, faseAntes: FaseJogo) {
    val r = reg
    r.limparForaDeJogo()
    if (r.desempate != null) return
    if (faseAntes == FaseJogo.PONTAPE_BALIZA || faseAntes == FaseJogo.LANCAMENTO || faseAntes == FaseJogo.CANTO) return
    val e = eq(i); val s = sinal(e)
    val linha = linhaForaDeJogo(e)
    var algum = false
    for (j in e * 11 until e * 11 + 11) {
        if (j == i || !ativo[j]) continue
        if (px[j] * s > linha + 0.3f) { r.foraPos[j] = true; algum = true }
    }
    if (algum) r.foraEq = e
}

/** Chamado quando "i" vai ficar com a bola. Devolve true se o árbitro apitou fora de jogo (e já preparou o livre). */
internal fun MotorAoVivo.apitarForaDeJogo(i: Int): Boolean {
    val r = reg
    if (r.foraEq < 0) return false
    val e = eq(i)
    if (e != r.foraEq) return false
    if (!r.foraPos[i]) { r.limparForaDeJogo(); return false }
    val x = px[i].coerceIn(-K.MX + 1f, K.MX - 1f); val y = py[i].coerceIn(-K.MY + 1f, K.MY - 1f)
    val nome = jog[i]!!.nome
    r.foraDeJogo[e]++
    lance(TipoLance.FORA_DE_JOGO, 1 - e, i, "Fora de jogo: $nome")
    iniciarReinicio(FaseJogo.LIVRE, 1 - e, x, y)
    mensagem("Fora de jogo: $nome", 2f)
    return true
}

// ---------------------------------------------------------------- vantagem

/**
 * Chamado por uma falta (sem ser na área). Se a equipa que sofreu a falta continua com a bola e a jogada é prometedora,
 * o árbitro deixa seguir. Devolve true se há vantagem (e não há reinício).
 */
internal fun MotorAoVivo.tentarVantagem(inf: Int, vit: Int, x: Float, y: Float): Boolean {
    if (fase != FaseJogo.A_JOGAR || dono != vit || reg.vantagemEq >= 0) return false
    val ve = eq(vit)
    val s = sinal(ve)
    val u = px[vit] * s
    if (u < -12f) return false
    var adiante = 0
    for (o in (1 - ve) * 11 until (1 - ve) * 11 + 11) {
        if (!ativo[o] || papel[o] == 0) continue
        if (px[o] * s > u && hypot(px[o] - px[vit], py[o] - py[vit]) < 25f) adiante++
    }
    val dGol = hypot(K.MX * s - px[vit], py[vit])
    val prometedora = adiante <= 3 || dGol < 25f
    if (!prometedora || rng.nextFloat() > 0.85f) return false
    reg.vantagemEq = ve; reg.vantagemT = 3f
    reg.vantagemX = x; reg.vantagemY = y; reg.vantagemInfrator = inf
    cdDesarme[inf] = 3f
    if (msgT <= 0f) mensagem("Vantagem!", 1.5f)
    lance(TipoLance.VANTAGEM, ve, vit, "Vantagem para ${if (ve == 0) casa.abreviatura else fora.abreviatura}")
    return true
}

private fun MotorAoVivo.vantagemTick(dt: Float) {
    val r = reg
    val ve = r.vantagemEq
    r.vantagemT -= dt
    val perdeu = (dono >= 0 && eq(dono) != ve) || (dono < 0 && ultimoEq >= 0 && ultimoEq != ve)
    if (perdeu) {
        // A vantagem não deu em nada: volta-se à falta.
        val x = r.vantagemX; val y = r.vantagemY
        val inf = r.vantagemInfrator
        r.vantagemEq = -1
        lance(TipoLance.VANTAGEM_ANULADA, ve, -1, "A vantagem não resultou: falta assinalada")
        mensagem("Falta", 1.5f)
        iniciarReinicio(FaseJogo.LIVRE, ve, x, y)
        if (inf >= 0) cdDesarme[inf] = 0f
        return
    }
    if (r.vantagemT <= 0f) r.vantagemEq = -1
}

// ---------------------------------------------------------------- hooks

/** Chamado a cada passo do jogo aberto. */
internal fun MotorAoVivo.tickRegras(dt: Float) {
    val r = reg
    if (r.foraEq >= 0) { r.foraIdade += dt; if (r.foraIdade > 8f) r.limparForaDeJogo() }
    for (e in 0..1) if (r.contraPressaoT[e] > 0f) r.contraPressaoT[e] -= dt
    if (r.vantagemEq >= 0) vantagemTick(dt)
}

/** Chamado no fim de [MotorAoVivo.iniciarReinicio]. */
internal fun MotorAoVivo.aoIniciarReinicio(nova: FaseJogo, e: Int, x: Float, y: Float) {
    val r = reg
    r.limparForaDeJogo()
    r.vantagemEq = -1
    if (r.desempate != null) return
    calcularBarreira(nova, e, x, y)
    val sigla = if (e == 0) casa.abreviatura else fora.abreviatura
    val c = cobrador
    when (nova) {
        FaseJogo.CANTO -> lance(TipoLance.CANTO, e, c, "Canto para $sigla")
        FaseJogo.LANCAMENTO -> lance(TipoLance.LANCAMENTO, e, c, "Lançamento lateral para $sigla")
        FaseJogo.PONTAPE_BALIZA -> lance(TipoLance.PONTAPE_BALIZA, e, c, "Pontapé de baliza de $sigla")
        FaseJogo.LIVRE -> lance(TipoLance.LIVRE, e, c, "Livre para $sigla")
        FaseJogo.PENALTI -> lance(TipoLance.PENALTI, e, c, "Penálti para $sigla")
        else -> {}
    }
}

// ---------------------------------------------------------------- períodos: intervalo, prolongamento, fim

/** Segundos de jogo em que acaba cada período (45', 90', 105', 120'). */
internal fun MotorAoVivo.limiteDoPeriodo(): Float = when (reg.periodo) { 0 -> 2700f; 1 -> 5400f; 2 -> 6300f; else -> 7200f }

/** true se, somando o que já havia na eliminatória, o jogo está empatado e a competição exige vencedor. */
internal fun MotorAoVivo.empatadoNaEliminatoria(): Boolean {
    val x = exigencia ?: return false
    return golos[0] + x.golosAntesCasa == golos[1] + x.golosAntesFora
}

/** Fim de um período de jogo: intervalo, início do prolongamento, penáltis ou fim. */
internal fun MotorAoVivo.acabarPeriodo() {
    val r = reg
    when (r.periodo) {
        0 -> {
            relogio = 2700f
            fase = FaseJogo.INTERVALO
            tFase = 0f
            mensagem("Intervalo: ${golos[0]}-${golos[1]}", 3f)
        }
        1 -> if (empatadoNaEliminatoria()) {
            relogio = 5400f
            r.prolongamento = true
            fase = FaseJogo.INTERVALO
            tFase = 0f
            lance(TipoLance.FIM_DOS_90, -1, -1, "Fim dos 90 minutos: ${golos[0]}-${golos[1]}. Há prolongamento")
            mensagem("Prolongamento!", 3f)
        } else terminarJogo()
        2 -> {
            relogio = 6300f
            fase = FaseJogo.INTERVALO
            tFase = 0f
            lance(TipoLance.INTERVALO_PROLONGAMENTO, -1, -1, "Intervalo do prolongamento: ${golos[0]}-${golos[1]}")
            mensagem("Intervalo do prolongamento: ${golos[0]}-${golos[1]}", 3f)
        }
        else -> if (empatadoNaEliminatoria()) {
            relogio = 7200f
            lance(TipoLance.FIM_PROLONGAMENTO, -1, -1, "Fim do prolongamento: ${golos[0]}-${golos[1]}. Há penáltis")
            ultimoMinuto = max(120, minutoAtual() + (if (relogio % 60f > 0f) 1 else 0))
            iniciarDesempate()
        } else terminarJogo()
    }
}

internal fun MotorAoVivo.terminarJogo() {
    ultimoMinuto = max(if (reg.prolongamento) 120 else 90, minutoAtual() + (if (relogio % 60f > 0f) 1 else 0))
    fase = FaseJogo.FIM
    mensagem("Fim do jogo: ${golos[0]}-${golos[1]}", 5f)
}

/** Depois do intervalo (3 s): começa o período seguinte. */
internal fun MotorAoVivo.iniciarPeriodoSeguinte() {
    val r = reg
    r.periodo++
    segundaParte = r.periodo % 2 == 1
    excedente = 0f
    for (i in 0 until N) stam[i] = min(100f, stam[i] + (if (r.periodo == 1) 15f else 8f))
    val e = when (r.periodo) {
        1 -> 1
        2 -> { r.saidaProlongamento = rng.nextInt(2); r.saidaProlongamento }
        else -> 1 - r.saidaProlongamento
    }
    if (r.periodo == 2) {
        lance(TipoLance.INICIO_PROLONGAMENTO, -1, -1, "Começa o prolongamento")
        mensagem("Começa o prolongamento", 2.5f)
    }
    iniciarReinicio(FaseJogo.PONTAPE_SAIDA, e, 0f, 0f)
}

/** O minuto que aparece nos relatos, conforme o período. */
internal fun MotorAoVivo.minutoDoPeriodo(m: Int): Int = when (reg.periodo) {
    0 -> min(m, 45)
    1 -> if (exigencia != null) m.coerceIn(46, 90) else max(m, 46)
    2 -> m.coerceIn(91, 105)
    else -> max(m, 106)
}
