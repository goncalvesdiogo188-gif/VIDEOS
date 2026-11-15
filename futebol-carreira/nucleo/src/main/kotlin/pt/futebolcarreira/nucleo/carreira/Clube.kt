package pt.futebolcarreira.nucleo.carreira

import kotlinx.serialization.Serializable
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.simulacao.EvolucaoPlantel
import java.time.LocalDate

/** As instalações do clube que se podem melhorar. */
enum class Instalacao(val nome: String, val efeito: String) {
    TREINO("Centro de treinos", "Os jogadores do plantel evoluem mais depressa."),
    ACADEMIA("Academia", "Entram mais jovens, com mais qualidade e potencial, e evoluem mais depressa."),
    MEDICO("Departamento médico", "Menos lesões no treino e paragens mais curtas."),
}

/**
 * Academia e instalações do clube do utilizador: promover ou dispensar jovens
 * e melhorar o centro de treinos, a academia e o departamento médico.
 */
class Academia(private val c: Carreira) {
    private val bd get() = c.bd
    private val meu get() = c.equipa
    private val podeGerir get() = c.info.modo == ModoCarreira.TREINADOR && !c.info.despedido

    /** Se a academia do clube estiver vazia, entra uma geração nova de jovens. */
    fun garantirJovens() {
        if (meu.academia.isNotEmpty()) return
        EvolucaoPlantel.entradaNaAcademia(bd, c.gestor.gerador, meu, c.gestor.aleatorio, c.gestor.ano)
        c.baseDadosPorGravar = true
    }

    /** No início de cada época entra uma geração nova. Devolve os jovens que entraram. */
    fun novaGeracao(): List<Jogador> {
        val novos = EvolucaoPlantel.entradaNaAcademia(bd, c.gestor.gerador, meu, c.gestor.aleatorio, c.gestor.ano)
        c.baseDadosPorGravar = true
        return novos
    }

    /** Um jovem sobe ao plantel principal, com contrato de três anos. */
    fun promover(j: Jogador): Resposta {
        if (!podeGerir || j.equipa != meu.id || !j.academia) return Resposta.NAO_PERMITIDO
        if (meu.plantel.size >= MAX_PLANTEL) return Resposta.PLANTEL_CHEIO
        bd.promover(j)
        j.contratoAte = c.gestor.ano + 1 + 3
        j.salario = j.salarioJusto()
        c.baseDadosPorGravar = true
        c.mensagem("Academia: ${j.nome} sobe ao plantel", "${j.nome} (${j.posicao}, ${j.idade} anos, geral ${j.geral()}) assinou contrato profissional até ${j.contratoAte}.")
        return Resposta.ACEITE
    }

    /** Um jovem deixa a academia e fica sem clube. */
    fun dispensar(j: Jogador): Resposta {
        if (!podeGerir || j.equipa != meu.id || !j.academia) return Resposta.NAO_PERMITIDO
        bd.mudarDeEquipa(j, "")
        j.contratoAte = c.gestor.ano + 2
        c.baseDadosPorGravar = true
        return Resposta.ACEITE
    }

    fun nivel(i: Instalacao, equipa: Equipa = meu): Int = when (i) {
        Instalacao.TREINO -> equipa.nivelTreino
        Instalacao.ACADEMIA -> equipa.nivelAcademia
        Instalacao.MEDICO -> equipa.nivelMedico
    }

    /** Quanto custa subir um nível (null se já está no máximo). */
    fun custoMelhoria(i: Instalacao): Long? = CUSTOS.getOrNull(nivel(i))

    fun melhorar(i: Instalacao): Resposta {
        if (!podeGerir) return Resposta.NAO_PERMITIDO
        val custo = custoMelhoria(i) ?: return Resposta.NIVEL_MAXIMO
        if (custo > meu.orcamento) return Resposta.SEM_DINHEIRO
        meu.orcamento -= custo
        meu.contas.obras += custo
        when (i) {
            Instalacao.TREINO -> meu.nivelTreino++
            Instalacao.ACADEMIA -> meu.nivelAcademia++
            Instalacao.MEDICO -> meu.nivelMedico++
        }
        c.baseDadosPorGravar = true
        c.mensagem("Obras: ${i.nome.lowercase()}", "${i.nome} passou para o nível ${nivel(i)}. Custo: ${Mercado.euros(custo)}.")
        return Resposta.ACEITE
    }

