package eun.xposed.gpsnotification;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.widget.Toast;


public class SettingsActivity extends PreferenceActivity {

	private Preference iconposition;
	private Preference icon;
	private Preference animationspeed;
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
		bindPreferenceSummaryToValue(iconposition);
		bindPreferenceSummaryToValue(icon);
		bindPreferenceSummaryToValue(animationspeed);
		
			
		bInit = true;
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
			
			if (bInit)
				Toast.makeText(SettingsActivity.this, "Restart to apply changes!", Toast.LENGTH_LONG).show();
			return retVal;
		}
	};

	
	private void bindPreferenceSummaryToValue(Preference preference) {
		preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
		sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(), "1"));
		
	}
}
