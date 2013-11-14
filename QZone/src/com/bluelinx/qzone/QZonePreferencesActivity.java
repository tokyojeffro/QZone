package com.bluelinx.qzone;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class QZonePreferencesActivity extends PreferenceActivity {
	
	public static final String PREF_QZONE_SETTING = "PREF_QZONE_SETTING";
	public static final String PREF_SCAN_FREQ = "PREF_SCAN_FREQ";

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.userpreferences);
		
	}
}