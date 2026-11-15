extends Control
## Ecrã de teste da Fase 1: mostra uma liga a ser simulada, época após época.
## A interface é toda criada aqui em código (os menus "a sério" chegam na Fase 3).

const COLUNAS_TABELA := ["#", "Equipa", "J", "V", "E", "D", "GM", "GS", "DG", "Pts"]
const COLUNAS_PLANTEL := ["Pos", "Nome", "Id.", "Ger", "Pot", "Valor"]
const COR_SUBIDA := Color(0.15, 0.45, 0.2, 0.55)
const COR_DESCIDA := Color(0.55, 0.15, 0.15, 0.55)

var _liga_id := "POR1"
var _equipa_escolhida := ""

var _titulo: Label
var _escolha_liga: OptionButton
var _tabela: Tree
var _separadores: TabContainer
var _resultados: RichTextLabel
var _plantel_titulo: Label
var _plantel: Tree
var _historico: RichTextLabel


func _ready() -> void:
	_construir_interface()
	_atualizar()


# ---------------------------------------------------------------- construção

func _construir_interface() -> void:
	var margem := MarginContainer.new()
	margem.set_anchors_preset(Control.PRESET_FULL_RECT)
	for lado in ["left", "right", "top", "bottom"]:
		margem.add_theme_constant_override("margin_" + lado, 12)
	add_child(margem)

	var coluna := VBoxContainer.new()
	coluna.add_theme_constant_override("separation", 8)
	margem.add_child(coluna)

	# Barra de cima: época, escolha da liga e botões.
	var barra := HBoxContainer.new()
	barra.add_theme_constant_override("separation", 8)
	coluna.add_child(barra)

	_titulo = Label.new()
	_titulo.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_titulo.clip_text = true
	barra.add_child(_titulo)

	_escolha_liga = OptionButton.new()
	for pais in Jogo.bd.paises:
		for liga in Jogo.bd.ligas_do_pais(pais):
			_escolha_liga.add_item("%s – %s" % [Jogo.bd.paises[pais]["nome"], liga.nome])
			_escolha_liga.set_item_metadata(_escolha_liga.item_count - 1, liga.id)
	_escolha_liga.item_selected.connect(_ao_escolher_liga)
	barra.add_child(_escolha_liga)

	barra.add_child(_criar_botao("Jornada seguinte", _ao_simular_jornada))
	barra.add_child(_criar_botao("Época inteira", _ao_simular_epoca))
	barra.add_child(_criar_botao("10 épocas", _ao_simular_dez_epocas))
	barra.add_child(_criar_botao("Recomeçar", _ao_recomecar))

	# Zona principal: tabela à esquerda, separadores à direita.
	var zona := HBoxContainer.new()
	zona.size_flags_vertical = Control.SIZE_EXPAND_FILL
	zona.add_theme_constant_override("separation", 10)
	coluna.add_child(zona)

	_tabela = _criar_arvore(COLUNAS_TABELA, 1)
	_tabela.size_flags_stretch_ratio = 1.25
	_tabela.item_selected.connect(_ao_escolher_equipa)
	zona.add_child(_tabela)

	_separadores = TabContainer.new()
	_separadores.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	zona.add_child(_separadores)

	_resultados = _criar_texto("Resultados")
	_separadores.add_child(_resultados)

	var caixa_plantel := VBoxContainer.new()
	caixa_plantel.name = "Plantel"
	_separadores.add_child(caixa_plantel)
	_plantel_titulo = Label.new()
	_plantel_titulo.text = "Toca numa equipa da tabela para ver o plantel."
	_plantel_titulo.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	caixa_plantel.add_child(_plantel_titulo)
	_plantel = _criar_arvore(COLUNAS_PLANTEL, 1)
	_plantel.size_flags_vertical = Control.SIZE_EXPAND_FILL
	caixa_plantel.add_child(_plantel)

	_historico = _criar_texto("Histórico")
	_separadores.add_child(_historico)


func _criar_botao(texto: String, acao: Callable) -> Button:
	var botao := Button.new()
	botao.text = texto
	botao.custom_minimum_size = Vector2(0, 48)   # altura confortável para o dedo
	botao.pressed.connect(acao)
	return botao


