/*
Copyright (c) 2012, Geomatics and Cartographic Research Centre, Carleton 
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

$Id: n2.couchRelatedDoc.js 8484 2012-09-05 19:38:37Z jpfiset $
*/
;(function($,$n2){

// Localization
var _loc = function(str,args){ return $n2.loc(str,'nunaliit2-couch',args); };

var CreateRelatedDocProcess = $n2.Class({
	
	options: null
	
	,initialize: function(options_) {
		this.options = $n2.extend(
			{
				documentSource: null
				,schemaRepository: null
				,uploadService: null
				,showService: null
				,authService: null
			}
			,options_
		);
	}

	,addRelatedDocumentFromSchemaNames: function(opt_){
		
		var _this = this;
		
		// Check that we are logged in
		var authService = this.options.authService;
		if( authService && false == authService.isLoggedIn() ) {
			authService.showLoginForm({
				onSuccess: function(result,options) {
					_this.addRelatedDocumentFromSchemaNames(opt_);
				}
			});
			return;
		};
		
		var opt = $n2.extend({
			docId: null
			,relatedSchemaNames: []
			,onSuccess: function(docId){}
			,onError: $n2.reportErrorForced
			,onCancel: function(){}
		},opt_);
		
		this.selectSchemaDialog({
			schemaNames: opt.relatedSchemaNames
			,onSuccess: selectedSchemaName
			,onError: opt.onError
			,onCancel: opt.onCancel
		});
		
		function selectedSchemaName(schemaName){
			_this.options.schemaRepository.getSchema({
				name: schemaName
				,onSuccess: selectedSchema
				,onError: function(err){
					opt.onError( _loc('Unable to fetch schema') );
				}
			});
		};
		
		function selectedSchema(schema){
			_this.addRelatedDocumentFromSchema({
				docId: opt.docId
				,schema: schema
				,onSuccess: opt.onSuccess
				,onError: opt.onError
				,onCancel: opt.onCancel
			});
		};
	}

	,replyToDocument: function(opt_){
		
		var _this = this;
		
		// Check that we are logged in
		var authService = this.options.authService;
		if( authService && false == authService.isLoggedIn() ) {
			authService.showLoginForm({
				onSuccess: function(result,options) {
					_this.replyToDocument(opt_);
				}
			});
			return;
		};
		
		var opt = $n2.extend({
			doc: null
			,schema: null
			,origin: null
			,onSuccess: function(docId){}
			,onError: $n2.reportErrorForced
			,onCancel: function(){}
		},opt_);

		if( opt.schema && opt.schema.isSchema ) {
			// OK
		} else {
			opt.onError( _loc('A valid schema must be provided') );
			return;
		};
		
		var origin = null;
		if( opt.origin ){
			origin = opt.origin;
		} else if( opt.doc.nunaliit_origin && opt.doc.nunaliit_origin.doc ) {
			origin = opt.doc.nunaliit_origin.doc;
		} else if( opt.doc.nunaliit_source && opt.doc.nunaliit_source.doc ) {
			origin = opt.doc.nunaliit_source.doc;
		};
		
		this.addRelatedDocumentFromSchema({
			docId: opt.doc._id
			,schema: opt.schema
			,origin: origin
			,onSuccess: opt.onSuccess
			,onError: opt.onError
			,onCancel: opt.onCancel
		});
	}

	,addRelatedDocumentFromSchema: function(opt_){

		var _this = this;

		// Check that we are logged in
		var authService = this.options.authService;
		if( authService && false == authService.isLoggedIn() ) {
			authService.showLoginForm({
				onSuccess: function(result,options) {
					_this.addRelatedDocumentFromSchema(opt_);
				}
			});
			return;
		};

		var opt = $n2.extend({
			docId: null
			,schema: null
			,origin: null
			,onSuccess: function(docId){}
			,onError: $n2.reportErrorForced
			,onCancel: function(){}
		},opt_);
		
		if( opt.schema && opt.schema.isSchema ) {
			// OK
		} else {
			opt.onError( _loc('A valid schema must be provided') );
			return;
		};
		
		// Check that upload service is available
		this.options.uploadService.checkWelcome({
			onSuccess: uploadServiceAvailable
			,onError: function(err){
				alert( _loc('Upload service can not be reached. Unable to submit a related document.') );
			}
		});

		function uploadServiceAvailable(){
			var obj = opt.schema.createObject();
			obj.nunaliit_source = {
				nunaliit_type: 'reference'
				,doc: opt.docId
				,category: 'attachment'
			};
			
			if( opt.origin ){
				obj.nunaliit_origin = {
					nunaliit_type: 'reference'
					,doc: opt.origin
				};
			};
			
			new Editor({
				documentSource: _this.options.documentSource
				,uploadService: _this.options.uploadService
				,showService: _this.options.showService
				,obj: obj
				,schema: opt.schema 
				,onSuccess: opt.onSuccess
				,onError: opt.onError
				,onCancel: opt.onCancel
			});
		};
	}
	
	,selectSchemaDialog: function(opt_){
		var opt = $n2.extend({
			schemaNames: []
			,onSuccess: function(schemaName){}
			,onError: $n2.reportErrorForced
			,onCancel: function(){}
		},opt_);
		
		if( !opt.schemaNames.length ) {
			opt.onCancel();
			return;
		}

		if( opt.schemaNames.length == 1 ) {
			opt.onSuccess( opt.schemaNames[0] );
			return;
		}
		
		var diagId = $n2.getUniqueId();
		var $dialog = $('<div id="'+diagId+'"></div>');

		var $label = $('<span></span>');
		$label.text( _loc('Select schema') + ': ' );
		$dialog.append($label);
		
		var $select = $('<select></select>');
		$dialog.append($select);
		for(var i=0,e=opt.schemaNames.length; i<e; ++i){
			var schemaName = opt.schemaNames[i];
			var $option = $('<option></option>');
			$option.text(schemaName);
			$select.append($option);
		};

		$dialog.append( $('<br/>') );
		
		var $ok = $('<button></button>');
		$ok.text( _loc('OK') );
		$ok.button({icons:{primary:'ui-icon-check'}});
		$dialog.append( $ok );
		$ok.click(function(){
			var $diag = $('#'+diagId);
			var schemaName = $diag.find('select').val();
			$diag.dialog('close');
			opt.onSuccess(schemaName);
			return false;
		});
		
		var $cancel = $('<button></button>');
		$cancel.text( _loc('Cancel') );
		$cancel.button({icons:{primary:'ui-icon-cancel'}});
		$dialog.append( $cancel );
		$cancel.click(function(){
			$('#'+diagId).dialog('close');
			opt.onCancel();
			return false;
		});
		
		var dialogOptions = {
			autoOpen: true
			,title: _loc('Select a schema')
			,modal: true
			,width: 740
			,close: function(event, ui){
				var diag = $(event.target);
				diag.dialog('destroy');
				diag.remove();
			}
		};
		$dialog.dialog(dialogOptions);
	}
});

var Editor = $n2.Class({
	
	options: null
	
	,diagId: null
	
	,attachmentUploadHandler: null
	
	,initialize: function(options_) {
		this.options = $n2.extend(
			{
				documentSource: null
				,uploadService: null
				,showService: null
				,obj: null
				,schema: null
				,onSuccess: function(docId){}
				,onError: $n2.reportErrorForced
				,onCancel: function(){}
			}
			,options_
		);

		var _this = this;
		
		var diagId = $n2.getUniqueId();
		this.diagId = diagId;
		var $dialog = $('<div id="'+diagId+'"></div>');
		
		var obj = this.options.obj;
		var schema = this.options.schema;
		
		this.attachmentUploadHandler = new $n2.CouchEditor.AttachmentUploadHandler({
			doc: obj
			,schema: schema
			,uploadService: this.options.uploadService
		});
		
		var $form = $('<div></div>');
		$dialog.append($form);
		schema.form(obj, $form);
		
		if( this.options.showService ){
			this.options.showService.fixElementAndChildren($form, {}, obj);
		};

		var $fileElement = $('<div></div>');
		$dialog.append($fileElement);
		this.attachmentUploadHandler.printFileForm({
			elem: $fileElement
			,doc: obj
		});
		
		var $ok = $('<button></button>');
		$ok.text( _loc('OK') );
		$ok.button({icons:{primary:'ui-icon-check'}});
		$dialog.append( $ok );
		$ok.click(function(){
			_this._clickOK();
			return false;
		});
		
		var $cancel = $('<button></button>');
		$cancel.text( _loc('Cancel') );
		$cancel.button({icons:{primary:'ui-icon-cancel'}});
		$dialog.append( $cancel );
		$cancel.click(function(){
			_this._clickCancel();
			return false;
		});
		
		var dialogOptions = {
			autoOpen: true
			,title: _loc('Fill Out Related Document')
			,modal: true
			,width: 740
			,close: function(event, ui){
				var diag = $('#'+diagId);
				diag.remove();
			}
		};
		$dialog.dialog(dialogOptions);
	}

	,_clickOK: function(){

		var _this = this;
		var obj = this.options.obj;

		// Check that a file was provided
		this.attachmentUploadHandler.performPreSavingActions({
			doc: obj
			,onSuccess: function(doc){
				_this.options.obj = doc;
				_this._saveObj();
			}
			,onError: function(err){
				alert(err);
			}
		});
	}

	,_clickCancel: function(){
		$('#'+this.diagId).dialog('close');
		this.options.onCancel();
	}
	
	,_saveObj: function(){
		var _this = this;
		var obj = this.options.obj;

		$n2.couchMap.adjustDocument(obj);

		this.options.documentSource.createDocument({
			doc: obj
			,onSuccess: function(updatedDoc) {
				_this._uploadFile(updatedDoc);
			}
			,onError: function(err){
				_this.options.onError( _loc('Unable to reach database to submit document: {err}',{err:err}) );
			}
		});
	}
	
	,_uploadFile: function(doc){
		var _this = this;

		this.attachmentUploadHandler.performPostSavingActions({
			doc: doc
			,onSuccess: function(doc){
				_this._success(doc._id);
			}
			,onError: function(err){
				_this.options.onError( 
					_loc('Error occurred after related document was created. Error: {err}',{err:err})
				);
			}
		});
	}
	
	,_success: function(docId){
		// Close upload dialog
		$('#'+this.diagId).dialog('close');
		
		// Call back client
		this.options.onSuccess(docId);
	}
});

$n2.couchRelatedDoc = {
	CreateRelatedDocProcess: CreateRelatedDocProcess	
};

})(jQuery,nunaliit2);
