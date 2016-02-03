package nl.vrom.roo.validator.core.util;

import java.io.File;
import java.io.IOException;

public class ContextSerializer extends Object2XmlSerializer<TaskContextList> {

	public void marshallTaskContextList(TaskContextList taskContextList, File file) throws IOException {

		getXMLFromObject(taskContextList, file);
	}

	public TaskContextList unmarshallTaskContextList(File file) throws IOException {

		return getObjectFromXmlFile(file);
	}

}
