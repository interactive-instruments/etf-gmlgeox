package nl.vrom.roo.validator.core;

import java.util.Date;

import nl.vrom.roo.validator.core.errorlocation.ErrorLocation;

/**
 * This class is contains the validator message that is used to contain information about the validation
 *
 * @author brinkmanro
 * @version 1.0.0 Date: 28 Jan 2008
 */
public class ValidatorMessage {
	private Date messageDate;

	private ValidatorMessageType type;

	private String message;

	private ErrorLocation errorLocation;

	private String validationName;

	/**
	 * Default constructor (private, can not be used to instantiate a ValidatorMessage)
	 */
	private ValidatorMessage() {
		this.messageDate = new Date();
	}

	/**
	 * ValidatorMessage constructor
	 *
	 * @param validationName
	 * @param type
	 *            the type of the message
	 * @param message
	 *            the message
	 */
	public ValidatorMessage(String validationName, ValidatorMessageType type, String message) {
		this();
		this.validationName = validationName;
		this.type = type;
		this.message = message;
	}

	/**
	 * ValidatorMessage constructor
	 *
	 * @param validationName
	 * @param type
	 *            the type of the message
	 * @param message
	 *            the message
	 * @Param errorLocation an optional {@link ErrorLocation}
	 */
	public ValidatorMessage(String validationName, ValidatorMessageType type, String message,
			ErrorLocation errorLocation) {
		this(validationName, type, message);
		this.errorLocation = errorLocation;
	}

	/**
	 * @return the message type
	 */
	public ValidatorMessageType getType() {
		return type;
	}

	/**
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * @return the message date
	 */
	public Date getMessageDate() {
		return (Date) messageDate.clone();
	}

	/**
	 * @return the validationName
	 */
	public String getValidationName() {
		return validationName;
	}

	@Override
	public String toString() {
		return "[" + type.getName() + "] [" + validationName + "] [" + messageDate + "] - " + message;
	}

	/**
	 * @return the optional errorLocation
	 */
	public ErrorLocation getErrorLocation() {
		return errorLocation;
	}
}
