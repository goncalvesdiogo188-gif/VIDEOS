package pt.futebolcarreira.nucleo.aovivo

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

// Inteligência dos jogadores: onde se põem (forma, pressão, apoio, marcação) e o que faz quem tem a bola.

/** A equipa que "tem" a bola: quem a leva, ou quem lhe tocou por último (ou quem cobra um lance parado). */
internal fun MotorAoVivo.posseEq(): Int = when {
    dono >= 0 -> eq(dono)
    fase != FaseJogo.A_JOGAR -> eqReinicio
    else -> ultimoEq
}

/** Decide, a 10 vezes por segundo, para onde vai cada jogador que não é comandado pelo utilizador. */
internal fun MotorAoVivo.calcularAlvos() {
    val pe = posseEq()
    val jogoAberto = fase == FaseJogo.A_JOGAR
    // Onde estará a bola dentro de pouco tempo
    val qx = if (dono >= 0) bx else bx + bvx * 0.35f
    val qy = if (dono >= 0) by else by + bvy * 0.35f
    for (e in 0..1) {
        presser1[e] = -1; presser2[e] = -1
        var d1 = Float.MAX_VALUE; var d2 = Float.MAX_VALUE
        for (i in e * 11 until e * 11 + 11) {
            if (!ativo[i] || papel[i] == 0 || i == controlado || atordoado[i] > 0f) continue
            val d = (px[i] - qx) * (px[i] - qx) + (py[i] - qy) * (py[i] - qy)
            if (d < d1) { d2 = d1; presser2[e] = presser1[e]; d1 = d; presser1[e] = i }
            else if (d < d2) { d2 = d; presser2[e] = i }
        }
        // Apoio: os dois colegas mais perto de quem tem a bola (ou de quem cobra)
        apoio1[e] = -1; apoio2[e] = -1
        val origem = if (jogoAberto && dono >= 0 && eq(dono) == e) dono else if (!jogoAberto && eqReinicio == e) cobrador else -1
        if (origem >= 0) {
            var a1 = Float.MAX_VALUE; var a2 = Float.MAX_VALUE
            for (i in e * 11 until e * 11 + 11) {
                if (!ativo[i] || papel[i] == 0 || i == origem || i == controlado) continue
                val d = (px[i] - px[origem]) * (px[i] - px[origem]) + (py[i] - py[origem]) * (py[i] - py[origem])
                if (d < a1) { a2 = a1; apoio2[e] = apoio1[e]; a1 = d; apoio1[e] = i }
                else if (d < a2) { a2 = d; apoio2[e] = i }
            }
        }
    }
    for (i in 0 until N) {
        if (!ativo[i]) continue
        alvoSprint[i] = false
        if (i == controlado && jogoAberto) continue
        val e = eq(i); val s = sinal(e)
        if (!jogoAberto) { alvoParado(i, e, s); continue }
        if (papel[i] == 0) { alvoGR(i, e, s); continue }
        if (dono == i) { alvoPortador(i, e, s); continue }
        if (receptor == i) {
            alvoX[i] = qx; alvoY[i] = qy; alvoSprint[i] = true
            continue
        }
        val atacar = pe == e
        if (dono < 0 && presser1[e] == i) {
            if (receptor >= 0 && eq(receptor) != e) {
                // Passe do adversário no ar: só corta quem está perto da trajetória
                val fx = bx + bvx * 1.5f; val fy = by + bvy * 1.5f
                if (distSegmento(px[i], py[i], bx, by, fx, fy) < 3.5f) {
                    val dx = fx - bx; val dy = fy - by
                    val l2 = max(0.01f, dx * dx + dy * dy)
                    val t = (((px[i] - bx) * dx + (py[i] - by) * dy) / l2).coerceIn(0f, 1f)
                    alvoX[i] = bx + dx * t; alvoY[i] = by + dy * t; alvoSprint[i] = true
                    continue
                }
            } else {
                // Bola solta: o mais perto de cada equipa vai buscá-la
                alvoX[i] = qx; alvoY[i] = qy; alvoSprint[i] = true
                continue
            }
        }
        if (atacar) {
            if (apoio1[e] == i || apoio2[e] == i) {
                val c = dono
                val lado = if (apoio1[e] == i) -1f else 1f
                if (c >= 0) {
                    alvoX[i] = (px[c] * s + 7f).coerceIn(-48f, 47f) * s
                    alvoY[i] = ((py[c] * s) + lado * 10f).coerceIn(-31f, 31f) * s
                    alvoSprint[i] = distJ(i, alvoX[i], alvoY[i]) > 6f
                    continue
                }
            }
            forma(i, e, s, true)
        } else {
            if (presser1[e] == i && dono >= 0) {
                alvoX[i] = qx; alvoY[i] = qy; alvoSprint[i] = distJ(i, qx, qy) < 30f
                val dd = distJ(i, qx, qy)
                if (dd > 1.3f && dd < 2.6f && carrinho[i] <= 0f && atordoado[i] <= 0f && cdDesarme[i] <= 0f && rng.nextFloat() < 0.05f) {
                    iniciarCarrinho(i, false)
                }
            } else if (presser2[e] == i && dono >= 0 && distJ(i, qx, qy) < 18f) {
                // Fecha a linha de passe: fica entre a bola e a própria baliza
                val gx = -K.MX * s
                val dx = gx - qx; val dy = -qy
                val d = max(1f, hypot(dx, dy))
                alvoX[i] = qx + dx / d * 5f; alvoY[i] = qy + dy / d * 5f
                alvoSprint[i] = true
            } else {
                forma(i, e, s, false)
                marcar(i, e, s)
            }
        }
    }
}

