package pt.futebolcarreira.ecras

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.futebolcarreira.JogoViewModel
import pt.futebolcarreira.nucleo.carreira.CandidatoStaff
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Funcao
import pt.futebolcarreira.nucleo.carreira.Mercado
import pt.futebolcarreira.nucleo.carreira.MembroStaff
import pt.futebolcarreira.nucleo.modelos.Jogador

/** G2: o que cada função da equipa técnica dá ao clube, em palavras. */
private fun bonus(f: Funcao, nivel: Int): String {
    val n = nivel - 1
    return when (f) {
        Funcao.ADJUNTO -> if (n == 0) "Equipa básica: sem bónus." else "Jovens evoluem +${"%.1f".format(3.5 * n)}% · ${3 + n} treinos individuais"
        Funcao.PREPARADOR -> if (n == 0) "Equipa básica: sem bónus." else "Lesões no treino −${7 * n}% · veteranos perdem −${6 * n}% menos"
        Funcao.MEDICO -> if (n == 0) "Equipa básica: sem bónus." else "Paragens mais curtas (+${5 * n}% por jogo) · lesões −${3 * n}%"
        Funcao.FISIOTERAPEUTA -> if (n == 0) "Equipa básica: sem bónus." else "Recuperação +${4 * n}% por jogo · lesões −${4 * n}%"
        Funcao.OLHEIRO -> "Relatórios com ${2 + nivel} jovens · viagens de ${28 - 3 * n} dias"
    }
}

/** Separador «Equipa técnica»: contratar e despedir adjunto, preparador físico, médicos e olheiros. */
@Composable
internal fun EcraEquipaTecnica(c: Carreira, ativo: Boolean, jogo: JogoViewModel, versao: Int) {
    val g = c.gestaoClube
    val et = g.equipaTecnica
    val secundaria = MaterialTheme.colorScheme.onSurfaceVariant
    // Candidatos e membros copiados para a lembrança: só se refazem quando algo muda.
    val funcoes = rememberGestao(c, versao) { Funcao.entries.map { f -> Triple(f, et.membros(f).toList(), et.candidatos(f)) } }
    val custo = rememberGestao(c, versao) { Mercado.euros(et.custoAnual()) }
    val nivelMax = rememberGestao(c, versao) { et.nivelMaximo() }
    val caixa = rememberGestao(c, versao) { Mercado.euros(c.equipa.orcamento) }
    var enviar by remember { mutableStateOf<MembroStaff?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Bloco {
                Rotulo("Equipa técnica")
                LinhaValor("Custo anual", custo, negrito = true)
                LinhaValor("Caixa", caixa)
                LinhaValor("Nível máximo que o clube atrai", estrelas(nivelMax))
                Text(
                    "Sem ninguém contratado o clube tem uma equipa básica (nível 1), já incluída nas contas. Quem contratares custa um salário que sai ao longo da época e melhora o treino, as lesões ou o scouting.",
                    Modifier.padding(top = 6.dp), fontSize = 12.sp, color = secundaria,
                )
            }
        }
        for ((f, membros, candidatos) in funcoes) {
            item(key = f.name) {
                Bloco {
                    val nivel = membros.maxOfOrNull { it.nivel } ?: 1
                    Rotulo("${f.nome} · nível $nivel")
                    Text(f.efeito, fontSize = 12.sp, color = secundaria)
                    Text(bonus(f, nivel), Modifier.padding(top = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    for (m in membros) {
                        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.nome, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("${estrelas(m.nivel)} · ${Mercado.euros(m.salario.toLong())} por ano", fontSize = 12.sp, color = secundaria)
                                if (f == Funcao.OLHEIRO) {
                                    val missao = m.missao
                                    Text(
                                        if (missao == null) "Livre para viajar"
                                        else "Em viagem: ${c.bd.paises[missao.pais]?.nome ?: missao.pais} (regressa a ${missao.regressa})",
                                        fontSize = 12.sp, color = if (missao == null) MaterialTheme.colorScheme.primary else secundaria,
                                    )
                                }
                            }
                            if (f == Funcao.OLHEIRO && m.missao == null) {
                                OutlinedButton(onClick = { enviar = m }, enabled = ativo, modifier = Modifier.heightIn(min = 48.dp)) { Text("Enviar", fontSize = 13.sp) }
                            }
                            TextButton(onClick = { jogo.gerir { frase(et.despedir(m.id), "${m.nome} saiu da equipa técnica.") } }, enabled = ativo, modifier = Modifier.heightIn(min = 48.dp)) { Text("Despedir", fontSize = 13.sp) }
                        }
                    }
                    if (membros.size < f.maximo) {
                        Text(
                            if (f.maximo > 1) "Candidatos (${membros.size} de ${f.maximo})" else "Candidatos",
                            Modifier.padding(top = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        )
                        for (cand in candidatos) CandidatoLinha(cand, ativo) { jogo.gerir { frase(et.contratar(cand), "${cand.nome} juntou-se à equipa técnica.") } }
                    }
                }
            }
        }
    }

    enviar?.let { m -> DialogoOlheiro(c, m, { enviar = null }) { pais, pos -> enviar = null; jogo.gerir { frase(et.enviar(m.id, pais, pos), "${m.nome} partiu em viagem.") } } }
}

@Composable
private fun CandidatoLinha(cand: CandidatoStaff, ativo: Boolean, aoContratar: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(cand.nome, fontSize = 14.sp)
            Text("${estrelas(cand.nivel)} · ${Mercado.euros(cand.salario.toLong())} por ano", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = aoContratar, enabled = ativo, modifier = Modifier.heightIn(min = 48.dp)) { Text("Contratar", fontSize = 13.sp) }
    }
}

/** Escolher o país (e a posição) para onde um olheiro contratado vai. */
@Composable
private fun DialogoOlheiro(c: Carreira, m: MembroStaff, aoFechar: () -> Unit, aoEnviar: (String, String) -> Unit) {
    val paises = remember { c.bd.paises.values.toList() }
    var pais by remember { mutableStateOf(paises.firstOrNull()?.id ?: "") }
    var posicao by remember { mutableStateOf("") }
    val et = c.gestaoClube.equipaTecnica
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Enviar ${m.nome}") },
        text = {
            Column {
                Text("Viagem de ${et.diasDeViagem(m)} dias: custa ${Mercado.euros(et.custoViagem(m, pais))}.", fontSize = 13.sp)
                Text("País", Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in paises) ChipToque(pais == p.id, { pais = p.id }, { Text(p.nome) })
                }
                Text("Posição", Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChipToque(posicao.isEmpty(), { posicao = "" }, { Text("Todas") })
                    for (p in Jogador.POSICOES) ChipToque(posicao == p, { posicao = p }, { Text(p) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { aoEnviar(pais, posicao) }, enabled = pais.isNotEmpty()) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}
