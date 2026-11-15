class_name GestorEpocas
extends RefCounted
## Conduz as épocas: cria calendários, simula jornadas, fecha a época
## (campeões, subidas e descidas) e prepara a seguinte.

var bd: BaseDados
var rng := RandomNumberGenerator.new()
var gerador: GeradorJogadores
var ano: int = 2026

## Estado de cada liga nesta época:
## id da liga -> {"calendario": [...], "resultados": [...], "tabela": TabelaClassificativa, "jornada": int}
var estado := {}

## Uma entrada por época terminada:
## {"ano": 2026, "ligas": {id: {"campeao": id, "pontos": n, "sobem": [ids], "descem": [ids]}}}
var historico: Array = []

var _epoca_fechada := false


## "semente" igual dá sempre os mesmos resultados (útil para testes); -1 = ao acaso.
func _init(base: BaseDados, semente: int = -1, ano_inicial: int = 2026) -> void:
	bd = base
	ano = ano_inicial
	if semente >= 0:
		rng.seed = semente
	else:
		rng.randomize()
	gerador = GeradorJogadores.new(bd.nomes, rng)
	iniciar_epoca()


## Nome da época, por exemplo "2026/27".
func nome_epoca() -> String:
	return "%d/%02d" % [ano, (ano + 1) % 100]


## Cria calendários e tabelas novas para todas as ligas.
func iniciar_epoca() -> void:
	estado.clear()
	_epoca_fechada = false
	for liga: Competicao in bd.competicoes.values():
		var ids := []
		for e in bd.equipas_da_liga(liga.id):
			ids.append(e.id)
		ids.sort()
		estado[liga.id] = {
			"calendario": GeradorCalendario.gerar(ids, liga.voltas, rng),
			"resultados": [],
			"tabela": TabelaClassificativa.new(ids, liga.pontos_vitoria, liga.pontos_empate),
			"jornada": 0,
		}


func total_jornadas(liga_id: String) -> int:
	return estado[liga_id]["calendario"].size()


func epoca_terminada() -> bool:
	for liga_id in estado:
		if estado[liga_id]["jornada"] < total_jornadas(liga_id):
			return false
	return true


## Simula a jornada seguinte de todas as ligas. Devolve false se já não havia jogos.
func simular_jornada() -> bool:
	var houve_jogos := false
	for liga_id in estado:
		var e: Dictionary = estado[liga_id]
		if e["jornada"] >= e["calendario"].size():
			continue
		var tabela: TabelaClassificativa = e["tabela"]
		var resultados := []
		for jogo in e["calendario"][e["jornada"]]:
			var r := SimuladorJogo.simular(bd.equipas[jogo[0]], bd.equipas[jogo[1]], rng)
			tabela.registar(jogo[0], jogo[1], r["golos_casa"], r["golos_fora"])
			resultados.append({"casa": jogo[0], "fora": jogo[1], "golos_casa": r["golos_casa"], "golos_fora": r["golos_fora"]})
		e["resultados"].append(resultados)
		e["jornada"] += 1
		houve_jogos = true
	if epoca_terminada() and not _epoca_fechada:
		_fechar_epoca()
	return houve_jogos


func simular_ate_ao_fim() -> void:
	while simular_jornada():
		pass


## Regista campeões, subidas e descidas no histórico (ainda sem mexer nas equipas).
func _fechar_epoca() -> void:
	_epoca_fechada = true
	var resumo := {"ano": ano, "ligas": {}}
	for liga_id in estado:
		var ordem: Array = estado[liga_id]["tabela"].ordenada()
		if ordem.is_empty():
			continue
		resumo["ligas"][liga_id] = {"campeao": ordem[0]["id"], "pontos": ordem[0]["pts"], "sobem": [], "descem": []}
	for liga: Competicao in bd.competicoes.values():
		if liga.descidas <= 0 or not resumo["ligas"].has(liga.id) or not resumo["ligas"].has(liga.desce_para):
			continue
		var cima: Array = estado[liga.id]["tabela"].ordenada()
		var baixo: Array = estado[liga.desce_para]["tabela"].ordenada()
		var quantas := mini(liga.descidas, mini(cima.size(), baixo.size()) / 2)
		for i in quantas:
			resumo["ligas"][liga.id]["descem"].append(cima[cima.size() - 1 - i]["id"])
			resumo["ligas"][liga.desce_para]["sobem"].append(baixo[i]["id"])
	for liga_id in resumo["ligas"]:
		var liga: Competicao = bd.competicoes[liga_id]
		if liga.nivel == 1:
			bd.equipas[resumo["ligas"][liga_id]["campeao"]].titulos += 1
	historico.append(resumo)


## Aplica subidas/descidas, faz evoluir os jogadores e começa a época seguinte.
func avancar_para_epoca_seguinte() -> void:
	if not epoca_terminada():
		return
	var resumo: Dictionary = historico[-1]
	for liga_id in resumo["ligas"]:
		var liga: Competicao = bd.competicoes[liga_id]
		var dados: Dictionary = resumo["ligas"][liga_id]
		var campeao: Equipa = bd.equipas[dados["campeao"]]
		campeao.reputacao = mini(99, campeao.reputacao + 1)
		for id in dados["descem"]:
			var e: Equipa = bd.equipas[id]
			e.liga = liga.desce_para
			e.reputacao = maxi(1, e.reputacao - 2)
		for id in dados["sobem"]:
			# Sobe para a liga que tem esta como "desce_para".
			for acima: Competicao in bd.competicoes.values():
				if acima.desce_para == liga_id:
					bd.equipas[id].liga = acima.id
					bd.equipas[id].reputacao = mini(99, bd.equipas[id].reputacao + 1)
	EvolucaoPlantel.aplicar(bd, gerador, rng)
	ano += 1
	iniciar_epoca()
