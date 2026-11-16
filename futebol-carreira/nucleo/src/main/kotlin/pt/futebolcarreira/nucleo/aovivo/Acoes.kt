package pt.futebolcarreira.nucleo.aovivo

import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.simulacao.Evento
import pt.futebolcarreira.nucleo.simulacao.FichaJogador
import pt.futebolcarreira.nucleo.simulacao.TipoEvento
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

// Ações: comandos do utilizador, passes, remates, contactos com a bola, desarmes, faltas e lesões.

private const val PI = Math.PI.toFloat()

/** Aplica o comando do utilizador ao jogador que ele comanda (uma vez por passo de jogo). */
internal fun MotorAoVivo.usarComando() {
    if (eqUser < 0) { osTrocar = false; osPasse = false; osRemate = false; return }
    if (osTrocar) {
        osTrocar = false
        val dele = dono >= 0 && eq(dono) == eqUser
        if (fase == FaseJogo.A_JOGAR && !dele) controlado = maisPerto(eqUser, bx, by, false, controlado)
    }
    if (controlado < 0 || !ativo[controlado]) controlado = maisPerto(eqUser, bx, by, false)
    val c = controlado
    val passe = osPasse; val remate = osRemate
    osPasse = false; osRemate = false
    if (!passe && !remate) return
    val temBola = fase == FaseJogo.A_JOGAR && dono == c
    val cobra = fase != FaseJogo.A_JOGAR && fase != FaseJogo.GOLO && cobrador == c && eq(c) == eqUser && tFase >= 0.4f
    when {
        fase == FaseJogo.PENALTI && cobra -> remateUtilizador(c)
        (temBola || cobra) && remate -> remateUtilizador(c)
        (temBola || cobra) && passe -> passeUtilizador(c)
        fase == FaseJogo.A_JOGAR && remate && dono != c && atordoado[c] <= 0f && carrinho[c] <= 0f -> iniciarCarrinho(c)
    }
}

/** Carrinho: o jogador atira-se na direção da bola (ou para onde olha) durante cerca de meio segundo. */
internal fun MotorAoVivo.iniciarCarrinho(i: Int, joystick: Boolean = true) {
    var a = dir[i]
    if (hypot(bx - px[i], by - py[i]) < 7f) a = atan2(by - py[i], bx - px[i])
    val m = hypot(comX, comY)
    if (joystick && m > 0.3f) a = atan2(comY, comX)
    lungeX[i] = cos(a) * 8.5f; lungeY[i] = sin(a) * 8.5f
    dir[i] = a
    carrinho[i] = 0.55f
}

/** Passe do utilizador: para o colega mais alinhado com o joystick; "forca" alta = passe longo (pelo ar). */
internal fun MotorAoVivo.passeUtilizador(i: Int) {
    val e = eq(i)
    val m = hypot(comX, comY)
    val a = if (m > 0.25f) atan2(comY, comX) else dir[i]
    val ux = cos(a); val uy = sin(a)
    val longo = cmdForca > 0.62f
    var melhor = -1; var ms = -99f
    for (j in e * 11 until e * 11 + 11) {
        if (j == i || !ativo[j]) continue
        val dx = px[j] - px[i]; val dy = py[j] - py[i]
        val d = hypot(dx, dy)
        if (d < 3f) continue
        val cosT = (dx * ux + dy * uy) / d
        if (cosT < 0.62f) continue
        var s = cosT * 2f - d * 0.012f
        if (longo) { if (d < 14f) continue; s += d * 0.03f }
        else if (d > 30f) s -= 1f
        if (s > ms) { ms = s; melhor = j }
    }
    if (melhor >= 0) {
        passar(i, melhor, longo || hypot(px[melhor] - px[i], py[melhor] - py[i]) > 34f)
    } else {
        // Sem ninguém na direção: a bola vai para onde apontas.
        val dist = if (longo) 12f + 26f * cmdForca else 8f + 14f * cmdForca
        val tx = px[i] + ux * dist; val ty = py[i] + uy * dist
        receptor = -1
        if (longo) chutarAlto(i, tx, ty, 0.6f + dist / 28f, 0.07f) else chutarSolo(i, tx, ty, (1.25f * dist + 5f).coerceIn(8f, 28f), 0.05f)
        registarPasse(i)
    }
}

