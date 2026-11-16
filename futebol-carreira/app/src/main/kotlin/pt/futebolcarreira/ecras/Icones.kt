package pt.futebolcarreira.ecras

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Ícones desenhados por código (formas simples, 24x24), para não depender de bibliotecas de ícones.

private fun icone(nome: String, desenho: String): ImageVector =
    ImageVector.Builder(nome, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(desenho).toNodes(), fill = SolidColor(Color.Black))
        .build()

object Icones {
    val Inicio: ImageVector by lazy { icone("Inicio", "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z") }

    val Plantel: ImageVector by lazy {
        icone(
            "Plantel",
            "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3zM8,11c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3zM8,13c-2.33,0 -7,1.17 -7,3.5V19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5zM16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45V19h6v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z",
        )
    }

    val Mercado: ImageVector by lazy { icone("Mercado", "M6.99,11L3,15l3.99,4v-3H14v-2H6.99v-3zM21,9l-3.99,-4v3H10v2h7.01v3L21,9z") }

    val Liga: ImageVector by lazy {
        icone(
            "Liga",
            "M19,5h-2V3H7v2H5c-1.1,0 -2,0.9 -2,2v1c0,2.55 1.92,4.63 4.39,4.94 0.63,1.5 1.98,2.63 3.61,2.96V19H7v2h10v-2h-4v-3.1c1.63,-0.33 2.98,-1.46 3.61,-2.96C19.08,12.63 21,10.55 21,8V7c0,-1.1 -0.9,-2 -2,-2zM5,8V7h2v3.82C5.84,10.4 5,9.3 5,8zM19,8c0,1.3 -0.84,2.4 -2,2.82V7h2v1z",
        )
    }

    val Mais: ImageVector by lazy { icone("Mais", "M3,18h18v-2H3v2zM3,13h18v-2H3v2zM3,6v2h18V6H3z") }

    val Jogador: ImageVector by lazy {
        icone("Jogador", "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z")
    }
}
