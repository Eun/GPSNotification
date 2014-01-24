package eun.xposed.gpsnotification;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

public class SettingsActivity extends PreferenceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getPreferenceManager().setSharedPreferencesMode(Context.MODE_WORLD_READABLE);
		addPreferencesFromResource(R.xml.pref_general);
		bindPreferenceSummaryToValue(findPreference("iconposition"));
	}
	
	
	private Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object value) {
			String stringValue = value.toString();
			if (preference instanceof ListPreference) {
				// For list preferences, look up the correct display value in
				// the preference's 'entries' list.
				ListPreference listPreference = (ListPreference) preference;
				int index = listPreference.findIndexOfValue(stringValue);

				// Set the summary to reflect the new value.
				preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
				
				Toast.makeText(SettingsActivity.this, "Restart to apply changes!", Toast.LENGTH_LONG).show();
				
			}
			return true;
		}
	};

	
	private void bindPreferenceSummaryToValue(Preference preference) {
		preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
		
		sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(), "1"));
	}
}
