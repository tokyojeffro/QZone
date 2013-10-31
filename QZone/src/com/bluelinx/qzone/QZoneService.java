package com.bluelinx.qzone;

import java.io.IOException;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.Notification.Builder;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

public class QZoneService extends Service {
	
	public static final int NOTIFICATION_ID = 1;
	public static String TAG = "Service";
//	public static final int QZONE_ACTIVE_SCAN_TIMEOUT = 240;  // in seconds
// uncomment the other one once everything works
// TODO
	public static final int QZONE_ACTIVE_SCAN_TIMEOUT = 20; // in seconds
	public static final int QZONE_INACTIVE = 0;
	public static final int QZONE_ACTIVE = 1;
	public static final int MSG_REGISTER_CLIENT = 1;
	public static final int MSG_UNREGISTER_CLIENT = 2;
	public static final int MSG_STATE_REQUEST = 3;
	public static final int MSG_STATE_RESPONSE = 4;
	public static final int MSG_VIEW_REFRESH = 5;
	public static final int MSG_STOP_ALERT = 6;
	public String currentQZoneState = null;

	final Messenger mMessenger = new Messenger(new IncomingHandler()); // Target we publish for clients to send messages to IncomingHandler.

	private Messenger mClient = null;
	
	private AlarmManager alarmManager;
	private Intent alarmIntent = null;
	private PendingIntent pendingIntent = null;
	private Notification.Builder qzoneNotificationBuilder;
	private NotificationManager notificationManager = null;
	private AudioManager mAudioManager = null;
	private QZone qzone;
	private BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
	private int previousRingVolume;
	private int previousRingMode;
	private int previousNotificationVolume;
	private boolean qzoneEnabled = false;
	private SharedPreferences prefs = null;
	private Context context = null;
	private TelephonyManager telephonyManager = null;
	private CallStateListener callStateListener = null;
	private static boolean isRunning = false;
	private MediaPlayer mediaPlayer = null;
	
	
	public static boolean isRunning()
	{
		return isRunning;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return mMessenger.getBinder();
	}
	
	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			
			switch (msg.what) {
			case MSG_REGISTER_CLIENT:
				Log.d(TAG,"Message from Activity Received - Register Client\n");
				mClient = msg.replyTo;
				changeQZoneState(currentQZoneState);
				break;
			case MSG_UNREGISTER_CLIENT:
				Log.d(TAG,"Message from Activity Received - Unregister Client\n");
				mClient = null;
				break;
			case MSG_STATE_REQUEST:
				Log.d(TAG,"Message from Activity Received - Get State\n");
				changeQZoneState(currentQZoneState);
				break;
			case MSG_STOP_ALERT:
				Log.d(TAG,"Message from Activity Recieved - Stop Alert\n");
				if (mediaPlayer != null) {
					if (mediaPlayer.isPlaying()) {
						mediaPlayer.stop();						
					}
					mediaPlayer.release();
					mediaPlayer = null;
				}
				break;
			default:
				super.handleMessage(msg);
			}
		}
	}
	
	@SuppressLint("NewApi")
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		currentQZoneState = qzone.getState();
			
		if (qzoneEnabled) {
			
			int messageCode = intent.getIntExtra(QZoneMainActivity.svcName,0);
			
			if ( ((messageCode==QZoneMainActivity.QZONE_ENABLED_START_SCAN) &&
					(currentQZoneState!=QZone.QZONE_ENABLED_SCAN_PENDING)) || 
					
					((messageCode==QZoneMainActivity.QZONE_ACTIVE_SCAN_PENDING) &&
					(currentQZoneState!=QZone.QZONE_ACTIVE_SCAN_PENDING)))
			{
				// we have received a timer timeout, but we are not supposed to be waiting for one.
				// ignore it.
				Log.d(TAG,"Timeout - " + String.valueOf(messageCode) + " received and ignored\n");
				qzone.logState();
			}
				else processMessage(messageCode);
			
			return Service.START_STICKY;
		} // end of qzoneEnabled
		else return Service.START_NOT_STICKY;
		
	}  // end of method
		
	@Override
	public void onCreate() {
		super.onCreate();
		// This is the initialize phase
		Log.d(TAG,"Initialize\n");
		isRunning = true;
		
		//  Check to see if Q-Zone is enabled by the user
		context = getApplicationContext();
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		qzoneEnabled = prefs.getBoolean(QZonePreferencesActivity.PREF_QZONE_SETTING, false);
		notificationManager = (NotificationManager)getSystemService(QZoneMainActivity.svcName);
		alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
		
		// Get an instance of AudioManager
		mAudioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
		
		callStateListener = new CallStateListener();
		
		telephonyManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
		telephonyManager.listen(callStateListener, PhoneStateListener.LISTEN_CALL_STATE);
		
		// Initialize the state machine
		qzone = new QZone();
		if (qzoneEnabled) {
			qzone.setState(QZone.QZONE_ENABLED_SCAN_PENDING);
		} else {
			qzone.setState(QZone.QZONE_DISABLED);
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		// Cancel notifications
		notificationManager.cancelAll();
		
		// cancel current Alarm timer
		alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
		alarmManager.cancel(pendingIntent);
		
	}
	
	@SuppressLint("NewApi")
	private void processMessage(int messageCode) {
		
		switch (messageCode)
		{
		case QZoneMainActivity.QZONE_ENABLED_START_SCAN:
		{
			// cancel current Alarm timer
			// this new code should cancel ALL pending alarms for this Alarm Manager instance.
			alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
			alarmManager.cancel(pendingIntent);

			
			// need to reset our active status and restart the discovery process
			changeQZoneState(QZone.QZONE_ENABLED_SCAN_ACTIVE);
			
			// First, trigger a Bluetooth scan
			bluetooth.startDiscovery();
				
			Log.d(TAG,"Q-Zone Enabled Start Discovery\n");
			break;
		}
		case QZoneMainActivity.QZONE_ACTIVE_SCAN_PENDING:
		{				
			// cancel current Alarm timer
			// this new code should cancel ALL pending alarms for this Alarm Manager instance.
			alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
			alarmManager.cancel(pendingIntent);

			// QZone is already active, we need to start a new Discovery cycle
			// First, trigger a Bluetooth scan
			bluetooth.startDiscovery();

			Log.d(TAG,"Q-Zone Active Start Discovery\n");
			
			changeQZoneState(QZone.QZONE_ACTIVE_SCAN_ACTIVE_NODE_NOT_FOUND);				
			break;
		}
		case QZoneMainActivity.QZONE_NODE_FOUND:
		{
			Log.d(TAG,"Q-Zone Node Found\n");
			// Stop the current Bluetooth scan - we have found what we are looking for.
			if (bluetooth.isDiscovering()) {
				bluetooth.cancelDiscovery();
				Log.d(TAG,"Bluetooth Discovery Canceled\n");
			}
			
			// need to go into Q-Zone active status here if we were not active before
			if (currentQZoneState.equals(QZone.QZONE_ENABLED_SCAN_ACTIVE) ||
					currentQZoneState.equals(QZone.QZONE_ENABLED_SCAN_PENDING))
			{
				Log.d(TAG,"Entered a Q-Zone\n");
				
				// toggling from inactive to active.  set the notifications, change ring volume, set a different timer for refresh
				// Need to get and save current ringmode, current ring volume

				// Ringer Volume
				int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
				previousRingVolume = currentVolume;
						
				// Silent Mode
				int currentRingerMode = mAudioManager.getRingerMode();
				previousRingMode = currentRingerMode;
				
				// need to set to vibrate, turn down ringer volume, flash lights, send toast
				// and post the qzone logo to the status bar.  ;-)
				mAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
				mAudioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0);
				
				// Need to build the notification
				qzoneNotificationBuilder = new Notification.Builder(getApplicationContext());
				
				String tickerText = getString(R.string.qzone_active);
						
				qzoneNotificationBuilder.setSmallIcon(R.drawable.qzonesmall)
						.setTicker(tickerText)
						.setWhen(System.currentTimeMillis())
						.setDefaults(Notification.DEFAULT_LIGHTS |
							Notification.DEFAULT_VIBRATE )
						.setOnlyAlertOnce(true)
						.setLights(Color.RED, 0, 1);					

				Notification notification = qzoneNotificationBuilder.build();
				notificationManager.notify(NOTIFICATION_ID, notification);
				
				CharSequence text = getString(R.string.qzone_active);
				int duration = Toast.LENGTH_SHORT;
				Toast toast = Toast.makeText(context, text, duration);
				toast.show();
			
			} else if (currentQZoneState.equals(QZone.QZONE_ACTIVE_SCAN_ACTIVE_NODE_NOT_FOUND) ||
					currentQZoneState.equals(QZone.QZONE_ACTIVE_SCAN_ACTIVE_NODE_FOUND) ||
					currentQZoneState.equals(QZone.QZONE_ACTIVE_SCAN_PENDING)) {
				// this is the case where we are already in a Q-Zone and need to stay in the Q-Zone
				// do nothing
				Log.d(TAG,"Q-Zone Active and remains Active\n");	

			} else {
				Log.d(TAG,"Unknown state on Node Found\n");
			}

			// start new QZone Active alarm timer
			setTimer(QZoneMainActivity.QZONE_ACTIVE_SCAN_PENDING);					
			
			// Change state to QZone Active scan pending
			changeQZoneState(QZone.QZONE_ACTIVE_SCAN_PENDING);
			break;
		}
		case QZoneMainActivity.QZONE_SCAN_COMPLETE:
		{
			// This just means that Discovery completed.
			Log.d(TAG,"Discovery Scan Complete\n");
			
			// Was QZone Active?
			if (currentQZoneState.equals(QZone.QZONE_ACTIVE_SCAN_ACTIVE_NODE_NOT_FOUND))
			{
				// A scan completed while Q-Zone was ACTIVE.  This means we must toggle from ACTIVE to ENABLED
				// No node was found (or else we would have cancelled discovery and changed state)
				Log.d(TAG,"Left the Q-Zone\n");
				
				// put things back where they belong
				
				// Set previous ring mode
				mAudioManager.setRingerMode(previousRingMode);
				
				// Set previous ring volume
				mAudioManager.setStreamVolume(AudioManager.STREAM_RING, previousRingVolume, 0);
				
				// Cancel notifications
				notificationManager.cancelAll();
				
				// Notify change in status via Toast
				CharSequence text = getString(R.string.qzone_inactive);
				int duration = Toast.LENGTH_SHORT;
				Toast toast = Toast.makeText(context, text, duration);
				toast.show();
				
				// Now set up the next Q-Zone Timeout timer
				setTimer(QZoneMainActivity.QZONE_ENABLED_SCAN_PENDING);					
				changeQZoneState(QZone.QZONE_ENABLED_SCAN_PENDING);
				
			}  // end of if QZone active but no new nodes found
			else if (currentQZoneState.equals(QZone.QZONE_ACTIVE_SCAN_ACTIVE_NODE_FOUND)) {
				// start new QZone Active alarm timer
				setTimer(QZoneMainActivity.QZONE_ACTIVE_SCAN_PENDING);
				changeQZoneState(QZone.QZONE_ACTIVE_SCAN_PENDING);
				
			} else if (currentQZoneState.equals(QZone.QZONE_ENABLED_SCAN_ACTIVE) ||
					currentQZoneState.equals(QZone.QZONE_ENABLED_SCAN_PENDING)) {
				// Now set up the next Q-Zone Timeout timer
				setTimer(QZoneMainActivity.QZONE_ENABLED_SCAN_PENDING);
				changeQZoneState(QZone.QZONE_ENABLED_SCAN_PENDING);					
			}
			
			break;
		} // end of case
		case QZoneMainActivity.EMERGENCY_BEACON_FOUND:
		{
			Log.d(TAG,"Emergency Alert Received\n");
			//  Need to save off the old ring tone so we can put it back
			// Ringer Volume
			previousNotificationVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);

			// Make sure the volume is MAX
			int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
			mAudioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, maxVolume, AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_PLAY_SOUND | AudioManager.FLAG_VIBRATE);
			
			// Silent Mode
			previousRingMode = mAudioManager.getRingerMode();

			// Make sure the mode is normal (so the notification will be audible)
			mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
			
			// New code to play notification
			// I don't need to change the ring tone.  I can just play the notification.
			// Need to build the text and icon notifications			Notification.Builder alertNotificationBuilder = new Notification.Builder(getApplicationContext());

			NotificationManager alertNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			
			String soundTarget = Uri.decode(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getPackageName() + "/" + R.raw.alarm);
			
			Uri soundUri = Uri.parse(soundTarget);
			
			Notification.Builder alertNotificationBuilder = new Notification.Builder(getApplicationContext());
			
			String tickerText = getString(R.string.emergency_alert);
					
			alertNotificationBuilder.setTicker(tickerText)
				.setWhen(System.currentTimeMillis())
				.setLights(Color.RED, 0, 1)
				.setSound(soundUri)
	            .setOngoing(true);					

			Notification notification = alertNotificationBuilder.build();
			alertNotificationManager.notify(NOTIFICATION_ID, notification);
	
			mediaPlayer = new MediaPlayer();
			
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
			mediaPlayer.setLooping(true);
			try {
			mediaPlayer.setDataSource(getApplicationContext(), soundUri);
			}
			catch (IOException e) {
				Log.d(TAG,"IOException on setting alert sound target.\n");
			}
			try {
			mediaPlayer.prepare();
			}
			catch (IOException e) {
				Log.d(TAG,"IOException on prepare.\n");
			}
			
			mediaPlayer.start();
			
			CharSequence text = getString(R.string.emergency_alert);
			int duration = Toast.LENGTH_SHORT;
			Toast toast = Toast.makeText(context, text, duration);
			toast.show();			
						
			//  Need to set a timer so it turns off in 5 secs (hardcode test value)
					            
			break;
			} // end of case EMERGENCY ALERT
		} // end of switch	
	} // end of method processMessage
	
	private void changeQZoneState (String newQZoneState) {

		if (mClient!=null) {
			Bundle b = new Bundle();
			Message msgOut = Message.obtain(null, MSG_STATE_RESPONSE);
			
			try {
				b.putString(QZoneMainActivity.QZONE_CURRENT_STATE, newQZoneState);
				msgOut.setData(b);
				mClient.send(msgOut);
				Log.d(TAG,"Message sent to Activity." + newQZoneState + "\n");
			}
			catch (RemoteException e) {
				Log.e(TAG,"Send message failed");
			}
		}
		currentQZoneState = qzone.setState(newQZoneState);
	}
	
	private void setTimer(int qzoneAlarmType) {

		// cancel current Alarm timer
		// this new code should cancel ALL pending alarms for this Alarm Manager instance.
		alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
		alarmManager.cancel(pendingIntent);

		alarmIntent = new Intent (context, QZoneAlarmReceiver.class);
		int scanFreq = 0;
		
		if (qzoneAlarmType==QZoneMainActivity.QZONE_ENABLED_SCAN_PENDING) {
			// set the stuff up for the enabled scan
			alarmIntent.putExtra(QZoneMainActivity.svcName,QZoneMainActivity.QZONE_ENABLED_START_SCAN);
			pendingIntent = PendingIntent.getBroadcast(context, QZoneMainActivity.QZONE_ENABLED_START_SCAN, alarmIntent, 0);
			scanFreq = Integer.parseInt(prefs.getString(QZonePreferencesActivity.PREF_SCAN_FREQ, "60"));
			Log.d(TAG,"Q-Zone Enabled. Timer renewed");
		}
		else if (qzoneAlarmType==QZoneMainActivity.QZONE_ACTIVE_SCAN_PENDING) {
			// set the stuff up for the active scan
			alarmIntent.putExtra(QZoneMainActivity.svcName,QZoneMainActivity.QZONE_ACTIVE_SCAN_PENDING);
			pendingIntent = PendingIntent.getBroadcast(context, QZoneMainActivity.QZONE_ACTIVE_SCAN_PENDING, alarmIntent, 0);
			scanFreq = QZONE_ACTIVE_SCAN_TIMEOUT;
			Log.d(TAG,"Q-Zone Active. Timer renewed");
		}
		
		int alarmType = AlarmManager.ELAPSED_REALTIME_WAKEUP;	
		
		long timeToRefresh = SystemClock.elapsedRealtime() + scanFreq*1000;
		
		alarmManager.setInexactRepeating(alarmType, timeToRefresh, scanFreq*1000, pendingIntent);
		Log.d(TAG,"Timer Length " + String.valueOf(scanFreq) + " Set\n");
	}
	
	public class CallStateListener extends PhoneStateListener {
		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			switch (state) {
			case TelephonyManager.CALL_STATE_RINGING:
			{
				if ( (currentQZoneState.equals(QZone.QZONE_ACTIVE_SCAN_ACTIVE_NODE_FOUND)) ||
					(currentQZoneState.equals(QZone.QZONE_ACTIVE_SCAN_ACTIVE_NODE_NOT_FOUND)) ||
					(currentQZoneState.equals(QZone.QZONE_ACTIVE_SCAN_PENDING))) {
					
					Toast.makeText(context, "You are in a Q-Zone.  Do you really want to answer?", Toast.LENGTH_LONG).show();
					} // end of if Qzone is active
				break;
			} // end of case
			} // end of switch
		} // end of onCallStateChanged
	} // end of CallStateListener
	
	
}