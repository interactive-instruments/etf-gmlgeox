package nl.vrom.roo.validator.core;

import nl.vrom.roo.core.util.SystemPropertiesParameterUtil;
import nl.vrom.roo.validator.core.exception.ValidatorFactoryException;
import nl.vrom.roo.validator.core.impl.BasicValidatorImpl;
import nl.vrom.roo.validator.core.validation.AbstractValidation;
import nl.vrom.roo.validator.core.validation.ValidationFactory;
import nl.vrom.roo.validator.core.validation.exception.ValidationFactoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

/**
 * ValidatorFactory is responsible for creating {@link Validator}s. A validator consists of several
 * {@link AbstractValidation}s. The factory knows how to construct the validators by reading the configuration from a
 * properties file containing the following keys:
 *
 * <pre>
 * validator.&lt;identity&gt;.name=&lt;string&gt;
 * validator.&lt;identity&gt;.validations=&lt;validation-identity&gt;[,&lt;validation-identity&gt;]*
 *
 * validation.&lt;identity&gt;.name=&lt;string&gt;
 * validation.&lt;identity&gt;.class=&lt;fully qualified class name&gt;
 * validation.&lt;identity&gt;.parameters.&lt;key&gt;=
 * </pre>
 *
 * @author rb
 * @author ss
 * @since 1.0
 */
public final class ValidatorFactory {
	
	private static final String PROPKEY_VALIDATIONS = "validations";
	private static final String PROPKEY_VALIDATOR = "validator";
	private static final String PROPKEY_VALIDATION = "validation.";
	private static final String PROPKEY_NAME = ".name";
	private static final String PROPKEY_SKIP = ".skipAllowed";
	private static final String PROPKEY_CLASS = ".class";
	private static final String PROPKEY_PARAMETERS = ".parameters.";

	private final Logger logger = LoggerFactory.getLogger(ValidatorFactory.class);

	private final ValidationFactory validationFactory;

	/**
	 * Instantiates a new ValidatorFactory.
	 *
	 * @param validationFactory
	 *            the validatorFactory needs a validationFactory that constructs the validations
	 */
	public ValidatorFactory(ValidationFactory validationFactory) {
		this.validationFactory = validationFactory;
	}

	/**
	 * Creates a new {@linkplain Validator} based on the properties provided.
	 *
	 * The properties contain only one validator, and a key {@value #PROPKEY_VALIDATIONS}.
	 *
	 * @param properties
	 *            the properties used to construct a new validator instance
	 * @return a validator instance
	 * @throws ValidatorFactoryException
	 *             if the configuration is insufficient of incorrect to create a validator instance
	 */
	public Validator newInstance(Properties properties) throws ValidatorFactoryException {
		return newInstance(properties, null);
	}

	/**
	 * Creates a new {@linkplain Validator} based on the properties provided.
	 *
	 * The properties contain more than one validator, as follows:
     * <pre>
     * validator.&lt;identity&gt;.name=&lt;string&gt;
     * validator.&lt;identity&gt;.validations=&lt;validation-identity&gt;[,&lt;validation-identity&gt;]*
     * </pre>
	 *
	 * @param properties
	 *            the properties used to construct a new validator instance
	 * @param key
	 *            the &lt;identity&gt; of the validator to instantiate
	 * @return a validator instance
	 * @throws ValidatorFactoryException
	 *             if the configuration is insufficient of incorrect to create a validator instance
	 */
	public Validator newInstance(Properties properties, String key) throws ValidatorFactoryException {
    	if (properties == null) {
    	    throw new IllegalArgumentException("No properties passed while calling new Instance on ValidatorFactory");
    	}

    	List<AbstractValidation> validations = new ArrayList<AbstractValidation>();

		String lookupPropKey = null;
		if (key == null) {
			lookupPropKey = PROPKEY_VALIDATIONS;
		} else {
			lookupPropKey = PROPKEY_VALIDATOR + "." + key + "." + PROPKEY_VALIDATIONS;
		}

		if (properties.getProperty(lookupPropKey) == null) {
			throw new ValidatorFactoryException("No property " + lookupPropKey + " found");
		}

		String[] validationIds = properties.getProperty(lookupPropKey).split(",");

		for (String validationId : validationIds) {

			validationId = validationId.trim();

			String name = properties.getProperty(PROPKEY_VALIDATION + validationId + PROPKEY_NAME);
			if (name == null) {
				throw new ValidatorFactoryException("No property " + PROPKEY_VALIDATION + validationId + PROPKEY_NAME
						+ " found for validation " + validationId);
			}
			String className = properties.getProperty(PROPKEY_VALIDATION + validationId + PROPKEY_CLASS);
			if (className == null) {
				throw new ValidatorFactoryException("No property " + PROPKEY_VALIDATION + validationId + PROPKEY_CLASS
						+ " found for validation " + validationId);
			}
			
			String skipAllowedValue = properties.getProperty(PROPKEY_VALIDATION + validationId + PROPKEY_SKIP);

			boolean skipAllowed = false;
			if (skipAllowedValue != null && skipAllowedValue.trim().equalsIgnoreCase("true")) {
				skipAllowed = true;
			}

			Map<String, Object> parameters = getParamsMap(PROPKEY_VALIDATION + validationId, properties);
			SystemPropertiesParameterUtil.copySystemProperties(parameters, properties);
			
			try {
				logger.info("Creating instance name: {}, className {}, parameters: {}", new Object[] { name, className,
						parameters });
				
				AbstractValidation validation = validationFactory.newInstance(validationId, name, className, skipAllowed, parameters);
				validations.add(validation);
			} catch (ValidationFactoryException e) {
				throw new ValidatorFactoryException("Could not instantiate validation name: " + name + ", reason: "
						+ e.getMessage() + ", className " + className + ", parameters: " + parameters, e);
			}
		}
		return new BasicValidatorImpl(validations);
	}
	


