package pt.futebolcarreira.nucleo.modelos

/**
 * Fase 13: o que a equipa técnica, o plano semanal e a carga dos jogadores mudam no treino e nas lesões de um clube.
 * Todos os valores neutros (1,0 e 0,0) deixam o treino exatamente como era; só o clube do utilizador os altera
 * (ver carreira/GestaoClube.kt). Não é gravado: volta a ser calculado quando a carreira abre.
 */
class AjustesTreino {
    /** Multiplica a probabilidade de os jovens evoluírem em cada mês. */
    var crescimento = 1.0

    /** Multiplicador por atributo (ausente = 1,0), por exemplo vindo do plano semanal. */
    var pesoAtributo: Map<String, Double> = emptyMap()

    /** Multiplica o risco de lesão no treino. */
    var riscoLesao = 1.0

    /** Probabilidade extra, por jogo, de encurtar uma paragem por lesão (médicos e fisioterapeutas). */
    var recuperacao = 0.0

    /** Multiplica a probabilidade de os veteranos perderem qualidades (preparador físico). */
    var declinio = 1.0

    /** Multiplicador do risco de lesão de cada jogador (a fadiga acumulada aumenta-o). */
    var riscoJogador: (Jogador) -> Double = { 1.0 }

    fun peso(atributo: String): Double = pesoAtributo[atributo] ?: 1.0
}
