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
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class GPSNotification  extends BroadcastReceiver implements IXposedHookLoadPackage,  IXposedHookZygoteInit, IXposedHookInitPackageResources  {
	
    public static final String GPS_ENABLED_CHANGE_ACTION =
            "android.location.GPS_ENABLED_CHANGE";
    public static final String GPS_FIX_CHANGE_ACTION =
            "android.location.GPS_FIX_CHANGE";
    public static final String EXTRA_GPS_ENABLED = "enabled";
    
    private static final int GPS_NOTIFICATION_ID = 374203-122084;
    
        
	private Context mContext;
	
	private static String MODULE_PATH = null;
	
	private NotificationManager nm;
	
	int stat_sys_gps_on, stat_sys_gps_acquiring, stat_sys_gps_acquiring_anim, gps_notification_found_text, gps_notification_searching_text;
	
	
	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals("com.android.systemui"))
	        return;
		
		if (Build.VERSION.SDK_INT >= 19)
		{
			final Class<?> LocationControllerClass =  XposedHelpers.findClass("com.android.systemui.statusbar.policy.LocationController", lpparam.classLoader);
			
			XposedBridge.hookAllConstructors(LocationControllerClass, new XC_MethodHook() {
				 @Override
	             protected void afterHookedMethod(MethodHookParam param) throws Throwable {
	                    
	                     mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
	                     IntentFilter filter = new IntentFilter();
	                     filter.addAction(GPS_ENABLED_CHANGE_ACTION);
	                     filter.addAction(GPS_FIX_CHANGE_ACTION);
	                     mContext.registerReceiver(GPSNotification.this, filter);
	                     nm = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
	             }
			});
		}
	}
	
	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		MODULE_PATH = startupParam.modulePath;
	}

	@Override
	public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {
		if (!resparam.packageName.equals("com.android.systemui"))
			return;

		XModuleResources modRes = XModuleResources.createInstance(MODULE_PATH, resparam.res);
		stat_sys_gps_on = resparam.res.addResource(modRes, R.drawable.stat_sys_gps_on);
		stat_sys_gps_acquiring = resparam.res.addResource(modRes, R.drawable.stat_sys_gps_acquiring);
		stat_sys_gps_acquiring_anim = resparam.res.addResource(modRes, R.drawable.stat_sys_gps_acquiring_anim);
		gps_notification_searching_text = resparam.res.getIdentifier("gps_notification_searching_text", "string", resparam.packageName);
		gps_notification_found_text  = resparam.res.getIdentifier("gps_notification_found_text", "string", resparam.packageName);
		
	}
	
	
	
	@Override
	public void onReceive(Context context, Intent intent) {
		final String action = intent.getAction();
        final boolean enabled = intent.getBooleanExtra(EXTRA_GPS_ENABLED, false);

        boolean visible;
        int iconId, textResId;

        if (action.equals(GPS_FIX_CHANGE_ACTION) && enabled) {
            // GPS is getting fixes
            iconId = stat_sys_gps_on;
            textResId = gps_notification_found_text;
            visible = true;
        } else if (action.equals(GPS_ENABLED_CHANGE_ACTION) && !enabled) {
            // GPS is off
            visible = false;
            iconId = textResId = 0;
        } else {
            // GPS is on, but not receiving fixes
            iconId = stat_sys_gps_acquiring_anim;
            textResId = gps_notification_searching_text;
            visible = true;
        }
        
        try {
            if (visible) {
                Intent gpsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                gpsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, gpsIntent, 0);

                Notification n = new Notification.Builder(mContext)
                    .setSmallIcon(iconId)
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
}