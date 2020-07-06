// TODO - filtrage par SSID WiFi
// TODO - jeton optionnel
// TODO - utiliser des constantes pour le stockage des paramètres
// TODO - section "pare-feu" avec : SSID (regex), interfaces autorisées [chercher moyen], IP des clients [déjà fait]
//    Socket.getLocalAddress() -> interface concernée

package ovh.thouvest.phonecomposerhttp;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.StrictMode;
import android.support.v7.app.AlertDialog;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.regex.Pattern;

public final class ApplicationHere extends Application{
	static final int NOTIFICATION_SERVICE_RUNNING = 1;
	static final int NOTIFICATION_COMPOSE_REQUEST = 2; // also channel id
	
	static final String KEY_ServiceAutoStart = "ServiceAutoStart";
	static final String KEY_ListenPort = "ListenPort";
	static final String KEY_HeaderServer = "HeaderServer";
	static final String KEY_HeaderPoweredBy = "HeaderPoweredBy";
	static final String KEY_ShowDetails500 = "ShowDetails500";
	static final String KEY_RequestWorkersMax = "RequestWorkersMax";
	static final String KEY_RequestExpire_ms = "RequestExpire_ms";
	//static final String KEY_AddressDenyReaction = "AddressDenyReaction";
	static final String KEY_AddressWhitelist = "AddressWhitelist";
	static final String KEY_AddressBlacklist = "AddressBlacklist";
	static final String KEY_PasswordUse = "PasswordUse";
	static final String KEY_Password = "Password";
	static final String KEY_ForbidIfLocked = "ForbidIfLocked";
	//static final String KEY_AllowedDuringCall = "AllowedDuringCall";
	static final String KEY_ComposeNotif = "ComposeNotif";
	static final String KEY_ComposeVibrate = "ComposeVibrate";
	static final String KEY_ComposeMode = "ComposeMode";
	static final String KEY_ComposeSpeakerphone = "ComposeSpeakerphone";
	static final String KEY_NumbersAllowed = "NumbersAllowed";
	
	static final int CFG_LISTEN_PORT_DEFAULT = 26676; // "COMPO"
	static final String CFG_ADDRESS_WHITELIST_DEFAULT = (
		// Default IP address ranges are local networks, see:
		// - https://en.wikipedia.org/wiki/IPv4
		// - https://en.wikipedia.org/wiki/Private_network
		// - https://en.wikipedia.org/wiki/IPv6_address
		"10.0.0.0/8\n"+
		"127.0.0.1/8\n"+
		"169.254.0.0/16\n"+
		"172.16.0.0/12\n"+
		"192.0.0.0/24\n"+
		"192.168.0.0/16\n"+
		"::1\n"+
		"fc00::/7\n"+
		"fe80::/10"
	);
	
	enum CFG_AUTOSTART{
		NO, // 0b00
		YES, // 0b01
		LAST_STATE_NO, // 0b10
		LAST_STATE_YES; // 0b11
		
		boolean isPreserveState(){
			return ( ( this.ordinal()&0b10 )==0b10 );
		}
		
		boolean isYes(){
			return ( ( this.ordinal()&0b01 )==0b01 );
		}
	}
	
	enum CFG_PASSWORD_USE{
		NONE,
		BASIC,
		TOKEN_SIGNED,
		ENCRYPTED;
	}
	
	/*
	enum CFG_ADDRESS_DENY{
		RST, // TCP RST
		_403; // HTTP 403
	}
	*/
	
	enum CFG_COMPO_MODE{
		INTENT_DIAL, // Pre-composes the number without application choice
		INTENT_VIEW, // Pre-composes the number without application choice
		INTENT_CALL, // Run the call with application choice [android.permission.CALL_PHONE]
		PLACE_CALL; // Run the call without application choice, even when locked [android.permission.CALL_PHONE]
	}
	
	enum CFG_COMPO_NOTIF{
		YES,
		IF_LOCKED,
		NO;
	}
	
	enum CFG_COMPO_VIBRATE{
		YES,
		IF_LOCKED,
		NO;
	}
	
	protected static ApplicationHere sInstance = null;
	protected static SharedPreferences sPreferences = null;
	
