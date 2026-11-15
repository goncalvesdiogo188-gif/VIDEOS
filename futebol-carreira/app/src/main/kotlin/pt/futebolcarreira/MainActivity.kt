package pt.futebolcarreira

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import pt.futebolcarreira.ecras.Aplicacao
import pt.futebolcarreira.ecras.Tema

/** Ponto de entrada da aplicação: abre o menu principal. */
class MainActivity : ComponentActivity() {
    private val jogo: JogoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Tema { Aplicacao(jogo) }
        }
    }

    /** A aplicação foi para segundo plano: grava-se o que estiver por gravar. */
    override fun onStop() {
        super.onStop()
        jogo.aoSairDaApp()
    }
}
