package pt.futebolcarreira.ecras

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.ClubeEdUi
import pt.futebolcarreira.ClubeFichaUi
import pt.futebolcarreira.ControloEditor
import pt.futebolcarreira.JogadorEdUi
import pt.futebolcarreira.JogadorFichaUi
import pt.futebolcarreira.OpcaoUi
import pt.futebolcarreira.OrigemEditor
import pt.futebolcarreira.RelatorioUi
import pt.futebolcarreira.SeparadorEditor
import pt.futebolcarreira.TipoExportacao
import pt.futebolcarreira.nucleo.modelos.Jogador
import kotlin.math.roundToInt

private val NOMES_ATRIBUTOS = mapOf(
    "ritmo" to "Ritmo", "remate" to "Remate", "passe" to "Passe", "drible" to "Drible",
    "defesa" to "Defesa", "fisico" to "Físico", "guarda_redes" to "Guarda-redes",
)

/** Cores para escolher nas fichas dos clubes. */
private val PALETA = listOf(
    "#e30613", "#b71c1c", "#ff6f00", "#fdd835", "#2e7d32", "#00804a", "#00a6a6", "#1e88e5",
    "#00428c", "#0d1b4c", "#6a1b9a", "#d81b60", "#795548", "#9e9e9e", "#000000", "#ffffff",
)

/**
 * Editor da base de dados: clubes, jogadores e ficheiros (importar/exportar JSON).
 * Serve para a base das carreiras novas (a partir do menu) e para a carreira aberta (Mais → Opções).
 */
