package ovh.thouvest.phonecomposerhttp;

import android.util.Log;
import android.util.Base64;

import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class HttpRequestParser extends HttpProcessCommon{
	static final int MAX_LENGTH_REQUEST = 131072; // basic checks (no total check)
	static final int MAX_LENGTH_HEADER = 65536;
	static final int MAX_LENGTH_PAYLOAD = 65536;
	// TODO - test hard all these patterns
	static final Pattern regexHeaderFirst = Pattern.compile( "^([A-Za-z]+)\\s*(/[A-Z0-9a-z$\\-_.+!*'(),/?=%&#]*|\\*)\\s*HTTP/[0-9\\.]+$" ); // could be better
	static final Pattern regexHeader = Pattern.compile( "^([A-Z0-9a-z\\-]+):\\s*(.*)$" );
	static final Pattern regexHeaderValSep = Pattern.compile( ";\\s*" );
	static final Pattern regexHeaderAuthBasicPass1 = Pattern.compile( "^Basic\\s+(.*)$"/*, Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE*/ ); // as in header
	static final Pattern regexHeaderAuthBasicPass2 = Pattern.compile( "^[^:]*:(.*)$" ); // after Base64 decode
	protected InputStream mIn = null;
	//protected BufferedReader mInBuf = null;
	protected Socket mClientSock = null;
	
	HttpRequestParser(Socket clientSock, InputStream in){
		mIn = in;
		//mInBuf = new BufferedReader( new InputStreamReader( in, Utility.CHARSET_UTF8 ) );
		mClientSock = clientSock;
	}
	
	String readHeaderLine(HttpRequest request) throws Throwable{
		if( mIn.available()>MAX_LENGTH_REQUEST ){
			throw new HTTP( (short)400, "The total request length exceeds "+Integer.toString( MAX_LENGTH_REQUEST >> 10 )+" KiB." );
		}
		ArrayList<Byte> lineBytes = new ArrayList<>();
		boolean keepReading = true;
		while( keepReading ){
			if( request.isExpired() ){
				Log.d( this.getClass().getSimpleName(), "readHeaderLine(): request.isExpired() == true" );
				throw new HTTP( (short)408, "The header phase did not finish during the allowed duration." );
			}
			int newByte;
			try{
				newByte = mIn.read();
			}
			catch( SocketTimeoutException e ){
				Log.d( this.getClass().getSimpleName(), "readHeaderLine(): ["+mClientSock.toString()+"]", e );
				throw new HTTP( (short)408, "The input stream timed out while receiving headers." );
			}
			switch( newByte ){
				case 0x0A:
					// end of line
					keepReading = false;
					break;
				case 0x0D:
					// ignored carriage return
					break;
				default:
					if( newByte<0 ){
						// read error
						Log.d( this.getClass().getSimpleName(), "readHeaderLine(): newByte<0" );
					}
					else{
						// new character in line
						lineBytes.add( (byte)newByte );
					}
			}
		}
		return new String( Utility.collectionToArray_By( lineBytes ), Utility.CHARSET_UTF8 );
	}
	
	HttpRequest parse() throws Throwable{
		// For design simplicity & performance, CR characters are ignored.
		// The max request size is 64 KiB. If anything bigger is found, flood gets prevented.
		
		HttpRequest request = new HttpRequest();
		String headerLine;
		Matcher matcher;
		
		// 1- Parse the 1st header line
		headerLine = readHeaderLine( request );
		matcher = regexHeaderFirst.matcher( headerLine );
		if( matcher.find() ){
			request.setMethod( matcher.group( 1 ) );
			request.setUrl( matcher.group( 2 ) );
		}
		else{
			throw new HTTP( (short)400, "The 1st header line is invalid." );
		}
		
		// 2- Parse all following header lines
		int contentLength = -1;
		String contentType = null;
		while( ( headerLine = readHeaderLine( request ) ).length()!=0 ){
			matcher = regexHeader.matcher( headerLine );
			if( matcher.find() ){
				String headerName = matcher.group( 1 ).toLowerCase( Locale.US );
				String headerValue = matcher.group( 2 );
				switch( headerName ){
					case HEADER_AUTHORIZATION:
						try{
							matcher = regexHeaderAuthBasicPass1.matcher( headerValue );
							matcher.find();
							headerValue = matcher.group( 1 ); // base64 of "user:pass"
							headerValue = new String( Base64.decode( headerValue, Base64.DEFAULT ), Utility.CHARSET_UTF8 ); // "user:pass"
							matcher = regexHeaderAuthBasicPass2.matcher( headerValue );
							matcher.find();
							request.setPassword( matcher.group( 1 ) ); // "pass"
						}
						catch( Throwable e ){
							throw new HTTP( (short)400, "In header Authorization: "+e.getLocalizedMessage() );
						}
						break;
					case HEADER_CONTENT_LENGTH:
						try{
							contentLength = Integer.parseInt( headerValue );
						}
						catch( Throwable e ){
							throw new HTTP( (short)400, "In header Content-Length: "+e.getLocalizedMessage() );
						}
						if( contentLength<0 ){
							throw new HTTP( (short)400, "In header Content-Length: A negative value does not make sense" );
						}
						if( contentLength>MAX_LENGTH_PAYLOAD ){
							throw new HTTP( (short)413, "The POST payload must be lower than "+( MAX_LENGTH_PAYLOAD >> 10 )+" KiB" );
						}
						break;
					case HEADER_CONTENT_TYPE:
						contentType = headerValue.toLowerCase( Locale.US );
						break;
					case HEADER_COOKIE:
						break;
				}
			}
		}
		
		// 3- Do some checks:
		switch( request.getMethod() ){
			case METHOD_GET:
			case METHOD_HEAD:
			case METHOD_OPTIONS:
				break;
			case METHOD_POST:
				if( contentLength<0 ){
					throw new HTTP( (short)411, "Unable to parse a POST request without a Content-Length header" );
				}
				break;
			default:
				throw new HTTP( (short)405, "Only GET, HEAD and POST requests are supported." );
		}
		
		// 4- Read POST data:
		if( request.getMethod().equals( METHOD_POST ) ){
			byte[] payloadRaw = new byte[contentLength];
			try{
				// TODO - expiration chronométrique
				for( int remainingLength = contentLength, receivedLength, saveOffset = 0; remainingLength>0; remainingLength -= receivedLength ){
					if( request.isExpired() ){
						throw new SocketTimeoutException( "request.isExpired() == true" );
					}
					receivedLength = mIn.read( payloadRaw, saveOffset, remainingLength );
					saveOffset += receivedLength;
				}
			}
			catch( SocketTimeoutException e ){
				Log.d( this.getClass().getSimpleName(), "parse(): partial POST content ["+mClientSock.toString()+"]", e );
				throw new HTTP( (short)408, "Received partial POST content, please check the request expiration time" );
			}
			String payload = new String( payloadRaw, Utility.CHARSET_UTF8 );
			request.setDataPost( payload, contentType );
		}
		
		return request;
	}
}
