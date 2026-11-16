extends Node3D
## Protótipo C0 do jogo ao vivo: campo, bola, um jogador e câmara de transmissão.
## O Kotlin manda as posições (sinal "estado" do plugin "FutebolPonte") e
## este script devolve o joystick virtual (método "joystick" do plugin).
## Fora do Android (no computador) corre em modo de demonstração, sem o Kotlin.

const COMPRIMENTO := 105.0
const LARGURA := 68.0
const NOME_PLUGIN := "FutebolPonte"

## Posições recebidas (metros; o centro do campo é 0,0).
var bola := Vector2.ZERO
var jogador := Vector2.ZERO
## O que o dedo está a fazer: de (-1,-1) a (1,1). Fica sempre exposto aqui.
var joystick := Vector2.ZERO

var _ponte: Object = null
var _demo := false
var _t_demo := 0.0
var _arranque_ms := 0
var _primeira_imagem := true
var _bola_no: MeshInstance3D
var _jogador_no: Node3D
var _camara: Camera3D
var _rotulo: Label
var _controlo: Control
var _toque := -1
var _centro_joystick := Vector2.ZERO
var _ponta_joystick := Vector2.ZERO
var _ultimo_envio := Vector2(9, 9)
var _fps_enviado := 0.0
var _imagens := 0
var _ficheiro_captura := ""


func _ready() -> void:
	_arranque_ms = Time.get_ticks_msec()
	var args := OS.get_cmdline_user_args()
	_demo = "--demo" in args
	var i := args.find("--captura")
	if i >= 0 and i + 1 < args.size():
		_ficheiro_captura = args[i + 1]
	_construir_campo()
	_construir_bola_e_jogador()
	_construir_camara_e_luz()
	_construir_interface()
	if Engine.has_singleton(NOME_PLUGIN):
		_ponte = Engine.get_singleton(NOME_PLUGIN)
		_ponte.connect("estado", _ao_receber_estado)
		_ponte.call("pronto")
	else:
		# Sem Kotlin (computador): o jogador anda com o joystick e a bola vai à frente.
		print("Modo de demonstração (sem ponte Kotlin).")


func _ao_receber_estado(bx: float, bz: float, jx: float, jz: float) -> void:
	bola = Vector2(bx, bz)
	jogador = Vector2(jx, jz)


func _process(delta: float) -> void:
	if _demo:
		_t_demo += delta
		joystick = Vector2(cos(_t_demo * 0.6), sin(_t_demo * 0.6)) * 0.8
		_centro_joystick = Vector2(160, 560)
		_ponta_joystick = _centro_joystick + joystick * 70.0
		_controlo.queue_redraw()
	if _ponte == null:
		_simular_localmente(delta)
	else:
		_enviar_joystick()
	_aplicar_posicoes(delta)
	if _primeira_imagem:
		_primeira_imagem = false
		var ms := Time.get_ticks_msec() - _arranque_ms
		print("Primeira imagem do Godot após %d ms." % ms)
		if _ponte != null:
			_ponte.call("arranque", ms)
	_imagens += 1
	if _ficheiro_captura != "" and _imagens == 120:
		get_viewport().get_texture().get_image().save_png(_ficheiro_captura)
		get_tree().quit()
	var fps := Engine.get_frames_per_second()
	_rotulo.text = "Godot %d im/s   joystick %.2f, %.2f" % [fps, joystick.x, joystick.y]
	if _ponte != null and absf(fps - _fps_enviado) >= 1.0:
		_fps_enviado = fps
		_ponte.call("fps", float(fps))


func _enviar_joystick() -> void:
	if joystick.distance_to(_ultimo_envio) > 0.01:
		_ultimo_envio = joystick
		_ponte.call("joystick", joystick.x, joystick.y)


func _simular_localmente(delta: float) -> void:
	var teclas := Input.get_vector("ui_left", "ui_right", "ui_up", "ui_down")
	var entrada := joystick if joystick != Vector2.ZERO else teclas
	jogador += entrada * 8.0 * delta
	jogador = jogador.clamp(Vector2(-COMPRIMENTO / 2, -LARGURA / 2), Vector2(COMPRIMENTO / 2, LARGURA / 2))
	var alvo := jogador + (entrada.normalized() * 1.6 if entrada != Vector2.ZERO else Vector2(1.6, 0))
	bola = bola.lerp(alvo, minf(1.0, 8.0 * delta))


