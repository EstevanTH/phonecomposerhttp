// TODO - bouton "Restore defaults"
// TODO - préservation en cas de destruction / rotation d'écran ?

package ovh.thouvest.phonecomposerhttp;

import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import java.util.HashSet;
import java.util.regex.Pattern;

public class ActivitySettings extends AppCompatActivity implements View.OnClickListener{
	
	// TODO - pourquoi les champs sont-ils restaurés sur changement d'orientation ?
	// TODO - case à cocher non mémorisée pour autoriser valeurs dangereuses
	
	protected Spinner mddCfgServiceAutoStart;
	protected AppCompatEditText mtxtCfgListenPort;
	protected AppCompatCheckBox mcbCfgHeaderServer;
	protected AppCompatCheckBox mcbCfgHeaderPoweredBy;
	protected AppCompatCheckBox mcbCfgShowDetails500;
	protected AppCompatEditText mtxtCfgRequestWorkersMax;
	protected AppCompatEditText mtxtCfgRequestExpire_ms;
	//protected Spinner mddCfgAddressDenyReaction;
	protected AppCompatEditText mtxtCfgAddressWhitelist;
	protected AppCompatEditText mtxtCfgAddressBlacklist;
	protected Spinner mddCfgPasswordUse;
	protected AppCompatEditText mtxtCfgPassword;
	protected AppCompatCheckBox mcbCfgForbidIfLocked;
	//protected AppCompatCheckBox mcbCfgAllowedDuringCall;
	protected Spinner mddCfgComposeNotif;
	protected Spinner mddCfgComposeVibrate;
	protected Spinner mddCfgComposeMode;
	protected AppCompatCheckBox mcbCfgComposeSpeakerphone;
	protected AppCompatEditText mtxtCfgNumbersAllowed;
	protected Button mbtnOk;
	protected Button mbtnCancel;
	protected Button mbtnApply;
	
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate( savedInstanceState );
		try{
			setContentView( R.layout.activity_settings );
			Toolbar toolbar = findViewById( R.id.toolbar );
			setSupportActionBar( toolbar );
			
			// Initialize members for GUI elements:
			mddCfgServiceAutoStart = findViewById( R.id.ddCfgServiceAutoStart );
			mtxtCfgListenPort = findViewById( R.id.txtCfgListenPort );
			mcbCfgHeaderServer = findViewById( R.id.cbCfgHeaderServer );
			mcbCfgHeaderPoweredBy = findViewById( R.id.cbCfgHeaderPoweredBy );
			mcbCfgShowDetails500 = findViewById( R.id.cbCfgShowDetails500 );
			mtxtCfgRequestWorkersMax = findViewById( R.id.txtCfgRequestWorkersMax );
			mtxtCfgRequestExpire_ms = findViewById( R.id.txtCfgRequestExpire_ms );
			//mddCfgAddressDenyReaction = findViewById( R.id.ddCfgAddressDenyReaction );
			mtxtCfgAddressWhitelist = findViewById( R.id.txtCfgAddressWhitelist );
			mtxtCfgAddressBlacklist = findViewById( R.id.txtCfgAddressBlacklist );
			mddCfgPasswordUse = findViewById( R.id.ddCfgPasswordUse );
			mtxtCfgPassword = findViewById( R.id.txtCfgPassword );
			mcbCfgForbidIfLocked = findViewById( R.id.cbCfgForbidIfLocked );
			//mcbCfgAllowedDuringCall = findViewById( R.id.cbCfgAllowedDuringCall );
			mddCfgComposeNotif = findViewById( R.id.ddCfgComposeNotif );
			mddCfgComposeVibrate = findViewById( R.id.ddCfgComposeVibrate );
			mddCfgComposeMode = findViewById( R.id.ddCfgComposeMode );
			mcbCfgComposeSpeakerphone = findViewById( R.id.cbCfgComposeSpeakerphone );
			mtxtCfgNumbersAllowed = findViewById( R.id.txtCfgNumbersAllowed );
			mbtnOk = findViewById( R.id.btnOk );
			mbtnCancel = findViewById( R.id.btnCancel );
			mbtnApply = findViewById( R.id.btnApply );
			
			// Configure fields:
			{
				String[] choices = {
					getString( R.string.value_no ),
					getString( R.string.value_yes ),
					getString( R.string.value_last_state )
				};
				Utility.guiFillDropdownStringArray( mddCfgServiceAutoStart, choices );
			}
			//Utility.guiFillDropdownEnum( mddCfgAddressDenyReaction, ApplicationHere.CFG_ADDRESS_DENY.class );
			Utility.guiFillDropdownEnum( mddCfgPasswordUse, ApplicationHere.CFG_PASSWORD_USE.class );
			Utility.guiFillDropdownEnum( mddCfgComposeNotif, ApplicationHere.CFG_COMPO_NOTIF.class );
			Utility.guiFillDropdownEnum( mddCfgComposeVibrate, ApplicationHere.CFG_COMPO_VIBRATE.class );
			Utility.guiFillDropdownEnum( mddCfgComposeMode, ApplicationHere.CFG_COMPO_MODE.class );
			
			mbtnOk.setOnClickListener( this );
			mbtnCancel.setOnClickListener( this );
			mbtnApply.setOnClickListener( this );
			
			// Fill current values:
			// Note: For IP blacklist & whitelist, the user entry is used so hostnames are allowed.
			SharedPreferences pref = ApplicationHere.getAllPreferences( this );
			{
				switch( ApplicationHere.getCfgServiceAutoStart() ){
					case YES:
						mddCfgServiceAutoStart.setSelection( 1 );
						break;
					case LAST_STATE_NO:
					case LAST_STATE_YES:
						mddCfgServiceAutoStart.setSelection( 2 );
						break;
					default:
						mddCfgServiceAutoStart.setSelection( 0 );
				}
			}
			Utility.guiTextViewSetNumber( mtxtCfgListenPort, ApplicationHere.getCfgListenPort() );
			mcbCfgHeaderServer.setChecked( ApplicationHere.isCfgHeaderServer() );
			mcbCfgHeaderPoweredBy.setChecked( ApplicationHere.isCfgHeaderPoweredBy() );
			mcbCfgShowDetails500.setChecked( ApplicationHere.isCfgShowDetails500() );
			Utility.guiTextViewSetNumber( mtxtCfgRequestWorkersMax, ApplicationHere.getCfgRequestWorkersMax() );
			Utility.guiTextViewSetNumber( mtxtCfgRequestExpire_ms, ApplicationHere.getCfgRequestExpire_ms() );
			//Utility.guiSpinnerSetEnum( mddCfgAddressDenyReaction, ApplicationHere.getCfgAddressDenyReaction() );
			Utility.guiTextViewSetText( mtxtCfgAddressWhitelist, pref.getString( ApplicationHere.KEY_AddressWhitelist, ApplicationHere.CFG_ADDRESS_WHITELIST_DEFAULT ) );
			Utility.guiTextViewSetText( mtxtCfgAddressBlacklist, pref.getString( ApplicationHere.KEY_AddressBlacklist, "" ) );
			Utility.guiSpinnerSetEnum( mddCfgPasswordUse, ApplicationHere.getCfgPasswordUse() );
			Utility.guiTextViewSetText( mtxtCfgPassword, ApplicationHere.getCfgPassword() );
			mcbCfgForbidIfLocked.setChecked( ApplicationHere.isCfgForbidIfLocked() );
			//mcbCfgAllowedDuringCall.setChecked( ApplicationHere.isCfgAllowedDuringCall() );
			Utility.guiSpinnerSetEnum( mddCfgComposeNotif, ApplicationHere.getCfgComposeNotif() );
			Utility.guiSpinnerSetEnum( mddCfgComposeVibrate, ApplicationHere.getCfgComposeVibrate() );
			Utility.guiSpinnerSetEnum( mddCfgComposeMode, ApplicationHere.getCfgComposeMode() );
			mcbCfgComposeSpeakerphone.setChecked( ApplicationHere.isCfgComposeSpeakerphone() );
			{
				Pattern pattern = ApplicationHere.getCfgNumbersAllowed();
				if( pattern!=null ){
					Utility.guiTextViewSetText( mtxtCfgNumbersAllowed, pattern.pattern() );
				}
			}
			
			/*
			FloatingActionButton fab = findViewById( R.id.fab );
			fab.setOnClickListener( new View.OnClickListener(){
				@Override
				public void onClick(View view){
					Snackbar.make( view, "Replace with your own action", Snackbar.LENGTH_LONG )
						.setAction( "Action", null ).show();
				}
			} );
			*/
		}
		catch( Throwable e ){
			Log.e( this.getClass().getSimpleName(), e.getLocalizedMessage(), e );
		}
	}
	
	@Override
	public void onClick(View view){
		if( view==mbtnOk || view==mbtnCancel || view==mbtnApply ){
			boolean configurationOk = ( view==mbtnCancel );
			if( view==mbtnOk || view==mbtnApply ){
				// apply modifications:
				int errorField = R.string.app_name;
				try{
					// 1- Try all the settings for validity:
					errorField = R.string.cfg_autostart;
					ApplicationHere.CFG_AUTOSTART cfgServiceAutoStart = ApplicationHere.CFG_AUTOSTART.NO;
					{
						switch( mddCfgServiceAutoStart.getSelectedItemPosition() ){
							case 1:
								cfgServiceAutoStart = ApplicationHere.CFG_AUTOSTART.YES;
								break;
							case 2:
								if( HttpServer.isRunning() ){
									cfgServiceAutoStart = ApplicationHere.CFG_AUTOSTART.LAST_STATE_YES;
								}
								else{
									cfgServiceAutoStart = ApplicationHere.CFG_AUTOSTART.LAST_STATE_NO;
								}
								break;
						}
					}
					errorField = R.string.cfg_listen_port;
					int cfgListenPort = Integer.parseInt( mtxtCfgListenPort.getText().toString() );
					{
						if( cfgListenPort<1024 || cfgListenPort>65535 ){
							throw new IllegalArgumentException( getString( R.string.msg_invalid_port ) );
						}
					}
					errorField = R.string.cfg_header_server;
					boolean cfgHeaderServer = mcbCfgHeaderServer.isChecked();
					errorField = R.string.cfg_header_powered_by;
					boolean cfgHeaderPoweredBy = mcbCfgHeaderPoweredBy.isChecked();
					errorField = R.string.cfg_show_details_500;
					boolean cfgShowDetails500 = mcbCfgShowDetails500.isChecked();
					errorField = R.string.cfg_request_workers_max;
					int cfgRequestWorkersMax = Integer.parseInt( mtxtCfgRequestWorkersMax.getText().toString() );
					errorField = R.string.cfg_request_expire;
					int cfgRequestExpire_ms = Integer.parseInt( mtxtCfgRequestExpire_ms.getText().toString() );
					{
						if( cfgRequestExpire_ms<=0 ){
							throw new IllegalArgumentException( getString( R.string.msg_invalid_expire ) );
						}
					}
					//errorField = R.string.cfg_address_deny_reaction;
					//ApplicationHere.CFG_ADDRESS_DENY cfgAddressDenyReaction =
					// ApplicationHere.CFG_ADDRESS_DENY.values()[mddCfgAddressDenyReaction.getSelectedItemPosition()];
					errorField = R.string.cfg_address_whitelist;
					HashSet<Utility.InetRange> cfgAddressWhitelist;
					{
						cfgAddressWhitelist = (HashSet<Utility.InetRange>)Utility.InetRange.fillCollectionFromUserString(
							new HashSet<Utility.InetRange>(),
							mtxtCfgAddressWhitelist.getText().toString()
						);
					}
					errorField = R.string.cfg_address_blacklist;
					HashSet<Utility.InetRange> cfgAddressBlacklist;
					{
						cfgAddressBlacklist = (HashSet<Utility.InetRange>)Utility.InetRange.fillCollectionFromUserString(
							new HashSet<Utility.InetRange>(),
							mtxtCfgAddressBlacklist.getText().toString()
						);
					}
					errorField = R.string.cfg_password_use;
					ApplicationHere.CFG_PASSWORD_USE cfgPasswordUse =
						ApplicationHere.CFG_PASSWORD_USE.values()[mddCfgPasswordUse.getSelectedItemPosition()];
					errorField = R.string.cfg_password;
					String cfgPassword = mtxtCfgPassword.getText().toString();
					errorField = R.string.cfg_forbid_if_locked;
					boolean cfgForbidIfLocked = mcbCfgForbidIfLocked.isChecked();
					//errorField = R.string.cfg_allowed_during_call;
					//boolean cfgAllowedDuringCall = mcbCfgAllowedDuringCall.isChecked();
					errorField = R.string.cfg_compo_notif;
					ApplicationHere.CFG_COMPO_NOTIF cfgComposeNotif =
						ApplicationHere.CFG_COMPO_NOTIF.values()[mddCfgComposeNotif.getSelectedItemPosition()];
					errorField = R.string.cfg_compose_vibrate;
					ApplicationHere.CFG_COMPO_VIBRATE cfgComposeVibrate =
						ApplicationHere.CFG_COMPO_VIBRATE.values()[mddCfgComposeVibrate.getSelectedItemPosition()];
					errorField = R.string.cfg_compose_mode;
					ApplicationHere.CFG_COMPO_MODE cfgComposeMode =
						ApplicationHere.CFG_COMPO_MODE.values()[mddCfgComposeMode.getSelectedItemPosition()];
					errorField = R.string.cfg_compose_speakerphone;
					boolean cfgComposeSpeakerphone = mcbCfgComposeSpeakerphone.isChecked();
					errorField = R.string.cfg_numbers_allowed;
					Pattern cfgNumbersAllowed = null;
					{
						String cfgNumbersAllowedString = mtxtCfgNumbersAllowed.getText().toString();
						if( cfgNumbersAllowedString.length()!=0 ){
							cfgNumbersAllowed = Pattern.compile( cfgNumbersAllowedString );
						}
					}
					
					// 2- Apply the new settings:
					errorField = R.string.cfg_autostart;
					ApplicationHere.setCfgServiceAutoStart( cfgServiceAutoStart );
					errorField = R.string.cfg_listen_port;
					ApplicationHere.setCfgListenPort( cfgListenPort );
					errorField = R.string.cfg_header_server;
					ApplicationHere.setCfgHeaderServer( cfgHeaderServer );
					errorField = R.string.cfg_header_powered_by;
					ApplicationHere.setCfgHeaderPoweredBy( cfgHeaderPoweredBy );
					errorField = R.string.cfg_show_details_500;
					ApplicationHere.setCfgShowDetails500( cfgShowDetails500 );
					errorField = R.string.cfg_request_workers_max;
					ApplicationHere.setCfgRequestWorkersMax( cfgRequestWorkersMax );
					errorField = R.string.cfg_request_expire;
					ApplicationHere.setCfgRequestExpire_ms( cfgRequestExpire_ms );
					//errorField = R.string.cfg_address_deny_reaction;
					//ApplicationHere.setCfgAddressDenyReaction( cfgAddressDenyReaction );
					errorField = R.string.cfg_address_whitelist;
					ApplicationHere.setCfgAddressWhitelist( cfgAddressWhitelist );
					errorField = R.string.cfg_address_blacklist;
					ApplicationHere.setCfgAddressBlacklist( cfgAddressBlacklist );
					errorField = R.string.cfg_password_use;
					ApplicationHere.setCfgPasswordUse( cfgPasswordUse );
					errorField = R.string.cfg_password;
					ApplicationHere.setCfgPassword( cfgPassword );
					errorField = R.string.cfg_forbid_if_locked;
					ApplicationHere.setCfgForbidIfLocked( cfgForbidIfLocked );
					//errorField = R.string.cfg_allowed_during_call;
					//ApplicationHere.setCfgAllowedDuringCall cfgAllowedDuringCall );
					errorField = R.string.cfg_compo_notif;
					ApplicationHere.setCfgComposeNotif( cfgComposeNotif );
					errorField = R.string.cfg_compose_vibrate;
					ApplicationHere.setCfgComposeVibrate( cfgComposeVibrate );
					errorField = R.string.cfg_compose_mode;
					ApplicationHere.setCfgComposeMode( cfgComposeMode );
					errorField = R.string.cfg_compose_speakerphone;
					ApplicationHere.setCfgComposeSpeakerphone( cfgComposeSpeakerphone );
					errorField = R.string.cfg_numbers_allowed;
					ApplicationHere.setCfgNumbersAllowed( cfgNumbersAllowed );
					
					// 3- Save new settings:
					errorField = R.string.app_name;
					SharedPreferences.Editor prefEditor = ApplicationHere.getAllPreferences( this ).edit();
					errorField = R.string.cfg_autostart;
					prefEditor.putInt( ApplicationHere.KEY_ServiceAutoStart, cfgServiceAutoStart.ordinal() );
					errorField = R.string.cfg_listen_port;
					prefEditor.putInt( ApplicationHere.KEY_ListenPort, cfgListenPort );
					errorField = R.string.cfg_header_server;
					prefEditor.putBoolean( ApplicationHere.KEY_HeaderServer, cfgHeaderServer );
					errorField = R.string.cfg_header_powered_by;
					prefEditor.putBoolean( ApplicationHere.KEY_HeaderPoweredBy, cfgHeaderPoweredBy );
					errorField = R.string.cfg_show_details_500;
					prefEditor.putBoolean( ApplicationHere.KEY_ShowDetails500, cfgShowDetails500 );
					errorField = R.string.cfg_request_workers_max;
					prefEditor.putInt( ApplicationHere.KEY_RequestWorkersMax, cfgRequestWorkersMax );
					errorField = R.string.cfg_request_expire;
					prefEditor.putInt( ApplicationHere.KEY_RequestExpire_ms, cfgRequestExpire_ms );
					//errorField = R.string.cfg_address_deny_reaction;
					//prefEditor.putInt( ApplicationHere.KEY_AddressDenyReaction, cfgAddressDenyReaction.ordinal() );
					errorField = R.string.cfg_address_whitelist;
					prefEditor.putString( ApplicationHere.KEY_AddressWhitelist, mtxtCfgAddressWhitelist.getText().toString() );
					errorField = R.string.cfg_address_blacklist;
					prefEditor.putString( ApplicationHere.KEY_AddressBlacklist, mtxtCfgAddressBlacklist.getText().toString() );
					errorField = R.string.cfg_password_use;
					prefEditor.putInt( ApplicationHere.KEY_PasswordUse, cfgPasswordUse.ordinal() );
					errorField = R.string.cfg_password;
					prefEditor.putString( ApplicationHere.KEY_Password, cfgPassword );
					errorField = R.string.cfg_forbid_if_locked;
					prefEditor.putBoolean( ApplicationHere.KEY_ForbidIfLocked, cfgForbidIfLocked );
					//errorField = R.string.cfg_allowed_during_call;
					//prefEditor.putBoolean( ApplicationHere.KEY_AllowedDuringCall, cfgAllowedDuringCall );
					errorField = R.string.cfg_compo_notif;
					prefEditor.putInt( ApplicationHere.KEY_ComposeNotif, cfgComposeNotif.ordinal() );
					errorField = R.string.cfg_compose_vibrate;
					prefEditor.putInt( ApplicationHere.KEY_ComposeVibrate, cfgComposeVibrate.ordinal() );
					errorField = R.string.cfg_compose_mode;
					prefEditor.putInt( ApplicationHere.KEY_ComposeMode, cfgComposeMode.ordinal() );
					errorField = R.string.cfg_compose_speakerphone;
					prefEditor.putBoolean( ApplicationHere.KEY_ComposeSpeakerphone, cfgComposeSpeakerphone );
					errorField = R.string.cfg_numbers_allowed;
					{
						String pattern = null;
						if( cfgNumbersAllowed==null ){
							pattern = "";
						}
						else{
							pattern = cfgNumbersAllowed.pattern();
						}
						prefEditor.putString( ApplicationHere.KEY_NumbersAllowed, pattern );
					}
					errorField = R.string.app_name;
					prefEditor.apply(); // asynchronous
					
					configurationOk = true;
				}
				catch( Throwable e ){
					AlertDialog.Builder builder = new AlertDialog.Builder( this );
					builder.setTitle( errorField );
					builder.setMessage( getString( R.string.msg_check_input )+"\n"+e.getClass().getSimpleName()+"\n"+e.getLocalizedMessage() );
					builder.create().show();
				}
			}
			if( view==mbtnOk || view==mbtnCancel ){
				// leave this Activity if supposed to:
				if( configurationOk ){
					finish();
				}
			}
		}
	}
}
