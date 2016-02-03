package nl.vrom.roo.validator.core;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nl.vrom.roo.validator.core.errorlocation.ErrorLocation;
import nl.vrom.roo.validator.core.validation.AbstractValidation;

/**
 * This class is contains the validator context information of the several validations executed by the validator.
 *
 * @author brinkmanro
 * @version 1.0.0 Date: 28 Jan 2008
 */

public class ValidatorContext extends TaskContext {

	private static final String DETERMINED_VERSION = "determined_version";

	/** List of messages for this context (in order of occurrence) */
	public ArrayList<ValidatorMessage> messages;

	/** Set of messageTypes in context */
	public HashMap<ValidatorMessageType, HashSet<String>> messageTypes;


	public String getDeterminedVersion() {
		return (String) getParameter(DETERMINED_VERSION);
	}
	
	public void setDeterminedVersion(String version) {
		setParameter(DETERMINED_VERSION, version);
	}

	/**
	 * Instantiates a new validatorContext.
	 *
	 * @param inputFile
	 *            the file for which the context is valid
	 */
	public ValidatorContext(File inputFile) {
		super(inputFile);
		messages = new ArrayList<ValidatorMessage>();
		messageTypes = new HashMap<ValidatorMessageType, HashSet<String>>();
	
	}
	
	public ValidatorContext(){
		messages = new ArrayList<ValidatorMessage>();
		messageTypes = new HashMap<ValidatorMessageType, HashSet<String>>();
	}

	/**
	 * Adds a message of type {@link ValidatorMessageType#FATAL} to this validorContext.
	 *
	 * @param message
	 *            the message to add
	 */
	public void addFatal(String message) {
		addFatal(message, null);
	}

	/**
	 * Adds a message of type {@link ValidatorMessageType#FATAL} to this validorContext.
	 *
	 * @param message
	 *            the message to add
	 * @param errorLocation
	 *            a known errorLocation
	 */
	public void addFatal(String message, ErrorLocation errorLocation) {
		addMessage(ValidatorMessageType.FATAL, message, errorLocation);
	}

	/**
	 * Adds a message of type {@link ValidatorMessageType#ERROR} to this validorContext.
	 *
	 * @param message
	 *            the message to add
	 */
	public void addError(String message) {
		addError(message, null);

	}

	/**
	 * Adds a message of type {@link ValidatorMessageType#ERROR} to this validorContext.
	 *
	 * @param message
	 *            the message to add
	 * @param errorLocation
	 *            a known errorLocation
	 */
	public void addError(String message, ErrorLocation errorLocation) {
		addMessage(ValidatorMessageType.ERROR, message, errorLocation);

	}

	/**
	 * Adds a message of type {@link ValidatorMessageType#WARNING} to this validorContext.
	 *
	 * @param message
	 *            the message to add
	 */
	public void addWarning(String message) {
		addWarning(message, null);

	}

	/**
	 * Adds a message of type {@link ValidatorMessageType#WARNING} to this validorContext.
	 *
	 * @param message
	 *            the message to add
	 * @param errorLocation
	 *            a known errorLocation
	 */
	public void addWarning(String message, ErrorLocation errorLocation) {
		addMessage(ValidatorMessageType.WARNING, message, errorLocation);
	}

	/**
	 * Adds a message of type {@link ValidatorMessageType#NOTICE} to this validorContext.
	 *
	 * @param message
	 *            the message to add
	 */
	public void addNotice(String message) {
		addNotice(message, null);
	}

	/**
	 * Adds a message of type {@link ValidatorMessageType#NOTICE} to this validorContext.
	 *
	 * @param message
	 *            the message to add
	 * @param errorLocation
	 *            a known errorLocation
	 */
	public void addNotice(String message, ErrorLocation errorLocation) {
		addMessage(ValidatorMessageType.NOTICE, message, errorLocation);
	}

	/**
	 * Get the list of messages in this context.
	 *
	 * @return a list of messages, or an empty list if no messages are available
	 */
	public List<ValidatorMessage> getMessages() {
		return messages;
	}

