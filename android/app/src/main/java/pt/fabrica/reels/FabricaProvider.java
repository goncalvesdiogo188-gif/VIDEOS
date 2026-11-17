package pt.fabrica.reels;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Entrega aos outros programas (Instagram, TikTok, leitor de vídeo) os ficheiros dos pacotes que a
 * app guardou no próprio espaço dela (os que vieram de um .zip). Só serve ficheiros dentro de files/pacotes.
 */
public final class FabricaProvider extends ContentProvider {
    public static final String AUTORIDADE = "pt.fabrica.reels.arquivos";

    public static Uri uriDe(String pastaPacote, String relativo) {
        Uri.Builder b = new Uri.Builder().scheme("content").authority(AUTORIDADE).appendPath(pastaPacote);
        for (String parte : relativo.split("/")) if (!parte.isEmpty()) b.appendPath(parte);
        return b.build();
    }

    private File ficheiro(Uri uri) throws FileNotFoundException {
        File raiz = new File(getContext().getFilesDir(), "pacotes");
        File f = new File(raiz, String.join("/", uri.getPathSegments()));
        try {
            if (!f.getCanonicalPath().startsWith(raiz.getCanonicalPath() + File.separator) || !f.isFile()) {
                throw new FileNotFoundException(uri.toString());
            }
        } catch (IOException e) {
            throw new FileNotFoundException(uri.toString());
        }
        return f;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String modo) throws FileNotFoundException {
        if (!"r".equals(modo)) throw new FileNotFoundException("Só leitura.");
        return ParcelFileDescriptor.open(ficheiro(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] colunas, String sel, String[] args, String ordem) {
        try {
            File f = ficheiro(uri);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        String n = uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment().toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".txt")) return "text/plain";
        if (n.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    @Override
    public Uri insert(Uri uri, ContentValues v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String s, String[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues v, String s, String[] a) {
        throw new UnsupportedOperationException();
    }
}
