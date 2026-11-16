package pt.futebolcarreira.nucleo.aovivo

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

// Desempate por penáltis ao vivo: série de 5 e morte súbita. Cada chute usa a física normal (bola, poste, guarda-redes),
// mas não conta como golo nem remate do jogo: só o marcador dos penáltis ("penaltisCasa"/"penaltisFora" do relatório).

/** Fotografia da marcação de penáltis de desempate, em [EstadoPartida.desempate]. */
data class EstadoDesempate(
    val golosCasa: Int, val golosFora: Int,
    /** Resultado de cada chute da equipa da casa/de fora, por ordem (true = marcou). */
    val chutesCasa: List<Boolean>, val chutesFora: List<Boolean>,
    /** true se a próxima marcação é da equipa da casa (ou, se [decidido], a que marcou o último). */
    val vezCasa: Boolean,
    /** true depois dos cinco primeiros chutes de cada equipa. */
    val mortaSubita: Boolean,
    val decidido: Boolean,
)

internal enum class EstadoChute { INTRO, PREPARAR, EM_VOO, PAUSA }

internal class Desempate(val golo: Float, val comecaEq: Int) {
    var estado = EstadoChute.INTRO
    val marcados = IntArray(2)
    val chutes = IntArray(2)
    val resultados = arrayOf(ArrayList<Boolean>(), ArrayList<Boolean>())
    var vez = comecaEq
    var ordem: Array<IntArray> = arrayOf(IntArray(0), IntArray(0))
    val indice = IntArray(2)
    var tVoo = 0f
    var defendeu = false
    var trave = false
    var cobradorChute = -1
    var decidido = false

    fun proximoDaEquipa(e: Int): Int {
        val o = ordem[e]
        val k = o[indice[e] % o.size]
        indice[e]++
        return k
    }

    /** Já não há como virar (ou, na morte súbita, a ronda acabou com marcadores diferentes). */
    fun estaDecidido(): Boolean {
        val c0 = chutes[0]; val c1 = chutes[1]
        if (max(c0, c1) <= 5) {
            val r0 = 5 - c0; val r1 = 5 - c1
            return marcados[0] > marcados[1] + r1 || marcados[1] > marcados[0] + r0
        }
        return c0 == c1 && marcados[0] != marcados[1]
    }
}

internal fun MotorAoVivo.estadoDoDesempate(): EstadoDesempate? {
    val d = reg.desempate ?: return null
    return EstadoDesempate(
        d.marcados[0], d.marcados[1], d.resultados[0].toList(), d.resultados[1].toList(),
        d.vez == 0, max(d.chutes[0], d.chutes[1]) >= 5, d.decidido,
    )
}

/** Começa a marcação de penáltis (fim do prolongamento empatado). */
internal fun MotorAoVivo.iniciarDesempate() {
    val g = if (rng.nextBoolean()) 1f else -1f
    val d = Desempate(g, rng.nextInt(2))
    reg.desempate = d
    for (e in 0..1) {
        val lista = (e * 11 until e * 11 + 11).filter { ativo[it] }
        val marcadores = lista.filter { papel[it] != 0 }.sortedByDescending { aRemate[it] + (if (papel[it] == 3) 4f else 0f) }
        d.ordem[e] = (marcadores + lista.filter { papel[it] == 0 }).toIntArray()
        if (d.ordem[e].isEmpty()) d.ordem[e] = intArrayOf(e * 11)
    }
    // Todos vão para o círculo central; os guarda-redes vão para as balizas quando for preciso.
    var a = 0; var b = 0
    for (i in 0 until N) {
        if (!ativo[i]) continue
        val n = if (eq(i) == 0) a++ else b++
        px[i] = (if (eq(i) == 0) -2.5f else 2.5f) * (if (n % 2 == 0) 1f else 1.4f); py[i] = (n - 5) * 1.6f
        vx[i] = 0f; vy[i] = 0f
        alvoX[i] = px[i]; alvoY[i] = py[i]; alvoSprint[i] = false
        atordoado[i] = 0f; carrinho[i] = 0f
    }
    dono = -1; receptor = -1
    bx = 0f; by = 0f; bz = 0f; bvx = 0f; bvy = 0f; bvz = 0f
    controlado = -1
    cobrador = -1
    fase = FaseJogo.INTERVALO
    tFase = 0f
    lance(TipoLance.INICIO_PENALTIS, -1, -1, "Penáltis para decidir o jogo")
    mensagem("Penáltis!", 3f)
}

/** Um passo da marcação de penáltis (substitui o ciclo normal). */
internal fun MotorAoVivo.passoDesempate(dt: Float) {
    val d = reg.desempate!!
    temporizadores(dt)
    when (d.estado) {
        EstadoChute.INTRO -> if (tFase >= 2.5f) proximoChute()
        EstadoChute.PREPARAR -> {
            val k = cobrador
            if (k < 0 || !ativo[k]) { proximoChute() } else if (eq(k) == eqUser) {
                if ((osRemate || osPasse) && tFase >= 0.4f) { osRemate = false; osPasse = false; penaltiComJoystick(k) }
                else if (tFase > TEMPO_PARADA_UTILIZADOR) cobrarPenaltiIA(k)
            } else if (tFase >= espera) cobrarPenaltiIA(k)
        }
        EstadoChute.EM_VOO -> {
            d.tVoo += dt
            val g = 1 - eq(d.cobradorChute)
            val gr = (g * 11 until g * 11 + 11).firstOrNull { ativo[it] && papel[it] == 0 }
            if (gr != null && gkAtivo[g]) {
                alvoX[gr] = d.golo * (K.MX - 0.6f); alvoY[gr] = gkAlvoY[g].coerceIn(-5f, 5f); alvoSprint[gr] = true
            }
            moverBola(dt)
            contactos()
            avaliarChute(d)
        }
        EstadoChute.PAUSA -> {
            bolaNoFundo(dt)
            if (tFase >= 1.8f) {
                if (d.decidido) terminarDesempate(d) else proximoChute()
            }
        }
    }
    for (i in 0 until N) if (ativo[i]) mover(i, dt)
}

