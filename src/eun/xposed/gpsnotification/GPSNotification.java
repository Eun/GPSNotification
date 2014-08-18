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


import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.osmand.GeoidAltitudeCorrection;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
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

public class GPSNotification  extends BroadcastReceiver implements IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources, GpsStatus.Listener {
	
	// Constants
	public static final String PKG = "eun.xposed.gpsnotification";
	public static final String ACTION_BROADCAST = PKG + ".BROADCAST";
	private static final String GPS_ENABLED_CHANGE_ACTION = "android.location.GPS_ENABLED_CHANGE";
	private static final String GPS_FIX_CHANGE_ACTION = "android.location.GPS_FIX_CHANGE";
	private static final String EXTRA_GPS_ENABLED = "enabled";
	private static final int GPS_NOTIFICATION_ID = 374203-122084;
	private static final String LOCATION_STATUS_ICON_PLACEHOLDER = "location";
	private static final String SYSTEMPKG = "com.android.systemui";
	
	private static String MODULE_PATH = null;
	private Class<?> LocationControllerClass;
	private Class<?> ResourceClass;
	private Context mContext;
	private NotificationManager nm;
	
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
	private Boolean mLocationManagerHooked = false;
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
	

	
	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {		
		if (!lpparam.packageName.equals(SYSTEMPKG))
	        return;
		
		if (Build.VERSION.SDK_INT >= 19)
		{
			LocationControllerClass = null;
			ResourceClass = null;
			
			XSharedPreferences prefs = new XSharedPreferences(PKG);
			mIconPos = GPSIconPosition.fromInteger(Integer.parseInt(prefs.getString("iconposition", String.valueOf(GPSIconPosition.getValue(GPSIconPosition.LEFT)))));
			mIcon = GPSIconStyle.fromInteger(Integer.parseInt(prefs.getString("icon", String.valueOf(GPSIconStyle.getValue(GPSIconStyle.JellyBean)))));
			mPermamode = prefs.getBoolean("permamode", false);
			mQSTile = prefs.getBoolean("replace_quicksettings", true);
			
			if (mIconPos == GPSIconPosition.NONE)
				mPermamode = false;
			
			if (mIconPos != GPSIconPosition.LEFT)
			{
				prefs.edit().putBoolean("gpsstatus", false).commit();
			}
			
			
			
			mAnimationSpeed = Integer.parseInt(prefs.getString("animationspeed", String.valueOf(500)));
			
			LocationControllerClass = XposedHelpers.findClass("com.android.systemui.statusbar.policy.LocationController", lpparam.classLoader);
			XposedBridge.hookAllConstructors(LocationControllerClass, new XC_MethodHook() {
				 @Override
				 protected void afterHookedMethod(MethodHookParam param) throws Throwable {
					 mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
		             IntentFilter filter = new IntentFilter();
		             filter.addAction(GPS_ENABLED_CHANGE_ACTION);
		             filter.addAction(GPS_FIX_CHANGE_ACTION);
		             filter.addAction(ACTION_BROADCAST);
		             mContext.registerReceiver(GPSNotification.this, filter);
	                 nm = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
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
				XposedBridge.log("GPSNotification: LocationController Class not found! Could not apply Notification.");
			}	
			if (ResourceClass == null)
			{
				XposedBridge.log("GPSNotification: Resource Class not found! Could not apply Notification.");
			}
			
		    
			
			
		}
		else
		{
			LocationControllerClass = null;
			ResourceClass = null;
		}
	}
	
	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		if (Build.VERSION.SDK_INT >= 19)
		{
			MODULE_PATH = startupParam.modulePath;
			
			XSharedPreferences prefs = new XSharedPreferences(PKG);
			GPSIconPosition mIconPos = GPSIconPosition.fromInteger(Integer.parseInt(prefs.getString("iconposition", String.valueOf(GPSIconPosition.getValue(GPSIconPosition.LEFT)))));
			Boolean mGPSStatus = prefs.getBoolean("gpsstatus", false);
			if (mIconPos != GPSIconPosition.LEFT)
				mGPSStatus = false;
			
			if (mGPSStatus)
			{
				hookSystemService("android.app.ContextImpl");
				hookSystemService("android.app.Activity");
			}
			
			
		}
	}

	
	@Override
	public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {
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
	
	
	private void hookSystemService(String context) {
		try {
			XC_MethodHook methodHook = new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					}

					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						if (!param.hasThrowable())
							try {
								if (param.args.length > 0 && param.args[0] != null) {
									// XposedBridge.log("Hook Method : " + mInstance + " " + mApp + " " + packageName);
									String name = (String) param.args[0];
									Object instance = param.getResult();
									if (name != null && instance != null) {
										handleGetSystemService(name, instance);
									}
								}
							} catch (Throwable ex) {
								throw ex;
							}
					}
				};

			Set<XC_MethodHook.Unhook> hookSet = new HashSet<XC_MethodHook.Unhook>();

			Class<?> hookClass = null;
			try {
				hookClass = XposedHelpers.findClass(context, null);
				if (hookClass == null)
					throw new ClassNotFoundException(context);
				// XposedBridge.log("Zygote Context Find Class Done");
			} catch (Exception ex) {
				// XposedBridge.log("Zygote Context Impl Exception " + ex);
			}

			// XposedBridge.log("Zygote Context Find Class " + hookClass);
			Class<?> clazz = hookClass;
			while (clazz != null) {
				for (Method method : clazz.getDeclaredMethods()) {
					if (method != null && method.getName().equals("getSystemService")) {
						hookSet.add(XposedBridge.hookMethod(method, methodHook));
					}
				}
				clazz = (hookSet.isEmpty() ? clazz.getSuperclass() : null);
			}
		} catch (Exception ex) {
			// XposedBridge.log("Zygote Context Hook Exception " + ex);
		}
    }


	private void handleGetSystemService(String name, Object instance) {
		if (name.equals(Context.LOCATION_SERVICE) && mLocationManagerHooked == false) {
			Context mContext = (Context)XposedHelpers.getObjectField(instance, "mContext");
			if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
			{
				try
				{
					Class<?> hookClass = null;
					hookClass = XposedHelpers.findClass(instance.getClass().getName(), null);
					if (hookClass != null)
					{
						XposedHelpers.callMethod(instance, "addGpsStatusListener", GPSNotification.this);
					}	
				}
				catch (Exception ex)
				{
				}
			}
			mLocationManagerHooked = true;
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
		
			
			return String.format("%d�%d'%d\"%s %d�%d'%d\"%s @%dm �%dm", 
					latDegrees, latMinutes, latSeconds,	(ModResources == null) ? "N" : ModResources.getString(R.string.north),
					longDegrees, longMinutes, longSeconds, (ModResources == null) ? "E" : ModResources.getString(R.string.east),
					(int)alt, (int)location.getAccuracy());
		}
		else
			return "";
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		final String action = intent.getAction();
		
		if (action.equals(ACTION_BROADCAST))
		{
			if (mShowIcon)
			{
				int event = intent.getIntExtra("event", 0);
				int satInView = intent.getIntExtra("satInView", 0);
				int satInUse = intent.getIntExtra("satInUse", 0);
				Location location = (Location)intent.getExtras().get("location");
				
				if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS)
				{										
					if (mAcquiring == true)
					{
						NotificationBuilder.setContentText(Html.fromHtml("<b>SAT "+satInUse+"/"+satInView+"</b>"));
					}
					else
					{
						CharSequence message = Html.fromHtml("<b>SAT "+satInUse+"/"+satInView+"</b><br/>"+getPosition(location));
						
						NotificationBuilder.setStyle(new Notification.BigTextStyle()
				         .bigText(message));
						NotificationBuilder.setContentText(message);
					}
					Notification n = NotificationBuilder.build();
		            n.tickerView = null;
		            n.tickerText = null;
		            nm.notify(GPS_NOTIFICATION_ID, n);
				}
			}
			return;
		}
		
        final boolean enabled = intent.getBooleanExtra(EXTRA_GPS_ENABLED, false);

        int textResId = 0, icon;

        if (action.equals(GPS_FIX_CHANGE_ACTION) && enabled)
        {
            // GPS is getting fixes
        	icon = gps_on;
        	mShowIcon = true;
            if (mIconPos == GPSIconPosition.LEFT)
			{
            	textResId = found_text;
			}
            if (mQSTile && quicksettings_icon != null)
            {
	            quicksettings_icon.stop();
	            quicksettings_icon.selectDrawable(1);
	            quicksettings_icon.skipFrame(0, false);
	            quicksettings_icon.skipFrame(1, true);
		    	quicksettings_icon.skipFrame(2, false);
            }
            mAcquiring = false;
        }
        else if (action.equals(GPS_ENABLED_CHANGE_ACTION) && !enabled)
        {
            // GPS is off
        	if (mPermamode == false)
        	{
        		mShowIcon = false;
	            icon = textResId = 0;
        	}
        	else
        	{
        		mShowIcon = true;
	            icon = gps_on;
	            textResId = quick_settings_location_label;
	     	}
            if (mQSTile && quicksettings_icon != null)
            {
	            quicksettings_icon.stop();
	            quicksettings_icon.selectDrawable(0);
	            quicksettings_icon.skipFrame(0, false);
	            quicksettings_icon.skipFrame(1, false);
		    	quicksettings_icon.skipFrame(2, false);
            }
            mAcquiring = false;
        }
        else
        {
            // GPS is on, but not receiving fixes
        	icon = gps_anim;
        	mShowIcon = true;
        	mAcquiring = true;
            if (mIconPos == GPSIconPosition.LEFT)
			{
            	textResId = searching_text;
			}
            if (mQSTile && quicksettings_icon != null)
            {
	            quicksettings_icon.skipFrame(0, true);
	            quicksettings_icon.skipFrame(1, false);
		    	quicksettings_icon.skipFrame(2, false);
	            quicksettings_icon.start();
            }
        }
        
       
        
        if (mIconPos == GPSIconPosition.LEFT)
        {
	        try
	        {
	            if (mShowIcon)
	            {
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
	               
	                nm.notify(GPS_NOTIFICATION_ID, n);  
	            }
	            else
	            {
	            	nm.cancel(GPS_NOTIFICATION_ID);
	            };
	        }
	        catch (Exception ex)
	        {
	            // well, it was worth a shot
	        }
        }
        else if (mIconPos == GPSIconPosition.RIGHT)
		{
			 if (mShowIcon)
			 {
				XposedHelpers.callMethod(mStatusBarManager, "setIcon", LOCATION_STATUS_ICON_PLACEHOLDER, icon, 0, mContext.getString(accessibility_location_active));
			 }
			 else
			 {
				XposedHelpers.callMethod(mStatusBarManager, "removeIcon", LOCATION_STATUS_ICON_PLACEHOLDER);
			 }
		}
	}

	@Override
	public void onGpsStatusChanged(int event) {
		Context context = android.app.AndroidAppHelper.currentApplication().getApplicationContext();
		if (context == null)
			return;
	
		LocationManager locationManager = ((LocationManager)context.getSystemService(Context.LOCATION_SERVICE));
		if (locationManager == null)
			return;
		GpsStatus status = locationManager.getGpsStatus(null);
	  
    	if (status != null)
    	{
    		
    		Iterable<GpsSatellite>satellites = status.getSatellites();
            Iterator<GpsSatellite>sat = satellites.iterator();
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
	            
	            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
	            if (location == null)
	            {
	            	location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
	            }
	            intent.putExtra("location", location);
	            
	            context.sendBroadcast(intent);
            }
            
    	}		
	}
}