package ca.carleton.gcrc.couch.onUpload.multimedia;

import java.io.File;
import java.util.Properties;

import org.apache.log4j.Logger;

import ca.carleton.gcrc.couch.client.CouchUserContext;
import ca.carleton.gcrc.couch.onUpload.UploadConstants;
import ca.carleton.gcrc.couch.onUpload.UploadProgressAdaptor;
import ca.carleton.gcrc.couch.onUpload.conversion.AttachmentDescriptor;
import ca.carleton.gcrc.couch.onUpload.conversion.FileConversionContext;
import ca.carleton.gcrc.couch.onUpload.conversion.OriginalFileDescriptor;
import ca.carleton.gcrc.couch.onUpload.conversion.ServerWorkDescriptor;
import ca.carleton.gcrc.couch.onUpload.conversion.WorkDescriptor;
import ca.carleton.gcrc.couch.onUpload.plugin.FileConversionMetaData;
import ca.carleton.gcrc.couch.onUpload.plugin.FileConversionPlugin;
import ca.carleton.gcrc.couch.utils.CouchNunaliitUtils;
import ca.carleton.gcrc.olkit.multimedia.converter.MultimediaConversionRequest;
import ca.carleton.gcrc.olkit.multimedia.converter.MultimediaConverter;
import ca.carleton.gcrc.olkit.multimedia.converter.impl.MultimediaConverterImpl;
import ca.carleton.gcrc.olkit.multimedia.file.SystemFile;
import ca.carleton.gcrc.olkit.multimedia.imageMagick.ImageInfo;
import ca.carleton.gcrc.olkit.multimedia.imageMagick.ImageMagick;
import ca.carleton.gcrc.olkit.multimedia.imageMagick.ImageMagickProcessor;
import ca.carleton.gcrc.olkit.multimedia.utils.MimeUtils;
import ca.carleton.gcrc.olkit.multimedia.utils.MimeUtils.MultimediaClass;

public class MultimediaFileConverter implements FileConversionPlugin {

	final protected Logger logger = Logger.getLogger(this.getClass());

	private MultimediaConverter mmConverter = new MultimediaConverterImpl();
	private String atlasName = null;
	private boolean uploadOriginalImages = false;

	@Override
	public String getName() {
		return "Multimedia Converter";
	}
	
	public MultimediaFileConverter(){
		logSettings();
	}
	
	public MultimediaFileConverter(Properties props){
		this.parseProperties(props);
		logSettings();
	}
	
	public void parseProperties(Properties props){
		if( null != props ) {
			// multimedia.uploadOriginalImages
			{
				String uploadFlag = props.getProperty("multimedia.uploadOriginalImages", null);
				if( null != uploadFlag ) {
					boolean flag = Boolean.parseBoolean(uploadFlag);
					if( flag ) {
						this.uploadOriginalImages = true;
					}
				}
			}

			// atlas.name
			{
				String atlasName = props.getProperty("atlas.name", null);
				if( null != atlasName ) {
					this.atlasName = atlasName;
				}
			}
		}
	}
	
	public void logSettings() {
		logger.info("uploadOriginalImages: "+uploadOriginalImages);
	}

	public String getAtlasName() {
		return atlasName;
	}

	public void setAtlasName(String atlasName) {
		this.atlasName = atlasName;
	}

	@Override
	public FileConversionMetaData getFileMetaData(File file) {
		
		FileConversionMetaData result = new FileConversionMetaData();
		
		try {
			SystemFile sf = SystemFile.getSystemFile(file);
			String mimeType = sf.getMimeType();
			String mimeEncoding = sf.getMimeEncoding();
	
			// Is it a known MIME type?
			MultimediaClass aClass = MimeUtils.getMultimediaClassFromMimeType(sf.getMimeType());
			if( MultimediaClass.AUDIO == aClass 
			 || MultimediaClass.VIDEO == aClass 
			 || MultimediaClass.IMAGE == aClass 
			 ) {
				String fileClass = aClass.getValue();
				
				result.setMimeType(mimeType);
				result.setMimeEncoding(mimeEncoding);
				result.setFileClass(fileClass);
				result.setFileConvertable(true);
			}
		} catch(Exception e) {
			// Ignore
		}
		
		return result;
	}

