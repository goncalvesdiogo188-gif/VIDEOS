"""Testes da escrita em vários passos, do banco de conceitos e da Janela do Dono."""
import copy
import json
import os
import sys
import unittest
from pathlib import Path
from unittest import mock

from fabrica import historia as H
from fabrica import janela as J
from fabrica import pacote, validar as V, video
from fabrica.cenarios import ZONAS
from fabrica.render import svg_para_png

FAKE = Path(__file__).resolve().parent / "fake_claude.py"


class TestBanco(unittest.TestCase):
    def test_estrutura(self):
        ids = set()
        for c in H.carregar_conceitos():
            for campo in ("id", "nome", "explicacao", "certeza", "cuidado", "situacoes", "licoes", "zona", "temas"):
                self.assertIn(campo, c, c.get("id"))
            self.assertNotIn(c["id"], ids)
            ids.add(c["id"])
            self.assertIn(c["zona"], ZONAS)
            self.assertIn(c["certeza"], ("forte", "moderada"))
            self.assertGreaterEqual(len(c["temas"]), 1)
            self.assertLessEqual(len(c["nome"]), 40)
        self.assertGreaterEqual(len(ids), 20)

    def test_sem_termos_proibidos(self):
        erros = []
        for c in H.carregar_conceitos():
            for campo in ("nome", "explicacao", "situacoes", "licoes", "temas"):
                v = c[campo] if isinstance(c[campo], list) else [c[campo]]
                for x in v:
                    V._proibidos(x, f"{c['id']}.{campo}", erros)
        self.assertEqual(erros, [])

    def test_conceito_do_tema(self):
        self.assertEqual(H.conceito_do_tema("PORQUE deixas tudo para amanhã")["id"], "adiar_e_emocao")
        self.assertIsNone(H.conceito_do_tema("tema que não existe"))

    def test_temas_do_banco(self):
        t = H.temas_do_banco(8, semente=3)
        self.assertEqual(len(t), 8)
        self.assertEqual(len(set(t)), 8)
        zonas = {H.conceito_do_tema(x)["zona"] for x in t}
        self.assertGreaterEqual(len(zonas), 5)  # variedade de cenários

    def test_evitar_tema_e_conceito(self):
        todos = H.temas_do_banco(99, semente=1)
        self.assertEqual(len(todos), len(H.carregar_conceitos()))
        sem = H.temas_do_banco(99, evitar=["Porque deixas tudo para amanhã?", "conceito:dor_de_pagar"], semente=1)
        ids = {H.conceito_do_tema(x)["id"] for x in sem}
        self.assertNotIn("adiar_e_emocao", ids)
        self.assertNotIn("dor_de_pagar", ids)
        self.assertEqual(len(sem), len(todos) - 2)


class TestExemplosDeOuro(unittest.TestCase):
    def test_todos_validos(self):
        ex = H.carregar_exemplos()
        self.assertGreaterEqual(len(ex), 4)
        for g in ex:
            with self.subTest(g["titulo"]):
                self.assertEqual(V.validar(g), [])
                self.assertIsNotNone(H.conceito_por_id(g["conceito"]["id"]))
                self.assertGreaterEqual(sum(1 for f in g["falas"] if f.get("cena")), 4)
                self.assertEqual(g["falas"][-1]["quem"], "ze")
                self.assertGreaterEqual(video.planear(g)[1], 40)

    def test_exemplo_desenha_um_fotograma(self):
        g = H.carregar_exemplos()[0]
        plano, dur = video.planear(g)
        t = plano[4]["inicio"] + 0.8
        svg = video.frame_svg(g, plano, dur, t)
        self.assertIn("DONO", svg)
        self.assertIn("CONCEITO", svg)
        self.assertTrue(svg_para_png(svg).startswith(b"\x89PNG"))


class TestJanela(unittest.TestCase):
    def test_todos_os_icones(self):
        for i in J.ICONES:
            self.assertIn("<", J.desenha_icone(i, 1.3), i)
            svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300">{J.janela({"icone": i, "hora": "22:40", "texto": "Um texto de teste."}, "Marta", 2.0, 1.0)}</svg>'
            self.assertTrue(svg_para_png(svg).startswith(b"\x89PNG"))

    def test_invisivel_antes_de_comecar(self):
        self.assertEqual(J.janela({"icone": "cama", "texto": "x"}, "Rui", 0.5, 1.0), "")

    def test_cena_em(self):
        plano = [dict(inicio=1.0, fim=2.0, cena={"icone": "cama", "texto": "a"}),
                 dict(inicio=3.0, fim=4.0),
                 dict(inicio=5.0, fim=6.0, cena={"icone": "papeis", "texto": "b"})]
        self.assertEqual(J.cena_em(plano, 0.5)[0], None)
        self.assertEqual(J.cena_em(plano, 3.5)[0]["texto"], "a")
        self.assertEqual(J.cena_em(plano, 5.0)[0]["texto"], "b")

    def test_conceito_em(self):
        plano = [dict(inicio=1.0, fim=2.0), dict(inicio=3.0, fim=4.0, mostra_conceito=True)]
        g = {"conceito": {"nome": "Dor de pagar"}}
        self.assertEqual(J.conceito_em(plano, g, 3.5)[0], "Dor de pagar")
        self.assertEqual(J.conceito_em(plano, g, 8.0)[0], None)
        self.assertEqual(J.conceito_em(plano, {}, 3.5)[0], None)


