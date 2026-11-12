package com.example.volmp3;

import android.content.*;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        c.startService(new Intent(c, VolumeService.class));
    }
}