/** Remate do utilizador: para a baliza; o joystick (cima/baixo) escolhe o lado; "forca" dá velocidade (e menos pontaria). */
internal fun MotorAoVivo.remateUtilizador(i: Int) {
    val aim = if (abs(comY) > 0.3f) sign(comY) * (2.4f + rng.nextFloat() * 0.6f) else (rng.nextFloat() - 0.5f) * 4f
    val z = 0.4f + rng.nextFloat() * 1.3f
    val f = cmdForca
    val v = 16f + 16f * f * (0.6f + 0.4f * aRemate[i] / 100f)
    val sigma = (0.014f + (100f - aRemate[i]) * 0.0009f) * (0.8f + 0.9f * f) + pressao(i) * 0.02f
    rematar(i, aim, z, v, sigma)
}

internal fun MotorAoVivo.pressao(i: Int): Float {
    val o = distMaisPertoAdversario(i)
    return if (o < 4f) (4f - o) / 4f else 0f
}

internal fun MotorAoVivo.distMaisPertoAdversario(i: Int): Float {
    val e = eq(i)
    var md = 99f
    for (o in (1 - e) * 11 until (1 - e) * 11 + 11) {
        if (!ativo[o]) continue
        val d = hypot(px[o] - px[i], py[o] - py[i])
        if (d < md) md = d
    }
    return md
}

// ---------------------------------------------------------------- pontapés

/** Larga a bola: acaba a posse e marca o último toque. */
private fun MotorAoVivo.libertar(i: Int, anim: Animacao) {
    dono = -1
    ultimoToque = i
    val e = eq(i)
    ultimoEq = e
    ultimoDaEq[e] = i
    cd[i] = 0.5f
    animEsp[i] = anim; animT[i] = 0.3f
    parte[i]?.let { it.toques++ }
    if (fase != FaseJogo.A_JOGAR && fase != FaseJogo.GOLO) {
        fase = FaseJogo.A_JOGAR
        tFase = 0f
    }
    cobrador = -1
}

private fun MotorAoVivo.registarPasse(i: Int) {
    passador = i
    dbg[0]++
    tPasse = relogio
    parte[i]?.let { it.passes++ }
}

/** Passe rasteiro para um ponto. "v0" é a velocidade inicial da bola. */
internal fun MotorAoVivo.chutarSolo(i: Int, tx0: Float, ty0: Float, v0: Float, sigma: Float) {
    val tx = tx0.coerceIn(-K.MX + 1f, K.MX - 1f); val ty = ty0.coerceIn(-K.MY + 1f, K.MY - 1f)
    val d = hypot(tx - bx, ty - by)
    var a = atan2(ty - by, tx - bx) + gauss() * sigma * (1f + d * 0.02f)
    val v = v0 * (1f + gauss() * 0.04f)
    bvx = cos(a) * v; bvy = sin(a) * v; bvz = 0f
    dir[i] = atan2(ty - py[i], tx - px[i])
    libertar(i, Animacao.PASSAR)
}

/** Passe pelo ar: demora "t" segundos a chegar ao ponto. */
internal fun MotorAoVivo.chutarAlto(i: Int, tx0: Float, ty0: Float, t: Float, sigma: Float) {
    val tx = tx0.coerceIn(-K.MX + 1f, K.MX - 1f); val ty = ty0.coerceIn(-K.MY + 1f, K.MY - 1f)
    val d = hypot(tx - bx, ty - by)
    val a = atan2(ty - by, tx - bx) + gauss() * sigma * (1f + d * 0.015f)
    val tt = t * (1f + gauss() * 0.04f)
    val vh = d / tt
    bvx = cos(a) * vh; bvy = sin(a) * vh; bvz = 4.9f * tt
    dir[i] = atan2(ty - py[i], tx - px[i])
    libertar(i, Animacao.PASSAR)
}

