class_name SimuladorJogo
extends RefCounted
## Simulação rápida de um jogo (só o resultado).
## Na Fase 2 passa a haver simulação detalhada: marcadores, cartões, lesões, etc.

const GOLOS_BASE_CASA := 1.50   # média de golos da equipa da casa entre equipas iguais
const GOLOS_BASE_FORA := 1.15   # média de golos da equipa visitante
const ESCALA := 20.0            # quanto maior, menos pesa a diferença de qualidade
const MAX_GOLOS := 9


## Devolve {"golos_casa": x, "golos_fora": y}.
static func simular(casa: Equipa, fora: Equipa, rng: RandomNumberGenerator) -> Dictionary:
	var fc := casa.forca()
	var ff := fora.forca()
	# Golos esperados: o ataque de uma equipa contra a defesa da outra.
	var esperados_casa := GOLOS_BASE_CASA * exp((float(fc["ataque"]) - float(ff["defesa"])) / ESCALA)
	var esperados_fora := GOLOS_BASE_FORA * exp((float(ff["ataque"]) - float(fc["defesa"])) / ESCALA)
	return {
		"golos_casa": _poisson(esperados_casa, rng),
		"golos_fora": _poisson(esperados_fora, rng),
	}


## Sorteia um número de golos à volta de uma média (distribuição de Poisson).
static func _poisson(media: float, rng: RandomNumberGenerator) -> int:
	var limite := exp(-clampf(media, 0.05, 6.0))
	var golos := 0
	var p := rng.randf()
	while p > limite and golos < MAX_GOLOS:
		golos += 1
		p *= rng.randf()
	return golos
