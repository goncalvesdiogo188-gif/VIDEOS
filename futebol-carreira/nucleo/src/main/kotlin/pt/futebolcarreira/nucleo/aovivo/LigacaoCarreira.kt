package pt.futebolcarreira.nucleo.aovivo

import pt.futebolcarreira.nucleo.carreira.Carreira

/**
 * A exigência de vencedor do próximo jogo do clube, para passar a [FabricaPartida.criar] (parâmetro "exigencia"):
 * null num jogo de liga, na fase de liga europeia ou na primeira mão de uma eliminatória; nos outros jogos (jogo único ou última mão
 * de taças e competições europeias) traz os golos que cada equipa já tinha na eliminatória, para o agregado contar.
 * Com ela, o empate no fim dos 90 minutos leva a prolongamento e penáltis ao vivo, e o relatório fica pronto para
 * [Carreira.registarJogoAoVivo] (que usa os penáltis jogados em vez de os sortear).
 */
fun Carreira.exigenciaDoProximoJogo(): ExigenciaVencedor? {
    val jogo = proximoJogo() ?: return null
    val confronto = jogo.confronto ?: return null
    val ultima = !confronto.idaEVolta || jogo.perna == confronto.datas.size
    if (!ultima) return null
    val casaId = if (jogo.emCasa) equipa.id else jogo.adversario.id
    val foraId = if (jogo.emCasa) jogo.adversario.id else equipa.id
    return ExigenciaVencedor(confronto.golos(casaId), confronto.golos(foraId))
}