	@Override
	public boolean handlesFileClass(String fileClass, String work) {
			
		if( MultimediaClass.AUDIO.getValue().equals(fileClass) ) {
			if( work == FileConversionPlugin.WORK_ANALYZE ) {
				return true;
			} else if( work == FileConversionPlugin.WORK_APPROVE ) {
				return true;
			}
		}

		if( MultimediaClass.VIDEO.getValue().equals(fileClass) ) {
			if( work == FileConversionPlugin.WORK_ANALYZE ) {
				return true;
			} else if( work == FileConversionPlugin.WORK_APPROVE ) {
				return true;
			} else if( work == FileConversionPlugin.WORK_ROTATE_CW ) {
				return true;
			} else if( work == FileConversionPlugin.WORK_ROTATE_CCW ) {
				return true;
			} else if( work == FileConversionPlugin.WORK_ROTATE_180 ) {
				return true;
			}
		}

		if( MultimediaClass.IMAGE.getValue().equals(fileClass) ) {
			if( work == FileConversionPlugin.WORK_ANALYZE ) {
				return true;
			} else if( work == FileConversionPlugin.WORK_APPROVE ) {
				return true;
			} else if( work == FileConversionPlugin.WORK_ORIENT ) {
				return true;
			} else if( work == FileConversionPlugin.WORK_THUMBNAIL ) {
				return true;
			} else if( work == FileConversionPlugin.WORK_UPLOAD_ORIGINAL ) {
				return true;
			} else if( work == FileConversionPlugin.WORK_ROTATE_CW ) {
				return true;
			} else if( work == FileConversionPlugin.WORK_ROTATE_CCW ) {
				return true;
			} else if( work == FileConversionPlugin.WORK_ROTATE_180 ) {
				return true;
			}
		}
		
		return false;
	}

	@Override
	public void performWork(
		String work
		,FileConversionContext conversionContext
		) throws Exception {

		if( work == FileConversionPlugin.WORK_ANALYZE ) {
			analyzeFile(conversionContext);
		} else if( work == FileConversionPlugin.WORK_APPROVE ) {
			approveFile(conversionContext);
		} else if( work == FileConversionPlugin.WORK_ORIENT ) {
			orientImage(conversionContext);
		} else if( work == FileConversionPlugin.WORK_THUMBNAIL ) {
			createThumbnail(conversionContext);
		} else if( work == FileConversionPlugin.WORK_UPLOAD_ORIGINAL ) {
			uploadOriginalFile(conversionContext);
		} else if( work == FileConversionPlugin.WORK_ROTATE_CW ) {
			rotate(work, conversionContext);
		} else {
			throw new Exception("Plugin does not support work: "+work);
		}
	}

