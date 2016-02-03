package nl.vrom.roo.validator.core;

import java.util.Map;

public abstract class AbstractTask {

	protected Map<String, Object> parameters;

	protected TaskValueObject taskValueObject;

	/**
	 * Returns the version information of a validation object. It caches the information obtained from the subclasses
	 * locally.
	 *
	 * @return the version information
	 *
	 * @see nl.vrom.roo.validator.core.TaskVersion
	 */
	public abstract TaskVersion getTaskVersion();

	/**
	 * Method used to initialize the task handler.
	 */
	public final void initializeTask() {
		initialize();
		initializeVersion();
	}

	/**
	 * Abstract method used to initialize the task handler.
	 */
	protected abstract void initialize();

	public AbstractTask() {
		this.taskValueObject = new TaskValueObject();
	}

	/**
	 * To be executed when the task has been initialized completely.
	 */
	protected void initializeVersion() {
		this.taskValueObject.taskVersion = getTaskVersion();
	}

	@Override
	public boolean equals(Object obj) { // NOPMD suppressing faulty EmptyMethodInAbstractClassShouldBeAbstract
		if (this == obj) {
			return true;
		}
		if ((obj == null) || (obj.getClass() != this.getClass())) {
			return false;
		}
		return ((AbstractTask) obj).getId().equals(this.taskValueObject.taskId);
	}

	@Override
	public int hashCode() { // NOPMD suppressing faulty EmptyMethodInAbstractClassShouldBeAbstract
		return this.taskValueObject.getName().hashCode();
	}

	/**
	 * Get the name of this task.
	 *
	 * @return the name of the task
	 * @see nl.vrom.roo.validator.core.TaskValueObject#getName()
	 */
	public final String getName() { // NOPMD suppressing faulty EmptyMethodInAbstractClassShouldBeAbstract
		return this.taskValueObject.name;
	}

	/**
	 * Set the name of this task.
	 *
	 * @param name
	 *            the name of the task
	 * @see nl.vrom.roo.validator.core.TaskValueObject
	 */
	public final void setName(String name) {
		this.taskValueObject.name = name;
	}

	/**
	 * Get the parameters of this task.
	 *
	 * @return the parameters to configure the Task
	 */
	public final Map<String, Object> getParameters() { // NOPMD suppressing faulty EmptyMethodInAbstractClassShouldBeAbstract
		return parameters;
	}

	/**
	 * Set the parameters of this task.
	 *
	 * @param parameters
	 *            to configure the Task
	 */
	public final void setParameters(Map<String, Object> parameters) {
		this.parameters = parameters;
	}

	/**
	 * Set to true if skip is allowed for this task.
	 *
	 * @param skipAllowed
	 *            set to true if skipped is allowed
	 */
	public void setSkipAllowed(boolean skipAllowed) {
		this.taskValueObject.skipAllowed = skipAllowed;
	}

	/**
	 * Determine of skipping of this task is allowed.
	 *
	 * @return true is skip is allowed for this validation
	 */
	public boolean isSkipAllowed() {
		return this.taskValueObject.skipAllowed;
	}

	/**
	 * Sets the taskId of this task.
	 *
	 * @param taskId
	 *            the taskId of this task
	 */
	public void setId(String taskId) {
		this.taskValueObject.taskId = taskId;
	}

	/**
	 * Returns the taskId of this task.
	 *
	 * @return the taskId of this task
	 */
	public String getId() {
		return this.taskValueObject.taskId;
	}

	/**
	 * Gets the task value object.
	 *
	 * @return the task value object
	 */
	public TaskValueObject getTaskValueObject() {
		return taskValueObject;
	}
}
