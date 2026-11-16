package pt.futebolcarreira.nucleo.carreira

import kotlinx.serialization.Serializable
import pt.futebolcarreira.nucleo.dados.GeradorJogadores
import pt.futebolcarreira.nucleo.modelos.AjustesTreino
import java.time.LocalDate
import kotlin.math.pow
import kotlin.random.Random

/**
 * Fase 13 (G2): a equipa técnica do clube do utilizador. Sem ninguém contratado o clube tem uma equipa básica
 * (nível 1) já incluída nas contas, que não custa nada a mais nem dá bónus. Cada pessoa contratada acima disso
 * custa um salário (que cresce com o nível e com a dimensão do clube) e melhora o treino, as lesões ou o scouting.
 */
enum class Funcao(val nome: String, val maximo: Int, val efeito: String) {
    ADJUNTO("Treinador adjunto", 1, "Os jovens evoluem mais depressa, podes dar mais treinos individuais e a mudança de posição é mais rápida."),
    PREPARADOR("Preparador físico", 1, "Menos lesões no treino, veteranos que perdem menos qualidades e mais tolerância à carga."),
    MEDICO("Médico", 1, "Paragens por lesão mais curtas e menos lesões."),
    FISIOTERAPEUTA("Fisioterapeuta", 1, "Recuperação mais rápida entre jogos e menos fadiga acumulada."),
    OLHEIRO("Olheiro", 3, "Cada olheiro faz as suas viagens: mais nível = mais jovens no relatório e viagens mais curtas."),
}

/** Uma pessoa da equipa técnica. */
@Serializable
class MembroStaff(
    val id: String,
    val nome: String,
    val funcao: Funcao,
    /** 1 a 5. */
    val nivel: Int,
    /** Salário por ano, em euros. */
    val salario: Int,
    /** Só nos olheiros: a viagem em curso. */
    var missao: MissaoOlheiro? = null,
)

/** Alguém que se pode contratar (a lista renova-se todos os anos). */
class CandidatoStaff(val id: String, val nome: String, val funcao: Funcao, val nivel: Int, val salario: Int)

@Serializable
class EstadoEquipaTecnica(val membros: MutableList<MembroStaff> = mutableListOf())

class EquipaTecnica(private val c: Carreira, private val gestao: GestaoClube) {
    private val estado get() = gestao.estado.equipaTecnica
    private val meu get() = c.equipa

    val membros: List<MembroStaff> get() = estado.membros

    fun membros(f: Funcao): List<MembroStaff> = estado.membros.filter { it.funcao == f }

    /** Nível do melhor da função (1 = equipa básica, sem bónus). */
    fun nivel(f: Funcao): Int = membros(f).maxOfOrNull { it.nivel } ?: 1

    /** Custo anual de toda a equipa técnica contratada. */
    fun custoAnual(): Long = estado.membros.sumOf { it.salario.toLong() }

    /** Quanto custa um nível, no clube do utilizador (clubes grandes pagam mais). */
    fun salarioDoNivel(nivel: Int): Int = (SALARIOS.getOrElse(nivel - 1) { SALARIOS.last() } * escala(meu.reputacao)).toInt() / 1000 * 1000

    /** O nível máximo que aceita vir para este clube (depende da reputação). */
    fun nivelMaximo(): Int = (1 + meu.reputacao / 20).coerceIn(2, 5)

