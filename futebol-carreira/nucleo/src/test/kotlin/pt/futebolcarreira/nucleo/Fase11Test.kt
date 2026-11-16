package pt.futebolcarreira.nucleo

import pt.futebolcarreira.nucleo.aovivo.Comando
import pt.futebolcarreira.nucleo.aovivo.FabricaPartida
import pt.futebolcarreira.nucleo.aovivo.FaseJogo
import pt.futebolcarreira.nucleo.aovivo.PartidaAoVivo
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.modelos.Equipa
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/** Testes da Fase 11 (parte A): motor da partida ao vivo, sem desenhar nada. */
class Fase11Test {
    private val pastaDados = File("../app/src/main/assets/dados")
    private val ler = { caminho: String -> File(pastaDados, caminho).takeIf(File::exists)?.readText() }

    private fun carregar(): BaseDados {
        val bd = BaseDados()
        assertTrue(bd.carregar(ler), "carregar: ${bd.erros.take(3)}")
        return bd
    }

    /** Joga um jogo todo sem comandos; devolve a partida e o número de passos. */
    private fun jogar(casa: Equipa, fora: Equipa, semente: Int, comando: Comando = Comando(), semUtil: Boolean = true): Pair<PartidaAoVivo, Int> {
        val p = FabricaPartida.criar(casa, fora, true, Random(semente), semUtilizador = semUtil)
        var passos = 0
        while (!p.terminou && passos < 20_000) { p.passo(1f / 30f, comando); passos++ }
        return p to passos
    }

    @Test
    fun `calibracao temporaria`() {
        val bd = carregar()
        val todas = bd.equipas.values.filter { it.plantel.size >= 16 }.sortedBy { it.id }
        val rnd = Random(7)
        var n = 0; var golos = 0; var posse = 0; var rem = 0; var alvo = 0; var cantos = 0; var faltas = 0; var am = 0
        val dbg = IntArray(12); var pas = 0; var pasOk = 0; var des = 0; var fortesGanham = 0; var fortesJogos = 0; var empates = 0
        val t0 = System.nanoTime()
        for (k in 0 until 40) {
            val a = todas[rnd.nextInt(todas.size)]; val b = todas[rnd.nextInt(todas.size)]
            if (a === b) continue
            val (p, _) = jogar(a, b, k + 1)
            val rel = FabricaPartida.relatorio(p)
            n++; golos += rel.golosCasa + rel.golosFora; posse += rel.casa.estatisticas.posse
            rem += rel.casa.estatisticas.remates + rel.fora.estatisticas.remates
            alvo += rel.casa.estatisticas.rematesBaliza + rel.fora.estatisticas.rematesBaliza
            cantos += rel.casa.estatisticas.cantos + rel.fora.estatisticas.cantos
            faltas += rel.casa.estatisticas.faltas + rel.fora.estatisticas.faltas
            am += rel.casa.estatisticas.amarelos + rel.fora.estatisticas.amarelos
            val m = p as pt.futebolcarreira.nucleo.aovivo.MotorAoVivo
            pas += m.partes.sumOf { it.passes }; pasOk += m.partes.sumOf { it.passesOk }; des += m.partes.sumOf { it.desarmes }; for (q in 0 until 12) dbg[q] += m.dbg[q]
            val fa = a.forca().geral; val fb = b.forca().geral
            if (Math.abs(fa - fb) > 6) { fortesJogos++; val ganhouForte = if (fa > fb) rel.golosCasa > rel.golosFora else rel.golosFora > rel.golosCasa; if (ganhouForte) fortesGanham++ else if (rel.golosCasa == rel.golosFora) empates++ }
            if (k < 10) println("CAL ${a.id}(${"%.0f".format(fa)}) ${rel.golosCasa}-${rel.golosFora} ${b.id}(${"%.0f".format(fb)}) posse=${rel.casa.estatisticas.posse} rem=${rel.casa.estatisticas.remates}-${rel.fora.estatisticas.remates}")
        }
        val ms = (System.nanoTime() - t0) / 1e6
        println("CAL jogos=$n golos/jogo=${golos.toDouble() / n} posseCasa=${posse / n} remates/jogo=${rem.toDouble() / n} alvo=${alvo.toDouble() / n} cantos=${cantos.toDouble() / n} faltas=${faltas.toDouble() / n} amarelos=${am.toDouble() / n} passes=${pas / n} ok=${pasOk * 100 / maxOf(1, pas)}% desarmes=${des / n} ms/jogo=${ms / n} fortesGanham=$fortesGanham/$fortesJogos empates=$empates dbg=${dbg.map { it / n }}")
        val ordem = todas.sortedBy { it.forca().geral }
        val fracos = ordem.take(10); val fortes = ordem.takeLast(10)
        var g = 0; var e = 0; var d = 0; var gm = 0; var gs = 0
        for (k in 0 until 24) {
            val f = fortes[k % 10]; val w = fracos[(k * 3) % 10]
            val (p, _) = if (k % 2 == 0) jogar(f, w, 100 + k) else jogar(w, f, 100 + k)
            val rel = FabricaPartida.relatorio(p)
            val gf = if (k % 2 == 0) rel.golosCasa else rel.golosFora
            val gw = if (k % 2 == 0) rel.golosFora else rel.golosCasa
            gm += gf; gs += gw
            if (gf > gw) g++ else if (gf == gw) e++ else d++
        }
        println("CAL fortes vs fracos: V=$g E=$e D=$d golos $gm-$gs (geral ${"%.0f".format(fortes.map { it.forca().geral }.average())} vs ${"%.0f".format(fracos.map { it.forca().geral }.average())})")
        assertTrue(true)
    }
}