private fun MotorAoVivo.proximoChute() {
    val d = reg.desempate!!
    val e = d.vez
    val k = d.proximoDaEquipa(e)
    segundaParte = ((e == 0) == (d.golo < 0f))
    iniciarReinicio(FaseJogo.PENALTI, e, d.golo * K.PENALTI_X, 0f, k)
    // Os outros ficam no círculo central; os guarda-redes que não defendem também
    for (i in 0 until N) if (ativo[i]) { alvoX[i] = px[i]; alvoY[i] = py[i]; alvoSprint[i] = false }
    controlado = if (e == eqUser) k else -1
    d.estado = EstadoChute.PREPARAR
    d.cobradorChute = k
    espera = 1.4f + rng.nextFloat() * 0.8f
    tFase = 0f
    val sigla = if (e == 0) casa.abreviatura else fora.abreviatura
    mensagem("Penálti de $sigla: ${jog[k]!!.nome}", 2f)
}

private fun MotorAoVivo.avaliarChute(d: Desempate) {
    if (d.estado != EstadoChute.EM_VOO) return
    when {
        dono >= 0 -> resolverChute(d, false, TipoLance.PENALTI_DEFENDIDO)
        d.tVoo > 4.5f -> resolverChute(d, false, if (d.defendeu) TipoLance.PENALTI_DEFENDIDO else TipoLance.PENALTI_FORA)
        d.tVoo > 1.2f && hypot(bvx, bvy) < 0.4f && bz <= 0.05f ->
            resolverChute(d, false, if (d.defendeu) TipoLance.PENALTI_DEFENDIDO else TipoLance.PENALTI_FORA)
    }
}

/** Chamado por [MotorAoVivo]'s limites quando a bola sai do campo durante um penálti de desempate. Devolve true se tratou o caso. */
internal fun MotorAoVivo.desempateLimites(): Boolean {
    val d = reg.desempate ?: return false
    if (abs(bx) <= K.MX && abs(by) <= K.MY) return false
    if (d.estado != EstadoChute.EM_VOO) return true
    if (abs(bx) > K.MX) {
        val ay = abs(by)
        val dentro = ay <= K.POSTE - 0.12f && bz <= K.TRAVESSAO - 0.12f
        val trave = !dentro && ay <= K.POSTE + 0.12f && bz <= K.TRAVESSAO + 0.12f
        if (dentro) { resolverChute(d, true, TipoLance.PENALTI_MARCADO); return true }
        if (trave && dono < 0) { d.trave = true; return false }
    }
    resolverChute(d, false, if (d.defendeu) TipoLance.PENALTI_DEFENDIDO else TipoLance.PENALTI_FORA)
    return true
}

private fun MotorAoVivo.resolverChute(d: Desempate, marcou: Boolean, tipo: TipoLance) {
    if (d.estado != EstadoChute.EM_VOO) return
    val k = d.cobradorChute
    val e = eq(k)
    d.chutes[e]++
    if (marcou) d.marcados[e]++
    d.resultados[e] += marcou
    d.estado = EstadoChute.PAUSA
    tFase = 0f
    val nome = jog[k]!!.nome
    val sigla = if (e == 0) casa.abreviatura else fora.abreviatura
    val texto = when (tipo) {
        TipoLance.PENALTI_MARCADO -> "Golo! $nome marca ($sigla)"
        TipoLance.PENALTI_DEFENDIDO -> "Defendeu o guarda-redes! $nome falha ($sigla)"
        else -> "$nome falha o penálti ($sigla)"
    }
    lance(tipo, e, k, texto)
    mensagem(texto, 2f)
    d.vez = 1 - e
    d.decidido = d.estaDecidido()
    // Segurança: uma marcação que nunca acaba decide-se à sorte.
    if (!d.decidido && d.chutes[0] >= 30 && d.chutes[0] == d.chutes[1]) {
        if (rng.nextBoolean()) d.marcados[0]++ else d.marcados[1]++
        d.decidido = true
    }
}

/** A bola, depois do chute, assenta perto da baliza. */
private fun MotorAoVivo.bolaNoFundo(dt: Float) {
    bvx *= 1f - 4f * dt; bvy *= 1f - 4f * dt
    bvz -= K.G * dt
    bx += bvx * dt; by += bvy * dt; bz = max(0f, bz + bvz * dt)
    if (bz == 0f) bvz = 0f
    bx = bx.coerceIn(-K.MX - 2.2f, K.MX + 2.2f)
    by = by.coerceIn(-K.MY - 2f, K.MY + 2f)
}

private fun MotorAoVivo.terminarDesempate(d: Desempate) {
    fase = FaseJogo.FIM
    tFase = 0f
    val texto = "Penáltis: ${d.marcados[0]}-${d.marcados[1]}"
    lance(TipoLance.FIM_PENALTIS, if (d.marcados[0] > d.marcados[1]) 0 else 1, -1, texto)
    mensagem("Fim do jogo: ${golos[0]}-${golos[1]} ($texto)", 5f)
}
