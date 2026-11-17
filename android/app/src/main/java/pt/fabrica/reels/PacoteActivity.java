package pt.fabrica.reels;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Um pacote: capa, legenda e os botões para copiar a legenda e partilhar o vídeo ou o carrossel. */
public class PacoteActivity extends Activity {
    private static final String INSTAGRAM = "com.instagram.android";
    private static final String[] TIKTOK = {"com.zhiliaoapp.musically", "com.ss.android.ugc.trill"};

    private Pacote p;
    private Estado estado;
    private Button botaoPublicado;
    private final ExecutorService fundo = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = Repositorio.obter(getIntent().getStringExtra("chave"));
        if (p == null) {  // a app foi fechada pelo sistema: volta à lista
            finish();
            return;
        }
        estado = new Estado(this);
        construirEcra();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        fundo.shutdownNow();
    }

    private void construirEcra() {
        int m = Ui.dp(this, 16);
        ScrollView rolar = new ScrollView(this);
        rolar.setBackgroundColor(Ui.FUNDO);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(m, Ui.dp(this, 24), m, m);
        rolar.addView(col);

        if (p.capaUri != null) {
            final ImageView capa = new ImageView(this);
            capa.setAdjustViewBounds(true);
            capa.setMaxHeight(Ui.dp(this, 420));
            capa.setScaleType(ImageView.ScaleType.FIT_CENTER);
            capa.setBackground(Ui.fundo(this, Ui.CARTAO, 16));
            capa.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    verVideo();
                }
            });
            col.addView(capa);
            final Uri uri = Uri.parse(p.capaUri);
            fundo.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        final Bitmap bm = Ui.bitmap(getContentResolver(), uri, 900);
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                capa.setImageBitmap(bm);
                            }
                        });
                    } catch (IOException | RuntimeException e) {
                        // sem capa: não faz mal
                    }
                }
            });
        }

        TextView titulo = Ui.texto(this, p.titulo, 22, Ui.TEXTO, true);
        titulo.setPadding(0, Ui.dp(this, 14), 0, 0);
        col.addView(titulo);
        col.addView(Ui.texto(this, p.subtitulo(), 14, Ui.SUAVE, false));

        TextView legenda = Ui.texto(this, p.textoPublicar(), 16, Ui.TEXTO, false);
        legenda.setTextIsSelectable(true);
        legenda.setPadding(m, m, m, m);
        legenda.setBackground(Ui.fundo(this, Ui.CARTAO, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
        col.addView(legenda, lp);

        adicionar(col, "📋  Copiar legenda e hashtags", Ui.OURO, Ui.FUNDO, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copiarLegenda(true);
            }
        });
        adicionar(col, "▶  Ver o vídeo", Ui.CARTAO, Ui.TEXTO, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                verVideo();
            }
        });
        col.addView(Ui.texto(this, "Partilhar o vídeo", 13, Ui.SUAVE, true), com(Ui.dp(this, 14), 0));
        adicionar(col, "Instagram (Reels)", Ui.ROSA, Ui.TEXTO, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                partilharVideo(new String[]{INSTAGRAM}, "Instagram");
            }
        });
        adicionar(col, "TikTok", Ui.CIANO, Ui.FUNDO, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                partilharVideo(TIKTOK, "TikTok");
            }
        });
        adicionar(col, "Outra app…", Ui.CARTAO, Ui.TEXTO, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                partilharVideo(null, null);
            }
        });
        if (!p.carrosselUris.isEmpty()) {
            adicionar(col, "🖼  Partilhar o carrossel (" + p.carrosselUris.size() + " imagens)", Ui.CARTAO, Ui.TEXTO,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            partilharCarrossel();
                        }
                    });
        }
        botaoPublicado = adicionar(col, "", Ui.CARTAO, Ui.OURO, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                estado.marcarPublicado(p.chave, !estado.publicado(p.chave));
                atualizarPublicado();
            }
        });
        atualizarPublicado();

        TextView dica = Ui.texto(this, "Dica: o Instagram e o TikTok não aceitam a legenda de fora. Por isso a app copia-a "
                + "sozinha quando partilhas; é só colar na caixa da legenda.", 13, Ui.SUAVE, false);
        dica.setPadding(0, Ui.dp(this, 16), 0, 0);
        col.addView(dica);
        setContentView(rolar);
    }

    private LinearLayout.LayoutParams com(int cima, int baixo) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, cima, 0, baixo);
        return lp;
    }

    private Button adicionar(LinearLayout col, String texto, int fundo, int cor, View.OnClickListener l) {
        Button b = Ui.botao(this, texto, fundo, cor, l);
        col.addView(b, com(Ui.dp(this, 8), 0));
        return b;
    }

    private void atualizarPublicado() {
        botaoPublicado.setText(estado.publicado(p.chave) ? "✔  Publicado (toca para desfazer)" : "Marcar como publicado");
    }

    // ------------------------------------------------------------------ ações
    private void copiarLegenda(boolean avisar) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("legenda", p.textoPublicar()));
        if (avisar) Toast.makeText(this, "Legenda e hashtags copiadas.", Toast.LENGTH_SHORT).show();
    }

    private void verVideo() {
        Intent i = new Intent(this, VideoActivity.class);
        i.putExtra("uri", p.videoUri);
        startActivity(i);
    }

    private String instalada(String[] pacotes) {
        if (pacotes == null) return null;
        PackageManager pm = getPackageManager();
        for (String s : pacotes) {
            try {
                pm.getPackageInfo(s, 0);
                return s;
            } catch (PackageManager.NameNotFoundException e) {
                // tenta a seguinte
            }
        }
        return null;
    }

    private void partilharVideo(String[] apps, String nomeApp) {
        String app = instalada(apps);
        if (apps != null && app == null) {
            Toast.makeText(this, "O " + nomeApp + " não está instalado neste telemóvel.", Toast.LENGTH_LONG).show();
            return;
        }
        copiarLegenda(false);
        Uri v = Uri.parse(p.videoUri);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("video/mp4");
        i.putExtra(Intent.EXTRA_STREAM, v);
        i.putExtra(Intent.EXTRA_TEXT, p.textoPublicar());
        i.setClipData(ClipData.newRawUri("video", v));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (app != null) i.setPackage(app);
        Toast.makeText(this, "Legenda copiada: cola-a na caixa da legenda.", Toast.LENGTH_LONG).show();
        try {
            startActivity(app != null ? i : Intent.createChooser(i, "Partilhar vídeo"));
        } catch (RuntimeException e) {
            Toast.makeText(this, "Não consegui partilhar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void partilharCarrossel() {
        ArrayList<Uri> uris = new ArrayList<>();
        for (String s : p.carrosselUris) uris.add(Uri.parse(s));
        Intent i = new Intent(Intent.ACTION_SEND_MULTIPLE);
        i.setType("image/png");
        i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        i.putExtra(Intent.EXTRA_TEXT, p.textoPublicar());
        ClipData cd = ClipData.newRawUri("carrossel", uris.get(0));
        for (int k = 1; k < uris.size(); k++) cd.addItem(new ClipData.Item(uris.get(k)));
        i.setClipData(cd);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        copiarLegenda(false);
        Toast.makeText(this, "Legenda copiada: cola-a na caixa da legenda.", Toast.LENGTH_LONG).show();
        try {
            startActivity(Intent.createChooser(i, "Partilhar carrossel"));
        } catch (RuntimeException e) {
            Toast.makeText(this, "Não consegui partilhar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