	/**
	 * Creates a new {@linkplain Validator} based on the properties provided.
	 *
	 * The properties contain only one validator, and a key {@value #PROPKEY_VALIDATIONS}.
	 *
	 * @param propertiesFile
	 *            the propertiesFile used to construct a new validator instance
	 * @return a validator instance
	 * @throws ValidatorFactoryException
	 *             if the configuration is insufficient of incorrect to create a validator instance
	 */
	public Validator newInstance(File propertiesFile) throws ValidatorFactoryException {
		Properties properties = new Properties();
		FileInputStream propertiesIS = null;
		try {
			propertiesIS = new FileInputStream(propertiesFile);
			properties.load(propertiesIS);
		} catch (FileNotFoundException e) {
			throw new ValidatorFactoryException("Configuration file not found: " + propertiesFile.getAbsolutePath(), e);
		} catch (IOException e) {
			throw new ValidatorFactoryException("Unexpected IOException while reading configuration file: "
					+ propertiesFile.getAbsolutePath(), e);
		} finally {
			if (propertiesIS != null) {
				try {
					propertiesIS.close();
				} catch (IOException ioe) { // NOPMD - Nothing to fix here
				}
			}
		}

		return newInstance(properties);
	}


	/**
	 * Get the available "parameters" from a map. The parameters are defined as:
	 *
	 * <pre>
     * &lt;prefix&gt;.parameters.&lt;key&gt;=&lt;value&gt;
     * </pre>
	 *
	 *
	 * @param prefix
	 *            the prefix for which to get the parameters
	 * @param properties
	 *            the properties to get the parameters from
	 * @return map of parameters for the specified prefix
	 */
	public static Map<String, Object> getParamsMap(String prefix, Map<String, Object> properties) {
		Map<String, Object> result = new HashMap<String, Object>();

		for (Entry<String, Object> entry : properties.entrySet()) {
			String key = entry.getKey();

			if (key.startsWith(prefix + PROPKEY_PARAMETERS)) {
				result.put(key.substring((prefix + PROPKEY_PARAMETERS).length()), prepareParam((String) entry.getValue() ));
			}
		}
		return result;
	}

	private static Map<String, Object> getParamsMap(String prefix, Properties properties) {
		Map<String, Object> result = new HashMap<String, Object>();
		for (Enumeration<Object> propEnum = properties.keys(); propEnum.hasMoreElements(); /* */) {
			String key = (String) propEnum.nextElement();
			if (key.startsWith(prefix + PROPKEY_PARAMETERS)) {
				result.put(key.substring((prefix + PROPKEY_PARAMETERS).length()), prepareParam(properties
						.getProperty(key)));
			}
		}
		return result;
	}

	private static String prepareParam(String property) {
		String result = property;
		if (property != null && System.getProperty("CONFIGDIR") != null) {
			result = property.replace("${CONFIGDIR}", System.getProperty("CONFIGDIR"));
		}
		return result;
	}

}