/** Passe para o colega "c" (a bola vai para onde ele chegará). */
internal fun MotorAoVivo.passar(i: Int, c: Int, longo: Boolean) {
    val d0 = hypot(px[c] - bx, py[c] - by)
    val pr = pressao(i)
    val sigma = (0.012f + (100f - aPasse[i]) * 0.0012f) * (1f + pr * 0.6f)
    if (longo) {
        val t = 0.6f + d0 / 28f
        chutarAlto(i, px[c] + vx[c] * t * 0.7f, py[c] + vy[c] * t * 0.7f, t, sigma)
    } else {
        val v0 = (1.25f * d0 + 5f).coerceIn(8f, 30f)
        val t = d0 / (v0 * 0.62f)
        chutarSolo(i, px[c] + vx[c] * t * 0.8f, py[c] + vy[c] * t * 0.8f, v0, sigma)
    }
    receptor = c
    registarPasse(i)
    if (eq(i) == eqUser && i == controlado && c != i) controlado = c
}

/** Remate à baliza adversária: aponta a (aimY, aimZ) na linha de golo com velocidade horizontal "v". */
internal fun MotorAoVivo.rematar(i: Int, aimY: Float, aimZ: Float, v: Float, sigma: Float) {
    val e = eq(i)
    val gx = K.MX * sinal(e)
    val dx = gx - bx; val dy = aimY - by
    val dh = max(1f, hypot(dx, dy))
    val t = dh / v
    var vz = (aimZ + 4.9f * t * t) / t
    val a = atan2(dy, dx) + gauss() * sigma
    vz += gauss() * sigma * v * 0.7f
    bvx = cos(a) * v; bvy = sin(a) * v; bvz = max(0.2f, vz)
    dir[i] = atan2(dy, dx)
    // Vai à baliza? (previsão com a velocidade final)
    var alvo = false
    var yc = 0f
    if (bvx * sign(gx) > 0.1f) {
        val tc = (gx - bx) / bvx
        yc = by + bvy * tc
        val zc = bvz * tc - 4.9f * tc * tc
        alvo = abs(yc) < K.POSTE && zc > 0f && zc < K.TRAVESSAO
    }
    remates[e]++
    parte[i]?.let { it.remates++ }
    if (alvo) { noAlvo[e]++; parte[i]?.let { it.noAlvo++ } }
    // Guarda-redes adversário: percebe para onde vai a bola (com erro conforme a qualidade)
    val g = 1 - e
    val gr = (g * 11 until g * 11 + 11).firstOrNull { ativo[it] && papel[it] == 0 }
    if (gr != null) {
        gkAlvoY[g] = if (fase == FaseJogo.PENALTI) {
            if (rng.nextFloat() < 0.36f) (if (aimY >= 0f) 2.3f else -2.3f) else (if (aimY >= 0f) -2.3f else 2.3f)
        } else yc + gauss() * (0.5f + (100f - aGR[gr]) * 0.014f)
        gkAtraso[g] = if (fase == FaseJogo.PENALTI) 0f else 0.12f
        gkAtivo[g] = true
    }
    rolouDefesa = false
    ultimoRemateEq = e
    receptor = -1
    val pen = fase == FaseJogo.PENALTI
    libertar(i, Animacao.REMATAR)
    penaltiAtual = pen
}

// ---------------------------------------------------------------- contacto com a bola

