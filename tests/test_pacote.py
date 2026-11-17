"""Testes do pacote (com guionista e validador falsos, sem renderizar nada)."""
import copy
import os
os.environ["FABRICA_SEM_VOZ"] = "1"  # nos testes não se usa a internet para a voz
import datetime
import json
import sys
import tempfile
import types
import unittest
from pathlib import Path

import fabrica
from fabrica import pacote

GUIAO = {
    "tema": "Compras por impulso", "titulo": "Titulo", "cenario": "sala",
    "falas": [{"quem": "neurao", "texto": "Ola mundo."},
              {"quem": "ze", "texto": "Adeus mundo, ate logo."}],
    "carrossel": [], "legenda": "Uma legenda.", "hashtags": ["#a", "#b", "#c", "#d", "#e"],
}


class GuionistaErro(Exception):
    pass


def fazer_falsos(falhas=0, mudancas=None, corrigir_falha=False, temas=None, tema_mau=None):
    """Cria guionista e validar falsos. 'falhas' = nº de validações que falham."""
    estado = {"validacoes": 0, "corrigir": 0, "escrever": 0}
    gu = types.ModuleType("fabrica.guionista")
    va = types.ModuleType("fabrica.validar")
    gu.GuionistaErro = GuionistaErro

    def escrever(tema):
        estado["escrever"] += 1
        if tema == tema_mau:
            raise GuionistaErro("sem rede")
        g = copy.deepcopy(GUIAO)
        g["tema"] = tema
        return g

    def corrigir(g, erros):
        estado["corrigir"] += 1
        if corrigir_falha:
            raise GuionistaErro("Claude indisponivel")
        return g

    def rever(g):
        if mudancas:
            g = copy.deepcopy(g)
            g["titulo"] = "Revisto"
            return g, list(mudancas)
        return g, []

    def validar(g):
        estado["validacoes"] += 1
        if estado["validacoes"] <= falhas:
            return ["erro de teste"]
        return []

    gu.escrever_guiao = escrever
    gu.corrigir_guiao = corrigir
    gu.rever_texto = rever
    gu.escolher_temas = lambda n, evitar=(): (temas or [f"tema {i}" for i in range(n)])[:n]
    va.validar = validar
    va.duracao_estimada = lambda g: 20.0
    return gu, va, estado


class Base(unittest.TestCase):
    def setUp(self):
        self._guardado = {k: sys.modules.get(k) for k in ("fabrica.guionista", "fabrica.validar")}
        self._attrs = {k: getattr(fabrica, k, None) for k in ("guionista", "validar")}
        self.tmp = tempfile.TemporaryDirectory()
        self.base = Path(self.tmp.name)
        self.logs = []
        self.log = self.logs.append

    def tearDown(self):
        for k, v in self._guardado.items():
            if v is None:
                sys.modules.pop(k, None)
            else:
                sys.modules[k] = v
        for k, v in self._attrs.items():
            if v is None:
                if hasattr(fabrica, k):
                    delattr(fabrica, k)
            else:
                setattr(fabrica, k, v)
        self.tmp.cleanup()

    def instalar(self, **kw):
        gu, va, estado = fazer_falsos(**kw)
        sys.modules["fabrica.guionista"] = gu
        sys.modules["fabrica.validar"] = va
        fabrica.guionista = gu
        fabrica.validar = va
        return estado

    def falsos_render(self):
        from fabrica import video, carrossel
        orig = (video.gerar_video, carrossel.gerar_carrossel, video.frame_svg)
        self.addCleanup(lambda: (setattr(video, "gerar_video", orig[0]),
                                 setattr(carrossel, "gerar_carrossel", orig[1]),
                                 setattr(video, "frame_svg", orig[2])))
        video.gerar_video = lambda g, saida, plano=None, duracao=None, audio=None: (
            Path(saida).write_bytes(b"mp4") or (saida, duracao))

        def car(g, pasta):
            Path(pasta).mkdir(parents=True, exist_ok=True)
            f = Path(pasta) / "slide_01.png"
            f.write_bytes(b"png")
            return [f]
        carrossel.gerar_carrossel = car
        video.frame_svg = lambda g, plano, duracao, t: (
            '<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"/>')


