package eun.xposed.gpsnotification;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.XModuleResources;
import android.os.Build;
import android.provider.Settings;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class GPSNotification  extends BroadcastReceiver implements IXposedHookLoadPackage,  IXposedHookZygoteInit, IXposedHookInitPackageResources  {
	
	// Constants
	public static final String PKG = "eun.xposed.gpsnotification";
	public static final String ACTION_SETTINGS_CHANGED = PKG + ".changed";
	private static final String GPS_ENABLED_CHANGE_ACTION = "android.location.GPS_ENABLED_CHANGE";
	private static final String GPS_FIX_CHANGE_ACTION = "android.location.GPS_FIX_CHANGE";
	private static final String EXTRA_GPS_ENABLED = "enabled";
	private static final int GPS_NOTIFICATION_ID = 374203-122084;
	private static final String LOCATION_STATUS_ICON_PLACEHOLDER = "location";
	
	private static String MODULE_PATH = null;
	private Class<?> LocationControllerClass;
	private Class<?> QuickSettingsModelClass;
	private Context mContext;
	private NotificationManager nm;
	
	int stat_sys_gps_on, stat_sys_gps_off, stat_sys_gps_on_right, // drawables
		stat_sys_gps_acquiring_anim, stat_sys_gps_acquiring_anim_right, // animations
		gps_notification_found_text, gps_notification_searching_text, accessibility_location_active; // texts
	
	//InitPackageResourcesParam resparam;
	
	private GPSIconPosition IconPos;
		
	public enum GPSIconPosition
	{
		NONE,
		LEFT,
		RIGHT;

	    public static GPSIconPosition fromInteger(int value) {
	    	switch(value) {
	        case 1:
	            return LEFT;
	        case 2:
	            return RIGHT;
	        default:
	        	return NONE;
	    	}
	    }

	    public static int getValue(GPSIconPosition value) {
	    	switch(value) {
		        case LEFT:
		            return 1;
		        case RIGHT:
		            return 2;
		        default:
		        	return 0;
	        }
	    }
	}

	
	private Object mStatusBarManager;
	
	Object mLocationTile, mLocationState, mLocationCallback;
	
	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		 
		if (!lpparam.packageName.equals("com.android.systemui"))
	        return;
		
		
		if (Build.VERSION.SDK_INT >= 19)
		{
			LocationControllerClass = QuickSettingsModelClass = null;
			XSharedPreferences prefs = new XSharedPreferences(PKG);
			IconPos = GPSIconPosition.fromInteger(Integer.parseInt(prefs.getString("iconposition", String.valueOf(GPSIconPosition.getValue(GPSIconPosition.LEFT)))));
			
			LocationControllerClass = XposedHelpers.findClass("com.android.systemui.statusbar.policy.LocationController", lpparam.classLoader);
			XposedBridge.hookAllConstructors(LocationControllerClass, new XC_MethodHook() {
				 @Override
				 protected void afterHookedMethod(MethodHookParam param) throws Throwable {
					 mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
		             IntentFilter filter = new IntentFilter();
		             filter.addAction(GPS_ENABLED_CHANGE_ACTION);
		             filter.addAction(GPS_FIX_CHANGE_ACTION);
		             mContext.registerReceiver(GPSNotification.this, filter);
	                 nm = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
	                 mStatusBarManager = XposedHelpers.getObjectField(param.thisObject, "mStatusBarManager");
	                }
				 });
			XposedHelpers.findAndHookMethod(LocationControllerClass, "refreshViews", XC_MethodReplacement.DO_NOTHING);

			if (LocationControllerClass == null)
			{
				XposedBridge.log("GPSNotification: LocationController not found! Could not apply Notification.");
			}
			
			// TODO: animation in QuickSettings
			
			if (LocationControllerClass != null)
			{
				/*
				QuickSettingsModelClass = XposedHelpers.findClass("com.android.systemui.statusbar.phone.QuickSettingsModel", lpparam.classLoader);
				XposedHelpers.findAndHookMethod(QuickSettingsModelClass, "onLocationSettingsChanged", boolean.class, new XC_MethodHook() {
					 @Override
			            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						 mLocationTile = XposedHelpers.findField(QuickSettingsModelClass, "mLocationTile");
						 mLocationState = XposedHelpers.findField(QuickSettingsModelClass, "mLocationState");
						 mLocationCallback = XposedHelpers.findField(QuickSettingsModelClass, "mLocationCallback");
						 if ((Boolean)param.args[0] == false)
						 {
							 XposedHelpers.setObjectField(mLocationState, "iconId", stat_sys_gps_off);
							 XposedHelpers.callMethod(mLocationCallback, "refreshView", mLocationTile, mLocationState);
						 }
						}
						});
				*/
			}
			/*if (QuickSettingsModelClass == null)
			{
				XposedBridge.log("GPSNotification: QuickSettingsModel not found! Could not apply animation in Quicksettings.");
			}*/
					
		}
		else
		{
			LocationControllerClass = null;
		}
	}
	
	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		if (Build.VERSION.SDK_INT >= 19)
		{
			MODULE_PATH = startupParam.modulePath;
		}
	}

	
	@Override
	public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {
		if (!resparam.packageName.equals("com.android.systemui"))
			return;

	
		if (Build.VERSION.SDK_INT >= 19 && LocationControllerClass != null)
		{
			XModuleResources modRes = XModuleResources.createInstance(MODULE_PATH, resparam.res);
			stat_sys_gps_on = resparam.res.addResource(modRes, R.drawable.stat_sys_gps_on);
			stat_sys_gps_on_right = resparam.res.addResource(modRes, R.drawable.stat_sys_gps_on_right);
			stat_sys_gps_off  = resparam.res.addResource(modRes, R.drawable.stat_sys_gps_off);
			
			stat_sys_gps_acquiring_anim = resparam.res.addResource(modRes, R.drawable.stat_sys_gps_acquiring_anim);
			stat_sys_gps_acquiring_anim_right = resparam.res.addResource(modRes, R.drawable.stat_sys_gps_acquiring_anim_right);
			
			gps_notification_searching_text = resparam.res.getIdentifier("gps_notification_searching_text", "string", resparam.packageName);
			gps_notification_found_text  = resparam.res.getIdentifier("gps_notification_found_text", "string", resparam.packageName);
			accessibility_location_active = resparam.res.getIdentifier("accessibility_location_active", "string", resparam.packageName);
			
			// replace whole resource (e.g. for tiles)
			
			// Just in case QuickSettingsModel is not found,
			// we can do it a bit dirty and replace the resources at runtime.
			// Not fine, but works.
			if (QuickSettingsModelClass == null)
			{
				resparam.res.setReplacement("com.android.systemui", "drawable", "ic_qs_location_on", modRes.fwd(R.drawable.ic_qs_gps_on));
				resparam.res.setReplacement("com.android.systemui", "drawable", "ic_qs_location_off", modRes.fwd(R.drawable.ic_qs_gps_off));
				//this.resparam = resparam;
			}
		}
		
		
	}
	
	
	@Override
	public void onReceive(Context context, Intent intent) {
		final String action = intent.getAction();
		if (IconPos == GPSIconPosition.NONE)
		{
			return;
		}
		
		
		
        final boolean enabled = intent.getBooleanExtra(EXTRA_GPS_ENABLED, false);

        boolean visible;
        int textResId, icon;

        if (action.equals(GPS_FIX_CHANGE_ACTION) && enabled) {
            // GPS is getting fixes
        	 if (IconPos == GPSIconPosition.LEFT)
        		 icon = stat_sys_gps_on;
        	 else
        		 icon = stat_sys_gps_on_right;
            textResId = gps_notification_found_text;
            visible = true;
        } else if (action.equals(GPS_ENABLED_CHANGE_ACTION) && !enabled) {
            // GPS is off
            visible = false;
            icon = textResId = 0;
        } else {
            // GPS is on, but not receiving fixes
        	if (IconPos == GPSIconPosition.LEFT)
        		icon = stat_sys_gps_acquiring_anim;
        	else
        		icon = stat_sys_gps_acquiring_anim_right;
            textResId = gps_notification_searching_text;
            visible = true;
        }
        
        if (IconPos == GPSIconPosition.LEFT)
        {
	        try {
	            if (visible) {
	                Intent gpsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
	                gpsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, gpsIntent, 0);
	
	                Notification n = new Notification.Builder(mContext)
	                    .setSmallIcon(icon)
	                    .setContentTitle(mContext.getText(textResId))
	                    .setOngoing(true)
	                    .setContentIntent(pendingIntent)
	                    .build();
	
	                // Notification.Builder will helpfully fill these out for you no matter what you do
	                n.tickerView = null;
	                n.tickerText = null;
	                
	                
	                nm.notify(GPS_NOTIFICATION_ID, n);
	                 
	            } else {
	            	nm.cancel(GPS_NOTIFICATION_ID);
	            }
	        } catch (Exception ex) {
	            // well, it was worth a shot
	        }
        }
        else if (IconPos == GPSIconPosition.RIGHT)
		{
			 if (visible)
			 {
				XposedHelpers.callMethod(mStatusBarManager, "setIcon", LOCATION_STATUS_ICON_PLACEHOLDER, icon, 0, mContext.getString(accessibility_location_active));
			 }
			 else
			 {
				XposedHelpers.callMethod(mStatusBarManager, "removeIcon", LOCATION_STATUS_ICON_PLACEHOLDER);
			 }
		}
        
        /*if (QuickSettingsModelClass == null)
        {
        	XModuleResources modRes = XModuleResources.createInstance(MODULE_PATH, resparam.res);
        	if (icon == stat_sys_gps_acquiring_anim || icon == stat_sys_gps_acquiring_anim_right)	
        	{
        		resparam.res.setReplacement("com.android.systemui", "drawable", "ic_qs_location_on",  modRes.fwd(R.drawable.stat_sys_gps_acquiring_anim));
        	}
        	else
        	{
        		resparam.res.setReplacement("com.android.systemui", "drawable", "ic_qs_location_on",  modRes.fwd(R.drawable.stat_sys_gps_on));
        	}
        }*/
	}
	


}