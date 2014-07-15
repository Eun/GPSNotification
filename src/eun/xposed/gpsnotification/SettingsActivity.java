/*
 * Copyright (C) 2014 GPSNotification
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eun.xposed.gpsnotification;

import java.io.IOException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;


public class SettingsActivity extends PreferenceActivity {

	private Preference iconposition;
	private Preference icon;
	private Preference animationspeed;
	private Preference permamode;
	private Preference gpsstatus;
	private Object dIconPosition, dIcon, dAnimationSpeed, dPermaMode, dGpsStatus;
	private Boolean bInit;
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
				
		this.getActionBar().setDisplayHomeAsUpEnabled(true);
		getPreferenceManager().setSharedPreferencesMode(Context.MODE_WORLD_READABLE);
		addPreferencesFromResource(R.xml.pref_general);
		bInit = false;
		
		iconposition = findPreference("iconposition");
		icon = findPreference("icon");
		animationspeed = findPreference("animationspeed");
		permamode = findPreference("permamode");
		gpsstatus = findPreference("gpsstatus");
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		dIconPosition = prefs.getString("iconposition", "1");
		dIcon = prefs.getString("icon", "0");
		dAnimationSpeed = prefs.getString("animationspeed", "500");
		dPermaMode = prefs.getBoolean("permamode", false);
		dGpsStatus = prefs.getBoolean("gpsstatus", false);
		
		bindPreferenceSummaryToValue(iconposition, dIconPosition);
		bindPreferenceSummaryToValue(icon, dIcon);
		bindPreferenceSummaryToValue(animationspeed, dAnimationSpeed);
		bindPreferenceSummaryToValue(permamode, dPermaMode);
		bindPreferenceSummaryToValue(gpsstatus, dGpsStatus);
		bInit = true;
	}
	
	private Boolean SettingsModified()
	{
		Object dIconPosition, dIcon, dAnimationSpeed, dPermaMode, dGpsStatus;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		dIconPosition = prefs.getString("iconposition", "1");
		dIcon = prefs.getString("icon", "0");
		dAnimationSpeed = prefs.getString("animationspeed", "500");
		dPermaMode = prefs.getBoolean("permamode", false);
		dGpsStatus = prefs.getBoolean("gpsstatus", false);
		return !(dIconPosition.equals(this.dIconPosition) && dIcon.equals(this.dIcon) && dAnimationSpeed.equals(this.dAnimationSpeed) && dPermaMode.equals(this.dPermaMode) && dGpsStatus.equals(this.dGpsStatus));
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) { 
	    switch (item.getItemId()) {
	        case android.R.id.home:
	        	super.onBackPressed();
	            return true;
	            default:
	            return super.onOptionsItemSelected(item); 
	    }
	}

		

	private Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object value) {
			boolean retVal = true;
			String stringValue = value.toString();
			if (preference == icon || preference == iconposition) {
				// For list preferences, look up the correct display value in
				// the preference's 'entries' list.
				ListPreference listPreference = (ListPreference) preference;
				int index = listPreference.findIndexOfValue(stringValue);

				// Set the summary to reflect the new value.
				preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);				
			}
			else if (preference == animationspeed) {
				if (Integer.valueOf((String) value) < 100)
				{
					value = "100";
					SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(preference.getContext());
					Editor editor = prefs.edit();
					editor.putString("animationspeed", "100");
					editor.commit();
					retVal = false;
				}
				preference.setSummary(value + "ms");	
			}
			
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(preference.getContext());
			Editor editor = prefs.edit();
			if (value.getClass() == String.class)
				editor.putString(preference.getKey(), (String) value);
			else if (value.getClass() == Boolean.class)
				editor.putBoolean(preference.getKey(), (Boolean) value);
			else if (value.getClass() == int.class || value.getClass() == Integer.class)
				editor.putInt(preference.getKey(), (Integer) value);
			editor.commit();
			
			if ((prefs.getString("iconposition", "1").equals("1")))
			{
				gpsstatus.setEnabled(true);
			}
			else
			{
				gpsstatus.setEnabled(false);
			}
			
			if (bInit)
			{
				
				Context context = preference.getContext();
				if (SettingsModified())				
				{
					Notification n = new Notification.Builder(context)
	                .setSmallIcon(R.drawable.jb_gps_on_left)
	                .setContentTitle(context.getString(R.string.appname))
	                .setStyle(new Notification.BigTextStyle().bigText(context.getString(R.string.notification_restart))) 
	                .setTicker(context.getString(R.string.notification_restart))
	                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, new Intent(), 0))
	                .build();
					n.flags = Notification.DEFAULT_LIGHTS | Notification.FLAG_AUTO_CANCEL;
					((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(0, n); 
				}
				else
				{
					Log.d("GPSNotification", "cancel");
					((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(0); 
				}
			}
			return retVal;
		}
	};

	
	private void bindPreferenceSummaryToValue(Preference preference, Object defaultValue)
	{
		preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
		sBindPreferenceSummaryToValueListener.onPreferenceChange(
				preference,
				defaultValue
			);
	}
}
