package pt.futebolcarreira.nucleo.aovivo

import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Formacao
import pt.futebolcarreira.nucleo.modelos.Mentalidade
import pt.futebolcarreira.nucleo.simulacao.Evento
import pt.futebolcarreira.nucleo.simulacao.FichaJogador
import pt.futebolcarreira.nucleo.simulacao.TipoEvento
import kotlin.math.roundToInt

/**
 * Liga o contrato "ControloTatico" ao MotorAoVivo atual, só com os membros "internal" do motor (sem o editar).
 * Se o motor mudar o nome de algum destes membros, a junção tem de acertar aqui (ver o relatório da fase E).
 * Guarda a formação e a mentalidade escolhidas (o motor só guarda os deslocamentos já calculados).
 */
class TaticaDoMotor(private val m: MotorAoVivo) : ControloTatico {
    private val formacao = arrayOf(m.casa.formacao, m.fora.formacao)
    private val mentalidade = arrayOf(m.casa.mentalidade ?: Mentalidade.EQUILIBRADA, m.fora.mentalidade ?: Mentalidade.EQUILIBRADA)

    override val suportado: Boolean get() = true

    private fun e(casa: Boolean) = if (casa) 0 else 1
    private fun indices(casa: Boolean) = (e(casa) * 11) until (e(casa) * 11 + 11)

    private fun lesionadoAgora(j: pt.futebolcarreira.nucleo.modelos.Jogador) = j.lesao > 0

    override fun onzeEmCampo(casa: Boolean): List<JogadorTatico> =
        indices(casa).filter { m.ativo[it] && m.jog[it] != null }.map { i ->
            val j = m.jog[i]!!
            JogadorTatico(j.id, j.nome, j.posicao, m.lugar[i], m.numero[i], m.stam[i].roundToInt().coerceIn(0, 100), false, m.amarelos[i], j.geral())
        }

    override fun banco(casa: Boolean): List<JogadorTatico> =
        m.banco[e(casa)].mapIndexed { k, j -> JogadorTatico(j.id, j.nome, j.posicao, "", 12 + k, 100, lesionadoAgora(j), 0, j.geral()) }

    override fun substituicoesFeitas(casa: Boolean): Int =
        m.eventos.count { it.tipo == TipoEvento.SUBSTITUICAO && it.casa == casa }

    override fun substituir(casa: Boolean, saiId: Int, entraId: Int): ResultadoSubstituicao {
        val eq = e(casa)
        val i = indices(casa).firstOrNull { m.ativo[it] && m.jog[it]?.id == saiId }
        val entra = m.banco[eq].firstOrNull { it.id == entraId }
        val r = GestorSubstituicoes.validar(substituicoesFeitas(casa), i?.let { m.lugar[it] }, entra?.posicao, entra != null && !lesionadoAgora(entra))
        if (r != ResultadoSubstituicao.FEITA || i == null || entra == null) return r
        val sai = m.jog[i]!!
        val minuto = m.minutoEvento()
        m.parte[i]?.ficha?.saiu = minuto
        m.banco[eq].remove(entra)
        m.colocarJogador(i, entra)
        m.stam[i] = 100f
        m.amarelos[i] = 0
        val ficha = FichaJogador(entra, m.lugar[i], false, minuto)
        val np = Parte(eq, entra, ficha)
        m.parte[i] = np
        m.partes += np
        m.eventos += Evento(minuto, TipoEvento.SUBSTITUICAO, casa, sai, entra)
        m.mensagem("Sai ${sai.nome}, entra ${entra.nome}", 2.5f)
        return ResultadoSubstituicao.FEITA
    }

    override fun formacaoAtual(casa: Boolean): Formacao = formacao[e(casa)]

