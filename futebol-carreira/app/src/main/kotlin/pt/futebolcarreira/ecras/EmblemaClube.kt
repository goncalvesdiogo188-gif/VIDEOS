package pt.futebolcarreira.ecras

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.EmblemaUi
import kotlin.math.abs

// B4: emblemas (escudos) e camisolas desenhados por código a partir das cores do clube.
// O padrão é escolhido sempre da mesma maneira para o mesmo clube (hash da sigla e das cores).

/** Os padrões possíveis de um escudo ou camisola. */
internal enum class PadraoClube { LISO, LISTAS_VERTICAIS, FAIXA_DIAGONAL, METADES, CRUZ, LISTAS_HORIZONTAIS, FAIXA_HORIZONTAL, QUARTOS }

/** Padrão do clube: depende só da sigla e das cores, por isso nunca muda. */
internal fun padraoDoClube(e: EmblemaUi): PadraoClube {
    val h = abs((e.sigla.hashCode() * 31 + e.fundo.hashCode()) * 31 + e.letras.hashCode())
    return PadraoClube.entries[h % PadraoClube.entries.size]
}

private fun corPadrao(e: EmblemaUi): Color = lerp(Color(e.fundo), Color(e.letras), 0.3f)

/** Escudo de um clube: forma de escudo, cores e padrão do clube, e a sigla por cima. */
@Composable
internal fun EmblemaEscudo(e: EmblemaUi, tamanho: Dp = 40.dp) {
    val padrao = remember(e) { padraoDoClube(e) }
    Box(Modifier.size(tamanho), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(tamanho)) {
            val escudo = caminhoEscudo(size)
            clipPath(escudo) {
                drawRect(Color(e.fundo))
                desenharPadrao(padrao, corPadrao(e), size, 0f, 0f)
            }
            drawPath(escudo, Color(e.letras), style = Stroke(width = size.minDimension * 0.07f))
        }
        if (tamanho >= 24.dp) {
            Text(
                e.sigla.take(3), color = Color(e.letras), fontWeight = FontWeight.Black,
                fontSize = (tamanho.value * (if (e.sigla.length >= 3) 0.26f else 0.32f)).sp, maxLines = 1, softWrap = false,
                modifier = Modifier.padding(bottom = tamanho * 0.06f),
            )
        }
    }
}

/** Camisola de um clube (com número opcional), desenhada com as cores e o padrão do clube. */
@Composable
internal fun Camisola(e: EmblemaUi, tamanho: Dp = 48.dp, numero: Int? = null) {
    val padrao = remember(e) { padraoDoClube(e) }
    Box(Modifier.size(tamanho), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(tamanho)) {
            val corpo = caminhoCamisola(size)
            val larg = size.width
            clipPath(corpo) {
                drawRect(Color(e.fundo))
                // O padrão só ocupa o tronco (entre as mangas).
                desenharPadrao(padrao, corPadrao(e), Size(larg * 0.52f, size.height), larg * 0.24f, 0f)
                // Mangas na cor de contraste.
                drawRect(corPadrao(e), Offset(0f, 0f), Size(larg * 0.24f, size.height))
                drawRect(corPadrao(e), Offset(larg * 0.76f, 0f), Size(larg * 0.24f, size.height))
            }
            drawPath(corpo, Color(e.letras), style = Stroke(width = size.minDimension * 0.045f))
            // Gola.
            val gola = Path().apply {
                moveTo(larg * 0.36f, size.height * 0.06f)
                quadraticTo(larg * 0.5f, size.height * 0.22f, larg * 0.64f, size.height * 0.06f)
            }
            drawPath(gola, Color(e.letras), style = Stroke(width = size.minDimension * 0.05f))
        }
        if (numero != null && tamanho >= 30.dp) {
            Text(
                numero.toString(), color = Color(e.letras), fontWeight = FontWeight.Black,
                fontSize = (tamanho.value * 0.3f).sp, maxLines = 1, softWrap = false,
                modifier = Modifier.padding(top = tamanho * 0.18f),
            )
        }
    }
}

private fun caminhoEscudo(s: Size): Path {
    val w = s.width
    val h = s.height
    return Path().apply {
        moveTo(w * 0.10f, h * 0.05f)
        lineTo(w * 0.90f, h * 0.05f)
        lineTo(w * 0.90f, h * 0.50f)
        cubicTo(w * 0.90f, h * 0.76f, w * 0.68f, h * 0.90f, w * 0.5f, h * 0.97f)
        cubicTo(w * 0.32f, h * 0.90f, w * 0.10f, h * 0.76f, w * 0.10f, h * 0.50f)
        close()
    }
}

private fun caminhoCamisola(s: Size): Path {
    val w = s.width
    val h = s.height
    return Path().apply {
        moveTo(w * 0.36f, h * 0.06f)
        quadraticTo(w * 0.5f, h * 0.22f, w * 0.64f, h * 0.06f)
        lineTo(w * 0.88f, h * 0.16f)
        lineTo(w * 0.99f, h * 0.44f)
        lineTo(w * 0.82f, h * 0.52f)
        lineTo(w * 0.76f, h * 0.40f)
        lineTo(w * 0.76f, h * 0.94f)
        lineTo(w * 0.24f, h * 0.94f)
        lineTo(w * 0.24f, h * 0.40f)
        lineTo(w * 0.18f, h * 0.52f)
        lineTo(w * 0.01f, h * 0.44f)
        lineTo(w * 0.12f, h * 0.16f)
        close()
    }
}

/** Desenha o padrão (na cor "cor") dentro do retângulo que começa em (x0, y0) e tem o tamanho "s". */
private fun DrawScope.desenharPadrao(padrao: PadraoClube, cor: Color, s: Size, x0: Float, y0: Float) {
    val w = s.width
    val h = s.height
    when (padrao) {
        PadraoClube.LISO -> Unit
        PadraoClube.LISTAS_VERTICAIS -> for (i in 0 until 5) if (i % 2 == 1) drawRect(cor, Offset(x0 + w * i / 5f, y0), Size(w / 5f, h))
        PadraoClube.LISTAS_HORIZONTAIS -> for (i in 0 until 6) if (i % 2 == 1) drawRect(cor, Offset(x0, y0 + h * i / 6f), Size(w, h / 6f))
        PadraoClube.FAIXA_HORIZONTAL -> drawRect(cor, Offset(x0, y0 + h * 0.38f), Size(w, h * 0.24f))
        PadraoClube.METADES -> drawRect(cor, Offset(x0 + w * 0.5f, y0), Size(w * 0.5f, h))
        PadraoClube.CRUZ -> {
            drawRect(cor, Offset(x0 + w * 0.40f, y0), Size(w * 0.20f, h))
            drawRect(cor, Offset(x0, y0 + h * 0.36f), Size(w, h * 0.20f))
        }
        PadraoClube.QUARTOS -> {
            drawRect(cor, Offset(x0 + w * 0.5f, y0), Size(w * 0.5f, h * 0.5f))
            drawRect(cor, Offset(x0, y0 + h * 0.5f), Size(w * 0.5f, h * 0.5f))
        }
        PadraoClube.FAIXA_DIAGONAL -> {
            val faixa = Path().apply {
                moveTo(x0, y0 + h * 0.15f)
                lineTo(x0 + w * 0.35f, y0)
                lineTo(x0 + w, y0 + h * 0.85f)
                lineTo(x0 + w * 0.65f, y0 + h)
                close()
            }
            drawPath(faixa, cor)
        }
    }
}
