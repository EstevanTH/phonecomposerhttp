package ovh.thouvest.phonecomposerhttp;

import android.os.Build;
import android.os.NetworkOnMainThreadException;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Utility{
	private Utility(){}
	
	static class InetRange{
		// The object must be unmodifiable after creation.
		// Constructors do not throw exceptions because the invalid state is allowed.
		
		static final String INVALID_TEXT = "<invalid>";
		static final String REGEX_PIECE_IPV4_ADDR = "[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}";
		static final String REGEX_PIECE_IPV6_ADDR = "[0-9a-f:]{2,39}";
		static final Pattern REGEX_ADDRESS_RANGE = Pattern.compile( "^(?:("+REGEX_PIECE_IPV4_ADDR+")-("+REGEX_PIECE_IPV4_ADDR+")|("+REGEX_PIECE_IPV6_ADDR+")-("+REGEX_PIECE_IPV6_ADDR+"))$" );
		static final Pattern REGEX_ADDRESS_SUBNET = Pattern.compile( "^(?:("+REGEX_PIECE_IPV4_ADDR+")/([0-9]{1,2})|("+REGEX_PIECE_IPV6_ADDR+")/([0-9]{1,3}))$" );
		static final Pattern REGEX_ADDRESS_IP_ADDR = Pattern.compile( "^(?:"+REGEX_PIECE_IPV4_ADDR+"|"+REGEX_PIECE_IPV6_ADDR+")$" );
		
		protected InetAddress mRangeMin = null; // null if invalid
		protected InetAddress mRangeMax = null; // null if single address
		protected short mPrefixLength = -1; // -1 if unused
		protected int mHash;
		
		public InetRange(InetAddress rangeMin, InetAddress rangeMax){
			mRangeMin = rangeMin;
			mRangeMax = rangeMax;
			mHash = serialize().hashCode();
		}
		
		public InetRange(String addressOrRangeOrSubnet){
			try{
				boolean hashFromSerialized = true;
				
				boolean keepAnalyzing = true;
				{
					if( INVALID_TEXT.equals( addressOrRangeOrSubnet ) ){
						keepAnalyzing = false;
					}
				}
				if( keepAnalyzing ){
					Matcher matcher = REGEX_ADDRESS_RANGE.matcher( addressOrRangeOrSubnet );
					if( matcher.find() ){
						// range: "10.0.0.1-10.0.0.254"
						keepAnalyzing = false;
						String rangeMin = matcher.group( 1 );
						String rangeMax;
						if( rangeMin==null ){
							// IPv6
							rangeMin = matcher.group( 3 );
							rangeMax = matcher.group( 4 );
						}
						else{
							// IPv4
							rangeMax = matcher.group( 2 );
						}
						mRangeMin = InetAddress.getByName( rangeMin );
						mRangeMax = InetAddress.getByName( rangeMax );
					}
				}
				if( keepAnalyzing ){
					Matcher matcher = REGEX_ADDRESS_SUBNET.matcher( addressOrRangeOrSubnet );
					if( matcher.find() ){
						// subnet: "10.0.0.0/24"
						keepAnalyzing = false;
						String networkPrefix = matcher.group( 1 );
						String prefixLength;
						if( networkPrefix==null ){
							// IPv6
							networkPrefix = matcher.group( 3 );
							prefixLength = matcher.group( 4 );
						}
						else{
							// IPv4
							prefixLength = matcher.group( 2 );
						}
						mPrefixLength = Short.parseShort( prefixLength );
						byte[] rangeMinRaw = InetAddress.getByName( networkPrefix ).getAddress();
						byte[] rangeMaxRaw = rangeMinRaw.clone();
						for( int bitId = 0, bitIdEnd = rangeMinRaw.length<<3; bitId<bitIdEnd; ){
							// configure the beginning and the end of the IP address range:
							int byteId = bitId >> 3;
							short byteMin = ubyteToShort( rangeMinRaw[byteId] );
							short byteMax = ubyteToShort( rangeMaxRaw[byteId] );
							for( int bitIdEnd2 = bitId+8; bitId<bitIdEnd2; ++bitId ){
								int bitIdOfByte = bitId&0b111;
								if( bitId >= mPrefixLength ){
									byteMin &= ~( 1<<bitIdOfByte ); // bit to 0 (change only if badly written subnet)
									byteMax |= 1<<bitIdOfByte; // bit to 1
								}
								rangeMinRaw[byteId] = (byte)byteMin;
								rangeMaxRaw[byteId] = (byte)byteMax;
							}
						}
						mRangeMin = InetAddress.getByAddress( rangeMinRaw ); // subnet
						mRangeMax = InetAddress.getByAddress( rangeMaxRaw ); // broadcast
						if( mRangeMax.equals( mRangeMin ) ){
							// This is a single address.
							mRangeMax = null;
							mPrefixLength = -1;
						}
					}
				}
				if( keepAnalyzing ){
					Matcher matcher = REGEX_ADDRESS_IP_ADDR.matcher( addressOrRangeOrSubnet );
					if( matcher.find() ){
						// single IP address: "10.0.0.1"
						keepAnalyzing = false;
						String address = matcher.group( 0 );
						mRangeMin = InetAddress.getByName( address );
					}
				}
				if( keepAnalyzing ){
					{
						// single hostname: "kitchen.domain.tld" or "kitchen"
						keepAnalyzing = false;
						final String address = addressOrRangeOrSubnet;
						hashFromSerialized = false;
						mHash = address.hashCode();
						Thread resolverThread = new Thread(){
							@Override
							public void run(){
								try{
									mRangeMin = InetAddress.getByName( address );
								}
								catch( UnknownHostException e ){
									Log.w( InetRange.this.getClass().getSimpleName(), "\""+address+"\": "+e.getLocalizedMessage(), e );
								}
							}
						};
						try{ // in current thread
							resolverThread.run();
						}
						catch( NetworkOnMainThreadException e ){ // in new thread
							resolverThread.setDaemon( true );
							resolverThread.start();
						}
					}
				}
				if( hashFromSerialized ){
					mHash = serialize().hashCode();
				}
			}
			catch( UnknownHostException e ){
				mRangeMin = null;
				mRangeMax = null;
				mPrefixLength = -1;
				mHash = addressOrRangeOrSubnet.hashCode();
				Log.w( this.getClass().getSimpleName(), "\""+addressOrRangeOrSubnet+"\": "+e.getLocalizedMessage(), e );
			}
		}
		
		static Collection<InetRange> fillCollectionFromUserString(Collection<InetRange> container, String input){
			// Fills a Collection of InetRanges from a user input String, or set it to null if empty
			if( input.length()==0 ){
				container = null;
			}
			else{
				String[] inetStrings = input.split( "\\n" );
				for( int i = 0, iEnd = inetStrings.length; i<iEnd; ++i ){
					container.add( new InetRange( inetStrings[i].trim() ) );
				}
			}
			return container;
		}
		
		@Override
		public boolean equals(Object obj){
			boolean isEqual = false;
			if( mRangeMin!=null ){
				if( obj instanceof InetRange ){
					InetRange other = (InetRange)obj;
					isEqual = (
						mRangeMin.equals( other.mRangeMin ) &&
							mPrefixLength==other.mPrefixLength
					);
					if( isEqual ){
						if( mRangeMax==null ){
							isEqual = ( other.mRangeMax==null );
						}
						else{
							isEqual = mRangeMax.equals( other.mRangeMax );
						}
					}
				}
			}
			return isEqual;
		}
		
		static boolean isLowerEq(byte[] address1, byte[] address2){
			// IPv4 & IPv6 addresses must not be mixed!
			boolean result = true;
			for( int b = 0, bEnd = address1.length; b<bEnd; ++b ){
				short byte1 = ubyteToShort( address1[b] );
				short byte2 = ubyteToShort( address2[b] );
				if( byte1>byte2 ){
					// the 1st non-equal byte is greater
					result = false;
					break;
				}
				else if( byte1<byte2 ){
					// the 1st non-equal byte is lower
					break;
				}
			}
			return result;
		}
		
		static boolean isGreaterEq(byte[] address1, byte[] address2){
			// IPv4 & IPv6 addresses must not be mixed!
			boolean result = true;
			for( int b = 0, bEnd = address1.length; b<bEnd; ++b ){
				short byte1 = ubyteToShort( address1[b] );
				short byte2 = ubyteToShort( address2[b] );
				if( byte1<byte2 ){
					// the 1st non-equal byte is lower
					result = false;
					break;
				}
				else if( byte1>byte2 ){
					// the 1st non-equal byte is greater
					break;
				}
			}
			return result;
		}
		
		boolean isIncluded(InetAddress address){
			boolean included = false;
			if( mRangeMin!=null ){
				if( address.getClass()==mRangeMin.getClass() ){
					if( address.equals( mRangeMin ) ){
						// equals to single address or 1st in range
						included = true;
					}
					else if( mRangeMax!=null ){
						// may be in the range
						byte[] addressRaw = address.getAddress();
						included = (
							isGreaterEq( addressRaw, mRangeMin.getAddress() ) &&
								isLowerEq( addressRaw, mRangeMax.getAddress() )
						);
					}
				}
			}
			return included;
		}
		
		boolean isValid(){
			return ( mRangeMin!=null );
		}
		
		String serialize(){
			String serialized;
			if( mRangeMin==null ){
				serialized = INVALID_TEXT;
			}
			else if( mRangeMax==null ){
				// single address
				serialized = mRangeMin.getHostAddress();
			}
			else if( mPrefixLength<0 ){
				// address range
				serialized = mRangeMin.getHostAddress()+"-"+mRangeMax.getHostAddress();
			}
			else{
				// subnet
				serialized = mRangeMin.getHostAddress()+"/"+Short.toString( mPrefixLength );
			}
			return serialized;
		}
		
		@Override
		public String toString(){
			return this.getClass().getSimpleName()+"("+this.serialize()+")";
		}
		
		@Override
		public int hashCode(){
			return mHash;
		}
	}
	
	static final Charset CHARSET_UTF8 = Charset.forName( "UTF-8" );
	
	static CharSequence join(CharSequence delimiter, Iterable<? extends CharSequence> elements){
		if( Build.VERSION.SDK_INT >= 26 ){
			return String.join( delimiter, elements );
		}
		else{
			int allCharactersLength = 0;
			{
				Iterator<? extends CharSequence> elementsIter = elements.iterator();
				for( int i = 0; elementsIter.hasNext(); ++i ){
					if( i>0 ){
						allCharactersLength += delimiter.length();
					}
					{
						allCharactersLength += elementsIter.next().length();
					}
				}
			}
			char[] allCharacters = new char[allCharactersLength];
			{
				Iterator<? extends CharSequence> elementsIter = elements.iterator();
				for( int i = 0, k = 0; elementsIter.hasNext(); ++i ){
					if( i>0 ){
						for( int j = 0, jEnd = delimiter.length(); j<jEnd; ++j ){
							allCharacters[k++] = delimiter.charAt( j );
						}
					}
					{
						CharSequence piece = elementsIter.next();
						for( int j = 0, jEnd = piece.length(); j<jEnd; ++j ){
							allCharacters[k++] = piece.charAt( j );
						}
					}
				}
			}
			return String.valueOf( allCharacters );
		}
	}
	
	// For all ArrayList<non-elementary> -> elementary[]:
	static byte[] collectionToArray_By(Collection<Byte> collection){
		byte[] array = new byte[collection.size()];
		int index = 0;
		for( byte element: collection ){
			array[index] = element;
			++index;
		}
		return array;
	}
	
	static short[] collectionToArray_Sh(Collection<Short> collection){
		short[] array = new short[collection.size()];
		int index = 0;
		for( short element: collection ){
			array[index] = element;
			++index;
		}
		return array;
	}
	
	static int[] collectionToArray_In(Collection<Integer> collection){
		int[] array = new int[collection.size()];
		int index = 0;
		for( int element: collection ){
			array[index] = element;
			++index;
		}
		return array;
	}
	
	static long[] collectionToArray_Lo(Collection<Long> collection){
		long[] array = new long[collection.size()];
		int index = 0;
		for( long element: collection ){
			array[index] = element;
			++index;
		}
		return array;
	}
	
	static float[] collectionToArray_Fl(Collection<Float> collection){
		float[] array = new float[collection.size()];
		int index = 0;
		for( float element: collection ){
			array[index] = element;
			++index;
		}
		return array;
	}
	
	static double[] collectionToArray_Do(Collection<Double> collection){
		double[] array = new double[collection.size()];
		int index = 0;
		for( double element: collection ){
			array[index] = element;
			++index;
		}
		return array;
	}
	
	static boolean[] collectionToArray_Bo(Collection<Boolean> collection){
		boolean[] array = new boolean[collection.size()];
		int index = 0;
		for( boolean element: collection ){
			array[index] = element;
			++index;
		}
		return array;
	}
	
	static char[] collectionToArray_Ch(Collection<Character> collection){
		char[] array = new char[collection.size()];
		int index = 0;
		for( char element: collection ){
			array[index] = element;
			++index;
		}
		return array;
	}
	
	static short ubyteToShort(byte input){
		// Convert to short, considering input as unsigned
		short result = input;
		if( result<0 ){
			result += 256;
		}
		return result;
	}
	
	
	/// GUI ///
	
	static void guiFillDropdownStringArray(Spinner spinner, String[] choiceStrings){
		ArrayAdapter<String> adapter = new ArrayAdapter<>(
			spinner.getContext(),
			android.R.layout.simple_spinner_item,
			choiceStrings
		);
		spinner.setAdapter( adapter );
	}
	
	static void guiFillDropdownEnum(Spinner spinner, Class<? extends Enum> choicesEnum){
		Enum[] choiceEnums = choicesEnum.getEnumConstants();
		String[] choiceStrings = new String[choiceEnums.length];
		for( int i = 0, iEnd = choiceEnums.length; i<iEnd; ++i ){
			choiceStrings[i] = choiceEnums[i].name();
		}
		guiFillDropdownStringArray(spinner, choiceStrings);
	}
	
	static void guiTextViewSetNumber(TextView textView, int value){
		textView.setText( Integer.toString( value ) );
	}
	
	static void guiTextViewSetText(TextView textView, CharSequence value){
		if( value==null ){
			textView.setText( "" );
		}
		else{
			textView.setText( value );
		}
	}
	
	static void guiSpinnerSetEnum(Spinner spinner, Enum value){
		spinner.setSelection( value.ordinal(), false );
	}
	
}
