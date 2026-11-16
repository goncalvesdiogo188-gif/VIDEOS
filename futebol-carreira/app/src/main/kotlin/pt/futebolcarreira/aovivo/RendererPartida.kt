package pt.futebolcarreira.aovivo

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import pt.futebolcarreira.nucleo.aovivo.Animacao
import pt.futebolcarreira.nucleo.aovivo.EstadoPartida
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Desenha o jogo com OpenGL ES 2.0. Corre no fio do GLSurfaceView: a cada imagem avança o motor
 * ([SessaoAoVivo.avancar]) e desenha o estado. Tudo o que é fixo (campo, balizas, bancadas) é calculado uma vez;
 * durante o jogo não se criam objetos novos, só se reaproveitam as matrizes.
 */
class RendererPartida(private val sessao: SessaoAoVivo, private val estiloPs1: Boolean = true) : GLSurfaceView.Renderer {
    private val camera = CameraTransmissao()
    private var proporcao = 2f
    private var ultimoNs = 0L
    private var tempo = 0f

    // OpenGL
    private var programa = 0
    private var uMvp = 0
    private var uModelo = 0
    private var uCor = 0
    private var uLuz = 0
    private var uRes = 0
    private var uPs1 = 0
    private var uPs1F = 0
    private var uNevoa = 0
    private var vbo = 0