/** Quem está ao alcance da bola tenta apanhá-la (ou bloqueá-la, ou defendê-la). */
internal fun MotorAoVivo.contactos() {
    if (dono >= 0 || bz > 2.7f) return
    val vh = hypot(bvx, bvy)
    var melhor = -1; var ms = -1000f
    for (i in 0 until N) {
        if (!ativo[i] || cd[i] > 0f || atordoado[i] > 0f) continue
        val gr = papel[i] == 0
        val alcance = if (gr) (if (!rolouDefesa || ultimoEq == eq(i)) 1.5f else 2.6f) else 1.0f
        val dx = px[i] - bx; val dy = py[i] - by
        if (dx * dx + dy * dy > alcance * alcance) continue
        if (bz > (if (gr) 2.7f else 1.9f)) continue
        var sc = rng.nextFloat() * 25f + aFisico[i] * 0.25f
        if (i == receptor) sc += 40f
        if (eq(i) == ultimoEq && i != receptor) sc -= 10f
        if (gr) sc += 12f
        if (sc > ms) { ms = sc; melhor = i }
    }
    if (melhor >= 0) tocar(melhor, vh)
}

private fun MotorAoVivo.tocar(i: Int, vh: Float) {
    val e = eq(i)
    if (papel[i] == 0 && !rolouDefesa && ultimoEq != e && vh > 11f) {
        rolouDefesa = true
        val dist3 = hypot(hypot(px[i] - bx, py[i] - by), max(0f, bz - 0.9f))
        val q = dist3 / 2.6f
        val p = (0.97f - q * 0.5f - max(0f, vh - 18f) * 0.011f) * (0.5f + 0.5f * aGR[i] / 100f)
        if (rng.nextFloat() < p) defender(i, vh)
        return
    }
    val deles = ultimoEq != e
    if (deles && vh > 17f && papel[i] != 0) {
        if (bz < 1.6f && rng.nextFloat() < 0.4f) {
            // Remate bloqueado
            if (rng.nextFloat() < 0.3f) { bvx = sign(bvx) * 6f; bvy = (if (by >= 0f) 1f else -1f) * 8f } else { bvx = -bvx * 0.3f + gauss() * 3f; bvy = bvy * 0.3f + gauss() * 3f }
            bvz = 1.5f + rng.nextFloat() * 2f
            ultimoToque = i; ultimoEq = e; ultimoDaEq[e] = i; cd[i] = 0.3f
            parte[i]?.let { it.desarmes++ }
            rolouDefesa = true
        }
        return
    }
    if (!deles && vh > 22f && i != receptor) return
    if (vh > 8f) {
        val vv = if (bz > 0.4f) vh * 0.5f else vh
        val p = ((vv - 8f) * 0.012f + (100f - aDrible[i]) * 0.0008f).coerceIn(0f, 0.4f)
        if (rng.nextFloat() < p) {
            bvx = bvx * 0.4f + gauss() * 1.5f; bvy = bvy * 0.4f + gauss() * 1.5f; bvz = 1f + rng.nextFloat()
            cd[i] = 0.4f
            return
        }
    }
    aoReceber(i)
}

private fun MotorAoVivo.defender(i: Int, vh: Float) {
    val e = eq(i)
    val atacante = 1 - e
    parte[i]?.ficha?.let { it.defesas++ }
    val rematador = ultimoDaEq[atacante]
    if (rematador >= 0 && parte[rematador] != null && parte[i] != null) {
        eventos += Evento(minutoEvento(), TipoEvento.DEFESA, atacante == 0, jog[rematador]!!, jog[i]!!)
    }
    animEsp[i] = Animacao.DEFESA_GR; animT[i] = 0.7f
    if (rng.nextFloat() < 0.45f + (aGR[i] - 60f) * 0.004f && vh < 26f) {
        aoReceber(i)
        decT[i] = 1.0f + rng.nextFloat() * 1.2f
        mensagem("Grande defesa de ${jog[i]!!.nome}", 2f)
    } else {
        val s = sinal(e)
        if (rng.nextFloat() < 0.35f) {
            bvx = -s * 3f; bvy = (if (by >= 0f) 1f else -1f) * (8f + rng.nextFloat() * 3f); bvz = 1.5f
        } else {
            bvx = s * (4f + rng.nextFloat() * 6f); bvy = (rng.nextFloat() - 0.5f) * 10f; bvz = 2f + rng.nextFloat() * 3f
        }
        ultimoToque = i; ultimoEq = e; ultimoDaEq[e] = i
        cd[i] = 0.6f
        receptor = -1
    }
}

