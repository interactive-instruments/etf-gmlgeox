package nl.vrom.roo.validator.core.validation;

import java.util.HashMap;
import java.util.Map;

import nl.vrom.roo.validator.core.validation.exception.ValidationFactoryException;

/**
 * Factory to instantiate validations. The intention of this factory is to make is possible to be able to configure all
 * Validations within a Validator
 *
 * @author brinkmanro
 * @version 1.0.0 Date: 28 Jan 2008
 */
public final class ValidationFactory {

	/**
	 * Default constructor (private, so ValidationFactory can not be instantiated)
	 */


	public ValidationFactory() {
		super();
	}

	/**
	 * Creates a new instance of a Validation based on the given parameters
	 *
	 * @param validationId
	 *            the id of the validation
	 * @param name
	 *            the name of the validation
	 * @param className
	 *            the class name of the validation
	 * @param skipAllowed
	 *            true if skipping this validation is allowed
	 * @param parameters
	 *            the parameters used to configure this validation
	 * @return an implementation of a AbstractValidation subclass based on the className and parameters
	 * @throws ValidationFactoryException
	 *             when the instance could not be created
	 */
	public AbstractValidation newInstance(String validationId, String name, String className, boolean skipAllowed,
			Map<String, Object> parameters) throws ValidationFactoryException {
		AbstractValidation result = null;
		try {
			Class<?> clazz = Class.forName(className);
			result = (AbstractValidation) clazz.newInstance();
			result.setId(validationId);
			result.setName(name);
			result.setSkipAllowed(skipAllowed);
			result.setParameters(parameters);
//
//
//			if (result instanceof NSUrlBasedValidationImpl) {
//				((NSUrlBasedValidationImpl) result).setValidationFactory(this);
//			}

			result.initializeTask();
		} catch (ClassNotFoundException e) {
			throw new ValidationFactoryException("Could not find class for className: " + className, e);
		} catch (InstantiationException e) {
			throw new ValidationFactoryException("Could not instantiate class for className: " + className, e);
		} catch (IllegalAccessException e) {
			throw new ValidationFactoryException("Could not access class for className: " + className, e);
		} catch (IllegalArgumentException e) {
			throw new ValidationFactoryException(e.getMessage(), e);
		}
		return result;
	}

	/**
	 * Creates a new instance of a Validation based on the given parameters
	 *
	 * @param validationId
	 *            the id of the validation
	 * @param name
	 *            the name of the validation
	 * @param className
	 *            the class name of the validation
	 * @param skipAllowed
	 *            true if skipping this validation is allowed
	 * @return an implementation of a Validation subclass based on the className
	 * @throws ValidationFactoryException
	 *             when the instance could not be created
	 */
	public AbstractValidation newInstance(String validationId, String name, String className, boolean skipAllowed)
			throws ValidationFactoryException {
		return this.newInstance(validationId, name, className, skipAllowed, new HashMap<String, Object>());
	}

	/**
	 * Creates a new instance of a Validation based on the given parameters
	 *
	 * @param validationId
	 *            the id of the validation
	 * @param name
	 *            the name of the validation
	 * @param className
	 *            the class name of the validation
	 * @return an implementation of a Validation subclass based on the className
	 * @throws ValidationFactoryException
	 *             when the instance could not be created
	 */
	public AbstractValidation newInstance(String validationId, String name, String className)
			throws ValidationFactoryException {
		return this.newInstance(validationId, name, className, false, new HashMap<String, Object>());
	}

	/**
	 * Creates a new instance of a Validation based on the given parameters
	 *
	 * @param validationId
	 *            the id of the validation
	 * @param name
	 *            the name of the validation
	 * @param className
	 *            the class name of the validation
	 * @param parameters
	 *            the parameters used to configure this validation
	 * @return an implementation of a AbstractValidation subclass based on the className and parameters
	 * @throws ValidationFactoryException
	 *             when the instance could not be created
	 */
	public AbstractValidation newInstance(String validationId, String name, String className,
			Map<String, Object> parameters) throws ValidationFactoryException {
		return this.newInstance(validationId, name, className, false, parameters);
	}


}