	// First-start configuration: working, safe, easy
	// TODO - permissions lors de la soumission des paramètres
	protected static CFG_AUTOSTART sCfgServiceAutoStart = CFG_AUTOSTART.NO;
	protected static int sCfgListenPort = CFG_LISTEN_PORT_DEFAULT;
	protected static boolean sCfgHeaderServer = false;
	protected static boolean sCfgHeaderPoweredBy = false;
	protected static boolean sCfgShowDetails500 = false;
	protected static int sCfgRequestWorkersMax = 0;
	protected static int sCfgRequestExpire_ms = 2000;
	//protected static CFG_ADDRESS_DENY sCfgAddressDenyReaction = CFG_ADDRESS_DENY.RST;
	protected static HashSet<Utility.InetRange> sCfgAddressWhitelist = null; // no whitelist
	protected static HashSet<Utility.InetRange> sCfgAddressBlacklist = null; // no blacklist
	protected static CFG_PASSWORD_USE sCfgPasswordUse = CFG_PASSWORD_USE.NONE;
	protected static String sCfgPassword = "";
	protected static boolean sCfgForbidIfLocked = true;
	//protected static boolean sCfgAllowedDuringCall = false;
	protected static CFG_COMPO_NOTIF sCfgComposeNotif = CFG_COMPO_NOTIF.YES;
	protected static CFG_COMPO_VIBRATE sCfgComposeVibrate = CFG_COMPO_VIBRATE.YES;
	protected static CFG_COMPO_MODE sCfgComposeMode = CFG_COMPO_MODE.INTENT_DIAL;
	protected static boolean sCfgComposeSpeakerphone = true;
	protected static Pattern sCfgNumbersAllowed = null; // TODO - page test JavaScript
	
	
	static ApplicationHere getInstance(){
		return sInstance;
	}
	
