package pt.futebolcarreira.nucleo.carreira

import kotlinx.serialization.Serializable
import pt.futebolcarreira.nucleo.modelos.Equipa
import pt.futebolcarreira.nucleo.simulacao.ConfrontoNoticia
import pt.futebolcarreira.nucleo.simulacao.FasesEuropa
import pt.futebolcarreira.nucleo.simulacao.JogoNoticia
import pt.futebolcarreira.nucleo.simulacao.Resultado
import pt.futebolcarreira.nucleo.simulacao.TipoEuropa
import pt.futebolcarreira.nucleo.simulacao.TipoEvento
import java.time.LocalDate

/**
 * Uma notícia (manchete e texto curto). "tipo" diz de que acontecimento nasceu ("derby", "goleada", "surpresa",
 * "hat-trick", "golos", "lideranca", "titulo", "descida", "taca", "europa", "lesao", "mercado", "selecao", "humor"...);
 * "doClube" é true se diz respeito ao clube do utilizador.
 */
@Serializable
class Noticia(val data: String, val titulo: String, val texto: String, val tipo: String = "", val doClube: Boolean = false)

/**
 * Transforma o que aconteceu no mundo do jogo em manchetes ao estilo de um jornal desportivo português.
 * Só conta o que o jogo modela (resultados, marcadores, lesões, tabelas, taças, Europa, mercado, seleções, ambiente).
 * No máximo [MAX_POR_DIA] notícias por dia de jogos, as mais importantes primeiro; o clube do utilizador tem prioridade.
 */
class GeradorNoticias(private val c: Carreira) {
    private class Candidata(val prioridade: Int, val noticia: Noticia)

    private val pendentes = mutableListOf<Candidata>()

    /** Golos seguidos de cada jogador na liga (não se grava: recomeça a contar ao carregar). */
    private val seguidos = HashMap<Int, Int>()
    private val lideres = HashMap<String, String>()
    private var humorAdeptos: Int? = null
    private var humorDirecao: Int? = null

    /** Acontecimentos únicos já noticiados nesta época (título decidido, descida decidida...). Grava-se com a carreira. */
    val marcas = mutableSetOf<String>()

    private val rng get() = c.gestor.aleatorio
    private val bd get() = c.bd

    /** Começa uma época nova: esquece liderança, sequências e acontecimentos únicos. */
    fun novaEpoca() {
        seguidos.clear(); lideres.clear(); marcas.clear(); pendentes.clear()
        humorAdeptos = null; humorDirecao = null
    }

    private fun <T> variante(vararg o: T): T = o[rng.nextInt(o.size)]

    private fun nome(id: String) = bd.equipas[id]?.nome ?: id

    /** Junta uma notícia à fila do dia ("publicar" escolhe as melhores). */
    fun adicionar(prioridade: Int, tipo: String, titulo: String, texto: String, doClube: Boolean = false, data: LocalDate = c.gestor.data) {
        pendentes += Candidata(prioridade + if (doClube) 10 else 0, Noticia(data.toString(), titulo, texto, tipo, doClube))
    }

    /** Escolhe as melhores notícias pendentes (no máximo [MAX_POR_DIA]) e põe-nas no topo da lista. */
    fun publicar() {
        val escolhidas = pendentes.distinctBy { it.noticia.titulo }.sortedByDescending { it.prioridade }.take(MAX_POR_DIA)
        pendentes.clear()
        // A mais importante fica em cima.
        for (n in escolhidas.asReversed()) c.manchetes.add(0, n.noticia)
        while (c.manchetes.size > MAX_NOTICIAS) c.manchetes.removeAt(c.manchetes.lastIndex)
    }

    // ------------------------------------------------ a partir dos jogos

    /** Prepara as notícias do que se jogou desde a última vez; quem chama deve depois chamar [publicar]. */
    fun gerar(jogos: List<JogoNoticia>, confrontos: List<ConfrontoNoticia>, negocios: List<String>) {
        val pais = c.equipa.pais
        val ligasVistas = LinkedHashSet<String>()
        for (jn in jogos) {
            when (jn.tipo) {
                "liga" -> { jogoDeLiga(jn, pais); ligasVistas += jn.competicaoId }
                "europa" -> jogoEuropeu(jn, pais)
            }
        }
        for (liga in ligasVistas) tabela(liga, pais)
        for (cn in confrontos) taca(cn, pais)
        mercado(negocios)
        humor()
    }