func _aplicar_posicoes(delta: float) -> void:
	_bola_no.position = Vector3(bola.x, 0.22, bola.y)
	var anterior := _jogador_no.position
	_jogador_no.position = Vector3(jogador.x, 0.0, jogador.y)
	var velocidade := Vector3(jogador.x, 0, jogador.y) - anterior
	if velocidade.length() > 0.001:
		var alvo := _jogador_no.position + velocidade
		_jogador_no.look_at(alvo, Vector3.UP)
	# Câmara de transmissão: ao lado do campo, alta, a seguir a bola com suavidade.
	var foco := Vector3(bola.x, 0.0, bola.y)
	var pos := Vector3(foco.x * 0.8, 24.0, 36.0)
	_camara.position = _camara.position.lerp(pos, minf(1.0, 3.0 * delta))
	_camara.look_at(Vector3(_camara.position.x * 0.9 + foco.x * 0.1, 0.0, foco.z * 0.4), Vector3.UP)


# --- construção da cena (tudo em código, para o protótipo ser pequeno) ---

func _construir_campo() -> void:
	var largura_px := 1050
	var altura_px := 680
	var img := Image.create(largura_px, altura_px, false, Image.FORMAT_RGB8)
	var verde_a := Color(0.12, 0.42, 0.15)
	var verde_b := Color(0.10, 0.36, 0.12)
	var faixa := largura_px / 14
	for i in 14:
		img.fill_rect(Rect2i(i * faixa, 0, faixa + 1, altura_px), verde_a if i % 2 == 0 else verde_b)
	var linha := Color(0.95, 0.95, 0.92)
	_retangulo_linhas(img, Rect2i(5, 5, largura_px - 10, altura_px - 10), linha, 4)
	img.fill_rect(Rect2i(largura_px / 2 - 2, 5, 4, altura_px - 10), linha)
	_retangulo_linhas(img, Rect2i(5, 138, 165, altura_px - 276), linha, 4)
	_retangulo_linhas(img, Rect2i(largura_px - 170, 138, 165, altura_px - 276), linha, 4)
	_circulo(img, Vector2(largura_px / 2.0, altura_px / 2.0), 91.5, linha, 4)
	img.generate_mipmaps()
	var textura := ImageTexture.create_from_image(img)
	var material := StandardMaterial3D.new()
	material.albedo_texture = textura
	material.roughness = 1.0
	material.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
	var plano := PlaneMesh.new()
	plano.size = Vector2(COMPRIMENTO, LARGURA)
	var campo := MeshInstance3D.new()
	campo.mesh = plano
	campo.material_override = material
	add_child(campo)
	# Terreno à volta do campo (mais escuro), para a câmara não ver o vazio.
	var fora := MeshInstance3D.new()
	var plano_fora := PlaneMesh.new()
	plano_fora.size = Vector2(260, 200)
	fora.mesh = plano_fora
	var mat_fora := StandardMaterial3D.new()
	mat_fora.albedo_color = Color(0.10, 0.30, 0.14)
	fora.material_override = mat_fora
	fora.position = Vector3(0, -0.02, 0)
	add_child(fora)
	# Duas balizas simples.
	for lado in [-1, 1]:
		_baliza(lado)


func _retangulo_linhas(img: Image, r: Rect2i, cor: Color, e: int) -> void:
	img.fill_rect(Rect2i(r.position.x, r.position.y, r.size.x, e), cor)
	img.fill_rect(Rect2i(r.position.x, r.end.y - e, r.size.x, e), cor)
	img.fill_rect(Rect2i(r.position.x, r.position.y, e, r.size.y), cor)
	img.fill_rect(Rect2i(r.end.x - e, r.position.y, e, r.size.y), cor)


func _circulo(img: Image, centro: Vector2, raio: float, cor: Color, e: int) -> void:
	var passos := 360
	for i in passos:
		var a := TAU * i / passos
		var p := centro + Vector2(cos(a), sin(a)) * raio
		img.fill_rect(Rect2i(int(p.x) - e / 2, int(p.y) - e / 2, e, e), cor)


func _baliza(lado: int) -> void:
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.96, 0.96, 0.96)
	var x := lado * COMPRIMENTO / 2.0
	for dz in [-3.66, 3.66]:
		var poste := MeshInstance3D.new()
		var m := CylinderMesh.new()
		m.top_radius = 0.06
		m.bottom_radius = 0.06
		m.height = 2.44
		poste.mesh = m
		poste.material_override = mat
		poste.position = Vector3(x, 1.22, dz)
		add_child(poste)
	var barra := MeshInstance3D.new()
	var mb := BoxMesh.new()
	mb.size = Vector3(0.12, 0.12, 7.44)
	barra.mesh = mb
	barra.material_override = mat
	barra.position = Vector3(x, 2.44, 0)
	add_child(barra)


