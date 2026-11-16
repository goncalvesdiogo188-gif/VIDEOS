package pt.futebolcarreira.ecras

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider

private fun Context.atividade(): Activity? {
    var c: Context? = this
    while (c != null) {
        if (c is Activity) return c
        c = (c as? ContextWrapper)?.baseContext
    }
    return null
}

/** Versão da app e telemóvel, para o relatório. */
private fun dadosBase(contexto: Context, estado: String, nota: String): DadosRelatorio {
    val versao = try {
        contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
    return DadosRelatorio(
        versaoApp = versao,
        telemovel = "${Build.MANUFACTURER} ${Build.MODEL}",
        android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        estado = estado, nota = nota,
    )
}

/** Imagem do ecrã da app, sem os diálogos (que são outra janela). Devolve null se o telemóvel não deixar. */
private fun capturarEcra(atividade: Activity?, aoTerminar: (Bitmap?) -> Unit) {
    val janela = atividade?.window
    val raiz = janela?.decorView
    if (janela == null || raiz == null || raiz.width <= 0 || raiz.height <= 0) return aoTerminar(null)
    try {
        val imagem = Bitmap.createBitmap(raiz.width, raiz.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(janela, imagem, { codigo -> aoTerminar(if (codigo == PixelCopy.SUCCESS) imagem else null) }, Handler(Looper.getMainLooper()))
    } catch (e: Exception) {
        aoTerminar(null)
    }
}

/** Abre o seletor de partilha do Android com o relatório (e a imagem, se existir). */
private fun partilhar(contexto: Context, gravado: RelatorioGravado) {
    val autoridade = "${contexto.packageName}.relatorios"
    val uris = ArrayList(listOfNotNull(gravado.texto, gravado.imagem).map { FileProvider.getUriForFile(contexto, autoridade, it) })
    val envio = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = if (uris.size > 1) "*/*" else "text/plain"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        putExtra(Intent.EXTRA_SUBJECT, "Relatório de problema — Futebol Carreira")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val escolha = Intent.createChooser(envio, "Partilhar relatório").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    contexto.startActivity(escolha)
}

/**
 * Diálogo «Relatar problema»: nota opcional; ao guardar, escreve o relatório em ficheiros da app e oferece «Partilhar».
 * [estado] resume onde o jogador está (clube, data de jogo, ecrã).
 */
@Composable
fun DialogoRelatarProblema(estado: String, aoFechar: () -> Unit) {
    val contexto = LocalContext.current
    var nota by remember { mutableStateOf("") }
    var gravado by remember { mutableStateOf<RelatorioGravado?>(null) }
    var erro by remember { mutableStateOf(false) }
    var imagem by remember { mutableStateOf<Bitmap?>(null) }
    // A imagem é tirada já à abertura, antes de o jogador escrever.
    LaunchedEffect(Unit) { capturarEcra(contexto.atividade()) { imagem = it } }

    val pronto = gravado
    if (pronto == null) {
        AlertDialog(
            onDismissRequest = aoFechar,
            title = { Text("Relatar problema") },
            text = {
                Column {
                    Text("Escreve o que aconteceu (opcional). Guardamos também a versão da app, o modelo do telemóvel, o clube, a data de jogo e uma imagem do ecrã.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = nota, onValueChange = { nota = it }, label = { Text("Nota (opcional)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(top = 12.dp),
                    )
                    if (erro) Text("Não foi possível guardar o relatório.", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        gravado = Relatorio.gravar(contexto.filesDir, dadosBase(contexto, estado, nota), imagem)
                        erro = false
                    } catch (e: java.io.IOException) {
                        erro = true
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = aoFechar,
            title = { Text("Relatório guardado") },
            text = {
                Text(
                    "Guardado no telemóvel" + (if (pronto.imagem != null) ", com imagem" else ", sem imagem") +
                        ". Toca em «Partilhar» para o enviares.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    try { partilhar(contexto, pronto) } catch (e: Exception) { erro = true }
                    aoFechar()
                }) { Text("Partilhar") }
            },
            dismissButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
        )
    }
}