    /** Os candidatos desta época para uma função (os já contratados saem da lista). */
    fun candidatos(f: Funcao): List<CandidatoStaff> {
        val rng = Random((c.gestor.ano * 7919L + meu.id.hashCode() * 31L + f.ordinal * 101L) xor 0x5DEECE66DL)
        val maximo = nivelMaximo()
        val usados = estado.membros.map { it.id }.toSet()
        return (0 until 5).map { i ->
            // Os níveis espalham-se de 1 até ao máximo que o clube atrai.
            val nivel = (1 + (i * maximo) / 4 + (if (i % 2 == 1 && rng.nextBoolean()) -1 else 0)).coerceIn(1, maximo)
            val nome = NOMES[rng.nextInt(NOMES.size)] + " " + APELIDOS[rng.nextInt(APELIDOS.size)]
            val variacao = 0.92 + rng.nextDouble() * 0.16
            CandidatoStaff("${c.gestor.ano}-${f.name}-$i", nome, f, nivel, (salarioDoNivel(nivel) * variacao).toInt() / 1000 * 1000)
        }.filter { it.id !in usados }.sortedBy { it.nivel }
    }

    fun contratar(candidato: CandidatoStaff): Resposta {
        if (!gestao.podeGerir) return Resposta.NAO_PERMITIDO
        if (estado.membros.any { it.id == candidato.id }) return Resposta.NAO_PERMITIDO
        if (membros(candidato.funcao).size >= candidato.funcao.maximo) return Resposta.EQUIPA_COMPLETA
        gestao.bloqueioSalarios(candidato.salario.toLong())?.let { return it }
        if (meu.orcamento < 0) return Resposta.SEM_DINHEIRO
        estado.membros += MembroStaff(candidato.id, candidato.nome, candidato.funcao, candidato.nivel, candidato.salario)
        gestao.aplicarAjustes()
        c.baseDadosPorGravar = true
        c.mensagem("Equipa técnica: ${candidato.nome}", "${candidato.nome} (${candidato.funcao.nome.lowercase()}, nível ${candidato.nivel}) juntou-se à equipa técnica. Salário: ${Mercado.euros(candidato.salario.toLong())} por ano.")
        return Resposta.ACEITE
    }

    /** Despede alguém: o clube paga um quarto do salário anual de indemnização. */
    fun despedir(id: String): Resposta {
        if (!gestao.podeGerir) return Resposta.NAO_PERMITIDO
        val m = estado.membros.firstOrNull { it.id == id } ?: return Resposta.NAO_PERMITIDO
        val indemnizacao = m.salario / 4L
        if (indemnizacao > meu.orcamento) return Resposta.SEM_DINHEIRO
        meu.orcamento -= indemnizacao
        meu.contas.equipaTecnica += indemnizacao
        estado.membros.remove(m)
        gestao.aplicarAjustes()
        c.mensagem("Equipa técnica: saída", "${m.nome} (${m.funcao.nome.lowercase()}) deixou o clube. Indemnização: ${Mercado.euros(indemnizacao)}.")
        return Resposta.ACEITE
    }

    /** O que a equipa técnica muda no treino e nas lesões (somado ao plano semanal e à fadiga em GestaoClube). */
    fun aplicarEm(a: AjustesTreino) {
        val adj = nivel(Funcao.ADJUNTO) - 1
        val prep = nivel(Funcao.PREPARADOR) - 1
        val med = nivel(Funcao.MEDICO) - 1
        val fis = nivel(Funcao.FISIOTERAPEUTA) - 1
        a.crescimento *= 1.0 + 0.035 * adj
        a.riscoLesao *= (1.0 - 0.07 * prep) * (1.0 - 0.03 * med) * (1.0 - 0.04 * fis)
        a.recuperacao += 0.05 * med + 0.04 * fis
        a.declinio *= 1.0 - 0.06 * prep
    }

    /** Quantos jogadores podem ter treino individual. */
    fun vagasIndividuais(): Int = 3 + (nivel(Funcao.ADJUNTO) - 1)

    // ------------------------------------------------------------ olheiros contratados

    fun diasDeViagem(m: MembroStaff): Long = (28 - 3 * (m.nivel - 1)).toLong()

    fun custoViagem(m: MembroStaff, pais: String): Long = (c.olheiros.custo(pais) * (1.0 - 0.05 * (m.nivel - 1))).toLong()