/** Posição de "forma" de um jogador: o lugar da formação, deslocado conforme a bola e a mentalidade. */
private fun MotorAoVivo.forma(i: Int, e: Int, s: Float, atacar: Boolean) {
    val ballU = bx * s; val ballV = by * s
    val k = when (papel[i]) { 1 -> 0.65f; 2 -> 0.9f; else -> 1f }
    var u = baseU[i] + (ballU * 0.38f + (if (atacar) 7f else -5f) + mentShift[e]) * k
    u = when (papel[i]) { 1 -> u.coerceIn(-50f, 14f); 2 -> u.coerceIn(-46f, 40f); else -> u.coerceIn(-40f, 47f) }
    val v = (baseV[i] * 0.85f + ballV * 0.25f).coerceIn(-31f, 31f)
    alvoX[i] = u * s; alvoY[i] = v * s
    alvoSprint[i] = distJ(i, alvoX[i], alvoY[i]) > 14f
}

/** Defesas e médios marcam o adversário mais perto do seu lugar (ficam do lado da própria baliza). */
private fun MotorAoVivo.marcar(i: Int, e: Int, s: Float) {
    if (papel[i] == 3) return
    var melhor = -1; var md = 8f * 8f
    for (o in (1 - e) * 11 until (1 - e) * 11 + 11) {
        if (!ativo[o] || papel[o] == 0) continue
        val d = (px[o] - alvoX[i]) * (px[o] - alvoX[i]) + (py[o] - alvoY[i]) * (py[o] - alvoY[i])
        if (d < md) { md = d; melhor = o }
    }
    if (melhor < 0) return
    val gx = -K.MX * s
    val dx = gx - px[melhor]; val dy = -py[melhor]
    val d = max(1f, hypot(dx, dy))
    val mx = px[melhor] + dx / d * 2.2f; val my = py[melhor] + dy / d * 2.2f
    alvoX[i] = alvoX[i] * 0.35f + mx * 0.65f
    alvoY[i] = alvoY[i] * 0.35f + my * 0.65f
    alvoSprint[i] = distJ(i, alvoX[i], alvoY[i]) > 8f
}

private fun MotorAoVivo.alvoGR(i: Int, e: Int, s: Float) {
    val ownX = -K.MX * s
    if (gkAtivo[e] && gkAtraso[e] <= 0f) {
        alvoX[i] = ownX + s * 0.6f; alvoY[i] = gkAlvoY[e].coerceIn(-5f, 5f); alvoSprint[i] = true
        return
    }
    if (dono == i) { alvoX[i] = px[i]; alvoY[i] = py[i]; return }
    val vh = hypot(bvx, bvy)
    val naArea = abs(bx - ownX) < 16f && abs(by) < 20f
    if (dono < 0 && naArea && vh < 7f && ultimoEq != e) {
        val perto = maisPerto(1 - e, bx, by, true)
        if (hypot(px[perto] - bx, py[perto] - by) > distJ(i, bx, by) * 0.8f) {
            alvoX[i] = bx; alvoY[i] = by; alvoSprint[i] = true
            return
        }
    }
    if (dono < 0 && naArea && vh < 3f && distJ(i, bx, by) < 11f) {
        alvoX[i] = bx; alvoY[i] = by; alvoSprint[i] = true
        return
    }
    val dx = bx - ownX; val dy = by
    val d = max(1f, hypot(dx, dy))
    val passo = (d * 0.12f).coerceIn(1.5f, 6.5f)
    alvoX[i] = ownX + dx / d * passo; alvoY[i] = dy / d * passo
}

