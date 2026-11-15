extends SceneTree
## Testes automáticos da Fase 1. Na linha de comandos, dentro da pasta do projeto:
##   godot --headless -s testes/testar_fase1.gd
## No fim escreve "TODOS OS TESTES PASSARAM" ou a lista do que falhou.

var falhas := 0


func _init() -> void:
	_testar_base_dados()
	_testar_calendario()
	_testar_epocas()
	if falhas == 0:
		print("TODOS OS TESTES PASSARAM")
	else:
		print("FALHARAM %d VERIFICAÇÕES" % falhas)
	quit(1 if falhas > 0 else 0)


func _verificar(condicao: bool, descricao: String) -> void:
	if not condicao:
		falhas += 1
		print("  FALHOU: " + descricao)


func _testar_base_dados() -> void:
	print("Base de dados...")
	var bd := BaseDados.new()
	_verificar(bd.carregar("res://dados"), "carregar sem erros: " + str(bd.erros.slice(0, 3)))
	_verificar(bd.paises.size() == 6, "6 países")
	_verificar(bd.competicoes.size() == 12, "12 ligas")
	for liga: Competicao in bd.competicoes.values():
		var n := bd.equipas_da_liga(liga.id).size()
		_verificar(n >= 2 and n % 2 == 0, "liga %s com número par de equipas (%d)" % [liga.id, n])
	for e: Equipa in bd.equipas.values():
		_verificar(e.plantel.size() >= 18, "plantel de %s com 18+ jogadores" % e.id)
		for entrada in e.melhor_onze():
			_verificar(entrada["jogador"] != null and entrada["nota"] > 30, "onze de %s completo" % e.id)
	for j: Jogador in bd.jogadores.values():
		_verificar(j.geral() >= 25 and j.geral() <= j.potencial, "geral/potencial válidos em %s" % j.nome)
	print("  %d equipas, %d jogadores" % [bd.equipas.size(), bd.jogadores.size()])


func _testar_calendario() -> void:
	print("Calendário...")
	var rng := RandomNumberGenerator.new()
	rng.seed = 1
	for n: int in [2, 4, 7, 18, 20]:
		var ids := []
		for i in n:
			ids.append("E%d" % i)
		var cal := GeradorCalendario.gerar(ids, 2, rng)
		var rondas: int = (n - 1 if n % 2 == 0 else n) * 2
		_verificar(cal.size() == rondas, "%d equipas: %d jornadas" % [n, rondas])
		var confrontos := {}
		var em_casa := {}
		for jornada in cal:
			var vistos := {}
			for jogo in jornada:
				_verificar(not vistos.has(jogo[0]) and not vistos.has(jogo[1]), "%d equipas: ninguém joga 2 vezes na mesma jornada" % n)
				vistos[jogo[0]] = true
				vistos[jogo[1]] = true
				var chave := "%s-%s" % [jogo[0], jogo[1]]
				confrontos[chave] = confrontos.get(chave, 0) + 1
				em_casa[jogo[0]] = em_casa.get(jogo[0], 0) + 1
		_verificar(confrontos.size() == n * (n - 1), "%d equipas: todos os confrontos casa/fora existem" % n)
		for chave in confrontos:
			_verificar(confrontos[chave] == 1, "%d equipas: %s só uma vez" % [n, chave])
		for id in ids:
			_verificar(em_casa.get(id, 0) == n - 1, "%d equipas: %s joga %d vezes em casa" % [n, id, n - 1])


func _testar_epocas() -> void:
	print("Épocas...")
	var bd := BaseDados.new()
	bd.carregar("res://dados")
	var gestor := GestorEpocas.new(bd, 12345)
	var inicio := Time.get_ticks_msec()
	for epoca in 5:
		gestor.simular_ate_ao_fim()
		_verificar(gestor.epoca_terminada(), "época %d terminada" % epoca)
		for liga_id in gestor.estado:
			var ordem: Array = gestor.estado[liga_id]["tabela"].ordenada()
			var n := ordem.size()
			var golos_m := 0
			var golos_s := 0
			for linha in ordem:
				_verificar(linha["j"] == (n - 1) * 2, "%s: todos jogaram %d jogos" % [liga_id, (n - 1) * 2])
				_verificar(linha["pts"] == linha["v"] * 3 + linha["e"], "%s: pontos certos" % liga_id)
				golos_m += linha["gm"]
				golos_s += linha["gs"]
			_verificar(golos_m == golos_s, "%s: golos marcados = sofridos" % liga_id)
			var media := float(golos_m) / float(n * (n - 1))
			_verificar(media > 2.0 and media < 3.6, "%s: média de golos realista (%.2f)" % [liga_id, media])
		var resumo: Dictionary = gestor.historico[-1]
		var p1: Dictionary = resumo["ligas"]["POR1"]
		var p2: Dictionary = resumo["ligas"]["POR2"]
		_verificar(p1["descem"].size() == 2 and p2["sobem"].size() == 2, "Portugal: 2 descem e 2 sobem")
		print("  %s  campeão POR: %s (%d pts) | desceram: %s | subiram: %s" % [
			gestor.nome_epoca(), bd.equipas[p1["campeao"]].nome, p1["pontos"],
			p1["descem"].map(func(id): return bd.equipas[id].abreviatura),
			p2["sobem"].map(func(id): return bd.equipas[id].abreviatura)])
		gestor.avancar_para_epoca_seguinte()
		for id in p1["descem"]:
			_verificar(bd.equipas[id].liga == "POR2", "%s desceu" % id)
		for id in p2["sobem"]:
			_verificar(bd.equipas[id].liga == "POR1", "%s subiu" % id)
		for liga: Competicao in bd.competicoes.values():
			_verificar(bd.equipas_da_liga(liga.id).size() == gestor.estado[liga.id]["tabela"].linhas.size(), "%s mantém o número de equipas" % liga.id)
		for e: Equipa in bd.equipas.values():
			_verificar(e.plantel.size() == 24, "%s mantém 24 jogadores" % e.id)
	_verificar(gestor.ano == 2031, "ano avançou para 2031")
	print("  5 épocas simuladas em %d ms" % (Time.get_ticks_msec() - inicio))
