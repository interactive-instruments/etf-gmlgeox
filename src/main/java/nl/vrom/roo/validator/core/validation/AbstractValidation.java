package nl.vrom.roo.validator.core.validation;

import nl.vrom.roo.core.util.SystemPropertiesParameterUtil;
import nl.vrom.roo.validator.core.AbstractTask;
import nl.vrom.roo.validator.core.ValidatorContext;
import nl.vrom.roo.validator.core.ValidatorMessageBundle;
import nl.vrom.roo.validator.core.exception.ValidationException;
import nl.vrom.roo.validator.core.util.SystemSettingsDependentFactory;

import java.io.Reader;

/**
 * This class is the framework of a general validation.
 * It contains all functionality of general tasks including a validation template
 * 
 * @see AbstractTask
 * @author rbrinkman
 */
public abstract class AbstractValidation extends AbstractTask {

	private static final Object PROPKEY_FATAL = "fatal";
	
	private boolean fatal;
	
	private boolean fileNeeded = true;


	protected abstract class SettingsDependentFactory<T> extends SystemSettingsDependentFactory<T> {
		
		public SettingsDependentFactory() {
			super(SystemPropertiesParameterUtil.getSystemProperties(getParameters()));
		}
		
		public String getProperty(String propertyName) {
			return super.getSystemProperties().getProperty(propertyName);
		}
		
	}
	

	
	/**
	 * @return the fatal
	 */
	public boolean isFatal() {
		return fatal;
	}
	
	/**
	 * Initializes the flag that judges whether an error is fatal or not
	 * This method is generally called from the {@link #initialize()} method.
	 */
	protected void initFatalFlag() {
		
		String fatalParam = (String) parameters.get(PROPKEY_FATAL);

		if (fatalParam == null || (!"true".equals(fatalParam) && !"false".equals(fatalParam))) {
			throw new IllegalArgumentException("No or empty parameter " + PROPKEY_FATAL
					+ " specified for validation "+ this.getName());
		} else if ("true".equals(fatalParam)) {
			fatal = true;
		} else {
			fatal = false;
		}	
	}
	
	/**
	 * Abstract method used to execute the validation of the subclass as a template
	 * 
	 * @param validatorContext
	 *            context of the validator
	 * @param reader the reader used to validate
	 * @throws ValidationException
	 */
	protected abstract void validateTemplate(ValidatorContext validatorContext, Reader reader)
			throws ValidationException;

	/**
	 * Validates the contents of the reader using the validatorContext to store the results for this validator
	 * 
	 * @param validatorContext
	 *            context of the validator
	 * @param reader
	 *            the inputStream to the document to validate
	 * @throws ValidationException
	 */
	public final void validate(ValidatorContext validatorContext, Reader reader) throws ValidationException {
		validatorContext.setCurrentTask(this);
		validatorContext.addNotice(ValidatorMessageBundle.getMessage("validator.core.validation.start",
				new Object[] { this.getName() }), null);
		validateTemplate(validatorContext, reader);
		validatorContext.addNotice(ValidatorMessageBundle.getMessage("validator.core.validation.end",
				new Object[] { this.getName() }), null);
		validatorContext.resetTaskHandling();
	}

	/**
	 * Validates the contens of the reader using the validatorContext to store the results for this validator
	 * 
	 * @param validatorContext
	 *            context of the validator
	 * @param reader
	 *            the inputStream to the document to validate
	 * @param parentValidation
	 *            the validation that this validation is a child of
	 * @throws ValidationException
	 */
	public final void validate(ValidatorContext validatorContext, Reader reader, AbstractValidation parentValidation)
			throws ValidationException {
		validatorContext.removeTaskFromHistory(parentValidation);
		this.validate(validatorContext, reader);
	}

	public static <T> void clearHandlersInContext(ValidatorContext validatorContext, Class<T>... handlerClasses) {
		for(Class<T> handler : handlerClasses) {
			validatorContext.setParameter(handler.getName(), null);
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public static <T> T retrieveHandlerFromContext(ValidatorContext validatorContext, Class<T> handlerClass) {
		return (T) validatorContext.getParameter(handlerClass.getName());
	}

	/**
	 * @return the needsFile
	 */
	public boolean isFileNeeded() {
		return fileNeeded;
	}

	/**
	 * @param fileNeeded the needsFile to set
	 */
	public void setFileNeeded(boolean fileNeeded) {
		this.fileNeeded = fileNeeded;
	}


}
