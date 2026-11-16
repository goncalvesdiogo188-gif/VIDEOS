package pt.futebolcarreira.nucleo.dados

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pt.futebolcarreira.nucleo.modelos.Competicao
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.modelos.ListaNomes
import pt.futebolcarreira.nucleo.modelos.Pais

/** O que um ficheiro importado contém. */
enum class TipoImportacao { DESCONHECIDO, BASE, CLUBE }

/**
 * Resultado de validar um ficheiro importado. Os erros bloqueiam a importação; os avisos não.
 * Se não há erros, "ficheiros" (base completa ou de um país) ou "equipa" e "jogadores" (um clube) trazem
 * os dados já corrigidos (ids repetidos trocados por novos), prontos a aplicar.
 */
class RelatorioImportacao(
    val tipo: TipoImportacao,
    val erros: List<String>,
    val avisos: List<String>,
    val resumo: String,
    val ficheiros: Map<String, String>? = null,
    val equipa: Equipa? = null,
    val jogadores: List<Jogador> = emptyList(),
) {
    val ok get() = erros.isEmpty()
}

/** Um clube exportado: os dados do clube e todos os seus jogadores. */
@Serializable
class ClubeExportado(val equipa: Equipa, val jogadores: List<Jogador>)

/**
 * Valida dados importados (base de dados completa, de um país, ou de um clube) antes de os aplicar.
 * Tudo em texto português, para ser mostrado ao utilizador tal e qual.
 */
object Validador {
    const val MIN_JOGADORES = 18
    const val MIN_GUARDA_REDES = 2
    private const val MAX_MENSAGENS = 40
    private val HEX = Regex("^#[0-9a-fA-F]{6}$")
    private val CAMINHO = Regex("^(equipas|jogadores)/[A-Za-z0-9_]+\\.json$")
    private val FIXOS = setOf("paises.json", "competicoes.json", "nomes.json", "selecoes.json", "rivalidades.json")
    private val PES = setOf("direito", "esquerdo", "ambos")
    private val JSON = BaseDados.JSON

    // ------------------------------------------------------------------ exportar

    /** A base de dados inteira como um único ficheiro JSON: caminho -> conteúdo. */
    fun exportarBase(bd: BaseDados): String = textoDeMapa(bd.paraJsonCompleto())

    /** Os clubes e jogadores de um país, no mesmo formato. */
    fun exportarPais(bd: BaseDados, pais: String): String =
        textoDeMapa(bd.paraJson().filterKeys { it == "equipas/$pais.json" || it == "jogadores/$pais.json" })

    /** Um clube e os seus jogadores: {"equipa": ..., "jogadores": [...]}. */
    fun exportarClube(bd: BaseDados, equipaId: String): String {
        val e = requireNotNull(bd.equipas[equipaId]) { "Clube desconhecido: $equipaId" }
        return BaseDados.JSON_BONITO.encodeToString(ClubeExportado.serializer(), ClubeExportado(e, e.plantel + e.academia))
    }

    private fun textoDeMapa(mapa: Map<String, String>) =
        BaseDados.JSON_BONITO.encodeToString(MapSerializer(String.serializer(), String.serializer()), mapa)

    // ------------------------------------------------------------------ importar

    /** Valida o texto de um ficheiro importado em relação à base de dados "atual". */
    fun validarTexto(texto: String, atual: BaseDados): RelatorioImportacao {
        val raiz = try {
            JSON.parseToJsonElement(texto)
        } catch (e: Exception) {
            return falha("O ficheiro não é JSON válido: ${primeiraLinha(e)}")
        }
        if (raiz !is JsonObject) return falha("O ficheiro tem de ser um objeto JSON (um mapa de ficheiros, ou {\"equipa\": ..., \"jogadores\": ...}).")
        if ("equipa" in raiz && "jogadores" in raiz) return validarClube(texto, atual)
        val mapa = LinkedHashMap<String, String>()
        for ((chave, valor) in raiz) {
            mapa[chave] = when {
                valor is JsonPrimitive && valor.isString -> valor.content
                valor is JsonArray || valor is JsonObject -> valor.toString()
                else -> return falha("O valor de \"$chave\" devia ser o conteúdo do ficheiro (texto).")
            }
        }
        if (mapa.isEmpty()) return falha("O ficheiro está vazio.")
        return validarMapa(mapa, atual)
    }