/** Para onde corre quem tem a bola (IA): em direção à baliza, a fugir dos adversários mais perto. */
private fun MotorAoVivo.alvoPortador(i: Int, e: Int, s: Float) {
    val gx = K.MX * s
    var dx = gx - px[i]; var dy = -py[i] * 0.4f
    val n = max(1f, hypot(dx, dy)); dx /= n; dy /= n
    var rx = dx; var ry = dy
    var maisPerto = 99f
    for (o in (1 - e) * 11 until (1 - e) * 11 + 11) {
        if (!ativo[o]) continue
        val ox = px[o] - px[i]; val oy = py[o] - py[i]
        val d = hypot(ox, oy)
        if (d < maisPerto) maisPerto = d
        if (d < 8f && d > 0.1f && ox * dx + oy * dy > 0f) {
            val w = (1f - d / 8f) * 1.6f
            rx -= ox / d * w; ry -= oy / d * w
        }
    }
    val r = max(0.01f, hypot(rx, ry))
    alvoX[i] = (px[i] + rx / r * 10f).coerceIn(-K.MX + 1f, K.MX - 1f)
    alvoY[i] = (py[i] + ry / r * 10f).coerceIn(-K.MY + 1.5f, K.MY - 1.5f)
    alvoSprint[i] = maisPerto > 3.5f && stam[i] > 35f
}

/** Alvos durante bolas paradas (pontapé de saída, cantos, livres, penáltis...). */
private fun MotorAoVivo.alvoParado(i: Int, e: Int, s: Float) {
    if (i == cobrador || fase == FaseJogo.PONTAPE_SAIDA || fase == FaseJogo.INTERVALO) { alvoX[i] = px[i]; alvoY[i] = py[i]; return }
    if (papel[i] == 0 && fase != FaseJogo.PENALTI) {
        alvoGR(i, e, s)
        return
    }
    val idx = i % 11
    when (fase) {
        FaseJogo.PENALTI -> {
            if (papel[i] == 0) { alvoX[i] = px[i]; alvoY[i] = py[i]; return }
            val g = sign(bx)
            alvoX[i] = g * (K.MX - 22f) + (if (e == eqReinicio) -g * 2f else 0f)
            alvoY[i] = (idx - 5) * 3.4f + (if (e == eqReinicio) 1.5f else 0f)
            return
        }
        FaseJogo.CANTO -> {
            val g = sign(bx)
            if (e == eqReinicio) {
                if (papel[i] == 3 || papel[i] == 2 || (papel[i] == 1 && abs(baseV[i]) < 12f)) {
                    alvoX[i] = g * (K.MX - 7f - (idx % 3) * 3f); alvoY[i] = (idx - 5) * 2.8f
                    return
                }
            } else if (papel[i] != 3) {
                alvoX[i] = g * (K.MX - 5f - (idx % 3) * 2.5f); alvoY[i] = (idx - 5) * 2.6f
                return
            }
        }
        else -> {}
    }
    val atacar = e == eqReinicio
    if (apoio1[e] == i || apoio2[e] == i) {
        val lado = if (apoio1[e] == i) -1f else 1f
        // Perto de quem cobra, para dar opção de passe
        alvoX[i] = (bx * s + 9f).coerceIn(-48f, 47f) * s
        alvoY[i] = (by * s + lado * 8f).coerceIn(-31f, 31f) * s
        return
    }
    forma(i, e, s, atacar)
    // Os adversários ficam afastados da bola
    val raio = when (fase) { FaseJogo.LIVRE, FaseJogo.CANTO -> 9.5f; FaseJogo.LANCAMENTO -> 4f; else -> 0f }
    if (!atacar && raio > 0f) {
        val dx = alvoX[i] - bx; val dy = alvoY[i] - by
        val d = hypot(dx, dy)
        if (d < raio) {
            val n = if (d < 0.1f) 1f else d
            alvoX[i] = bx + (if (d < 0.1f) 1f else dx / n) * raio
            alvoY[i] = by + dy / n * raio
        }
    }
    alvoSprint[i] = false
}

