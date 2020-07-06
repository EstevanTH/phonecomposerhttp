package ovh.thouvest.phonecomposerhttp;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.v4.app.NotificationManagerCompat;
import android.telecom.TelecomManager;

/**
 * DeviceFeatures provides functions to help dealing with different devices & OS versions.
 * It also simplifies the usage of such features.
 **/
final class DeviceFeatures{
	private DeviceFeatures(){}
	
	protected static ApplicationHere sApplication = null;
	protected static KeyguardManager sSystemServiceKeyGuard = null;
	protected static NotificationManagerCompat sSystemServiceNotification = null;
	protected static TelecomManager sSystemServiceTelecomManager = null;
	protected static Vibrator sSystemServiceVibrator = null;
	
	// Must be called once during startup
	static void initialize(){
		sApplication = ApplicationHere.getInstance();
		sSystemServiceKeyGuard = (KeyguardManager)sApplication.getSystemService( Context.KEYGUARD_SERVICE );
		sSystemServiceNotification = NotificationManagerCompat.from( sApplication );
		if( Build.VERSION.SDK_INT >= 21 ){
			sSystemServiceTelecomManager = (TelecomManager)sApplication.getSystemService( Context.TELECOM_SERVICE );
		}
		sSystemServiceVibrator = (Vibrator)sApplication.getSystemService( Context.VIBRATOR_SERVICE );
	}
	
	public static KeyguardManager getKeyguardManager(){
		return sSystemServiceKeyGuard;
	}
	
	public static NotificationManagerCompat getNotificationManager(){
		return sSystemServiceNotification;
	}
	
	public static TelecomManager getTelecomManager(){
		return sSystemServiceTelecomManager;
	}
	
	public static Vibrator getVibrator(){
		return sSystemServiceVibrator;
	}
	
	public static boolean lockedDetectable(){
		return ( Build.VERSION.SDK_INT >= 22 );
	}
	
	public static boolean isLocked(){
		boolean isLocked = false;
		if( Build.VERSION.SDK_INT >= 22 ){
			isLocked = sSystemServiceKeyGuard.isDeviceLocked();
		}
		return isLocked;
	}
	
	public static boolean canVibrate(){
		if( sSystemServiceVibrator!=null ){
			return sSystemServiceVibrator.hasVibrator();
		}
		else{
			return false;
		}
	}
	
	protected static void runVibrator(long milliseconds){
		if( DeviceFeatures.canVibrate() ){
			if( Build.VERSION.SDK_INT >= 26 ){
				sSystemServiceVibrator.vibrate( VibrationEffect.createOneShot( milliseconds, VibrationEffect.DEFAULT_AMPLITUDE ) );
			}
			else{
				sSystemServiceVibrator.vibrate( milliseconds );
			}
		}
	}
}
