package nl.vrom.roo.validator.core.util;

import com.thoughtworks.xstream.XStream;

import java.io.*;

public class Object2XmlSerializer<T> {

	public XStream xStream;

	@SuppressWarnings("unchecked")
	public T getObjectFromXmlFile(File inputFile) throws IOException {

		InputStream inputStream = null;

		try {
			inputStream = new BufferedInputStream(new FileInputStream(inputFile));
			return (T) xStream.fromXML(inputStream); // NOPMD - intention is clear
		} finally {
			if (inputStream != null) {
				inputStream.close();
			}
		}
	}

	public void getXMLFromObject(T object, File outputFile) throws IOException {

		BufferedOutputStream outputStream = null;

		try {
			//System.out.println("Path=" + outputFile.getAbsolutePath());

			outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
			xStream.toXML(object, outputStream);
		} finally {
			if (outputStream != null) {
				outputStream.close();
			}
		}

	}

	/**
	 * Gets the XStream object
	 *
	 * @return the XStream object
	 */
	public XStream getxStream() {
		return xStream;
	}

	/**
	 * Sets the XStream object
	 *
	 * @param stream
	 *            the XStream object
	 */
	public void setxStream(XStream stream) {
		xStream = stream;
	}

}
