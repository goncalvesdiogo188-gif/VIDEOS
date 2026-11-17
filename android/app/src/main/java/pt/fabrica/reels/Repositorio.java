package pt.fabrica.reels;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Encontra os pacotes: numa pasta escolhida (Android SAF) e os que a app guardou a partir de .zip. */
public final class Repositorio {
    private static final Map<String, Pacote> EM_MEMORIA = new LinkedHashMap<>();

    private Repositorio() {}

    public static synchronized void guardar(List<Pacote> lista) {
        EM_MEMORIA.clear();
        for (Pacote p : lista) EM_MEMORIA.put(p.chave, p);
    }

    public static synchronized Pacote obter(String chave) {
        return EM_MEMORIA.get(chave);
    }

    /** Pacotes da pasta escolhida + pacotes vindos de zip, do mais recente para o mais antigo. */
    public static List<Pacote> carregarTudo(Context c, String arvore) throws IOException {
        List<Pacote> todos = new ArrayList<>(doZip(c));
        if (arvore != null) {
            try {
                todos.addAll(daArvore(c.getContentResolver(), Uri.parse(arvore)));
            } catch (SecurityException | IllegalArgumentException e) {
                throw new IOException("A app já não tem acesso à pasta escolhida. Escolhe-a outra vez.", e);
            }
        }
        Collections.sort(todos, new Comparator<Pacote>() {
            @Override
            public int compare(Pacote a, Pacote b) {
                return b.ordem().compareTo(a.ordem());
            }
        });
        guardar(todos);
        return todos;
    }

    // ------------------------------------------------------------------ pacotes que vieram de um .zip
    private static File pasta(Context c) {
        return new File(c.getFilesDir(), "pacotes");
    }

    public static List<Pacote> doZip(Context c) {
        List<Pacote> r = new ArrayList<>();
        File[] pastas = pasta(c).listFiles();
        if (pastas == null) return r;
        for (File d : pastas) {
            File man = new File(d, "pacote.json");
            if (!man.isFile()) continue;
            try {
                r.add(prepararZip(d, lerTexto(man)));
            } catch (IOException | JSONException e) {
                // pacote estragado: ignora
            }
        }
        return r;
    }

    private static Pacote prepararZip(File d, String json) throws JSONException {
        Pacote p = Pacote.desdeJson(json);
        p.doZip = true;
        p.chave = "zip:" + d.getName();
        p.videoUri = FabricaProvider.uriDe(d.getName(), p.video).toString();
        p.capaUri = new File(d, p.capa).isFile() ? FabricaProvider.uriDe(d.getName(), p.capa).toString() : null;
        for (String s : p.carrossel) {
            if (new File(d, s).isFile()) p.carrosselUris.add(FabricaProvider.uriDe(d.getName(), s).toString());
        }
        return p;
    }

