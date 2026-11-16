package pt.futebolcarreira.aovivo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Que forma desenhar numa [Peca]. */
enum class Forma { CAIXA, DISCO, ANEL }

/**
 * Uma peça fixa do cenário (centro x,y,z; tamanho sx,sy,sz; rotação em torno de z).
 * CAIXA mede sx*sy*sz; DISCO e ANEL ficam no chão e usam sx como diâmetro.
 */
class Peca(
    val forma: Forma, val x: Float, val y: Float, val z: Float,
    val sx: Float, val sy: Float, val sz: Float, val rotacao: Float, val cor: Int,
    /** true = rede (desenha-se com transparência). */
    val transparente: Boolean = false,
    /** Material para o estilo PS2: 0 normal, 1 relva (com textura), 2 público (pontos de cor), 3 luz (brilha). */
    val material: Int = 0,
)

/** O campo, as linhas, as balizas e a bancada, como listas de peças (sem OpenGL, para se poder testar). */
object CenarioCampo {
    const val COMPRIMENTO = 105f
    const val LARGURA = 68f
    const val BALIZA_LARGURA = 7.32f
    const val BALIZA_ALTURA = 2.44f
    const val BALIZA_FUNDO = 2.0f

    private const val RELVA_CLARA = 0xFF3E9B3F.toInt()
    private const val RELVA_ESCURA = 0xFF358A37.toInt()
    private const val LINHA = 0xFFF5F5F0.toInt()
    private const val LARGURA_LINHA = 0.14f
    private const val MARGEM = 4f   // faixa de relva fora das linhas

    /** Tudo o que se desenha no chão, por ordem (de baixo para cima), sem teste de profundidade. */
    fun chao(): List<Peca> {
        val l = ArrayList<Peca>(64)
        // Terreno à volta do estádio e relva.
        l += Peca(Forma.CAIXA, 0f, 0f, -0.05f, 400f, 300f, 0.1f, 0f, 0xFF1B2A1E.toInt())
        l += Peca(Forma.CAIXA, 0f, 0f, 0f, COMPRIMENTO + 2 * MARGEM, LARGURA + 2 * MARGEM, 0.02f, 0f, RELVA_ESCURA, material = 1)
        // Listas verticais: 14 faixas, as pares mais claras.
        val n = 14
        val larg = COMPRIMENTO / n
        for (i in 0 until n step 2) {
            l += Peca(Forma.CAIXA, -COMPRIMENTO / 2 + larg * (i + 0.5f), 0f, 0.01f, larg, LARGURA, 0.02f, 0f, RELVA_CLARA, material = 1)
        }
        linhas(l)
        return l
    }

    private fun linha(l: MutableList<Peca>, x1: Float, y1: Float, x2: Float, y2: Float) {
        val horizontal = y1 == y2
        val cx = (x1 + x2) / 2
        val cy = (y1 + y2) / 2
        val comp = if (horizontal) kotlin.math.abs(x2 - x1) else kotlin.math.abs(y2 - y1)
        l += if (horizontal) Peca(Forma.CAIXA, cx, cy, 0.02f, comp + LARGURA_LINHA, LARGURA_LINHA, 0.02f, 0f, LINHA)
        else Peca(Forma.CAIXA, cx, cy, 0.02f, LARGURA_LINHA, comp + LARGURA_LINHA, 0.02f, 0f, LINHA)
    }

    private fun retangulo(l: MutableList<Peca>, x1: Float, y1: Float, x2: Float, y2: Float) {
        linha(l, x1, y1, x2, y1); linha(l, x1, y2, x2, y2); linha(l, x1, y1, x1, y2); linha(l, x2, y1, x2, y2)
    }

    private fun linhas(l: MutableList<Peca>) {
        val a = COMPRIMENTO / 2
        val b = LARGURA / 2
        retangulo(l, -a, -b, a, b)
        linha(l, 0f, -b, 0f, b)
        l += Peca(Forma.ANEL, 0f, 0f, 0.03f, 18.3f, 18.3f, 1f, 0f, LINHA)
        l += Peca(Forma.DISCO, 0f, 0f, 0.03f, 0.5f, 0.5f, 1f, 0f, LINHA)
        for (lado in listOf(-1f, 1f)) {
            // Grande área (40,32 x 16,5), pequena área (18,32 x 5,5), marca de penálti a 11 m.
            retangulo(l, lado * a, -20.16f, lado * (a - 16.5f), 20.16f)
            retangulo(l, lado * a, -9.16f, lado * (a - 5.5f), 9.16f)
            l += Peca(Forma.DISCO, lado * (a - 11f), 0f, 0.03f, 0.5f, 0.5f, 1f, 0f, LINHA)
        }
    }

