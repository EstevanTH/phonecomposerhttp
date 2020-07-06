package ovh.thouvest.phonecomposerhttp;

import android.os.SystemClock;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.Serializable;
import java.net.URLDecoder;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class HttpRequest extends HttpProcessCommon{
	static final Pattern regexUrl = Pattern.compile( "^(/[^\\?#]*)(?:\\?([^#]*)|)" );
	static final Pattern regexSeparatorUrlEncoded = Pattern.compile( "&" );
	static final Pattern regexPieceUrlEncoded = Pattern.compile( "([^\\?=&#]+)(?:=([^\\?=&#]*)|)" );
	
	//protected boolean mValidHttp = true; // valid against HTTP protcol
	protected long mRequestTimeout_ms = SystemClock.elapsedRealtime()+ApplicationHere.getCfgRequestExpire_ms();
	protected String mMethod = null;
	protected String mPassword = null; // filled for clear password only
	protected String mUri = null;
	protected Hashtable<String, String> mDataGet = new Hashtable<>();
	protected Hashtable<String, Serializable> mDataPost = new Hashtable<>();
	//protected Hashtable<String, String> mDataCookies = new Hashtable<>();
	
	boolean isExpired(){
		return SystemClock.elapsedRealtime()>mRequestTimeout_ms;
	}
	
	static <TValue extends Serializable>
	void pushUrlEncodedInto(String urlEncoded, Hashtable<String, TValue> target) throws Throwable{
		// Decodes a URL-encoded string into a Hashtable
		// This method does not support duplicated keys, the last duplicated is used.
		String[] piecesUrlEncoded = regexSeparatorUrlEncoded.split( urlEncoded );
		for( String pieceUrlEncoded: piecesUrlEncoded ){
			Matcher pieceMatched = regexPieceUrlEncoded.matcher( pieceUrlEncoded );
			if( pieceMatched.find() ){
				String key = pieceMatched.group( 1 );
				if( key!=null ){
					String value = URLDecoder.decode( pieceMatched.group( 2 ), Utility.CHARSET_UTF8.name() );
					target.put( key, (TValue)value );
				}
			}
		}
	}
	
	static <TValue extends Serializable>
	void pushJsonObjectInto(String json, Hashtable<String, TValue> target) throws Throwable{
		// Decodes a JSON string into a Hashtable
		JSONTokener jsonParser = new JSONTokener( json );
		JSONObject jsonObjectRoot;
		try{
			jsonObjectRoot = (JSONObject)jsonParser.nextValue();
		}
		catch( ClassCastException e ){
			throw new ClassCastException( "The root of JSON data is required to be an object." );
		}
		for( Iterator<String> it = jsonObjectRoot.keys(); it.hasNext(); ){
			String key = it.next();
			Object valueRaw = jsonObjectRoot.get( key );
			TValue value;
			try{
				value = (TValue)valueRaw;
			}
			catch( ClassCastException e ){
				value = (TValue)String.valueOf( valueRaw );
			}
			target.put( key, value );
		}
	}
	
	void setMethod(String newMethod){
		mMethod = newMethod;
	}
	
	void setPassword(String newPassword){
		mPassword = newPassword;
	}
	
	void setUrl(String url) throws Throwable{
		Matcher parsedUrl = regexUrl.matcher( url );
		if( parsedUrl.find() ){
			mUri = parsedUrl.group( 1 );
			String dataGetString = parsedUrl.group( 2 );
			if( dataGetString!=null && dataGetString.length()!=0 ){
				pushUrlEncodedInto( dataGetString, mDataGet );
			}
		}
	}
	
	void setDataPost(String payload, String suggestedMime) throws Throwable{
		switch( suggestedMime ){
			case MIME_JSON:
			case MIME_URLENCODED:
				break;
			default:
				// MIME-type auto-detection
				switch( payload.charAt( 0 ) ){
					case '\t':
					case '\n':
					case '\r':
					case ' ':
					case '"':
					case '[':
					case ']':
					case '{':
					case '}':
						suggestedMime = MIME_JSON;
						break;
					default:
						suggestedMime = MIME_URLENCODED;
				}
		}
		switch( suggestedMime ){
			case MIME_JSON:
				try{
					pushJsonObjectInto( payload, mDataPost );
				}
				catch( ClassCastException e ){
					throw new HTTP( (short)400, e.getLocalizedMessage() );
				}
				catch( JSONException e ){
					throw new HTTP( (short)400, "Received invalid JSON data: "+e.getLocalizedMessage() );
				}
				break;
			case MIME_URLENCODED:
			default:
				pushUrlEncodedInto( payload, mDataPost );
		}
	}
	
	/*
	boolean isValidHttp(){
		return mValidHttp;
	}
	*/
	
	String getMethod(){
		return mMethod;
	}
	
	String getUri(){
		return mUri;
	}
	
	Hashtable<String, String> getDataGet(){
		return mDataGet;
	}
	String getFieldGet( String field ){
		return mDataGet.get(field);
	}
	
	Hashtable<String, Serializable> getDataPost(){
		return mDataPost;
	}
	Serializable getFieldPost( String field ){
		return mDataPost.get(field);
	}
	
	String getPassword(){
		return mPassword;
	}
}