    private fun falha(msg: String) = RelatorioImportacao(TipoImportacao.DESCONHECIDO, listOf(msg), emptyList(), "Importação bloqueada.")

    private fun primeiraLinha(e: Exception) = e.message?.lineSequence()?.firstOrNull() ?: e.javaClass.simpleName

    /** Mensagens recolhidas, com limite para o relatório não ficar enorme. */
    private class Msgs {
        val erros = mutableListOf<String>()
        val avisos = mutableListOf<String>()
    }

    private fun limitar(lista: List<String>) =
        if (lista.size <= MAX_MENSAGENS) lista else lista.take(MAX_MENSAGENS) + "… e mais ${lista.size - MAX_MENSAGENS} problemas semelhantes."

    /** Valida uma base de dados completa, ou parte dela (ficheiros de países): caminho -> conteúdo. */
    fun validarMapa(mapa: Map<String, String>, atual: BaseDados): RelatorioImportacao {
        val m = Msgs()
        val fusao = LinkedHashMap(atual.paraJsonCompleto())
        for ((caminho, texto) in mapa) {
            if (caminho in FIXOS || CAMINHO.matches(caminho)) fusao[caminho] = texto
            else m.avisos += "Ficheiro desconhecido, ignorado: $caminho"
        }

        // 1. Sintaxe de todos os ficheiros.
        fun <T> ler(caminho: String, formato: kotlinx.serialization.KSerializer<T>): T? {
            val texto = fusao[caminho] ?: return null
            return try {
                JSON.decodeFromString(formato, texto)
            } catch (e: Exception) {
                m.erros += "Erro de JSON em $caminho: ${primeiraLinha(e)}"
                null
            }
        }
        val paises = ler("paises.json", ListSerializer(Pais.serializer()))
        val competicoes = ler("competicoes.json", ListSerializer(Competicao.serializer()))
        ler("nomes.json", MapSerializer(String.serializer(), ListaNomes.serializer()))
        for (extra in listOf("selecoes.json", "rivalidades.json")) {
            val t = fusao[extra] ?: continue
            try { JSON.parseToJsonElement(t) } catch (e: Exception) { m.erros += "Erro de JSON em $extra: ${primeiraLinha(e)}" }
        }
        if (paises == null || competicoes == null) {
            if (paises == null && m.erros.none { it.contains("paises.json") }) m.erros += "Falta o ficheiro paises.json."
            if (competicoes == null && m.erros.none { it.contains("competicoes.json") }) m.erros += "Falta o ficheiro competicoes.json."
            return RelatorioImportacao(TipoImportacao.BASE, limitar(m.erros), m.avisos, "Importação bloqueada.")
        }
        val idsPaises = paises.map { it.id }
        val padraoPais = Regex("^(equipas|jogadores)/(.+)\\.json$")
        for (caminho in fusao.keys) {
            val pais = padraoPais.matchEntire(caminho)?.groupValues?.get(2) ?: continue
            if (pais != "livres" && pais !in idsPaises) m.avisos += "O ficheiro $caminho é de um país que não existe em paises.json: ignorado."
        }
        val equipas = mutableListOf<Equipa>()
        val jogadoresPorFicheiro = LinkedHashMap<String, MutableList<Jogador>>()
        for (pais in idsPaises) {
            ler("equipas/$pais.json", ListSerializer(Equipa.serializer()))?.let { l -> l.forEach { if (it.pais.isEmpty()) it.pais = pais }; equipas += l }
            ler("jogadores/$pais.json", ListSerializer(Jogador.serializer()))?.let { jogadoresPorFicheiro["jogadores/$pais.json"] = it.toMutableList() }
        }
        ler("jogadores/livres.json", ListSerializer(Jogador.serializer()))?.let { jogadoresPorFicheiro["jogadores/livres.json"] = it.toMutableList() }
        if (m.erros.isNotEmpty()) return RelatorioImportacao(TipoImportacao.BASE, limitar(m.erros), m.avisos, "Importação bloqueada.")

        // 2. Clubes.
        val ligas = competicoes.filter { it.eLiga }.map { it.id }.toSet()
        val idsEquipas = HashSet<String>()
        for (e in equipas) {
            if (!idsEquipas.add(e.id)) m.erros += "O clube ${e.id} aparece repetido."
            verificarEquipa(e, ligas, m.erros, m.avisos)
        }
        // 3. Tamanho das ligas.
        for (liga in atual.competicoes.values.filter { it.eLiga }) {
            val esperado = atual.equipasDaLiga(liga.id).size
            val encontrado = equipas.count { it.liga == liga.id }
            if (encontrado != esperado) m.erros += "A liga ${liga.nome} (${liga.id}) tem $encontrado clubes e devia ter $esperado."
        }
        // 4. Jogadores: ids, campos e clube.
        val todos = jogadoresPorFicheiro.values.flatten()
        var maiorId = todos.maxOfOrNull { it.id } ?: 0
        val usados = HashSet<Int>()
        val alterados = HashSet<String>()
        for ((caminho, lista) in jogadoresPorFicheiro) {
            for (j in lista) {
                if (j.id > 0 && usados.add(j.id)) continue
                val antigo = j.id
                j.id = ++maiorId
                usados += j.id
                alterados += caminho
                m.avisos += if (antigo <= 0) "O jogador ${j.nome} não tinha id: recebeu o id ${j.id}."
                else "O jogador ${j.nome} tinha o id $antigo repetido: recebeu o id ${j.id}."
            }
        }
        val nacionalidades = nacionalidadesConhecidas(atual)
        val desconhecidas = LinkedHashMap<String, Int>()
        for (j in todos) {
            verificarJogador(j, m.erros, m.avisos)
            if (j.equipa.isNotEmpty() && j.equipa !in idsEquipas) m.erros += "O jogador ${j.nome} (${j.id}) está num clube que não existe: '${j.equipa}'."
            if (j.nacionalidade.isNotEmpty() && j.nacionalidade !in nacionalidades) desconhecidas.merge(j.nacionalidade, 1, Int::plus)
        }
        for ((codigo, n) in desconhecidas) m.avisos += "Nacionalidade desconhecida '$codigo' ($n jogadores): aceite assim mesmo."
        // 5. Plantéis.
        val porClube = todos.filter { !it.academia }.groupBy { it.equipa }
        for (e in equipas.filter { it.liga in ligas }) {
            verificarPlantel(e.nome, porClube[e.id].orEmpty(), m.erros)
        }
        if (m.erros.isNotEmpty()) return RelatorioImportacao(TipoImportacao.BASE, limitar(m.erros), limitar(m.avisos), "Importação bloqueada.")

        // 6. Aplicação de teste: carrega tudo e devolve os ficheiros já corrigidos.
        for (caminho in alterados) {
            fusao[caminho] = JSON.encodeToString(ListSerializer(Jogador.serializer()), jogadoresPorFicheiro.getValue(caminho))
        }
        val candidata = BaseDados()
        candidata.carregar { fusao[it] }
        if (candidata.erros.isNotEmpty()) {
            return RelatorioImportacao(TipoImportacao.BASE, candidata.erros.take(MAX_MENSAGENS), limitar(m.avisos), "Importação bloqueada.")
        }
        val resumo = "${candidata.equipas.size} clubes e ${candidata.jogadores.size} jogadores prontos a importar."
        return RelatorioImportacao(TipoImportacao.BASE, emptyList(), limitar(m.avisos), resumo, candidata.paraJsonCompleto())
    }

