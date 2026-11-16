package pt.futebolcarreira.aovivo

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot

/**
 * O estado mínimo do protótipo C0: um jogador e uma bola num campo de 105 × 68 metros.
 * É Kotlin puro (sem Android nem Godot), por isso testa-se com testes normais.
 * Faz de "motor do jogo": recebe o joystick e calcula as posições novas.
 */
class EstadoPrototipo {
    var jogadorX = -20f; private set
    var jogadorZ = 0f; private set
    var bolaX = -18.4f; private set
    var bolaZ = 0f; private set
    private var joyX = 0f
    private var joyZ = 0f

    /** Joystick de -1 a 1 (o Godot manda-o a cada alteração). */
    fun definirJoystick(x: Float, z: Float) {
        joyX = x.coerceIn(-1f, 1f)
        joyZ = z.coerceIn(-1f, 1f)
    }

    /** Avança [dt] segundos: o jogador corre a até 8 m/s e leva a bola à frente. */
    fun passo(dt: Float) {
        jogadorX = (jogadorX + joyX * VELOCIDADE * dt).coerceIn(-METADE_COMPRIMENTO, METADE_COMPRIMENTO)
        jogadorZ = (jogadorZ + joyZ * VELOCIDADE * dt).coerceIn(-METADE_LARGURA, METADE_LARGURA)
        val comprimento = kotlin.math.sqrt(joyX * joyX + joyZ * joyZ)
        val alvoX = if (comprimento > 0.05f) jogadorX + joyX / comprimento * 1.6f else bolaX
        val alvoZ = if (comprimento > 0.05f) jogadorZ + joyZ / comprimento * 1.6f else bolaZ
        val k = (8f * dt).coerceAtMost(1f)
        bolaX += (alvoX - bolaX) * k
        bolaZ += (alvoZ - bolaZ) * k
    }

    companion object {
        const val VELOCIDADE = 8f
        const val METADE_COMPRIMENTO = 52.5f
        const val METADE_LARGURA = 34f
    }
}

/**
 * A ponte entre o Kotlin e o Godot. Para o Godot é o singleton «FutebolPonte»:
 *  - o Kotlin manda o sinal `estado(bola_x, bola_z, jogador_x, jogador_z)` 60 vezes por segundo;
 *  - o Godot chama `joystick(x, z)`, `pronto()`, `fps(valor)` e `arranque(ms)`.
 */
class PonteGodot(godot: Godot) : GodotPlugin(godot) {
    private val estado = EstadoPrototipo()
    private var fio: HandlerThread? = null
    private var tarefa: Handler? = null
    @Volatile private var aCorrer = false

    /** Últimas medidas que o Godot enviou (para a Activity as poder mostrar/registar). */
    @Volatile var imagensPorSegundo = 0f; private set
    @Volatile var arranqueMs = -1; private set

    override fun getPluginName() = NOME

    override fun getPluginSignals(): Set<SignalInfo> {
        val f = Float::class.javaObjectType
        return setOf(SignalInfo("estado", f, f, f, f))
    }

    @UsedByGodot
    fun pronto() {
        if (aCorrer) return
        aCorrer = true
        val f = HandlerThread("ponte-godot").also { it.start() }
        fio = f
        val h = Handler(f.looper)
        tarefa = h
        var anterior = SystemClock.uptimeMillis()
        val ciclo = object : Runnable {
            override fun run() {
                if (!aCorrer) return
                val agora = SystemClock.uptimeMillis()
                val dt = ((agora - anterior) / 1000f).coerceAtMost(0.1f)
                anterior = agora
                estado.passo(dt)
                emitSignal("estado", estado.bolaX, estado.bolaZ, estado.jogadorX, estado.jogadorZ)
                h.postDelayed(this, PERIODO_MS)
            }
        }
        h.post(ciclo)
    }

    @UsedByGodot
    fun joystick(x: Float, z: Float) = estado.definirJoystick(x, z)

    @UsedByGodot
    fun fps(valor: Float) { imagensPorSegundo = valor }

    @UsedByGodot
    fun arranque(ms: Int) {
        arranqueMs = ms
        Log.i(NOME, "Primeira imagem do Godot após $ms ms.")
    }

    override fun onMainDestroy() {
        aCorrer = false
        fio?.quitSafely()
        fio = null
    }

    companion object {
        const val NOME = "FutebolPonte"
        private const val PERIODO_MS = 16L
    }
}