class TestValidarHistoria(unittest.TestCase):
    def setUp(self):
        self.g = copy.deepcopy(H.carregar_exemplos()[0])

    def erro(self, g, trecho):
        e = V.validar(g)
        self.assertTrue(any(trecho in x for x in e), f"{trecho!r} não está em {e}")

    def test_primeira_fala_sem_cena(self):
        del self.g["falas"][0]["cena"]
        self.erro(self.g, "primeira fala tem de ter uma")

    def test_poucas_cenas(self):
        for f in self.g["falas"][1:]:
            f.pop("cena", None)
        self.erro(self.g, "pelo menos 3 cenas")

    def test_sem_mostra_conceito(self):
        for f in self.g["falas"]:
            f.pop("mostra_conceito", None)
        self.erro(self.g, "mostra_conceito")

    def test_dois_mostra_conceito(self):
        for f in self.g["falas"][:2]:
            f["mostra_conceito"] = True
        self.erro(self.g, "Só uma fala")

    def test_icone_e_texto_da_cena(self):
        self.g["falas"][0]["cena"]["icone"] = "foguetao"
        self.erro(self.g, "icone da cena")
        self.g = copy.deepcopy(H.carregar_exemplos()[0])
        self.g["falas"][0]["cena"]["texto"] = "x" * 41
        self.erro(self.g, "41 caracteres")

    def test_dono_e_conceito(self):
        self.g["dono"] = ""
        self.erro(self.g, "dono")
        self.g = copy.deepcopy(H.carregar_exemplos()[0])
        self.g["conceito"] = {"id": "x"}
        self.erro(self.g, "conceito")

    def test_formato_antigo_continua_valido(self):
        g = json.loads((Path(__file__).resolve().parent.parent / "guioes" / "exemplo_compras_impulso.json").read_text(encoding="utf-8"))
        self.assertEqual(V.validar(g), [])


class TestPipeline(unittest.TestCase):
    def setUp(self):
        p = mock.patch.dict(os.environ, {"FABRICA_CLAUDE": f'"{sys.executable}" "{FAKE}"'})
        p.start()
        self.addCleanup(p.stop)
        self.logs = []

    def test_ciclo_completo_com_reescrita(self):
        g = H.escrever_guiao_rico("procrastinação", log=self.logs.append, semente=1)
        self.assertIn("v2", g["titulo"])  # a 1.ª versão teve nota 3 e foi reescrita
        self.assertEqual(g["conceito"]["id"], "adiar_e_emocao")
        self.assertTrue(g["dono"])
        self.assertEqual(g["qualidade"]["media"], 5.0)
        self.assertIn("licao", g["ficha"])
        self.assertTrue(any("reescrever" in x for x in self.logs))
        self.assertEqual(sum(1 for f in g["falas"] if f.get("mostra_conceito")), 1)

    def test_sem_reescrita_fica_com_a_melhor(self):
        g = H.escrever_guiao_rico("procrastinação", log=self.logs.append, max_rondas=0, semente=1)
        self.assertIn("v1", g["titulo"])
        self.assertEqual(g["qualidade"]["media"], 3.0)

    def test_tema_do_banco_nao_pergunta_o_conceito(self):
        with mock.patch.object(H.GU, "perguntar_claude", side_effect=AssertionError("não devia perguntar")):
            self.assertEqual(H.escolher_conceito("Porque deixas tudo para amanhã?")["id"], "adiar_e_emocao")

    def test_guionista_usa_o_pipeline(self):
        from fabrica import guionista
        g = guionista.escrever_guiao("procrastinação")
        self.assertIn("qualidade", g)

    def test_arrumar(self):
        c = H.conceito_por_id("dor_de_pagar")
        g = H.arrumar({"falas": [{"texto": "a"}, {"texto": "b"}, {"texto": "Atenção, ciência!"}]}, "t", c, "Rui")
        self.assertEqual(g["conceito"], {"id": "dor_de_pagar", "nome": "Dor de pagar"})
        self.assertEqual(g["dono"], "Rui")
        self.assertTrue(g["falas"][2].get("mostra_conceito"))
        g = H.arrumar({"falas": [{"texto": "a", "mostra_conceito": True}, {"texto": "b", "mostra_conceito": True}]}, "t", c, "Rui")
        self.assertEqual(sum(1 for f in g["falas"] if f.get("mostra_conceito")), 1)

    def test_critica_limita_notas(self):
        c = H.conceito_por_id("dor_de_pagar")
        with mock.patch.object(H.GU, "perguntar_claude", return_value='{"notas": {"coerencia": 9, "humor": "x"}, "problemas": ["a"]}'):
            r = H.criticar({"falas": []}, c)
        self.assertEqual(r["notas"]["coerencia"], 5)
        self.assertEqual(r["notas"]["humor"], 3)
        self.assertFalse(H.passou(r))


class TestPacoteHistoria(unittest.TestCase):
    def test_linhas_e_historico(self):
        g = {"dono": "Rui", "conceito": {"id": "dor_de_pagar", "nome": "Dor de pagar"},
             "qualidade": {"notas": {"humor": 4}, "media": 4.5}}
        linhas = pacote._linhas_historia(g)
        self.assertIn("Dono: Rui", linhas)
        self.assertIn("Conceito: Dor de pagar", linhas)
        self.assertTrue(any("média 4.5" in x for x in linhas))
        self.assertEqual(pacote._linhas_historia({}), [])

    def test_historico_para_evitar(self):
        import tempfile
        with tempfile.TemporaryDirectory() as d:
            pacote.registar_historico(Path(d), {"tema": "A", "conceito": "dor_de_pagar"})
            pacote.registar_historico(Path(d), {"tema": "B"})
            self.assertEqual(pacote.historico_para_evitar(Path(d)), ["A", "conceito:dor_de_pagar", "B"])


if __name__ == "__main__":
    unittest.main()