	public void analyzeFile(FileConversionContext conversionContext) throws Exception {
		AttachmentDescriptor attDescription = conversionContext.getAttachmentDescription();
		OriginalFileDescriptor originalObj = attDescription.getOriginalFileDescription();
		CouchUserContext submitter = attDescription.getSubmitter();
		
		// Figure out media file located on disk
		File originalFile = originalObj.getMediaFile();
		String mimeType = originalObj.getContentType();

		// Perform conversion(s)
		MultimediaConversionRequest request = new MultimediaConversionRequest();
		request.setInFile( originalFile );
		request.setThumbnailRequested(true);
		request.setProgress( new UploadProgressAdaptor() );
		
		MultimediaClass mmClass = MimeUtils.getMultimediaClassFromMimeType(mimeType);
		if( MultimediaClass.VIDEO == mmClass ) {
			// For video file, convert to appropriate file type
			mmConverter.convertVideo(request);

		} else if( MultimediaClass.AUDIO == mmClass ) {
			// For audio file, convert to appropriate file type
			mmConverter.convertAudio(request);
			
		} else if( MultimediaClass.IMAGE == mmClass ) {
			// For image file, convert to appropriate file type
			mmConverter.convertImage(request);
			
		} else {
			throw new Exception("Unknown multimedia class: "+mmClass);
		}
		
		// Report original size
		if( request.getInHeight() != 0 && request.getInWidth() != 0 ) {
			originalObj.setHeight( request.getInHeight() );
			originalObj.setWidth( request.getInWidth() );
		}

		// Report converted object
		{
			File convertedFile = request.getOutFile();
			SystemFile convertedSf = SystemFile.getSystemFile(convertedFile);

			if( CouchNunaliitUtils.hasVetterRole(submitter, atlasName) ) {
				attDescription.setStatus(UploadConstants.UPLOAD_STATUS_APPROVED);
			} else {
				attDescription.setStatus(UploadConstants.UPLOAD_STATUS_WAITING_FOR_APPROVAL);
			}
			attDescription.setConversionPerformed(request.isConversionPerformed());
			attDescription.setMediaFileName(convertedFile.getName());
			attDescription.setSize(convertedFile.length());
			attDescription.setContentType(convertedSf.getMimeType());
			attDescription.setEncodingType(convertedSf.getMimeEncoding());
			if( request.getOutHeight() != 0 && request.getOutWidth() != 0 ) {
				attDescription.setHeight(request.getOutHeight());
				attDescription.setWidth(request.getOutWidth());
			}
			
			ServerWorkDescriptor serverWork = attDescription.getServerWorkDescription();
			serverWork.setOrientationLevel(UploadConstants.SERVER_ORIENTATION_VALUE);
		}

		// Report thumbnail object
		if( request.isThumbnailCreated() ) {
			File thumbFile = request.getThumbnailFile();
			SystemFile thumbSf = SystemFile.getSystemFile(thumbFile);
			
			String thumbnailAttachmentName = computeThumbnailName(attDescription.getAttachmentName());
			AttachmentDescriptor thumbnailObj = conversionContext.getAttachmentDescription(thumbnailAttachmentName);

			if( CouchNunaliitUtils.hasVetterRole(submitter, atlasName) ) {
				thumbnailObj.setStatus(UploadConstants.UPLOAD_STATUS_APPROVED);
			} else {
				thumbnailObj.setStatus(UploadConstants.UPLOAD_STATUS_WAITING_FOR_APPROVAL);
			}
			thumbnailObj.setFileClass("image");
			thumbnailObj.setOriginalName(attDescription.getOriginalName());
			thumbnailObj.setMediaFileName(thumbFile.getName());
			thumbnailObj.setSource(attDescription.getAttachmentName());

			thumbnailObj.setSize(thumbFile.length());
			thumbnailObj.setContentType(thumbSf.getMimeType());
			thumbnailObj.setEncodingType(thumbSf.getMimeEncoding());

			if( request.getThumbnailHeight() != 0 && request.getThumbnailWidth() != 0 ) {
				thumbnailObj.setHeight(request.getThumbnailHeight());
				thumbnailObj.setWidth(request.getThumbnailWidth());
			}

			attDescription.setThumbnailReference(thumbnailAttachmentName);
		}
		
		// Upload original file
		if( request.isConversionPerformed() ) {
			// Original is not needed if no conversion performed
			
			String fileClass = attDescription.getFileClass();
			if( "image".equals(fileClass) && uploadOriginalImages ) {
				String originalAttachmentName = computeOriginalName(attDescription.getAttachmentName());
				AttachmentDescriptor origDescription = conversionContext.getAttachmentDescription(originalAttachmentName);

				if( CouchNunaliitUtils.hasVetterRole(submitter, atlasName) ) {
					origDescription.setStatus(UploadConstants.UPLOAD_STATUS_APPROVED);
				} else {
					origDescription.setStatus(UploadConstants.UPLOAD_STATUS_WAITING_FOR_APPROVAL);
				}
				origDescription.setContentType(attDescription.getContentType());
				origDescription.setOriginalName(attDescription.getOriginalName());
				origDescription.setMediaFileName(originalFile.getName());
				origDescription.setSource(attDescription.getAttachmentName());

				origDescription.setSize(originalObj.getSize());
				origDescription.setContentType(originalObj.getContentType());
				origDescription.setEncodingType(originalObj.getEncodingType());

				origDescription.setHeight(originalObj.getHeight());
				origDescription.setWidth(originalObj.getWidth());

				ServerWorkDescriptor serverWork = origDescription.getServerWorkDescription();
				serverWork.setOrientationLevel(UploadConstants.SERVER_ORIENTATION_VALUE);

				attDescription.setOriginalAttachment(originalAttachmentName);
			}
		}
	}