    /** Valida um clube importado: {"equipa": ..., "jogadores": [...]}. O clube tem de existir já na base "atual". */
    fun validarClube(texto: String, atual: BaseDados): RelatorioImportacao {
        val m = Msgs()
        val pacote = try {
            JSON.decodeFromString(ClubeExportado.serializer(), texto)
        } catch (e: Exception) {
            return RelatorioImportacao(TipoImportacao.CLUBE, listOf("Ficheiro de clube inválido: ${primeiraLinha(e)}"), emptyList(), "Importação bloqueada.")
        }
        val e = pacote.equipa
        val existente = atual.equipas[e.id]
        if (existente == null) {
            m.erros += "O clube '${e.id}' não existe na base de dados: só se podem importar clubes que já existem."
            return RelatorioImportacao(TipoImportacao.CLUBE, m.erros, emptyList(), "Importação bloqueada.")
        }
        if (e.pais.isEmpty()) e.pais = existente.pais
        if (e.liga.isEmpty()) e.liga = existente.liga
        if (e.pais != existente.pais) m.erros += "O clube ${e.nome} é do país ${e.pais} e devia ser de ${existente.pais}."
        if (e.liga != existente.liga) m.erros += "O clube ${e.nome} mudava da liga ${existente.liga} para ${e.liga}: isso alterava o tamanho das ligas."
        val ligas = atual.competicoes.values.filter { it.eLiga }.map { it.id }.toSet()
        verificarEquipa(e, ligas, m.erros, m.avisos)

        val jogadores = pacote.jogadores
        // Ids: os do próprio clube podem repetir-se (são substituídos); os de outros clubes não.
        val reservados = atual.jogadores.values.filter { it.equipa != e.id }.map { it.id }.toHashSet()
        var maiorId = maxOf(atual.jogadores.keys.maxOrNull() ?: 0, jogadores.maxOfOrNull { it.id } ?: 0)
        val vistos = HashSet<Int>()
        for (j in jogadores) {
            if (j.id <= 0 || j.id in reservados || !vistos.add(j.id)) {
                val antigo = j.id
                j.id = ++maiorId
                vistos += j.id
                m.avisos += if (antigo <= 0) "O jogador ${j.nome} não tinha id: recebeu o id ${j.id}."
                else "O jogador ${j.nome} tinha o id $antigo repetido: recebeu o id ${j.id}."
            }
            if (j.equipa != e.id) {
                if (j.equipa.isNotEmpty()) m.avisos += "O jogador ${j.nome} estava no clube '${j.equipa}': foi posto em ${e.nome}."
                j.equipa = e.id
            }
        }
        val nacionalidades = nacionalidadesConhecidas(atual)
        val desconhecidas = LinkedHashMap<String, Int>()
        for (j in jogadores) {
            verificarJogador(j, m.erros, m.avisos)
            if (j.nacionalidade.isNotEmpty() && j.nacionalidade !in nacionalidades) desconhecidas.merge(j.nacionalidade, 1, Int::plus)
        }
        for ((codigo, n) in desconhecidas) m.avisos += "Nacionalidade desconhecida '$codigo' ($n jogadores): aceite assim mesmo."
        if (e.liga in ligas) verificarPlantel(e.nome, jogadores.filter { !it.academia }, m.erros)
        if (m.erros.isNotEmpty()) return RelatorioImportacao(TipoImportacao.CLUBE, limitar(m.erros), limitar(m.avisos), "Importação bloqueada.")
        return RelatorioImportacao(
            TipoImportacao.CLUBE, emptyList(), limitar(m.avisos),
            "Clube ${e.nome}: ${jogadores.size} jogadores prontos a importar (os atuais são substituídos).", equipa = e, jogadores = jogadores,
        )
    }

