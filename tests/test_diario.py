"""Testes do comando diário e do agendamento (sem internet, sem Claude, sem render)."""
import datetime
import sys
import tempfile
import types
import unittest
from pathlib import Path

import fabrica
from fabrica import diario, pacote


class TestDiario(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.base = Path(self.tmp.name)
        self.hoje = datetime.date(2026, 5, 6)
        self.logs = []
        self.chamadas = []
        gu = types.ModuleType("fabrica.guionista")

        class GuionistaErro(Exception):
            pass
        gu.GuionistaErro = GuionistaErro
        self.temas = iter(["tema A", "tema B", "tema C"])
        gu.escolher_temas = lambda n, evitar=(): [next(self.temas)]
        self._orig = (sys.modules.get("fabrica.guionista"), getattr(fabrica, "guionista", None),
                      pacote.criar_pacote)
        sys.modules["fabrica.guionista"] = gu
        fabrica.guionista = gu
        self.falhar = set()

        def criar(tema, base, data, log):
            self.chamadas.append(tema)
            if tema in self.falhar:
                raise pacote.PacoteErro("falhou de propósito")
            p = Path(base) / f"{data}_{tema.replace(' ', '-')}"
            p.mkdir()
            return p
        pacote.criar_pacote = criar

    def tearDown(self):
        g, a, c = self._orig
        if g is None:
            sys.modules.pop("fabrica.guionista", None)
        else:
            sys.modules["fabrica.guionista"] = g
        fabrica.guionista = a
        pacote.criar_pacote = c
        self.tmp.cleanup()

    def correr(self, **kw):
        return diario.diario(base=self.base, hoje=self.hoje, log=self.logs.append, **kw)

    def test_cria_e_regista(self):
        p = self.correr()
        self.assertTrue(p.exists())
        self.assertEqual(diario.ler_registo(self.base)["2026-05-06"], str(p))

    def test_nao_repete_no_mesmo_dia(self):
        self.correr()
        self.assertIsNone(self.correr())
        self.assertEqual(self.chamadas, ["tema A"])

    def test_forcar(self):
        self.correr()
        self.assertIsNotNone(self.correr(forcar=True))
        self.assertEqual(len(self.chamadas), 2)

    def test_tenta_outro_tema(self):
        self.falhar = {"tema A"}
        p = self.correr()
        self.assertIn("tema-B", p.name)

    def test_desiste_depois_de_duas(self):
        self.falhar = {"tema A", "tema B"}
        with self.assertRaises(pacote.PacoteErro):
            self.correr()
        self.assertEqual(diario.ler_registo(self.base), {})

    def test_ficheiro_de_registo(self):
        diario.diario(base=self.base, hoje=self.hoje)
        f = self.base / "logs" / "diario_2026-05-06.log"
        self.assertIn("tema A", f.read_text(encoding="utf-8"))


class TestAgendar(unittest.TestCase):
    def test_comando_powershell(self):
        c = diario.comando_powershell_agendar("9:05", raiz=Path("C:/Users/Diogo's/fabrica"),
                                              python="C:/Python312/python.exe")
        self.assertIn("-At '09:05'", c)
        self.assertIn("Diogo''s", c)  # plica escapada
        self.assertIn("-StartWhenAvailable", c)
        self.assertIn("-Argument '-m fabrica diario'", c)
        self.assertIn("-Execute 'C:/Python312/python.exe'", c)
        self.assertIn("'FABRICA diario'", c)

    def test_hora_invalida(self):
        for h in ("25:00", "9h", "", "12:60"):
            with self.assertRaises(ValueError):
                diario.comando_powershell_agendar(h)

    def test_cron(self):
        self.assertTrue(diario.linha_cron("07:30", raiz=Path("/x")).startswith("30 7 * * * cd /x"))


if __name__ == "__main__":
    unittest.main()