func _criar_arvore(colunas: Array, coluna_larga: int) -> Tree:
	var arvore := Tree.new()
	arvore.columns = colunas.size()
	arvore.column_titles_visible = true
	arvore.hide_root = true
	arvore.select_mode = Tree.SELECT_ROW
	arvore.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	for i in colunas.size():
		arvore.set_column_title(i, colunas[i])
		arvore.set_column_expand(i, i == coluna_larga)
		if i != coluna_larga:
			arvore.set_column_custom_minimum_width(i, 96 if colunas[i] == "Valor" else (64 if colunas[i] == "Pos" else 50))
	return arvore


func _criar_texto(nome: String) -> RichTextLabel:
	var texto := RichTextLabel.new()
	texto.name = nome
	texto.bbcode_enabled = true
	return texto


# -------------------------------------------------------------------- botões

func _ao_escolher_liga(indice: int) -> void:
	_liga_id = _escolha_liga.get_item_metadata(indice)
	_equipa_escolhida = ""
	_atualizar()


func _ao_simular_jornada() -> void:
	if Jogo.gestor.epoca_terminada():
		Jogo.gestor.avancar_para_epoca_seguinte()
	else:
		Jogo.gestor.simular_jornada()
	_atualizar()


func _ao_simular_epoca() -> void:
	if Jogo.gestor.epoca_terminada():
		Jogo.gestor.avancar_para_epoca_seguinte()
	Jogo.gestor.simular_ate_ao_fim()
	_atualizar()


func _ao_simular_dez_epocas() -> void:
	for i in 10:
		if Jogo.gestor.epoca_terminada():
			Jogo.gestor.avancar_para_epoca_seguinte()
		Jogo.gestor.simular_ate_ao_fim()
	_separadores.current_tab = 2
	_atualizar()


func _ao_recomecar() -> void:
	Jogo.novo_jogo()
	_equipa_escolhida = ""
	_atualizar()


func _ao_escolher_equipa() -> void:
	var item := _tabela.get_selected()
	if item == null:
		return
	_equipa_escolhida = item.get_metadata(0)
	_separadores.current_tab = 1
	_mostrar_plantel()


# -------------------------------------------------------------- atualização

func _atualizar() -> void:
	var gestor: GestorEpocas = Jogo.gestor
	# Se a liga escolhida deixou de existir (ex.: dados editados), usa a primeira.
	if not gestor.estado.has(_liga_id):
		_liga_id = gestor.estado.keys()[0]
	var estado: Dictionary = gestor.estado[_liga_id]
	var situacao := "Jornada %d/%d" % [estado["jornada"], gestor.total_jornadas(_liga_id)]
	if gestor.epoca_terminada():
		situacao = "Época terminada"
	_titulo.text = "Época %s  ·  %s" % [gestor.nome_epoca(), situacao]
	_mostrar_tabela()
	_mostrar_resultados()
	_mostrar_plantel()
	_mostrar_historico()


func _mostrar_tabela() -> void:
	var bd: BaseDados = Jogo.bd
	var liga: Competicao = bd.competicoes[_liga_id]
	var ordem: Array = Jogo.gestor.estado[_liga_id]["tabela"].ordenada()
	# Quantas sobem desta liga = quantas descem da liga de cima.
	var sobem := 0
	for outra: Competicao in bd.competicoes.values():
		if outra.desce_para == _liga_id:
			sobem = outra.descidas
	_tabela.clear()
	var raiz := _tabela.create_item()
	for i in ordem.size():
		var linha: Dictionary = ordem[i]
		var equipa: Equipa = bd.equipas[linha["id"]]
		var item := _tabela.create_item(raiz)
		var valores := [i + 1, equipa.nome, linha["j"], linha["v"], linha["e"], linha["d"],
			linha["gm"], linha["gs"], "%+d" % (linha["gm"] - linha["gs"]), linha["pts"]]
		for c in valores.size():
			item.set_text(c, str(valores[c]))
			if c != 1:
				item.set_text_alignment(c, HORIZONTAL_ALIGNMENT_CENTER)
			# Verde: campeão ou zona de subida. Vermelho: zona de descida.
			if (liga.nivel == 1 and i == 0) or i < sobem:
				item.set_custom_bg_color(c, COR_SUBIDA)
			elif liga.desce_para != "" and i >= ordem.size() - liga.descidas:
				item.set_custom_bg_color(c, COR_DESCIDA)
		item.set_metadata(0, equipa.id)
		if equipa.id == _equipa_escolhida:
			item.select(0)


