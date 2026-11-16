package pt.futebolcarreira

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.futebolcarreira.nucleo.carreira.Carreira
import pt.futebolcarreira.nucleo.dados.BaseDados
import pt.futebolcarreira.nucleo.dados.EditorDados
import pt.futebolcarreira.nucleo.dados.RelatorioImportacao
import pt.futebolcarreira.nucleo.dados.TipoImportacao
import pt.futebolcarreira.nucleo.dados.Validador
import pt.futebolcarreira.nucleo.dados.carregarComSubstituicoes
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.modelos.Jogador
import java.io.File

// Editor da base de dados: dois modos (a base que serve para carreiras novas, ou a carreira aberta),
// com listas, fichas de clube e de jogador e importação/exportação de JSON.

/** Que base de dados está a ser editada. */
enum class OrigemEditor { BASE, CARREIRA }

enum class SeparadorEditor(val nome: String) { CLUBES("Clubes"), JOGADORES("Jogadores"), FICHEIROS("Ficheiros") }

/** O que se exporta. */
enum class TipoExportacao { TUDO, PAIS, CLUBE }

/** Uma opção de uma lista de escolha. */
class OpcaoUi(val id: String, val texto: String)

class ClubeEdUi(val id: String, val nome: String, val sigla: String, val liga: String, val reputacao: Int, val jogadores: Int, val emblema: EmblemaUi)

/** Ficha de um clube em edição (os números ficam em texto enquanto se escreve). */
data class ClubeFichaUi(
    val id: String, val nome: String, val sigla: String, val cidade: String, val estadio: String,
    val capacidade: String, val reputacao: Int, val orcamento: String, val cor1: String, val cor2: String,
    val ligaNome: String, val pais: String,
    /** Clubes do mesmo país noutra liga, para trocar de liga. Vazio = não se pode trocar. */
    val trocas: List<OpcaoUi>,
)

class JogadorEdUi(val id: Int, val nome: String, val posicao: String, val idade: Int, val geral: Int, val potencial: Int, val clube: String)

/** Ficha de um jogador em edição; "novo" = ainda não existe. */
data class JogadorFichaUi(
    val id: Int, val novo: Boolean, val nome: String, val nacionalidade: String, val idade: Int, val posicao: String, val pe: String,
    val atributos: Map<String, Int>, val potencial: Int, val equipaId: String, val equipaNome: String, val contratoAte: Int, val salario: String,
)

/** O relatório de uma importação, para mostrar. */
class RelatorioUi(val resumo: String, val erros: List<String>, val avisos: List<String>) {
    val podeAplicar get() = erros.isEmpty()
}

/** Guarda a base de dados editada na memória privada da aplicação (mesmos ficheiros que assets/dados). */
class ArmazemBase(pastaDaApp: File) {
    private val pasta = File(pastaDaApp, "dados_editados")
    private val novaPasta = File(pastaDaApp, "dados_editados.novo")

    fun existe() = File(pasta, "paises.json").isFile

    /** Um ficheiro editado (null se não existir). */
    fun ler(caminho: String): String? {
        if (caminho.contains("..")) return null
        val f = File(pasta, caminho)
        return if (f.isFile) f.readText() else null
    }

    /** Substitui todos os ficheiros editados pelos novos. */
    fun gravar(ficheiros: Map<String, String>) {
        novaPasta.deleteRecursively()
        for ((caminho, texto) in ficheiros) {
            val f = File(novaPasta, caminho)
            f.parentFile?.mkdirs()
            f.writeText(texto)
        }
        pasta.deleteRecursively()
        novaPasta.renameTo(pasta)
    }

    /** Volta aos dados originais da aplicação. */
    fun repor() {
        pasta.deleteRecursively()
    }
}

/**
 * Estado e ações do editor. Os ecrãs só leem os campos e chamam as funções; as mudanças são feitas na base de dados
 * "de trabalho" e só ficam guardadas quando se toca em «Guardar».
 */