    // ------------------------------------------------------------------ regras

    private fun nacionalidadesConhecidas(bd: BaseDados): Set<String> =
        bd.jogadores.values.map { it.nacionalidade }.toHashSet() + bd.paises.keys + bd.nomes.keys

    /** Erros de um clube (campos obrigatórios e intervalos). */
    fun verificarEquipa(e: Equipa, ligas: Set<String>, erros: MutableList<String>, avisos: MutableList<String>) {
        val nome = e.nome.ifBlank { e.id }
        if (e.id.isBlank()) erros += "Há um clube sem id."
        if (e.nome.isBlank()) erros += "O clube ${e.id} não tem nome."
        if (e.liga !in ligas) erros += "O clube $nome está numa liga que não existe: '${e.liga}'."
        if (e.reputacao !in 1..99) erros += "O clube $nome tem reputação ${e.reputacao} (tem de estar entre 1 e 99)."
        if (e.capacidade < 1) erros += "O clube $nome tem capacidade ${e.capacidade} (tem de ser positiva)."
        if (e.orcamento < 0) erros += "O clube $nome tem orçamento negativo."
        if (e.cores.isEmpty() || e.cores.any { !HEX.matches(it) }) erros += "O clube $nome tem cores inválidas (use o formato #rrggbb)."
        for (n in listOf(e.nivelTreino, e.nivelAcademia, e.nivelMedico)) {
            if (n !in 0..5) { erros += "O clube $nome tem um nível de instalações fora de 0 a 5."; break }
        }
        if (e.abreviatura.isBlank()) avisos += "O clube $nome não tem abreviatura: vai usar as 3 primeiras letras."
    }