    /** Abre um pacote.zip (ex.: do Google Drive), guarda-o no espaço da app e devolve-o. */
    public static Pacote importarZip(Context c, Uri zip) throws IOException {
        File tmp = new File(pasta(c), "_tmp_" + System.nanoTime());
        try (InputStream in = c.getContentResolver().openInputStream(zip)) {
            if (in == null) throw new IOException("Não consegui abrir o ficheiro.");
            ZipUtil.extrair(in, tmp);
        } catch (IOException | RuntimeException e) {
            ZipUtil.apagar(tmp);
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
        File man = new File(tmp, "pacote.json");
        if (!man.isFile() || !new File(tmp, "video.mp4").isFile()) {
            ZipUtil.apagar(tmp);
            throw new IOException("Este zip não é um pacote da FÁBRICA (falta o pacote.json ou o vídeo).");
        }
        try {
            Pacote p = Pacote.desdeJson(lerTexto(man));
            File destino = new File(pasta(c), (p.data.isEmpty() ? "sem-data" : p.data) + "_" + ZipUtil.slug(p.titulo));
            ZipUtil.apagar(destino);
            if (!tmp.renameTo(destino)) throw new IOException("Não consegui guardar o pacote.");
            return prepararZip(destino, lerTexto(new File(destino, "pacote.json")));
        } catch (JSONException e) {
            ZipUtil.apagar(tmp);
            throw new IOException("O pacote.json está estragado: " + e.getMessage(), e);
        }
    }

    public static void remover(Context c, Pacote p) {
        if (p.doZip) ZipUtil.apagar(new File(pasta(c), p.chave.substring(4)));
    }

    private static String lerTexto(File f) throws IOException {
        try (InputStream in = new java.io.FileInputStream(f)) {
            return lerTexto(in);
        }
    }

    private static String lerTexto(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        return o.toString(StandardCharsets.UTF_8.name());
    }

    // ------------------------------------------------------------------ pasta escolhida (SAF)
    private static final class Doc {
        final String id, nome, mime;

        Doc(String id, String nome, String mime) {
            this.id = id;
            this.nome = nome;
            this.mime = mime;
        }

        boolean pasta() {
            return DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
        }
    }

    private static List<Doc> listar(ContentResolver cr, Uri arvore, String idPai) {
        List<Doc> r = new ArrayList<>();
        Uri filhos = DocumentsContract.buildChildDocumentsUriUsingTree(arvore, idPai);
        String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cur = cr.query(filhos, cols, null, null, null)) {
            while (cur != null && cur.moveToNext()) r.add(new Doc(cur.getString(0), cur.getString(1), cur.getString(2)));
        } catch (RuntimeException e) {
            // pasta sem acesso: devolve vazio
        }
        return r;
    }

    private static Doc achar(List<Doc> docs, String nome) {
        for (Doc d : docs) if (nome.equals(d.nome)) return d;
        return null;
    }

    static List<Pacote> daArvore(ContentResolver cr, Uri arvore) throws IOException {
        List<Pacote> r = new ArrayList<>();
        String raiz = DocumentsContract.getTreeDocumentId(arvore);
        List<Doc> topo = listar(cr, arvore, raiz);
        if (achar(topo, "pacote.json") != null) {  // a pasta escolhida já é um pacote
            Pacote p = lerPacote(cr, arvore, raiz, topo);
            if (p != null) r.add(p);
        }
        for (Doc d : topo) {
            if (!d.pasta()) continue;
            Pacote p = lerPacote(cr, arvore, d.id, listar(cr, arvore, d.id));
            if (p != null) r.add(p);
        }
        return r;
    }

    private static Pacote lerPacote(ContentResolver cr, Uri arvore, String idPasta, List<Doc> itens) throws IOException {
        Doc man = achar(itens, "pacote.json");
        if (man == null) return null;
        try (InputStream in = cr.openInputStream(DocumentsContract.buildDocumentUriUsingTree(arvore, man.id))) {
            if (in == null) return null;
            Pacote p = Pacote.desdeJson(lerTexto(in));
            p.chave = "pasta:" + idPasta;
            Map<String, List<Doc>> cache = new HashMap<>();
            p.videoUri = resolver(cr, arvore, itens, p.video, cache);
            if (p.videoUri == null) return null;  // sem vídeo, o pacote não serve
            p.capaUri = resolver(cr, arvore, itens, p.capa, cache);
            for (String s : p.carrossel) {
                String u = resolver(cr, arvore, itens, s, cache);
                if (u != null) p.carrosselUris.add(u);
            }
            return p;
        } catch (JSONException e) {
            return null;
        }
    }

    /** "carrossel/slide_01.png" → endereço do documento, descendo pelas subpastas. */
    private static String resolver(ContentResolver cr, Uri arvore, List<Doc> itens, String relativo, Map<String, List<Doc>> cache) {
        List<Doc> atual = itens;
        String[] partes = relativo.split("/");
        for (int i = 0; i < partes.length; i++) {
            Doc d = achar(atual, partes[i]);
            if (d == null) return null;
            if (i == partes.length - 1) return DocumentsContract.buildDocumentUriUsingTree(arvore, d.id).toString();
            List<Doc> sub = cache.get(d.id);
            if (sub == null) {
                sub = listar(cr, arvore, d.id);
                cache.put(d.id, sub);
            }
            atual = sub;
        }
        return null;
    }
}