class ControloEditor(
    private val escopo: CoroutineScope,
    private val contexto: Context,
    private val armazemBase: ArmazemBase,
    private val lerOriginal: (String) -> String?,
    private val carreiraAtual: () -> Carreira?,
    private val gravarCarreira: (Carreira) -> Unit,
    /** Chamado depois de guardar: a lista de clubes das carreiras novas ou os ecrãs da carreira têm de ser refeitos. */
    private val aoGuardar: (OrigemEditor) -> Unit,
) {
    var origem by mutableStateOf(OrigemEditor.BASE)
        private set
    var pronto by mutableStateOf(false)
        private set
    var ocupado by mutableStateOf(false)
        private set
    var separador by mutableStateOf(SeparadorEditor.CLUBES)
    var alterado by mutableStateOf(false)
        private set

    /** Mensagem para mostrar numa janela (null = nenhuma). */
    var mensagem by mutableStateOf<String?>(null)

    var paises by mutableStateOf<List<OpcaoUi>>(emptyList())
        private set
    var opcoesClubes by mutableStateOf<List<OpcaoUi>>(emptyList())
        private set
    var opcoesNacionalidades by mutableStateOf<List<OpcaoUi>>(emptyList())
        private set

    var clubes by mutableStateOf<List<ClubeEdUi>>(emptyList())
        private set
    var filtroPais by mutableStateOf<String?>(null)
        private set
    var textoClubes by mutableStateOf("")
        private set

    var jogadores by mutableStateOf<List<JogadorEdUi>>(emptyList())
        private set
    var totalJogadores by mutableStateOf(0)
        private set
    var filtroClube by mutableStateOf<String?>(null)
        private set
    var filtroPosicao by mutableStateOf<String?>(null)
        private set
    var textoJogadores by mutableStateOf("")
        private set

    var clubeAberto by mutableStateOf<ClubeFichaUi?>(null)
        private set
    var jogadorAberto by mutableStateOf<JogadorFichaUi?>(null)
        private set
    var relatorio by mutableStateOf<RelatorioUi?>(null)
        private set

    private var bd: BaseDados? = null
    private var editor: EditorDados? = null
    private var importacao: RelatorioImportacao? = null
    private var exportacao: String = ""

    /** Nome da carreira aberta (para o título no modo «carreira»). */
    var nomeDaCarreira by mutableStateOf("")
        private set

    // ------------------------------------------------------------------ abrir e fechar

    fun abrir(modo: OrigemEditor) {
        origem = modo
        pronto = false
        alterado = false
        clubeAberto = null; jogadorAberto = null; relatorio = null; importacao = null
        separador = SeparadorEditor.CLUBES
        filtroPais = null; textoClubes = ""; filtroClube = null; filtroPosicao = null; textoJogadores = ""
        if (modo == OrigemEditor.CARREIRA) {
            val c = carreiraAtual() ?: return
            nomeDaCarreira = "${c.info.nome} · ${c.equipa.nome}"
            usar(c.bd)
        } else {
            ocupado = true
            escopo.launch {
                try {
                    val carregada = withContext(Dispatchers.Default) { lerBase() }
                    usar(carregada)
                } catch (e: Exception) {
                    mensagem = "Não foi possível abrir a base de dados: ${e.message ?: e.javaClass.simpleName}"
                } finally {
                    ocupado = false
                }
            }
        }
    }

    /** Fecha o editor. Numa carreira, grava o que tiver sido alterado. */
    fun fechar() {
        val c = carreiraAtual()
        if (origem == OrigemEditor.CARREIRA && alterado && c != null) {
            escopo.launch(Dispatchers.Default) { gravarCarreira(c) }
            aoGuardar(OrigemEditor.CARREIRA)
        }
        bd = null; editor = null; pronto = false; alterado = false
        clubes = emptyList(); jogadores = emptyList()
        clubeAberto = null; jogadorAberto = null; relatorio = null; importacao = null
    }

    /** A base de dados editada (se existir) com os originais por baixo. */
    fun lerBase(): BaseDados {
        val base = BaseDados()
        if (armazemBase.existe()) {
            base.carregarComSubstituicoes({ armazemBase.ler(it) }, lerOriginal)
            if (base.erros.isEmpty() && base.equipas.isNotEmpty()) return base
        }
        val original = BaseDados()
        original.carregar(lerOriginal)
        check(original.equipas.isNotEmpty()) { "Base de dados vazia. ${original.erros.take(3).joinToString(" ")}" }
        return original
    }

    private fun usar(base: BaseDados) {
        bd = base
        editor = EditorDados(base)
        paises = base.paises.values.map { OpcaoUi(it.id, it.nome) }
        val codigos = (base.paises.keys + base.jogadores.values.map { it.nacionalidade }).filter { it.isNotEmpty() }.toSortedSet()
        opcoesNacionalidades = codigos.map { OpcaoUi(it, base.paises[it]?.let { p -> "$it · ${p.nome}" } ?: it) }
        refrescarOpcoes()
        refrescarClubes()
        refrescarJogadores()
        pronto = true
    }

    private fun refrescarOpcoes() {
        val base = bd ?: return
        opcoesClubes = base.equipas.values.sortedWith(compareBy({ it.pais }, { it.nome }))
            .map { OpcaoUi(it.id, "${it.nome} (${nomeLiga(base, it.liga)})") }
    }

    private fun nomeLiga(base: BaseDados, id: String) = base.competicoes[id]?.nome ?: id

    private fun marcarAlterado() {
        alterado = true
        refrescarOpcoes()
        refrescarClubes()
        refrescarJogadores()
    }

    // ------------------------------------------------------------------ listas

    fun filtrarClubes(pais: String?, texto: String) {
        filtroPais = pais; textoClubes = texto
        refrescarClubes()
    }

    fun filtrarJogadores(clube: String?, posicao: String?, texto: String) {
        filtroClube = clube; filtroPosicao = posicao; textoJogadores = texto
        refrescarJogadores()
    }

    private fun refrescarClubes() {
        val base = bd ?: return
        val t = textoClubes.trim()
        clubes = base.equipas.values
            .filter { (filtroPais == null || it.pais == filtroPais) && (t.isEmpty() || it.nome.contains(t, true) || it.abreviatura.contains(t, true) || it.cidade.contains(t, true)) }
            .sortedWith(compareBy({ it.pais }, { base.competicoes[it.liga]?.nivel ?: 9 }, { it.nome }))
            .map { ClubeEdUi(it.id, it.nome, it.abreviatura, nomeLiga(base, it.liga), it.reputacao, it.plantel.size, emblema(it)) }
    }

    private fun refrescarJogadores() {
        val base = bd ?: return
        val t = textoJogadores.trim()
        val todos = base.jogadores.values.filter {
            (filtroClube == null || it.equipa == filtroClube) &&
                (filtroPosicao == null || it.posicao == filtroPosicao) &&
                (t.isEmpty() || it.nome.contains(t, true))
        }
        totalJogadores = todos.size
        jogadores = todos.sortedWith(compareByDescending<Jogador> { it.geral() }.thenBy { it.nome }).take(LIMITE_LISTA)
            .map { JogadorEdUi(it.id, it.nome, it.posicao, it.idade, it.geral(), it.potencial, base.equipas[it.equipa]?.abreviatura ?: "Livre") }
    }

    // ------------------------------------------------------------------ clubes

    fun abrirClube(id: String) {
        val base = bd ?: return
        val e = base.equipas[id] ?: return
        clubeAberto = ClubeFichaUi(
            e.id, e.nome, e.abreviatura, e.cidade, e.estadio, e.capacidade.toString(), e.reputacao, e.orcamento.toString(),
            e.cores.getOrElse(0) { "#ffffff" }, e.cores.getOrElse(1) { "#000000" },
            nomeLiga(base, e.liga), e.pais,
            // Numa carreira o calendário já está feito: não se trocam clubes de liga.
            if (origem == OrigemEditor.BASE) {
                base.equipas.values.filter { it.pais == e.pais && it.liga != e.liga }.sortedBy { it.nome }
                    .map { OpcaoUi(it.id, "${it.nome} (${nomeLiga(base, it.liga)})") }
            } else emptyList(),
        )
    }

    fun fecharClube() {
        clubeAberto = null
    }

    fun guardarClube(f: ClubeFichaUi) {
        val ed = editor ?: return
        val capacidade = f.capacidade.trim().toIntOrNull() ?: return avisar("A capacidade tem de ser um número.")
        val orcamento = f.orcamento.trim().toLongOrNull() ?: return avisar("O orçamento tem de ser um número (em euros).")
        val erro = ed.editarClube(f.id, f.nome, f.sigla, f.cidade, f.estadio, capacidade, f.reputacao, orcamento, listOf(f.cor1.trim(), f.cor2.trim()))
        if (erro != null) return avisar(erro)
        clubeAberto = null
        marcarAlterado()
    }

    /** O clube troca de liga com outro do mesmo país (as ligas ficam com o mesmo tamanho). */
    fun trocarLiga(clubeId: String, outroId: String) {
        val erro = editor?.trocarLiga(clubeId, outroId)
        if (erro != null) return avisar(erro)
        marcarAlterado()
        abrirClube(clubeId)
    }

    // ------------------------------------------------------------------ jogadores

    fun abrirJogador(id: Int) {
        val base = bd ?: return
        val j = base.jogadores[id] ?: return
        jogadorAberto = JogadorFichaUi(
            j.id, false, j.nome, j.nacionalidade, j.idade, j.posicao, j.pe,
            Jogador.ATRIBUTOS.associateWith { j.atributo(it) }, j.potencial.coerceAtLeast(j.geral()),
            j.equipa, base.equipas[j.equipa]?.nome ?: "Sem clube", j.contratoAte, j.salario.toString(),
        )
    }

    /** Um jogador novo, de 20 anos e atributos 50, no clube que estiver filtrado (se houver). */
    fun novoJogador() {
        val base = bd ?: return
        val clube = filtroClube?.let { base.equipas[it] }
        val atributos = Jogador.ATRIBUTOS.associateWith { 50 }
        jogadorAberto = JogadorFichaUi(
            0, true, "", clube?.pais?.takeIf { it in base.paises } ?: "POR", 20, filtroPosicao ?: "MC", "direito", atributos, 60,
            clube?.id ?: "", clube?.nome ?: "Escolhe o clube", 2028, "100000",
        )
    }

    fun fecharJogador() {
        jogadorAberto = null
    }

    fun guardarJogador(f: JogadorFichaUi) {
        val base = bd ?: return
        val ed = editor ?: return
        val salario = f.salario.trim().toIntOrNull() ?: return avisar("O salário tem de ser um número (em euros por ano).")
        if (f.novo) {
            val j = Jogador(0, f.nome.trim(), f.nacionalidade, f.idade, f.posicao, f.pe, f.equipaId, f.potencial, f.atributos.toMutableMap(), salario, f.contratoAte)
            val erro = ed.criarJogador(j)
            if (erro != null) return avisar(erro)
        } else {
            val erro = ed.editarJogador(f.id, f.nome, f.nacionalidade, f.idade, f.posicao, f.pe, f.atributos, f.potencial, f.contratoAte, salario)
            if (erro != null) return avisar(erro)
            val atual = base.jogadores[f.id]
            if (atual != null && f.equipaId != atual.equipa) {
                val erroMover = ed.moverJogador(f.id, f.equipaId)
                if (erroMover != null) {
                    marcarAlterado()
                    return avisar("Os dados ficaram guardados, mas o jogador não mudou de clube: $erroMover")
                }
            }
        }
        jogadorAberto = null
        marcarAlterado()
    }

    fun apagarJogador(id: Int) {
        val erro = editor?.apagarJogador(id)
        if (erro != null) return avisar("Não se pode apagar: $erro")
        jogadorAberto = null
        marcarAlterado()
    }

    // ------------------------------------------------------------------ guardar e repor

    /** Grava as alterações: na base para carreiras novas (ficheiros privados) ou na carreira aberta. */
    fun guardar() {
        val base = bd ?: return
        if (ocupado) return
        ocupado = true
        val modo = origem
        val carreira = carreiraAtual()
        escopo.launch {
            try {
                withContext(Dispatchers.Default) {
                    if (modo == OrigemEditor.BASE) {
                        armazemBase.gravar(base.paraJsonCompleto())
                    } else if (carreira != null) {
                        carreira.baseDadosPorGravar = true
                        gravarCarreira(carreira)
                    }
                }
                alterado = false
                aoGuardar(modo)
                mensagem = if (modo == OrigemEditor.BASE) "Guardado. As carreiras novas vão usar estes dados." else "Guardado nesta carreira."
            } catch (e: Exception) {
                mensagem = "Não foi possível guardar: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                ocupado = false
            }
        }
    }

    /** Apaga as alterações guardadas e volta aos dados que vêm com a aplicação. */
    fun reporDadosOriginais() {
        if (origem != OrigemEditor.BASE || ocupado) return
        ocupado = true
        escopo.launch {
            try {
                val original = withContext(Dispatchers.Default) {
                    armazemBase.repor()
                    lerBase()
                }
                clubeAberto = null; jogadorAberto = null
                usar(original)
                alterado = false
                aoGuardar(OrigemEditor.BASE)
                mensagem = "Dados originais repostos."
            } catch (e: Exception) {
                mensagem = "Não foi possível repor: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                ocupado = false
            }
        }
    }

    // ------------------------------------------------------------------ exportar

    /** Prepara o texto a exportar e devolve o nome sugerido para o ficheiro; a escolha do ficheiro é feita pelo ecrã. */
    fun prepararExportacao(tipo: TipoExportacao, id: String? = null): String {
        val base = bd ?: return "base.json"
        return when (tipo) {
            TipoExportacao.TUDO -> { exportacao = Validador.exportarBase(base); "futebol-carreira-base.json" }
            TipoExportacao.PAIS -> { exportacao = Validador.exportarPais(base, id.orEmpty()); "futebol-carreira-${id}.json" }
            TipoExportacao.CLUBE -> { exportacao = Validador.exportarClube(base, id.orEmpty()); "clube-${id}.json" }
        }
    }

    /** Escreve o texto preparado no ficheiro que o utilizador escolheu. */
    fun escreverExportacao(uri: Uri) {
        val texto = exportacao
        escopo.launch {
            try {
                withContext(Dispatchers.IO) {
                    contexto.contentResolver.openOutputStream(uri, "wt")?.use { it.write(texto.toByteArray(Charsets.UTF_8)) }
                        ?: error("Não foi possível abrir o ficheiro.")
                }
                mensagem = "Ficheiro exportado (${texto.length / 1024 + 1} KB)."
            } catch (e: Exception) {
                mensagem = "Não foi possível exportar: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    // ------------------------------------------------------------------ importar

    /** Lê o ficheiro escolhido pelo utilizador e valida-o. */
    fun lerImportacao(uri: Uri) {
        escopo.launch {
            val texto = try {
                withContext(Dispatchers.IO) {
                    contexto.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Não foi possível abrir o ficheiro.")
                }
            } catch (e: Exception) {
                mensagem = "Não foi possível ler o ficheiro: ${e.message ?: e.javaClass.simpleName}"
                return@launch
            }
            importarTexto(texto)
        }
    }

    /** Valida um texto JSON importado e mostra o relatório (nada é alterado enquanto o utilizador não confirmar). */
    fun importarTexto(texto: String) {
        val base = bd ?: return
        if (ocupado) return
        ocupado = true
        escopo.launch {
            try {
                val r = withContext(Dispatchers.Default) { Validador.validarTexto(texto, base) }
                importacao = r
                val erros = r.erros.toMutableList()
                if (origem == OrigemEditor.CARREIRA && r.tipo == TipoImportacao.BASE) {
                    erros += "Numa carreira só se importam clubes. Para importar a base de dados completa, usa o «Editor» do menu principal."
                }
                relatorio = RelatorioUi(r.resumo, erros, r.avisos)
            } catch (e: Exception) {
                relatorio = RelatorioUi("Importação bloqueada.", listOf("Erro inesperado: ${e.message ?: e.javaClass.simpleName}"), emptyList())
            } finally {
                ocupado = false
            }
        }
    }

    fun cancelarImportacao() {
        relatorio = null
        importacao = null
    }

    /** Aplica a importação validada (só na base de trabalho; é preciso «Guardar» para ficar). */
    fun aplicarImportacao() {
        val r = importacao ?: return
        val rel = relatorio ?: return
        if (!rel.podeAplicar || !r.ok) return
        try {
            if (r.tipo == TipoImportacao.CLUBE) {
                editor?.aplicarClube(r.equipa!!, r.jogadores)
            } else {
                val nova = BaseDados()
                nova.carregar { r.ficheiros?.get(it) }
                check(nova.erros.isEmpty()) { nova.erros.first() }
                usar(nova)
            }
            relatorio = null; importacao = null
            marcarAlterado()
            mensagem = "Importado. Toca em «Guardar» para ficar guardado."
        } catch (e: Exception) {
            mensagem = "Não foi possível aplicar: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun avisar(texto: String) {
        mensagem = texto
    }

    companion object {
        const val LIMITE_LISTA = 150

        /** O "geral" que um jogador teria com estes atributos nesta posição (para mostrar enquanto se mexe nos sliders). */
        fun geralDe(posicao: String, atributos: Map<String, Int>): Int =
            Jogador(posicao = posicao, atributos = atributos.toMutableMap()).geral()

        /** Emblema (cores) de um clube. */
        fun emblema(e: Equipa): EmblemaUi {
            val fundo = corDe(e.cores.getOrNull(0)) ?: 0xFF2E7D32
            var letras = corDe(e.cores.getOrNull(1)) ?: 0xFFFFFFFF
            if (kotlin.math.abs(brilho(fundo) - brilho(letras)) < 90) letras = if (brilho(fundo) > 140) 0xFF000000 else 0xFFFFFFFF
            return EmblemaUi(e.abreviatura, fundo, letras)
        }

        /** "#rrggbb" em ARGB (null se o texto não for uma cor). */
        fun corDe(texto: String?): Long? {
            if (texto == null || !Regex("^#[0-9a-fA-F]{6}$").matches(texto)) return null
            return 0xFF000000 or texto.substring(1).toLong(16)
        }

        private fun brilho(c: Long): Int = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114).toInt() / 1000
    }
}
