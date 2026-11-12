package com.example.volmp3;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int REQ_PICK = 1;
    private TextView current;
    private MediaPlayer testPlayer;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 40);

        TextView info = new TextView(this);
        info.setText("DoubleTap Sound - Active le service, puis appuie 2 fois sur volume + (meme ecran eteint).");
        root.addView(info);

        current = new TextView(this);
        current.setPadding(0, 30, 0, 30);
        root.addView(current);
        refreshCurrent();

        final TextView volLabel = new TextView(this);
        volLabel.setPadding(0, 20, 0, 0);
        root.addView(volLabel);
        SeekBar vol = new SeekBar(this);
        vol.setMax(SoundUtil.MAX_PERCENT);
        vol.setProgress(SoundUtil.getPercent(this));
        volLabel.setText(label(SoundUtil.getPercent(this)));
        vol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                volLabel.setText(label(p));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {
                SoundUtil.setPercent(MainActivity.this, s.getProgress());
            }
        });
        root.addView(vol);

        Button pick = new Button(this);
        pick.setText("Choisir un MP3");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("audio/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(Intent.createChooser(i, "Choisir un MP3"), REQ_PICK);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                            "Aucun gestionnaire de fichiers trouve", Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(pick);

        Button test = new Button(this);
        test.setText("Tester le son");
        test.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (testPlayer != null) { SoundUtil.release(testPlayer); testPlayer = null; }
                testPlayer = SoundUtil.create(MainActivity.this);
                if (testPlayer != null) testPlayer.start();
            }
        });
        root.addView(test);

        Button on = new Button(this);
        on.setText("Activer");
        on.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startService(new Intent(MainActivity.this, VolumeService.class));
            }
        });
        root.addView(on);

        Button off = new Button(this);
        off.setText("Desactiver");
        off.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                stopService(new Intent(MainActivity.this, VolumeService.class));
            }
        });
        root.addView(off);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    private String label(int p) {
        return "Volume du son : " + p + " %" + (p > 100 ? "  (overclock)" : "");
    }

    private void refreshCurrent() {
        SharedPreferences sp = getSharedPreferences("prefs", MODE_PRIVATE);
        if (SoundUtil.hasCustom(this)) {
            current.setText("Son actuel : " + sp.getString("name", "MP3 choisi"));
        } else {
            current.setText("Son actuel : bip par defaut");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        File tmp = new File(getFilesDir(), "tmp.mp3");
        InputStream in = null;
        OutputStream out = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) throw new IOException("flux null pour " + uri);
            out = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close(); out = null;
            in.close(); in = null;

            // verifie que le fichier est lisible comme audio
            MediaPlayer check = new MediaPlayer();
            try {
                FileInputStream fis = new FileInputStream(tmp);
                check.setDataSource(fis.getFD());
                fis.close();
                check.prepare();
            } finally {
                check.release();
            }

            File dest = new File(getFilesDir(), SoundUtil.FILE_NAME);
            if (dest.exists()) dest.delete();
            if (!tmp.renameTo(dest)) throw new IOException("renommage impossible");

            getSharedPreferences("prefs", MODE_PRIVATE).edit()
                    .putString("name", displayName(uri)).commit();
            refreshCurrent();
            Toast.makeText(this, "Son enregistre", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            tmp.delete();
            Toast.makeText(this, "Erreur : " + e.getClass().getSimpleName() + " - "
                    + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
        }
    }

    private String displayName(Uri uri) {
        String name = null;
        try {
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) name = c.getString(idx);
                }
                c.close();
            }
        } catch (Exception ignored) {}
        if (name == null) name = uri.getLastPathSegment();
        return name != null ? name : "MP3 choisi";
    }

    @Override
    protected void onDestroy() {
        if (testPlayer != null) { SoundUtil.release(testPlayer); testPlayer = null; }
        super.onDestroy();
    }
}
