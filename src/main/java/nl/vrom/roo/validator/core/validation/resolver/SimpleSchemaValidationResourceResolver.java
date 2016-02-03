package nl.vrom.roo.validator.core.validation.resolver;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

/**
 * 
 * Deprecated: Use the AdvancedSchemaValidationbResourceResolver instead
 * 
 * Implementation of LSResourceResolver which resolves dependent .xsd schema's based on the last part of the
 * systemId.
 *
 */
@Deprecated
public class SimpleSchemaValidationResourceResolver implements LSResourceResolver {

	private final List<File> resourceDirs;

	private final Map<String, LSInput> resourceFileCache;
	
	Logger logger = LoggerFactory.getLogger(SimpleSchemaValidationResourceResolver.class);

	/**
	 * SchemaValidationResourceResolver constructor
	 *
	 * @param schemaFile
	 *            reference to the schemaFile used for this validation
	 */
	public SimpleSchemaValidationResourceResolver(File schemaFile) {
		this.resourceDirs = new ArrayList<File>();
		this.resourceDirs.add(schemaFile.getParentFile());

		this.resourceFileCache = new HashMap<String, LSInput>();
	}

	/*
	 * Resolve logic based resolving relative from the parent directory of the schema and all other resources parent
	 * directories
	 *
	 * @see org.w3c.dom.ls.LSResourceResolver#resolveResource(java.lang.String, java.lang.String, java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId,
			String baseURI) {

		if (systemId.contains("://")) {

			String schemaName = new File(systemId).getName();

			LSInput result = resolveFileResource(systemId, true, schemaName);
			if (result == null) {
				throw new IllegalArgumentException("Resolving of remote resources is not allowed (systemId: "
						+ systemId + "). Could not find schema in local directory.");
			}
			return result;
		}

		return resolveFileResource(systemId, false, systemId);
	}

	private LSInput resolveFileResource(String systemId, boolean isRemote, String filename) {

		LSInput result = this.resourceFileCache.get(systemId);
		if (result == null) {
			result = resolveResourceFromResourceDirs(systemId, filename, isRemote);
			if (result != null) {
				this.resourceFileCache.put(systemId, result);
			}
		}
		return result;
	}

	private LSInput resolveResourceFromResourceDirs(String systemId, String filename, boolean remote) {
		LSInput result = null;
		for (File resourceDir : resourceDirs) {
			File dir = resourceDir;
			if (remote) {
				dir = new File(resourceDir, "/remote");
			}
			File resourceFile = new File(dir, filename);
			if (resourceFile.exists()) {
				logger.debug("Resolved resource with systemId: {} in resourceDir: {}", systemId, dir);

				// Add the parent folder of the resource to the resourceDir
				// if it's not already part of it
				if (!resourceDirs.contains(resourceFile.getParentFile())) {
					resourceDirs.add(resourceFile.getParentFile());
				}
				result = new SchemaValidationResource(resourceFile, systemId, null, null);
				break;
			}
		}
		if (result == null) {
			logger.error("Could not resolve resource for systemId: {}", systemId);
		}
		return result;
	}
}
