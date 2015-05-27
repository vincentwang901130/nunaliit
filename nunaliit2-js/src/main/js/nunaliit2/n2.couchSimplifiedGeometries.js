/*
Copyright (c) 2015, Geomatics and Cartographic Research Centre, Carleton 
University
All rights reserved.

Redistribution and use in source and binary forms, with or without 
modification, are permitted provided that the following conditions are met:

 - Redistributions of source code must retain the above copyright notice, 
   this list of conditions and the following disclaimer.
 - Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.
 - Neither the name of the Geomatics and Cartographic Research Centre, 
   Carleton University nor the names of its contributors may be used to 
   endorse or promote products derived from this software without specific 
   prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE 
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
POSSIBILITY OF SUCH DAMAGE.

*/
;(function($,$n2){

// Localization
var _loc = function(str,args){ return $n2.loc(str,'nunaliit2-couch',args); };

var DH = 'n2.couchSimplifiedGeometry';
	
//*******************************************************
var SimplifiedGeometryService = $n2.Class({

	dispatchService: null,
	
	atlasDb: null,

	initialize: function(opts_){
		var opts = $n2.extend({
			dispatchService: null
			,atlasDb: null
		},opts_);
		
		var _this = this;
	
		this.dispatchService = opts.dispatchService;
		this.atlasDb = opts.atlasDb;
		
		if( this.dispatchService ){
			var f = function(m, address, dispatchService){
				_this._handleDispatch(m, address, dispatchService);
			};
			
			this.dispatchService.register(DH, 'simplifiedGeometryRequest', f);
		};
	},
	
	_handleDispatch: function(m, address, dispatchService){
		if( 'simplifiedGeometryRequest' === m.type ){
			this._handleRequest(m.geometriesRequested);
		};
	},
	
	_handleRequest: function(geometriesRequested){
		var _this = this;
		
		for(var i=0,e=geometriesRequested.length; i<e; ++i){
			var geometryRequest = geometriesRequested[i];
			
			var id = geometryRequest.id;
			var doc = geometryRequest.doc;
			var attName = geometryRequest.attName;
			
			var url = this.atlasDb.getAttachmentUrl(doc,attName);
			
			$.ajax({
		    	url: url
		    	,type: 'get'
		    	,async: true
		    	,dataType: 'text'
		    	,success: function(wkt) {
		    		if( wkt ) {
		    			
		    			var simplifiedGeometry = {
		    				id: id
		    				,attName: attName
		    				,wkt: wkt
		    			};
		    			
		    			_this.dispatchService.send(DH,{
		    				type: 'simplifiedGeometryReport'
		    				,simplifiedGeometries: [ simplifiedGeometry ]
		    			});
		    		};
		    	}
		    	,error: function(XMLHttpRequest, textStatus, errorThrown) {
					$n2.log('Unable to get simplified geometry: '+geometryRequest.id+' '+attName);
		    	}
			});
		};
	}
});

//*******************************************************
$n2.couchSimplifiedGeometries = {
	Service: SimplifiedGeometryService	
};

})(jQuery,nunaliit2);