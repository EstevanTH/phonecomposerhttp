package ovh.thouvest.phonecomposerhttp;

import android.os.Build;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

final class HttpResponseMaker extends HttpProcessCommon{
	private HttpResponseMaker(){}

    /*
    Make the byte[] to pass to the output stream
    By default the output just contains the status and the content in a brutal way
    customPresentation: display a nice page instead of error-like messages
    
    Note: Nothing should be passed to the stream until the response is fully ready.
     */
	
	static String statusDescription(short status){
		switch( status ){
			case 200:
				return "OK";
			case 204:
				return "No Content";
			case 400:
				return "Bad Request";
			case 401:
				return "Unauthorized";
			case 403:
				return "Forbidden";
			case 404:
				return "Not Found";
			case 405:
				return "Method Not Allowed";
			case 408:
				return "Request Timeout";
			case 411:
				return "Length Required";
			case 413:
				return "Payload Too Large";
			case 414:
				return "URI Too Long";
			case 431:
				return "Request Header Fields Too Large";
			case 500:
				return "Internal Server Error";
			case 503:
				return "Service Unavailable";
			default:
				return "Unknown status";
		}
	}
	
	static void make(OutputStream out, HttpRequest request, Short status, String mimeType, String content, boolean customPresentation, Map<String, Object> extraInfo) throws IOException{
		String resultDescription = statusDescription( status );
		
		String requestMethod = METHOD_GET;
		if( request!=null ){
			requestMethod = request.getMethod();
		}
		
		if( requestMethod.equals( METHOD_OPTIONS ) ){
			if( status==200 ){
				status = 204;
			}
		}
		
		String payloadString;
		if( customPresentation ){
			payloadString = content;
		}
		else if( mimeType.equals( MIME_JSON ) ){
			payloadString = String.format( Locale.US, "{\"status\":%3d,\"message\":%s}", status, asJson( content ) );
		}
		else{
			if( status==500 && extraInfo!=null && extraInfo.containsKey( "stack" ) ){
				String fullStackString;
				{
					StackTraceElement[] stackObjects = (StackTraceElement[])extraInfo.get( "stack" );
					String[] stackStrings = new String[stackObjects.length];
					int stackLevelId = 0;
					for( StackTraceElement stackLevelObject: stackObjects ){
						stackStrings[stackLevelId] = stackLevelObject.toString();
						++stackLevelId;
					}
					fullStackString = (String)Utility.join( "\r\n", Arrays.asList(stackStrings) );
				}
				payloadString = String.format(
					"<html><body><h1>%s</h1><p>%s</p><pre>%s</pre></body></html>",
					resultDescription,
					escapeHtml( content ),
					escapeHtml( fullStackString )
				);
			}
			else{
				payloadString = String.format(
					"<html><body><h1>%s</h1><p>%s</p></body></html>",
					resultDescription,
					escapeHtml( content )
				);
			}
		}
		byte[] payload = null;
		if( status!=204 && !requestMethod.equals( METHOD_HEAD ) ){
			payload = payloadString.getBytes( Utility.CHARSET_UTF8 );
		}
		
		String headerContentType;
		switch( mimeType ){
			case MIME_HTML:
			case MIME_PLAIN:
				headerContentType = String.format( "Content-Type: %s; charset=UTF-8", mimeType );
				break;
			default:
				headerContentType = "Content-Type: "+mimeType;
		}
		
		ArrayList<String> headerLines = new ArrayList<>();
		headerLines.add( String.format( Locale.US, "HTTP/1.0 %3d %s", status, resultDescription ) );
		headerLines.add( "Connection: close" );
		if( ApplicationHere.isCfgHeaderServer() ){
			headerLines.add( "Server: "+ApplicationHere.getInstance().getText( R.string.app_name ) );
		}
		if( ApplicationHere.isCfgHeaderPoweredBy() ){
			headerLines.add( "X-Powered-By: Java SE (Android "+Build.VERSION.RELEASE+")" );
		}
		headerLines.add( "Access-Control-Allow-Headers: *" );
		headerLines.add( "Access-Control-Allow-Origin: *" );
		headerLines.add( "Tk: N" );
		headerLines.add( "Cache-Control: no-cache" );
		if( status==401 ){
			headerLines.add( "WWW-Authenticate: Basic realm=\"Enter the password, any username accepted:\", charset=\"UTF-8\"" );
		}
		if( status==405 || requestMethod.equals( METHOD_OPTIONS ) ){
			String methods;
			if( extraInfo!=null && extraInfo.containsKey( "Allow" ) ){
				methods = (String)extraInfo.get( "Allow" );
			}
			else{
				methods = "GET, POST";
			}
			headerLines.add( "Allow: "+methods );
			if( requestMethod.equals( METHOD_OPTIONS ) ){
				headerLines.add( "Access-Control-Allow-Methods: "+methods );
			}
		}
		headerLines.add( headerContentType );
		if( payload!=null ){
			headerLines.add( String.format( Locale.US, "Content-Length: %d", payload.length ) );
		}
		headerLines.add( "\r\n" );
		byte[] headers = ( (String)Utility.join( "\r\n", headerLines ) ).getBytes( Utility.CHARSET_UTF8 );
		
		out.write( headers );
		if( payload!=null ){
			out.write( payload );
		}
	}
	
	static void make(OutputStream out, HttpRequest request, Short status, String mimeType, String content, boolean customPresentation) throws IOException{
		make(out, request, status, mimeType, content, customPresentation, null);
	}
	
}
