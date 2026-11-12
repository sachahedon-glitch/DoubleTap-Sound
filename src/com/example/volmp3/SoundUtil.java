package com.example.volmp3;

import android.content.Context;
import android.media.MediaPlayer;
import java.io.File;
import android.media.audiofx.LoudnessEnhancer;
import java.io.FileInputStream;
import java.util.HashMap;

public class SoundUtil {
    public static final String FILE_NAME = "son.mp3";
    public static final int MAX_PERCENT = 400; // overclock jusqu'a 400 %
    private static final HashMap<MediaPlayer, LoudnessEnhancer> enh = new HashMap<MediaPlayer, LoudnessEnhancer>();

    public static int getPercent(Context c) {
        return c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getInt("percent", 100);
    }

    public static void setPercent(Context c, int p) {
        c.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putInt("percent", p).commit();
    }

    /** <=100 % : volume normal. >100 % : volume max + gain LoudnessEnhancer (overclock). */
    private static void applyVolume(Context c, MediaPlayer mp) {
        int pct = getPercent(c);
        if (pct <= 100) {
            float v = pct / 100f;
            mp.setVolume(v, v);
            return;
        }
        mp.setVolume(1f, 1f);
        try {
            int mB = (int) Math.round(2000.0 * Math.log10(pct / 100.0));
            LoudnessEnhancer e = new LoudnessEnhancer(mp.getAudioSessionId());
            e.setTargetGain(mB);
            e.setEnabled(true);
            enh.put(mp, e);
        } catch (Throwable t) {
            // effet indisponible : volume max classique
        }
    }

    public static void release(MediaPlayer mp) {
        if (mp == null) return;
        LoudnessEnhancer e = enh.remove(mp);
        if (e != null) { try { e.release(); } catch (Throwable ignored) {} }
        try { mp.release(); } catch (Throwable ignored) {}
    }

    public static boolean hasCustom(Context c) {
        return new File(c.getFilesDir(), FILE_NAME).exists();
    }

    /** MP3 choisi par l'utilisateur, sinon le bip par defaut. */
    public static MediaPlayer create(Context c) {
        File f = new File(c.getFilesDir(), FILE_NAME);
        if (f.exists()) {
            try {
                FileInputStream in = new FileInputStream(f);
                MediaPlayer mp = new MediaPlayer();
                mp.setDataSource(in.getFD());
                in.close();
                mp.prepare();
                applyVolume(c, mp);
                return mp;
            } catch (Exception e) {
                // fichier illisible : on retombe sur le son par defaut
            }
        }
        MediaPlayer d = MediaPlayer.create(c, R.raw.son);
        if (d != null) applyVolume(c, d);
        return d;
    }
}
