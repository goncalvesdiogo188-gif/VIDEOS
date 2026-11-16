package pt.futebolcarreira.nucleo.dados

import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Jogador

/**
 * Carrega a base de dados lendo primeiro os ficheiros editados ("substituicoes") e, se faltarem,
 * os originais ("originais", normalmente a pasta assets/dados da aplicação).
 */
fun BaseDados.carregarComSubstituicoes(substituicoes: (String) -> String?, originais: (String) -> String?): Boolean =
    carregar { substituicoes(it) ?: originais(it) }

/**
 * Alterações feitas à mão numa base de dados (a base nova ou a de uma carreira), com as mesmas regras da importação.
 * Cada função devolve null se correu bem, ou o texto do erro (e nesse caso não muda nada).
 */
class EditorDados(val bd: BaseDados) {

    /** Pode sair um jogador deste clube sem ficar com menos de 18 ou com menos de 2 guarda-redes? */
    private fun erroAoSair(j: Jogador): String? {
        val e = bd.equipas[j.equipa] ?: return null
        if (j.academia || j !in e.plantel) return null
        if (e.plantel.size - 1 < Validador.MIN_JOGADORES) return "${e.nome} ficava com menos de ${Validador.MIN_JOGADORES} jogadores."
        if (j.posicao == "GR" && e.plantel.count { it.posicao == "GR" } - 1 < Validador.MIN_GUARDA_REDES) return "${e.nome} ficava com menos de ${Validador.MIN_GUARDA_REDES} guarda-redes."
        return null
    }

    /** Muda os dados de um clube (não muda a liga nem o país; para a liga, ver [trocarLiga]). */
    fun editarClube(
        id: String, nome: String, abreviatura: String, cidade: String, estadio: String,
        capacidade: Int, reputacao: Int, orcamento: Long, cores: List<String>,
    ): String? {
        val e = bd.equipas[id] ?: return "O clube não existe."
        val prova = Equipa(id, nome.trim(), abreviatura.trim().uppercase(), e.pais, e.liga, cidade.trim(), estadio.trim(), capacidade, reputacao, orcamento, cores)
        val erros = mutableListOf<String>()
        Validador.verificarEquipa(prova, setOf(e.liga), erros, mutableListOf())
        if (prova.abreviatura.isEmpty()) erros += "A abreviatura não pode estar vazia."
        if (prova.abreviatura.length > 5) erros += "A abreviatura tem no máximo 5 letras."
        if (erros.isNotEmpty()) return erros.first()
        e.nome = prova.nome; e.abreviatura = prova.abreviatura; e.cidade = prova.cidade; e.estadio = prova.estadio
        e.capacidade = capacidade; e.reputacao = reputacao; e.orcamento = orcamento; e.cores = cores
        return null
    }

    /** Dois clubes do mesmo país trocam de liga, para o tamanho das ligas não mudar. */
    fun trocarLiga(a: String, b: String): String? {
        val x = bd.equipas[a] ?: return "O clube não existe."
        val y = bd.equipas[b] ?: return "O outro clube não existe."
        if (x.pais != y.pais) return "Os clubes têm de ser do mesmo país."
        if (x.liga == y.liga) return "Os clubes já estão na mesma liga."
        val liga = x.liga
        x.liga = y.liga
        y.liga = liga
        return null
    }

    /** Valida os campos de um jogador (novo ou alterado). */
    private fun erroDoJogador(j: Jogador): String? {
        val erros = mutableListOf<String>()
        Validador.verificarJogador(j, erros, mutableListOf())
        if (j.potencial <= 0) erros += "O potencial tem de estar entre 1 e 99."
        if (erros.isEmpty() && j.potencial < j.geral()) erros += "O potencial (${j.potencial}) não pode ser menor do que o geral (${j.geral()})."
        return erros.firstOrNull()
    }

    /** Muda os dados de um jogador que já existe. "atributos" substitui os atuais. */
    fun editarJogador(
        id: Int, nome: String, nacionalidade: String, idade: Int, posicao: String, pe: String,
        atributos: Map<String, Int>, potencial: Int, contratoAte: Int, salario: Int,
    ): String? {
        val j = bd.jogadores[id] ?: return "O jogador não existe."
        val prova = Jogador(id, nome.trim(), nacionalidade, idade, posicao, pe, j.equipa, potencial, atributos.toMutableMap(), salario, contratoAte)
        erroDoJogador(prova)?.let { return it }
        if (j.posicao == "GR" && posicao != "GR") {
            val e = bd.equipas[j.equipa]
            if (e != null && !j.academia && e.plantel.count { it.posicao == "GR" } - 1 < Validador.MIN_GUARDA_REDES) {
                return "${e.nome} ficava com menos de ${Validador.MIN_GUARDA_REDES} guarda-redes."
            }
        }
        j.nome = prova.nome; j.nacionalidade = nacionalidade; j.idade = idade; j.posicao = posicao; j.pe = pe
        j.atributos.clear(); j.atributos.putAll(prova.atributos)
        j.potencial = potencial; j.contratoAte = contratoAte; j.salario = salario
        bd.equipas[j.equipa]?.invalidarForca()
        return null
    }

    /** Acrescenta um jogador novo ao clube indicado em "equipa" (recebe um id novo). Devolve o erro, se houver. */
    fun criarJogador(j: Jogador): String? {
        if (bd.equipas[j.equipa] == null) return "Escolhe o clube do jogador."
        erroDoJogador(j)?.let { return it }
        j.id = 0
        j.academia = false
        j.normalizar()
        bd.adicionarJogador(j)
        return null
    }

    /** Muda um jogador de clube (o clube de onde sai tem de ficar com 18 jogadores e 2 guarda-redes). */
    fun moverJogador(id: Int, destino: String): String? {
        val j = bd.jogadores[id] ?: return "O jogador não existe."
        if (bd.equipas[destino] == null) return "O clube de destino não existe."
        if (j.equipa == destino) return null
        erroAoSair(j)?.let { return it }
        bd.mudarDeEquipa(j, destino)
        return null
    }

    /** Apaga um jogador (o clube tem de ficar com 18 jogadores e 2 guarda-redes). */
    fun apagarJogador(id: Int): String? {
        val j = bd.jogadores[id] ?: return "O jogador não existe."
        erroAoSair(j)?.let { return it }
        bd.removerJogador(j)
        return null
    }

    /** Aplica um clube importado e validado ([Validador.validarClube]): os dados do clube e o plantel passam a ser os importados. */
    fun aplicarClube(importado: Equipa, jogadores: List<Jogador>) {
        val e = bd.equipas.getValue(importado.id)
        e.nome = importado.nome; e.abreviatura = importado.abreviatura.ifEmpty { importado.nome.take(3).uppercase() }
        e.cidade = importado.cidade; e.estadio = importado.estadio; e.capacidade = importado.capacidade
        e.reputacao = importado.reputacao; e.orcamento = importado.orcamento; e.cores = importado.cores
        e.nivelTreino = importado.nivelTreino; e.nivelAcademia = importado.nivelAcademia; e.nivelMedico = importado.nivelMedico
        e.normalizar()
        for (antigo in (e.plantel + e.academia).toList()) bd.removerJogador(antigo)
        for (j in jogadores) {
            j.normalizar()
            j.equipa = e.id
            bd.adicionarJogador(j)
        }
        e.invalidarForca()
    }
}
