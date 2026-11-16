package pt.futebolcarreira.aovivo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotActivity
import org.godotengine.godot.plugin.GodotPlugin
import java.io.File

/**
 * Protótipo C0: um ecrã em Godot dentro da aplicação.
 * Corre num processo próprio (":godot", ver o manifesto), por isso ao sair o Godot
 * desaparece por inteiro e a aplicação fica como estava.
 *
 * O jogo do Godot vem de um ficheiro .pck nos «assets». O Godot precisa de o ler a partir
 * de um ficheiro normal, por isso copiamo-lo para a pasta privada da aplicação ao arrancar.
 */
class EcraGodot : GodotActivity() {
    private lateinit var ponte: PonteGodot

    override fun onCreate(savedInstanceState: Bundle?) {
        ficheiroPck = copiarPck(this)
        super.onCreate(savedInstanceState)
    }

    override fun getCommandLine(): MutableList<String> {
        val parametros = super.getCommandLine()
        if ("--main-pack" !in parametros) {
            parametros.add("--main-pack")
            parametros.add(ficheiroPck.absolutePath)
        }
        return parametros
    }

    override fun getHostPlugins(engine: Godot): MutableSet<GodotPlugin> {
        ponte = PonteGodot(engine)
        return mutableSetOf(ponte)
    }

    private var ficheiroPck: File = File("")

    companion object {
        private const val ASSET_PCK = "godot/ao_vivo.pck"

        /** Copia o .pck dos assets para a pasta privada (sempre, para apanhar versões novas). */
        fun copiarPck(contexto: Context): File {
            val destino = File(contexto.filesDir, "godot/ao_vivo.pck")
            destino.parentFile?.mkdirs()
            contexto.assets.open(ASSET_PCK).use { entrada ->
                destino.outputStream().use { saida -> entrada.copyTo(saida) }
            }
            return destino
        }

        fun abrir(contexto: Context) {
            contexto.startActivity(Intent(contexto, EcraGodot::class.java))
        }
    }
}
