package nl.vrom.roo.validator.core;

/**
 * Enum that contains the available message types
 * 
 * @author brinkmanro
 * @version 1.0.0 Date: 28 Jan 2008
 */
public enum ValidatorMessageType {
	FATAL(ValidatorMessageBundle.getMessage("validator.core.validation.message-type.fatal")), ERROR(
			ValidatorMessageBundle.getMessage("validator.core.validation.message-type.error")), WARNING(
			ValidatorMessageBundle.getMessage("validator.core.validation.message-type.warning")), NOTICE(
			ValidatorMessageBundle.getMessage("validator.core.validation.message-type.notice"));

	private String name;

	/**
	 * ValidatorMessageType constructor
	 * 
	 * @param name
	 *            the name of the message type
	 */
	private ValidatorMessageType(String name) {
		this.name = name;
	}

	/**
	 * @return the name of the message type
	 */
	public String getName() {
		return name;
	}

}
