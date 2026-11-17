package pt.fabrica.reels;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Extrai um zip para uma pasta, com proteção contra ficheiros que tentam sair da pasta ("zip slip"). */
public final class ZipUtil {
    public static final long MAXIMO_TOTAL = 500L * 1024 * 1024;

    private ZipUtil() {}

    /** Devolve os nomes dos ficheiros extraídos. */
    public static List<String> extrair(InputStream entrada, File destino) throws IOException {
        List<String> nomes = new ArrayList<>();
        if (!destino.isDirectory() && !destino.mkdirs()) throw new IOException("Não consegui criar a pasta " + destino);
        String base = destino.getCanonicalPath() + File.separator;
        long total = 0;
        byte[] buf = new byte[64 * 1024];
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(entrada))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String nome = e.getName().replace('\\', '/');
                File f = new File(destino, nome);
                if (!f.getCanonicalPath().startsWith(base)) {
                    throw new IOException("Ficheiro suspeito dentro do zip: " + nome);
                }
                if (e.isDirectory()) {
                    f.mkdirs();
                    continue;
                }
                File pai = f.getParentFile();
                if (pai != null && !pai.isDirectory() && !pai.mkdirs()) throw new IOException("Não consegui criar " + pai);
                try (OutputStream out = new FileOutputStream(f)) {
                    int n;
                    while ((n = zin.read(buf)) > 0) {
                        total += n;
                        if (total > MAXIMO_TOTAL) throw new IOException("O zip é demasiado grande.");
                        out.write(buf, 0, n);
                    }
                }
                nomes.add(nome);
            }
        }
        return nomes;
    }

    /** Apaga uma pasta e tudo o que tem dentro. */
    public static void apagar(File f) {
        File[] filhos = f.listFiles();
        if (filhos != null) for (File c : filhos) apagar(c);
        f.delete();
    }

    /** "Porque gastas?" → "porque-gastas" (para nomes de pastas). */
    public static String slug(String t) {
        String s = java.text.Normalizer.normalize(t == null ? "" : t, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.length() > 40) s = s.substring(0, 40).replaceAll("-+$", "");
        return s.isEmpty() ? "pacote" : s;
    }
}