    fun enviar(id: String, pais: String, posicao: String = ""): Resposta {
        if (!gestao.podeGerir || pais !in c.bd.paises) return Resposta.NAO_PERMITIDO
        val m = estado.membros.firstOrNull { it.id == id && it.funcao == Funcao.OLHEIRO } ?: return Resposta.NAO_PERMITIDO
        if (m.missao != null) return Resposta.OLHEIRO_OCUPADO
        val custo = custoViagem(m, pais)
        if (custo > meu.orcamento) return Resposta.SEM_DINHEIRO
        meu.orcamento -= custo
        meu.contas.olheiros += custo
        m.missao = MissaoOlheiro(pais, posicao, c.gestor.data.plusDays(diasDeViagem(m)).toString())
        return Resposta.ACEITE
    }

    /** Os olheiros que já voltaram entregam o relatório (chamado sempre que o tempo avança). */
    fun verificar() {
        for (m in estado.membros) {
            val missao = m.missao ?: continue
            if (c.gestor.data < LocalDate.parse(missao.regressa)) continue
            m.missao = null
            relatorio(m, missao)
        }
    }

    private fun relatorio(m: MembroStaff, missao: MissaoOlheiro) {
        val teto = meu.forca().geral + 4 + (if (m.nivel >= 4) 2 else 0)
        val folga = 8 + 2 * (m.nivel - 1)
        val achados = c.bd.equipas.values.asSequence()
            .filter { it.pais == missao.pais && it.id != meu.id && it.reputacao <= meu.reputacao + folga }
            .flatMap { it.plantel }
            .filter { it.idade <= 23 && it.emprestadoPor.isEmpty() }
            .filter { missao.posicao.isEmpty() || it.posicao == missao.posicao }
            .filter { it.geral() <= teto && it.potencial >= it.geral() + 4 }
            .sortedWith(compareByDescending<pt.futebolcarreira.nucleo.modelos.Jogador> { it.potencial }.thenBy { it.id })
            .take(2 + m.nivel).toList()
        val o = c.olheiros.estado
        o.observados += achados.map { it.id }
        o.relatorio = (achados.map { it.id } + o.relatorio).distinct().take(12)
        val nomePais = c.bd.paises[missao.pais]?.nome ?: missao.pais
        c.mensagem(
            "Relatório de ${m.nome}: $nomePais",
            if (achados.isEmpty()) "${m.nome} não encontrou nenhum jovem que valha a pena."
            else "Jovens a seguir: " + achados.joinToString("; ") { "${it.nome} (${it.posicao}, ${it.idade} anos, ${c.bd.equipas[it.equipa]?.nome}): geral ${it.geral()}, potencial ${it.potencial}" } +
                ". Vê as fichas em Mais › Clube › Olheiros.",
        )
        c.baseDadosPorGravar = true
    }

    companion object {
        /** Salário anual de cada nível para um clube de reputação média. */
        val SALARIOS = listOf(25_000, 70_000, 160_000, 360_000, 800_000)

        /** Clubes maiores pagam mais à equipa técnica (e os pequenos menos): 1,0 para reputação 50. */
        fun escala(reputacao: Int): Double =
            1.1.pow((GeradorJogadores.nivelDaReputacao(reputacao) - 59.0) / 1.0).coerceIn(0.35, 6.0)

        private val NOMES = listOf("Rui", "Paulo", "Nuno", "Hugo", "Bruno", "Tiago", "Sérgio", "Luís", "Fábio", "Ricardo", "Marco", "Vítor", "Daniel", "Emílio", "Jorge", "Pedro", "Carlos", "Miguel", "André", "João")
        private val APELIDOS = listOf("Almeida", "Carvalho", "Teixeira", "Monteiro", "Figueiredo", "Rebelo", "Seabra", "Tavares", "Cardoso", "Lacerda", "Pimentel", "Quintela", "Domingues", "Barata", "Nogueira", "Esteves", "Valente", "Salgado", "Matos", "Brandão")
    }
}
