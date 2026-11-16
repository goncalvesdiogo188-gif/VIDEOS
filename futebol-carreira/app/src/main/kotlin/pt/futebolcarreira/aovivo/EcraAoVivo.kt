package pt.futebolcarreira.aovivo

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pt.futebolcarreira.nucleo.aovivo.EstadoPartida
import pt.futebolcarreira.nucleo.aovivo.FaseJogo
import pt.futebolcarreira.nucleo.aovivo.PartidaAoVivo

private val VERDE = Color(0xFF43A047)
private val VERMELHO = Color(0xFFE53935)
private val AZUL = Color(0xFF1E88E5)
private val LARANJA = Color(0xFFFB8C00)
private val DOURADO = Color(0xFFFFD54F)

/**
 * O jogo 3D: a vista OpenGL por baixo e, por cima, o marcador, as mensagens, o joystick e os botões.
 * "vistaJogo" existe para os testes poderem trocar o OpenGL (que o Robolectric não corre) por um marcador de posição.
 */
@Composable
fun EcraAoVivo(
    sessao: SessaoAoVivo,
    aoContinuar: (PartidaAoVivo) -> Unit,
    aoSair: () -> Unit,
    fixarOrientacao: Boolean = true,
    vistaJogo: @Composable (Modifier) -> Unit = { VistaGl(sessao, it) },
) {
    if (fixarOrientacao) ModoPaisagem()

    var estado by remember(sessao) { mutableStateOf(sessao.partida.estado) }
    var pausa by remember(sessao) { mutableStateOf(false) }
    var aSimular by remember(sessao) { mutableStateOf(false) }
    // O HUD vai buscar o estado ao motor 10 vezes por segundo (o desenho 3D lê-o a cada imagem).
    LaunchedEffect(sessao) {
        while (true) {
            estado = sessao.partida.estado
            delay(100)
        }
    }
    LaunchedEffect(pausa) {
        sessao.pausado = pausa
        if (pausa) sessao.controlo.limpar()
    }
    // Se a aplicação vai para segundo plano, o jogo fica em pausa.
    val dono = LocalContext.current.procurar<androidx.lifecycle.LifecycleOwner>()
    DisposableEffect(dono) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) pausa = true }
        dono?.lifecycle?.addObserver(obs)
        onDispose { dono?.lifecycle?.removeObserver(obs) }
    }
    // O resto do jogo é jogado sem ninguém a comandar, em segundo plano.
    LaunchedEffect(aSimular) {
        if (aSimular) withContext(Dispatchers.Default) { sessao.simularResto() }
    }
    val terminou = sessao.partida.terminou || estado.fase == FaseJogo.FIM
    BackHandler(enabled = !terminou) { pausa = !pausa }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        vistaJogo(Modifier.fillMaxSize())

        MarcadorETempo(estado, Modifier.align(Alignment.TopCenter))
        Mensagem(estado, Modifier.align(Alignment.TopCenter).padding(top = 58.dp))

        if (!terminou) {
            // Botão de pausa, em cima à esquerda.
            Surface(
                onClick = { pausa = true }, shape = CircleShape, color = Color(0xAA000000),
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(48.dp),
            ) { Box(contentAlignment = Alignment.Center) { Text("II", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) } }

            if (sessao.controlar) Controlos(sessao)
        }

        if (pausa && !terminou) MenuPausa(
            aSimular = aSimular, controlar = sessao.controlar,
            aoContinuar = { pausa = false },
            aoSimular = { aSimular = true },
            aoSair = aoSair,
        )
        if (terminou) CartaoFinal(estado) { aoContinuar(sessao.partida) }
    }
}

// --------------------------------------------------------------- orientação

