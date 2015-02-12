GPSNotification
===============

Brings the JellyBean GPS Notification back.

Needs the [Xposed Framework](http://xposed.info)

[This Module on the Repository](http://repo.xposed.info/module/eun.xposed.gpsnotification)

[Discussion thread](http://forum.xda-developers.com/showthread.php?t=2621751)

CHANGELOG
=========
* 1.81
  * Fixed writing on XSharedPreferences

* 1.8
  * Russian translation 
  * Moved to Android Studio
  * Better hooking for GPSStatus
  * Resolved some errors

* 1.7
  * fixed CM11 (Resource) bug
  * fixed invisible bug in quicksettings
  * added permamode
  * added GPSStatus in notification
  * added German Translation

* 1.6
  * Improved quicksettings icons
  * Added Animation speed

* 1.5
  * Added KitKat Icon
  * Added the ability to choose icons

* 1.4
  * Correct Icons in QuickSettings

* 1.3
  * Fixed Bug: Flashing icon in settings

* 1.2
  * Added posibility to choose where the icon should be visible

* 1.0
  * Initial Release

TODO
====
* 3th Icon: Kitkat WIFI / GPS / Location Icon
* <s>Hide notification text for Left Icon (when gps found) (s3icc0)</s>
  * Canceled: It is not possible to display notifications without text
