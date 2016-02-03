package nl.vrom.roo.validator.core.util;

import nl.vrom.roo.validator.core.ReporterContext;
import nl.vrom.roo.validator.core.TaskContext;
import nl.vrom.roo.validator.core.ValidatorContext;

public class TaskContextList {

	private TaskContext[] taskContexts;

	private boolean validateAuthenticity;
	private boolean includingDocuments;
	private Exception detectedException;

	public TaskContextList() {
		this.taskContexts = new TaskContext[3];
	}

	public TaskContextList(boolean includingDocuments) {
		this();
		this.includingDocuments = includingDocuments;
	}

	public boolean isAllSuccessful() {
		for(TaskContext taskContext : taskContexts) {
			if(taskContext instanceof ValidatorContext) {
				if(!((ValidatorContext)taskContext).isSuccessful()) {
					return false;
				}
			}
		}
		return true;
	}
	
	public boolean isAllCompletelySuccessful() {
		
		for(TaskContext taskContext : taskContexts) {
			if(taskContext instanceof ValidatorContext) {
				if(!((ValidatorContext)taskContext).isCompletelySuccessful()) {
					return false;
				}
			}
		}
		return true;
	}

	protected TaskContext[] getTaskContexts() {
		return taskContexts.clone();
	}

	public void setFormulierContext(ValidatorContext context) {
		this.taskContexts[0] = context;
	}

	public void setValidatorContext(ValidatorContext context) {
		this.taskContexts[1] = context;
	}

	public void setReporterContext(ReporterContext context) {
		this.taskContexts[2] = context;
	}

	public ValidatorContext getFormulierContext() {
		return (ValidatorContext) this.taskContexts[0];
	}

	public ValidatorContext getValidatorContext() {
		return (ValidatorContext) this.taskContexts[1];
	}

	public ReporterContext getReporterContext() {
		return (ReporterContext) this.taskContexts[2];
	}

	public boolean isValidateAuthenticity() {
		return validateAuthenticity;
	}

	public void setValidateAuthenticity(boolean validateAuthenticity) {
		this.validateAuthenticity = validateAuthenticity;
	}

	public Exception getException() {
		return this.detectedException;
	}

	public void setException(Exception exception) {
		this.detectedException = exception;
	}

	public boolean isIncludingDocuments() {
		return includingDocuments;
	}

}