	public void uploadOriginalFile(FileConversionContext conversionContext) throws Exception {
		
		if( false == conversionContext.isAttachmentDescriptionAvailable() ){
			throw new Exception("Invalid attachment description");
		}
		
		AttachmentDescriptor attDescription = conversionContext.getAttachmentDescription();
		
		if( false == attDescription.isOriginalFileDescriptionAvailable()
		 || false == attDescription.isWorkDescriptionAvailable()
		 ){
			throw new Exception("Invalid attachment description");
		}
		
		OriginalFileDescriptor originalObj = attDescription.getOriginalFileDescription();
		WorkDescriptor work = attDescription.getWorkDescription();
		
		// Figure out media file located on disk
		File originalFile = originalObj.getMediaFile();
		String mimeType = originalObj.getContentType();

		// Is file converted?
		boolean conversionPerformed = attDescription.isConversionPerformed();
		if( false == conversionPerformed ) {
			work.setStringAttribute(UploadConstants.UPLOAD_WORK_UPLOAD_ORIGINAL_IMAGE, "No conversion performed. No need to upload.");
			return;
		}
		
		// Are uploaded files allowed?
		if( false == uploadOriginalImages ) {
			work.setStringAttribute(UploadConstants.UPLOAD_WORK_UPLOAD_ORIGINAL_IMAGE, "Original file uploads not allowed.");
			return;
		}
		
		// Is it an image?
		String fileClass = attDescription.getFileClass();
		if( false == "image".equals(fileClass) ) {
			work.setStringAttribute(UploadConstants.UPLOAD_WORK_UPLOAD_ORIGINAL_IMAGE, "Original file uploads allowed only for images");
			return;
		}
		
		// Does the original file exist?
		if( false == originalFile.exists() || false == originalFile.isFile() ) {
			work.setStringAttribute(UploadConstants.UPLOAD_WORK_UPLOAD_ORIGINAL_IMAGE, "Can not find original file from media directory");
			return;
		}
		
		// Create attachment description for original file
		String originalAttachmentName = computeOriginalName(attDescription.getAttachmentName());
		AttachmentDescriptor origDescription = conversionContext.getAttachmentDescription(originalAttachmentName);

		origDescription.setStatus(attDescription.getStatus());
		origDescription.setOriginalName(attDescription.getOriginalName());
		origDescription.setMediaFileName(originalFile.getName());
		origDescription.setSource(attDescription.getAttachmentName());

		origDescription.setSize(originalObj.getSize());
		origDescription.setContentType(originalObj.getContentType());
		origDescription.setEncodingType(originalObj.getEncodingType());

		origDescription.setHeight(originalObj.getHeight());
		origDescription.setWidth(originalObj.getWidth());

		ServerWorkDescriptor serverWork = origDescription.getServerWorkDescription();
		serverWork.setOrientationLevel(UploadConstants.SERVER_ORIENTATION_VALUE);

		// Remember original attachment
		attDescription.setOriginalAttachment(originalAttachmentName);

		// Remember that work was performed
		work.removeAttribute(UploadConstants.UPLOAD_WORK_UPLOAD_ORIGINAL_IMAGE);
		
		// Save before upload
		conversionContext.saveDocument();
		
		// Upload original file
		conversionContext.uploadFile(originalAttachmentName, originalFile, mimeType);
	}

	public void rotate(String workType, FileConversionContext conversionContext) throws Exception {
		
		if( false == conversionContext.isAttachmentDescriptionAvailable() ){
			throw new Exception("Invalid attachment description");
		}
		
		AttachmentDescriptor attDescription = conversionContext.getAttachmentDescription();
		
		if( false == attDescription.isOriginalFileDescriptionAvailable()
		 || false == attDescription.isWorkDescriptionAvailable()
		 ){
			throw new Exception("Invalid attachment description");
		}
		
//		OriginalFileDescriptor originalObj = attDescription.getOriginalFileDescription();
		WorkDescriptor work = attDescription.getWorkDescription();
		
		// Figure out media file located on disk
//		File originalFile = originalObj.getMediaFile();
//		String mimeType = originalObj.getContentType();

		// Is file attached?
		if( false == attDescription.getStatus().equals(UploadConstants.UPLOAD_STATUS_ATTACHED) ) {
			work.setStringAttribute(workType, "Media file is not attached and conversion can not be performed.");
			return;
		}
		
		// Is it an image or video?
		String fileClass = attDescription.getFileClass();
		if( false == "image".equals(fileClass) 
		 && false == "video".equals(fileClass) ) {
			work.setStringAttribute(workType, "Media file must be imgage or video.");
			return;
		}

		// Not implemented
		work.setStringAttribute(workType, "Not implemented");
		return;
		
		
		// Download file
//		File outputFile = File.createTempFile("original_", attDescription.getMediaFileName());
//		conversionContext.downloadFile(outputFile);

		// TBD rotate

		// Remember that work was performed
//		work.removeAttribute(workType);
		
		// Save before upload
//		conversionContext.saveDocument();
		
		// Upload rotated file
//		conversionContext.uploadFile(originalAttachmentName, originalFile, mimeType);
	}

	public void approveFile(FileConversionContext conversionContext) throws Exception {
		// Upload file
		String attachementName = conversionContext.getAttachmentName();
		AttachmentDescriptor attDescription = conversionContext.getAttachmentDescription();
		File file = attDescription.getMediaFile();
		String mimeType = conversionContext.getAttachmentDescription().getContentType();
		conversionContext.uploadFile(attachementName, file, mimeType);
	}

