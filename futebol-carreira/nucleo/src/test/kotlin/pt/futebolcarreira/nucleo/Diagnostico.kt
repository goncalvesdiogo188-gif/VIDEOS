package pt.futebolcarreira.nucleo

import pt.futebolcarreira.nucleo.aovivo.Comando
import pt.futebolcarreira.nucleo.aovivo.Dificuldade
import pt.futebolcarreira.nucleo.aovivo.EstadoPartida
import pt.futebolcarreira.nucleo.aovivo.FabricaPartida
import pt.futebolcarreira.nucleo.aovivo.FaseJogo
import pt.futebolcarreira.nucleo.dados.BaseDados
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.test.Test

/** Joga partidas completas com um utilizador simulado e regista tudo o que parece errado (saída em DIAGNOSTICO.txt). */
class Diagnostico {
    private val pasta = File("../app/src/main/assets/dados")

    private class Anomalias {
        val msgs = LinkedHashMap<String, Int>()
        fun add(k: String) { msgs[k] = (msgs[k] ?: 0) + 1 }
    }

    private fun guiao(e: EstadoPartida, passo: Int, rng: Random): Comando {
        val sentido = if (e.segundaParte) -1f else 1f
        val c = e.jogadores.firstOrNull { it.controlado } ?: return Comando()
        val perto = c.x * sentido > 22f
        val temBola = hypot(c.x - e.bola.x, c.y - e.bola.y) < 1.5f
        return Comando(
            movX = if (temBola || rng.nextFloat() < 0.8f) sentido else -sentido * 0.3f,
            movY = (-c.y / 30f).coerceIn(-0.6f, 0.6f) + (rng.nextFloat() - 0.5f) * 0.4f,
            sprint = passo % 90 < 60, remate = perto && temBola && passo % 5 == 0, forca = 0.7f,
            passe = !perto && temBola && passo % 45 == 0, trocar = passo % 40 == 0,
        )
    }

