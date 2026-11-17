package pt.fabrica.reels;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Testes da parte pura (sem Android). Corre com: java -cp ... pt.fabrica.reels.AutoTeste */
public final class AutoTeste {
    static int falhas = 0, total = 0;

    static void igual(String nome, Object esperado, Object obtido) {
        total++;
        if (!String.valueOf(esperado).equals(String.valueOf(obtido))) {
            falhas++;
            System.out.println("FALHOU " + nome + ": esperava [" + esperado + "] e obtive [" + obtido + "]");
        }
    }

    static void verdade(String nome, boolean ok) {
        igual(nome, true, ok);
    }

    static byte[] zip(String... paresNomeConteudo) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            for (int i = 0; i < paresNomeConteudo.length; i += 2) {
                z.putNextEntry(new ZipEntry(paresNomeConteudo[i]));
                z.write(paresNomeConteudo[i + 1].getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return bo.toByteArray();
    }

    public static void main(String[] a) throws Exception {
        // ---- Pacote
        String json = "{\"versao\":1,\"titulo\":\"Porque sim?\",\"dono\":\"Rui\",\"conceito\":\"Dor de pagar\","
                + "\"data\":\"2026-11-17\",\"hora\":\"09:05\",\"duracao\":73.5,\"video\":\"video.mp4\",\"capa\":\"capa.png\","
                + "\"carrossel\":[\"carrossel/slide_01.png\",\"carrossel/slide_02.png\"],\"legenda\":\"Uma legenda.\","
                + "\"hashtags\":[\"#a\",\"#b\"],\"texto_para_publicar\":\"Uma legenda.\\n\\n#a #b\"}";
        Pacote p = Pacote.desdeJson(json);
        igual("titulo", "Porque sim?", p.titulo);
        igual("carrossel", 2, p.carrossel.size());
        igual("texto", "Uma legenda.\n\n#a #b", p.textoPublicar());
        igual("duracao", "1 min 14 s", p.duracaoTexto());
        igual("subtitulo", "17/11/2026 · 1 min 14 s · Dor de pagar", p.subtitulo());
        Pacote q = Pacote.desdeJson("{\"video\":\"v.mp4\",\"legenda\":\"L\",\"hashtags\":[\"#x\",\"#y\"],\"duracao\":45}");
        igual("sem texto_para_publicar", "L\n\n#x #y", q.textoPublicar());
        igual("titulo por defeito", "(sem título)", q.titulo);
        igual("45 s", "45 s", q.duracaoTexto());
        igual("data estranha", "ontem", Pacote.dataBonita("ontem"));
        igual("data null", "", Pacote.dataBonita(null));
        try {
            Pacote.desdeJson("{\"titulo\":\"x\"}");
            verdade("sem video deve falhar", false);
        } catch (org.json.JSONException e) {
            verdade("sem video falha", true);
        }
        Pacote antigo = Pacote.desdeJson("{\"video\":\"v\",\"data\":\"2026-11-10\",\"hora\":\"08:00\",\"titulo\":\"A\"}");
        verdade("ordem", p.ordem().compareTo(antigo.ordem()) > 0);

        // ---- slug
        igual("slug", "porque-gastas-logo-que-recebes", ZipUtil.slug("Porque gastas logo que recebes?"));
        igual("slug acentos", "acao-coracao", ZipUtil.slug("Ação, coração!"));
        igual("slug vazio", "pacote", ZipUtil.slug("???"));
        verdade("slug curto", ZipUtil.slug("a".repeat(100)).length() <= 40);

        // ---- zip normal
        File tmp = Files.createTempDirectory("fab").toFile();
        List<String> nomes = ZipUtil.extrair(new ByteArrayInputStream(zip("pacote.json", json, "carrossel/slide_01.png", "PNG", "video.mp4", "V")), new File(tmp, "ok"));
        igual("n ficheiros", 3, nomes.size());
        igual("conteudo", "PNG", new String(Files.readAllBytes(new File(tmp, "ok/carrossel/slide_01.png").toPath()), StandardCharsets.UTF_8));

        // ---- zip malicioso (zip slip)
        try {
            ZipUtil.extrair(new ByteArrayInputStream(zip("../fora.txt", "mau")), new File(tmp, "mau"));
            verdade("zip slip devia falhar", false);
        } catch (java.io.IOException e) {
            verdade("zip slip bloqueado", e.getMessage().contains("suspeito"));
        }
        verdade("nada escrito fora", !new File(tmp, "fora.txt").exists());
        try {
            ZipUtil.extrair(new ByteArrayInputStream(zip("..\\fora2.txt", "mau")), new File(tmp, "mau2"));
            verdade("zip slip com barras invertidas devia falhar", new File(tmp, "fora2.txt").exists() == false);
        } catch (java.io.IOException e) {
            verdade("zip slip \\ bloqueado", true);
        }
        // ---- zip vazio ou lixo
        igual("zip vazio", 0, ZipUtil.extrair(new ByteArrayInputStream(new byte[0]), new File(tmp, "vazio")).size());

        ZipUtil.apagar(tmp);
        verdade("apagado", !tmp.exists());
        System.out.println(total + " verificações, " + falhas + " falhas.");
        System.exit(falhas == 0 ? 0 : 1);
    }
}