	/**
	 * Returns a map of messages by validation name for this context.
	 *
	 * @return a map of messages by validation name, or an empty map if no messages are available
	 */
	public Map<String, List<ValidatorMessage>> getMessagesByValidationName() {

		Map<String, List<ValidatorMessage>> resultMap = new LinkedHashMap<String, List<ValidatorMessage>>();

		for (ValidatorMessage message : messages) {

			List<ValidatorMessage> messageList = resultMap.get(message.getValidationName());
			if (messageList == null) {
				messageList = new ArrayList<ValidatorMessage>();
				resultMap.put(message.getValidationName(), messageList);
			}
			messageList.add(message);
		}
		return resultMap;
	}

	/**
	 * Determine if this context has messages.
	 *
	 * @return true if this context contains messages
	 */
	public boolean hasMessages() {
		return (!getMessages().isEmpty());
	}

	/**
	 * Get a list of messages with type {@link ValidatorMessageType#FATAL}.
	 *
	 * @return a list of messages, or an empty list if no messages are available
	 */
	public List<ValidatorMessage> getFatals() {
		return getMessages(ValidatorMessageType.FATAL);
	}

	/**
	 * Determine if this context has messages of type {@link ValidatorMessageType#FATAL}.
	 *
	 * @return true if this context contains messages of type {@link ValidatorMessageType#FATAL}
	 */
	public boolean hasFatals() {
		return messageTypes.containsKey(ValidatorMessageType.FATAL);
	}

	/**
	 * Get a list of messages with type {@link ValidatorMessageType#ERROR}.
	 *
	 * @return a list of messages, or an empty list if no messages are available
	 */
	public List<ValidatorMessage> getErrors() {
		return getMessages(ValidatorMessageType.ERROR);
	}

	/**
	 * Determine if this context has messages of type {@link ValidatorMessageType#ERROR}.
	 *
	 * @return true if this context contains messages of type {@link ValidatorMessageType#ERROR}
	 */
	public boolean hasErrors() {
		return messageTypes.containsKey(ValidatorMessageType.ERROR);
	}

	/**
	 * Get a list of messages with type {@link ValidatorMessageType#WARNING}.
	 *
	 * @return a list of messages, or an empty list if no messages are available
	 */
	public List<ValidatorMessage> getWarnings() {
		return getMessages(ValidatorMessageType.WARNING);
	}

	/**
	 * Determine if this context has messages of type {@link ValidatorMessageType#WARNING}.
	 *
	 * @return true if this context contains messages of type {@link ValidatorMessageType#WARNING}
	 */
	public boolean hasWarnings() {
		return (!getWarnings().isEmpty());
	}

	/**
	 * Get a list of messages with type {@link ValidatorMessageType#NOTICE}.
	 *
	 * @return a list of messages, or an empty list if no messages are available
	 */
	public List<ValidatorMessage> getNotices() {
		return getMessages(ValidatorMessageType.NOTICE);
	}

	/**
	 * Determine if this context has messages of type {@link ValidatorMessageType#NOTICE}.
	 *
	 * @return true if this context contains messages of type {@link ValidatorMessageType#NOTICE}
	 */
	public boolean hasNotices() {
		return messageTypes.containsKey(ValidatorMessageType.NOTICE);
	}

	/**
	 * Determine if all the validations executed where successful.
	 * This means that there are no messages of type {@link ValidatorMessageType#FATAL} and no messages of type
	 * {@link ValidatorMessageType#ERROR}.
	 *
	 * @return true if all the validations in this context where successful
	 */
	public boolean isSuccessful() {
		return !hasFatals() && !hasErrors();
	}

	/**
	 * Determine if all the validations executed where completely successful.
	 * This means that the context {@linkplain #isSuccessful()} and there are no messages of type
	 * {@link ValidatorMessageType#WARNING}.
	 *
	 * @return true if all the validations in this context where completely successful
	 */
	public boolean isCompletelySuccessful() {
		return isSuccessful() && !hasWarnings();
	}

