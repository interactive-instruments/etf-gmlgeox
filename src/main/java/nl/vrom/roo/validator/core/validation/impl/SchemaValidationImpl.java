package nl.vrom.roo.validator.core.validation.impl;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import nl.vrom.roo.validator.core.TaskVersion;
import nl.vrom.roo.validator.core.ValidatorContext;
import nl.vrom.roo.validator.core.ValidatorMessageBundle;
import nl.vrom.roo.validator.core.errorlocation.PositionErrorLocation;
import nl.vrom.roo.validator.core.validation.AbstractValidation;
import nl.vrom.roo.validator.core.validation.resolver.AdvancedSchemaValidationResourceResolver;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;

/**
 * Schema based validation implementation which validates the content of the
 * input stream against the schemaFile
 *
 * @author brinkmanro
 * @version 1.0.0 Date: 28 Jan 2008
 */
/**
 * @author rdool
 *
 */
public class SchemaValidationImpl extends AbstractValidation {

	private static final Logger LOGGER = LoggerFactory.getLogger(SchemaValidationImpl.class);
	private static final String HONOUR_ALL_SCHEMA_LOCATIONS_FEATURE_VALUE = "http://apache.org/xml/features/honour-all-schemaLocations";
	protected static final String PROPKEY_HONOUR_ALL_SCHEMA_LOCATIONS =  "honour-all-schema-locations";
	
	private Schema schema;

	private File schemaFile;

	private String schemaVersion;

	/*
	 * Initializes the schema validation based on the provided parameters:
	 *
	 * schemaFile: String containing the path to the schema file used when validating schemaResourceDirs: array of
	 * Strings containing paths to directories from which .xsd files should be added to the resources for validation
	 *
	 * @see nl.vrom.roo.validator.core.validation.AbstractValidation#initialize()
	 */
	@Override
	protected void initialize() {

		/*
		 * Due to a bug in Java 1.5 always the XMLSchemaFactory included in the rt.jar is loaded here. Since rt.jar
		 * contains an old version (2.6 or lower) of Xerces it also contains the bugs in it. Now we explicitly load the
		 * Xerces version of the schema factory.
		 *
		 * Tricks like the META-INF/services/javax.xml.validation.SchemaFactory with in it
		 * http\://www.w3.org/2001/XMLSchema=org.apache.xerces.jaxp.validation.XMLSchemaFactory don't work because the
		 * file is not found by the used class loader. Maybe it looks at the wrong place. Very difficult to debug.
		 * Therefore the following explicit assignment.
		 */
		SchemaFactory factory = new org.apache.xerces.jaxp.validation.XMLSchemaFactory();
				//SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema-instance");
		//SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.);
		//SchemaFactory factory = new XMLSchemaFactory();
		
		LOGGER.info("SchemaFactory class=" + factory.getClass().getName());
		if(parameters.containsKey(PROPKEY_HONOUR_ALL_SCHEMA_LOCATIONS)){
			String propValue = (String) parameters.get(PROPKEY_HONOUR_ALL_SCHEMA_LOCATIONS);
			LOGGER.debug("parsing {} ..", PROPKEY_HONOUR_ALL_SCHEMA_LOCATIONS);
			if(propValue !=null){
			
				boolean honourAllSchemaLocations = Boolean.parseBoolean(propValue);
				if(honourAllSchemaLocations){
					LOGGER.debug("Setting {} on SchemaFactory", HONOUR_ALL_SCHEMA_LOCATIONS_FEATURE_VALUE);
					try {
						factory.setFeature(HONOUR_ALL_SCHEMA_LOCATIONS_FEATURE_VALUE, honourAllSchemaLocations);
					} catch (SAXNotRecognizedException e) {
						LOGGER.warn(e.getMessage(), e);
					} catch (SAXNotSupportedException e) {
						LOGGER.warn(e.getMessage(), e);
					}
				}
			}
		} 
		
		
		String schemaFilename = (String) parameters.get("schemaFile");

		if (schemaFilename == null) {
			throw new IllegalArgumentException("No parameter schemaFile specified for schema validation");
		}

		schemaFile = new File(schemaFilename);
		if (!schemaFile.exists()) {
			throw new IllegalArgumentException("Schema file does not exist at location: " + schemaFilename);
		}

		this.determineSchemaVersion();

		File remoteResourceFolder = new File(schemaFile.getParentFile(), "remote");
		factory.setResourceResolver(new AdvancedSchemaValidationResourceResolver(remoteResourceFolder));

		try {
			schema = factory.newSchema(new StreamSource(schemaFile));
		} catch (SAXException e) {
			throw new IllegalArgumentException("Could not create schema Object based on contents of file: "
					+ schemaFile, e);
		}
	}