    private fun relevante(e: Equipa, pais: String) = e.pais == pais || e.id == c.info.equipaId
    private fun nivel(e: Equipa) = bd.competicoes[e.liga]?.nivel ?: 1

    private fun marcadores(r: Resultado): String {
        val golos = r.relatorio?.eventos?.filter { it.tipo == TipoEvento.GOLO }.orEmpty()
        if (golos.isEmpty()) return ""
        return " Marcaram: " + golos.take(6).joinToString { "${it.jogador.nome} (${it.minuto}')" } + if (golos.size > 6) " e mais ${golos.size - 6}." else "."
    }

    private fun jogoDeLiga(jn: JogoNoticia, pais: String) {
        val r = jn.resultado
        val casa = bd.equipas[r.casa] ?: return
        val fora = bd.equipas[r.fora] ?: return
        val meu = c.info.equipaId
        val doClube = r.casa == meu || r.fora == meu
        val rel = relevante(casa, pais) || relevante(fora, pais)
        val topo = nivel(casa) == 1 && nivel(fora) == 1
        val liga = bd.competicoes[jn.competicaoId]?.nome ?: ""
        val placar = "${casa.nome} ${r.golosCasa}-${r.golosFora} ${fora.nome}"
        val vencedor = if (r.golosCasa > r.golosFora) casa else if (r.golosFora > r.golosCasa) fora else null
        val vencido = if (vencedor == null) null else if (vencedor === casa) fora else casa
        val gv = maxOf(r.golosCasa, r.golosFora)
        val gp = minOf(r.golosCasa, r.golosFora)
        val margem = gv - gp

        // Derbies.
        val rivalidade = bd.rivalidades().entre(r.casa, r.fora)
        if (rivalidade != null && (doClube || rel || jn.derby >= 3)) {
            val d = rivalidade.nome
            val titulo = if (vencedor == null) variante(
                "$d: tudo igual", "$d termina empatado a $gv", "$d: ninguém cede",
            ) else variante(
                "$d: ${vencedor.nome} vence por $gv-$gp",
                "$d: ${vencedor.nome} leva a melhor",
                "$d: ${vencedor.nome} derrota ${vencido!!.nome}",
                "$d é de ${vencedor.nome}",
            )
            val texto = "$placar ($liga)." + marcadores(r) + variante(
                " Foi um jogo jogado com a faca nos dentes.", " Ambiente de grande clássico nas bancadas.", " O estádio encheu para ver o derby.",
            )
            adicionar(55 + 8 * jn.derby, "derby", titulo, texto, doClube)
        }
        // Goleadas.
        if (margem >= 4 && vencedor != null && (doClube || rel || (topo && margem >= 5))) {
            val titulo = variante(
                "${vencedor.nome} goleia ${vencido!!.nome} por $gv-$gp",
                "Goleada de ${vencedor.nome}: $gv-$gp",
                "${vencedor.nome} em festa: $gv-$gp ao ${vencido.nome}",
                "${vencido.nome} afundado: derrota por $gv-$gp",
            )
            adicionar(if (doClube) 62 else if (rel) 48 else 34, "goleada", titulo, "$placar ($liga)." + marcadores(r), doClube)
        }
        // Surpresas: o mais fraco vence.
        if (vencedor != null && vencido!!.reputacao - vencedor.reputacao >= 12 && (doClube || rel || (topo && vencido.reputacao >= 75))) {
            val titulo = variante(
                "Surpresa: ${vencedor.nome} vence ${vencido.nome}",
                "${vencedor.nome} deixa ${vencido.nome} de rastos",
                "Tropeção de ${vencido.nome} diante do ${vencedor.nome}",
                "Dia de glória para o ${vencedor.nome}",
            )
            adicionar(if (doClube) 60 else if (rel) 47 else 36, "surpresa", titulo, "$placar ($liga). O favorito ficou pelo caminho.", doClube)
        }
        // Hat-tricks, sequências de golos e lesões de estrelas.
        for (lado in listOf(r.relatorio?.casa, r.relatorio?.fora)) {
            lado ?: continue
            val equipa = bd.equipas[lado.equipa] ?: continue
            val nosso = equipa.id == meu
            val relEquipa = relevante(equipa, pais)
            val estrelas = if (lado.fichas.any { it.lesao >= 3 }) equipa.plantel.sortedByDescending { it.geral() }.take(3) else emptyList()
            for (f in lado.fichas) {
                val j = f.jogador
                if (f.golos >= 3) {
                    val titulo = variante(
                        "${j.nome} marca ${f.golos} e é o herói do ${equipa.nome}",
                        "Hat-trick de ${j.nome}",
                        "${j.nome} em estado de graça: ${f.golos} golos",
                    )
                    adicionar(if (nosso) 66 else if (relEquipa) 52 else 38, "hat-trick", titulo, "$placar ($liga)." + marcadores(r), nosso)
                }
                if (f.golos > 0) {
                    val n = (seguidos[j.id] ?: 0) + 1
                    seguidos[j.id] = n
                    if (n == 4 || n == 6 || n == 8) {
                        val titulo = variante(
                            "${j.nome} marca pelo ${n}.º jogo seguido",
                            "${j.nome} não pára de marcar: $n jogos seguidos a festejar",
                            "Série de ${j.nome} chega aos $n jogos",
                        )
                        adicionar(if (nosso) 58 else if (relEquipa) 44 else 32, "golos", titulo, "O avançado do ${equipa.nome} voltou a marcar ($placar).", nosso)
                    }
                } else if (f.titular || f.entrou > 0) {
                    seguidos[j.id] = 0
                }
                if (f.lesao >= 3 && j in estrelas) {
                    val titulo = variante(
                        "Má notícia no ${equipa.nome}: ${j.nome} lesionado",
                        "${j.nome} cai com lesão e falha ${f.lesao} jogos",
                        "Baixa de peso: ${j.nome} fora por ${f.lesao} jornadas",
                    )
                    adicionar(if (nosso) 50 else if (relEquipa) 42 else 30, "lesao", titulo, "O ${equipa.nome} perde uma das suas figuras. O jogador lesionou-se frente ao ${nome(if (lado === r.relatorio?.casa) r.fora else r.casa)}.", nosso)
                }
            }
        }
    }