func _mostrar_resultados() -> void:
	var bd: BaseDados = Jogo.bd
	var estado: Dictionary = Jogo.gestor.estado[_liga_id]
	if estado["resultados"].is_empty():
		_resultados.text = "A época ainda não começou.\n\nCarrega em [b]Jornada seguinte[/b] para simular uma jornada, ou em [b]Época inteira[/b] para ir até ao fim."
		return
	var texto := "[b]Jornada %d[/b]\n\n" % estado["jornada"]
	for r in estado["resultados"][-1]:
		texto += "%s  [b]%d - %d[/b]  %s\n" % [bd.equipas[r["casa"]].nome, r["golos_casa"], r["golos_fora"], bd.equipas[r["fora"]].nome]
	if Jogo.gestor.epoca_terminada():
		texto += "\n[b]Época terminada.[/b] Carrega em [b]Jornada seguinte[/b] para começar a próxima."
	_resultados.text = texto


func _mostrar_plantel() -> void:
	_plantel.clear()
	var bd: BaseDados = Jogo.bd
	if not bd.equipas.has(_equipa_escolhida):
		_plantel_titulo.text = "Toca numa equipa da tabela para ver o plantel."
		return
	var equipa: Equipa = bd.equipas[_equipa_escolhida]
	var forca := equipa.forca()
	_plantel_titulo.text = "%s (%s) · %s, %d lugares\nReputação %d · Ataque %d · Defesa %d · Títulos %d" % [
		equipa.nome, equipa.abreviatura, equipa.estadio, equipa.capacidade,
		equipa.reputacao, roundi(forca["ataque"]), roundi(forca["defesa"]), equipa.titulos]
	var jogadores := equipa.plantel.duplicate()
	jogadores.sort_custom(func(a: Jogador, b: Jogador) -> bool:
		var pa := Jogador.POSICOES.find(a.posicao)
		var pb := Jogador.POSICOES.find(b.posicao)
		return pa < pb if pa != pb else a.geral() > b.geral())
	var raiz := _plantel.create_item()
	for j: Jogador in jogadores:
		var item := _plantel.create_item(raiz)
		var valores := [j.posicao, j.nome, j.idade, j.geral(), j.potencial, _formatar_euros(j.valor_mercado())]
		for c in valores.size():
			item.set_text(c, str(valores[c]))


func _mostrar_historico() -> void:
	var bd: BaseDados = Jogo.bd
	var gestor: GestorEpocas = Jogo.gestor
	if gestor.historico.is_empty():
		_historico.text = "Ainda não terminou nenhuma época."
		return
	var texto := "[b]%s – campeões[/b]\n\n" % bd.competicoes[_liga_id].nome
	var contagem := {}
	for i in range(gestor.historico.size() - 1, -1, -1):
		var resumo: Dictionary = gestor.historico[i]
		if not resumo["ligas"].has(_liga_id):
			continue
		var dados: Dictionary = resumo["ligas"][_liga_id]
		contagem[dados["campeao"]] = contagem.get(dados["campeao"], 0) + 1
		texto += "[b]%d/%02d[/b]  %s (%d pts)\n" % [resumo["ano"], (resumo["ano"] + 1) % 100, bd.equipas[dados["campeao"]].nome, dados["pontos"]]
		if not dados["sobem"].is_empty():
			texto += "      Subiram: %s\n" % _nomes(dados["sobem"])
		if not dados["descem"].is_empty():
			texto += "      Desceram: %s\n" % _nomes(dados["descem"])
	var ids := contagem.keys()
	ids.sort_custom(func(a, b) -> bool: return contagem[a] > contagem[b])
	texto += "\n[b]Total de títulos[/b]\n"
	for id in ids:
		texto += "%d × %s\n" % [contagem[id], bd.equipas[id].nome]
	_historico.text = texto


func _nomes(ids: Array) -> String:
	var lista := PackedStringArray()
	for id in ids:
		lista.append(Jogo.bd.equipas[id].nome)
	return ", ".join(lista)


func _formatar_euros(valor: int) -> String:
	if valor >= 1000000:
		return "%.1f M€" % (valor / 1000000.0)
	return "%d m€" % (valor / 1000)
