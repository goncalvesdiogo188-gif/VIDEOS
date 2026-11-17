package pt.fabrica.reels;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** O que a app se lembra entre aberturas: a pasta escolhida e os pacotes já publicados. */
public final class Estado {
    private final SharedPreferences sp;

    public Estado(Context c) {
        sp = c.getSharedPreferences("fabrica", Context.MODE_PRIVATE);
    }

    public String arvore() {
        return sp.getString("arvore", null);
    }

    public void guardarArvore(String uri) {
        sp.edit().putString("arvore", uri).apply();
    }

    public void esquecerArvore() {
        sp.edit().remove("arvore").apply();
    }

    public boolean publicado(String chave) {
        return sp.getStringSet("publicados", new HashSet<String>()).contains(chave);
    }

    public void marcarPublicado(String chave, boolean sim) {
        Set<String> s = new HashSet<>(sp.getStringSet("publicados", new HashSet<String>()));
        if (sim) s.add(chave); else s.remove(chave);
        sp.edit().putStringSet("publicados", s).apply();
    }
}