// ---------------------------------------------------------------- decisões de quem tem a bola

internal fun MotorAoVivo.decisoesDoPortador(@Suppress("UNUSED_PARAMETER") dt: Float) {
    val c = dono
    if (c < 0) return
    if (c == controlado && eq(c) == eqUser) {
        // O guarda-redes do utilizador não fica horas com a bola: se o utilizador não a joga, joga o adjunto.
        if (papel[c] == 0 && decT[c] < -2f) decidir(c)
        return
    }
    if (decT[c] > 0f) return
    decidir(c)
}

private fun MotorAoVivo.decidir(c: Int) {
    val e = eq(c); val s = sinal(e)
    val gx = K.MX * s
    val dGol = hypot(px[c] - gx, py[c])
    val pr = distMaisPertoAdversario(c)
    // 1) Remate
    val alcance = 17f + aRemate[c] * 0.2f
    if (papel[c] != 0 && dGol < alcance) {
        var p = if (dGol < 11f) 0.9f else 0.25f + 0.6f * (1f - (dGol - 11f) / (alcance - 11f))
        if (abs(py[c]) > 17f && dGol > 12f) p *= 0.4f
        if (linhaBloqueada(c, gx, 0f)) p *= 0.5f
        if (rng.nextFloat() < p) { rematarIA(c); return }
    }
    // 2) Passe
    val j = escolherPasse(c, if (papel[c] == 0) 55f else 42f, true)
    var pPasse = if (pr < 2f) 0.95f else if (pr < 5f) 0.55f else 0.3f
    if (papel[c] == 1) pPasse += 0.25f
    if (passeScore < 0.8f && pr > 3f) pPasse = min(pPasse, 0.2f)
    if (j >= 0 && (papel[c] == 0 || rng.nextFloat() < pPasse)) {
        passar(c, j, passeLob)
        return
    }
    // 3) Continua a driblar
    decT[c] = 0.5f + rng.nextFloat() * 0.6f
}

/** Há um adversário (que não o guarda-redes) a menos de 2 m da linha entre o jogador e o ponto? */
private fun MotorAoVivo.linhaBloqueada(i: Int, tx: Float, ty: Float): Boolean {
    val e = eq(i)
    for (o in (1 - e) * 11 until (1 - e) * 11 + 11) {
        if (!ativo[o] || papel[o] == 0) continue
        if (distSegmento(px[o], py[o], px[i], py[i], tx, ty) < 1.6f) return true
    }
    return false
}

internal fun distSegmento(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax; val dy = by - ay
    val l2 = dx * dx + dy * dy
    if (l2 < 1e-6f) return hypot(x - ax, y - ay)
    val t = (((x - ax) * dx + (y - ay) * dy) / l2).coerceIn(0f, 1f)
    return hypot(x - (ax + t * dx), y - (ay + t * dy))
}

internal fun MotorAoVivo.rematarIA(c: Int, extraErro: Float = 0f) {
    val e = eq(c); val s = sinal(e)
    val gx = K.MX * s
    val dGol = hypot(px[c] - gx, py[c])
    val g = (1 - e) * 11
    val gr = (g until g + 11).firstOrNull { ativo[it] && papel[it] == 0 }
    val gy = if (gr != null) py[gr] else 0f
    val lado = if (rng.nextFloat() < 0.8f) (if (gy > 0.3f) -1f else if (gy < -0.3f) 1f else (if (rng.nextBoolean()) 1f else -1f)) else 0f
    val aim = lado * (1.3f + rng.nextFloat() * 1.9f)
    val z = if (rng.nextFloat() < 0.7f) 0.25f + rng.nextFloat() * 0.9f else 1.2f + rng.nextFloat() * 0.9f
    val v = 17f + 11f * aRemate[c] / 100f + rng.nextFloat() * 3f
    val sigma = 0.02f + (100f - aRemate[c]) * 0.0014f + dGol * 0.0016f + pressao(c) * 0.04f + extraErro
    rematar(c, aim, z, v, sigma)
}

/**
 * Escolhe o melhor colega para um passe (ou -1). Deixa a pontuação em "passeScore" e se o passe deve ir pelo ar em "passeLob".
 * Conta o avanço no campo, a linha de passe livre, o espaço do recetor e a distância.
 */