class TestNomes(Base):
    def test_slug(self):
        self.assertEqual(pacote.slug("Compras por impulso!"), "compras-por-impulso")
        self.assertEqual(pacote.slug("Ação é coração"), "acao-e-coracao")
        self.assertEqual(pacote.slug("!!!"), "video")
        s = pacote.slug("palavra " * 20)
        self.assertLessEqual(len(s), 40)
        self.assertFalse(s.endswith("-"))

    def test_pasta_sufixos(self):
        d = datetime.date(2026, 1, 2)
        p1 = pacote.pasta_pacote("Olá", d, self.base)
        self.assertEqual(p1.name, "2026-01-02_ola")
        p1.mkdir()
        p2 = pacote.pasta_pacote("Olá", d, self.base)
        self.assertEqual(p2.name, "2026-01-02_ola_2")
        p2.mkdir()
        self.assertEqual(pacote.pasta_pacote("Olá", d, self.base).name, "2026-01-02_ola_3")


class TestHistorico(Base):
    def test_ler_escrever(self):
        self.assertEqual(pacote.ler_historico(self.base), [])
        pacote.registar_historico(self.base, {"tema": "ação"})
        pacote.registar_historico(self.base, {"tema": "b"})
        h = pacote.ler_historico(self.base)
        self.assertEqual([x["tema"] for x in h], ["ação", "b"])
        self.assertIn("ação", (self.base / "historico.json").read_text(encoding="utf-8"))

    def test_historico_estragado(self):
        (self.base / "historico.json").write_text("lixo", encoding="utf-8")
        self.assertEqual(pacote.ler_historico(self.base), [])


class TestObterGuiao(Base):
    def test_sem_erros(self):
        est = self.instalar()
        g, corr = pacote.obter_guiao_valido("tema x", log=self.log)
        self.assertEqual(g["tema"], "tema x")
        self.assertEqual(corr, [])
        self.assertEqual(est["corrigir"], 0)

    def test_falha_duas_vezes(self):
        est = self.instalar(falhas=2)
        g, corr = pacote.obter_guiao_valido("t", log=self.log)
        self.assertEqual(est["corrigir"], 2)
        self.assertEqual(len(corr), 2)
        self.assertTrue(any("erro de teste" in l for l in self.logs))

    def test_max_tentativas(self):
        est = self.instalar(falhas=99)
        with self.assertRaises(pacote.PacoteErro) as c:
            pacote.obter_guiao_valido("t", max_tentativas=3, log=self.log)
        self.assertIn("erro de teste", str(c.exception))
        self.assertEqual(est["corrigir"], 2)
        self.assertIsNotNone(c.exception.guiao)

    def test_rever_texto_registado(self):
        self.instalar(mudancas=["mudei 'ola' para 'olá'"])
        g, corr = pacote.obter_guiao_valido("t", log=self.log)
        self.assertEqual(g["titulo"], "Revisto")
        self.assertTrue(any("olá" in c for c in corr))
        self.assertTrue(any("olá" in l for l in self.logs))

    def test_rever_texto_invalido_volta_ao_anterior(self):
        # 1.ª validação ok, a revisão fica inválida e a correção falha
        gu, va, est = fazer_falsos(mudancas=["x"], corrigir_falha=True)
        n = {"i": 0}

        def validar(g):
            n["i"] += 1
            return [] if n["i"] == 1 else ["estragou"]
        va.validar = validar
        sys.modules["fabrica.guionista"], sys.modules["fabrica.validar"] = gu, va
        fabrica.guionista, fabrica.validar = gu, va
        g, corr = pacote.obter_guiao_valido("t", log=self.log)
        self.assertNotEqual(g["titulo"], "Revisto")

    def test_guiao_dado_sem_claude(self):
        self.instalar(falhas=1, corrigir_falha=True)
        with self.assertRaises(pacote.PacoteErro) as c:
            pacote.obter_guiao_valido(guiao=copy.deepcopy(GUIAO), log=self.log)
        self.assertIn("erro de teste", str(c.exception))


