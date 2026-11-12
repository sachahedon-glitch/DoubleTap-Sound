package com.example.volmp3;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;

public class VolumeService extends Service {

    private static final String VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION";
    private static final String EXTRA_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE";
    private static final String EXTRA_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE";
    private static final long WINDOW_MS = 600;

    private AudioManager am;
    private final Handler handler = new Handler();
    private int refVolume;
    private int pressCount = 0;
    private volatile boolean running = false;
    private Thread silenceThread;
    private MediaPlayer mp;
    private PowerManager.WakeLock wakeLock;

    private final Runnable resetCount = new Runnable() {
        @Override public void run() { pressCount = 0; }
    };

    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (!VOLUME_CHANGED_ACTION.equals(i.getAction())) return;
            if (i.getIntExtra(EXTRA_STREAM_TYPE, -1) != AudioManager.STREAM_MUSIC) return;

            int value = i.getIntExtra(EXTRA_STREAM_VALUE, -1);
            if (value == refVolume) return;

            boolean up = value > refVolume;
            am.setStreamVolume(AudioManager.STREAM_MUSIC, refVolume, 0);
            if (up) onVolumeUp();
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_STICKY;
        running = true;

        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        refVolume = Math.max(1, Math.min(max - 1, (max * 2) / 3));
        am.setStreamVolume(AudioManager.STREAM_MUSIC, refVolume, 0);

        registerReceiver(volumeReceiver, new IntentFilter(VOLUME_CHANGED_ACTION));

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VolMp3");
        wakeLock.acquire();

        startSilence();

        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);
        Notification n = new Notification.Builder(this)
                .setContentTitle("DoubleTap Sound actif")
                .setContentText("Appuie 2 fois sur volume + pour lancer le son")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentIntent(pi)
                .build();
        startForeground(1, n);

        return START_STICKY;
    }

    private void onVolumeUp() {
        pressCount++;
        handler.removeCallbacks(resetCount);
        if (pressCount >= 2) {
            pressCount = 0;
            playSound();
        } else {
            handler.postDelayed(resetCount, WINDOW_MS);
        }
    }

    private void playSound() {
        if (mp != null) { SoundUtil.release(mp); mp = null; }
        mp = SoundUtil.create(this);
        if (mp == null) return;
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer p) { SoundUtil.release(p); if (p == mp) mp = null; }
        });
        mp.start();
    }

    private void startSilence() {
        silenceThread = new Thread(new Runnable() {
            @Override public void run() {
                int rate = 8000;
                int buf = AudioTrack.getMinBufferSize(rate,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, rate,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        buf, AudioTrack.MODE_STREAM);
                t.play();
                byte[] zeros = new byte[buf];
                while (running) t.write(zeros, 0, zeros.length);
                t.stop();
                t.release();
            }
        });
        silenceThread.start();
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(volumeReceiver); } catch (Exception ignored) {}
        if (mp != null) { SoundUtil.release(mp); mp = null; }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