/** O jogador "i" fica com a bola. */
internal fun MotorAoVivo.aoReceber(i: Int) {
    val e = eq(i)
    val anterior = ultimoEq
    if (passador >= 0) { if (eq(passador) == e) dbg[3]++ else dbg[2]++ }
    if (passador >= 0 && ultimoToque == passador && eq(passador) == e && passador != i) {
        parte[passador]?.let { it.passesOk++ }
        assistente = passador
    } else if (anterior != e) {
        assistente = -1
        if (anterior >= 0 && ultimoToque >= 0) parte[i]?.let { it.desarmes++ }
    } else if (ultimoToque != i) assistente = -1
    val eraDoUtilizador = anterior == eqUser
    dono = i
    ultimoToque = i; ultimoEq = e; ultimoDaEq[e] = i
    receptor = -1; passador = -1
    prot[i] = 0.45f
    parte[i]?.let { it.toques++ }
    val pr = distMaisPertoAdversario(i)
    decT[i] = if (papel[i] == 0) 1.0f + rng.nextFloat() * 1.2f else if (pr < 2.5f) 0.3f else 1.0f + rng.nextFloat() * 1.6f
    if (e == eqUser) controlado = i
    else if (eraDoUtilizador && controlado >= 0 && hypot(px[controlado] - bx, py[controlado] - by) > 15f) controlado = maisPerto(eqUser, bx, by, false)
}

// ---------------------------------------------------------------- desarmes e faltas

internal fun MotorAoVivo.desarmes() {
    val c = dono
    if (c >= 0) {
        dbg[6]++
        val e0 = eq(c)
        var md = 99f
        for (o in (1 - e0) * 11 until (1 - e0) * 11 + 11) if (ativo[o]) md = min(md, hypot(px[o] - bx, py[o] - by))
        if (md < 3f) dbg[7]++
        if (md < 1.1f) dbg[8]++
        if (prot[c] > 0f) dbg[9]++
    }
    if (c < 0 || prot[c] > 0f) return
    val e = eq(c)
    for (o in (1 - e) * 11 until (1 - e) * 11 + 11) {
        if (!ativo[o] || cdDesarme[o] > 0f || atordoado[o] > 0f) continue
        val sl = carrinho[o] > 0f
        val d = hypot(px[o] - bx, py[o] - by)
        if (d > (if (sl) 1.8f else 1.4f)) continue
        cdDesarme[o] = 0.7f + rng.nextFloat() * 0.5f
        tentarDesarme(o, c, sl)
        if (dono != c) return
    }
}

private fun MotorAoVivo.tentarDesarme(o: Int, c: Int, sl: Boolean) {
    dbg[4]++
    val sprint = hypot(vx[c], vy[c]) > velMax(c) * 1.1f
    val p = (0.30f + (aDefesa[o] - aDrible[c]) * 0.009f + (aFisico[o] - aFisico[c]) * 0.003f + (if (sl) 0.10f else 0f) - (if (sprint) 0.04f else 0f)).coerceIn(0.08f, 0.68f)
    if (rng.nextFloat() < p) {
        parte[o]?.let { it.desarmes++ }
        cd[c] = 0.6f
        ultimoToque = c
        aoReceber(o)
    } else if (rng.nextFloat() < (if (sl) 0.34f else 0.22f)) {
        falta(o, c, sl)
    }
}

