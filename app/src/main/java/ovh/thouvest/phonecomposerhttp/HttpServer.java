// TODO - mécanisme jeton (+ paramètre pour rendre optionnel)
// TODO - vérifier que la lecture d'un entête est limitée en taille
// TODO - utiliser paramètre URL pour pré-remplir n° et lancer appel

package ovh.thouvest.phonecomposerhttp;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v7.content.res.AppCompatResources;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

public class HttpServer extends Service{
	
	protected static final String EXTRA_START_CALL_WITH_SPEAKERPHONE = // from TelecomManager
		"android.telecom.extra.START_CALL_WITH_SPEAKERPHONE"; // copied for API<26 compatibility
	protected static final String URI_HOME_HTML = "/";
	protected static final String URI_CALLFORM_HTML = "/call.html";
	protected static final String URI_TOKEN_JSON = "/token.json";
	protected static final String URI_DOCALL_HTML = "/docall.html";
	protected static final String URI_DOCALL_JSON = "/docall.json";
	
	protected static HttpServer sInstance;
	protected static Thread sThreadHttp;
	protected static ServerSocket sServerSock;
	protected static Semaphore sSemaphoreWorkers = null;
	
	static protected class RequestThread extends Thread{
		Socket mClientSock = null;
		
		public RequestThread(Socket clientSock){
			super();
			mClientSock = clientSock;
		}
		
		@Override
		public void run(){
			InputStream in = null;
			OutputStream out = null;
			try{
				in = mClientSock.getInputStream();
				out = mClientSock.getOutputStream();
				HttpServer.handleRequest( mClientSock, in, out );
			}
			catch( Throwable e ){
				Log.w( this.getClass().getSimpleName(), e );
			}
			finally{
				// The semaphore is released in the thread.
				if( sSemaphoreWorkers!=null ){
					sSemaphoreWorkers.release();
				}
				try{
					if( out!=null ){
						out.flush();
					}
					// graceful end
					mClientSock.shutdownOutput();
					mClientSock.shutdownInput();
				}
				catch( Throwable e ){
					Log.w( this.getClass().getSimpleName(), e );
				}
			}
		}
	}
	
	static class ServerThread extends Thread{
		protected ServerSocket mServerSock;
		protected int mRequestWorkersMax;
		
		@Override
		public synchronized void start(){
			// Preparation: exceptions are thrown here in case of a startup failure
			try{
				mServerSock = new ServerSocket( ApplicationHere.getCfgListenPort() );
				//mServerSock = new ServerSocket( ApplicationHere.getCfgListenPort(), 0, InetAddress.getByName( "172.16.0.2" ) ); // test
				mRequestWorkersMax = ApplicationHere.getCfgRequestWorkersMax();
				// mRequestWorkersMax>=2: process request in a worker thread, use semaphore to limit
				// mRequestWorkersMax==1: process request in the current thread
				// mRequestWorkersMax<=0: process request in a worker thread, no limit
				if( mRequestWorkersMax >= 2 ){
					// new fresh count (important because workers are daemonic)
					sSemaphoreWorkers = new Semaphore( mRequestWorkersMax, true );
				}
				else{
					sSemaphoreWorkers = null;
				}
				super.start();
			}
			catch( IOException e ){
				throw new RuntimeException( e );
			}
		}
		
