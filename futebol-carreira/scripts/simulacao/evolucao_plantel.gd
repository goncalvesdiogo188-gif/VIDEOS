class_name EvolucaoPlantel
extends RefCounted
## Evolução simples dos jogadores no fim da época: envelhecem, os jovens
## melhoram, os veteranos pioram e alguns reformam-se (entra um jovem).
## PROVISÓRIO: na Fase 6 é substituído pelo sistema completo de treino e academia.

static func aplicar(bd: BaseDados, gerador: GeradorJogadores, rng: RandomNumberGenerator) -> void:
	for equipa: Equipa in bd.equipas.values():
		for j: Jogador in equipa.plantel.duplicate():
			j.idade += 1
			var limite_reforma := 37 if j.posicao == "GR" else 35
			if j.idade >= limite_reforma and (j.idade >= limite_reforma + 3 or rng.randf() < 0.5):
				bd.remover_jogador(j)
				bd.adicionar_jogador(gerador.criar_jovem(equipa, j.posicao))
				continue
			var geral := j.geral()
			if j.idade <= 23 and geral < j.potencial:
				j.ajustar_atributos(mini(rng.randi_range(0, 4), j.potencial - geral))
			elif j.idade <= 28 and geral < j.potencial:
				j.ajustar_atributos(rng.randi_range(0, 1))
			elif j.idade >= (33 if j.posicao == "GR" else 31):
				j.ajustar_atributos(-rng.randi_range(0, 2))
		equipa.invalidar_forca()
