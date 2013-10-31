package com.bluelinx.qzone;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class QZoneBluetoothIntentReceiver extends BroadcastReceiver {
	
	public static final String TAG = "Bluetooth";

	public static final String DEVICE_FOUND = 
			"com.bluelinx.qzone.DEVICE_FOUND";
	
	public static final String ACTIVATE_QZONE = 
			"com.bluelinx.qzone.ACTIVATE_QZONE";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		try
		{
			String action = intent.getAction();
						
			// When discovery finds a device
			if (BluetoothDevice.ACTION_FOUND.equals(action) )
			{
				// Get the BluetoothDevice object from the intent
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				
				String deviceName = device.getName();
				Log.d(TAG,"Device " + deviceName + " Found during scan\n");
			
				// TODO Remove hardcoded device target name
				String target = "Jeff5";
				String emergency_target = "Jeff iPad";
				
				// Check to see if the device should change behavior
				if (deviceName.equals(target))
				{
					// Send the intent to service to trigger a change in Q-Zone status
					Log.d(TAG,"Q-Zone Node found during scan\n");
					Intent startIntent = new Intent(context, QZoneService.class);
					startIntent.putExtra(QZoneMainActivity.svcName,QZoneMainActivity.QZONE_NODE_FOUND);
					context.startService(startIntent);
				}
				else if (deviceName.equals(emergency_target)) {
					Log.d(TAG,"Emergency Beacon Found During Scan\n");
					Intent startIntent = new Intent(context, QZoneService.class);
					startIntent.putExtra(QZoneMainActivity.svcName,QZoneMainActivity.EMERGENCY_BEACON_FOUND);
					context.startService(startIntent);					
				}
			}
			else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
			{
				// need to send the corresponding event to the service through the intent
				Log.d(TAG,"Discovery Scan complete\n");
				Intent startIntent = new Intent(context, QZoneService.class);
				startIntent.putExtra(QZoneMainActivity.svcName,QZoneMainActivity.QZONE_SCAN_COMPLETE);
				context.startService(startIntent);	
			}
		}
		catch (Exception e)
		{
			System.out.println("Broadcast Error " + e.toString() );
		}
	}
}