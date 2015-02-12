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


import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.content.res.XResources;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;
import android.text.Html;

import net.osmand.GeoidAltitudeCorrection;

import java.util.Iterator;

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

public class GPSNotification implements IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {
	
	// Constants
    private static final boolean DEBUG = BuildConfig.DEBUG;
	public static final String PKG = BuildConfig.APPLICATION_ID;
	public static final String ACTION_BROADCAST = PKG + ".BROADCAST";
	private static final String GPS_ENABLED_CHANGE_ACTION = "android.location.GPS_ENABLED_CHANGE";
	private static final String GPS_FIX_CHANGE_ACTION = "android.location.GPS_FIX_CHANGE";
	private static final String EXTRA_GPS_ENABLED = "enabled";
	private static final int GPS_NOTIFICATION_ID = 374203-122084;
	private static final String LOCATION_STATUS_ICON_PLACEHOLDER = "location";
	private static final String SYSTEMPKG = "com.android.systemui";
    private static final String TAG = "GPSNotification";

    public static final String SETTING_ICONPOSITION = "iconposition";
    public static final String SETTING_ICON = "icon";
    public static final String SETTING_PERMAMODE = "permamode";
    public static final String SETTING_QUICKSETTINGS = "replace_quicksettings";
    public static final String SETTING_SHOW_GPS_STATUS = "gpsstatus";
    public static final String SETTING_ANIMATION_SPEED = "animationspeed";
	
	private static String MODULE_PATH = null;
	private Class<?> LocationControllerClass;
	private Class<?> ResourceClass;

	private Context mContext = null;
    private LocationManager mLocationManager = null;
    private GpsStatus mGpsStatus = null;

	private NotificationManager mNotificationManager;
	
	private int gps_on, gps_anim, gps_acquiring;
	private int searching_text, found_text, accessibility_location_active, quick_settings_location_label; 
	
	private AnimationDrawable2 quicksettings_icon;
	private int qs_gps_on,qs_gps_acquiring1,qs_gps_acquiring2;
		
	private GPSIconPosition mIconPos;
	private GPSIconStyle mIcon;
	private int mAnimationSpeed;
	private Boolean mPermamode;
	private Boolean mQSTile;

	
	private Object mStatusBarManager;
	private Notification.Builder NotificationBuilder = null;
	private Boolean mShowIcon = false;
	private Boolean mAcquiring = false;
	private GeoidAltitudeCorrection geo = null;
	private Resources ModResources;

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
	
	public enum GPSIconStyle
	{
		JellyBean,
		KitKat;

	    public static GPSIconStyle fromInteger(int value) {
	    	switch(value) {
	        case 0:
	            return JellyBean;
	        case 1:
	            return KitKat;
	        default:
	        	return JellyBean;
	    	}
	    }

	    public static int getValue(GPSIconStyle value) {
	    	switch(value) {
		        case JellyBean:
		            return 0;
		        case KitKat:
		            return 1;
		        default:
		        	return 0;
	        }
	    }
	}
	


