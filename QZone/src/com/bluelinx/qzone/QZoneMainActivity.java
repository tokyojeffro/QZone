package com.bluelinx.qzone;

import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class QZoneMainActivity extends Activity {
	
	public static final String TAG = "Main Activity";
	
	public static final String svcName = Context.NOTIFICATION_SERVICE;
	public static final String QZONE_STATE = "com.bluelinx.qzone.state";
	public static final String QZONE_CURRENT_STATE = "com.bluelinx.qzone.state.current";
	public static final String REQUEST = "REQUEST";
	public static final String RESPONSE = "RESPONSE";
	public static final int NOTIFICATION_ID = 1;
	public static final int QZONE_ACTIVE = 1;
	public static final int QZONE_INACTIVE = 0;
	public static final int QZONE_ENABLED_START_SCAN = 5;
	public static final int QZONE_ENABLED_SCAN_PENDING = 20;
	public static final int QZONE_NODE_FOUND = 99;
	public static final int EMERGENCY_BEACON_FOUND = 13;
	public static final int QZONE_SCAN_COMPLETE = 100;
	public static final int QZONE_ACTIVE_SCAN_PENDING = 50;

	BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
	private AudioManager mAudioManager;
	private Context mContext;
	private int scanFreq;
	private boolean qzoneEnabled;
	private static final int SHOW_PREFERENCES = 1;
	static final int UPDATE_VIEW = 5;
	private String qzoneState = null;
	
	Messenger mService = null;
	boolean mIsBound = false;
	final Messenger mMessenger = new Messenger(new IncomingHandler());
	
	SettingsContentObserver mSettingsContentObserver;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_qzone_main);
		updateFromPreferences();
		mContext = this;
		
		if (qzoneEnabled) {	
			bluetooth.enable();
			
			// Start the QZone intent service (it starts the timer)
			Intent msgIntent = new Intent(this, QZoneService.class);
			msgIntent.putExtra(svcName,QZONE_ENABLED_START_SCAN);
			msgIntent.putExtra(QZONE_STATE,REQUEST);
			startService(msgIntent);
		}
		
		mSettingsContentObserver = new SettingsContentObserver(new Handler(), this);
		getApplicationContext().getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, mSettingsContentObserver);
		
		updateViewValues();
		
		CheckIfServiceIsRunning();
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		if (requestCode == SHOW_PREFERENCES) {
			updateFromPreferences();
			updateViewValues();
		} else if (requestCode == UPDATE_VIEW) {
			updateViewValues();
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.qzone_main, menu);
		return true;
	}
	
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		
		Intent i = new Intent(this, QZonePreferencesActivity.class);
		startActivityForResult(i, SHOW_PREFERENCES);
		return true;
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
		{
			updateViewValues();
		}
		return super.onKeyUp(keyCode, event);
	}
	
	private void updateFromPreferences() {
		Context context = getApplicationContext();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		
		scanFreq = Integer.parseInt(prefs.getString(QZonePreferencesActivity.PREF_SCAN_FREQ, "30"));
		
		qzoneEnabled = prefs.getBoolean(QZonePreferencesActivity.PREF_QZONE_SETTING, true);	
	}
	
	Runnable updateView = new Runnable() {
		@Override
		public void run() {
			updateViewValues();
		}
	};
	
	void updateViewValues () {
		
		
		TextView bt_discovery_state = (TextView)findViewById(R.id.bluetooth_status);
			
		if (bluetooth.isDiscovering()) 
				{
			bt_discovery_state.setText(R.string.scanning_status);
			}
		else {
			
			bt_discovery_state.setText(R.string.waiting_status);
		}
		
		TextView qzone_interval = (TextView)findViewById(R.id.qzone_timing_interval);
		qzone_interval.setText(String.valueOf(scanFreq));
		
		// Do the Audio Manager creation stuff here
		// Get an instance of AudioManager
		mAudioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
		// Extract the settings info and shove it into the view variables Ringer Volume

		int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
		TextView currentRingerVolume = (TextView)findViewById(R.id.current_ringerVolume);
		currentRingerVolume.setText(String.valueOf(currentVolume));
		
		// Silent Mode
		int currentRingerMode = mAudioManager.getRingerMode();
		TextView currentRingerState = (TextView)findViewById(R.id.silent_status);
		if (currentRingerMode == AudioManager.RINGER_MODE_SILENT) {
			currentRingerState.setText(R.string.active_status);
		} else {
			currentRingerState.setText(R.string.inactive_status);
		}

		// Vibrate Mode Status
		TextView currentVibeState = (TextView)findViewById(R.id.vibe_status);
		if (currentRingerMode == AudioManager.RINGER_MODE_VIBRATE) {
			currentVibeState.setText(R.string.active_status);
		} else {
			currentVibeState.setText(R.string.inactive_status);
		}
			
		// Q-Zone Status
		TextView currentQZoneState = (TextView)findViewById(R.id.qzone_status);
		if (qzoneState != null) {
			if ((qzoneState == QZone.QZONE_ACTIVE_SCAN_ACTIVE_NODE_FOUND) ||
					(qzoneState == QZone.QZONE_ACTIVE_SCAN_ACTIVE_NODE_NOT_FOUND) ||
					(qzoneState == QZone.QZONE_ACTIVE_SCAN_PENDING))
			{
				currentQZoneState.setText(R.string.qzone_active);
			} else {
				currentQZoneState.setText(R.string.qzone_inactive);
			}
		} else currentQZoneState.setText(R.string.qzone_inactive);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
        try {
            doUnbindService();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to unbind from the service", t);
        }
	}
	
	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			Log.d(TAG,"Message received from Service\n");
			switch (msg.what) {
			case QZoneService.MSG_STATE_RESPONSE:
				String str1 = msg.getData().getString(QZONE_CURRENT_STATE);
				qzoneState = str1;
				Log.d(TAG,"State response received - " + qzoneState + "\n");
				updateViewValues();
				break;
			case QZoneService.MSG_VIEW_REFRESH:
				Log.d(TAG,"View refresh request received.\n");
				if (mIsBound) {
					try {
						Message msgOut = Message.obtain(null, QZoneService.MSG_STATE_REQUEST);
						msgOut.replyTo = mMessenger;
						mService.send(msgOut);
					} catch (RemoteException e) {
						// Log the exception
						Log.d(TAG,"Exception -" + e + " \n");	
					}
				}
				break;
			default:
				super.handleMessage(msg);
				updateViewValues();
			}  // end of switch
			updateViewValues();	
		} // end of handleMessage
	}  // end of IncomingHandler class
	
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName name, IBinder service) {
			mService = new Messenger(service);
			Log.d(TAG,"Attached to service. \n");
			try {
				Message msg = Message.obtain(null, QZoneService.MSG_REGISTER_CLIENT);
				msg.replyTo = mMessenger;
				mService.send(msg);
			} catch (RemoteException e) {
				// Log the exception
				Log.d(TAG,"Exception -" + e + " \n");	
			}
		}
		
		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been unexpectedly disconnected (crashed)
			mService = null;
			Log.d(TAG,"Service disconnected unexpectedly.\n");
		}
	};
	
	private void CheckIfServiceIsRunning() {
		// If the service is running when the activity starts, we want to automatically bind to it
		if (QZoneService.isRunning()) {
			doBindService();
		}
	}
	
	 void doBindService() {
	        bindService(new Intent(this, QZoneService.class), mConnection, Context.BIND_AUTO_CREATE);
	        mIsBound = true;
			Log.d(TAG,"Service is Bound.\n");
	    }
	    void doUnbindService() {
	        if (mIsBound) {
	            // If we have received the service, and hence registered with it, then now is the time to unregister.
	            if (mService != null) {
	                try {
	                    Message msg = Message.obtain(null, QZoneService.MSG_UNREGISTER_CLIENT);
	                    msg.replyTo = mMessenger;
	                    mService.send(msg);
	                } catch (RemoteException e) {
	                    // There is nothing special we need to do if the service has crashed.
	                }
	            }
	            // Detach our existing connection.
	            unbindService(mConnection);
	            mIsBound = false;
				Log.d(TAG,"Service is Unbound.\n");
	        }
	    }
	public void stopAlert(View view) {
		Log.d(TAG,"Stop Alert pressed\n");
		try {
			Message msg = Message.obtain(null, QZoneService.MSG_STOP_ALERT);
			msg.replyTo = mMessenger;
			mService.send(msg);
		} catch (RemoteException e) {
			// Log the exception
			Log.d(TAG,"Exception -" + e + " \n");	
		}

	}
}
