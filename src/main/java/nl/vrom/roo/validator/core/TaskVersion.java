package nl.vrom.roo.validator.core;

public class TaskVersion {

	private String componentVersion;

	private String fileName;

	private String fileVersion;

	public TaskVersion(String componentVersion) {
		this.componentVersion = componentVersion;
	}

	public TaskVersion(String componentVersion, String fileName) {
		this(componentVersion);
		this.fileName = fileName;
	}

	public TaskVersion(String componentVersion, String fileName, String fileVersion) {
		this(componentVersion, fileName);
		this.fileVersion = fileVersion;
	}

	public String getComponentVersion() {
		return componentVersion;
	}

	public String getFileName() {
		return fileName;
	}

	public String getFileVersion() {
		return fileVersion;
	}

	@Override
	public String toString() {
		StringBuffer stringBuffer = new StringBuffer(TaskVersion.class.getName());
		stringBuffer.append("{ componentVersion: ").append(this.componentVersion);
		stringBuffer.append(", fileName: ").append(this.fileName);
		stringBuffer.append(", fileVersion: ").append(this.fileVersion);
		stringBuffer.append("} ");
		return stringBuffer.toString();
	}

	public String getMessage() {
		StringBuffer stringBuffer = new StringBuffer();
		stringBuffer.append("component: ").append(this.componentVersion);
		if (this.fileName != null) {
			stringBuffer.append(", bestand: ").append(this.fileName);
		}
		if (this.fileVersion != null) {
			stringBuffer.append(", versie: ").append(this.fileVersion);
		}
		return stringBuffer.toString();
	}

}