    private static void log(String text)
    {
        XposedBridge.log(TAG + ": " + text);
    }
	
	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {		
		if (!lpparam.packageName.equals(SYSTEMPKG))
	        return;
		
		if (Build.VERSION.SDK_INT >= 19)
		{
			LocationControllerClass = null;
			ResourceClass = null;
			
			XSharedPreferences prefs = new XSharedPreferences(PKG);
			mIconPos = GPSIconPosition.fromInteger(Integer.parseInt(prefs.getString(SETTING_ICONPOSITION, String.valueOf(GPSIconPosition.getValue(GPSIconPosition.LEFT)))));
			mIcon = GPSIconStyle.fromInteger(Integer.parseInt(prefs.getString(SETTING_ICON, String.valueOf(GPSIconStyle.getValue(GPSIconStyle.JellyBean)))));
			mPermamode = prefs.getBoolean(SETTING_PERMAMODE, false);
			mQSTile = prefs.getBoolean(SETTING_QUICKSETTINGS, true);
			
			if (mIconPos == GPSIconPosition.NONE)
				mPermamode = false;
			

			
			
			mAnimationSpeed = Integer.parseInt(prefs.getString(SETTING_ANIMATION_SPEED, String.valueOf(500)));
			
			LocationControllerClass = XposedHelpers.findClass("com.android.systemui.statusbar.policy.LocationController", lpparam.classLoader);
			XposedBridge.hookAllConstructors(LocationControllerClass, new XC_MethodHook() {
				 @Override
				 protected void afterHookedMethod(MethodHookParam param) throws Throwable {
					 mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
		             IntentFilter filter = new IntentFilter();
		             filter.addAction(GPS_ENABLED_CHANGE_ACTION);
		             filter.addAction(GPS_FIX_CHANGE_ACTION);
		             filter.addAction(ACTION_BROADCAST);
		             mContext.registerReceiver(mBroadcastReciver, filter);
                     mNotificationManager = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
	                 mStatusBarManager = XposedHelpers.getObjectField(param.thisObject, "mStatusBarManager");
	                }
				 });
			XposedHelpers.findAndHookMethod(LocationControllerClass, "refreshViews", XC_MethodReplacement.DO_NOTHING);

			
			// fix resources for cm11
			ResourceClass = XposedHelpers.findClass("android.content.res.Resources", lpparam.classLoader);
			XposedHelpers.findAndHookMethod(ResourceClass, "getDrawable", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                	int id = (Integer) param.args[0];
                    if (id == gps_anim || id == gps_on || id == gps_acquiring || id == qs_gps_on || id == qs_gps_acquiring1 || id == qs_gps_acquiring2)
	                {
	                	param.setResult(mContext.getResources().getDrawable(id));
	                	return;
	                } 
                }
            });
			
