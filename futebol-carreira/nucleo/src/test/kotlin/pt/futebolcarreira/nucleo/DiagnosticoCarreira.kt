package pt.futebolcarreira.nucleo

import pt.futebolcarreira.nucleo.aovivo.Comando
import pt.futebolcarreira.nucleo.aovivo.FabricaPartida
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.carreira.Gravador
import pt.futebolcarreira.nucleo.carreira.ModoCarreira
import pt.futebolcarreira.nucleo.dados.BaseDados
import java.io.File
import kotlin.random.Random
import kotlin.test.Test

/** Joga várias épocas de carreira (com alguns jogos ao vivo pelo meio, gravação e carregamento) e regista problemas. */
class DiagnosticoCarreira {
    @Test
    fun diagnosticoCarreira() {
        val out = StringBuilder()
        val bd = BaseDados(); bd.carregar { File("../app/src/main/assets/dados", it).takeIf(File::exists)?.readText() }
        for ((ligaId, grande, semente) in listOf(Triple("POR1", true, 3L), Triple("ESP1", false, 4L), Triple("ING2", true, 5L), Triple("ITA1", false, 6L))) {
            val clube = bd.equipasDaLiga(ligaId).let { l -> if (grande) l.maxBy { it.reputacao } else l.minBy { it.reputacao } }
            var c = Carreira.nova(bd, "d", "D", ModoCarreira.TREINADOR, clube.id, semente = semente)
            for (epoca in 1..4) {
                try {
                    var ao = 0
                    var guarda = 0
                    while (!c.gestor.epocaTerminada() && !c.info.despedido && ao < 4 && guarda++ < 80) {
                        val par = c.prepararJogoAoVivo() ?: break
                        val (casa, fora) = par
                        val p = FabricaPartida.criar(casa, fora, casa.id == c.equipa.id, Random(epoca * 31 + ao), 60f)
                        var n = 0
                        while (!p.terminou && n++ < 4000) p.passo(1f / 30f, Comando(movX = 1f, sprint = true, remate = n % 7 == 0))
                        if (!p.terminou) out.append("!! ${ligaId} época $epoca: jogo ao vivo não terminou\n")
                        c.registarJogoAoVivo(FabricaPartida.relatorio(p)); ao++
                    }
                    c.simularEpoca()
                    if (c.info.despedido) { out.append("   $ligaId época $epoca: despedido, aceita proposta\n"); c.aceitarProposta(c.info.propostas.first()); c.simularEpoca() }
                    val eq = c.equipa
                    val l = c.estadoLiga.tabela.linhas.getValue(eq.id)
                    val problemas = mutableListOf<String>()
                    if (eq.plantel.size < 16) problemas += "plantel pequeno (${eq.plantel.size})"
                    if (eq.plantel.any { it.idade !in 15..43 }) problemas += "idades fora de 15..43: ${eq.plantel.filter { it.idade !in 15..43 }.map { "${it.nome} ${it.idade}" }.take(3)}"
                    if (eq.plantel.count { it.posicao == "GR" } < 2) problemas += "menos de 2 guarda-redes"
                    if (l.pts != 3 * l.v + l.e) problemas += "pontos incoerentes (${l.pts} != 3*${l.v}+${l.e})"
                    if (l.j != l.v + l.e + l.d) problemas += "jogos incoerentes"
                    out.append("$ligaId época $epoca: ${eq.nome} ${l.pts} pts (${l.v}V ${l.e}E ${l.d}D) plantel ${eq.plantel.size} [${l.j} jogos, ${c.estadoLiga.tabela.linhas.size} equipas, GM ${l.gm} GS ${l.gs}]${if (problemas.isEmpty()) "" else "  PROBLEMAS: $problemas"}\n")
                    c.novaEpoca()
                    // gravar e voltar a carregar
                    c = Gravador.deTexto(Gravador.paraTexto(c), Gravador.baseDadosParaTexto(c))
                } catch (e: Throwable) {
                    out.append("!! $ligaId época $epoca: EXCEÇÃO ${e::class.simpleName}: ${e.message?.take(150)}\n")
                    e.stackTrace.take(4).forEach { out.append("      at $it\n") }
                    break
                }
            }
        }
        File("DIAGNOSTICO_CARREIRA.txt").writeText(out.toString())
        println(out)
    }
}