private fun MotorAoVivo.falta(inf: Int, vit: Int, sl: Boolean) {
    dbg[5]++
    val e = eq(inf)
    faltas[e]++
    parte[inf]?.let { it.faltas++ }
    var x = bx.coerceIn(-K.MX + 0.5f, K.MX - 0.5f)
    var y = by.coerceIn(-K.MY + 0.5f, K.MY - 0.5f)
    val r = rng.nextFloat()
    val pv = if (sl) 0.05f else 0.008f
    val pa = if (sl) 0.40f else 0.17f
    val min = minutoEvento()
    var expulso = false
    val ficha = parte[inf]?.ficha
    if (ficha != null) {
        if (r < pv) {
            ficha.vermelho = true; ficha.vermelhoDireto = true; cartVm[e]++
            eventos += Evento(min, TipoEvento.VERMELHO, e == 0, jog[inf]!!)
            mensagem("Cartão vermelho: ${jog[inf]!!.nome}", 3f)
            expulso = true
        } else if (r < pv + pa) {
            if (amarelos[inf] >= 1) {
                amarelos[inf] = 2; ficha.amarelos = 2; ficha.vermelho = true; cartAm[e]++; cartVm[e]++
                eventos += Evento(min, TipoEvento.SEGUNDO_AMARELO, e == 0, jog[inf]!!)
                mensagem("Segundo amarelo: ${jog[inf]!!.nome}", 3f)
                expulso = true
            } else {
                amarelos[inf] = 1; ficha.amarelos = 1; cartAm[e]++
                eventos += Evento(min, TipoEvento.AMARELO, e == 0, jog[inf]!!)
                mensagem("Cartão amarelo: ${jog[inf]!!.nome}", 3f)
            }
        }
    }
    if (expulso) {
        ficha?.saiu = min
        ativo[inf] = false
        if (controlado == inf) controlado = maisPerto(eqUser, bx, by, false)
    }
    // Penálti ou livre
    val golo = sign(x)   // baliza que o atacante (vítima) ataca, se a falta for junto a ela
    val naAreaDele = sinal(e) == -golo && abs(x) > K.AREA_X && abs(y) < K.AREA_Y
    dono = -1
    if (naAreaDele) {
        mensagem("Penálti!", 2.5f)
        iniciarReinicio(FaseJogo.PENALTI, 1 - e, golo * K.PENALTI_X, 0f)
    } else {
        if (!expulso) mensagem("Falta", 1.5f)
        iniciarReinicio(FaseJogo.LIVRE, 1 - e, x, y)
    }
    if (vit != cobrador && ativo[vit]) { atordoado[vit] = 1.2f; vx[vit] = 0f; vy[vit] = 0f }
    // Lesão rara de quem sofre a falta
    if (ativo[vit] && rng.nextFloat() < 0.006f) lesionar(vit)
}

/** Lesão: entra um suplente (se houver), senão a equipa fica com menos um. */
internal fun MotorAoVivo.lesionar(i: Int) {
    val e = eq(i)
    val min = minutoEvento()
    val sai = jog[i]!!
    val p = parte[i] ?: return
    p.ficha.lesao = (1 + (-ln(1.0 - rng.nextDouble()) * 3.0).toInt()).coerceAtMost(25)
    p.ficha.saiu = min
    eventos += Evento(min, TipoEvento.LESAO, e == 0, sai)
    val entra = banco[e].filter { if (lugar[i] == "GR") it.posicao == "GR" else it.posicao != "GR" }.maxByOrNull { Equipa.notaNoLugar(it, lugar[i]) }
    if (entra == null) {
        ativo[i] = false
        if (controlado == i) controlado = maisPerto(eqUser, bx, by, false)
        return
    }
    banco[e].remove(entra)
    colocarJogador(i, entra)
    stam[i] = 100f
    amarelos[i] = 0
    val ficha = FichaJogador(entra, lugar[i], false, min)
    val np = Parte(e, entra, ficha)
    parte[i] = np
    partes += np
    eventos += Evento(min, TipoEvento.SUBSTITUICAO, e == 0, sai, entra)
    mensagem("Lesão: ${sai.nome} sai, entra ${entra.nome}", 3f)
}
