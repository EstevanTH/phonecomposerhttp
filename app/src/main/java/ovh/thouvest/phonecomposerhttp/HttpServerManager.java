package ovh.thouvest.phonecomposerhttp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class HttpServerManager extends BroadcastReceiver{
	
	static boolean shoudAutoStart(){
		return ApplicationHere.getCfgServiceAutoStart().isYes();
	}
	
	protected static void onReceive_static(Context context, Intent intent){
		// Start the server when needed
		
		Log.i( HttpServerManager.class.getSimpleName(), "onReceive()" );
		boolean runAllowed = false;
		if( context instanceof ApplicationHere ){
			// start request coming from this application
			runAllowed = true;
		}
		else if( intent!=null ){
			// start request coming from the OS
			String action = intent.getAction();
			if( action!=null && action.equalsIgnoreCase( Intent.ACTION_BOOT_COMPLETED ) ){
				if( shoudAutoStart() ){
					runAllowed = true;
				}
			}
		}
		if( runAllowed ){
			Intent startHttpServer = new Intent( ApplicationHere.getInstance(), HttpServer.class );
			context.startService( startHttpServer );
			//context.startForegroundService( startHttpServer );
		}
	}
	
	@Override
	public void onReceive(Context context, Intent intent){
		onReceive_static(context, intent);
	}
	
	static void startHttpService(boolean saveState){
		ApplicationHere application = ApplicationHere.getInstance();
		onReceive_static( application, null );
		Log.i( HttpServerManager.class.getSimpleName(), "startHttpService()" );
		if( saveState && ApplicationHere.getCfgServiceAutoStart()==ApplicationHere.CFG_AUTOSTART.LAST_STATE_NO ){
			try{
				SharedPreferences.Editor prefEditor = ApplicationHere.getAllPreferences( application ).edit();
				prefEditor.putInt( ApplicationHere.KEY_ServiceAutoStart, ApplicationHere.CFG_AUTOSTART.LAST_STATE_YES.ordinal() );
				prefEditor.apply();
			}
			catch( Throwable e ){
				Log.e( HttpServerManager.class.getSimpleName(), e.getLocalizedMessage(), e );
			}
		}
	}
	
	static void stopHttpService(boolean saveState){
		ApplicationHere application = ApplicationHere.getInstance();
		try{
			application.stopService( new Intent( application, HttpServer.class ) );
		}
		catch( Throwable e ){
			Log.e( HttpServerManager.class.getSimpleName(), e.getLocalizedMessage(), e );
		}
		Log.i( HttpServerManager.class.getSimpleName(), "stopHttpService()" );
		if( saveState && ApplicationHere.getCfgServiceAutoStart()==ApplicationHere.CFG_AUTOSTART.LAST_STATE_YES ){
			try{
				SharedPreferences.Editor prefEditor = ApplicationHere.getAllPreferences( application ).edit();
				prefEditor.putInt( ApplicationHere.KEY_ServiceAutoStart, ApplicationHere.CFG_AUTOSTART.LAST_STATE_NO.ordinal() );
				prefEditor.apply();
			}
			catch( Throwable e ){
				Log.e( HttpServerManager.class.getSimpleName(), e.getLocalizedMessage(), e );
			}
		}
	}
	
}