    // ------------------------------------------------ tabelas

    private fun tabela(ligaId: String, pais: String) {
        val liga = bd.competicoes[ligaId] ?: return
        val estado = c.gestor.estado[ligaId] ?: return
        val ordem = estado.tabela.ordenada()
        if (ordem.size < 2) return
        val minhaLiga = ligaId == c.equipa.liga
        val paisLiga = liga.pais == pais
        val importante = minhaLiga || (liga.nivel == 1 && paisLiga) || liga.nivel == 1
        val restantes = estado.totalJornadas - estado.jornada
        val epoca = c.gestor.ano
        val lider = ordem[0]
        val anterior = lideres[ligaId]
        lideres[ligaId] = lider.id
        if (anterior != null && anterior != lider.id && estado.jornada >= 3 && importante) {
            val nosso = lider.id == c.info.equipaId
            val titulo = variante(
                "${nome(lider.id)} assume a liderança",
                "Novo líder na ${liga.nome}: ${nome(lider.id)}",
                "${nome(lider.id)} ultrapassa ${nome(anterior)} e sobe ao primeiro lugar",
            )
            adicionar(if (minhaLiga) 56 else if (paisLiga) 45 else 28, "lideranca", titulo, "Com ${lider.pts} pontos, ${nome(lider.id)} lidera a ${liga.nome} a ${restantes} jornadas do fim.", nosso)
        }
        // Título decidido: ninguém apanha o líder.
        val chaveTitulo = "titulo/$epoca/$ligaId"
        if (importante && chaveTitulo !in marcas && lider.pts > ordem[1].pts + 3 * restantes && estado.jornada >= 5) {
            marcas += chaveTitulo
            val nosso = lider.id == c.info.equipaId
            val titulo = variante(
                "${nome(lider.id)} é campeão!",
                "Festa em ${bd.equipas[lider.id]?.cidade?.ifEmpty { null } ?: nome(lider.id)}: ${nome(lider.id)} conquista o título",
                "Título entregue: ${nome(lider.id)} campeão na ${liga.nome}",
            )
            adicionar(if (minhaLiga) 90 else if (paisLiga) 70 else 50, "titulo", titulo, "Com ${lider.pts} pontos e ${restantes} jornadas por jogar, ${nome(lider.id)} já não pode ser alcançado na ${liga.nome}.", nosso)
        }
        // Descida decidida: há tantas equipas fora de alcance que o clube já não escapa.
        if (liga.descidas > 0 && estado.jornada >= 8) {
            for (l in ordem.takeLast(liga.descidas + 2)) {
                val chave = "descida/$epoca/${l.id}"
                if (chave in marcas) continue
                val acima = ordem.count { it.pts > l.pts + 3 * restantes }
                if (acima >= ordem.size - liga.descidas) {
                    marcas += chave
                    val nosso = l.id == c.info.equipaId
                    val titulo = variante(
                        "${nome(l.id)} desce de divisão",
                        "Descida confirmada para o ${nome(l.id)}",
                        "${nome(l.id)} já não escapa à descida",
                    )
                    adicionar(if (nosso) 88 else if (minhaLiga) 52 else if (paisLiga) 46 else 26, "descida", titulo, "Com ${l.pts} pontos e ${restantes} jornadas por jogar, o ${nome(l.id)} está matematicamente despromovido da ${liga.nome}.", nosso)
                }
            }
        }
    }