		@Override
		public void run(){
			sServerSock = mServerSock;
			while( !isInterrupted() ){
				Socket clientSock;
				boolean shouldReleaseNow = false; // semaphore release
				try{
					if( sSemaphoreWorkers!=null ){
						sSemaphoreWorkers.acquire();
						shouldReleaseNow = true;
					}
					if( isInterrupted() ){
						break;
					}
					clientSock = mServerSock.accept();
					if( clientSock!=null ){
						if( ApplicationHere.isCfgAddressAllowed( clientSock.getInetAddress() ) ){
							if( mRequestWorkersMax==1 ){
								// Run in the current thread
								RequestThread requestThread = new RequestThread( clientSock );
								requestThread.run();
							}
							else{
								// Run in a worker thread
								RequestThread requestThread = new RequestThread( clientSock );
								requestThread.setDaemon( true );
								requestThread.start();
								if( sSemaphoreWorkers!=null ){
									// The semaphore will be released in the worker thread.
									shouldReleaseNow = false;
								}
							}
						}
						else{
							clientSock.close(); // TCP RST
						}
					}
				}
				catch( Throwable e ){
					if( mServerSock.isClosed() ){
						Log.i( this.getClass().getSimpleName(), e.getLocalizedMessage() );
					}
					else{
						Log.w( this.getClass().getSimpleName(), e );
					}
				}
				finally{
					if( shouldReleaseNow ){
						// The semaphore will not be released in the worker thread.
						sSemaphoreWorkers.release();
					}
				}
			}
			// Warning: if the socket server fails at startup, the notification may unset at that moment
			sThreadHttp = null;
			if( sInstance!= null ){
				sInstance.stopForeground( true );
			}
			sSemaphoreWorkers = null;
		}
	}
	
	protected static Intent _compose_IntentMakeActivity(Uri dest, String action){
		Intent intent = new Intent( action, dest );
		intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
		if( Build.VERSION.SDK_INT >= 26 ){
			intent.addFlags( Intent.FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS ); // needed?
		}
		if( ApplicationHere.isCfgComposeSpeakerphone() ){
			intent.putExtra( EXTRA_START_CALL_WITH_SPEAKERPHONE, true );
		}
		return intent;
	}
	
	protected static Intent _compose_IntentMakeReceiver(Uri dest, String action){
		Intent intent = new Intent( action, dest );
		switch( action ){
			// security: restrict package (does not work with an explicit class)
			case ReceiverPlaceCall.ACTION_PLACE_CALL:
				intent.setPackage( ReceiverPlaceCall.class.getPackage().getName() );
				//intent.setClass( sInstance, ReceiverPlaceCall.class );
				break;
		}
		return intent;
	}
	
	protected static void _compose_IntentRunActivity(Uri dest, String action){
		Intent intent = _compose_IntentMakeActivity( dest, action );
		sInstance.startActivity( intent );
	}
	
	protected static void _compose_TelecomManagerPlaceCall(Uri dest){
		ReceiverPlaceCall.placeCall( dest );
	}
	
	protected static void compose(Uri dest){
		switch( ApplicationHere.getCfgComposeMode() ){
			case INTENT_CALL:
				_compose_IntentRunActivity( dest, Intent.ACTION_CALL );
				break;
			case INTENT_DIAL:
				_compose_IntentRunActivity( dest, Intent.ACTION_DIAL );
				break;
			case INTENT_VIEW:
				_compose_IntentRunActivity( dest, Intent.ACTION_VIEW );
				break;
			case PLACE_CALL:
				_compose_TelecomManagerPlaceCall( dest );
				break;
			default:
				throw new HttpProcessCommon.HTTP( (short)500, "Unknown CFG_COMPO_MODE value!" );
		}
	}
	
	protected static Intent composeNotificationIntent(Uri dest){
		// Intent to be used in clicked notifications
		Intent intent;
		switch( ApplicationHere.getCfgComposeMode() ){
			case INTENT_CALL:
				intent = _compose_IntentMakeActivity( dest, Intent.ACTION_CALL );
				break;
			case INTENT_DIAL:
				intent = _compose_IntentMakeActivity( dest, Intent.ACTION_DIAL );
				break;
			case INTENT_VIEW:
				intent = _compose_IntentMakeActivity( dest, Intent.ACTION_VIEW );
				break;
			case PLACE_CALL:
				intent = _compose_IntentMakeReceiver( dest, ReceiverPlaceCall.ACTION_PLACE_CALL );
				break;
			default:
				throw new HttpProcessCommon.HTTP( (short)500, "Unknown CFG_COMPO_MODE value!" );
		}
		return intent;
	}
	
	protected static void runVibrator(){
		DeviceFeatures.runVibrator( 700 );
	}
	
