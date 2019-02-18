package nl.vrom.roo.validator.core;

import java.io.File;
import java.util.*;

/**
 * This class contains general context information.
 *
 * @author rdool
 * @version 1.0.0 Date: 13 Mar 2008
 */
public class TaskContext {

	/**
	 * The filename. This name may be serialized.
	 */
	private String inputFile;

	/**
	 * Date when reporter was started
	 */
	private final Date dateStarted;

	/**
	 * Date when reporter was finished
	 */
	private Date dateFinished;

	/**
	 * The current task handling for this context
	 */
	private TaskValueObject currentTask;

	/**
	 * The task handling history for this context (in order of occurrence)
	 */
	private final List<TaskValueObject> taskHistory;

	/**
	 * Map of context parameters by validation name
	 */
	private final transient Map<String, Object> parameterMap;
	
	
	
	private InputParametersEnum defaultInputParameter;

	/**
	 * Ids of the tasks that should be skipped
	 */
	private transient List<String> skipTaskIds;

	/**
	 * Embedded observable setted up as a member variable (object composition) instead of inheritance. This because of
	 * serialization reasons.
	 */
	private final transient ContextObservable observable;

	
	public TaskContext(InputParametersEnum ipar) {
		this();
		this.defaultInputParameter = ipar;
	}
	
	/**
	 * Default constructor
	 *
	 * @param inputFile
	 */
	public TaskContext(File inputFile) {
		this();
		this.inputFile = inputFile==null ? null : inputFile.getAbsolutePath();
	}
		
	public TaskContext() {
		this.dateStarted = new Date();
		this.taskHistory = new ArrayList<TaskValueObject>();
		this.parameterMap = new HashMap<String, Object>();
		this.skipTaskIds = new ArrayList<String>();
		this.observable = new ContextObservable();
	}
	
		
	public void defineDefaultInput(InputParametersEnum ipar) {
		this.defaultInputParameter = ipar;
	}
	
	
	
	

	/**
	 * @return the date when the validator started
	 */
	public Date getDateStarted() {
		return dateStarted==null ? null : new Date(dateStarted.getTime());
	}

	/**
	 * @return the date when the validator finished
	 */
	public Date getDateFinished() {
		return dateFinished==null ? null : new Date(dateFinished.getTime());
	}
	
	/**
	 * @param dateFinished the dateFinished to set
	 */
	public void setDateFinished(Date dateFinished) {
		this.dateFinished = new Date(dateFinished.getTime());
	}


	/**
	 * @return the file this ReporterContext belongs to
	 */
	public File getInputFile() {
		if(defaultInputParameter==null) {
			return new File(inputFile);
		}
		else {
			return (File) getParameter(defaultInputParameter);
		}
	}

	public String getInputFilename() {
		return getInputFile().getName();
	}

	/**
	 * @param task
	 *            the current task
	 */
	public void setCurrentTask(AbstractTask task) {
		this.currentTask = task.taskValueObject;

		// If the task is not in the history add it
		if (!historyContainsTask(task)) {
			addTaskToHistory(task);
		}
		this.observable.setChanged();
		this.observable.notifyObservers();
	}

	private void addTaskToHistory(AbstractTask task) {
		this.taskHistory.add(task.taskValueObject);
	}

	public void removeTaskFromHistory(AbstractTask task) {
		this.taskHistory.remove(task.taskValueObject);
	}

	public boolean historyContainsTask(AbstractTask task) {
		return this.taskHistory.contains(task.taskValueObject);
	}

	/**
	 * Reset the task name
	 */
	public void resetTaskHandling() {
		this.currentTask = null;
		this.observable.setChanged();
		this.observable.notifyObservers();
	}

	/**
	 * @return all task executed The list is protected against changes since other methods are used for manipulation of
	 *         the task history.
	 * @see #setCurrentTask(AbstractTask)
	 * @see #removeTaskFromHistory(AbstractTask)
	 */
	protected List<TaskValueObject> getTaskHistory() {
		return Collections.unmodifiableList(taskHistory);
	}

	/**
	 * @return names of all tasks executed
	 */
	protected List<String> getTaskNameHistory() {

		List<String> names = new ArrayList<String>();
		for (TaskValueObject task : taskHistory) {
			names.add(task.name);
		}

		return names;
	}

	/**
	 * @return the current task name or null when no current is visible
	 */
	public String getCurrentTaskHandlingName() {
		return (currentTask == null) ? null : currentTask.getName();
	}

	public Object getParameter(InputParametersEnum paramEnum) {
		return parameterMap.get(paramEnum.getName());
	}
	
	/**
	 * Gets a context related parameter for the task with the given name (or a default parameter if absent)
	 *
	 * @param paramName
	 *            the name of the context parameter for the current task
	 * @return the requested parameter object
	 */
	public Object getParameter(String paramName) {

		return parameterMap.get(paramName);
	}

	/**
	 * Sets a context related parameter for the task with the given name (or a default parameter if task name is null)
	 *
	 * @param paramName
	 *            the name of the context parameter
	 * @param paramValue
	 *            the value of the context parameter
	 */
	public void setParameter(String paramName, Object paramValue) {

		parameterMap.put(paramName, paramValue);
	}
	
	public void setParameter(InputParametersEnum paramEnum, Object paramValue) {
		parameterMap.put(paramEnum.getName(), paramValue);
	}


	/**
	 * Set the ids of the tasks to skip for this ValidatorContext
	 *
	 * @param skipTaskIds
	 */
	public void setSkipTaskIds(List<String> skipTaskIds) {
		this.skipTaskIds = skipTaskIds;
	}

	/**
	 * Returns true if the task specified should be skipped
	 *
	 * @param taskId
	 *            the Id of the task to check
	 * @return true if the task specified should be skipped
	 */
	public boolean skipTask(String taskId) {
		return skipTaskIds != null && skipTaskIds.contains(taskId);
	}

	public void addObserver(Observer taskObserver) {
		if (taskObserver == null) {
			return;
		}
		this.observable.addObserver(taskObserver);
	}

	/**
	 * @author rdool Class defined to prevent inheritance
	 */
	public class ContextObservable extends Observable {

		@Override
		public synchronized void setChanged() { // NOPMD intentionally done to change visibility
			super.setChanged();
		}

		public TaskContext getContext() {
			return TaskContext.this;
		}
	}

}