/** Põe o ecrã em horizontal e esconde as barras do sistema enquanto este ecrã está aberto. */
@Composable
private fun ModoPaisagem() {
    val contexto = LocalContext.current
    val vista = LocalView.current
    DisposableEffect(Unit) {
        val atividade = contexto.procurar<Activity>()
        val anterior = atividade?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        atividade?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controlador = atividade?.window?.let { WindowCompat.getInsetsController(it, vista) }
        controlador?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controlador?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controlador?.show(WindowInsetsCompat.Type.systemBars())
            atividade?.requestedOrientation = anterior
        }
    }
}

// ----------------------------------------------------------------- marcador

@Composable
private fun MarcadorETempo(e: EstadoPartida, modifier: Modifier) {
    Surface(modifier.padding(top = 8.dp), shape = RoundedCornerShape(14.dp), color = Color(0xCC000000)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Camisola(e.coresCasa.first)
            Text(e.siglaCasa, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("${e.golosCasa} - ${e.golosFora}", color = DOURADO, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(e.siglaFora, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Camisola(e.coresFora.first)
            Text(textoTempo(e), color = Color(0xFFCCCCCC), fontSize = 15.sp, modifier = Modifier.widthIn(min = 44.dp), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun Camisola(argb: Long) {
    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(argb.toInt())))
}

internal fun textoTempo(e: EstadoPartida): String = when (e.fase) {
    FaseJogo.INTERVALO -> "Intervalo"
    FaseJogo.FIM -> "Fim"
    else -> "${e.minuto}'"
}

@Composable
private fun Mensagem(e: EstadoPartida, modifier: Modifier) {
    val texto = e.mensagem ?: when (e.fase) {
        FaseJogo.INTERVALO -> "Intervalo"
        FaseJogo.CANTO -> "Canto"
        FaseJogo.LIVRE -> "Livre"
        FaseJogo.PENALTI -> "Penálti"
        FaseJogo.LANCAMENTO -> "Lançamento lateral"
        FaseJogo.PONTAPE_BALIZA -> "Pontapé de baliza"
        else -> null
    } ?: return
    val golo = e.fase == FaseJogo.GOLO || texto.startsWith("GOLO")
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = if (golo) Color(0xE6F9A825) else Color(0xB3000000)) {
        Text(
            texto, Modifier.padding(horizontal = 18.dp, vertical = 6.dp), color = if (golo) Color(0xFF2B2000) else Color.White,
            fontWeight = FontWeight.Bold, fontSize = if (golo) 22.sp else 16.sp, textAlign = TextAlign.Center,
        )
    }
}

// ------------------------------------------------------------------ controlos

@Composable
private fun Controlos(sessao: SessaoAoVivo) {
    val c = sessao.controlo
    val haptico = LocalHapticFeedback.current
    Box(Modifier.fillMaxSize()) {
        ZonaJoystick(c, Modifier.align(Alignment.BottomStart).fillMaxWidth(0.45f).fillMaxHeight(0.7f))
        // Botões à direita: REMATE e PASSE em baixo, SPRINT e TROCAR por cima.
        BotaoAcao("REMATE", VERMELHO, 94.dp, { c.rematePremido() }, { c.remateSolto(); haptico.performHapticFeedback(HapticFeedbackType.LongPress) },
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 22.dp), forca = { c.forcaRemateAgora() })
        BotaoAcao("PASSE", VERDE, 82.dp, { c.passePremido() }, { c.passeSolto() },
            Modifier.align(Alignment.BottomEnd).padding(end = 128.dp, bottom = 22.dp), forca = { c.forcaPasseAgora() })
        BotaoAcao("SPRINT", AZUL, 70.dp, { c.sprint(true) }, { c.sprint(false) },
            Modifier.align(Alignment.BottomEnd).padding(end = 34.dp, bottom = 130.dp))
        BotaoAcao("TROCAR", LARANJA, 62.dp, { c.trocar() }, {},
            Modifier.align(Alignment.BottomEnd).padding(end = 130.dp, bottom = 118.dp))
    }
}

/** Botão redondo que avisa quando é premido e quando é largado; mostra uma barra de força enquanto está premido. */
@Composable
private fun BotaoAcao(
    texto: String, cor: Color, tamanho: Dp, aoPremir: () -> Unit, aoSoltar: () -> Unit, modifier: Modifier = Modifier,
    forca: (() -> Float)? = null,
) {
    val premir by rememberUpdatedState(aoPremir)
    val soltar by rememberUpdatedState(aoSoltar)
    var premido by remember { mutableStateOf(false) }
    var nivel by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(premido) {
        while (premido && forca != null) {
            nivel = forca()
            androidx.compose.runtime.withFrameNanos { }
        }
        nivel = 0f
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(tamanho).clip(CircleShape).background(if (premido) cor else cor.copy(alpha = 0.72f))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        premido = true
                        premir()
                        waitForUpOrCancellation()
                        premido = false
                        soltar()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(texto, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (tamanho > 75.dp) 14.sp else 12.sp, textAlign = TextAlign.Center)
        }
        if (premido && forca != null) {
            // Barra de força por cima do botão.
            Box(Modifier.offset(y = -(tamanho / 2 + 10.dp)).width(tamanho).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x88000000))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(nivel.coerceIn(0f, 1f)).background(DOURADO))
            }
        }
    }
}

