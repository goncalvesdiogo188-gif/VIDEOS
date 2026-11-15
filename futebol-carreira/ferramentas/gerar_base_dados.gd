extends SceneTree
## FERRAMENTA: cria a base de dados fictícia inicial (equipas e jogadores).
##
## ATENÇÃO: apaga e volta a criar dados/equipas e dados/jogadores.
## Só é preciso correr se quiseres uma base de dados nova. Na linha de comandos,
## dentro da pasta do projeto:
##   godot --headless -s ferramentas/gerar_base_dados.gd
##
## Todos os nomes são inventados. Para usar nomes reais, edita os JSON gerados.

const SEMENTE := 20260815

## "topo" = reputação do melhor clube do país; "tamanho" = equipas por divisão.
## Os locais estão por ordem aproximada de força (os primeiros são os "grandes").
const PAISES := [
	{
		"id": "POR", "topo": 82, "tamanho": 18, "ligas": ["POR1", "POR2"],
		"clubes": ["Atlético de %s", "Sporting de %s", "União de %s", "Desportivo de %s", "Académico de %s", "Vitória de %s", "FC %s", "SC %s", "GD %s", "CF %s", "Estrela de %s", "Leões de %s"],
		"estadios": ["Estádio Municipal de %s", "Estádio de %s", "Parque Desportivo de %s"],
		"locais": ["Valverde", "Ribadouro", "Montebelo", "Vila Serrana", "Pedralva", "Costa Nova", "Castrelo", "Alvorada", "Fonte Santa", "Serra Azul", "Monfalim", "Rio Largo", "Torre Velha", "Almendra", "Vale Dourado", "Penha Alta", "Sobreiral", "Lagoa Verde", "Entroncado", "Pinhal Manso", "Ribeira Clara", "Mirandal", "Salgueiral", "Cabo Raso", "Arrifal", "Vila Mourisca", "Porto Sereno", "Carvalhedo", "Fozmar", "Santa Luzia do Monte", "Barrocal", "Azenha Nova", "Moinhos Altos", "Campo Raso", "Urzeira", "Atalaia do Mar"],
	},
	{
		"id": "ESP", "topo": 88, "tamanho": 20, "ligas": ["ESP1", "ESP2"],
		"clubes": ["Real %s", "Atlético %s", "Deportivo %s", "CD %s", "UD %s", "Sporting %s", "Racing %s", "CF %s", "SD %s", "%s CF"],
		"estadios": ["Estadio Municipal de %s", "Campo de %s", "Estadio Nuevo %s"],
		"locais": ["Valdorado", "Puerto Blanco", "Sierralta", "Villaclara", "Monteazul", "Castroviejo", "Rioseco", "Torrealba", "Campoverde", "Bahía Dorada", "Almenar", "Peñarroja", "Fuentesanta", "Villarmosa", "Costalinda", "Robledal", "Olivares del Sur", "Marazul", "Solana", "Pinarejo", "Vegafría", "Lagunilla", "Cerro Alto", "Aldeanueva", "Portonuevo", "Zarzalejo", "Encinar", "Molinoviejo", "Valdehierro", "Arenales", "Brisamar", "Peñablanca", "Rocafuerte", "Trigales", "Miraflor", "Navalonga", "Casalta", "Viñaroja", "Hondonada", "Altavista"],
	},
	{
		"id": "ING", "topo": 89, "tamanho": 20, "ligas": ["ING1", "ING2"],
		"clubes": ["%s United", "%s City", "%s Town", "%s Rovers", "%s Athletic", "%s Wanderers", "FC %s", "%s Albion", "%s County"],
		"estadios": ["%s Park", "%s Road Stadium", "%s Arena"],
		"locais": ["Ashford Vale", "Northbridge", "Kingsmere", "Redcliffe", "Westhaven", "Blackmoor", "Stonebridge", "Elmhurst", "Harrowgate", "Thornbury", "Millbrook", "Oakfield", "Riverton", "Castlewick", "Eastmarsh", "Whitcombe", "Greywater", "Fernleigh", "Dunmore", "Marlowe", "Ironvale", "Southmere", "Brackenhill", "Lakewood", "Foxbury", "Hollowell", "Kestrel Bay", "Longmead", "Norcastle", "Pendlewood", "Queensholt", "Ravenmoor", "Saltwick", "Tidewell", "Upperton", "Winterbourne", "Yarrowby", "Abbotsley", "Coldharbour", "Wrenfield"],
	},
	{
		"id": "ITA", "topo": 86, "tamanho": 20, "ligas": ["ITA1", "ITA2"],
		"clubes": ["AC %s", "US %s", "Calcio %s", "Sporting %s", "Virtus %s", "Atletico %s", "FC %s", "SS %s", "Unione %s"],
		"estadios": ["Stadio Comunale di %s", "Stadio %s", "Arena %s"],
		"locais": ["Montefiore", "Valdoro", "Portoreale", "Castelrosso", "Fontanella", "Rivabella", "Pietraviva", "Collalto", "Borgonuovo", "Marebello", "Torrebianca", "Vallombra", "Sanfelice", "Campoluce", "Rocca d'Oro", "Lagoscuro", "Acquaviva", "Monteverde", "Pontevecchio", "Belmonte", "Serravalle", "Villalba", "Costadoro", "Fiumechiaro", "Poggiosole", "Casalfiore", "Altomare", "Spinaleto", "Vignarossa", "Terranova", "Cervara", "Olmeto", "Ripalta", "Solferina", "Mirabello", "Nevoso", "Pratolungo", "Querceto", "Zaffiro", "Isolabella"],
	},
	{
		"id": "ALE", "topo": 87, "tamanho": 18, "ligas": ["ALE1", "ALE2"],
		"clubes": ["FC %s", "SV %s", "TSV %s", "VfB %s", "Eintracht %s", "Borussia %s", "SC %s", "Union %s", "Fortuna %s"],
		"estadios": ["%s-Stadion", "%s-Arena", "Waldstadion %s"],
		"locais": ["Hohenberg", "Steinhafen", "Waldbruck", "Neustadt am See", "Rheinmark", "Eisenburg", "Lindenthal", "Schwarzbach", "Königsfeld", "Altenmoor", "Bergwald", "Falkenau", "Grünwalde", "Hafenstadt", "Kaltenbrunn", "Lauterhof", "Mühlental", "Nordhaven", "Osterfeld", "Rosenau", "Sonnenberg", "Tannenried", "Ulmenau", "Weissenstein", "Zellerode", "Adlerhorst", "Birkenau", "Dornfeld", "Eichstadt", "Friedrichsau", "Goldbach", "Heidenau", "Isarbrück", "Kronstadt", "Silberthal", "Wolfsgrund"],
	},
	{
		"id": "FRA", "topo": 85, "tamanho": 18, "ligas": ["FRA1", "FRA2"],
		"clubes": ["FC %s", "AS %s", "Olympique %s", "Racing %s", "US %s", "SC %s", "Étoile %s", "Union %s", "Stade %s"],
		"estadios": ["Stade Municipal %s", "Parc %s", "Stade %s"],
		"locais": ["Beaurivage", "Montclair", "Port-Lumière", "Valdoré", "Saint-Aubry", "Rochebrune", "Clairval", "Belleroche", "Grandpré", "Châteauvert", "Fontenelle", "Mirebois", "Lac-d'Argent", "Villeneuve-les-Pins", "Hauterive", "Bois-Joli", "Sablonne", "Cormeray", "Dambreuil", "Estival", "Florimont", "Givrac", "Lorvigny", "Marsanne-sur-Mer", "Noirétang", "Ormeville", "Pierrelune", "Quincy-le-Haut", "Rivelande", "Sérignac", "Tournelle", "Urville", "Vauxbleu", "Aigremont", "Brumeval", "Castelnoir"],
	},
]