    // ------------------------------------------------ taças e Europa

    private fun resultadoComExtras(r: Resultado): String {
        val extra = (if (r.prolongamento) " a.p." else "") + (if (r.penaltis) " (g.p. ${r.penaltisCasa}-${r.penaltisFora})" else "")
        return "${nome(r.casa)} ${r.golosCasa}-${r.golosFora} ${nome(r.fora)}$extra"
    }

    private fun taca(cn: ConfrontoNoticia, pais: String) {
        val conf = cn.confronto
        val vencedor = bd.equipas[conf.vencedor ?: return] ?: return
        val vencido = bd.equipas[conf.perdedor ?: return] ?: return
        val comp = bd.competicoes[cn.competicaoId]?.nome ?: return
        val doClube = c.info.equipaId in listOf(vencedor.id, vencido.id)
        val rel = relevante(vencedor, pais) || relevante(vencido, pais)
        val ultimo = conf.jogos.lastOrNull() ?: return
        val resultado = resultadoComExtras(ultimo)
        if (cn.tipo == "supertaca") {
            if (rel || cn.eFinal) {
                adicionar(if (rel) 66 else 40, "taca", variante("${vencedor.nome} vence a $comp", "$comp: ${vencedor.nome} levanta o troféu"), "$resultado.", doClube)
            }
            return
        }
        if (cn.eFinal) {
            val titulo = variante(
                "${vencedor.nome} conquista a $comp",
                "$comp: ${vencedor.nome} festeja",
                "${vencedor.nome} vence a final da $comp e ergue o troféu",
            )
            adicionar(if (rel) 80 else 52, "taca", titulo, "Na final: $resultado. ${vencido.nome} fica a um passo da glória.", doClube)
            return
        }
        val gigante = nivel(vencido) < nivel(vencedor) || vencido.reputacao - vencedor.reputacao >= 15
        if (gigante && (rel || vencido.reputacao >= 75)) {
            val titulo = variante(
                "$comp: ${vencedor.nome} elimina ${vencido.nome}",
                "Gigante caído: ${vencido.nome} fora da $comp",
                "David vence Golias: ${vencedor.nome} afasta ${vencido.nome}",
            )
            adicionar(if (rel) 68 else 38, "taca", titulo, "${cn.ronda}: $resultado.", doClube)
        }
    }

    private fun jogoEuropeu(jn: JogoNoticia, pais: String) {
        val r = jn.resultado
        val casa = bd.equipas[r.casa] ?: return
        val fora = bd.equipas[r.fora] ?: return
        val tipo = TipoEuropa.values().firstOrNull { it.id == jn.competicaoId } ?: return
        val doClube = c.info.equipaId in listOf(r.casa, r.fora)
        if (jn.fase == FasesEuropa.FINAL) {
            val campeao = c.gestor.europa?.estado(tipo)?.campeao?.takeIf { it.isNotEmpty() } ?: return
            val vencedor = bd.equipas[campeao] ?: return
            val rel = relevante(vencedor, pais) || relevante(casa, pais) || relevante(fora, pais)
            val titulo = variante("${vencedor.nome} é campeão europeu", "${tipo.nome}: ${vencedor.nome} no topo da Europa", "Noite histórica: ${vencedor.nome} vence a ${tipo.nome}")
            adicionar(if (rel) 85 else 60, "europa", titulo, "Na final: ${resultadoComExtras(r)}.", doClube)
            return
        }
        val nacional = if (relevante(casa, pais)) casa else if (relevante(fora, pais)) fora else return
        val outro = if (nacional === casa) fora else casa
        val golosN = if (nacional === casa) r.golosCasa else r.golosFora
        val golosO = if (nacional === casa) r.golosFora else r.golosCasa
        val titulo = when {
            golosN > golosO -> variante(
                "Noite europeia: ${nacional.nome} vence ${outro.nome} por $golosN-$golosO",
                "${tipo.nome}: ${nacional.nome} brilha frente ao ${outro.nome}",
                "${nacional.nome} sorri na Europa",
            )
            golosN < golosO -> variante(
                "${tipo.nome}: ${nacional.nome} cai diante do ${outro.nome}",
                "Noite negra na Europa para o ${nacional.nome}",
                "${nacional.nome} derrotado por $golosO-$golosN pelo ${outro.nome}",
            )
            else -> variante("${tipo.nome}: ${nacional.nome} empata com o ${outro.nome}", "Empate europeu para o ${nacional.nome}")
        }
        adicionar(if (doClube) 54 else 46, "europa", titulo, "${tipo.nome} (${jn.fase}): ${resultadoComExtras(r)}." + marcadores(r), doClube)
    }

