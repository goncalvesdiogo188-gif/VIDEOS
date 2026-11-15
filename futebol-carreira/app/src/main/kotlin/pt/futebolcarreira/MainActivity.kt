package pt.futebolcarreira

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.futebolcarreira.ecras.EcraLiga

/** Ponto de entrada da aplicação: abre o ecrã de teste da Fase 1. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = CORES) {
                EcraLiga(viewModel())
            }
        }
    }
}

/** Cores da aplicação (tema escuro com verde de relvado). */
private val CORES = darkColorScheme(
    primary = Color(0xFF66BB6A),
    onPrimary = Color(0xFF00210B),
    secondary = Color(0xFFA5D6A7),
    background = Color(0xFF101412),
    surface = Color(0xFF171C19),
    surfaceVariant = Color(0xFF26302A),
)