@Composable
fun EcraEditor(c: ControloEditor, aoSair: () -> Unit) {
    var sairSemGuardar by remember { mutableStateOf(false) }
    val criar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) c.escreverExportacao(uri) }
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) c.lerImportacao(uri) }
    val exportar: (TipoExportacao, String?) -> Unit = { tipo, id -> criar.launch(c.prepararExportacao(tipo, id)) }
    val importar: () -> Unit = { abrir.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }

    val relatorio = c.relatorio
    val jogador = c.jogadorAberto
    val clube = c.clubeAberto
    BackHandler {
        when {
            relatorio != null -> c.cancelarImportacao()
            jogador != null -> c.fecharJogador()
            clube != null -> c.fecharClube()
            c.alterado && c.origem == OrigemEditor.BASE -> sairSemGuardar = true
            else -> aoSair()
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        when {
            relatorio != null -> PaginaRelatorio(relatorio, c::aplicarImportacao, c::cancelarImportacao)
            jogador != null -> PaginaJogador(c, jogador)
            clube != null -> PaginaClube(c, clube, exportar, importar)
            else -> {
                val titulo = if (c.origem == OrigemEditor.BASE) "Editor" else "Editar esta carreira"
                BarraVoltar(titulo) { if (c.alterado && c.origem == OrigemEditor.BASE) sairSemGuardar = true else aoSair() }
                if (c.ocupado || !c.pronto) LinearProgressIndicator(Modifier.fillMaxWidth())
                Aviso(c)
                TabRow(selectedTabIndex = c.separador.ordinal) {
                    for (s in SeparadorEditor.entries) {
                        Tab(selected = c.separador == s, onClick = { c.separador = s }) {
                            Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                                Text(s.nome, fontSize = 14.sp, color = if (c.separador == s) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    if (c.pronto) when (c.separador) {
                        SeparadorEditor.CLUBES -> SeparadorClubes(c)
                        SeparadorEditor.JOGADORES -> SeparadorJogadores(c)
                        SeparadorEditor.FICHEIROS -> SeparadorFicheiros(c, exportar, importar)
                    }
                }
                if (c.alterado) {
                    HorizontalDivider()
                    BotaoGrande("Guardar alterações", Modifier.padding(10.dp), ativo = !c.ocupado, aoTocar = c::guardar)
                }
            }
        }
    }

    c.mensagem?.let { texto ->
        AlertDialog(
            onDismissRequest = { c.mensagem = null },
            confirmButton = { TextButton(onClick = { c.mensagem = null }) { Text("OK") } },
            text = { Text(texto) },
        )
    }
    if (sairSemGuardar) {
        AlertDialog(
            onDismissRequest = { sairSemGuardar = false },
            title = { Text("Sair sem guardar?") },
            text = { Text("As alterações que ainda não guardaste vão perder-se.") },
            confirmButton = { TextButton(onClick = { sairSemGuardar = false; aoSair() }) { Text("Sair", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { sairSemGuardar = false }) { Text("Ficar") } },
        )
    }
}

/** Diz claramente que base de dados está a ser editada. */
@Composable
private fun Aviso(c: ControloEditor) {
    Bloco(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (c.origem == OrigemEditor.BASE) {
            Rotulo("Base de dados das carreiras novas")
            Text("O que mudares aqui vale só para carreiras que começares depois. As carreiras que já tens não mudam.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Rotulo("Esta carreira: ${c.nomeDaCarreira}")
            Text("O que mudares aqui vale só para esta carreira. A base das carreiras novas não muda.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// -------------------------------------------------------------------- clubes

@Composable
private fun SeparadorClubes(c: ControloEditor) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipToque(c.filtroPais == null, { c.filtrarClubes(null, c.textoClubes) }, { Text("Todos") })
            for (p in c.paises) ChipToque(c.filtroPais == p.id, { c.filtrarClubes(p.id, c.textoClubes) }, { Text(p.texto) })
        }
        OutlinedTextField(
            c.textoClubes, { c.filtrarClubes(c.filtroPais, it) }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            label = { Text("Procurar clube") }, singleLine = true,
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(c.clubes, key = { it.id }) { k -> LinhaClube(k) { c.abrirClube(k.id) } }
        }
    }
}

@Composable
private fun LinhaClube(k: ClubeEdUi, aoTocar: () -> Unit) {
    Bloco(aoTocar = aoTocar) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Emblema(k.emblema, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(k.nome, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${k.liga} · ${k.jogadores} jogadores", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(k.reputacao.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PaginaClube(c: ControloEditor, ficha: ClubeFichaUi, exportar: (TipoExportacao, String?) -> Unit, importar: () -> Unit) {
    var f by remember(ficha.id, ficha.ligaNome) { mutableStateOf(ficha) }
    var trocar by remember { mutableStateOf(false) }
    BarraVoltar(ficha.nome, c::fecharClube)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Campo("Nome", f.nome) { f = f.copy(nome = it) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Campo("Abreviatura", f.sigla, Modifier.weight(1f)) { f = f.copy(sigla = it.take(5)) }
            Campo("Cidade", f.cidade, Modifier.weight(2f)) { f = f.copy(cidade = it) }
        }
        Campo("Estádio", f.estadio) { f = f.copy(estadio = it) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Campo("Capacidade", f.capacidade, Modifier.weight(1f), numero = true) { f = f.copy(capacidade = it) }
            Campo("Orçamento (€)", f.orcamento, Modifier.weight(1f), numero = true) { f = f.copy(orcamento = it) }
        }
        SliderValor("Reputação", f.reputacao) { f = f.copy(reputacao = it) }
        SeletorCor("Cor principal", f.cor1) { f = f.copy(cor1 = it) }
        SeletorCor("Cor secundária", f.cor2) { f = f.copy(cor2 = it) }
        Bloco {
            Rotulo("Liga")
            Text(f.ligaNome, fontWeight = FontWeight.Bold)
            if (ficha.trocas.isEmpty()) {
                Text(
                    if (c.origem == OrigemEditor.CARREIRA) "Numa carreira não se trocam clubes de liga (o calendário já está feito)." else "Não há outra liga neste país.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Para as ligas ficarem com o mesmo tamanho, o clube troca de lugar com outro clube do país.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { trocar = true }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Trocar de liga com…") }
            }
        }
        BotaoGrande("Aplicar alterações") { c.guardarClube(f) }
        OutlinedButton(onClick = {
            c.filtrarJogadores(ficha.id, null, ""); c.separador = SeparadorEditor.JOGADORES; c.fecharClube()
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ver os jogadores deste clube") }
        OutlinedButton(onClick = { exportar(TipoExportacao.CLUBE, ficha.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Exportar este clube (JSON)") }
        OutlinedButton(onClick = importar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Importar um clube (JSON)") }
        Spacer(Modifier.height(16.dp))
    }
    if (trocar) {
        DialogoEscolha("Trocar de liga com…", ficha.trocas, { trocar = false; c.trocarLiga(ficha.id, it.id) }, { trocar = false })
    }
}

// -------------------------------------------------------------------- jogadores

@Composable
private fun SeparadorJogadores(c: ControloEditor) {
    var escolherClube by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            c.textoJogadores, { c.filtrarJogadores(c.filtroClube, c.filtroPosicao, it) },
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), label = { Text("Procurar jogador") }, singleLine = true,
        )
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { escolherClube = true }, Modifier.weight(1f)) {
                val nome = c.opcoesClubes.firstOrNull { it.id == c.filtroClube }?.texto?.substringBefore(" (") ?: "Todos os clubes"
                Text("Clube: $nome", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = c::novoJogador) { Text("+ Novo") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipToque(c.filtroPosicao == null, { c.filtrarJogadores(c.filtroClube, null, c.textoJogadores) }, { Text("Todas") })
            for (p in Jogador.POSICOES) ChipToque(c.filtroPosicao == p, { c.filtrarJogadores(c.filtroClube, p, c.textoJogadores) }, { Text(p) })
        }
        Text(
            if (c.totalJogadores > c.jogadores.size) "A mostrar os ${c.jogadores.size} melhores de ${c.totalJogadores}" else "${c.totalJogadores} jogadores",
            Modifier.padding(horizontal = 14.dp, vertical = 2.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(c.jogadores, key = { it.id }) { j -> LinhaJogador(j) { c.abrirJogador(j.id) } }
        }
    }
    if (escolherClube) {
        val todos = listOf(OpcaoUi("", "Todos os clubes")) + c.opcoesClubes
        DialogoEscolha("Filtrar por clube", todos, { escolherClube = false; c.filtrarJogadores(it.id.ifEmpty { null }, c.filtroPosicao, c.textoJogadores) }, { escolherClube = false })
    }
}

@Composable
private fun LinhaJogador(j: JogadorEdUi, aoTocar: () -> Unit) {
    Bloco(aoTocar = aoTocar) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(j.posicao, Modifier.width(38.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(j.nome, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${j.idade} anos · ${j.clube} · pot. ${j.potencial}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(j.geral.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun PaginaJogador(c: ControloEditor, ficha: JogadorFichaUi) {
    var f by remember(ficha.id, ficha.novo) { mutableStateOf(ficha) }
    var escolherClube by remember { mutableStateOf(false) }
    var escolherPais by remember { mutableStateOf(false) }
    var apagar by remember { mutableStateOf(false) }
    val geral = ControloEditor.geralDe(f.posicao, f.atributos)
    BarraVoltar(if (ficha.novo) "Novo jogador" else ficha.nome.ifEmpty { "Jogador" }, c::fecharJogador)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Campo("Nome", f.nome) { f = f.copy(nome = it) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { escolherPais = true }, Modifier.weight(1f)) { Text("Nacionalidade: ${f.nacionalidade.ifEmpty { "?" }}") }
            Passo("Idade", f.idade, 14, 50) { f = f.copy(idade = it) }
        }
        Text("Posição", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (linha in Jogador.POSICOES.chunked(5)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (p in linha) ChipToque(f.posicao == p, { f = f.copy(posicao = p) }, { Text(p, fontSize = 13.sp) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Pé", Modifier.width(30.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (p in listOf("direito", "esquerdo", "ambos")) ChipToque(f.pe == p, { f = f.copy(pe = p) }, { Text(p, fontSize = 13.sp) })
        }
        Bloco {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Rotulo("Atributos", Modifier.weight(1f))
                Text("Geral ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(geral.toString(), fontSize = 26.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
            for (a in Jogador.ATRIBUTOS) {
                SliderValor(NOMES_ATRIBUTOS.getValue(a), f.atributos.getValue(a)) { v -> f = f.copy(atributos = f.atributos + (a to v)) }
            }
            SliderValor("Potencial", f.potencial) { f = f.copy(potencial = it) }
            if (f.potencial < geral) Text("O potencial tem de ser pelo menos igual ao geral ($geral).", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(onClick = { escolherClube = true }, Modifier.fillMaxWidth()) { Text("Clube: ${f.equipaNome}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Passo("Contrato até", f.contratoAte, 2026, 2040) { f = f.copy(contratoAte = it) }
            Campo("Salário (€/ano)", f.salario, Modifier.weight(1f), numero = true) { f = f.copy(salario = it) }
        }
        BotaoGrande(if (ficha.novo) "Criar jogador" else "Aplicar alterações") { c.guardarJogador(f) }
        if (!ficha.novo) {
            OutlinedButton(onClick = { apagar = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Apagar jogador", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (escolherClube) {
        DialogoEscolha("Clube do jogador", c.opcoesClubes, { escolherClube = false; f = f.copy(equipaId = it.id, equipaNome = it.texto.substringBefore(" (")) }, { escolherClube = false })
    }
    if (escolherPais) {
        DialogoEscolha("Nacionalidade", c.opcoesNacionalidades, { escolherPais = false; f = f.copy(nacionalidade = it.id) }, { escolherPais = false })
    }
    if (apagar) {
        AlertDialog(
            onDismissRequest = { apagar = false },
            title = { Text("Apagar jogador?") },
            text = { Text("${ficha.nome} vai ser apagado da base de dados. O clube tem de ficar com pelo menos 18 jogadores e 2 guarda-redes.") },
            confirmButton = { TextButton(onClick = { apagar = false; c.apagarJogador(ficha.id) }) { Text("Apagar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { apagar = false }) { Text("Cancelar") } },
        )
    }
}

// -------------------------------------------------------------------- ficheiros

@Composable
private fun SeparadorFicheiros(c: ControloEditor, exportar: (TipoExportacao, String?) -> Unit, importar: () -> Unit) {
    var escolherPais by remember { mutableStateOf(false) }
    var escolherClube by remember { mutableStateOf(false) }
    var repor by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Bloco {
            Rotulo("Exportar")
            Text("Cria um ficheiro .json no sítio que escolheres (telemóvel, Drive...).", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = { exportar(TipoExportacao.TUDO, null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Exportar toda a base de dados") }
        OutlinedButton(onClick = { escolherPais = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Exportar um país…") }
        OutlinedButton(onClick = { escolherClube = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Exportar um clube…") }
        Bloco {
            Rotulo("Importar")
            Text(
                "Aceita ficheiros exportados por aqui: a base completa, um país ou um clube. Antes de mudar alguma coisa, vês um relatório com os erros e avisos.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (c.origem == OrigemEditor.CARREIRA) Text("Nesta carreira só se importam clubes.", fontSize = 13.sp, color = MaterialTheme.colorScheme.tertiary)
        }
        BotaoGrande("Importar ficheiro JSON", ativo = !c.ocupado, aoTocar = importar)
        if (c.origem == OrigemEditor.BASE) {
            Bloco {
                Rotulo("Dados originais")
                Text("Apaga todas as tuas alterações à base de dados e volta aos dados que vêm com a aplicação.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { repor = true }, enabled = !c.ocupado, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Text("Repor dados originais", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (escolherPais) DialogoEscolha("Exportar que país?", c.paises, { escolherPais = false; exportar(TipoExportacao.PAIS, it.id) }, { escolherPais = false })
    if (escolherClube) DialogoEscolha("Exportar que clube?", c.opcoesClubes, { escolherClube = false; exportar(TipoExportacao.CLUBE, it.id) }, { escolherClube = false })
    if (repor) {
        AlertDialog(
            onDismissRequest = { repor = false },
            title = { Text("Repor dados originais?") },
            text = { Text("Todas as alterações guardadas na base de dados das carreiras novas vão ser apagadas. As carreiras que já tens não mudam.") },
            confirmButton = { TextButton(onClick = { repor = false; c.reporDadosOriginais() }) { Text("Repor", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { repor = false }) { Text("Cancelar") } },
        )
    }
}

/** O relatório de uma importação: erros (bloqueiam) e avisos (não bloqueiam). */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PaginaRelatorio(r: RelatorioUi, aoAplicar: () -> Unit, aoCancelar: () -> Unit) {
    BarraVoltar("Relatório da importação", aoCancelar)
    LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Bloco {
                Text(
                    if (r.podeAplicar) "Pronto a importar" else "Importação bloqueada",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (r.podeAplicar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(
                    if (r.podeAplicar) r.resumo else "Nada foi alterado. Corrige os erros no ficheiro e tenta outra vez.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("${r.erros.size} erros · ${r.avisos.size} avisos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (r.erros.isNotEmpty()) item { Rotulo("Erros (têm de ser corrigidos)", Modifier.padding(top = 6.dp)) }
        items(r.erros) { Linha("✖", it, MaterialTheme.colorScheme.error) }
        if (r.avisos.isNotEmpty()) item { Rotulo("Avisos (não impedem a importação)", Modifier.padding(top = 6.dp)) }
        items(r.avisos) { Linha("⚠", it, MaterialTheme.colorScheme.tertiary) }
    }
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (r.podeAplicar) BotaoGrande("Aplicar importação", aoTocar = aoAplicar)
        OutlinedButton(onClick = aoCancelar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (r.podeAplicar) "Cancelar" else "Fechar") }
    }
}

@Composable
private fun Linha(simbolo: String, texto: String, cor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(simbolo, color = cor, fontWeight = FontWeight.Bold)
        Text(texto, Modifier.weight(1f), fontSize = 14.sp)
    }
}

// -------------------------------------------------------------------- peças

@Composable
private fun Campo(rotulo: String, valor: String, modifier: Modifier = Modifier, numero: Boolean = false, aoMudar: (String) -> Unit) {
    OutlinedTextField(
        valor, aoMudar, modifier.fillMaxWidth(), label = { Text(rotulo, maxLines = 1) }, singleLine = true,
        keyboardOptions = if (numero) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
    )
}

/** Slider de 1 a 99 com o nome à esquerda e o valor à direita. */
@Composable
private fun SliderValor(nome: String, valor: Int, aoMudar: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(nome, Modifier.width(96.dp), fontSize = 14.sp, maxLines = 1)
        Slider(valor.toFloat(), { aoMudar(it.roundToInt()) }, Modifier.weight(1f), valueRange = 1f..99f)
        Text(valor.toString(), Modifier.width(30.dp), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/** Número com botões − e +. */
@Composable
private fun Passo(nome: String, valor: Int, minimo: Int, maximo: Int, aoMudar: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(nome, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { aoMudar((valor - 1).coerceAtLeast(minimo)) }) { Text("−", fontSize = 20.sp) }
            Text(valor.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            TextButton(onClick = { aoMudar((valor + 1).coerceAtMost(maximo)) }) { Text("+", fontSize = 20.sp) }
        }
    }
}

/** Escolher uma cor numa paleta, ou escrever o código hexadecimal. */
@Composable
private fun SeletorCor(titulo: String, hex: String, aoMudar: (String) -> Unit) {
    Bloco {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Rotulo(titulo, Modifier.weight(1f))
            val cor = ControloEditor.corDe(hex)
            Box(Modifier.size(28.dp).background(Color(cor ?: 0xFF444444), CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            OutlinedTextField(
                hex, { aoMudar(it.take(7)) }, Modifier.width(120.dp), singleLine = true, isError = cor == null,
            )
        }
        for (linha in PALETA.chunked(8)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                for (p in linha) {
                    val escolhida = p.equals(hex, true)
                    Box(
                        Modifier.size(34.dp).background(Color(ControloEditor.corDe(p)!!), CircleShape)
                            .border(if (escolhida) 3.dp else 1.dp, if (escolhida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { aoMudar(p) },
                    )
                }
            }
        }
    }
}

/** Janela com uma lista (e pesquisa) para escolher uma opção. */
@Composable
private fun DialogoEscolha(titulo: String, opcoes: List<OpcaoUi>, aoEscolher: (OpcaoUi) -> Unit, aoFechar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    val visiveis = opcoes.filter { texto.isBlank() || it.texto.contains(texto.trim(), true) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = {
            Column {
                if (opcoes.size > 8) {
                    OutlinedTextField(texto, { texto = it }, Modifier.fillMaxWidth(), label = { Text("Procurar") }, singleLine = true)
                }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(visiveis.take(200), key = { it.id }) { o ->
                        Text(o.texto, Modifier.fillMaxWidth().clickable { aoEscolher(o) }.padding(vertical = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
    )
}
