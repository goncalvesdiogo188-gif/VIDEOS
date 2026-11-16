package pt.futebolcarreira.nucleo.aovivo

import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.simulacao.Evento
import pt.futebolcarreira.nucleo.simulacao.FichaJogador
import pt.futebolcarreira.nucleo.simulacao.TipoEvento
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** Medidas do campo e constantes da física (metros, segundos). */
internal object K {
    const val MX = 52.5f
    const val MY = 34f
    const val POSTE = 3.66f
    const val TRAVESSAO = 2.44f
    const val AREA_X = 36f
    const val AREA_Y = 20.16f
    const val PENALTI_X = 41.5f
    const val SUB = 1f / 60f
    const val G = 9.8f
    const val ATRITO = 1.1f
    const val ACEL = 11f
}

/** O que um jogador fez no jogo (para a ficha e a nota). */
internal class Parte(val eq: Int, val jogador: Jogador, val ficha: FichaJogador) {
    var toques = 0
    var passes = 0
    var passesOk = 0
    var remates = 0
    var noAlvo = 0
    var desarmes = 0
    var faltas = 0
    var segundos = 0f
}

/**
 * Motor de um jogo ao vivo: 22 jogadores com física simples, IA por posição e a bola com velocidade, atrito e gravidade.
 * Tudo corre em passos fixos de 1/60 s (determinístico para a mesma semente e os mesmos comandos).
 * O estado fica em arrays (sem alocações por passo); as decisões de IA estão em Ia.kt e as ações em Acoes.kt.
 */