const CORES := ["#d62828", "#1d3557", "#2a9d8f", "#f4a261", "#ffffff", "#111111", "#6a4c93", "#ffd60a", "#0077b6", "#2b9348", "#e85d04", "#7b2cbf", "#8d99ae", "#9d0208"]


func _init() -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = SEMENTE
	var pasta := ProjectSettings.globalize_path("res://dados")

	var bd := BaseDados.new()
	bd.carregar("res://dados")   # lê países, competições e nomes
	bd.equipas.clear()
	bd.jogadores.clear()
	var gerador := GeradorJogadores.new(bd.nomes, rng)
	var abreviaturas := {}

	for pais in PAISES:
		var locais: Array = pais["locais"]
		var tamanho: int = pais["tamanho"]
		assert(locais.size() == tamanho * 2, "Número de locais errado em " + pais["id"])
		for i in locais.size():
			var divisao := i / tamanho          # 0 = primeira, 1 = segunda
			var lugar := i % tamanho
			var topo: float = pais["topo"] - divisao * 25.0
			var amplitude := 26.0 if divisao == 0 else 15.0
			var fracao := pow(float(lugar) / float(tamanho - 1), 0.7)
			var e := Equipa.new()
			e.id = "%s_%02d" % [pais["id"], i + 1]
			e.pais = pais["id"]
			e.liga = pais["ligas"][divisao]
			e.cidade = locais[i]
			e.nome = pais["clubes"][rng.randi() % pais["clubes"].size()] % e.cidade
			e.estadio = pais["estadios"][rng.randi() % pais["estadios"].size()] % e.cidade
			e.abreviatura = _abreviatura(e.cidade, abreviaturas)
			e.reputacao = clampi(roundi(topo - amplitude * fracao + rng.randfn(0.0, 1.2)), 20, 95)
			e.capacidade = 3000 + int(pow(e.reputacao - 30, 2) * 22.0 / 100.0) * 100
			e.orcamento = int(pow(1.15, e.reputacao - 40) * 4.0) * 100000 + 300000
			var cor1: String = CORES[rng.randi() % CORES.size()]
			var cor2: String = CORES[rng.randi() % CORES.size()]
			while cor2 == cor1:
				cor2 = CORES[rng.randi() % CORES.size()]
			e.cores = [cor1, cor2]
			bd.equipas[e.id] = e
			for j in gerador.criar_plantel(e):
				bd.adicionar_jogador(j)

	bd.guardar(pasta)
	print("Base de dados criada: %d equipas, %d jogadores." % [bd.equipas.size(), bd.jogadores.size()])
	quit()


## Sigla de 3 letras, sem repetições.
func _abreviatura(local: String, usadas: Dictionary) -> String:
	var letras := local.replace(" ", "").replace("-", "").replace("'", "").to_upper()
	var sigla := letras.substr(0, 3)
	var k := 3
	while usadas.has(sigla) and k < letras.length():
		sigla = letras.substr(0, 2) + letras[k]
		k += 1
	var n := 2
	while usadas.has(sigla):
		sigla = letras.substr(0, 2) + str(n)
		n += 1
	usadas[sigla] = true
	return sigla