/** Zona do ecrã onde o dedo faz de joystick: o centro é onde se toca primeiro, e o arrasto dá a direção. */
@Composable
private fun ZonaJoystick(controlo: ControloTactil, modifier: Modifier) {
    val raio = with(LocalDensity.current) { 62.dp.toPx() }
    var centro by remember { mutableStateOf<Offset?>(null) }
    var dedo by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val inicio = awaitFirstDown(requireUnconsumed = false)
                centro = inicio.position
                dedo = inicio.position
                while (true) {
                    val evento = awaitPointerEvent(PointerEventPass.Main)
                    val m = evento.changes.firstOrNull { it.id == inicio.id } ?: break
                    if (!m.pressed) break
                    var d = m.position - inicio.position
                    val n = d.getDistance()
                    if (n > raio) d = d * (raio / n)
                    dedo = inicio.position + d
                    controlo.joystick(d.x / raio, -d.y / raio)
                    m.consume()
                }
                controlo.joystick(0f, 0f)
                centro = null
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = centro ?: Offset(raio + 34.dp.toPx(), size.height - raio - 22.dp.toPx())
            val k = if (centro == null) c else dedo
            drawCircle(Color(0x33FFFFFF), radius = raio, center = c)
            drawCircle(Color(0x66FFFFFF), radius = raio, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
            drawCircle(Color(0x99FFFFFF), radius = raio * 0.42f, center = k)
        }
    }
}

// --------------------------------------------------------------------- janelas

@Composable
private fun MenuPausa(aSimular: Boolean, controlar: Boolean, aoContinuar: () -> Unit, aoSimular: () -> Unit, aoSair: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }, contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(22.dp).widthIn(min = 260.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (aSimular) "A simular o resto do jogo…" else "Jogo em pausa", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (!aSimular) {
                    Button(onClick = aoContinuar, Modifier.fillMaxWidth().height(50.dp)) { Text("Continuar", fontSize = 17.sp) }
                    OutlinedButton(onClick = aoSimular, Modifier.fillMaxWidth().height(50.dp)) { Text("Simular o resto", fontSize = 16.sp) }
                    OutlinedButton(onClick = aoSair, Modifier.fillMaxWidth().height(50.dp)) { Text("Abandonar o jogo", fontSize = 16.sp) }
                }
            }
        }
    }
}

@Composable
private fun CartaoFinal(e: EstadoPartida, aoContinuar: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)).pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }, contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp).widthIn(min = 280.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fim do jogo", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(e.siglaCasa, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${e.golosCasa} - ${e.golosFora}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = DOURADO)
                    Text(e.siglaFora, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = aoContinuar, Modifier.fillMaxWidth().height(52.dp)) { Text("Continuar", fontSize = 17.sp) }
            }
        }
    }
}
