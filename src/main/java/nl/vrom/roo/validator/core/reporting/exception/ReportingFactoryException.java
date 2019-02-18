package nl.vrom.roo.validator.core.reporting.exception;

/**
 * Exception related to the ValidationFactory
 * 
 * @author rdool
 * @version 1.0.0 Date: 13 Mar 2008
 */
public class ReportingFactoryException extends Exception {

	private static final long serialVersionUID = 2764298054325660856L;

	/**
	 * @see Exception#Exception()
	 */
	public ReportingFactoryException() {
		super();
	}

	/**
	 * @see Exception#Exception(String)
	 */
	public ReportingFactoryException(String message) {
		super(message);
	}

	/**
	 * @see Exception#Exception(Throwable)
	 */
	public ReportingFactoryException(Throwable cause) {
		super(cause);
	}

	/**
	 * @see Exception#Exception(String, Throwable)
	 */
	public ReportingFactoryException(String message, Throwable cause) {
		super(message, cause);
	}

}