    /** Erros de um jogador (campos obrigatórios e intervalos), contados antes de qualquer correção automática. */
    fun verificarJogador(j: Jogador, erros: MutableList<String>, avisos: MutableList<String>) {
        val nome = "${j.nome} (${j.id})"
        val errosAntes = erros.size
        if (j.nome.isBlank()) erros += "Há um jogador sem nome (id ${j.id})."
        if (j.idade !in 14..50) erros += "O jogador $nome tem ${j.idade} anos (tem de ter entre 14 e 50)."
        if (j.posicao !in Jogador.POSICOES) erros += "O jogador $nome tem a posição '${j.posicao}' (válidas: ${Jogador.POSICOES.joinToString()})."
        if (j.pe !in PES) erros += "O jogador $nome tem o pé '${j.pe}' (válidos: direito, esquerdo, ambos)."
        for (a in Jogador.ATRIBUTOS) {
            val v = j.atributos[a]
            when {
                v == null -> avisos += "O jogador $nome não tem o atributo '$a': fica a 30."
                v !in 1..99 -> erros += "O jogador $nome tem '$a' = $v (tem de estar entre 1 e 99)."
            }
        }
        for (a in j.atributos.keys) if (a !in Jogador.ATRIBUTOS) avisos += "O jogador $nome tem o atributo desconhecido '$a': é ignorado."
        if (j.potencial !in 0..99) erros += "O jogador $nome tem potencial ${j.potencial} (tem de estar entre 1 e 99)."
        if (j.salario < 0) erros += "O jogador $nome tem salário negativo."
        if (j.contratoAte != 0 && j.contratoAte !in 2000..2100) erros += "O jogador $nome tem o contrato a acabar em ${j.contratoAte} (ano inválido)."
        if (erros.size == errosAntes && j.potencial in 1 until j.geral()) {
            avisos += "O jogador $nome tem potencial ${j.potencial} abaixo do geral ${j.geral()}: sobe para o geral."
        }
    }

    /** Um clube de liga precisa de pelo menos 18 jogadores e 2 guarda-redes. */
    fun verificarPlantel(clube: String, plantel: List<Jogador>, erros: MutableList<String>) {
        if (plantel.size < MIN_JOGADORES) erros += "O clube $clube tem só ${plantel.size} jogadores (o mínimo é $MIN_JOGADORES)."
        val gr = plantel.count { it.posicao == "GR" }
        if (gr < MIN_GUARDA_REDES) erros += "O clube $clube tem só $gr guarda-redes (o mínimo é $MIN_GUARDA_REDES)."
    }
}
