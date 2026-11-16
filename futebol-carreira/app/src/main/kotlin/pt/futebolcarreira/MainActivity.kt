package pt.futebolcarreira

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import pt.futebolcarreira.ecras.Aplicacao
import pt.futebolcarreira.ecras.Tema
import pt.futebolcarreira.ecras.corDoClube

/** Ponto de entrada da aplicação: abre o menu principal. */
class MainActivity : ComponentActivity() {
    private val jogo: JogoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // A cor do clube da carreira aberta dá o destaque ao jogo todo.
            val destaque = jogo.ui?.takeIf { jogo.ecra == Ecra.CARREIRA }?.let { corDoClube(it.emblema) }
            Tema(corDestaque = destaque) { Aplicacao(jogo) }
        }
    }

    /** A aplicação foi para segundo plano: grava-se o que estiver por gravar. */
    override fun onStop() {
        super.onStop()
        jogo.aoSairDaApp()
    }
}