func _construir_bola_e_jogador() -> void:
	_bola_no = MeshInstance3D.new()
	var esfera := SphereMesh.new()
	esfera.radius = 0.22
	esfera.height = 0.44
	_bola_no.mesh = esfera
	var mat_bola := StandardMaterial3D.new()
	mat_bola.albedo_color = Color(1, 1, 1)
	mat_bola.roughness = 0.4
	_bola_no.material_override = mat_bola
	add_child(_bola_no)

	_jogador_no = Node3D.new()
	add_child(_jogador_no)
	var corpo := MeshInstance3D.new()
	var capsula := CapsuleMesh.new()
	capsula.radius = 0.32
	capsula.height = 1.5
	corpo.mesh = capsula
	var camisola := StandardMaterial3D.new()
	camisola.albedo_color = Color(0.85, 0.10, 0.12)
	corpo.material_override = camisola
	corpo.position = Vector3(0, 0.95, 0)
	_jogador_no.add_child(corpo)
	var cabeca := MeshInstance3D.new()
	var esf := SphereMesh.new()
	esf.radius = 0.22
	esf.height = 0.44
	cabeca.mesh = esf
	var pele := StandardMaterial3D.new()
	pele.albedo_color = Color(0.93, 0.74, 0.60)
	cabeca.material_override = pele
	cabeca.position = Vector3(0, 1.95, 0)
	_jogador_no.add_child(cabeca)
	# Um "nariz" para se ver para onde o jogador olha (a frente é -Z do nó).
	var nariz := MeshInstance3D.new()
	var caixa := BoxMesh.new()
	caixa.size = Vector3(0.14, 0.14, 0.3)
	nariz.mesh = caixa
	nariz.material_override = pele
	nariz.position = Vector3(0, 1.95, -0.25)
	_jogador_no.add_child(nariz)


func _construir_camara_e_luz() -> void:
	_camara = Camera3D.new()
	_camara.fov = 50.0
	_camara.far = 400.0
	_camara.position = Vector3(0, 26, 40)
	add_child(_camara)
	_camara.look_at(Vector3.ZERO, Vector3.UP)
	_camara.current = true
	var sol := DirectionalLight3D.new()
	sol.rotation_degrees = Vector3(-55, -35, 0)
	sol.shadow_enabled = true
	sol.light_energy = 0.8
	sol.directional_shadow_max_distance = 80.0
	add_child(sol)
	var ambiente := Environment.new()
	ambiente.background_mode = Environment.BG_COLOR
	ambiente.background_color = Color(0.45, 0.62, 0.85)
	ambiente.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	ambiente.ambient_light_color = Color(0.75, 0.80, 0.90)
	ambiente.ambient_light_energy = 0.35
	var mundo := WorldEnvironment.new()
	mundo.environment = ambiente
	add_child(mundo)


func _construir_interface() -> void:
	var camada := CanvasLayer.new()
	add_child(camada)
	_controlo = Control.new()
	_controlo.set_anchors_preset(Control.PRESET_FULL_RECT)
	_controlo.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_controlo.draw.connect(_desenhar_joystick)
	camada.add_child(_controlo)
	_rotulo = Label.new()
	_rotulo.position = Vector2(16, 10)
	_rotulo.add_theme_font_size_override("font_size", 22)
	_rotulo.add_theme_color_override("font_color", Color.WHITE)
	_rotulo.add_theme_color_override("font_outline_color", Color.BLACK)
	_rotulo.add_theme_constant_override("outline_size", 6)
	camada.add_child(_rotulo)


# --- joystick virtual: toca em qualquer ponto do ecrã e arrasta ---

func _input(evento: InputEvent) -> void:
	if _demo:
		return
	if evento is InputEventScreenTouch:
		if evento.pressed and _toque == -1:
			_toque = evento.index
			_centro_joystick = evento.position
			_ponta_joystick = evento.position
		elif not evento.pressed and evento.index == _toque:
			_soltar()
	elif evento is InputEventScreenDrag and evento.index == _toque:
		_arrastar(evento.position)
	elif evento is InputEventMouseButton and evento.button_index == MOUSE_BUTTON_LEFT:
		if evento.pressed:
			_toque = 0
			_centro_joystick = evento.position
			_ponta_joystick = evento.position
		else:
			_soltar()
	elif evento is InputEventMouseMotion and _toque == 0:
		_arrastar(evento.position)


func _arrastar(pos: Vector2) -> void:
	var raio := 70.0
	var d := pos - _centro_joystick
	if d.length() > raio:
		d = d.normalized() * raio
	_ponta_joystick = _centro_joystick + d
	joystick = d / raio
	_controlo.queue_redraw()


func _soltar() -> void:
	_toque = -1
	joystick = Vector2.ZERO
	_controlo.queue_redraw()


func _desenhar_joystick() -> void:
	if _toque == -1 and not _demo:
		return
	_controlo.draw_circle(_centro_joystick, 70.0, Color(1, 1, 1, 0.18))
	_controlo.draw_arc(_centro_joystick, 70.0, 0.0, TAU, 48, Color(1, 1, 1, 0.6), 3.0)
	_controlo.draw_circle(_ponta_joystick, 30.0, Color(1, 1, 1, 0.55))
