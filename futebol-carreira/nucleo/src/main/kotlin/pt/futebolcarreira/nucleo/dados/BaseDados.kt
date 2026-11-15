package pt.futebolcarreira.nucleo.dados

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import pt.futebolcarreira.nucleo.modelos.Competicao
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Jogador
import pt.futebolcarreira.nucleo.modelos.ListaNomes
import pt.futebolcarreira.nucleo.modelos.Pais

/**
 * Carrega e guarda toda a base de dados do jogo a partir de ficheiros JSON.
 *
 * Ficheiros (dentro da pasta "dados"):
 *   paises.json            lista de países
 *   competicoes.json       ligas, taças e supertaças
 *   nomes.json             nomes próprios e apelidos por país (para gerar jogadores)
 *   equipas/<PAIS>.json    clubes de cada país
 *   jogadores/<PAIS>.json  jogadores dos clubes de cada país
 */
class BaseDados {
    val paises = LinkedHashMap<String, Pais>()
    val competicoes = LinkedHashMap<String, Competicao>()
    val equipas = LinkedHashMap<String, Equipa>()
    val jogadores = HashMap<Int, Jogador>()
    var nomes: Map<String, ListaNomes> = emptyMap()

    /** Problemas encontrados ao carregar (ficheiros em falta, erros de JSON...). */
    val erros = mutableListOf<String>()

    private var proximoId = 1

    /**
     * Lê todos os ficheiros. "ler" recebe um caminho (ex.: "equipas/POR.json") e devolve
     * o texto do ficheiro, ou null se não existir. Assim o mesmo código serve para o
     * telemóvel (pasta assets) e para os testes no computador.
     * Devolve true se correu tudo bem.
     */
    fun carregar(ler: (String) -> String?): Boolean {
        paises.clear(); competicoes.clear(); equipas.clear(); jogadores.clear(); erros.clear()
        proximoId = 1

        lerJson("paises.json", ler, ListSerializer(Pais.serializer()))?.forEach { paises[it.id] = it }
        lerJson("competicoes.json", ler, ListSerializer(Competicao.serializer()))?.forEach { competicoes[it.id] = it }
        nomes = lerJson("nomes.json", ler, MapSerializer(String.serializer(), ListaNomes.serializer())) ?: emptyMap()

        for (pais in paises.keys) {
            for (e in lerJson("equipas/$pais.json", ler, ListSerializer(Equipa.serializer())).orEmpty()) {
                if (e.pais.isEmpty()) e.pais = pais
                if (e.abreviatura.isEmpty()) e.abreviatura = e.nome.take(3).uppercase()
                if (e.id in equipas) {
                    erros += "Equipa repetida: ${e.id}"
                    continue
                }
                if (e.liga !in competicoes) erros += "A equipa ${e.id} está numa liga que não existe: '${e.liga}'"
                e.normalizar()
                equipas[e.id] = e
            }
        }
        for (pais in paises.keys) {
            for (j in lerJson("jogadores/$pais.json", ler, ListSerializer(Jogador.serializer()), obrigatorio = false).orEmpty()) {
                j.normalizar()
                if (j.id <= 0 || j.id in jogadores) j.id = 0  // id em falta ou repetido: recebe um novo
                adicionarJogador(j)
            }
        }
        // Jogadores sem clube (opcional).
        for (j in lerJson("jogadores/livres.json", ler, ListSerializer(Jogador.serializer()), obrigatorio = false).orEmpty()) {
            j.normalizar()
            j.equipa = ""
            if (j.id <= 0 || j.id in jogadores) j.id = 0
            adicionarJogador(j)
        }
        return erros.isEmpty()
    }

    /** Jogadores sem clube. */
    fun livres(): List<Jogador> = jogadores.values.filter { it.equipa.isEmpty() }

    /** Muda um jogador de clube ("" = fica sem clube). Quem estava numa academia deixa de estar. */
    fun mudarDeEquipa(j: Jogador, destino: String) {
        equipas[j.equipa]?.let {
            it.plantel.remove(j)
            it.academia.remove(j)
            it.invalidarForca()
        }
        j.academia = false
        j.equipa = destino
        equipas[destino]?.let {
            it.plantel += j
            it.invalidarForca()
        }
    }

    /** Regista um jogador e coloca-o no plantel da sua equipa. */
    fun adicionarJogador(j: Jogador) {
        if (j.id <= 0) j.id = proximoId
        proximoId = maxOf(proximoId, j.id + 1)
        // Contrato em falta: salário de acordo com a qualidade e duração de 1 a 4 anos.
        if (j.salario <= 0) j.salario = j.salarioJusto()
        if (j.contratoAte <= 0) j.contratoAte = 2027 + j.id % 4
        jogadores[j.id] = j
        val equipa = equipas[j.equipa]
        if (equipa != null && j.academia) {
            equipa.academia += j
        } else if (equipa != null) {
            equipa.plantel += j
            equipa.invalidarForca()
        } else {
            if (j.equipa.isNotEmpty()) erros += "O jogador ${j.nome} (${j.id}) está numa equipa que não existe: '${j.equipa}'"
            j.equipa = ""
            j.academia = false
        }
    }

