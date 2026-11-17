package pt.fabrica.reels;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Ecrã inicial: a lista de pacotes e os botões para escolher a pasta ou abrir um pacote.zip. */
public class MainActivity extends Activity {
    private static final int PEDIR_PASTA = 1, PEDIR_ZIP = 2;

    private Estado estado;
    private final List<Pacote> pacotes = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView mensagem;
    private final ExecutorService fundo = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        estado = new Estado(this);
        construirEcra();
        recarregar();
        if (b == null) tratarIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        tratarIntent(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) adapter.notifyDataSetChanged();  // atualiza os "publicado"
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        fundo.shutdownNow();
    }

    // ------------------------------------------------------------------ ecrã
    private void construirEcra() {
        int p = Ui.dp(this, 16);
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(Ui.FUNDO);
        raiz.setPadding(p, Ui.dp(this, 28), p, p);

        raiz.addView(Ui.texto(this, "FÁBRICA", 30, Ui.CIANO, true));
        raiz.addView(Ui.texto(this, "Os teus Reels prontos a publicar", 15, Ui.SUAVE, false));

        LinearLayout botoes = new LinearLayout(this);
        botoes.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(0, Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 6));
        Button pasta = Ui.botao(this, "Escolher pasta", Ui.CIANO, Ui.FUNDO, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                escolherPasta();
            }
        });
        Button zip = Ui.botao(this, "Abrir pacote .zip", Ui.OURO, Ui.FUNDO, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                escolherZip();
            }
        });
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp2.setMargins(Ui.dp(this, 8), Ui.dp(this, 14), 0, Ui.dp(this, 6));
        botoes.addView(pasta, lp);
        botoes.addView(zip, lp2);
        raiz.addView(botoes);

        mensagem = Ui.texto(this, "", 14, Ui.SUAVE, false);
        mensagem.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        raiz.addView(mensagem);

        ListView lista = new ListView(this);
        lista.setDividerHeight(Ui.dp(this, 10));
        lista.setDivider(null);
        lista.setClipToPadding(false);
        adapter = new Adaptador();
        lista.setAdapter(adapter);
        lista.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
                abrir(pacotes.get(pos));
            }
        });
        lista.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> a, View v, int pos, long id) {
                return perguntarRemover(pacotes.get(pos));
            }
        });
        raiz.addView(lista, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(raiz);
    }

    private void abrir(Pacote p) {
        Intent i = new Intent(this, PacoteActivity.class);
        i.putExtra("chave", p.chave);
        startActivity(i);
    }

    private boolean perguntarRemover(final Pacote p) {
        if (!p.doZip) {
            Toast.makeText(this, "Este pacote está na tua pasta; apaga-o lá (no PC ou no Drive).", Toast.LENGTH_LONG).show();
            return true;
        }
        new AlertDialog.Builder(this).setTitle("Remover do telemóvel?")
                .setMessage(p.titulo + "\n\nO pacote.zip original não é apagado.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Remover", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Repositorio.remover(MainActivity.this, p);
                        recarregar();
                    }
                }).show();
        return true;
    }

    // ------------------------------------------------------------------ pedir pasta / zip
    private void escolherPasta() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(i, PEDIR_PASTA);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Este telemóvel não deixa escolher pastas. Usa \"Abrir pacote .zip\".", Toast.LENGTH_LONG).show();
        }
    }

    private void escolherZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        try {
            startActivityForResult(i, PEDIR_ZIP);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Não encontrei nenhuma app para escolher ficheiros.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent dados) {
        super.onActivityResult(pedido, resultado, dados);
        if (resultado != RESULT_OK || dados == null || dados.getData() == null) return;
        Uri uri = dados.getData();
        if (pedido == PEDIR_PASTA) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException e) {
                Toast.makeText(this, "Esta pasta não deixa a app lembrar-se dela. Se for do Google Drive, usa \"Abrir pacote .zip\".", Toast.LENGTH_LONG).show();
            }
            estado.guardarArvore(uri.toString());
            recarregar();
        } else if (pedido == PEDIR_ZIP) {
            importar(uri);
        }
    }

    @SuppressWarnings("deprecation")
    private void tratarIntent(Intent i) {
        if (i == null) return;
        Uri u = null;
        if (Intent.ACTION_VIEW.equals(i.getAction())) u = i.getData();
        else if (Intent.ACTION_SEND.equals(i.getAction())) u = (Uri) i.getParcelableExtra(Intent.EXTRA_STREAM);
        if (u != null) {
            setIntent(new Intent());  // não voltar a importar ao rodar o ecrã
            importar(u);
        }
    }

    // ------------------------------------------------------------------ trabalho em segundo plano
    private void importar(final Uri zip) {
        mensagem.setText("A abrir o pacote...");
        fundo.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Pacote p = Repositorio.importarZip(MainActivity.this, zip);
                    final List<Pacote> todos = Repositorio.carregarTudo(MainActivity.this, estado.arvore());
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            mostrar(todos);
                            abrir(p);
                        }
                    });
                } catch (final IOException e) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            mensagem.setText("Não consegui abrir: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void recarregar() {
        mensagem.setText("A procurar pacotes...");
        fundo.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Pacote> todos = Repositorio.carregarTudo(MainActivity.this, estado.arvore());
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            mostrar(todos);
                        }
                    });
                } catch (final IOException e) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            mensagem.setText(e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void mostrar(List<Pacote> todos) {
        pacotes.clear();
        pacotes.addAll(todos);
        adapter.notifyDataSetChanged();
        if (!pacotes.isEmpty()) {
            mensagem.setText(pacotes.size() + (pacotes.size() == 1 ? " pacote" : " pacotes") + ". Toca num para abrir.");
        } else if (estado.arvore() == null) {
            mensagem.setText("Ainda não há pacotes.\n\n• Se a pasta dos pacotes está no telemóvel: \"Escolher pasta\".\n"
                    + "• Se está no Google Drive: abre o pacote.zip do dia com \"Abrir pacote .zip\".");
        } else {
            mensagem.setText("Não encontrei pacotes nessa pasta. Cada pacote tem de ter um pacote.json. "
                    + "Se a pasta é do Google Drive, usa \"Abrir pacote .zip\".");
        }
    }

    // ------------------------------------------------------------------ lista
    private final class Adaptador extends BaseAdapter {
        @Override
        public int getCount() {
            return pacotes.size();
        }

        @Override
        public Object getItem(int i) {
            return pacotes.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View velha, ViewGroup pai) {
            Pacote p = pacotes.get(i);
            LinearLayout cartao = new LinearLayout(MainActivity.this);
            cartao.setOrientation(LinearLayout.VERTICAL);
            int m = Ui.dp(MainActivity.this, 14);
            cartao.setPadding(m, m, m, m);
            cartao.setBackground(Ui.fundo(MainActivity.this, Ui.CARTAO, 16));
            TextView t = Ui.texto(MainActivity.this, p.titulo, 18, Ui.TEXTO, true);
            cartao.addView(t);
            cartao.addView(Ui.texto(MainActivity.this, p.subtitulo(), 13, Ui.SUAVE, false));
            if (estado.publicado(p.chave)) {
                TextView ok = Ui.texto(MainActivity.this, "✔ Publicado", 13, Ui.OURO, true);
                ok.setGravity(Gravity.END);
                cartao.addView(ok);
            }
            return cartao;
        }
    }
}
