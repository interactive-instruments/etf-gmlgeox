package nl.vrom.roo.validator.core;

import nl.vrom.roo.validator.core.validation.AbstractValidation;

import java.util.List;


/**
 * A Validator is responsible for validating. The provided {@linkplain ValidatorContext} contains the data/file that
 * needs to be validated. The {@link AbstractValidation}s do the actual validation of the data and a validator can
 * consist of multiple vlaidations.
 *
 * @author rb
 * @author ss
 *
 * @since 1.0
 */
public interface Validator {

	/**
	 * Validate the provided context.
	 *
	 * @param context
	 *            the context to validate
	 */
	public void validate(ValidatorContext context);

	/**
	 * Get the validations that the base of this validator.
	 *
	 * @return the validations that form this validator
	 */
	public List<AbstractValidation> getValidations();

}