class TestCriarPacote(Base):
    def test_pacote_completo(self):
        self.instalar()
        self.falsos_render()
        p = pacote.criar_pacote("Compras por impulso", base=self.base,
                                data=datetime.date(2026, 3, 4), log=self.log)
        self.assertEqual(p.name, "2026-03-04_compras-por-impulso")
        for f in ("guiao.json", "video.mp4", "capa.png", "legenda.txt", "info.txt",
                  "carrossel/slide_01.png"):
            self.assertTrue((p / f).exists(), f)
        leg = (p / "legenda.txt").read_text(encoding="utf-8")
        self.assertEqual(leg, "Uma legenda.\n\n#a #b #c #d #e\n")
        self.assertIn("Número de falas: 2", (p / "info.txt").read_text(encoding="utf-8"))
        self.assertEqual(pacote.ler_historico(self.base)[0]["tema"], "Compras por impulso")
        self.assertTrue(any(l.startswith("1/5") for l in self.logs))
        self.assertTrue(any(l.startswith("5/5") for l in self.logs))

    def test_guiao_rejeitado(self):
        self.instalar(falhas=99)
        self.falsos_render()
        with self.assertRaises(pacote.PacoteErro):
            pacote.criar_pacote("tema", base=self.base, data=datetime.date(2026, 3, 4), log=self.log)
        pasta = self.base / "2026-03-04_tema"
        self.assertTrue((pasta / "guiao_rejeitado.json").exists())
        self.assertFalse((pasta / "video.mp4").exists())
        self.assertEqual(pacote.ler_historico(self.base), [])

    def test_guiao_ficheiro(self):
        self.instalar()
        self.falsos_render()
        f = self.base / "g.json"
        f.write_text(json.dumps(GUIAO, ensure_ascii=False), encoding="utf-8")
        p = pacote.criar_pacote(guiao_ficheiro=str(f), base=self.base / "out", log=self.log)
        self.assertTrue((p / "video.mp4").exists())
        self.assertIn("compras-por-impulso", p.name)


class TestRefazer(Base):
    def test_refazer_usa_guiao_da_pasta(self):
        self.instalar()
        self.falsos_render()
        p = pacote.criar_pacote("Compras por impulso", base=self.base, log=self.log)
        (p / "video.mp4").unlink()
        pacote.refazer_pacote(p, log=self.log)
        self.assertTrue((p / "video.mp4").exists())
        self.assertIn("refeito", (p / "info.txt").read_text(encoding="utf-8"))
        self.assertEqual(len(pacote.ler_historico(self.base)), 1)  # não regista outra vez

    def test_refazer_sem_guiao(self):
        self.instalar()
        with self.assertRaises(pacote.PacoteErro):
            pacote.refazer_pacote(self.base / "nao_existe", log=self.log)


class TestSemana(Base):
    def test_continua_apos_falha(self):
        self.instalar(temas=["a", "b", "c"], tema_mau="b")
        self.falsos_render()
        pastas = pacote.semana(3, base=self.base, log=self.log)
        self.assertEqual(len(pastas), 2)
        resumo = "\n".join(self.logs)
        self.assertIn("ERRO", resumo)
        self.assertIn("2 de 3", resumo)

    def test_evita_temas_do_historico(self):
        gu, va, _ = fazer_falsos(temas=["a"])
        visto = {}
        gu.escolher_temas = lambda n, evitar=(): visto.update(e=list(evitar)) or ["a"]
        sys.modules["fabrica.guionista"], sys.modules["fabrica.validar"] = gu, va
        fabrica.guionista, fabrica.validar = gu, va
        self.falsos_render()
        pacote.registar_historico(self.base, {"tema": "antigo"})
        pacote.semana(1, base=self.base, log=self.log)
        self.assertEqual(visto["e"], ["antigo"])


if __name__ == "__main__":
    unittest.main()
