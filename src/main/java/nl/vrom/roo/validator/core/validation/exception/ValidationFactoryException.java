package nl.vrom.roo.validator.core.validation.exception;

/**
 * Exception related to the ValidationFactory
 * 
 * @author brinkmanro
 * @version 1.0.0 Date: 28 Jan 2008
 */
public class ValidationFactoryException extends Exception {

	private static final long serialVersionUID = -9146059793823983715L;

	/**
	 * @see Exception#Exception()
	 */
	public ValidationFactoryException() {
		super();
	}

	/**
	 * @see Exception#Exception(String)
	 */
	public ValidationFactoryException(String message) {
		super(message);
	}

	/**
	 * @see Exception#Exception(Throwable)
	 */
	public ValidationFactoryException(Throwable cause) {
		super(cause);
	}

	/**
	 * @see Exception#Exception(String, Throwable)
	 */
	public ValidationFactoryException(String message, Throwable cause) {
		super(message, cause);
	}

}
