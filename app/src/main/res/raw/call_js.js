var xhr;
function docall(){
	if( xhr===undefined ){
		var callForm = document.getElementById( 'callForm' );
		var callButton = document.getElementById( 'callButton' );
		xhr = new XMLHttpRequest();
		//xhr.open( callForm.method.toUpperCase(), callForm.action, true, '', document.getElementById( 'callPassword' ).value ); // NOK Google Chrome
		xhr.open( callForm.method.toUpperCase(), callForm.action, true );
		xhr.setRequestHeader( 'Authorization', 'Basic '+btoa( ':'+document.getElementById( 'callPassword' ).value ) );
		xhr.setRequestHeader( 'Content-Type', 'application/json' );
		xhr.onreadystatechange = function(){
			if( this.readyState===4 ){
				callButton.disabled = false;
				xhr = undefined;
				var result = JSON.parse( this.responseText );
				alert( result.message );
			}
		};
		xhr.send( JSON.stringify( {
			'number': document.getElementById( 'callNumber' ).value,
		} ) );
		callButton.disabled = true;
	}
	return false;
}