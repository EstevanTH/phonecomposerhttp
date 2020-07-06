/*
 * This class allows Intents for calls to TelecomManager.placeCall().
 */

package ovh.thouvest.phonecomposerhttp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.util.Log;

public final class ReceiverPlaceCall extends BroadcastReceiver{
	static final String ACTION_PLACE_CALL = "ovh.thouvest.phonecomposerhttp.PLACE_CALL";
	static ReceiverPlaceCall sInstance = null; // not a true singleton
	
	static void activate(Context context){
		// Make ReceiverPlaceCall working
		if( sInstance==null ){
			sInstance = new ReceiverPlaceCall();
			IntentFilter filter = new IntentFilter();
			// Every IntentFilter completion mentioned here is mandatory.
			filter.addAction( ReceiverPlaceCall.ACTION_PLACE_CALL );
			filter.addDataScheme( "tel" );
			context.registerReceiver( sInstance, filter );
		}
	}
	
	@Override
	public void onReceive(Context context, Intent intent){
		if( context.getApplicationContext().getClass().getPackage()==this.getClass().getPackage() ){ // security
			try{
				placeCall( intent.getData() );
			}
			catch( Throwable e ){
				Log.e( this.getClass().getSimpleName(), e.getLocalizedMessage(), e );
			}
		}
	}
	
	@SuppressWarnings( {"MissingPermission", "NewApi"} )
	static void placeCall(Uri dest){
		TelecomManager tm = DeviceFeatures.getTelecomManager();
		Bundle extras = new Bundle();
		if( ApplicationHere.isCfgComposeSpeakerphone() ){
			extras.putBoolean( TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true );
		}
		tm.placeCall( dest, extras );
	}
}
