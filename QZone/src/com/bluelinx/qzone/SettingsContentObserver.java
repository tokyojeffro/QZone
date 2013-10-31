package com.bluelinx.qzone;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Handler;

public class SettingsContentObserver extends ContentObserver {
    
    private Context context;
    private int previousVolume;
    private AudioManager mAudioManager;

    public SettingsContentObserver( Handler handler, Context c) {
        super(handler);
        context=c;
        mAudioManager = (AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
		// Extract the settings info and shove it into the view variables
		// Ringer Volume
		previousVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
    }
    
    @Override
    public boolean deliverSelfNotifications() {
        return super.deliverSelfNotifications();
    }
    
    @Override
    public void onChange(boolean selfChange) {
    	super.onChange(selfChange);
    	
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int currentVolume = audio.getStreamVolume(AudioManager.STREAM_RING);

        if(previousVolume!=currentVolume)
        {
         //   QZoneMainActivity.updateViewValues();
        	// TODO
        }
    }
}