	private void determineSchemaVersion() {
		SAXBuilder builder = new SAXBuilder();
		try {
			Document doc = builder.build(schemaFile);
			schemaVersion = doc.getRootElement().getAttributeValue("version");
		} catch (IOException e) {
			LOGGER.warn("Could not determine version for schemaFile: {}, reason: {}", schemaFile.getAbsoluteFile(), e
					.getMessage());
		} catch (JDOMException e) {
			LOGGER.warn("Could not determine version for schemaFile: {}, reason: {}", schemaFile.getAbsoluteFile(), e
					.getMessage());
		}

	}

	/*
	 * Execution of the schema validation
	 *
	 * @see
	 * nl.vrom.roo.validator.core.validation.AbstractValidation#validateTemplate(nl.vrom.roo.validator.core.ValidatorContext
	 * , java.io.InputStream)
	 */
	@Override
	public void validateTemplate(ValidatorContext validatorContext, Reader reader) {
		Validator validator = schema.newValidator();
		validator.setErrorHandler(new SchemaValidationErrorHandler(validatorContext));
		try {
			StreamSource streamSource = new StreamSource(reader);
			
			validator.validate(streamSource);
			if (validatorContext.isSuccessful(this)) {
				validatorContext.addNotice(ValidatorMessageBundle.getMessage("validator.core.validation.schema.valid",
						new Object[] { validatorContext.getInputFile().getName(), schemaFile.getName() }), null);
			}
		} catch (IOException e) {
			LOGGER.error("Could not read from inputStream while validating schema", e);
			validatorContext.addFatal("Could not read from inputStream while validating schema");
		} catch (SAXException e) {
			LOGGER.error("Validation failed", e);
			validatorContext.addFatal("Validation failed");
		}
	}

	


	/**
	 * Implementation of the ErrorHandler for the SchemaValidationImpl
	 */
	static class SchemaValidationErrorHandler implements ErrorHandler {

		private final ValidatorContext validatorContext;

		/**
		 * Default constructor
		 *
		 * @param validatorContext
		 *            the validator context for the current validator
		 */
		public SchemaValidationErrorHandler(ValidatorContext validatorContext) {
			this.validatorContext = validatorContext;
		}

		/*
		 * Adds an error message with the SAXException to the validator context
		 *
		 * @see org.xml.sax.ErrorHandler#error(org.xml.sax.SAXParseException)
		 */
		public void error(SAXParseException exception) throws SAXException {
			this.validatorContext.addError(createMessage(exception), new PositionErrorLocation(exception
					.getLineNumber(), exception.getColumnNumber()));
		}

		/*
		 * Adds a fatal message with the SAXException to the validator context
		 *
		 * @see org.xml.sax.ErrorHandler#fatalError(org.xml.sax.SAXParseException)
		 */
		public void fatalError(SAXParseException exception) throws SAXException {
			this.validatorContext.addFatal(createMessage(exception), new PositionErrorLocation(exception
					.getLineNumber(), exception.getColumnNumber()));

		}

		/*
		 * Adds a warning message with the SAXException to the validator context
		 *
		 * @see org.xml.sax.ErrorHandler#warning(org.xml.sax.SAXParseException)
		 */
		public void warning(SAXParseException exception) throws SAXException {
			this.validatorContext.addWarning(createMessage(exception), new PositionErrorLocation(exception
					.getLineNumber(), exception.getColumnNumber()));
		}

		private String createMessage(SAXParseException exception) {
			return exception.getLocalizedMessage()
					+ " "
					+ ValidatorMessageBundle.getMessage("validator.core.validation.schema.location-text", new Object[] {
							exception.getLineNumber(), exception.getColumnNumber() });
		}

	}

	@Override
	public TaskVersion getTaskVersion() {
		return new TaskVersion("1.2", this.schemaFile.getName(), this.schemaVersion);
	}

}