    /** Um jovem da academia sobe ao plantel principal. */
    fun promover(j: Jogador) {
        val equipa = equipas[j.equipa] ?: return
        if (!equipa.academia.remove(j)) return
        j.academia = false
        equipa.plantel += j
        equipa.invalidarForca()
    }

    /** Remove um jogador do jogo (por exemplo, quando se reforma). */
    fun removerJogador(j: Jogador) {
        jogadores.remove(j.id)
        equipas[j.equipa]?.let {
            it.plantel.remove(j)
            it.academia.remove(j)
            it.invalidarForca()
        }
    }

    /** Equipas que jogam numa liga. */
    fun equipasDaLiga(ligaId: String): List<Equipa> = equipas.values.filter { it.liga == ligaId }

    /** Ligas de um país, ordenadas por nível (1.ª divisão primeiro). */
    fun ligasDoPais(pais: String): List<Competicao> =
        competicoes.values.filter { it.pais == pais && it.tipo == "liga" }.sortedBy { it.nivel }

    /** Taças e supertaças de um país. */
    fun tacasDoPais(pais: String): List<Competicao> =
        competicoes.values.filter { it.pais == pais && (it.eTaca || it.eSupertaca) }

    /**
     * Acrescenta as taças e supertaças habituais aos países que não as têm
     * (gravações antigas, de antes da Fase 7, só tinham ligas).
     */
    fun garantirTacas() {
        for (pais in paises.keys) {
            val (taca, supertaca) = NOMES_TACAS[pais] ?: continue
            val liga = ligasDoPais(pais).firstOrNull() ?: continue
            if (competicoes.values.none { it.pais == pais && it.eTaca }) {
                competicoes["${pais}_TACA"] = Competicao("${pais}_TACA", taca, pais, nivel = 0, tipo = "taca")
            }
            if (competicoes.values.none { it.pais == pais && it.eSupertaca }) {
                val ref = competicoes.values.first { it.pais == pais && it.eTaca }.id
                competicoes["${pais}_SUPER"] = Competicao("${pais}_SUPER", supertaca, pais, nivel = 0, tipo = "supertaca", ligaRef = liga.id, tacaRef = ref)
            }
        }
    }

    /**
     * Converte equipas e jogadores em texto JSON, por ficheiro (caminho -> conteúdo).
     * Usado pelo gerador e, mais tarde, pelo editor.
     */
    fun paraJson(): Map<String, String> {
        val ficheiros = LinkedHashMap<String, String>()
        for (pais in paises.keys) {
            val doPais = equipas.values.filter { it.pais == pais }
            ficheiros["equipas/$pais.json"] = JSON_BONITO.encodeToString(ListSerializer(Equipa.serializer()), doPais) + "\n"
            // Um jogador por linha: fica fácil de ler e de editar.
            ficheiros["jogadores/$pais.json"] = doPais.flatMap { it.plantel + it.academia }
                .joinToString(",\n", "[\n", "\n]\n") { "\t" + JSON.encodeToString(Jogador.serializer(), it) }
        }
        val livres = livres().sortedBy { it.id }
        if (livres.isNotEmpty()) {
            ficheiros["jogadores/livres.json"] = livres.joinToString(",\n", "[\n", "\n]\n") { "\t" + JSON.encodeToString(Jogador.serializer(), it) }
        }
        return ficheiros
    }

    /** Como "paraJson", mas com todos os ficheiros (países, competições e nomes): serve para gravar uma carreira. */
    fun paraJsonCompleto(): Map<String, String> {
        val ficheiros = LinkedHashMap<String, String>()
        ficheiros["paises.json"] = JSON.encodeToString(ListSerializer(Pais.serializer()), paises.values.toList())
        ficheiros["competicoes.json"] = JSON.encodeToString(ListSerializer(Competicao.serializer()), competicoes.values.toList())
        ficheiros["nomes.json"] = JSON.encodeToString(MapSerializer(String.serializer(), ListaNomes.serializer()), nomes)
        ficheiros += paraJson()
        return ficheiros
    }

    private fun <T> lerJson(
        caminho: String,
        ler: (String) -> String?,
        formato: kotlinx.serialization.KSerializer<T>,
        obrigatorio: Boolean = true,
    ): T? {
        val texto = ler(caminho)
        if (texto == null) {
            if (obrigatorio) erros += "Ficheiro em falta: $caminho"
            return null
        }
        return try {
            JSON.decodeFromString(formato, texto)
        } catch (e: Exception) {
            erros += "Erro de JSON em $caminho: ${e.message?.lineSequence()?.firstOrNull()}"
            null
        }
    }

    companion object {
        private val NOMES_TACAS = mapOf(
            "POR" to ("Taça de Portugal" to "Supertaça Cândido de Oliveira"),
            "ESP" to ("Copa del Rey" to "Supercopa de España"),
            "ING" to ("FA Cup" to "FA Community Shield"),
            "ITA" to ("Coppa Italia" to "Supercoppa Italiana"),
            "ALE" to ("DFB-Pokal" to "DFL-Supercup"),
            "FRA" to ("Coupe de France" to "Trophée des Champions"),
        )

        /** Leitor de JSON tolerante: ignora campos desconhecidos e aceita campos em falta. */
        val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            coerceInputValues = true
        }
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        val JSON_BONITO = Json(JSON) {
            prettyPrint = true
            prettyPrintIndent = "\t"
        }
    }
}
