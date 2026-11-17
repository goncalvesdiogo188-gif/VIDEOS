package pt.fabrica.reels;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;

/** Pequenas ajudas para desenhar o ecrã sem ficheiros de layout. */
final class Ui {
    static final int FUNDO = 0xFF120A2E, CARTAO = 0xFF1E1450, CIANO = 0xFF2EE6FF, OURO = 0xFFFFD447,
            ROSA = 0xFFE0628F, TEXTO = 0xFFFFFFFF, SUAVE = 0xFFB9A7FF;

    private Ui() {}

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable fundo(Context c, int cor, int raioDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor);
        g.setCornerRadius(dp(c, raioDp));
        return g;
    }

    static TextView texto(Context c, String t, int sp, int cor, boolean negrito) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(cor);
        if (negrito) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    static Button botao(Context c, String t, int fundo, int corTexto, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTextColor(corTexto);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(fundo(c, fundo, 14));
        b.setMinHeight(dp(c, 52));
        b.setOnClickListener(l);
        return b;
    }

    /** Lê uma imagem (de um content://) já reduzida para não encher a memória. */
    static Bitmap bitmap(ContentResolver cr, Uri uri, int larguraMax) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new IOException("sem imagem");
            BitmapFactory.decodeStream(in, null, o);
        }
        int amostra = 1;
        while (o.outWidth / (amostra * 2) >= larguraMax) amostra *= 2;
        BitmapFactory.Options f = new BitmapFactory.Options();
        f.inSampleSize = amostra;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new IOException("sem imagem");
            Bitmap b = BitmapFactory.decodeStream(in, null, f);
            if (b == null) throw new IOException("imagem inválida");
            return b;
        }
    }
}