	public void orientImage(FileConversionContext conversionContext) throws Exception {
		// Get file
		AttachmentDescriptor attDescription = conversionContext.getAttachmentDescription();
		String mimeType = attDescription.getContentType();
		File outputFile = File.createTempFile("original_", attDescription.getMediaFileName());
		conversionContext.downloadFile(outputFile);
		
		ImageMagickProcessor imp = ImageMagick.getInfo().getProcessor();
		ImageInfo imageInfo = imp.getImageInfo(outputFile);
		
		if( imageInfo.orientation == ImageInfo.Orientation.REQUIRES_CONVERSION ){
			File convertedFile = File.createTempFile("oriented_", attDescription.getMediaFileName());
			imp.reorientImage(outputFile, convertedFile);
			attDescription.uploadFile(convertedFile, mimeType);
		}

		ServerWorkDescriptor serverWork = attDescription.getServerWorkDescription();
		serverWork.setOrientationLevel(UploadConstants.SERVER_ORIENTATION_VALUE);
	}

	public void createThumbnail(FileConversionContext conversionContext) throws Exception {
		// Get file
		AttachmentDescriptor attDescription = conversionContext.getAttachmentDescription();
		String mimeType = attDescription.getContentType();
		File inFile = File.createTempFile("original_", attDescription.getMediaFileName());
		conversionContext.downloadFile(inFile);

		File outFile = File.createTempFile("thumb_", attDescription.getMediaFileName());
		
		// Perform thumbnail
		MultimediaConversionRequest request = new MultimediaConversionRequest();
		request.setInFile( inFile );
		request.setThumbnailFile(outFile);
		request.setThumbnailRequested(true);
		
		MultimediaClass mmClass = MimeUtils.getMultimediaClassFromMimeType(mimeType);
		if( MultimediaClass.IMAGE == mmClass ) {
			// For image file, convert to appropriate file type
			mmConverter.createImageThumbnail(request);
			
		} else {
			throw new Exception("Unknown multimedia class: "+mmClass);
		}

		// Compute attachment name
		SystemFile thumbSf = SystemFile.getSystemFile(outFile);
		String thumbnailAttachmentName = computeThumbnailName(attDescription.getAttachmentName());
		
		// Upload thumbnail
		String thumbMimeType = thumbSf.getMimeType();
		conversionContext.uploadFile(thumbnailAttachmentName, outFile, thumbMimeType);
		
		// Report thumbnail object
		if( request.isThumbnailCreated() ) {
			
			AttachmentDescriptor thumbnailObj = conversionContext.getAttachmentDescription(thumbnailAttachmentName);

			thumbnailObj.setStatus(UploadConstants.UPLOAD_STATUS_ATTACHED);
			thumbnailObj.setFileClass("image");
			thumbnailObj.setOriginalName(attDescription.getOriginalName());
			thumbnailObj.setSource(attDescription.getAttachmentName());

			thumbnailObj.setSize(outFile.length());
			thumbnailObj.setContentType(thumbSf.getMimeType());
			thumbnailObj.setEncodingType(thumbSf.getMimeEncoding());

			if( request.getThumbnailHeight() != 0 
			 && request.getThumbnailWidth() != 0 
			 ) {
				thumbnailObj.setHeight(request.getThumbnailHeight());
				thumbnailObj.setWidth(request.getThumbnailWidth());
			}
			
			attDescription.setThumbnailReference(thumbnailAttachmentName);
		}

		ServerWorkDescriptor serverWork = attDescription.getServerWorkDescription();
		serverWork.setThumbnailLevel(UploadConstants.SERVER_THUMBNAIL_VALUE);
	}

	private String computeOriginalName(String attachmentName) {
		if( null == attachmentName ) {
			return "original";
		}
		
		// Select a different file name
		String prefix = "";
		String suffix = "";
		int pos = attachmentName.indexOf('.', 1);
		if( pos < 0 ) {
			prefix = attachmentName;
		} else {
			prefix = attachmentName.substring(0, pos);
			suffix = attachmentName.substring(pos);
		}
		
		String originalName = prefix + "_original" + suffix;
		
		return originalName;
	}

	private String computeThumbnailName(String attachmentName) {
		if( null == attachmentName ) {
			return "thumbnail";
		}
		
		// Select a different file name
		String prefix = "";
		String suffix = "";
		int pos = attachmentName.indexOf('.', 1);
		if( pos < 0 ) {
			prefix = attachmentName;
		} else {
			prefix = attachmentName.substring(0, pos);
			suffix = attachmentName.substring(pos);
		}
		
		String thumbnailName = prefix + "_thumb" + suffix;
		
		return thumbnailName;
	}
}