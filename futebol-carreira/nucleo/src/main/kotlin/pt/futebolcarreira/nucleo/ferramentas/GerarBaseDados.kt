package pt.futebolcarreira.nucleo.ferramentas

import kotlinx.serialization.Serializable
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.dados.GeradorJogadores
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.simulacao.gauss
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * FERRAMENTA: cria a base de dados fictícia inicial (equipas e jogadores).
 *
 * ATENÇÃO: apaga e volta a criar dados/equipas e dados/jogadores.
 * Só é preciso correr se quiseres uma base de dados nova:
 *   ./gradlew :nucleo:gerarBaseDados
 *
 * Os nomes de locais e de clubes vêm de nucleo/src/main/resources/gerador/modelo.json.
 * Todos os nomes são inventados. Para usar nomes reais, edita os JSON gerados.
 */

/** "topo" = reputação do melhor clube do país; "tamanho" = equipas por divisão. */
@Serializable
private class ModeloPais(
    val id: String, val topo: Int, val tamanho: Int, val ligas: List<String>,
    val clubes: List<String>, val estadios: List<String>,
    /** Por ordem aproximada de força (os primeiros são os "grandes"). */
    val locais: List<String>,
)

@Serializable
private class Modelo(val cores: List<String>, val paises: List<ModeloPais>)

private const val SEMENTE = 20260815L

fun main(args: Array<String>) {
    val pasta = File(args.firstOrNull() ?: "app/src/main/assets/dados")
    val rng = Random(SEMENTE)
    val modelo = BaseDados.JSON.decodeFromString(
        Modelo.serializer(),
        object {}.javaClass.getResource("/gerador/modelo.json")!!.readText(),
    )

    val bd = BaseDados()
    bd.carregar { File(pasta, it).takeIf(File::exists)?.readText() }  // lê países, competições e nomes
    bd.equipas.clear()
    bd.jogadores.clear()
    val gerador = GeradorJogadores(bd.nomes, rng)
    val siglasUsadas = HashSet<String>()

    for (pais in modelo.paises) {
        require(pais.locais.size == pais.tamanho * 2) { "Número de locais errado em ${pais.id}" }
        pais.locais.forEachIndexed { i, local ->
            val divisao = i / pais.tamanho      // 0 = primeira, 1 = segunda
            val lugar = i % pais.tamanho
            val topo = pais.topo - divisao * 25.0
            val amplitude = if (divisao == 0) 26.0 else 15.0
            val fracao = (lugar.toDouble() / (pais.tamanho - 1)).pow(0.7)
            val reputacao = (topo - amplitude * fracao + rng.gauss(0.0, 1.2)).roundToInt().coerceIn(20, 95)
            val cor1 = modelo.cores.random(rng)
            val e = Equipa(
                id = "%s_%02d".format(pais.id, i + 1),
                nome = pais.clubes.random(rng).replace("%s", local),
                abreviatura = sigla(local, siglasUsadas),
                pais = pais.id,
                liga = pais.ligas[divisao],
                cidade = local,
                estadio = pais.estadios.random(rng).replace("%s", local),
                capacidade = 3000 + ((reputacao - 30.0).pow(2) * 22.0 / 100.0).toInt() * 100,
                reputacao = reputacao,
                orcamento = (1.15.pow(reputacao - 40) * 4.0).toLong() * 100_000 + 300_000,
                cores = listOf(cor1, (modelo.cores - cor1).random(rng)),
            )
            bd.equipas[e.id] = e
            gerador.criarPlantel(e).forEach(bd::adicionarJogador)
        }
    }

    for ((caminho, texto) in bd.paraJson()) {
        File(pasta, caminho).apply { parentFile.mkdirs() }.writeText(texto)
    }
    println("Base de dados criada: ${bd.equipas.size} equipas, ${bd.jogadores.size} jogadores.")
}

/** Sigla de 3 letras, sem repetições. */
private fun sigla(local: String, usadas: MutableSet<String>): String {
    val letras = local.filter { it.isLetter() }.uppercase()
    var sigla = letras.take(3)
    var k = 3
    while (sigla in usadas && k < letras.length) sigla = letras.take(2) + letras[k++]
    var n = 2
    while (sigla in usadas) sigla = letras.take(2) + n++
    usadas += sigla
    return sigla
}
