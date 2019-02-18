package nl.vrom.roo.validator.core;

public class TaskValueObject {

	protected String taskId;

	protected String name;

	protected boolean skipAllowed;

	protected TaskVersion taskVersion;

	@Override
	public int hashCode() { // NOPMD suppressing faulty EmptyMethodInAbstractClassShouldBeAbstract
		return name.hashCode();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof TaskValueObject) {
			if(this.taskId==null) {
				return ((TaskValueObject) obj).taskId==null;
			}
			else {
				return this.taskId.equals(((TaskValueObject) obj).taskId);
			}
		}
		return false;
	}
	
	public String getId() {
		return taskId;
	}

	public String getName() {
		return name;
	}

	public boolean isSkipAllowed() {
		return skipAllowed;
	}

	public TaskVersion getTaskVersion() {
		return taskVersion;
	}


}