    /** Redistribui os jogadores em campo pelos lugares da nova formação (o guarda-redes mantém-se). */
    override fun mudarFormacao(casa: Boolean, formacao: Formacao): Boolean {
        val eq = e(casa)
        if (formacao == this.formacao[eq]) return true
        val ativos = indices(casa).filter { m.ativo[it] }.toMutableList()
        val lugares = formacao.lugares.toMutableList()
        // Atribuição gananciosa: de cada vez, o par (jogador, lugar) com melhor nota.
        val atribuido = HashMap<Int, String>()
        val gr = ativos.firstOrNull { m.lugar[it] == "GR" }
        if (gr != null && lugares.remove("GR")) { atribuido[gr] = "GR"; ativos.remove(gr) }
        while (ativos.isNotEmpty() && lugares.isNotEmpty()) {
            var melhorI = -1; var melhorL = -1; var melhorNota = Int.MIN_VALUE
            for (i in ativos) for ((li, l) in lugares.withIndex()) {
                val nota = Equipa.notaNoLugar(m.jog[i]!!, l)
                if (nota > melhorNota) { melhorNota = nota; melhorI = i; melhorL = li }
            }
            atribuido[melhorI] = lugares.removeAt(melhorL)
            ativos.remove(melhorI)
        }
        val avancados = formacao.lugares.count { it == "PL" }
        val contados = HashMap<String, Int>()
        for ((i, l) in atribuido.entries.sortedBy { it.key }.map { it.key to it.value }) {
            val n = formacao.lugares.count { it == l }
            val k = contados.merge(l, 1, Int::plus)!! - 1
            val (u, v) = ponto(l, k, n, avancados)
            m.lugar[i] = l; m.baseU[i] = u; m.baseV[i] = v
            m.papel[i] = papelDe(l, u)
        }
        this.formacao[eq] = formacao
        return true
    }

    override fun mentalidadeAtual(casa: Boolean): Mentalidade = mentalidade[e(casa)]

    override fun mudarMentalidade(casa: Boolean, mentalidade: Mentalidade): Boolean {
        val eq = e(casa)
        this.mentalidade[eq] = mentalidade
        m.mentShift[eq] = when (mentalidade) { Mentalidade.OFENSIVA -> 4f; Mentalidade.DEFENSIVA -> -4f; else -> 0f }
        return true
    }

    override fun aplicarPalestra(casa: Boolean, palestra: Palestra): Int {
        val eq = e(casa)
        val d = palestra.efeitoMoral(m.golos[eq], m.golos[1 - eq])
        if (d == 0) return 0
        for (i in indices(casa)) {
            val j = m.jog[i] ?: continue
            if (!m.ativo[i]) continue
            j.moral = (j.moral + d).coerceIn(0, 100)
            m.colocarJogador(i, j)   // recalcula os atributos com o novo bónus de moral
        }
        return d
    }

    private fun papelDe(l: String, u: Float) = when (l) {
        "GR" -> 0
        "DD", "DC", "DE" -> 1
        "MDC", "MC", "MOC" -> 2
        "PL" -> 3
        else -> if (u > 10f) 3 else 2
    }

    // Cópia da geometria do motor (MotorAoVivo.ponto é privado): posição base de cada lugar da formação.
    private fun ponto(l: String, k: Int, n: Int, avancados: Int): Pair<Float, Float> {
        fun espalhar(largura: Float): Float = if (n == 1) 0f else (k - (n - 1) / 2f) * (largura / (n - 1))
        return when (l) {
            "GR" -> -49f to 0f
            "DD" -> -30f to -24f
            "DE" -> -30f to 24f
            "DC" -> -32f to espalhar(if (n >= 3) 22f else 17f)
            "MDC" -> -16f to espalhar(16f)
            "MC" -> -6f to espalhar(22f)
            "MOC" -> 8f to 0f
            "ED" -> (if (avancados >= 2) 3f else 17f) to -26f
            "EE" -> (if (avancados >= 2) 3f else 17f) to 26f
            "PL" -> (if (n == 1) 26f else 24f) to espalhar(14f)
            else -> 0f to 0f
        }
    }
}
