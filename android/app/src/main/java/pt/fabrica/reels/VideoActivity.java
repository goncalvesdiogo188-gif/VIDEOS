package pt.fabrica.reels;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

/** Mostra o vídeo do pacote, em ecrã inteiro, com os controlos normais. */
public class VideoActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String uri = getIntent().getStringExtra("uri");
        if (uri == null) {
            finish();
            return;
        }
        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(0xFF000000);
        final VideoView v = new VideoView(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER);
        raiz.addView(v, lp);
        setContentView(raiz);
        MediaController mc = new MediaController(this);
        mc.setAnchorView(v);
        v.setMediaController(mc);
        v.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                v.start();
            }
        });
        v.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Toast.makeText(VideoActivity.this, "Não consegui reproduzir o vídeo.", Toast.LENGTH_LONG).show();
                finish();
                return true;
            }
        });
        v.setVideoURI(Uri.parse(uri));
    }
}