class MotorAoVivo(
    internal val casa: Equipa, internal val fora: Equipa, val utilizadorCasa: Boolean,
    internal val rng: Random, val duracaoReal: Float = 480f, val semUtilizador: Boolean = false,
    val dificuldade: Dificuldade = Dificuldade.NORMAL,
    internal val exigencia: ExigenciaVencedor? = null,
) : PartidaAoVivo, IaPeloUtilizador {
    internal val reg = EstadoRegras()
    internal val N = 22
    internal val jog = arrayOfNulls<Jogador>(N)
    internal val lugar = Array(N) { "" }
    internal val numero = IntArray(N)
    internal val ativo = BooleanArray(N)
    internal val px = FloatArray(N); internal val py = FloatArray(N)
    internal val vx = FloatArray(N); internal val vy = FloatArray(N)
    internal val dir = FloatArray(N)
    internal val animEsp = Array(N) { Animacao.PARADO }
    internal val animT = FloatArray(N)
    internal val anim = Array(N) { Animacao.PARADO }
    internal val stam = FloatArray(N) { 100f }
    internal val cd = FloatArray(N)
    internal val atordoado = FloatArray(N)
    internal val cdDesarme = FloatArray(N)
    internal val carrinho = FloatArray(N)
    internal val lungeX = FloatArray(N); internal val lungeY = FloatArray(N)
    internal val prot = FloatArray(N)
    internal val decT = FloatArray(N)
    internal val papel = IntArray(N)          // 0 GR, 1 defesa, 2 médio, 3 avançado
    internal val baseU = FloatArray(N); internal val baseV = FloatArray(N)
    internal val aRitmo = FloatArray(N); internal val aRemate = FloatArray(N); internal val aPasse = FloatArray(N)
    internal val aDrible = FloatArray(N); internal val aDefesa = FloatArray(N); internal val aFisico = FloatArray(N)
    internal val aGR = FloatArray(N)
    internal val alvoX = FloatArray(N); internal val alvoY = FloatArray(N)
    internal val alvoSprint = BooleanArray(N)
    internal val amarelos = IntArray(N)
    internal val parte = arrayOfNulls<Parte>(N)
    internal val partes = mutableListOf<Parte>()
    internal val banco = arrayOf(mutableListOf<Jogador>(), mutableListOf<Jogador>())
    internal val eventos = mutableListOf<Evento>()

    // Bola
    internal var bx = 0f; internal var by = 0f; internal var bz = 0f
    internal var bvx = 0f; internal var bvy = 0f; internal var bvz = 0f
    internal var dono = -1
    internal var receptor = -1
    internal var ultimoToque = -1
    internal var ultimoEq = -1
    internal val ultimoDaEq = intArrayOf(-1, -1)
    internal var passador = -1
    internal var tPasse = -99f
    internal var assistente = -1
    internal var rolouDefesa = true
    internal var gkAlvoY = FloatArray(2)
    internal var gkAtraso = FloatArray(2)
    internal var gkAtivo = BooleanArray(2)

    // Jogo
    internal var fase = FaseJogo.PONTAPE_SAIDA
    internal var segundaParte = false
    internal val golos = IntArray(2)
    internal var relogio = 0f
    internal var excedente = 0f
    internal var tFase = 0f
    internal var espera = 1.2f
    internal var eqReinicio = 0
    internal var cobrador = -1
    internal var controlado = -1
    internal var quadro = 0
    internal var msg: String? = null
    internal var msgT = 0f
    internal var marcador = -1
    internal var ultimoRemateEq = -1
    internal val taxa = 5400f / duracaoReal
    private val eqUserBase = if (semUtilizador) -1 else if (utilizadorCasa) 0 else 1

    /** Com isto ligado (modo «só momentos-chave»), a equipa do utilizador joga pela IA e os comandos ficam sem efeito. */
    override var iaControlaUtilizador: Boolean = false
        set(v) {
            field = v
            if (v) controlado = -1
        }

    internal val eqUser: Int get() = if (iaControlaUtilizador) -1 else eqUserBase
    internal val posseT = FloatArray(2)
    internal val remates = IntArray(2); internal val noAlvo = IntArray(2); internal val cantos = IntArray(2)
    internal val faltas = IntArray(2); internal val cartAm = IntArray(2); internal val cartVm = IntArray(2)
    internal var ultimoMinuto = 90
    internal val taker get() = cobrador
    internal val presser1 = IntArray(2); internal val presser2 = IntArray(2)
    internal val apoio1 = IntArray(2); internal val apoio2 = IntArray(2)
    internal var passeScore = 0f
    internal val dbg = IntArray(12)
    internal var passeLob = false
    internal val mentShift = floatArrayOf(deslocamento(casa), deslocamento(fora))
    internal val indisponiveis = arrayOf(casa.plantel.filter { !it.disponivel }, fora.plantel.filter { !it.disponivel })

    // Comandos
    private var cmdX = 0f; private var cmdY = 0f; private var cmdSprint = false
    internal var cmdForca = 0.5f
    internal var osPasse = false; internal var osRemate = false; internal var osTrocar = false
    private var acum = 0f
    internal var comX = 0f; internal var comY = 0f; internal var comSprint = false

    private val coresCasa: Pair<Long, Long>
    private val coresFora: Pair<Long, Long>
    private var fim = false

    init {
        val (cc, cf) = Cores.escolher(casa, fora)
        coresCasa = cc
        coresFora = cf
        montar(casa, 0)
        montar(fora, 1)
        iniciarReinicio(FaseJogo.PONTAPE_SAIDA, 0, 0f, 0f)
    }

    override val terminou: Boolean get() = fase == FaseJogo.FIM

    override val lances: List<Lance> get() = reg.lances

    override fun executarParada(comando: ComandoParada): Boolean = executarParadaUtilizador(comando)

    override fun escolherMergulho(lado: Int) { reg.mergulhoUser = lado.coerceIn(-1, 1) }

    private fun deslocamento(e: Equipa): Float = when (e.mentalidade) {
        pt.futebolcarreira.nucleo.modelos.Mentalidade.OFENSIVA -> 4f
        pt.futebolcarreira.nucleo.modelos.Mentalidade.DEFENSIVA -> -4f
        else -> 0f
    }

    internal fun eq(i: Int) = if (i < 11) 0 else 1

    /** Sentido de ataque no eixo x: +1 ou -1. */
    internal fun sinal(e: Int): Float = if ((e == 0) != segundaParte) 1f else -1f

    internal fun minutoAtual(): Int = floor(relogio / 60f).toInt()

    /** Minuto para os relatos (1 a 45 na primeira parte; depois 46 em diante). */
    internal fun minutoEvento(): Int = minutoDoPeriodo(minutoAtual() + 1)

    // ---------------------------------------------------------------- preparação

    private fun montar(equipa: Equipa, e: Int) {
        val onze = equipa.onzeParaJogo()
        val lugares = equipa.formacao.lugares
        val avancados = lugares.count { it == "PL" }
        for ((s, l) in onze.withIndex()) {
            val i = e * 11 + s
            val j = l.jogador ?: Jogador(id = -(i + 1), nome = "Suplente ${s + 1}", posicao = l.lugar).also { f ->
                for (a in Jogador.ATRIBUTOS) f.atributos[a] = 30
            }
            lugar[i] = l.lugar
            numero[i] = s + 1
            ativo[i] = true
            colocarJogador(i, j)
            // Lugar na formação
            val n = lugares.count { it == l.lugar }
            val k = lugares.take(s).count { it == l.lugar }
            val (u, v) = ponto(l.lugar, k, n, avancados)
            baseU[i] = u; baseV[i] = v
            papel[i] = when (l.lugar) {
                "GR" -> 0
                "DD", "DC", "DE" -> 1
                "MDC", "MC", "MOC" -> 2
                "PL" -> 3
                else -> if (u > 10f) 3 else 2
            }
            if (l.jogador != null) {
                val ficha = FichaJogador(j, l.lugar, true, 0)
                val p = Parte(e, j, ficha)
                parte[i] = p
                partes += p
            }
        }
        banco[e].addAll(equipa.suplentesParaJogo(onze))
    }

    internal fun colocarJogador(i: Int, j: Jogador) {
        jog[i] = j
        val b = j.bonusForma().toFloat()
        fun a(n: String) = (j.atributo(n) + b).coerceIn(1f, 99f)
        aRitmo[i] = a("ritmo"); aRemate[i] = a("remate"); aPasse[i] = a("passe"); aDrible[i] = a("drible")
        aDefesa[i] = a("defesa"); aFisico[i] = a("fisico"); aGR[i] = a("guarda_redes")
    }

    private fun ponto(l: String, k: Int, n: Int, avancados: Int): Pair<Float, Float> {
        fun espalhar(largura: Float): Float = if (n == 1) 0f else (k - (n - 1) / 2f) * (largura / (n - 1))
        return when (l) {
            "GR" -> -49f to 0f
            "DD" -> -30f to -24f
            "DE" -> -30f to 24f
            "DC" -> -32f to espalhar(if (n >= 3) 22f else 17f)
            "MDC" -> -16f to espalhar(16f)
            "MC" -> -6f to espalhar(22f)
            "MOC" -> 8f to 0f
            "ED" -> (if (avancados >= 2) 3f else 17f) to -26f
            "EE" -> (if (avancados >= 2) 3f else 17f) to 26f
            "PL" -> (if (n == 1) 26f else 24f) to espalhar(14f)
            else -> 0f to 0f
        }
    }

    // ---------------------------------------------------------------- ciclo principal

    override fun passo(dt: Float, comando: Comando) {
        cmdX = comando.movX.coerceIn(-1f, 1f); cmdY = comando.movY.coerceIn(-1f, 1f)
        cmdForca = comando.forca.coerceIn(0f, 1f); cmdSprint = comando.sprint
        if (comando.passe) osPasse = true
        if (comando.remate) osRemate = true
        if (comando.trocar) osTrocar = true
        acum += dt.coerceIn(0f, 0.5f)
        var correu = false
        while (acum >= K.SUB) {
            acum -= K.SUB
            substep()
            correu = true
        }
        if (correu) { osPasse = false; osRemate = false; osTrocar = false }
    }

    private fun substep() {
        val dt = K.SUB
        quadro++
        if (fase == FaseJogo.FIM) return
        tFase += dt
        if (msgT > 0f) { msgT -= dt; if (msgT <= 0f) msg = null }
        if (reg.desempate != null) { passoDesempate(dt); return }
        if (fase == FaseJogo.INTERVALO) {
            if (tFase >= 3f) iniciarPeriodoSeguinte()
            return
        }
        temporizadores(dt)
        if (fase == FaseJogo.GOLO) {
            if (tFase <= 4.5f) relogio += dt * taxa   // o tempo que os jogadores levam a voltar às posições não conta
            celebrar(dt)
            if (tFase >= 4.5f && (todosNasPosicoesDeSaida() || tFase >= 8f)) depoisDoGolo()
            return
        }
        relogio += dt * taxa
        comX = cmdX; comY = cmdY; comSprint = cmdSprint
        usarComando()
        if (quadro % 6 == 0) calcularAlvos()
        if (fase == FaseJogo.A_JOGAR) decisoesDoPortador(dt) else reinicioIA()
        for (i in 0 until N) if (ativo[i]) mover(i, dt)
        separar()
        moverBola(dt)
        if (fase == FaseJogo.A_JOGAR) {
            contactos()
            desarmes()
            tickRegras(dt)
            val e = if (dono >= 0) eq(dono) else ultimoEq
            if (e >= 0) posseT[e] += dt
        }
        for (i in 0 until N) if (ativo[i]) parte[i]?.let { it.segundos += dt }
        verificarFimDeParte(dt)
    }

    internal fun temporizadores(dt: Float) {
        for (i in 0 until N) {
            if (cd[i] > 0f) cd[i] -= dt
            if (atordoado[i] > 0f) atordoado[i] -= dt
            if (cdDesarme[i] > 0f) cdDesarme[i] -= dt
            if (carrinho[i] > 0f) { carrinho[i] -= dt; if (carrinho[i] <= 0f) atordoado[i] = 0.45f }
            if (prot[i] > 0f) prot[i] -= dt
            if (animT[i] > 0f) animT[i] -= dt
            if (decT[i] > 0f) decT[i] -= dt
        }
        for (e in 0..1) if (gkAtraso[e] > 0f) gkAtraso[e] -= dt
    }

    /** Velocidade de corrida normal (sem sprint) de um jogador, em m/s. */
    internal fun velMax(i: Int): Float {
        val base = 4.3f + 2.9f * aRitmo[i] / 100f
        return base * (0.82f + 0.18f * stam[i] / 100f) * dif(eq(i)).velocidade
    }

    internal fun mover(i: Int, dt: Float) {
        var dx = 0f; var dy = 0f; var spr = false
        val vmax = velMax(i)
        val controlo = i == controlado && fase != FaseJogo.GOLO
        if (atordoado[i] > 0f) {
            // caído: fica parado
        } else if (carrinho[i] > 0f) {
            val f = carrinho[i] / 0.55f
            dx = lungeX[i] * f; dy = lungeY[i] * f
        } else if (i == cobrador && fase != FaseJogo.A_JOGAR && cobChegou) {
            // quem cobra um lance parado espera junto à bola
        } else if (i == cobrador && fase != FaseJogo.A_JOGAR) {
            // ainda vai a caminho da bola
            val ex = cobX - px[i]; val ey = cobY - py[i]
            val d = hypot(ex, ey)
            if (d < 0.5f || tFase > 9f) {
                px[i] = cobX; py[i] = cobY; dir[i] = cobDir; vx[i] = 0f; vy[i] = 0f
                cobChegou = true; cobChegadaT = tFase
            } else {
                spr = d > 6f && stam[i] > 12f
                val v = min(if (spr) vmax * 1.28f else vmax * 1.05f, d * 3f)
                dx = ex / d * v; dy = ey / d * v
            }
        } else if (controlo) {
            val m = hypot(comX, comY)
            if (m > 0.05f) {
                spr = comSprint && stam[i] > 4f
                val v = (if (spr) vmax * 1.28f else vmax) * min(1f, m) * (if (dono == i) 0.93f + 0.07f * aDrible[i] / 100f else 1f)
                dx = comX / m * v; dy = comY / m * v
            }
        } else {
            val ex = alvoX[i] - px[i]; val ey = alvoY[i] - py[i]
            val d = hypot(ex, ey)
            if (d > 0.35f) {
                spr = alvoSprint[i] && stam[i] > 12f
                var v = if (spr) vmax * 1.28f else vmax
                if (dono == i) v *= 0.93f + 0.07f * aDrible[i] / 100f
                v = min(v, d * 3f)
                dx = ex / d * v; dy = ey / d * v
            }
        }
        // O guarda-redes que se lança à bola acelera muito mais (mergulho)
        val mergulho = papel[i] == 0 && gkAtivo[eq(i)] && gkAtraso[eq(i)] <= 0f
        if (mergulho) { dx *= 1.3f; dy *= 1.3f }
        var ax = dx - vx[i]; var ay = dy - vy[i]
        val l = hypot(ax, ay); val m = K.ACEL * dt * (if (mergulho) 3.5f else 1f)
        if (l > m) { ax *= m / l; ay *= m / l }
        vx[i] += ax; vy[i] += ay
        px[i] = (px[i] + vx[i] * dt).coerceIn(-K.MX - 4f, K.MX + 4f)
        py[i] = (py[i] + vy[i] * dt).coerceIn(-K.MY - 4f, K.MY + 4f)
        val s = hypot(vx[i], vy[i])
        if (s > 0.6f && carrinho[i] <= 0f) dir[i] = atan2(vy[i], vx[i])
        if (spr && s > vmax * 1.05f) stam[i] = max(0f, stam[i] - 0.55f * (1.3f - aFisico[i] * 0.006f) * dt)
        else if (s > 1f) stam[i] = max(0f, stam[i] - 0.03f * dt)
        else stam[i] = min(100f, stam[i] + 0.35f * dt)
        anim[i] = when {
            atordoado[i] > 0f -> Animacao.CAIDO
            carrinho[i] > 0f -> Animacao.CARRINHO
            animT[i] > 0f -> animEsp[i]
            s > vmax * 1.05f -> Animacao.SPRINT
            s > 0.5f -> Animacao.CORRER
            else -> Animacao.PARADO
        }
    }

    private fun separar() {
        for (i in 0 until N - 1) {
            if (!ativo[i]) continue
            for (j in i + 1 until N) {
                if (!ativo[j]) continue
                val dx = px[j] - px[i]; val dy = py[j] - py[i]
                val d2 = dx * dx + dy * dy
                if (d2 < 0.36f && d2 > 1e-6f) {
                    val d = kotlin.math.sqrt(d2)
                    val f = (0.6f - d) * 0.5f / d
                    px[i] -= dx * f; py[i] -= dy * f; px[j] += dx * f; py[j] += dy * f
                }
            }
        }
    }

    // ---------------------------------------------------------------- bola

    internal fun moverBola(dt: Float) {
        val parada = fase != FaseJogo.A_JOGAR
        if (parada && reg.desempate?.estado != EstadoChute.EM_VOO) return
        if (dono >= 0) {
            val a = dir[dono]
            bx = px[dono] + cos(a) * 0.6f; by = py[dono] + sin(a) * 0.6f; bz = 0f
            bvx = vx[dono]; bvy = vy[dono]; bvz = 0f
        } else {
            bvz -= K.G * dt
            bx += bvx * dt; by += bvy * dt; bz += bvz * dt
            if (bz <= 0f) {
                bz = 0f
                if (bvz < -2f) { bvz = -bvz * 0.55f; bvx *= 0.7f; bvy *= 0.7f } else bvz = 0f
            }
            if (bz == 0f && bvz == 0f) {
                val f = max(0f, 1f - K.ATRITO * dt)
                bvx *= f; bvy *= f
                if (hypot(bvx, bvy) < 0.25f) { bvx = 0f; bvy = 0f }
            } else {
                val f = 1f - 0.03f * dt
                bvx *= f; bvy *= f
            }
        }
        limites()
    }

    private fun limites() {
        if (reg.desempate != null && desempateLimites()) return
        if ((abs(bx) > K.MX || abs(by) > K.MY) && passador >= 0) dbg[1]++
        if (abs(bx) > K.MX) {
            val sgn = sign(bx)
            val ay = abs(by)
            val dentro = ay <= K.POSTE - 0.12f && bz <= K.TRAVESSAO - 0.12f
            val trave = !dentro && ay <= K.POSTE + 0.12f && bz <= K.TRAVESSAO + 0.12f
            if (dentro) {
                val marcou = if (sinal(0) == sgn) 0 else 1
                golo(marcou)
            } else if (trave && dono < 0) {
                bx = sgn * (K.MX - 0.05f)
                bvx = -bvx * 0.45f; bvy *= 0.7f
                if (bz > K.TRAVESSAO - 0.12f) bvz = -abs(bvz) * 0.5f
                rolouDefesa = true
            } else {
                val defEq = if (sinal(0) == -sgn) 0 else 1
                val atacante = 1 - defEq
                if (ultimoEq == defEq) {
                    cantos[atacante]++
                    iniciarReinicio(FaseJogo.CANTO, atacante, sgn * K.MX, if (by >= 0f) K.MY else -K.MY)
                    mensagem("Canto", 2f)
                } else {
                    iniciarReinicio(FaseJogo.PONTAPE_BALIZA, defEq, sgn * (K.MX - 5.5f), if (by >= 0f) 9f else -9f)
                }
            }
        } else if (abs(by) > K.MY) {
            val e = 1 - (if (ultimoEq < 0) 1 else ultimoEq)
            iniciarReinicio(FaseJogo.LANCAMENTO, e, bx.coerceIn(-K.MX + 1f, K.MX - 1f), sign(by) * K.MY)
        }
    }

    internal fun mensagem(texto: String, segundos: Float) { msg = texto; msgT = segundos }

    // ---------------------------------------------------------------- golos e lances parados

    private fun golo(eqMarcou: Int) {
        var autor = ultimoDaEq[eqMarcou]
        if (autor < 0 || !ativo[autor]) autor = (0 until 11).map { eqMarcou * 11 + it }.firstOrNull { ativo[it] && papel[it] == 3 } ?: (eqMarcou * 11 + 1)
        golos[eqMarcou]++
        marcador = autor
        val nome = jog[autor]!!.nome
        val min = minutoEvento()
        val assist = if (assistente >= 0 && assistente != autor && eq(assistente) == eqMarcou && relogio - tPasse < 12f * taxa) assistente else -1
        // A assistência só conta se o autor recebeu diretamente o passe.
        val gc = golos[0]; val gf = golos[1]
        val ja = jog[assist.coerceAtLeast(0)]
        if (parte[autor] != null) {
            eventos += Evento(min, TipoEvento.GOLO, eqMarcou == 0, jog[autor]!!, if (assist >= 0) ja else null, penaltiAtual, gc, gf)
            parte[autor]!!.ficha.golos++
            if (assist >= 0) parte[assist]?.ficha?.let { it.assistencias++ }
        }
        for (i in (1 - eqMarcou) * 11 until (1 - eqMarcou) * 11 + 11) if (ativo[i]) parte[i]?.ficha?.let { it.golosSofridos++ }
        penaltiAtual = false
        dono = -1
        bvx *= 0.2f; bvy *= 0.2f
        fase = FaseJogo.GOLO
        tFase = 0f
        mensagem("GOLO! $nome $min'", 4.4f)
        eqReinicio = 1 - eqMarcou
        for (i in 0 until N) { vx[i] *= 0.5f; vy[i] *= 0.5f }
    }

    internal var penaltiAtual = false

    // Quem cobra um lance parado corre até à bola em vez de aparecer lá de repente.
    internal var cobX = 0f
    internal var cobY = 0f
    internal var cobDir = 0f
    internal var cobChegou = true
    /** Instante (tFase) em que o cobrador chegou à bola; conta o tempo de espera e o tempo do utilizador. */
    internal var cobChegadaT = 0f
    /** Segundos desde que o cobrador chegou à bola. */
    internal val tCobranca: Float get() = tFase - cobChegadaT

    private fun celebrar(dt: Float) {
        // A bola acaba dentro da baliza; o marcador festeja.
        bvx *= 0.97f; bvy *= 0.97f
        bvz -= K.G * dt
        bx += bvx * dt; by += bvy * dt; bz = max(0f, bz + bvz * dt)
        if (bz == 0f) bvz = 0f
        bx = bx.coerceIn(-K.MX - 2.2f, K.MX + 2.2f)
        for (i in 0 until N) {
            if (!ativo[i]) continue
            alvoX[i] = px[i]; alvoY[i] = py[i]; alvoSprint[i] = false
            if (i == marcador) { animEsp[i] = Animacao.FESTEJAR; animT[i] = 0.2f }
        }
        for (i in 0 until N) {
            if (!ativo[i]) continue
            // Depois de festejar, todos voltam a correr para o seu sítio do pontapé de saída.
            if (tFase > (if (i == marcador) 1.8f else 1.2f)) {
                val (sx, sy) = slotSaida(i, eqReinicio)
                val ex = sx - px[i]; val ey = sy - py[i]
                val d = hypot(ex, ey)
                if (d > 0.4f) {
                    val v = min(10f, d * 3f)
                    vx[i] = ex / d * v; vy[i] = ey / d * v
                    px[i] += vx[i] * dt; py[i] += vy[i] * dt
                    dir[i] = atan2(vy[i], vx[i])
                    anim[i] = if (v > 6f) Animacao.SPRINT else Animacao.CORRER
                } else {
                    vx[i] = 0f; vy[i] = 0f
                    anim[i] = Animacao.PARADO
                }
                continue
            }
            val s = hypot(vx[i], vy[i])
            val f = max(0f, 1f - 4f * dt)
            vx[i] *= f; vy[i] *= f
            px[i] += vx[i] * dt; py[i] += vy[i] * dt
            anim[i] = if (i == marcador) Animacao.FESTEJAR else if (s > 0.5f) Animacao.CORRER else Animacao.PARADO
        }
    }

    /** Onde o jogador i fica no pontapé de saída (a equipa que não pontapeia fica na sua metade). */
    internal fun slotSaida(i: Int, eqSaida: Int): Pair<Float, Float> {
        val e = eq(i); val s = sinal(e)
        var u = baseU[i]
        if (e == eqSaida) u = min(u, -2f) else u = min(u, -12f)
        return (u * s) to (baseV[i] * s)
    }

    private fun todosNasPosicoesDeSaida(): Boolean {
        for (i in 0 until N) {
            if (!ativo[i]) continue
            val (sx, sy) = slotSaida(i, eqReinicio)
            if (hypot(sx - px[i], sy - py[i]) > 1.0f) return false
        }
        return true
    }

    private fun depoisDoGolo() {
        if (fimDeParteChegou()) { acabarParte(); return }
        iniciarReinicio(FaseJogo.PONTAPE_SAIDA, eqReinicio, 0f, 0f)
    }

    /** Posiciona os jogadores para o pontapé de saída (a equipa que não pontapeia fica na sua metade). */
    private fun reposicionar(eqSaida: Int) {
        for (i in 0 until N) {
            if (!ativo[i]) continue
            val e = eq(i); val s = sinal(e)
            var u = baseU[i]
            if (e == eqSaida) u = min(u, -2f) else u = min(u, -12f)
            px[i] = u * s; py[i] = baseV[i] * s
            vx[i] = 0f; vy[i] = 0f
            dir[i] = if (s > 0f) 0f else Math.PI.toFloat()
            atordoado[i] = 0f; carrinho[i] = 0f; cd[i] = 0f; prot[i] = 0f; animT[i] = 0f
            alvoX[i] = px[i]; alvoY[i] = py[i]
        }
    }

    /** Põe a bola num ponto, escolhe quem cobra e passa para uma fase de lance parado. */
    internal fun iniciarReinicio(nova: FaseJogo, e: Int, x: Float, y: Float, forcarCobrador: Int = -1) {
        fase = nova
        tFase = 0f
        eqReinicio = e
        dono = -1; receptor = -1
        bx = x; by = y; bz = 0f; bvx = 0f; bvy = 0f; bvz = 0f
        rolouDefesa = true
        penaltiAtual = nova == FaseJogo.PENALTI
        gkAtivo[0] = false; gkAtivo[1] = false
        val s = sinal(e)
        if (nova == FaseJogo.PONTAPE_SAIDA) reposicionar(e)
        // Quem cobra
        var c = -1
        val base = e * 11
        when (nova) {
            FaseJogo.PONTAPE_BALIZA -> c = (base until base + 11).firstOrNull { ativo[it] && papel[it] == 0 } ?: -1
            FaseJogo.PENALTI -> c = (base until base + 11).filter { ativo[it] && papel[it] != 0 }.maxByOrNull { aRemate[it] } ?: -1
            FaseJogo.PONTAPE_SAIDA -> c = (base until base + 11).filter { ativo[it] && papel[it] == 3 }.maxByOrNull { baseU[it] } ?: (base until base + 11).firstOrNull { ativo[it] && papel[it] != 0 } ?: -1
            FaseJogo.CANTO, FaseJogo.LIVRE, FaseJogo.LANCAMENTO ->
                c = (base until base + 11).filter { ativo[it] && papel[it] != 0 }.minByOrNull { hypot(px[it] - x, py[it] - y) - (if (nova == FaseJogo.CANTO) aPasse[it] * 0.15f else 0f) } ?: -1
            else -> {}
        }
        if (forcarCobrador >= 0 && ativo[forcarCobrador] && eq(forcarCobrador) == e) c = forcarCobrador
        if (c < 0) c = (base until base + 11).firstOrNull { ativo[it] } ?: base
        cobrador = c
        // Posição de quem cobra e direção. Se está longe da bola, corre até lá (só o pontapé de saída é instantâneo).
        cobChegou = true; cobChegadaT = 0f
        if (reg.desempate == null && nova != FaseJogo.PONTAPE_SAIDA) {
            var sx = 0f; var sy = 0f; var sd = 0f
            when (nova) {
                FaseJogo.LANCAMENTO -> { sx = x; sy = y + sign(y) * 0.8f; sd = atan2(-y, 0.001f) }
                FaseJogo.CANTO -> { sx = x - sign(x) * 0.6f; sy = y + sign(y) * 0.6f; sd = atan2(-y, -x) }
                FaseJogo.PENALTI -> { val g = sign(x); sx = x - g * 1.5f; sy = 0f; sd = if (g > 0f) 0f else Math.PI.toFloat() }
                else -> { val a = atan2(-y, -x); sx = x - cos(a) * 0.8f; sy = y - sin(a) * 0.8f; sd = a }
            }
            if (hypot(px[c] - sx, py[c] - sy) > 2.5f) {
                cobX = sx; cobY = sy; cobDir = sd; cobChegou = false
            }
        }
        when (nova) {
            FaseJogo.PONTAPE_SAIDA -> { px[c] = -0.8f * s; py[c] = 0f; dir[c] = if (s > 0f) 0f else Math.PI.toFloat() }
            FaseJogo.LANCAMENTO -> if (cobChegou) { px[c] = x; py[c] = y + sign(y) * 0.8f; dir[c] = atan2(-y, 0.001f) }
            FaseJogo.CANTO -> if (cobChegou) { px[c] = x - sign(x) * 0.6f; py[c] = y + sign(y) * 0.6f; dir[c] = atan2(-y, -x) }
            FaseJogo.PENALTI -> if (!cobChegou) {
                val g = sign(x)
                val gr = ((1 - e) * 11 until (1 - e) * 11 + 11).firstOrNull { ativo[it] && papel[it] == 0 }
                if (gr != null) { px[gr] = g * (K.MX - 0.5f); py[gr] = 0f; vx[gr] = 0f; vy[gr] = 0f; dir[gr] = if (g > 0f) Math.PI.toFloat() else 0f }
            } else {
                val g = sign(x) // baliza que ataca
                px[c] = x - g * 1.5f; py[c] = 0f; dir[c] = if (g > 0f) 0f else Math.PI.toFloat()
                val gr = ((1 - e) * 11 until (1 - e) * 11 + 11).firstOrNull { ativo[it] && papel[it] == 0 }
                if (gr != null) { px[gr] = g * (K.MX - 0.5f); py[gr] = 0f; vx[gr] = 0f; vy[gr] = 0f; dir[gr] = if (g > 0f) Math.PI.toFloat() else 0f }
            }
            else -> if (cobChegou) {
                val a = atan2(-y, -x)
                px[c] = x - cos(a) * 0.8f; py[c] = y - sin(a) * 0.8f; dir[c] = a
            }
        }
        if (cobChegou) { vx[c] = 0f; vy[c] = 0f }
        atordoado[c] = 0f
        alvoX[c] = if (cobChegou) px[c] else cobX; alvoY[c] = if (cobChegou) py[c] else cobY
        espera = if (nova == FaseJogo.PONTAPE_SAIDA) 1.5f else 1.0f + rng.nextFloat() * 0.8f
        if (nova == FaseJogo.PENALTI) espera = 2.0f
        // Quem o utilizador comanda
        if (e == eqUser) controlado = c
        else controlado = maisPerto(eqUser, x, y, false)
        aoIniciarReinicio(nova, e, x, y)
    }

    private fun fimDeParteChegou(): Boolean = relogio >= limiteDoPeriodo()

    private fun verificarFimDeParte(dt: Float) {
        if (!fimDeParteChegou()) return
        excedente += dt
        if (fase != FaseJogo.A_JOGAR || excedente > 4f) acabarParte()
    }

    private fun acabarParte() {
        excedente = 0f
        dono = -1
        acabarPeriodo()
    }

    // ---------------------------------------------------------------- utilitários partilhados

    /** O jogador mais perto de (x, y) de uma equipa (sem o guarda-redes se "comGR" for falso). */
    internal fun maisPerto(e: Int, x: Float, y: Float, comGR: Boolean, excluir: Int = -1): Int {
        if (e !in 0..1) return -1
        var melhor = -1; var md = Float.MAX_VALUE
        for (i in e * 11 until e * 11 + 11) {
            if (!ativo[i] || i == excluir || (!comGR && papel[i] == 0)) continue
            val d = (px[i] - x) * (px[i] - x) + (py[i] - y) * (py[i] - y)
            if (d < md) { md = d; melhor = i }
        }
        return if (melhor < 0) (if (e in 0..1) e * 11 else -1) else melhor
    }

    internal fun distJ(i: Int, x: Float, y: Float) = hypot(px[i] - x, py[i] - y)

    internal fun gauss(): Float {
        val u1 = 1f - rng.nextFloat(); val u2 = rng.nextFloat()
        return (kotlin.math.sqrt(-2f * kotlin.math.ln(u1)) * cos(2f * Math.PI.toFloat() * u2))
    }

    // ---------------------------------------------------------------- estado para desenhar

    override val estado: EstadoPartida
        get() {
            val lista = ArrayList<JogadorEmCampo>(22)
            for (i in 0 until N) {
                if (!ativo[i]) continue
                val j = jog[i]!!
                lista += JogadorEmCampo(j.id, i < 11, numero[i], lugar[i], j.nome, px[i], py[i], dir[i], anim[i], i == controlado)
            }
            val gs = relogio
            return EstadoPartida(
                minutoAtual(), gs - floor(gs / 60f) * 60f, golos[0], golos[1], fase, segundaParte, lista,
                Bola(bx, by, bz), coresCasa, coresFora, casa.abreviatura, fora.abreviatura, msg,
                reg.periodo, construirBolaParada(), estadoDoDesempate(), reg.vantagemEq >= 0, dificuldade, reg.lances.size,
            )
        }
}
