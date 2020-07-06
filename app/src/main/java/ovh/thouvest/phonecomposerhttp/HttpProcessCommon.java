package ovh.thouvest.phonecomposerhttp;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONStringer;

import java.util.Map;

abstract class HttpProcessCommon{
	
	static final String MIME_HTML = "text/html";
	static final String MIME_JSON = "application/json";
	static final String MIME_PLAIN = "text/plain";
	static final String MIME_URLENCODED = "application/x-www-form-urlencoded";
	// request headers are lowercase:
	static final String HEADER_AUTHORIZATION = "authorization";
	static final String HEADER_CONTENT_LENGTH = "content-length";
	static final String HEADER_CONTENT_TYPE = "content-type";
	static final String HEADER_COOKIE = "cookie";
	static final String METHOD_GET = "GET";
	static final String METHOD_HEAD = "HEAD"; // just in case
	static final String METHOD_OPTIONS = "OPTIONS"; // for CORS compliance
	static final String METHOD_POST = "POST";
	
	static String escapeHtml(String text){
		text = text.replace( "&", "&amp;" );
		text = text.replace( "<", "&lt;" );
		text = text.replace( ">", "&gt;" );
		return text;
	}
	
	static String escapeJs(String text){
		text = asJson( text );
		text = text.substring( 1, text.length()-1 );
		text = text.replace( "'", "\\'" ); // JS also uses single quotes
		return text;
	}
	
	static String asJson(Object data){
		// data: JSONObject, JSONArray, String, Boolean, Integer, Long, Double or null
		
		try{
			JSONStringer jss = new JSONStringer();
			jss.array(); // top-level required
			{
				jss.value( data );
			}
			jss.endArray();
			String jsonCode = jss.toString();
			jsonCode = jsonCode.substring( 1, jsonCode.length()-1 ); // clear top-level junk
			return jsonCode;
		}
		catch( JSONException e ){
			Log.w( HttpProcessCommon.class.getSimpleName(), "asJson(): "+e.getLocalizedMessage(), e );
			return "'{JSONException "+e.getLocalizedMessage()+"}'";
		}
	}
	
	static class HTTP extends RuntimeException{
		// Thrown to break the execution with the given status
		
		protected short mStatus;
		protected String mLocalizedMessage;
		protected Map<String, Object> mExtraInfo = null;
		
		HTTP(short status, String message){
			super( message );
			mStatus = status;
			mLocalizedMessage = message; // TODO
		}
		
		HTTP(short status, String message, Map<String, Object> extraInfo){
			this( status, message );
			mExtraInfo = extraInfo;
		}
		
		short getStatus(){
			return mStatus;
		}
		
		Map<String, Object> getExtraInfo(){
			return mExtraInfo;
		}
		
		@Override
		public String getLocalizedMessage(){
			return mLocalizedMessage;
		}
	}
}