    // ------------------------------------------------ mercado e adeptos

    private fun mercado(negocios: List<String>) {
        val regex = Regex("""(\d+),(\d) M€""")
        for (n in negocios.take(3)) {
            val milhoes = regex.find(n)?.let { it.groupValues[1].toInt() + it.groupValues[2].toInt() / 10.0 } ?: 0.0
            val titulo = if (milhoes >= 8.0) variante("Mercado: negócio milionário", "Reforço de peso no mercado", "Mercado a ferver: transferência de ${"%.1f".format(milhoes).replace('.', ',')} M€")
            else variante("Mercado: há novo reforço", "Negócio fechado entre clubes", "Mexidas no mercado de transferências")
            adicionar(if (milhoes >= 8.0) 50 else 33, "mercado", titulo, n)
        }
    }

    /** Muda de estado quando os adeptos ou a direção passam para um nível diferente: Adeptos contestam, Direção renova confiança... */
    private fun humor() {
        if (c.info.modo != ModoCarreira.TREINADOR || c.info.despedido) return
        val adeptos = when {
            c.info.confiancaAdeptos <= 25 -> -2
            c.info.confiancaAdeptos <= 40 -> -1
            c.info.confiancaAdeptos >= 80 -> 2
            else -> 0
        }
        val direcao = when {
            c.info.confiancaDirecao <= 25 -> -2
            c.info.confiancaDirecao >= 78 -> 2
            else -> 0
        }
        val clube = c.equipa.nome
        val antesA = humorAdeptos
        val antesD = humorDirecao
        humorAdeptos = adeptos
        humorDirecao = direcao
        if (antesA != null && adeptos != antesA) {
            when (adeptos) {
                -2 -> adicionar(64, "humor", variante("Adeptos contestam o treinador", "Assobios no estádio: adeptos do $clube perdem a paciência", "Revolta nas bancadas do $clube"), "Os adeptos do $clube já não escondem o descontentamento com o treinador.", true)
                -1 -> if (antesA > -1) adicionar(48, "humor", variante("Aumenta a pressão sobre o treinador", "Ambiente pesado em ${c.equipa.cidade.ifEmpty { clube }}"), "As bancadas começam a mostrar impaciência com os resultados do $clube.", true)
                2 -> adicionar(58, "humor", variante("Adeptos em festa", "Bancadas rendidas ao treinador do $clube", "Idolatria nas bancadas: o $clube vive um sonho"), "Os adeptos do $clube estão rendidos à equipa e ao treinador.", true)
            }
        }
        if (antesD != null && direcao != antesD) {
            when (direcao) {
                2 -> adicionar(60, "humor", variante("Direção renova confiança no treinador", "Direção do $clube blinda o treinador", "Presidente elogia o trabalho do treinador"), "A direção do $clube mostra-se satisfeita e garante todo o apoio ao treinador.", true)
                -2 -> adicionar(62, "humor", variante("Direção avalia o futuro do treinador", "Pressão na direção do $clube: o treinador está em risco"), "A direção do $clube está insatisfeita e acompanha de perto os próximos jogos.", true)
            }
        }
    }

    companion object {
        const val MAX_POR_DIA = 5
        const val MAX_NOTICIAS = 80
    }
}