			if (LocationControllerClass == null)
			{
				log("LocationController Class not found! Could not apply Notification.");
			}	
			if (ResourceClass == null)
			{
				log("Resource Class not found! Could not apply Notification.");
			}
			
		    
			
			
		}
		else
		{
			LocationControllerClass = null;
			ResourceClass = null;
		}
	}
	
	@Override
	public void initZygote(StartupParam startupParam) {
		if (Build.VERSION.SDK_INT >= 19)
		{
			MODULE_PATH = startupParam.modulePath;
			
			XSharedPreferences prefs = new XSharedPreferences(PKG);
			GPSIconPosition mIconPos = GPSIconPosition.fromInteger(Integer.parseInt(prefs.getString(SETTING_ICONPOSITION, String.valueOf(GPSIconPosition.getValue(GPSIconPosition.LEFT)))));
			Boolean showGpsStatus = prefs.getBoolean(SETTING_SHOW_GPS_STATUS, false);
			if (mIconPos != GPSIconPosition.LEFT)
                showGpsStatus = false;
			
			if (showGpsStatus)
			{
                XposedHelpers.findAndHookMethod(LocationManager.class, "addGpsStatusListener", GpsStatus.Listener.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if ((Boolean)param.getResult() == true) {
                            mContext = android.app.AndroidAppHelper.currentApplication().getApplicationContext();
                            if (mContext == null)
                                return;
                            mLocationManager = (LocationManager) param.thisObject;
                            GpsStatus.Listener listener = (GpsStatus.Listener)param.args[0];
                            XposedHelpers.findAndHookMethod(listener.getClass(), "onGpsStatusChanged", int.class, onGpsStatusChanged);
                        }
                    }
                });

                XposedHelpers.findAndHookMethod(LocationManager.class, "getGpsStatus", GpsStatus.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        mGpsStatus = (GpsStatus)param.getResult();
                    }
                });
			}
		}
	}

	
	@Override
	public void handleInitPackageResources(InitPackageResourcesParam resparam) {
		if (!resparam.packageName.equals(SYSTEMPKG))
			return;
		
		if (Build.VERSION.SDK_INT >= 19 && LocationControllerClass != null && ResourceClass != null)
		{
			XModuleResources modRes = XModuleResources.createInstance(MODULE_PATH, resparam.res);
			if (mIcon == GPSIconStyle.JellyBean)
			{				
				if (mIconPos == GPSIconPosition.LEFT)
				{
					gps_on = resparam.res.addResource(modRes, R.drawable.jb_gps_on_left);
					gps_acquiring = resparam.res.addResource(modRes, R.drawable.jb_gps_acquiring_left);
					gps_anim = resparam.res.addResource(modRes, R.id.animation_icon);
				} 
				else if (mIconPos == GPSIconPosition.RIGHT)
				{
					gps_on = resparam.res.addResource(modRes, R.drawable.jb_gps_on_right);
					gps_acquiring = resparam.res.addResource(modRes, R.drawable.jb_gps_acquiring_right);
					gps_anim = resparam.res.addResource(modRes, R.id.animation_icon);
				}
				
				
				qs_gps_on = resparam.res.addResource(modRes, R.drawable.jb_qs_gps_on);
				qs_gps_acquiring1 = resparam.res.addResource(modRes, R.drawable.jb_qs_gps_acquiring1);
				qs_gps_acquiring2 = resparam.res.addResource(modRes, R.drawable.jb_qs_gps_acquiring2);
				if (mQSTile)
					resparam.res.setReplacement(SYSTEMPKG, "drawable", "ic_qs_location_off", modRes.fwd(R.drawable.jb_qs_gps_off));
			}
			else /*if (Icon == GPSIconStyle.KitKat)*/
			{
				if (mIconPos == GPSIconPosition.LEFT)
				{
					gps_on = resparam.res.addResource(modRes, R.drawable.kk_gps_on_left);
					gps_acquiring = resparam.res.addResource(modRes, R.drawable.kk_gps_acquiring_left);
					gps_anim = resparam.res.addResource(modRes, R.id.animation_icon);
				}
				else if (mIconPos == GPSIconPosition.RIGHT)
				{
					gps_on = resparam.res.addResource(modRes, R.drawable.kk_gps_on_right);
					gps_acquiring = resparam.res.addResource(modRes, R.drawable.kk_gps_acquiring_right);
					gps_anim = resparam.res.addResource(modRes, R.id.animation_icon);
				}
								
				qs_gps_on = resparam.res.addResource(modRes, R.drawable.kk_qs_gps_on);
				qs_gps_acquiring1 = resparam.res.addResource(modRes, R.drawable.kk_qs_gps_acquiring1);
				qs_gps_acquiring2 = resparam.res.addResource(modRes, R.drawable.kk_qs_gps_acquiring2);
				if (mQSTile)
					resparam.res.setReplacement(SYSTEMPKG, "drawable", "ic_qs_location_off", modRes.fwd(R.drawable.kk_qs_gps_off));
			}
			
			searching_text = resparam.res.getIdentifier("gps_notification_searching_text", "string", resparam.packageName);
			found_text  = resparam.res.getIdentifier("gps_notification_found_text", "string", resparam.packageName);
			accessibility_location_active = resparam.res.getIdentifier("accessibility_location_active", "string", resparam.packageName);
			quick_settings_location_label = resparam.res.getIdentifier("quick_settings_location_label", "string", resparam.packageName);
							
			if (mIconPos == GPSIconPosition.LEFT || mIconPos == GPSIconPosition.RIGHT)
			{
				// here is the funny part: hook our own animation icon, so we can set the duration
				resparam.res.setReplacement(gps_anim, new XResources.DrawableLoader() {
				    @Override
				    public Drawable newDrawable(XResources res, int id) throws Throwable {
				    	AnimationDrawable frameAnimation = new AnimationDrawable();
				        frameAnimation.addFrame(res.getDrawable(gps_on), mAnimationSpeed);
				        frameAnimation.addFrame(res.getDrawable(gps_acquiring), mAnimationSpeed);
				        frameAnimation.setOneShot(false);
				        frameAnimation.start();
				        return frameAnimation;
				    }
				});
			}
						
			if (mQSTile)
			{
				// quicksettings blinking
				XResources.DrawableLoader qs_location_on = new XResources.DrawableLoader() {
				    @Override
				    public Drawable newDrawable(XResources res, int id) throws Throwable {	
				    	if (quicksettings_icon == null)
				    	{
					    	quicksettings_icon = new AnimationDrawable2();
					    	quicksettings_icon.addFrame(res.getDrawable(qs_gps_on), mAnimationSpeed);
					    	quicksettings_icon.addFrame(res.getDrawable(qs_gps_acquiring1), mAnimationSpeed);
					    	quicksettings_icon.addFrame(res.getDrawable(qs_gps_acquiring2), mAnimationSpeed);
					    	quicksettings_icon.skipFrame(0, false);
					    	quicksettings_icon.skipFrame(1, true);
					    	quicksettings_icon.skipFrame(2, true);
					    	quicksettings_icon.setOneShot(false);
					    	quicksettings_icon.stop();
					    	quicksettings_icon.selectDrawable(0);
				    	}
				    	return quicksettings_icon;
				    }
				};
				try
				{
					resparam.res.setReplacement(SYSTEMPKG, "drawable", "ic_qs_location_on", qs_location_on);
				}
				catch (Exception e)
				{
					
				}
				try
				{
					resparam.res.setReplacement(SYSTEMPKG, "drawable", "ic_qs_location_on_gps", qs_location_on);
				}
				catch (Exception e)
				{
					
				}
				try
				{
					resparam.res.setReplacement(SYSTEMPKG, "drawable", "ic_qs_location_on_wifi", qs_location_on);
				}
				catch (Exception e)
				{
					
				}
			}
		}
	}

	private String getPosition(Location location){

		if (location != null)
		{
			double latitude = location.getLatitude();
			double longitude = location.getLongitude();
	
			int latSeconds = (int)Math.round(latitude * 3600);
			int latDegrees = latSeconds / 3600;
			latSeconds = Math.abs(latSeconds % 3600);
			int latMinutes = latSeconds / 60;
			latSeconds %= 60;

			int longSeconds = (int)Math.round(longitude * 3600);
			int longDegrees = longSeconds / 3600;
			longSeconds = Math.abs(longSeconds % 3600);
			int longMinutes = longSeconds / 60;
			longSeconds %= 60;

			
			double alt = location.getAltitude();
			
			
			if (ModResources == null)
			{
				PackageManager manager = mContext.getPackageManager();
	    		try {
	    			ModResources = manager.getResourcesForApplication(PKG);
	    		} catch (Exception e) {
	    			ModResources = null;
	    		}
			}
			
			if (geo == null && ModResources != null) {
				geo = new GeoidAltitudeCorrection(ModResources, R.raw.ww15mgh);
			}
			if (geo != null) {
				alt -= geo.getGeoidHeight(latitude, longitude);
			}
		
			
			return String.format("%d%c%d'%d\"%s %d%c%d'%d\"%s @%dm %c%dm",
					latDegrees, 0x00B0, latMinutes, latSeconds,	(ModResources == null) ? "N" : ModResources.getString(R.string.north),
					longDegrees, 0x00B0, longMinutes, longSeconds, (ModResources == null) ? "E" : ModResources.getString(R.string.east),
					(int)alt, 0x00B1, (int)location.getAccuracy());
		}
		else
			return "";
	}



    private BroadcastReceiver mBroadcastReciver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(ACTION_BROADCAST)) {
                if (mShowIcon) {
                    int event = intent.getIntExtra("event", 0);
                    int satInView = intent.getIntExtra("satInView", 0);
                    int satInUse = intent.getIntExtra("satInUse", 0);
                    Location location = (Location) intent.getExtras().get("location");

                    if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
                        if (mAcquiring == true) {
                            NotificationBuilder.setContentText(Html.fromHtml("<b>SAT " + satInUse + "/" + satInView + "</b>"));
                        } else {
                            CharSequence message = Html.fromHtml("<b>SAT " + satInUse + "/" + satInView + "</b><br/>" + getPosition(location));

                            NotificationBuilder.setStyle(new Notification.BigTextStyle()
                                    .bigText(message));
                            NotificationBuilder.setContentText(message);
                        }
                        Notification n = NotificationBuilder.build();
                        n.tickerView = null;
                        n.tickerText = null;
                        mNotificationManager.notify(GPS_NOTIFICATION_ID, n);
                    }
                }
            } else {
                final boolean enabled = intent.getBooleanExtra(EXTRA_GPS_ENABLED, false);
                int textResId = 0, icon;

                if (action.equals(GPS_FIX_CHANGE_ACTION) && enabled) {
                    // GPS is getting fixes
                    icon = gps_on;
                    mShowIcon = true;
                    if (mIconPos == GPSIconPosition.LEFT) {
                        textResId = found_text;
                    }
                    if (mQSTile && quicksettings_icon != null) {
                        quicksettings_icon.stop();
                        quicksettings_icon.selectDrawable(1);
                        quicksettings_icon.skipFrame(0, false);
                        quicksettings_icon.skipFrame(1, true);
                        quicksettings_icon.skipFrame(2, false);
                    }
                    mAcquiring = false;
                } else if (action.equals(GPS_ENABLED_CHANGE_ACTION) && !enabled) {
                    // GPS is off
                    if (mPermamode == false) {
                        mShowIcon = false;
                        icon = textResId = 0;
                    } else {
                        mShowIcon = true;
                        icon = gps_on;
                        textResId = quick_settings_location_label;
                    }
                    if (mQSTile && quicksettings_icon != null) {
                        quicksettings_icon.stop();
                        quicksettings_icon.selectDrawable(0);
                        quicksettings_icon.skipFrame(0, false);
                        quicksettings_icon.skipFrame(1, false);
                        quicksettings_icon.skipFrame(2, false);
                    }
                    mAcquiring = false;
                } else {
                    // GPS is on, but not receiving fixes
                    icon = gps_anim;
                    mShowIcon = true;
                    mAcquiring = true;
                    if (mIconPos == GPSIconPosition.LEFT) {
                        textResId = searching_text;
                    }
                    if (mQSTile && quicksettings_icon != null) {
                        quicksettings_icon.skipFrame(0, true);
                        quicksettings_icon.skipFrame(1, false);
                        quicksettings_icon.skipFrame(2, false);
                        quicksettings_icon.start();
                    }
                }


                if (mIconPos == GPSIconPosition.LEFT) {
                    try {
                        if (mShowIcon) {
                            Intent gpsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            gpsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, gpsIntent, 0);

                            NotificationBuilder = new Notification.Builder(mContext)
                                    .setSmallIcon(icon)
                                    .setContentTitle(mContext.getText(textResId))
                                    .setOngoing(true)
                                            //.setWhen(0)
                                    .setShowWhen(false)
                                    .setContentIntent(pendingIntent);


                            Notification n = NotificationBuilder.build();
                            // Notification.Builder will helpfully fill these out for you no matter what you do
                            n.tickerView = null;
                            n.tickerText = null;

                            mNotificationManager.notify(GPS_NOTIFICATION_ID, n);
                        } else {
                            mNotificationManager.cancel(GPS_NOTIFICATION_ID);
                        }
                    } catch (Exception ex) {
                        // well, it was worth a shot
                    }
                } else if (mIconPos == GPSIconPosition.RIGHT) {
                    if (mShowIcon) {
                        XposedHelpers.callMethod(mStatusBarManager, "setIcon", LOCATION_STATUS_ICON_PLACEHOLDER, icon, 0, mContext.getString(accessibility_location_active));
                    } else {
                        XposedHelpers.callMethod(mStatusBarManager, "removeIcon", LOCATION_STATUS_ICON_PLACEHOLDER);
                    }
                }
            }
        }
    };

    /**
     * only if ShowGPSStatus is set to true
     * runs in the private app context
     * sends a broadcast with the data
     */
    private XC_MethodHook onGpsStatusChanged = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {

            if (mGpsStatus == null || mContext == null || mLocationManager == null) {
                 return;
            }
            int event = (Integer)param.args[0];
            Iterable<GpsSatellite> satellites = mGpsStatus.getSatellites();
            Iterator<GpsSatellite> sat = satellites.iterator();
            int satInView = 0, satInUse = 0;
            while (sat.hasNext()) {
                GpsSatellite satellite = sat.next();
                if (satellite.usedInFix())
                    satInUse++;
                satInView++;
            }

            if (satInView > 0)
            {
                Intent intent = new Intent(ACTION_BROADCAST);
                intent.putExtra("event", event);
                intent.putExtra("satInUse", satInUse);
                intent.putExtra("satInView", satInView);

                Location location = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null)
                {
                    location = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                intent.putExtra("location", location);
                if (DEBUG) {
                    log("Sending BROADCAST: event=" + event + "\n\t\tsatInUse=" + satInView + "\n\t\tsatInView=" + satInView + "\n\t\tlocation=" + location);
                }
                mContext.sendBroadcast(intent);
            }
        }
    };

}