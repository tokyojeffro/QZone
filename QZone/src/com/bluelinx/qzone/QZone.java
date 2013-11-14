package com.bluelinx.qzone;

import android.util.Log;

public class QZone {
	private static final String TAG="State";
	
	public static final String QZONE_DISABLED = "Disabled";
	public static final String QZONE_ENABLED_SCAN_PENDING = "Enabled Scan Pending";
	public static final String QZONE_ENABLED_SCAN_ACTIVE = "Enabled Scan Active";
	public static final String QZONE_ACTIVE_SCAN_PENDING = "Active Scan Pending";
	public static final String QZONE_ACTIVE_SCAN_ACTIVE_NODE_NOT_FOUND = "Active Scan Active Node Not Found";	
	public static final String QZONE_ACTIVE_SCAN_ACTIVE_NODE_FOUND = "Active Scan Active Node Found";
	
	private String qzoneState = QZONE_DISABLED;
	
	public String getState () {
		return qzoneState;
	}
	
	public String setState (String newState) {
		Log.d(TAG, qzoneState + " -> " + newState + "\n");
		qzoneState = newState;
		return qzoneState;
	}
	
	public void logState() {
		Log.d(TAG,qzoneState + "\n");
	}
	
}