	protected static void runRing(){ // TODO
		// TODO - AudioManager.getRingerMode()
		/*
		try {
		   AudioManager am = (AudioManager)sInstance.getSystemService(Context.AUDIO_SERVICE);
		}
		catch(Throwable e){
		   Log.w( this.getClass().getSimpleName(), "runRing(): "+e.getLocalizedMessage(), e );
		}
		*/
	}
	
	protected static void handleRequest(Socket clientSock, InputStream in, OutputStream out){
		String contentType = HttpProcessCommon.MIME_HTML;
		HttpRequest request = null;
		try{
			Log.i( HttpServer.class.getSimpleName(), "Handling request ["+clientSock.toString()+"]" );
			clientSock.setSoTimeout( ApplicationHere.getCfgRequestExpire_ms() );
			HttpRequestParser requestParser = new HttpRequestParser( clientSock, in );
			request = requestParser.parse();
			Log.i( HttpServer.class.getSimpleName(), "Parsed request" );
			switch( request.getUri() ){
				case URI_HOME_HTML:
					contentType = HttpProcessCommon.MIME_HTML;
					HttpResponseMaker.make(
						out, request, (short)200, contentType,
						"<html><body><h1>It works!</h1></body></html>",
						true
					);
					break;
				case URI_CALLFORM_HTML:
					contentType = HttpProcessCommon.MIME_HTML;
					HttpResponseMaker.make(
						out, request, (short)200, contentType,
						"<html><body>\n"+
							"<form action='./docall.html' method='POST'>\n"+
							"<input type='text' name='number'/> <input type='submit' value='Compose'/>\n"+
							"</form>\n"+
							"</body></html>\n",
						true
					);
					break;
				case URI_TOKEN_JSON:
					contentType = HttpProcessCommon.MIME_JSON;
					HttpResponseMaker.make(
						out, request, (short)503, contentType,
						"{\"status\":503, \"token\":null}",
						true
					);
					break;
				case URI_DOCALL_HTML:
				case URI_DOCALL_JSON:
					contentType = HttpProcessCommon.MIME_HTML;
					if( request.getUri().equals( URI_DOCALL_JSON ) ){
						contentType = HttpProcessCommon.MIME_JSON;
					}
					String requestMethod = request.getMethod();
					if( requestMethod.equals( HttpProcessCommon.METHOD_POST ) ){
						String phoneNumber = null;
						try{
							phoneNumber = (String)request.getFieldPost( "number" );
						}
						catch( ClassCastException e ){
						}
						if( phoneNumber==null ){
							throw new HttpProcessCommon.HTTP( (short)400, "Missing \"number\" POST field" );
						}
						switch( ApplicationHere.getCfgPasswordUse() ){
							case BASIC:
								String password = request.getPassword();
								if( password==null || !request.getPassword().equals( ApplicationHere.getCfgPassword() ) ){
									throw new HttpProcessCommon.HTTP( (short)401, "Please enter the correct password." );
								}
								break;
							case TOKEN_SIGNED:
								throw new HttpProcessCommon.HTTP( (short)500, "Unsupported CFG_PASSWORD_USE.TOKEN_SIGNED!" );
								//break;
							case ENCRYPTED:
								throw new HttpProcessCommon.HTTP( (short)500, "Unsupported CFG_PASSWORD_USE.ENCRYPTED!" );
								//break;
							case NONE:
								break;
							default:
								throw new HttpProcessCommon.HTTP( (short)500, "Unknown CFG_PASSWORD_USE value!" );
						}
						if( !ApplicationHere.isCfgNumberAllowed( phoneNumber ) ){
							// after password requirement, for privacy
							throw new HttpProcessCommon.HTTP( (short)403, "The specified number does not match the regular expression." );
						}
						Uri dest = Uri.fromParts( "tel", phoneNumber, null );
						if( ApplicationHere.isCfgForbidIfLocked() && DeviceFeatures.isLocked() ){
							throw new HttpProcessCommon.HTTP( (short)503, "Please unlock the device first." );
						}
						else{
							/*
							// TODO - gestion permissions lors de validation
							if (ContextCompat.checkSelfPermission(sInstance, Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED) {
							  _compose_TelecomManagerPlaceCall(dest);
							}
							*/
							//}
							
							if( ApplicationHere.isCfgComposeNotifActual() ){
								NotificationCompat.Builder builder = new NotificationCompat.Builder(
									sInstance,
									Integer.toString( ApplicationHere.NOTIFICATION_COMPOSE_REQUEST )
								);
								builder.setSmallIcon( R.drawable.ic_launcher_background ); // TODO - jolie icône
								builder.setContentTitle( sInstance.getString( R.string.app_name ) );
								builder.setContentText( "Call "+phoneNumber );
								builder.setPriority( NotificationCompat.PRIORITY_DEFAULT );
								Intent intent = composeNotificationIntent( dest );
								if( ReceiverPlaceCall.ACTION_PLACE_CALL.equals( intent.getAction() ) ){
									builder.setContentIntent( PendingIntent.getBroadcast(
										sInstance,
										ApplicationHere.NOTIFICATION_COMPOSE_REQUEST,
										composeNotificationIntent( dest ),
										PendingIntent.FLAG_ONE_SHOT
									) );
								}
								else{
									builder.setContentIntent( PendingIntent.getActivity(
										sInstance,
										ApplicationHere.NOTIFICATION_COMPOSE_REQUEST,
										intent,
										PendingIntent.FLAG_ONE_SHOT
									) );
								}
								builder.setAutoCancel( true );
								builder.setVisibility( NotificationCompat.VISIBILITY_PUBLIC );
								builder.setSmallIcon( android.support.design.R.drawable.abc_ratingbar_indicator_material );
								DeviceFeatures.getNotificationManager().notify(
									ApplicationHere.NOTIFICATION_COMPOSE_REQUEST,
									builder.build()
								);
								HttpResponseMaker.make( out, request, (short)200, contentType, "The call should be pending in a notification!", false );
							}
							else{
								compose( dest );
								HttpResponseMaker.make( out, request, (short)200, contentType, "The call should have been initiated!", false );
							}
							if( false ){ // TODO
								runRing();
							}
							if( ApplicationHere.isCfgComposeVibrateActual() ){
								runVibrator();
							}
						}
					}
					else{
						HashMap<String, Object> extraInfo = new HashMap<>();
						extraInfo.put( "Allow", HttpProcessCommon.METHOD_POST );
						if( requestMethod.equals( HttpProcessCommon.METHOD_OPTIONS ) ){
							throw new HttpProcessCommon.HTTP( (short)204, "", extraInfo );
						}
						else{
							throw new HttpProcessCommon.HTTP( (short)405, "To make a call, a POST request must be submitted.", extraInfo );
						}
					}
					break;
				default:
					HttpResponseMaker.make( out, request, (short)404, contentType, "In this world, you are nowhere.", false );
			}
		}
		catch( HttpProcessCommon.HTTP e ){
			try{
				HttpResponseMaker.make( out, request, e.getStatus(), contentType, e.getLocalizedMessage(), false, e.getExtraInfo() );
			}
			catch( Throwable e1 ){
				Log.w(
					HttpServer.class.getSimpleName(),
					"handleRequest(): "+e1.getLocalizedMessage(),
					e1
				);
			}
		}
		catch( Throwable e ){
			Log.e(
				HttpServer.class.getSimpleName(),
				"handleRequest(): "+e.getLocalizedMessage(),
				e
			);
			try{
				String message = "An exception occurred.";
				HashMap<String, Object> extraInfo = null;
				if( ApplicationHere.isCfgShowDetails500() ){
					message = e.getLocalizedMessage();
					extraInfo = new HashMap<>();
					extraInfo.put( "stack", e.getStackTrace() );
				}
				HttpResponseMaker.make( out, request, (short)500, contentType, message, false, extraInfo );
			}
			catch( Throwable e1 ){
				Log.w(
					HttpServer.class.getSimpleName(),
					"handleRequest(): "+e1.getLocalizedMessage(),
					e1
				);
			}
		}
		Log.i(
			HttpServer.class.getSimpleName(),
			"Finished request ["+clientSock.toString()+"]"
		);
	}
	
