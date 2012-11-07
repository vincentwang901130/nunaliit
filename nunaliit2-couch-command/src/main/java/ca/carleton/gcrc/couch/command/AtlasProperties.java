package ca.carleton.gcrc.couch.command;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

public class AtlasProperties {

	static public AtlasProperties fromAtlasDir(File atlasDir) throws Exception {
		Properties props = new Properties();
		readProperties(atlasDir, props);
		
		return fromProperties(props);
	}

	static public AtlasProperties fromProperties(Properties props) throws Exception {
		AtlasProperties atlasProps = new AtlasProperties();
		
		atlasProps.setAtlasName( props.getProperty("atlas.name") );
		atlasProps.setCouchDbName( props.getProperty("couchdb.dbName") );
		atlasProps.setCouchDbAdminUser( props.getProperty("couchdb.admin.user") );
		atlasProps.setCouchDbAdminPassword( props.getProperty("couchdb.admin.password") );
		
		// CouchDb URL
		try {
			String urlStr = props.getProperty("couchdb.url");
			URL url = new URL(urlStr);
			atlasProps.setCouchDbUrl(url);
		} catch(Exception e) {
			throw new Exception("Unable to decode CouchDB URL",e);
		}
		
		// Server port
		try {
			String portString = props.getProperty("servlet.url.port");
			int port = Integer.parseInt(portString);
			if( 0 == port ) {
				throw new Exception("Invalid servlet port: "+portString);
			}
			atlasProps.setServerPort(port);
		} catch(Exception e) {
			throw new Exception("Unable to interpret servlet port",e);
		}
		
		
		return atlasProps;
	}

	static public void readProperties(File atlasDir, Properties props) throws Exception {
		// install.properties
		{
			File installPropFile = new File(atlasDir,"config/install.properties");
			if( installPropFile.exists() && installPropFile.isFile() ){
				FileInputStream fis = null;
				try {
					fis = new FileInputStream(installPropFile);
					InputStreamReader reader = new InputStreamReader(fis,"UTF-8");
					props.load(reader);
					
				} catch(Exception e) {
					throw new Exception("Unable to read config properties from: "+installPropFile.getAbsolutePath(), e);
					
				} finally {
					if( null != fis ){
						try{
							fis.close();
						} catch(Exception e) {
							// Ignore
						}
					}
				}
			}
		}

		// sensitive.properties
		{
			File sensitivePropFile = new File(atlasDir,"config/sensitive.properties");
			if( sensitivePropFile.exists() && sensitivePropFile.isFile() ){
				FileInputStream fis = null;
				try {
					fis = new FileInputStream(sensitivePropFile);
					InputStreamReader reader = new InputStreamReader(fis,"UTF-8");
					props.load(reader);
					
				} catch(Exception e) {
					throw new Exception("Unable to read config properties from: "+sensitivePropFile.getAbsolutePath(), e);
					
				} finally {
					if( null != fis ){
						try{
							fis.close();
						} catch(Exception e) {
							// Ignore
						}
					}
				}
			}
		}
	}
	
	static public void writeProperties(File atlasDir, Properties props) throws Exception {
		// Create config directory, if needed
		File configDir = new File(atlasDir,"config");
		try {
			if( false == configDir.exists() ){
				if( false == configDir.mkdir() ) {
					throw new Exception("Error creating directory: "+configDir.getAbsolutePath());
				}
			}
		} catch(Exception e) {
			throw new Exception("Unable to create config directory",e);
		}
		
		// Divide public and sensitive properties
		Properties publicProps = new Properties();
		Properties sensitiveProps = new Properties();
		
		Enumeration<?> namesEnum = props.propertyNames();
		while( namesEnum.hasMoreElements() ){
			Object keyObj = namesEnum.nextElement();
			if( keyObj instanceof String ) {
				String key = (String)keyObj;
				String value = props.getProperty(key);
				if( "couchdb.admin.password".equals(key) ){
					sensitiveProps.put(key, value);
				} else {
					publicProps.put(key, value);
				}
			}
		}
		
		// Write public file
		{
			File installPropFile = new File(configDir,"install.properties");
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(installPropFile);
				OutputStreamWriter osw = new OutputStreamWriter(fos,"UTF-8");
				publicProps.store(osw, "Atlas properties");
				
				osw.flush();
				
			} catch(Exception e) {
				throw new Exception("Unable to write config properties to: "+installPropFile.getAbsolutePath(), e);
				
			} finally {
				if( null != fos ){
					try{
						fos.close();
					} catch(Exception e) {
						// Ignore
					}
				}
			}
		}
		
		// Write sensitive file
		{
			File sensitivePropFile = new File(configDir,"sensitive.properties");
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(sensitivePropFile);
				OutputStreamWriter osw = new OutputStreamWriter(fos,"UTF-8");
				sensitiveProps.store(osw, "Atlas properties");
				
				osw.flush();
				
			} catch(Exception e) {
				throw new Exception("Unable to write config properties to: "+sensitivePropFile.getAbsolutePath(), e);
				
			} finally {
				if( null != fos ){
					try{
						fos.close();
					} catch(Exception e) {
						// Ignore
					}
				}
			}
		}
	}
	
	private String atlasName;
	private URL couchDbUrl;
	private String couchDbName;
	private String couchDbAdminUser;
	private String couchDbAdminPassword;
	private int serverPort = 8080;
	
	public String getAtlasName() {
		return atlasName;
	}
	public void setAtlasName(String atlasName) {
		this.atlasName = atlasName;
	}
	
	public URL getCouchDbUrl() {
		return couchDbUrl;
	}
	public void setCouchDbUrl(URL couchDbUrl) {
		this.couchDbUrl = couchDbUrl;
	}
	
	public String getCouchDbName() {
		return couchDbName;
	}
	public void setCouchDbName(String couchDbName) {
		this.couchDbName = couchDbName;
	}

	public String getCouchDbAdminUser() {
		return couchDbAdminUser;
	}
	public void setCouchDbAdminUser(String couchDbAdminUser) {
		this.couchDbAdminUser = couchDbAdminUser;
	}

	public String getCouchDbAdminPassword() {
		return couchDbAdminPassword;
	}
	public void setCouchDbAdminPassword(String couchDbAdminPassword) {
		this.couchDbAdminPassword = couchDbAdminPassword;
	}

	public int getServerPort() {
		return serverPort;
	}
	public void setServerPort(int serverPort) {
		this.serverPort = serverPort;
	}
}