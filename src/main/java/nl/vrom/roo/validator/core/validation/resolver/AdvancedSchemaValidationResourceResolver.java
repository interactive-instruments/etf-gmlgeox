package nl.vrom.roo.validator.core.validation.resolver;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

/**
 * Implementation of LSResourceResolver which resolves dependent .xsd schema's based on the last part of the systemId.
 *
 */
public class AdvancedSchemaValidationResourceResolver implements LSResourceResolver {

	Logger logger = LoggerFactory.getLogger(AdvancedSchemaValidationResourceResolver.class);

	private File remoteResourceFolder;

	public AdvancedSchemaValidationResourceResolver(File remoteResourceFolder) {
		if (remoteResourceFolder != null && remoteResourceFolder.exists()) {
			this.remoteResourceFolder = remoteResourceFolder;
		} else {
			logger.warn("Could not find remoteResourceFolder {} disabling resolving of remote resources",
					remoteResourceFolder==null ? null : remoteResourceFolder.getAbsolutePath());
		}

	}

	/**
	 * Resolve logic based resolving relative from the parent directory of the schema and all other resources parent
	 * directories
	 *
	 * @see org.w3c.dom.ls.LSResourceResolver#resolveResource(java.lang.String, java.lang.String, java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {

		logger.debug("Resolving resource {} for {}", systemId, baseURI);

		LSInput resource;
		if(isRemoteResource(systemId)) {	
			// systemId is absolute address. Start an absolute search in the remote directory.
			resource = resolveRemoteResource(systemId);
		}
		else {
			// SystemId is relative address. First determine whether to search local or remote.
			if(isRemoteResource(baseURI)) {
				// Start a relative search in the remote directory.
				
//				String tempParent = new File(baseURI).getParentFile().toString().replace("http:\\","http://").replace(File.separator, "/");
				String tempParent = getParentURI(baseURI);
				String tempSystemId = systemId;
				
				// Go to parent directories if needed
				while(tempSystemId.startsWith("../") || tempSystemId.startsWith("./")) {
					if(tempSystemId.startsWith("../")) {
						tempSystemId = tempSystemId.substring(3);
						tempParent = getParentURI(tempParent);
					}
					else {
						tempSystemId = tempSystemId.substring(2);
					}
				}
				
				String newSystemId = getChildURI(tempParent, tempSystemId);
				resource = resolveRemoteResource(newSystemId);
			}
			else {
				// Start a relative search in the schema directory.
				resource = resolveLocalResource(systemId, baseURI, publicId);
			}
		}
		
		
		return resource;
	}

	
	private String getParentURI(String uri) {
		
		int idx2 = uri.indexOf("//");
		int idx = uri.lastIndexOf("/");
		
		if(idx2+1==idx) {
			return uri;
		}
		
		if(uri.endsWith("/")) {
			return getParentURI(uri.substring(0, idx));
		}
		
		return uri.substring(0, idx);
	}
	
	private String getChildURI(String uri, String child) {
		
		if(child.startsWith("/")) {
			if(uri.endsWith("/")) {
				return uri.substring(0, uri.length()-1)+child;
			}
			else {
				return uri+child;
			}
		}
		else {
			if(uri.endsWith("/")) {
				return uri+child;
			}
			else {
				return uri+"/"+child;
			}
		}
	}
	
	

	private LSInput resolveLocalResource(String systemId, String baseURI, String publicId) {
		try {
			File baseDir = getBaseDirAsFile(baseURI);
			if (!baseDir.exists()) {
				throw new IllegalArgumentException("Basedir: " + baseDir.getAbsolutePath() + " does not exists");
			}

			String resourceFileName = FilenameUtils.normalize(baseDir.getAbsolutePath() + File.separator + systemId);

			File resourceFile = new File(resourceFileName);

			if (!resourceFile.exists()) {
				throw new IllegalArgumentException("Resource: " + resourceFile.getAbsolutePath() + " does not exists");
			}

			String resourceBaseURI = resourceFile.toURI().toString();
			logger.debug("Resolved local resource for systemId: {} to:  {} (baseURI: {})", new Object[] { systemId,
					resourceFile.getAbsolutePath(), resourceBaseURI });
			return new SchemaValidationResource(resourceFile, baseURI, systemId, publicId);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Invalid uri for baseURI: " + baseURI, e);
		}
	}

	private LSInput resolveRemoteResource(String systemId) {

		if (remoteResourceFolder == null) {
			throw new IllegalArgumentException("Not allowed to resolve remote resource: " + systemId
					+ " no remoteResourceFolder set");
		}

		logger.debug("Now try to resolve remote resource with systemId: {}", systemId);
		
		File resourceFile = retrieveResourceFileFromSystemIdDirectly(systemId);

		if (!resourceFile.exists()) {
			
			String triedFirst = resourceFile.getAbsolutePath();
			
			resourceFile = retrieveResourceFileFromSystemIdAsURL(systemId, false);
			
			if(!resourceFile.exists()) {

				String triedSecond = resourceFile.getAbsolutePath();
				
				resourceFile = retrieveResourceFileFromSystemIdAsURL(systemId, true);
				
				if(!resourceFile.exists()) {
					String triedThird = resourceFile.getAbsolutePath();
					
					throw new IllegalArgumentException("Could not resolve remote file for systemId:" + systemId + " tried: "
						+ triedFirst+" , then "+triedSecond+", then "+triedThird );
				}
			}
		}
		
		logger.debug("Resolved remote resource for systemId: {} to:  {}", systemId,	resourceFile.getAbsolutePath() );
		
		return new SchemaValidationResource(resourceFile, resourceFile.getParentFile().toURI().toString(), systemId,
				null);
	}
	
	private File retrieveResourceFileFromSystemIdDirectly(String systemId) {
		
		String filename = FilenameUtils.getName(systemId);

		String resourceFileName = FilenameUtils.normalize(remoteResourceFolder.getAbsolutePath() + File.separator
				+ filename);

		return new File(resourceFileName);
	}

	private File retrieveResourceFileFromSystemIdAsURL(String systemId, boolean inclHost) {
		
		logger.debug("Now try to resolve it using systemId as URL: {}", systemId);

		URI uri;
		try {
			uri = new URI(systemId);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Could not resolve remote file for systemId:" + systemId + " Is no valid URL.");
		}

		String path = uri.getPath();
		if(path.startsWith("/")) {
			path = path.substring(1);
		}
		
		if(inclHost) {
			path = uri.getHost() + "/" + path;
		}

		logger.debug("systemId: path: {}", path);

		return new File(remoteResourceFolder, path);
	}

	
	

	private boolean isRemoteResource(String systemId) {
		return systemId.startsWith("http://");
	}

	private File getBaseDirAsFile(String baseURI) throws URISyntaxException {
		if (isRemoteResource(baseURI)) {
			return remoteResourceFolder;
		} else {
			return new File(new URI(baseURI)).getParentFile();
		}
	}

}
