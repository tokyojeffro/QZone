package com.bluelinx.qzone;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;

public class QZoneSplashEmergencyAlertActivity extends Activity {
	
    protected int _splashTime = 5000; 

    private Thread splashTread;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.emergency_alert);

        // thread for displaying the SplashScreen
        splashTread = new Thread() {
            @Override
            public void run() {
                try {
                    synchronized(this){

                            //wait 5 sec
                            wait(_splashTime);
                    }

                } catch(InterruptedException e) {}
                finally {
                    finish();
                }
            }
        };

        splashTread.start();
    }

    //Function that will handle the touch
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            synchronized(splashTread){
                    splashTread.notifyAll();
            }
        }
        return true;
    }
}