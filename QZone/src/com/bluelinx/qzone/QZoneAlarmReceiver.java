package com.bluelinx.qzone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class QZoneAlarmReceiver extends BroadcastReceiver {

	public static final String ACTION_REFRESH_QZONE_ALARM = 
			"com.bluelinx.qzone.ACTION_REFRESH_QZONE_ALARM";

	public static final String ACTION_START_QZONE = 
			"com.bluelinx.qzone.ACTION_START_QZONE";
	
	public static final String ACTION_SCAN_COMPLETE=
			"com.bluelinx.qzone.ACTION_SCAN_COMPLETE";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		int messageCode = intent.getIntExtra(QZoneMainActivity.svcName,0);
		
		Log.d("Timer","Timer Expired. Message Code -" + String.valueOf(messageCode) +"\n");
		Intent startIntent = new Intent(context, QZoneService.class);
		startIntent.putExtra(QZoneMainActivity.svcName,messageCode);
		context.startService(startIntent);
	}
	
	
}