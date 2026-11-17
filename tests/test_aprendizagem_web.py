"""Testes da aprendizagem (regras que a fábrica tira dos vídeos) e da página web local."""
import http.client
import json
import os
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path
from unittest import mock

from fabrica import aprendizagem as A
from fabrica import web

FAKE = Path(__file__).resolve().parent / "fake_claude.py"
GUIAO = {"tema": "adiar", "titulo": "Porque adias?", "dono": "Marta", "cenario": "comando",
         "conceito": {"id": "adiar_e_emocao", "nome": "Adiar é fugir de uma emoção"},
         "falas": [{"quem": "neurao", "texto": "Marta adia tudo."}, {"quem": "ze", "texto": "Começa por dois minutos."}],
         "legenda": "Adias? Segue para mais!", "hashtags": ["#a"]}


class Base(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.base = Path(self.tmp.name)
        p = mock.patch.dict(os.environ, {"FABRICA_CLAUDE": f'"{sys.executable}" "{FAKE}"', "FABRICA_OUT": str(self.base)})
        p.start()
        self.addCleanup(p.stop)
        self.addCleanup(self.tmp.cleanup)

    def pacote(self, nome="2026-01-01_adiar"):
        p = self.base / nome
        p.mkdir()
        (p / "guiao.json").write_text(json.dumps(GUIAO), encoding="utf-8")
        (p / "info.txt").write_text("Duração: 30 s", encoding="utf-8")
        (p / "video.mp4").write_bytes(b"0123456789" * 10)
        return p


class TestAprendizagem(Base):
    def test_sem_regras_bloco_vazio(self):
        self.assertEqual(A.bloco_para_prompt(self.base), "")

    def test_analise_guarda_licoes_e_destila(self):
        p = self.pacote()
        r = A.analisar_pacote(p, base=self.base, log=lambda m: None)
        self.assertEqual(r["analise"]["nota"], 7)
        d = A.carregar(self.base)
        self.assertEqual(len(d["licoes"]), 2)
        self.assertTrue(d["regras"])  # destilou porque ainda não havia regras
        self.assertIn("Evita lições vagas", A.bloco_para_prompt(self.base))

    def test_licao_repetida_soma(self):
        p = self.pacote()
        A.analisar_pacote(p, base=self.base, log=lambda m: None)
        A.analisar_pacote(p, base=self.base, log=lambda m: None)
        d = A.carregar(self.base)
        self.assertEqual(len(d["licoes"]), 2)
        self.assertEqual(d["licoes"][0]["vezes"], 2)

    def test_feedback(self):
        p = self.pacote()
        A.registar_feedback(p, nota=4, comentario="A voz do Zé está rápida", publicado=True, visualizacoes="1200", base=self.base)
        d = A.carregar(self.base)
        mao = d["registos"][0]["diogo"]
        self.assertEqual((mao["nota"], mao["publicado"], mao["visualizacoes"]), (4, True, 1200))
        self.assertTrue(any(l["origem"] == "diogo" for l in d["licoes"]))
        with self.assertRaises(ValueError):
            A.registar_feedback(p, nota=9, base=self.base)

    def test_prompts_levam_as_regras(self):
        from fabrica import historia as H
        p = self.pacote()
        A.analisar_pacote(p, base=self.base, log=lambda m: None)
        self.assertIn("APRENDIZAGENS DESTA CONTA", H._aprendizagens())

    def test_ficheiro_estragado_nao_rebenta(self):
        (self.base / A.FICHEIRO).write_text("{{{", encoding="utf-8")
        self.assertEqual(A.carregar(self.base)["regras"], [])


class TestWeb(Base):
    def setUp(self):
        super().setUp()
        self.srv = web.Servidor(("127.0.0.1", 0))
        self.porta = self.srv.server_address[1]
        threading.Thread(target=self.srv.serve_forever, daemon=True).start()
        self.addCleanup(self.srv.server_close)
        self.addCleanup(self.srv.shutdown)

    def pedir(self, metodo, url, corpo=None, cabecalhos=None, host=None):
        c = http.client.HTTPConnection("127.0.0.1", self.porta, timeout=10)
        h = dict(cabecalhos or {})
        if host:
            h["Host"] = host
        if corpo is not None:
            h["Content-Type"] = "application/json"
            corpo = json.dumps(corpo)
        c.request(metodo, url, corpo, h)
        r = c.getresponse()
        dados = r.read()
        c.close()
        return r.status, dict(r.getheaders()), dados

    def post(self, url, corpo):
        s, _, d = self.pedir("POST", url, corpo, {"X-Fabrica": "1"})
        return s, json.loads(d)

    def test_pagina_e_estado(self):
        s, _, d = self.pedir("GET", "/")
        self.assertEqual(s, 200)
        self.assertIn(b"FABRICA", d.upper())
        s, _, d = self.pedir("GET", "/api/estado")
        self.assertEqual(json.loads(d)["saida"], str(self.base))

    def test_host_estranho_recusado(self):
        s, _, _ = self.pedir("GET", "/api/estado", host="mau.exemplo.com")
        self.assertEqual(s, 403)

    def test_post_sem_cabecalho_recusado(self):
        s, _, _ = self.pedir("POST", "/api/feedback", {"pacote": "x"})
        self.assertEqual(s, 403)

    def test_pacotes_e_video_com_range(self):
        self.pacote()
        s, _, d = self.pedir("GET", "/api/pacotes")
        lista = json.loads(d)
        self.assertEqual(lista[0]["nome"], "2026-01-01_adiar")
        s, h, d = self.pedir("GET", "/ficheiro/2026-01-01_adiar/video.mp4", cabecalhos={"Range": "bytes=10-19"})
        self.assertEqual(s, 206)
        self.assertEqual(d, b"0123456789")
        s, _, d = self.pedir("GET", "/api/pacotes/2026-01-01_adiar")
        self.assertIn("Porque adias?", json.loads(d)["texto"])

    def test_caminhos_perigosos(self):
        self.pacote()
        for url in ("/ficheiro/../x/video.mp4", "/ficheiro/2026-01-01_adiar/..%2f..%2fconfig.json",
                    "/api/pacotes/..", "/ficheiro/%2e%2e/historico.json/x"):
            s, _, _ = self.pedir("GET", url)
            self.assertIn(s, (400, 404), url)

    def test_feedback_via_web(self):
        self.pacote()
        s, r = self.post("/api/feedback", {"pacote": "2026-01-01_adiar", "nota": 5, "comentario": "ótimo", "publicado": True})
        self.assertEqual(s, 200)
        self.assertEqual(r["diogo"]["nota"], 5)
        s, r = self.post("/api/feedback", {"pacote": "nao_existe", "nota": 5})
        self.assertEqual(s, 404)

    def test_trabalho_analisar_corre_em_segundo_plano(self):
        self.pacote()
        s, t = self.post("/api/trabalhos", {"tipo": "analisar", "pacote": "2026-01-01_adiar"})
        self.assertEqual(s, 201)
        for _ in range(100):
            _, _, d = self.pedir("GET", f"/api/trabalhos/{t['id']}")
            r = json.loads(d)
            if r["estado"] in ("feito", "erro"):
                break
            time.sleep(0.1)
        self.assertEqual(r["estado"], "feito", r)
        self.assertTrue(A.carregar(self.base)["registos"][0]["analise"])

    def test_trabalho_invalido(self):
        self.assertEqual(self.post("/api/trabalhos", {"tipo": "guiao"})[0], 400)
        self.assertEqual(self.post("/api/trabalhos", {"tipo": "xpto", "tema": "a"})[0], 400)
        self.assertEqual(self.post("/api/trabalhos", {"tipo": "video", "guiao": "../x.json"})[0], 400)

    def test_conceitos_validacao(self):
        s, r = self.post("/api/conceitos", {"nome": "ab"})
        self.assertEqual(s, 400)
        self.assertIn("erro", r)

    def test_agenda(self):
        s, _, d = self.pedir("GET", "/api/agenda")
        self.assertEqual(s, 200)
        self.assertEqual(len(json.loads(d)["dias"]), 7)

    def test_token_na_rede(self):
        srv = web.Servidor(("127.0.0.1", 0), token="segredo123456789a")
        threading.Thread(target=srv.serve_forever, daemon=True).start()
        self.addCleanup(srv.server_close)
        self.addCleanup(srv.shutdown)
        c = http.client.HTTPConnection("127.0.0.1", srv.server_address[1])
        c.request("GET", "/api/estado")
        r = c.getresponse(); r.read()
        self.assertEqual(r.status, 403)
        c = http.client.HTTPConnection("127.0.0.1", srv.server_address[1])
        c.request("GET", "/api/estado?t=segredo123456789a")
        r = c.getresponse(); r.read()
        self.assertEqual(r.status, 200)
        self.assertIn("fabrica_t=", r.getheader("Set-Cookie"))


if __name__ == "__main__":
    unittest.main()