	@Override
	public void onCreate(){
		super.onCreate();
		sInstance = this;
		sPreferences = getSharedPreferences( sInstance.getPackageName(), Context.MODE_PRIVATE );
		DeviceFeatures.initialize();
		createNotificationChannels();
		ReceiverPlaceCall.activate( this );
		
		// Allow networking & disk in main thread:
		StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX);
		
		
		// Load the configuration:
		int errorField = R.string.app_name;
		try{
			/*
			sCfgComposeNotif = CFG_COMPO_NOTIF.YES;
			sCfgComposeMode = CFG_COMPO_MODE.PLACE_CALL;
			 */
			SharedPreferences pref = ApplicationHere.getAllPreferences( this );
			errorField = R.string.cfg_autostart;
			sCfgServiceAutoStart = CFG_AUTOSTART.values()[pref.getInt(
				KEY_ServiceAutoStart, sCfgServiceAutoStart.ordinal() )];
			errorField = R.string.cfg_listen_port;
			sCfgListenPort = pref.getInt( KEY_ListenPort, sCfgListenPort );
			errorField = R.string.cfg_header_server;
			sCfgHeaderServer = pref.getBoolean( KEY_HeaderServer, sCfgHeaderServer );
			errorField = R.string.cfg_header_powered_by;
			sCfgHeaderPoweredBy = pref.getBoolean( KEY_HeaderPoweredBy, sCfgHeaderPoweredBy );
			errorField = R.string.cfg_show_details_500;
			sCfgShowDetails500 = pref.getBoolean( KEY_ShowDetails500, sCfgShowDetails500 );
			errorField = R.string.cfg_request_workers_max;
			sCfgRequestWorkersMax = pref.getInt( KEY_RequestWorkersMax, sCfgRequestWorkersMax );
			errorField = R.string.cfg_request_expire;
			sCfgRequestExpire_ms = pref.getInt( KEY_RequestExpire_ms, sCfgRequestExpire_ms );
			//errorField = R.string.cfg_address_deny_reaction;
			//sCfgAddressDenyReaction = CFG_ADDRESS_DENY.values()[pref.getInt(
			// "AddressDenyReaction, sCfgAddressDenyReaction.ordinal() )];
			errorField = R.string.cfg_address_whitelist;
			sCfgAddressWhitelist = (HashSet<Utility.InetRange>)Utility.InetRange.fillCollectionFromUserString(
				new HashSet<Utility.InetRange>(),
				pref.getString( KEY_AddressWhitelist, CFG_ADDRESS_WHITELIST_DEFAULT )
			);
			errorField = R.string.cfg_address_blacklist;
			sCfgAddressBlacklist = (HashSet<Utility.InetRange>)Utility.InetRange.fillCollectionFromUserString(
				new HashSet<Utility.InetRange>(),
				pref.getString( KEY_AddressBlacklist, "" )
			);
			errorField = R.string.cfg_password_use;
			sCfgPasswordUse = CFG_PASSWORD_USE.values()[pref.getInt(
				KEY_PasswordUse, sCfgPasswordUse.ordinal() )];
			errorField = R.string.cfg_password;
			sCfgPassword = pref.getString( KEY_Password, sCfgPassword );
			errorField = R.string.cfg_forbid_if_locked;
			sCfgForbidIfLocked = pref.getBoolean( KEY_ForbidIfLocked, sCfgForbidIfLocked );
			//errorField = R.string.cfg_allowed_during_call;
			//sCfgAllowedDuringCall = pref.getBoolean( KEY_AllowedDuringCall, sCfgAllowedDuringCall );
			errorField = R.string.cfg_compo_notif;
			sCfgComposeNotif = CFG_COMPO_NOTIF.values()[pref.getInt(
				KEY_ComposeNotif, sCfgComposeNotif.ordinal() )];
			errorField = R.string.cfg_compose_vibrate;
			sCfgComposeVibrate = CFG_COMPO_VIBRATE.values()[pref.getInt(
				KEY_ComposeVibrate, sCfgComposeVibrate.ordinal() )];
			errorField = R.string.cfg_compose_mode;
			sCfgComposeMode = CFG_COMPO_MODE.values()[pref.getInt(
				KEY_ComposeMode, sCfgComposeMode.ordinal() )];
			errorField = R.string.cfg_compose_speakerphone;
			sCfgComposeSpeakerphone = pref.getBoolean( KEY_ComposeSpeakerphone, sCfgComposeSpeakerphone );
			errorField = R.string.cfg_numbers_allowed;
			{
				// TODO - vérifier qu'on a une vérification sur la regex complète (liste de numéros possible)
				String pattern = pref.getString( KEY_NumbersAllowed, "" );
				if( pattern.length()==0 ){
					sCfgNumbersAllowed = null;
				}
				else{
					sCfgNumbersAllowed = Pattern.compile( pattern );
				}
			}
			errorField = R.string.app_name;
		}
		catch( Throwable e ){
			AlertDialog.Builder builder = new AlertDialog.Builder( this );
			builder.setTitle( errorField );
			builder.setMessage( e.getClass().getSimpleName()+"\n"+e.getLocalizedMessage() );
			builder.create().show();
		}
	}
	
	private void createNotificationChannels(){
		// from https://developer.android.com/training/notify-user/build-notification.html
		if( Build.VERSION.SDK_INT >= 26 ){
			NotificationManager notificationManager = getSystemService( NotificationManager.class );
			if( notificationManager!=null ){
				NotificationChannel channel;
				
				channel = new NotificationChannel(
					Integer.toString( NOTIFICATION_COMPOSE_REQUEST ),
					getString( R.string.notif_channel_call_name ),
					NotificationManager.IMPORTANCE_HIGH
				);
				channel.setDescription( getString( R.string.notif_channel_call_description ) );
				notificationManager.createNotificationChannel( channel );
				
				channel = new NotificationChannel(
					Integer.toString( NOTIFICATION_SERVICE_RUNNING ),
					getString( R.string.notif_channel_service_name ),
					NotificationManager.IMPORTANCE_LOW
				);
				channel.setDescription( getString( R.string.notif_service_running ) );
				notificationManager.createNotificationChannel( channel );
			}
		}
	}
	
	/// Raw setters: ///
	
	static void setCfgServiceAutoStart(CFG_AUTOSTART setting){
		sCfgServiceAutoStart = setting;
	}
	
	static void setCfgListenPort(int setting){
		sCfgListenPort = setting;
	}
	
	static void setCfgHeaderServer(boolean setting){
		sCfgHeaderServer = setting;
	}
	
	static void setCfgHeaderPoweredBy(boolean setting){
		sCfgHeaderPoweredBy = setting;
	}
	
	static void setCfgShowDetails500(boolean setting){
		sCfgShowDetails500 = setting;
	}
	
	static void setCfgRequestWorkersMax(int setting){
		sCfgRequestWorkersMax = setting;
	}
	
	static void setCfgRequestExpire_ms(int setting){
		sCfgRequestExpire_ms = setting;
	}
	
	/*
	static void setCfgAddressDenyReaction(CFG_ADDRESS_DENY setting){
		sCfgAddressDenyReaction = setting;
	}
	*/
	
	static void setCfgAddressWhitelist(HashSet<Utility.InetRange> setting){
		sCfgAddressWhitelist = setting;
	}
	
	static void setCfgAddressBlacklist(HashSet<Utility.InetRange> setting){
		sCfgAddressBlacklist = setting;
	}
	
	static void setCfgPasswordUse(CFG_PASSWORD_USE setting){
		sCfgPasswordUse = setting;
	}
	
	static void setCfgPassword(String setting){
		sCfgPassword = setting;
	}
	
	static void setCfgForbidIfLocked(boolean setting){
		sCfgForbidIfLocked = setting;
	}
	
	/*static void setCfgAllowedDuringCall(boolean setting){
		sCfgAllowedDuringCall = setting;
	}*/
	
	static void setCfgComposeNotif(CFG_COMPO_NOTIF setting){
		sCfgComposeNotif = setting;
	}
	
	static void setCfgComposeVibrate(CFG_COMPO_VIBRATE setting){
		sCfgComposeVibrate = setting;
	}
	
	static void setCfgComposeMode(CFG_COMPO_MODE setting){
		sCfgComposeMode = setting;
	}
	
	static void setCfgComposeSpeakerphone(boolean setting){
		sCfgComposeSpeakerphone = setting;
	}
	
	static void setCfgNumbersAllowed(Pattern setting){
		sCfgNumbersAllowed = setting;
	}
	
	/// Raw accessors: ///
	
	static SharedPreferences getAllPreferences(Context context){
		if( context instanceof ApplicationHere || context instanceof ActivitySettings ){
			return sPreferences;
		}
		else{
			return null;
		}
	}
	
	static CFG_AUTOSTART getCfgServiceAutoStart(){
		return sCfgServiceAutoStart;
	}
	
	static int getCfgListenPort(){
		return sCfgListenPort;
	}
	
	static boolean isCfgHeaderServer(){
		return sCfgHeaderServer;
	}
	
	static boolean isCfgHeaderPoweredBy(){
		return sCfgHeaderPoweredBy;
	}
	
	static boolean isCfgShowDetails500(){
		return sCfgShowDetails500;
	}
	
	static int getCfgRequestWorkersMax(){
		return sCfgRequestWorkersMax;
	}
	
	static int getCfgRequestExpire_ms(){
		return sCfgRequestExpire_ms;
	}
	
	/*
	static CFG_ADDRESS_DENY getCfgAddressDenyReaction(){
		return sCfgAddressDenyReaction;
	}
	*/
	
	static HashSet<Utility.InetRange> getCfgAddressWhitelist(){
		return sCfgAddressWhitelist;
	}
	
	static HashSet<Utility.InetRange> getCfgAddressBlacklist(){
		return sCfgAddressBlacklist;
	}
	
	static CFG_PASSWORD_USE getCfgPasswordUse(){
		return sCfgPasswordUse;
	}
	
	static String getCfgPassword(){
		return sCfgPassword;
	}
	
	static boolean isCfgForbidIfLocked(){
		return sCfgForbidIfLocked;
	}
	
	/*static boolean isCfgAllowedDuringCall(){
		return sCfgAllowedDuringCall;
	}*/
	
	static CFG_COMPO_NOTIF getCfgComposeNotif(){
		return sCfgComposeNotif;
	}
	
	static CFG_COMPO_VIBRATE getCfgComposeVibrate(){
		return sCfgComposeVibrate;
	}
	
	static CFG_COMPO_MODE getCfgComposeMode(){
		return sCfgComposeMode;
	}
	
	static boolean isCfgComposeSpeakerphone(){
		return sCfgComposeSpeakerphone;
	}
	
	static Pattern getCfgNumbersAllowed(){
		return sCfgNumbersAllowed;
	}
	
	/// Smart accessors (thread-safe through copied references to unmodified objects) ///
	
	static boolean isCfgAddressAllowed(InetAddress clientAddress){
		boolean allowed = true;
		HashSet<Utility.InetRange> cfgAddressWhitelist = sCfgAddressWhitelist;
		HashSet<Utility.InetRange> cfgAddressBlacklist = sCfgAddressBlacklist;
		if( cfgAddressWhitelist!=null ){
			allowed = false;
			for( Utility.InetRange range: cfgAddressWhitelist ){
				if( range.isIncluded( clientAddress ) ){
					allowed = true;
					break;
				}
			}
		}
		if( allowed && cfgAddressBlacklist!=null ){
			for( Utility.InetRange range: cfgAddressBlacklist ){
				if( range.isIncluded( clientAddress ) ){
					allowed = false;
					break;
				}
			}
		}
		return allowed;
	}
	
	static boolean isCfgComposeNotifActual(){
		switch( sCfgComposeNotif ){
			case NO:
				return false;
			case IF_LOCKED:
				return DeviceFeatures.isLocked();
			case YES:
			default:
				return true;
		}
	}
	
	static boolean isCfgComposeVibrateActual(){
		switch( sCfgComposeVibrate ){
			case NO:
				return false;
			case IF_LOCKED:
				return DeviceFeatures.isLocked();
			case YES:
			default:
				return true;
		}
	}
	
	static boolean isCfgNumberAllowed(String phoneNumber){
		Pattern cfgNumbersAllowed = sCfgNumbersAllowed;
		return ( cfgNumbersAllowed==null || cfgNumbersAllowed.matcher( phoneNumber ).matches() );
	}
}
