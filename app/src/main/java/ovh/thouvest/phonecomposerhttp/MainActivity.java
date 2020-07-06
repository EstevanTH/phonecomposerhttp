package ovh.thouvest.phonecomposerhttp;

import android.content.Intent;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;

public class MainActivity extends AppCompatActivity{
	
	protected static boolean sHasRunOnce = false;
	
	protected CountDownTimer mTimerRefresh = new CountDownTimer( Long.MAX_VALUE, 1500 ){
		@Override
		public void onTick(long l){
			final TextView lblStatus = findViewById( R.id.lblStatus );
			final TextView lblBoundAddr = findViewById( R.id.lblBoundAddr );
			final TextView lblIpWhitelist = findViewById( R.id.lblIpWhitelist );
			final TextView lblIpBlacklist = findViewById( R.id.lblIpBlacklist );
			
			lblStatus.setText( HttpServer.isRunning()?
				R.string.info_status_started : R.string.info_status_stopped );
			
			ArrayList<String> addressStrings;
			try{
				addressStrings = new ArrayList<>();
				Enumeration<NetworkInterface> interfaces;
				for( interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements(); ){
					NetworkInterface iface = interfaces.nextElement();
					Enumeration<InetAddress> addresses = iface.getInetAddresses();
					if( addresses.hasMoreElements() ){ // at least 1 address
						addressStrings.add( "― "+iface.getDisplayName()+" ―" );
						for( ; addresses.hasMoreElements(); ){
							InetAddress address = addresses.nextElement();
							// copy to avoid '%' interface suffixes
							address = InetAddress.getByAddress( address.getAddress() );
							addressStrings.add( address.getHostAddress() );
						}
					}
				}
				lblBoundAddr.setText( Utility.join( "\n", addressStrings ) );
			}
			catch( Throwable e ){
				lblBoundAddr.setText( e.getLocalizedMessage() );
			}
			
			HashSet<Utility.InetRange> addresses = ApplicationHere.getCfgAddressWhitelist();
			if( addresses==null ){
				lblIpWhitelist.setText( R.string.value_none );
			}
			else{
				addressStrings = new ArrayList<>( addresses.size() );
				for( Utility.InetRange address: addresses ){
					addressStrings.add( address.serialize() );
				}
				lblIpWhitelist.setText( Utility.join( "\n", addressStrings ) );
			}
			
			addresses = ApplicationHere.getCfgAddressBlacklist();
			if( addresses==null ){
				lblIpBlacklist.setText( R.string.value_none );
			}
			else{
				addressStrings = new ArrayList<>( addresses.size() );
				for( Utility.InetRange address: addresses ){
					addressStrings.add( address.serialize() );
				}
				lblIpBlacklist.setText( Utility.join( "\n", addressStrings ) );
			}
		}
		
		@Override
		public void onFinish(){
			// never finishes
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate( savedInstanceState );
		setContentView( R.layout.activity_main );
		final Button btnSvcStop = findViewById( R.id.btnSvcStop );
		final Button btnSvcStart = findViewById( R.id.btnSvcStart );
		final TextView lblBoundPort = findViewById( R.id.lblBoundPort );
		final Button btnOpenGuide = findViewById( R.id.btnOpenGuide );
		final Button btnEditConfig = findViewById( R.id.btnEditConfig );
		
		btnSvcStop.setOnClickListener( new View.OnClickListener(){
			@Override
			public void onClick(View view){
				HttpServerManager.stopHttpService( true );
			}
		} );
		
		btnSvcStart.setOnClickListener( new View.OnClickListener(){
			@Override
			public void onClick(View view){
				HttpServerManager.startHttpService( true );
			}
		} );
		
		btnOpenGuide.setOnClickListener( new View.OnClickListener(){
			@Override
			public void onClick(View view){
				try{
					MainActivity.this.startActivity( Intent.parseUri(
						MainActivity.this.getString( R.string.btn_open_guide_url ),
						0
					) );
				}
				catch( Throwable e ){
					Log.e( MainActivity.this.getClass().getSimpleName(), e.getLocalizedMessage(), e );
				}
			}
		} );
		
		btnEditConfig.setOnClickListener( new View.OnClickListener(){
			@Override
			public void onClick(View view){
				MainActivity.this.startActivity( new Intent( MainActivity.this, ActivitySettings.class ) );
			}
		} );
		
		lblBoundPort.setText( Integer.toString( ApplicationHere.getCfgListenPort() ) );
		
		mTimerRefresh.start();
		
		try{
			// start the service if it should auto-start
			if( !sHasRunOnce && HttpServerManager.shoudAutoStart() ){
				HttpServerManager.startHttpService( false );
			}
		}
		catch( Throwable e ){
			Toast.makeText( this, e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
			Log.e( this.getClass().getSimpleName(), e.getLocalizedMessage(), e );
		}
		
		sHasRunOnce = true;
	}
	
	@Override
	protected void onDestroy(){
		super.onDestroy();
		mTimerRefresh.cancel();
	}
}
