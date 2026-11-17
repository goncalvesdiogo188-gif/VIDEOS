"""Testes do que a aplicação do telemóvel precisa: pasta de saída, pacote.json e pacote.zip."""
import datetime
import json
import os
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

from fabrica import config, pacote


class TestConfig(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.cfg = Path(self.tmp.name) / "config.json"
        for p in (mock.patch.object(config, "FICHEIRO", self.cfg),
                  mock.patch.dict(os.environ, {}, clear=False)):
            p.start()
            self.addCleanup(p.stop)
        os.environ.pop("FABRICA_OUT", None)

    def test_por_defeito(self):
        self.assertEqual(config.pasta_saida(), config.PADRAO)

    def test_definir_e_repor(self):
        alvo = Path(self.tmp.name) / "Drive" / "FABRICA"
        self.assertEqual(config.definir_saida(f'"{alvo}"'), alvo)  # aceita aspas coladas
        self.assertTrue(alvo.is_dir())
        self.assertEqual(config.pasta_saida(), alvo)
        config.repor_saida()
        self.assertEqual(config.pasta_saida(), config.PADRAO)

    def test_variavel_de_ambiente_manda(self):
        config.definir_saida(Path(self.tmp.name) / "a")
        with mock.patch.dict(os.environ, {"FABRICA_OUT": str(Path(self.tmp.name) / "b")}):
            self.assertEqual(config.pasta_saida(), Path(self.tmp.name) / "b")

    def test_pasta_impossivel(self):
        ficheiro = Path(self.tmp.name) / "ficheiro"
        ficheiro.write_text("x")
        with self.assertRaises(ValueError):
            config.definir_saida(ficheiro / "dentro")

    def test_config_estragado(self):
        self.cfg.write_text("não é json", encoding="utf-8")
        self.assertEqual(config.pasta_saida(), config.PADRAO)


class TestManifestoEZip(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.pasta = Path(self.tmp.name) / "2026-03-04_tema"
        (self.pasta / "carrossel").mkdir(parents=True)
        (self.pasta / "video.mp4").write_bytes(b"VIDEO")
        (self.pasta / "capa.png").write_bytes(b"CAPA")
        (self.pasta / "legenda.txt").write_text("L\n\n#a #b\n", encoding="utf-8")
        self.slides = []
        for i in (1, 2):
            f = self.pasta / "carrossel" / f"slide_{i:02d}.png"
            f.write_bytes(b"S%d" % i)
            self.slides.append(f)
        self.g = {"titulo": "Porque sim?", "dono": "Rui", "cenario": "cofre", "legenda": "Uma legenda.",
                  "hashtags": ["#a", "#b"], "conceito": {"id": "x", "nome": "Dor de pagar"}}

    def test_manifesto(self):
        m = pacote.escrever_manifesto(self.g, self.pasta, "tema", 73.46, datetime.datetime(2026, 3, 4, 9, 5), self.slides)
        lido = json.loads((self.pasta / "pacote.json").read_text(encoding="utf-8"))
        self.assertEqual(lido, m)
        self.assertEqual(lido["versao"], 1)
        self.assertEqual(lido["titulo"], "Porque sim?")
        self.assertEqual(lido["conceito"], "Dor de pagar")
        self.assertEqual(lido["data"], "2026-03-04")
        self.assertEqual(lido["duracao"], 73.5)
        self.assertEqual(lido["carrossel"], ["carrossel/slide_01.png", "carrossel/slide_02.png"])
        self.assertEqual(lido["texto_para_publicar"], "Uma legenda.\n\n#a #b")

    def test_manifesto_sem_campos_opcionais(self):
        m = pacote.escrever_manifesto({"titulo": "T"}, self.pasta, "t", 20, datetime.datetime(2026, 3, 4), [])
        self.assertEqual(m["conceito"], "")
        self.assertEqual(m["hashtags"], [])
        self.assertEqual(m["carrossel"], [])

    def test_zip(self):
        pacote.escrever_manifesto(self.g, self.pasta, "tema", 30, datetime.datetime(2026, 3, 4), self.slides)
        z = pacote.juntar_zip(self.pasta)
        with zipfile.ZipFile(z) as arq:
            self.assertEqual(sorted(arq.namelist()),
                             sorted(["pacote.json", "video.mp4", "capa.png", "legenda.txt",
                                     "carrossel/slide_01.png", "carrossel/slide_02.png"]))
            self.assertEqual(arq.read("video.mp4"), b"VIDEO")
            self.assertIsNone(arq.testzip())
        # refazer não acumula nem esconde o zip antigo
        pacote.juntar_zip(self.pasta)
        with zipfile.ZipFile(z) as arq:
            self.assertEqual(len(arq.namelist()), 6)


if __name__ == "__main__":
    unittest.main()