internal fun MotorAoVivo.escolherPasse(i: Int, maxD: Float, permitirLob: Boolean): Int {
    val e = eq(i); val s = sinal(e)
    var melhor = -1; var ms = -99f; var mlob = false
    val gr = papel[i] == 0
    for (j in e * 11 until e * 11 + 11) {
        if (j == i || !ativo[j]) continue
        val dx = px[j] - bx; val dy = py[j] - by
        val d = hypot(dx, dy)
        if (d < 5f || d > maxD) continue
        val progresso = ((px[j] - px[i]) * s / 25f).coerceIn(-1f, 1.5f)
        var lane = 99f; var recv = 99f
        for (o in (1 - e) * 11 until (1 - e) * 11 + 11) {
            if (!ativo[o]) continue
            val dl = distSegmento(px[o], py[o], bx, by, px[j], py[j])
            if (dl < lane) lane = dl
            val dr = hypot(px[o] - px[j], py[o] - py[j])
            if (dr < recv) recv = dr
        }
        var laneS = (lane / 5f).coerceIn(0f, 1f)
        val recvS = (recv / 9f).coerceIn(0f, 1f)
        var lob = false
        if (permitirLob && ((lane < 2.4f && d > 20f) || d > 28f || (gr && d > 18f))) { lob = true; laneS = 0.7f }
        var sc = (if (gr) 1.5f else 0.9f) * progresso + 1.1f * laneS + 0.9f * recvS - 0.010f * d + 0.25f * rng.nextFloat() + 0.1f * (aPasse[i] - 60f) / 40f
        if (papel[j] == 0) sc -= 1f
        if (!lob && laneS < 0.35f) sc -= 1f
        if (lob) sc -= 0.15f
        if (sc > ms) { ms = sc; melhor = j; mlob = lob }
    }
    passeScore = ms
    passeLob = mlob
    return melhor
}

// ---------------------------------------------------------------- lances parados

internal fun MotorAoVivo.reinicioIA() {
    val c = cobrador
    if (c < 0 || !ativo[c]) {
        // Quem cobrava foi expulso/saiu: escolhe outro
        if (fase != FaseJogo.GOLO && fase != FaseJogo.INTERVALO) iniciarReinicio(fase, eqReinicio, bx, by)
        return
    }
    if (eq(c) == eqUser) { if (tFase > 7f) executarReinicio(c); return }
    if (tFase >= espera) executarReinicio(c)
}

private fun MotorAoVivo.executarReinicio(c: Int) {
    val e = eq(c); val s = sinal(e)
    val dGol = hypot(px[c] - K.MX * s, py[c])
    when (fase) {
        FaseJogo.PENALTI -> {
            val lado = if (rng.nextBoolean()) 1f else -1f
            rematar(c, lado * (2.0f + rng.nextFloat() * 1.0f), 0.3f + rng.nextFloat() * 1.3f, 21f + 5f * aRemate[c] / 100f, 0.02f + (100f - aRemate[c]) * 0.0004f)
        }
        FaseJogo.LIVRE -> {
            if (dGol < 30f && aRemate[c] > 55f && rng.nextFloat() < 0.5f) rematarIA(c, 0.03f) else passeReinicio(c, 40f, true)
        }
        FaseJogo.CANTO -> cruzamento(c)
        FaseJogo.PONTAPE_BALIZA -> passeReinicio(c, 55f, true)
        FaseJogo.LANCAMENTO -> passeReinicio(c, 20f, false)
        else -> passeReinicio(c, 30f, false)
    }
}

private fun MotorAoVivo.passeReinicio(c: Int, maxD: Float, lob: Boolean) {
    val j = escolherPasse(c, maxD, lob)
    if (j >= 0) {
        passar(c, j, passeLob)
    } else {
        val k = maisPerto(eq(c), bx, by, false, c)
        passar(c, k, false)
    }
}

private fun MotorAoVivo.cruzamento(c: Int) {
    val e = eq(c)
    val g = sign(bx)
    val ptx = g * (K.MX - 9f)
    var melhor = -1; var md = 12f
    for (j in e * 11 until e * 11 + 11) {
        if (j == c || !ativo[j] || papel[j] == 0) continue
        val d = hypot(px[j] - ptx, py[j])
        if (d < md) { md = d; melhor = j }
    }
    if (melhor >= 0) passar(c, melhor, true)
    else {
        val ty = (rng.nextFloat() - 0.5f) * 10f
        chutarAlto(c, ptx, ty, 0.6f + hypot(ptx - bx, ty - by) / 28f, 0.08f)
    }
}