    // Estilo PlayStation 1: o jogo desenha-se numa imagem pequena (altura ALTURA_PS1) e depois amplia-se sem suavizar.
    private var fbo = 0
    private var fboTextura = 0
    private var fboProfundidade = 0
    private var fboLargura = 0
    private var fboAltura = 0
    private var fboOk = false
    private var ecraLargura = 1
    private var ecraAltura = 1
    private var programaEcra = 0
    private var uTexEcra = 0
    private val quadro: java.nio.FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f)); position(0)
    }
    private lateinit var malhas: Malhas.Conjunto

    // Matrizes reaproveitadas
    private val projecao = FloatArray(16)
    private val vista = FloatArray(16)
    private val vp = FloatArray(16)
    private val modelo = FloatArray(16)
    private val base = FloatArray(16)
    private val parte = FloatArray(16)
    private val mvp = FloatArray(16)

    // Cenário fixo, com as matrizes já feitas
    private class Fixos(val pecas: List<Peca>) {
        val matrizes = FloatArray(16 * pecas.size)
        val cores = FloatArray(4 * pecas.size)
    }
    private lateinit var chao: Fixos
    private lateinit var balizas: Fixos
    private lateinit var bancadas: Fixos

    // Animação: fase da corrida de cada jogador (por posição na lista).
    private val fases = FloatArray(40)
    private val cor = FloatArray(4)
    private val coresEquipa = FloatArray(12) // camisola, calções, meias de cada lado (r,g,b) ... ver preencherCores
    private var coresDe: EstadoPartida? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.34f, 0.42f, 0.55f, 1f)
        programa = criarPrograma(VERTICE, FRAGMENTO)
        uMvp = GLES20.glGetUniformLocation(programa, "uMvp")
        uModelo = GLES20.glGetUniformLocation(programa, "uModelo")
        uCor = GLES20.glGetUniformLocation(programa, "uCor")
        uLuz = GLES20.glGetUniformLocation(programa, "uLuz")
        uRes = GLES20.glGetUniformLocation(programa, "uRes")
        uPs1 = GLES20.glGetUniformLocation(programa, "uPs1")
        uPs1F = GLES20.glGetUniformLocation(programa, "uPs1F")
        uNevoa = GLES20.glGetUniformLocation(programa, "uNevoa")
        programaEcra = criarPrograma(VERTICE_ECRA, FRAGMENTO_ECRA)
        uTexEcra = GLES20.glGetUniformLocation(programaEcra, "uTex")
        fbo = 0; fboTextura = 0; fboProfundidade = 0; fboOk = false   // o contexto pode ter sido recriado

        malhas = Malhas.criar(poucosPoligonos = estiloPs1)
        val buf = ByteBuffer.allocateDirect(malhas.dados.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(malhas.dados).position(0)
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, malhas.dados.size * 4, buf, GLES20.GL_STATIC_DRAW)

        chao = fixos(CenarioCampo.chao())
        balizas = fixos(CenarioCampo.balizas())
        bancadas = fixos(CenarioCampo.bancadas())
        ultimoNs = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, largura: Int, altura: Int) {
        GLES20.glViewport(0, 0, largura, altura)
        ecraLargura = largura; ecraAltura = altura
        proporcao = largura.toFloat() / altura.coerceAtLeast(1)
        if (estiloPs1) criarImagemPequena(largura, altura)
        Matrix.perspectiveM(projecao, 0, camera.campoDeVisaoV, proporcao, 8f, 260f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val agora = System.nanoTime()
        sessao.medidor.imagem(agora)
        val dt = if (ultimoNs == 0L) 0f else ((agora - ultimoNs) / 1e9f).coerceIn(0f, 0.1f)
        ultimoNs = agora
        tempo += dt
        sessao.avancar(dt)
        val e = sessao.partida.estado
        camera.atualizar(e.bola.x, e.bola.y, dt, sessao.sentido())
        preencherCores(e)

        Matrix.setLookAtM(vista, 0, camera.olhoX, camera.olhoY, camera.olhoZ, camera.alvoX, camera.alvoY, 0f, 0f, 0f, 1f)
        Matrix.multiplyMM(vp, 0, projecao, 0, vista, 0)

        val ps1 = estiloPs1 && fboOk
        if (ps1) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            GLES20.glViewport(0, 0, fboLargura, fboAltura)
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(programa)
        GLES20.glUniform1f(uPs1, if (ps1) 1f else 0f)
        GLES20.glUniform1f(uPs1F, if (ps1) 1f else 0f)
        GLES20.glUniform2f(uRes, if (ps1) fboLargura.toFloat() else ecraLargura.toFloat(), if (ps1) fboAltura.toFloat() else ecraAltura.toFloat())
        GLES20.glUniform3f(uNevoa, NEVOA_R, NEVOA_G, NEVOA_B)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 24, 0)
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glVertexAttribPointer(1, 3, GLES20.GL_FLOAT, false, 24, 12)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // 1) Chão, sem profundidade: o que vem depois fica por cima.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        desenharFixos(chao, 0f)
        desenharSombrasEAneis(e)

        // 2) Coisas com altura, com profundidade.
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        desenharFixos(bancadas, 1f)
        desenharFixos(balizas, 1f, transparentes = false)
        for (i in 0 until e.jogadores.size) desenharJogador(e, i, dt)
        desenharBola(e)

        // 3) Redes das balizas, transparentes e sem escrever profundidade.
        GLES20.glDepthMask(false)
        desenharFixos(balizas, 1f, transparentes = true)
        GLES20.glDepthMask(true)

        if (ps1) ampliarImagemPequena()
    }

    // ----------------------------------------------------- estilo PS1

    /** Cria a imagem pequena em que o jogo se desenha (nítida ao ampliar, como nos ecrãs de há 25 anos). */
    private fun criarImagemPequena(largura: Int, altura: Int) {
        if (fbo != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            GLES20.glDeleteTextures(1, intArrayOf(fboTextura), 0)
            GLES20.glDeleteRenderbuffers(1, intArrayOf(fboProfundidade), 0)
        }
        fboAltura = ALTURA_PS1.coerceAtMost(altura.coerceAtLeast(1))
        fboLargura = (fboAltura * largura.toFloat() / altura.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        val id = IntArray(1)
        GLES20.glGenTextures(1, id, 0); fboTextura = id[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextura)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, fboLargura, fboAltura, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glGenRenderbuffers(1, id, 0); fboProfundidade = id[0]
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, fboProfundidade)
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, fboLargura, fboAltura)
        GLES20.glGenFramebuffers(1, id, 0); fbo = id[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTextura, 0)
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, fboProfundidade)
        // Se o telemóvel não aceitar, o jogo desenha-se normalmente (sem imagem pequena).
        fboOk = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    /** Põe a imagem pequena no ecrã inteiro, sem suavizar. */
    private fun ampliarImagemPequena() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, ecraLargura, ecraAltura)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(programaEcra)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextura)
        GLES20.glUniform1i(uTexEcra, 0)
        quadro.position(0)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 16, quadro)
        quadro.position(2)
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 16, quadro)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        quadro.position(0)
    }

    // ------------------------------------------------------------- fixos

    private fun fixos(pecas: List<Peca>): Fixos {
        val f = Fixos(pecas)
        val m = FloatArray(16)
        for ((i, p) in pecas.withIndex()) {
            Matrix.setIdentityM(m, 0)
            Matrix.translateM(m, 0, p.x, p.y, p.z)
            if (p.rotacao != 0f) Mat4.rodar(m, Math.toDegrees(p.rotacao.toDouble()).toFloat(), 0f, 0f, 1f)
            when (p.forma) {
                Forma.CAIXA -> Matrix.scaleM(m, 0, p.sx, p.sy, p.sz)
                else -> Matrix.scaleM(m, 0, p.sx / 2f, p.sy / 2f, 1f)
            }
            System.arraycopy(m, 0, f.matrizes, 16 * i, 16)
            f.cores[4 * i] = ((p.cor shr 16) and 0xFF) / 255f
            f.cores[4 * i + 1] = ((p.cor shr 8) and 0xFF) / 255f
            f.cores[4 * i + 2] = (p.cor and 0xFF) / 255f
            f.cores[4 * i + 3] = if (p.transparente) 0.28f else ((p.cor ushr 24) and 0xFF) / 255f
        }
        return f
    }

    private fun desenharFixos(f: Fixos, luz: Float, transparentes: Boolean? = null) {
        for (i in f.pecas.indices) {
            val p = f.pecas[i]
            if (transparentes != null && p.transparente != transparentes) continue
            System.arraycopy(f.matrizes, 16 * i, modelo, 0, 16)
            val malha = when (p.forma) { Forma.CAIXA -> malhas.caixa; Forma.DISCO -> malhas.disco; Forma.ANEL -> malhas.anel }
            desenhar(malha, f.cores[4 * i], f.cores[4 * i + 1], f.cores[4 * i + 2], f.cores[4 * i + 3], luz)
        }
    }

    // ----------------------------------------------------- sombras e aneis

    private fun desenharSombrasEAneis(e: EstadoPartida) {
        val bola = e.bola
        val lista = e.jogadores
        // Quem tem a bola: o mais perto, se ela estiver baixa e a menos de 1,6 m.
        var dono = -1
        var melhor = 1.6f
        if (bola.z < 1.5f) {
            for (i in 0 until lista.size) {
                val d = hypot(lista[i].x - bola.x, lista[i].y - bola.y)
                if (d < melhor) { melhor = d; dono = i }
            }
        }
        for (i in 0 until lista.size) {
            val j = lista[i]
            if (i == dono) anel(j.x, j.y, 0.95f, 0.95f, 0.95f, 0.95f, 0.9f)
            if (j.controlado) anel(j.x, j.y, 1.15f, 1.0f, 0.85f, 0.1f, 1f)
            Matrix.setIdentityM(modelo, 0)
            Matrix.translateM(modelo, 0, j.x, j.y, 0.04f)
            Matrix.scaleM(modelo, 0, 0.55f, 0.4f, 1f)
            desenhar(malhas.disco, 0f, 0f, 0f, 0.3f, 0f)
        }
        // Sombra da bola: mais pequena e mais clara quanto mais alta.
        val s = 0.42f + bola.z * 0.03f
        Matrix.setIdentityM(modelo, 0)
        Matrix.translateM(modelo, 0, bola.x, bola.y, 0.05f)
        Matrix.scaleM(modelo, 0, s, s, 1f)
        desenhar(malhas.disco, 0f, 0f, 0f, (0.45f - bola.z * 0.05f).coerceAtLeast(0.15f), 0f)
    }

    private fun anel(x: Float, y: Float, raio: Float, r: Float, g: Float, b: Float, a: Float) {
        Matrix.setIdentityM(modelo, 0)
        Matrix.translateM(modelo, 0, x, y, 0.06f)
        Matrix.scaleM(modelo, 0, raio, raio, 1f)
        desenhar(malhas.anelGrosso, r, g, b, a, 0f)
    }

    // ---------------------------------------------------------- jogadores

    /** Guarda em "coresEquipa" as cores das camisolas e calções (r,g,b): casa 0..5, fora 6..11. */
    private fun preencherCores(e: EstadoPartida) {
        if (coresDe === e || (coresDe != null && coresDe!!.coresCasa == e.coresCasa && coresDe!!.coresFora == e.coresFora)) { coresDe = e; return }
        coresDe = e
        var fora = e.coresFora
        // Se as duas camisolas são parecidas, a de fora joga com a cor dos calções.
        if (distancia(e.coresCasa.first, fora.first) < 90) fora = fora.second to fora.first
        guardarCor(0, e.coresCasa.first); guardarCor(3, e.coresCasa.second)
        guardarCor(6, fora.first); guardarCor(9, fora.second)
    }

    private fun guardarCor(i: Int, argb: Long) {
        coresEquipa[i] = ((argb shr 16) and 0xFF) / 255f
        coresEquipa[i + 1] = ((argb shr 8) and 0xFF) / 255f
        coresEquipa[i + 2] = (argb and 0xFF) / 255f
    }

    private fun distancia(a: Long, b: Long): Int =
        abs(((a shr 16) and 0xFF).toInt() - ((b shr 16) and 0xFF).toInt()) + abs(((a shr 8) and 0xFF).toInt() - ((b shr 8) and 0xFF).toInt()) +
            abs((a and 0xFF).toInt() - (b and 0xFF).toInt())

    private fun desenharJogador(e: EstadoPartida, i: Int, dt: Float) {
        val j = e.jogadores[i]
        val c = if (j.casa) 0 else 6
        val gr = j.posicao == "GR"
        // Guarda-redes: camisola de cor própria.
        val cr = if (gr) (if (j.casa) 0.96f else 0.18f) else coresEquipa[c]
        val cg = if (gr) (if (j.casa) 0.82f else 0.80f) else coresEquipa[c + 1]
        val cb = if (gr) (if (j.casa) 0.25f else 0.44f) else coresEquipa[c + 2]
        val sr = coresEquipa[c + 3]; val sg = coresEquipa[c + 4]; val sb = coresEquipa[c + 5]
        val pele = (j.id % 3)
        val pr = 0.93f - 0.18f * pele; val pg = 0.74f - 0.17f * pele; val pb = 0.60f - 0.16f * pele

        // Fase da passada: avança mais depressa a sprintar.
        val slot = i % fases.size
        val corre = j.animacao == Animacao.CORRER || j.animacao == Animacao.SPRINT
        if (corre || j.animacao == Animacao.FESTEJAR) fases[slot] += dt * (if (j.animacao == Animacao.SPRINT) 13f else 9f)
        val fase = fases[slot]
        var pernaD = 0f; var pernaE = 0f; var bracoD = 0f; var bracoE = 0f
        var inclina = 0f; var rola = 0f; var descer = 0f
        when (j.animacao) {
            Animacao.CORRER -> { val s = sin(fase); pernaD = 42f * s; pernaE = -42f * s; bracoD = -32f * s; bracoE = 32f * s }
            Animacao.SPRINT -> { val s = sin(fase); pernaD = 58f * s; pernaE = -58f * s; bracoD = -50f * s; bracoE = 50f * s; inclina = 12f }
            Animacao.PASSAR -> { pernaD = -38f; pernaE = 14f; bracoD = 20f; bracoE = -20f }
            Animacao.REMATAR -> { pernaD = -78f; pernaE = 22f; bracoD = 40f; bracoE = -50f; inclina = -6f }
            Animacao.CARRINHO -> { pernaD = -85f; pernaE = -70f; bracoD = 60f; bracoE = 80f; inclina = -70f; descer = -0.45f }
            Animacao.CAIDO -> { pernaD = 8f; pernaE = -5f; bracoD = 20f; bracoE = 30f; inclina = -90f; descer = -0.72f }
            Animacao.DEFESA_GR -> { bracoD = 165f; bracoE = 165f; pernaD = 15f; pernaE = -15f; rola = 78f; descer = -0.5f }
            Animacao.FESTEJAR -> { bracoD = 165f; bracoE = 165f; descer = abs(sin(fase * 0.8f)) * 0.3f }
            Animacao.PARADO -> {}
        }

        // Base: posição e direção do jogador no campo.
        Matrix.setIdentityM(base, 0)
        Matrix.translateM(base, 0, j.x, j.y, 0f)
        Mat4.rodar(base, Math.toDegrees(j.direcao.toDouble()).toFloat(), 0f, 0f, 1f)
        // Corpo inteiro roda em torno da anca (inclinação para a frente/trás, queda para o lado).
        Matrix.translateM(base, 0, 0f, 0f, ANCA + descer)
        if (rola != 0f) Mat4.rodar(base, rola, 1f, 0f, 0f)
        if (inclina != 0f) Mat4.rodar(base, inclina, 0f, 1f, 0f)
        Matrix.translateM(base, 0, 0f, 0f, -ANCA)

        // Pernas (meias na cor dos calções escurecida) e braços.
        perna(0.12f, pernaD, sr * 0.8f + 0.1f, sg * 0.8f + 0.1f, sb * 0.8f + 0.1f)
        perna(-0.12f, pernaE, sr * 0.8f + 0.1f, sg * 0.8f + 0.1f, sb * 0.8f + 0.1f)
        braco(0.31f, bracoD, cr, cg, cb)
        braco(-0.31f, bracoE, cr, cg, cb)
        // Calções, tronco e cabeça.
        caixa(0f, 0f, 0.9f, 0.27f, 0.46f, 0.3f, sr, sg, sb)
        caixa(0f, 0f, 1.27f, 0.28f, 0.5f, 0.62f, cr, cg, cb)
        System.arraycopy(base, 0, modelo, 0, 16)
        Matrix.translateM(modelo, 0, 0f, 0f, 1.74f)
        Matrix.scaleM(modelo, 0, 0.14f, 0.14f, 0.15f)
        desenhar(malhas.esfera, pr, pg, pb, 1f, 1f)
    }

    private fun caixa(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float) {
        System.arraycopy(base, 0, modelo, 0, 16)
        Matrix.translateM(modelo, 0, x, y, z)
        Matrix.scaleM(modelo, 0, sx, sy, sz)
        desenhar(malhas.caixa, r, g, b, 1f, 1f)
    }

    /** Perna que roda em torno da anca: "angulo" em graus (positivo = pé para trás). */
    private fun perna(y: Float, angulo: Float, r: Float, g: Float, b: Float) {
        System.arraycopy(base, 0, parte, 0, 16)
        Matrix.translateM(parte, 0, 0f, y, ANCA)
        Mat4.rodar(parte, angulo, 0f, 1f, 0f)
        Matrix.translateM(parte, 0, 0f, 0f, -0.42f)
        Matrix.scaleM(parte, 0, 0.17f, 0.17f, 0.84f)
        System.arraycopy(parte, 0, modelo, 0, 16)
        desenhar(malhas.caixa, r, g, b, 1f, 1f)
    }

    private fun braco(y: Float, angulo: Float, r: Float, g: Float, b: Float) {
        System.arraycopy(base, 0, parte, 0, 16)
        Matrix.translateM(parte, 0, 0f, y, 1.52f)
        Mat4.rodar(parte, angulo, 0f, 1f, 0f)
        Matrix.translateM(parte, 0, 0f, 0f, -0.3f)
        Matrix.scaleM(parte, 0, 0.12f, 0.12f, 0.6f)
        System.arraycopy(parte, 0, modelo, 0, 16)
        desenhar(malhas.caixa, r, g, b, 1f, 1f)
    }

    // --------------------------------------------------------------- bola

    private fun desenharBola(e: EstadoPartida) {
        val b = e.bola
        Matrix.setIdentityM(modelo, 0)
        Matrix.translateM(modelo, 0, b.x, b.y, b.z + RAIO_BOLA)
        Matrix.scaleM(modelo, 0, RAIO_BOLA, RAIO_BOLA, RAIO_BOLA)
        desenhar(malhas.esfera, 0.98f, 0.98f, 0.98f, 1f, 1f)
    }

    // ----------------------------------------------------------- comum

    private fun desenhar(m: Malha, r: Float, g: Float, b: Float, a: Float, luz: Float) {
        Matrix.multiplyMM(mvp, 0, vp, 0, modelo, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModelo, 1, false, modelo, 0)
        GLES20.glUniform4f(uCor, r, g, b, a)
        GLES20.glUniform1f(uLuz, luz)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, m.inicio, m.vertices)
    }

    private fun criarPrograma(vertice: String, fragmento: String): Int {
        val v = compilar(GLES20.GL_VERTEX_SHADER, vertice)
        val f = compilar(GLES20.GL_FRAGMENT_SHADER, fragmento)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glBindAttribLocation(p, 0, "aPos")
        GLES20.glBindAttribLocation(p, 1, "aNor")
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("Erro nos shaders: " + GLES20.glGetProgramInfoLog(p))
        return p
    }

    private fun compilar(tipo: Int, codigo: String): Int {
        val s = GLES20.glCreateShader(tipo)
        GLES20.glShaderSource(s, codigo)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("Erro num shader: " + GLES20.glGetShaderInfoLog(s))
        return s
    }

    private companion object {
        const val ANCA = 0.85f
        const val RAIO_BOLA = 0.38f   // maior que a bola verdadeira (0,11 m) para se ver bem

        const val ALTURA_PS1 = 240       // linhas da imagem pequena (a PlayStation usava 240)
        const val NEVOA_R = 0.34f
        const val NEVOA_G = 0.42f
        const val NEVOA_B = 0.55f

        const val VERTICE = """
            uniform mat4 uMvp;
            uniform mat4 uModelo;
            uniform vec2 uRes;
            uniform float uPs1;
            attribute vec3 aPos;
            attribute vec3 aNor;
            varying vec3 vNor;
            varying float vFundo;
            void main() {
                vec4 p = uMvp * vec4(aPos, 1.0);
                if (uPs1 > 0.5 && p.w > 0.5) {
                    // Os vértices «saltam» para a grelha de píxeis, como na PlayStation (o tremer das arestas).
                    vec2 g = uRes * 0.5;
                    p.xy = floor(p.xy / p.w * g + 0.5) / g * p.w;
                }
                gl_Position = p;
                vFundo = p.w;
                vNor = (uModelo * vec4(aNor, 0.0)).xyz;
            }
        """
        const val FRAGMENTO = """
            precision mediump float;
            uniform vec4 uCor;
            uniform float uLuz;
            uniform float uPs1F;
            uniform vec3 uNevoa;
            varying vec3 vNor;
            varying float vFundo;
            // Matriz 4x4 de Bayer (tremido ordenado), calculada sem tabelas.
            float bayer(vec2 p) {
                vec2 q = mod(p, 4.0);
                float x0 = mod(q.x, 2.0); float x1 = floor(q.x / 2.0);
                float y0 = mod(q.y, 2.0); float y1 = floor(q.y / 2.0);
                return (abs(x0 - y0) * 8.0 + y0 * 4.0 + abs(x1 - y1) * 2.0 + y1) / 16.0;
            }
            void main() {
                vec3 luz = normalize(vec3(0.35, -0.45, 0.85));
                float d = max(dot(normalize(vNor), luz), 0.0);
                if (uPs1F > 0.5) d = floor(d * 3.0 + 0.5) / 3.0;   // sombreado em poucos degraus
                float k = mix(1.0, 0.55 + 0.5 * d, uLuz);
                vec3 c = uCor.rgb * k;
                if (uPs1F > 0.5) {
                    c = mix(c, uNevoa, clamp((vFundo - 55.0) / 120.0, 0.0, 1.0) * 0.65);   // névoa à distância
                    float t = bayer(floor(gl_FragCoord.xy)) - 0.5;
                    c = floor(c * 31.0 + t + 0.5) / 31.0;   // 5 bits por cor, com tremido
                }
                gl_FragColor = vec4(c, uCor.a);
            }
        """
        const val VERTICE_ECRA = """
            attribute vec2 aPos;
            attribute vec2 aNor;
            varying vec2 vUv;
            void main() { gl_Position = vec4(aPos, 0.0, 1.0); vUv = aNor; }
        """
        const val FRAGMENTO_ECRA = """
            precision mediump float;
            uniform sampler2D uTex;
            varying vec2 vUv;
            void main() { gl_FragColor = vec4(texture2D(uTex, vUv).rgb, 1.0); }
        """
    }
}