    companion object {
        /** Custo de passar do nível 1 para o 2, do 2 para o 3, etc. (índice = nível atual). */
        private val CUSTOS = listOf(null, 500_000L, 1_500_000L, 4_000_000L, 10_000_000L)
    }
}

/** Uma viagem do olheiro: onde foi, que posição procura ("" = todas) e em que dia volta. */
@Serializable
class MissaoOlheiro(val pais: String, val posicao: String = "", val regressa: String)

/** O que fica gravado sobre os olheiros. */
@Serializable
class EstadoOlheiros(
    var missao: MissaoOlheiro? = null,
    /** Jogadores de que o clube conhece o potencial exato. */
    val observados: MutableSet<Int> = mutableSetOf(),
    /** Jogadores recomendados no último relatório. */
    var relatorio: List<Int> = emptyList(),
)

/**
 * Olheiros: o clube só conhece ao certo o potencial dos seus jogadores e dos
 * jogadores já feitos. Para descobrir jovens promessas manda-se o olheiro a
 * um país; passadas quatro semanas ele volta com um relatório.
 */
class Olheiros(private val c: Carreira, val estado: EstadoOlheiros = EstadoOlheiros()) {
    private val bd get() = c.bd
    private val meu get() = c.equipa

    /** Uma viagem no próprio país fica mais barata. */
    fun custo(pais: String): Long = if (pais == meu.pais) 30_000L else 80_000L

    /** O clube sabe ao certo o potencial deste jogador? */
    fun conhece(j: Jogador): Boolean = j.equipa == meu.id || j.idade >= 27 || j.id in estado.observados

    /** Potencial para mostrar: o valor exato, se for conhecido; senão um intervalo estimado. */
    fun potencial(j: Jogador): String {
        if (conhece(j)) return "${j.potencial}"
        // O erro da estimativa é sempre o mesmo para o mesmo jogador.
        val centro = j.potencial + Math.floorMod(j.id * 31, 9) - 4
        val minimo = maxOf(j.geral(), centro - 4)
        return "$minimo–${minOf(99, maxOf(minimo + 4, centro + 4))}"
    }

    fun enviar(pais: String, posicao: String = ""): Resposta {
        if (c.info.modo != ModoCarreira.TREINADOR || c.info.despedido || pais !in bd.paises) return Resposta.NAO_PERMITIDO
        if (estado.missao != null) return Resposta.OLHEIRO_OCUPADO
        val custo = custo(pais)
        if (custo > meu.orcamento) return Resposta.SEM_DINHEIRO
        meu.orcamento -= custo
        meu.contas.olheiros += custo
        estado.missao = MissaoOlheiro(pais, posicao, c.gestor.data.plusDays(DIAS_DE_VIAGEM).toString())
        return Resposta.ACEITE
    }

    /** Se o olheiro já voltou, escreve o relatório (chamado sempre que o tempo avança). */
    fun verificar() {
        val missao = estado.missao ?: return
        if (c.gestor.data < LocalDate.parse(missao.regressa)) return
        estado.missao = null
        // Jovens com futuro, que joguem num clube de onde aceitem sair e que não estejam fora do alcance.
        val teto = meu.forca().geral + 4
        val achados = bd.equipas.values.asSequence()
            .filter { it.pais == missao.pais && it.id != meu.id && it.reputacao <= meu.reputacao + 8 }
            .flatMap { it.plantel }
            .filter { it.idade <= 23 && it.emprestadoPor.isEmpty() && it.id !in estado.observados }
            .filter { missao.posicao.isEmpty() || it.posicao == missao.posicao }
            .filter { it.geral() <= teto && it.potencial >= it.geral() + 4 }
            .sortedWith(compareByDescending<Jogador> { it.potencial }.thenBy { it.id })
            .take(JOGADORES_POR_RELATORIO).toList()
        estado.observados += achados.map { it.id }
        estado.relatorio = achados.map { it.id }
        val nomePais = bd.paises[missao.pais]?.nome ?: missao.pais
        c.mensagem(
            "Relatório do olheiro: $nomePais",
            if (achados.isEmpty()) "O olheiro não encontrou nenhum jovem que valha a pena."
            else "Jovens a seguir: " + achados.joinToString("; ") {
                "${it.nome} (${it.posicao}, ${it.idade} anos, ${bd.equipas[it.equipa]?.nome}): geral ${it.geral()}, potencial ${it.potencial}"
            } + ". Vê as fichas em Mais › Olheiros.",
        )
    }

    companion object {
        const val DIAS_DE_VIAGEM = 28L
        const val JOGADORES_POR_RELATORIO = 5
    }
}