	/**
	 * Determine if the validation executed is completely successful for the validationName.
	 * This means that the context {@linkplain #isSuccessful()} and there are no messages of type
	 * {@link ValidatorMessageType#WARNING} for the specified validationName.
	 *
	 * @param validationName
	 *            name of the validation
	 * @return true if the validation in this context is completely successful for the validation name
	 */
	public boolean isCompletelySuccessful(String validationName) {
		boolean result = true;

		if (messageTypes.containsKey(ValidatorMessageType.FATAL)
				&& messageTypes.get(ValidatorMessageType.FATAL).contains(validationName)) {
			result = false;
		} else if (messageTypes.containsKey(ValidatorMessageType.ERROR)
				&& messageTypes.get(ValidatorMessageType.ERROR).contains(validationName)) {
			result = false;
		} else if (messageTypes.containsKey(ValidatorMessageType.WARNING)
				&& messageTypes.get(ValidatorMessageType.WARNING).contains(validationName)) {
			result = false;
		}
		return result;
	}

	/**
	 * Determine if the validation executed is completely successful for the validation.
	 * This means that the context {@linkplain #isSuccessful()} and there are no messages of type
	 * {@link ValidatorMessageType#WARNING} for the specified validation.
	 *
	 * @param validation
	 *            the validation
	 * @return true if the validation in this context is completely successful for the validation name
	 */
	public boolean isCompletelySuccessful(AbstractValidation validation) {
		return isCompletelySuccessful(validation.getName());
	}

	/**
	 * Determine if all the validations executed where successful for the validationName.
	 * This means that there are no messages of type {@link ValidatorMessageType#FATAL} and no messages of type
	 * {@link ValidatorMessageType#ERROR} for the specified validationName.
	 *
	 * @param validationName
	 *            name of the validation
	 * @return true if all the validations in this context where successful
	 */
	public boolean isSuccessful(String validationName) {
		boolean result = true;

		if (messageTypes.containsKey(ValidatorMessageType.FATAL)
				&& messageTypes.get(ValidatorMessageType.FATAL).contains(validationName)) {
			result = false;
		} else if (messageTypes.containsKey(ValidatorMessageType.ERROR)
				&& messageTypes.get(ValidatorMessageType.ERROR).contains(validationName)) {
			result = false;
		}
		return result;
	}

	/**
	 * Determine if all the validations executed where successful for the validation.
	 * This means that there are no messages of type {@link ValidatorMessageType#FATAL} and no messages of type
	 * {@link ValidatorMessageType#ERROR} for the specified validation.
	 *
	 * @param validation
	 *            the validation
	 * @return true if all the validations in this context where successful
	 */
	public boolean isSuccessful(AbstractValidation validation) {
		return isSuccessful(validation.getName());
	}

	/**
	 * Gets the list of performed validation properties.
	 *
	 * @return the unmodifiable list of performed validation properties
	 */
	public List<TaskValueObject> getValidations() {
		return super.getTaskHistory();
	}

	/**
	 * Gets the list of performed validation properties
	 *
	 * @return the unmodifiable list of performed validation properties
	 */
	public List<String> getValidationNames() {

		return super.getTaskNameHistory();
	}

	/**
	 * Add message to the validator context.
	 *
	 * @param type
	 *            the type of the message
	 * @param message
	 *            the message to add
	 * @param errorLocation
	 *            an aditional {@link ErrorLocation}
	 */
	private void addMessage(ValidatorMessageType type, String message, ErrorLocation errorLocation) {
		if (messageTypes.containsKey(type)) {
			messageTypes.get(type).add(this.getCurrentTaskHandlingName());
		} else {
			HashSet<String> validationNames = new HashSet<String>();
			validationNames.add(this.getCurrentTaskHandlingName());
			messageTypes.put(type, validationNames);
		}

		ValidatorMessage validatorMessage = new ValidatorMessage(this.getCurrentTaskHandlingName(), type, message,
				errorLocation);
		
		setDateFinished(validatorMessage.getMessageDate());

		messages.add(validatorMessage);
	}

	private List<ValidatorMessage> getMessages(ValidatorMessageType messageType) {
		List<ValidatorMessage> result = new ArrayList<ValidatorMessage>();
		for (ValidatorMessage message : messages) {
			if (message.getType() == messageType) {
				result.add(message);
			}
		}
		return result;
	}

}