    /** Balizas (traves, barra e rede) e painéis de publicidade: peças com altura, com profundidade. */
    fun balizas(): List<Peca> {
        val l = ArrayList<Peca>(40)
        val a = COMPRIMENTO / 2
        val meia = BALIZA_LARGURA / 2
        val branco = 0xFFFFFFFF.toInt()
        val rede = 0xFFE8E8E8.toInt()
        val espessura = 0.14f
        for (lado in listOf(-1f, 1f)) {
            val fundo = lado * (a + BALIZA_FUNDO)
            // Traves e barra.
            l += Peca(Forma.CAIXA, lado * a, -meia, BALIZA_ALTURA / 2, espessura, espessura, BALIZA_ALTURA, 0f, branco)
            l += Peca(Forma.CAIXA, lado * a, meia, BALIZA_ALTURA / 2, espessura, espessura, BALIZA_ALTURA, 0f, branco)
            l += Peca(Forma.CAIXA, lado * a, 0f, BALIZA_ALTURA, espessura, BALIZA_LARGURA + espessura, espessura, 0f, branco)
            // Rede: fundo, dois lados e teto, transparentes.
            l += Peca(Forma.CAIXA, fundo, 0f, BALIZA_ALTURA / 2, 0.03f, BALIZA_LARGURA, BALIZA_ALTURA, 0f, rede, true)
            for (s in listOf(-1f, 1f)) {
                l += Peca(Forma.CAIXA, lado * (a + BALIZA_FUNDO / 2), s * meia, BALIZA_ALTURA / 2, BALIZA_FUNDO, 0.03f, BALIZA_ALTURA, 0f, rede, true)
            }
            l += Peca(Forma.CAIXA, lado * (a + BALIZA_FUNDO / 2), 0f, BALIZA_ALTURA, BALIZA_FUNDO, BALIZA_LARGURA, 0.03f, 0f, rede, true)
            // Alguns fios verticais no fundo.
            for (k in 1..5) {
                l += Peca(Forma.CAIXA, fundo, -meia + BALIZA_LARGURA * k / 6f, BALIZA_ALTURA / 2, 0.04f, 0.03f, BALIZA_ALTURA, 0f, branco)
            }
        }
        return l
    }

    /** Painéis de publicidade e bancadas à volta do campo. */
    fun bancadas(): List<Peca> {
        val l = ArrayList<Peca>(48)
        val a = COMPRIMENTO / 2 + MARGEM
        val b = LARGURA / 2 + MARGEM
        val publicidade = intArrayOf(0xFFD32F2F.toInt(), 0xFF1565C0.toInt(), 0xFFF9A825.toInt(), 0xFF2E7D32.toInt())
        // Painéis baixos nas quatro margens.
        val painelLong = 10
        for (i in 0 until painelLong) {
            val x = -a + (2 * a) * (i + 0.5f) / painelLong
            val cor = publicidade[i % 4]
            l += Peca(Forma.CAIXA, x, -b, 0.45f, 2 * a / painelLong - 0.15f, 0.2f, 0.9f, 0f, cor)
            l += Peca(Forma.CAIXA, x, b, 0.45f, 2 * a / painelLong - 0.15f, 0.2f, 0.9f, 0f, cor)
        }
        for (i in 0 until 4) {
            val y = -b + (2 * b) * (i + 0.5f) / 4
            val cor = publicidade[(i + 1) % 4]
            l += Peca(Forma.CAIXA, -a, y, 0.45f, 0.2f, 2 * b / 4 - 0.15f, 0.9f, 0f, cor)
            l += Peca(Forma.CAIXA, a, y, 0.45f, 0.2f, 2 * b / 4 - 0.15f, 0.9f, 0f, cor)
        }
        // Bancadas em degraus (3 degraus por lado), com cores alternadas para parecer público.
        val publico = intArrayOf(0xFF37474F.toInt(), 0xFF455A64.toInt(), 0xFF5D4037.toInt(), 0xFF3949AB.toInt(), 0xFF546E7A.toInt())
        for (degrau in 0 until 3) {
            val altura = 2.5f + degrau * 2.5f
            val recuo = 2f + degrau * 3f
            val segLong = 6
            for (i in 0 until segLong) {
                val x = -a + (2 * a) * (i + 0.5f) / segLong
                val cor = publico[(i + degrau) % publico.size]
                val c = 2 * a / segLong
                l += Peca(Forma.CAIXA, x, -(b + recuo + 1.5f), altura / 2, c, 3f, altura, 0f, cor, material = 2)
                l += Peca(Forma.CAIXA, x, b + recuo + 1.5f, altura / 2, c, 3f, altura, 0f, cor, material = 2)
            }
            val segCurto = 3
            for (i in 0 until segCurto) {
                val y = -b + (2 * b) * (i + 0.5f) / segCurto
                val cor = publico[(i + degrau + 2) % publico.size]
                val c = 2 * b / segCurto
                l += Peca(Forma.CAIXA, -(a + recuo + 1.5f), y, altura / 2, 3f, c, altura, 0f, cor, material = 2)
                l += Peca(Forma.CAIXA, a + recuo + 1.5f, y, altura / 2, 3f, c, altura, 0f, cor, material = 2)
            }
        }
        // Quatro torres de holofotes nos cantos do estádio.
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
            val tx = sx * (a + 16f); val ty = sy * (b + 16f)
            l += Peca(Forma.CAIXA, tx, ty, 11f, 0.7f, 0.7f, 22f, 0f, 0xFF50565C.toInt())
            l += Peca(Forma.CAIXA, tx, ty, 22.8f, 5.5f, 2.2f, 2.6f, 0f, 0xFFFFF4D0.toInt(), material = 3)
        }
        return l
    }

    /** Pontos de um círculo unitário (para o disco e o anel). */
    fun circulo(segmentos: Int): FloatArray {
        val p = FloatArray(segmentos * 2)
        for (i in 0 until segmentos) {
            val ang = 2.0 * PI * i / segmentos
            p[2 * i] = cos(ang).toFloat()
            p[2 * i + 1] = sin(ang).toFloat()
        }
        return p
    }
}