    @Test
    fun diagnostico() {
        val bd = BaseDados()
        bd.carregar { File(pasta, it).takeIf(File::exists)?.readText() }
        val eq = bd.equipas.values.filter { it.plantel.size >= 16 }.sortedBy { it.id }
        val out = StringBuilder()
        val tot = Anomalias()
        var jogos = 0
        for (dif in Dificuldade.entries) for (semente in 1..5) for (fps in intArrayOf(30, 60)) {
            val casa = eq[(semente * 7) % eq.size]; val fora = eq[(semente * 13 + 5) % eq.size]
            val dt = 1f / fps
            val p = FabricaPartida.criar(casa, fora, true, Random(semente * 100 + fps), 480f, semente == 2, dif, null)
            val rng = Random(semente)
            var passo = 0; var t = 0f
            var faseAnt = p.estado.fase; var tFase = 0f; var maxTFase = 0f; var faseMax = faseAnt
            var bolaParadaT = 0f
            var anterior = p.estado.jogadores.map { it.x to it.y }
            var anteriorBola = p.estado.bola
            val a = Anomalias()
            var trocas = 0; var ctrlAnt = -1
            var maxSalto = 0f
            var ctxGolo = false
            while (!p.terminou && t < 480f * 1.5f) {
                val e0 = p.estado
                p.passo(dt, guiao(e0, passo, rng))
                passo++; t += dt
                val e = p.estado
                // NaN / fora do campo
                if (e.bola.x.isNaN() || e.bola.y.isNaN() || e.bola.z.isNaN()) a.add("bola NaN")
                if (abs(e.bola.x) > 75f || abs(e.bola.y) > 55f) a.add("bola muito longe do campo")
                if (e.bola.z > 40f) {
                    a.add("bola a mais de 40 m de altura")
                    if (a.msgs["bola a mais de 40 m de altura"] == 1) out.append("    contexto: t=${"%.1f".format(t)} bola ${"%.1f".format(e.bola.x)},${"%.1f".format(e.bola.y)},${"%.1f".format(e.bola.z)} fase=${e.fase}\n")
                }
                if (e.jogadores.size < 18 || e.jogadores.size > 22) a.add("jogadores em campo fora de 18..22 (${e.jogadores.size})")
                for ((i, j) in e.jogadores.withIndex()) {
                    if (j.x.isNaN() || j.y.isNaN()) a.add("jogador NaN")
                    if (abs(j.x) > 60f || abs(j.y) > 42f) a.add("jogador fora do campo (>60/42)")
                    val ant = anterior.getOrNull(i)
                    if (ant != null) {
                        val d = hypot(j.x - ant.first, j.y - ant.second)
                        if (d > maxSalto) maxSalto = d
                        if (d > 12f * dt + 0.5f) {
                            val k = "salto >12 m/s ${e0.fase}->${e.fase} (máx ${"%.0f".format(d)} m)"
                            if (e0.fase == e.fase && e.fase == FaseJogo.A_JOGAR) {
                                a.add("SALTO EM JOGO CORRIDO")
                                if (a.msgs["SALTO EM JOGO CORRIDO"] == 1) out.append("    contexto: t=${"%.1f".format(t)} jogador ${j.nome} ${"%.1f".format(ant.first)},${"%.1f".format(ant.second)} -> ${"%.1f".format(j.x)},${"%.1f".format(j.y)} anim=${j.animacao} min=${e.minuto}\n")
                            } else if (e0.fase != FaseJogo.INTERVALO) {
                                a.add(k)
                                if (e0.fase == FaseJogo.GOLO && d > 5f && !ctxGolo) { ctxGolo = true; out.append("    contexto golo: ${j.nome} (${j.posicao}, ${j.animacao}) ${"%.1f".format(ant.first)},${"%.1f".format(ant.second)} -> ${"%.1f".format(j.x)},${"%.1f".format(j.y)} t=${"%.1f".format(t)}\n") }
                            }
                        }
                    }
                }
                anterior = e.jogadores.map { it.x to it.y }
                val ctrl = e.jogadores.indexOfFirst { it.controlado }
                if (ctrl != ctrlAnt) { trocas++; ctrlAnt = ctrl }
                if (e.jogadores.count { it.controlado } > 1) a.add("mais de um jogador controlado")
                if (e.fase == faseAnt) tFase += dt else { faseAnt = e.fase; tFase = 0f }
                if (tFase > maxTFase) { maxTFase = tFase; faseMax = e.fase }
                if (e.fase == FaseJogo.A_JOGAR) {
                    val v = hypot(e.bola.x - anteriorBola.x, e.bola.y - anteriorBola.y)
                    if (v < 0.001f && e.bola.z < 0.3f) bolaParadaT += dt else bolaParadaT = 0f
                    if (bolaParadaT > 8f) {
                        a.add("bola parada 8 s em jogo")
                        val perto = e.jogadores.minByOrNull { hypot(it.x - e.bola.x, it.y - e.bola.y) }!!
                        (p as? pt.futebolcarreira.nucleo.aovivo.MotorAoVivo)?.let { m ->
                            out.append("    motor: dono=${m.dono} receptor=${m.receptor} ctrl=${m.controlado} fase=${m.estado.fase} presser1=${m.presser1.toList()} ")
                            val ii = e.jogadores.indexOfFirst { it.id == perto.id }
                            out.append("alvo do mais perto=${"%.1f".format(m.alvoX[ii])},${"%.1f".format(m.alvoY[ii])} cd=${"%.2f".format(m.cd[ii])} atordoado=${"%.1f".format(m.atordoado[ii])} bvz=${m.bvz}\n")
                        }
                        out.append("    contexto: t=${"%.1f".format(t)} bola ${"%.1f".format(e.bola.x)},${"%.1f".format(e.bola.y)} mais perto: ${perto.nome} (${perto.posicao}) a ${"%.1f".format(hypot(perto.x - e.bola.x, perto.y - e.bola.y))} m; min=${e.minuto}\n")
                        bolaParadaT = 0f
                    }
                }
                anteriorBola = e.bola
            }
            val e = p.estado
            val dur = t
            if (!p.terminou) a.add("O JOGO NÃO TERMINOU em ${dur.toInt()} s")
            jogos++
            out.append("[${dif.name} s$semente ${fps}fps] ${casa.nome} ${e.golosCasa}-${e.golosFora} ${fora.nome}; ${dur.toInt()} s reais; fase mais longa ${faseMax}=${"%.1f".format(maxTFase)} s; trocas de controlo $trocas; maior salto ${"%.2f".format(maxSalto)} m\n")
            for ((k, v) in a.msgs) { out.append("    ! $k  x$v\n"); tot.add(k) }
        }
        out.append("\nRESUMO ($jogos jogos): ${if (tot.msgs.isEmpty()) "nenhuma anomalia" else tot.msgs.entries.joinToString("; ") { "${it.key} (${it.value} jogos)" }}\n")
        File("DIAGNOSTICO.txt").writeText(out.toString())
        println(out)
    }
}
