package pt.futebolcarreira.ecras

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pt.futebolcarreira.Ecra
import pt.futebolcarreira.JogoViewModel

/** Raiz da aplicação: mostra o ecrã que estiver aberto e, por cima, o jogo, as janelas e os avisos. */
@Composable
fun Aplicacao(jogo: JogoViewModel) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (jogo.ecra) {
            Ecra.MENU -> EcraMenu(jogo.gravacoes, jogo.ocupado, jogo::continuar, jogo::abrirNovaCarreira, jogo::abrirCarregar)
            Ecra.NOVA_CARREIRA -> {
                BackHandler(onBack = jogo::abrirMenu)
                EcraNovaCarreira(jogo.paises, jogo.nacoes, jogo.ocupado, jogo::comecarCarreira, jogo::abrirMenu)
            }
            Ecra.CARREGAR -> {
                BackHandler(onBack = jogo::abrirMenu)
                EcraCarregar(jogo.gravacoes, jogo.ocupado, jogo::carregarCarreira, jogo::apagarCarreira, jogo::abrirMenu)
            }
            Ecra.CARREIRA -> jogo.ui?.let { EcraCarreira(it, jogo) }
        }

        jogo.jogo?.let { EcraJogo(it, jogo::fecharJogo) }
        jogo.ficha?.let { EcraJogador(it, !jogo.ocupado, jogo::negociar, jogo::fecharJogador) }
        jogo.plantel?.let { JanelaPlantel(it, jogo::fecharPlantel, jogo::abrirJogador) }
        jogo.escolha?.let { JanelaEscolha(it, jogo::escolherTitular, jogo::fecharEscolha) }
        jogo.aviso?.let { texto ->
            AlertDialog(
                onDismissRequest = jogo::fecharAviso,
                confirmButton = { TextButton(onClick = jogo::fecharAviso) { Text("OK") } },
                text = { Text(texto) },
            )
        }
    }
}
