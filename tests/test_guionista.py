import os
import sys
import unittest
from pathlib import Path
from unittest import mock

from fabrica import guionista as g

FAKE = Path(__file__).with_name("fake_claude.py")


class Base(unittest.TestCase):
    def setUp(self):
        p = mock.patch.dict(os.environ, {"FABRICA_CLAUDE": f'"{sys.executable}" "{FAKE}"',
                                                "FABRICA_GUIAO_SIMPLES": "1"})
        p.start()
        self.addCleanup(p.stop)


class TestExtrair(unittest.TestCase):
    def test_puro(self):
        self.assertEqual(g.extrair_json('{"a": 1}'), {"a": 1})

    def test_fences_e_texto(self):
        self.assertEqual(g.extrair_json('Olá\n```json\n{"a": [1, 2]}\n```\nfim'), {"a": [1, 2]})

    def test_texto_a_volta_sem_fences(self):
        self.assertEqual(g.extrair_json('Aqui: ["x", "y"] pronto'), ["x", "y"])

    def test_chavetas_no_texto(self):
        self.assertEqual(g.extrair_json('usa {isto} e {"a": {"b": 2}} ok'), {"a": {"b": 2}})

    def test_erro(self):
        for t in ("", "sem json aqui", "{partido"):
            with self.assertRaises(g.GuionistaErro):
                g.extrair_json(t)


class TestComando(unittest.TestCase):
    def test_padrao(self):
        with mock.patch.dict(os.environ):
            os.environ.pop("FABRICA_CLAUDE", None)
            c = g.comando_claude()
        self.assertEqual(c[1:], ["-p", "--tools", ""])

    def test_env(self):
        with mock.patch.dict(os.environ, {"FABRICA_CLAUDE": "python3 tests/fake_claude.py"}):
            self.assertEqual(g.comando_claude(), ["python3", "tests/fake_claude.py"])


class TestClaude(Base):
    def test_escrever_guiao(self):
        r = g.escrever_guiao("procrastinação")
        self.assertEqual(r["tema"], "procrastinação")
        self.assertEqual(r["falas"][-1]["quem"], "ze")
        self.assertEqual(len(r["hashtags"]), 5)

    def test_corrigir(self):
        r = g.corrigir_guiao({"tema": "x", "falas": []}, ["faltam falas"])
        self.assertTrue(r["falas"])
        self.assertEqual(r["tema"], "x")

    def test_rever_com_alteracoes(self):
        orig = g.escrever_guiao("t")
        orig["titulo"] = "Porque adiaz tudo?"
        novo, alt = g.rever_texto(orig)
        self.assertEqual(alt, ["adiaz -> adias"])
        self.assertEqual(novo["titulo"], "Porque adias tudo?")

    def test_rever_sem_alteracoes(self):
        orig = {"titulo": "SEMMUDANCAS", "falas": []}
        novo, alt = g.rever_texto(orig)
        self.assertEqual(alt, [])
        self.assertEqual(novo, orig)

    def test_temas_dedupe(self):
        self.assertEqual(g.escolher_temas(3), ["Adiar tarefas", "Sono e telefone", "Dinheiro no fim do mês"])
        with self.assertRaises(g.GuionistaErro):
            g.escolher_temas(3, evitar=["adiar tarefas"])

    def test_temas_menos(self):
        self.assertEqual(len(g.escolher_temas(2)), 2)

    def test_falha(self):
        with self.assertRaises(g.GuionistaErro) as cm:
            g.perguntar_claude("FALHA", timeout=20)
        self.assertIn("falhou", str(cm.exception))

    def test_retry(self):
        with mock.patch.object(g, "_uma_vez", side_effect=[g.GuionistaErro("x"), "ok"]) as m:
            self.assertEqual(g.perguntar_claude("a"), "ok")
            self.assertEqual(m.call_count, 2)

    def test_nao_instalado(self):
        with mock.patch.dict(os.environ, {"FABRICA_CLAUDE": "/nao/existe/claude"}):
            with self.assertRaises(g.GuionistaErro) as cm:
                g.perguntar_claude("a")
        self.assertIn("não está instalado", str(cm.exception))


if __name__ == "__main__":
    unittest.main()
