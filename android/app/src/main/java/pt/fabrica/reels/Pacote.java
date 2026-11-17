package pt.fabrica.reels;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Um pacote da FÁBRICA (vídeo, capa, carrossel, legenda), lido do pacote.json. Sem dependências do Android. */
public final class Pacote {
    public String chave = "";
    public String titulo = "(sem título)";
    public String tema = "", dono = "", conceito = "", data = "", hora = "";
    public String legenda = "", textoParaPublicar = "";
    public double duracao;
    public String video = "video.mp4", capa = "capa.png";
    public List<String> carrossel = new ArrayList<>();
    public List<String> hashtags = new ArrayList<>();

    // Preenchidos por quem carrega o pacote (endereços content:// como texto).
    public String videoUri, capaUri;
    public List<String> carrosselUris = new ArrayList<>();
    public boolean doZip;

    public static Pacote desdeJson(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        if (!o.has("video")) throw new JSONException("O pacote.json não tem o campo \"video\".");
        Pacote p = new Pacote();
        p.titulo = o.optString("titulo", p.titulo).trim();
        if (p.titulo.isEmpty()) p.titulo = "(sem título)";
        p.tema = o.optString("tema", "");
        p.dono = o.optString("dono", "");
        p.conceito = o.optString("conceito", "");
        p.data = o.optString("data", "");
        p.hora = o.optString("hora", "");
        p.duracao = o.optDouble("duracao", 0);
        p.video = o.optString("video", p.video);
        p.capa = o.optString("capa", p.capa);
        p.legenda = o.optString("legenda", "");
        p.textoParaPublicar = o.optString("texto_para_publicar", "");
        p.carrossel = lista(o.optJSONArray("carrossel"));
        p.hashtags = lista(o.optJSONArray("hashtags"));
        return p;
    }

    private static List<String> lista(JSONArray a) {
        List<String> r = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) r.add(a.optString(i, ""));
        return r;
    }

    /** O texto para colar no Instagram ou no TikTok: legenda, linha em branco e hashtags. */
    public String textoPublicar() {
        if (!textoParaPublicar.trim().isEmpty()) return textoParaPublicar.trim();
        StringBuilder sb = new StringBuilder(legenda.trim());
        if (!hashtags.isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            for (int i = 0; i < hashtags.size(); i++) sb.append(i > 0 ? " " : "").append(hashtags.get(i));
        }
        return sb.toString();
    }

    public String duracaoTexto() {
        int s = (int) Math.round(duracao);
        if (s <= 0) return "";
        return s >= 60 ? (s / 60) + " min " + (s % 60) + " s" : s + " s";
    }

    /** "2026-11-17" → "17/11/2026"; qualquer outra coisa fica como está. */
    public static String dataBonita(String iso) {
        if (iso != null && iso.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4);
        }
        return iso == null ? "" : iso;
    }

    public String subtitulo() {
        StringBuilder sb = new StringBuilder(dataBonita(data));
        String d = duracaoTexto();
        if (!d.isEmpty()) sb.append(sb.length() > 0 ? " · " : "").append(d);
        if (!conceito.isEmpty()) sb.append(sb.length() > 0 ? " · " : "").append(conceito);
        return sb.toString();
    }

    /** Para ordenar do mais recente para o mais antigo. */
    public String ordem() {
        return data + " " + hora + " " + titulo;
    }
}
