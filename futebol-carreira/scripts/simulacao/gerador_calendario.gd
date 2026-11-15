class_name GeradorCalendario
extends RefCounted
## Cria o calendário de uma liga (todos contra todos).
##
## Usa o "método do círculo": uma equipa fica fixa e as outras rodam à volta,
## de forma que em cada jornada todas as equipas jogam exatamente uma vez.

## Devolve uma lista de jornadas; cada jornada é uma lista de jogos [casa, fora]
## (ids das equipas). Com 2 voltas, a segunda repete a primeira com casa/fora trocados.
static func gerar(ids_equipas: Array, voltas: int, rng: RandomNumberGenerator) -> Array:
	var lista := ids_equipas.duplicate()
	# Baralha para o calendário ser diferente todas as épocas.
	for i in range(lista.size() - 1, 0, -1):
		var k := rng.randi_range(0, i)
		var temp = lista[i]
		lista[i] = lista[k]
		lista[k] = temp
	# Número ímpar de equipas: junta-se uma "folga" (quem calha com ela descansa).
	if lista.size() % 2 == 1:
		lista.append(null)
	var n := lista.size()
	if n < 2:
		return []

	var primeira_volta := []
	for ronda in n - 1:
		var jornada := []
		for i in n / 2:
			var a = lista[i]
			var b = lista[n - 1 - i]
			if a == null or b == null:
				continue
			# Alterna quem joga em casa para ficar equilibrado.
			var troca := (ronda % 2 == 1) if i == 0 else (i % 2 == 1)
			jornada.append([b, a] if troca else [a, b])
		primeira_volta.append(jornada)
		# Roda todas as equipas menos a primeira.
		lista.insert(1, lista.pop_back())

	var calendario := []
	for volta in voltas:
		for jornada in primeira_volta:
			var jogos := []
			for jogo in jornada:
				jogos.append([jogo[1], jogo[0]] if volta % 2 == 1 else [jogo[0], jogo[1]])
			calendario.append(jogos)
	return calendario
