package pt.futebolcarreira.aovivo

import android.content.Context
import android.content.ContextWrapper
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * A vista OpenGL do jogo dentro do Compose. É o único sítio que cria o GLSurfaceView.
 * "parado" (pausa ou jogo acabado): deixa de desenhar a 60 imagens/s e mantém a última imagem no ecrã.
 */
@Composable
fun VistaGl(sessao: SessaoAoVivo, modifier: Modifier = Modifier, parado: Boolean = false) {
    val contexto = LocalContext.current
    val vista = remember(sessao) {
        GLSurfaceView(contexto).apply {
            setEGLContextClientVersion(2)
            val estilo = OpcoesAoVivo.estilo(OpcoesAoVivo.prefs(contexto))
            // Nos estilos de resolução total pede-se suavização de arestas (MSAA); se o telemóvel não a der, fica sem ela.
            if (estilo == EstiloGraficos.DLS || estilo == EstiloGraficos.NORMAL) setEGLConfigChooser(EscolherMsaa()) else setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(RendererPartida(sessao, estilo))
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            preserveEGLContextOnPause = true
        }
    }
    // O GLSurfaceView tem de saber quando a aplicação pára e recomeça.
    DisposableEffect(vista) {
        val dono = contexto.procurar<LifecycleOwner>()
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_RESUME -> vista.onResume()
                Lifecycle.Event.ON_PAUSE -> vista.onPause()
                else -> {}
            }
        }
        dono?.lifecycle?.addObserver(observador)
        // Se o ecrã já estava ativo quando entrou, retoma já.
        if (dono?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) vista.onResume()
        onDispose {
            dono?.lifecycle?.removeObserver(observador)
            vista.onPause()
        }
    }
    AndroidView(
        factory = { vista },
        modifier = modifier,
        update = { v ->
            val modo = if (parado) GLSurfaceView.RENDERMODE_WHEN_DIRTY else GLSurfaceView.RENDERMODE_CONTINUOUSLY
            // Só mexe quando muda, para não pedir imagens a cada recomposição.
            if (v.renderMode != modo) {
                v.renderMode = modo
                if (parado) v.requestRender()   // desenha uma vez, para a última imagem ficar no ecrã
            }
        },
    )
}

/** Sobe pela cadeia de contextos até encontrar um do tipo T (por exemplo, a Activity). */
internal inline fun <reified T> Context.procurar(): T? {
    var c: Context? = this
    while (c != null) {
        if (c is T) return c
        c = (c as? ContextWrapper)?.baseContext
    }
    return null
}

/** Escolhe a configuração EGL com 4 amostras de MSAA; se não existir, uma normal (8 bits por canal, 16 de profundidade). */
internal class EscolherMsaa : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: javax.microedition.khronos.egl.EGL10, display: javax.microedition.khronos.egl.EGLDisplay): javax.microedition.khronos.egl.EGLConfig {
        for (amostras in intArrayOf(4, 0)) {
            val a = ArrayList<Int>()
            a += listOf(
                javax.microedition.khronos.egl.EGL10.EGL_RED_SIZE, 8, javax.microedition.khronos.egl.EGL10.EGL_GREEN_SIZE, 8,
                javax.microedition.khronos.egl.EGL10.EGL_BLUE_SIZE, 8, javax.microedition.khronos.egl.EGL10.EGL_ALPHA_SIZE, 8,
                javax.microedition.khronos.egl.EGL10.EGL_DEPTH_SIZE, 16, javax.microedition.khronos.egl.EGL10.EGL_RENDERABLE_TYPE, 4,
            )
            if (amostras > 0) a += listOf(javax.microedition.khronos.egl.EGL10.EGL_SAMPLE_BUFFERS, 1, javax.microedition.khronos.egl.EGL10.EGL_SAMPLES, amostras)
            a += javax.microedition.khronos.egl.EGL10.EGL_NONE
            val atributos = a.toIntArray()
            val n = IntArray(1)
            if (!egl.eglChooseConfig(display, atributos, null, 0, n) || n[0] <= 0) continue
            val configs = arrayOfNulls<javax.microedition.khronos.egl.EGLConfig>(n[0])
            if (!egl.eglChooseConfig(display, atributos, configs, n[0], n)) continue
            configs.firstOrNull { it != null }?.let { return it }
        }
        throw IllegalArgumentException("Nenhuma configuração EGL serve")
    }
}