	static HttpServer getInstance(){
		return sInstance;
	}
	
	static boolean isRunning(){
		return ( sThreadHttp!=null && sServerSock!=null );
	}
	
	@Override
	public IBinder onBind(Intent intent){
		return null;
	}
	
	@Override
	public boolean onUnbind(Intent intent){
		return super.onUnbind( intent );
	}
	
	@Override
	public void onCreate(){
		Log.i( this.getClass().getSimpleName(), "onCreate()" );
		super.onCreate();
		sInstance = this;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId){
		Log.i( this.getClass().getSimpleName(), "onStartCommand()" );
		try{
			if( sThreadHttp==null ){
				// Run service:
				ServerThread threadHttp = new ServerThread();
				threadHttp.setDaemon( true );
				threadHttp.start();
				sThreadHttp = threadHttp;
				Log.i( this.getClass().getSimpleName(), "sThreadHttp.start()" );
				
				// Notification:
				Intent intentActivity = new Intent();
				intentActivity.setAction( Intent.ACTION_MAIN );
				intentActivity.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
				intentActivity.addCategory( Intent.CATEGORY_LAUNCHER );
				intentActivity.setClass( this, MainActivity.class );
				NotificationCompat.Builder builder = new NotificationCompat.Builder(
					sInstance,
					Integer.toString( ApplicationHere.NOTIFICATION_SERVICE_RUNNING )
				);
				//builder.setSmallIcon( android.support.design.R.drawable.abc_ic_go_search_api_material );
				builder.setSmallIcon( android.support.design.R.drawable.navigation_empty_icon );
				builder.setBadgeIconType( NotificationCompat.BADGE_ICON_NONE ); // ignored?!
				{
					Drawable drawable = AppCompatResources.getDrawable( sInstance, R.mipmap.ic_launcher_round );
					if( drawable instanceof BitmapDrawable ){
						builder.setLargeIcon( ( (BitmapDrawable)drawable ).getBitmap() );
					}
				}
				builder.setContentTitle( sInstance.getString( R.string.app_name ) );
				builder.setContentText( getString( R.string.notif_service_running ) );
				builder.setPriority( NotificationCompat.PRIORITY_MIN ); // needed for VISIBILITY_SECRET
				builder.setContentIntent( PendingIntent.getActivity(
					sInstance,
					0,
					intentActivity,
					0
				) );
				builder.setCategory( NotificationCompat.CATEGORY_SERVICE );
				builder.setVisibility( NotificationCompat.VISIBILITY_SECRET ); // ignored?! see builder.setPriority()
				Notification notification = builder.build();
				notification.flags = Notification.FLAG_FOREGROUND_SERVICE|Notification.FLAG_NO_CLEAR;
				
				this.startForeground( ApplicationHere.NOTIFICATION_SERVICE_RUNNING, notification );
			}
		}
		catch( Throwable e ){
			Log.e( this.getClass().getSimpleName(), e.getLocalizedMessage(), e );
			Toast.makeText(
				ApplicationHere.getInstance(),
				getString( R.string.app_name )+"\n"+this.getClass().getSimpleName()+"\n"+e.getClass().getSimpleName()+"\n"+e.getLocalizedMessage(),
				Toast.LENGTH_LONG
			).show();
			// AlertDialog not guaranteed to be available from here
		}
		return START_STICKY;
	}
	
	@Override
	public void onDestroy(){
		// The server will not successfully start again until sServerSock is closed.
		
		Log.i( this.getClass().getSimpleName(), "onDestroy()" );
		sInstance = null;
		stopForeground( true );
		try{
			sThreadHttp.interrupt(); // set to null by the thread itself
		}
		catch( Throwable e ){
			Toast.makeText( this, e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
		}
		try{
			sServerSock.close();
			sServerSock = null;
		}
		catch( Throwable e ){
			Log.w( this.getClass().getSimpleName(), e );
		}
		super.onDestroy();
	}
}
