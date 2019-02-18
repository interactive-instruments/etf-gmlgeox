package nl.vrom.roo.validator.core.impl;

import nl.vrom.roo.validator.core.Validator;
import nl.vrom.roo.validator.core.ValidatorContext;
import nl.vrom.roo.validator.core.ValidatorMessageBundle;
import nl.vrom.roo.validator.core.exception.ValidationException;
import nl.vrom.roo.validator.core.util.EncodedFileReader;
import nl.vrom.roo.validator.core.validation.AbstractValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

/**
 * This class is responsible for validating the contents of an inputstream using the defined validation implementations
 *
 * @author brinkmanro
 * @version 1.0.0 Date: 28 Jan 2008
 */
public class BasicValidatorImpl implements Validator {

	private final Logger logger = LoggerFactory.getLogger(BasicValidatorImpl.class);

	/**
	 * List of validations used (in order) by this validator
	 */
	protected List<AbstractValidation> validations;

	/**
	 * Default constructor
	 */
	public BasicValidatorImpl(List<AbstractValidation> validations) {
		this.validations = validations;
	}

	public void validate(ValidatorContext context) {
		
		for (AbstractValidation validation : validations) {

			if (validation.isSkipAllowed() && context.skipTask(validation.getId())) {
				logger.info("Validation: {} ({}) has been skipped", validation.getName(), validation.getId());
				continue;
			}

			Reader reader = null;
			try {
				if(validation.isFileNeeded()) {
					if(context.getInputFile()==null || !context.getInputFile().exists() || !context.getInputFile().canRead()) {
						context.addFatal(ValidatorMessageBundle.getMessage("validator.core.validation.exception.file"), null);
						return;
					}
					reader = new EncodedFileReader(context.getInputFile());
				}
				validation.validate(context, reader);
			} catch (ValidationException e) {
				logger.error("Validation error: ", e);
				context.addFatal(ValidatorMessageBundle.getMessage("validator.core.validation.exception"));
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (IOException e) { // NOPMD - Ignored. The reader is not needed any more.
					}
				}
			}

			if (context.hasFatals()) {
				break;
			}
		}
	}

	/**
	 * Set the list of validations used by this validator
	 *
	 * @param validations
	 *            list of validations to set
	 */
	public void setValidations(List<AbstractValidation> validations) {
		this.validations = validations;
	}

	/**
	 * Returns the list of validation used by this validator
	 *
	 * @return the validations used by this validator
	 */
	public List<AbstractValidation> getValidations() {
		return validations;
	}

	/**
	 * Adds the validation to this validator
	 *
	 * @param validation
	 *            the validation to add to this validator
	 * @return true on success
	 */
	public boolean addValidation(AbstractValidation validation) {
		return validations.add(validation);
	}

}
