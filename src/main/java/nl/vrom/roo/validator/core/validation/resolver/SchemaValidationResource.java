package nl.vrom.roo.validator.core.validation.resolver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Reader;

import org.w3c.dom.ls.LSInput;

/**
 * Implementation of LSInput for the SchemaValidationImpl
 *
 */
public class SchemaValidationResource implements LSInput {

	private String systemId;

	private final File resourceFile;

	private String baseURI;

	private String publicId;

	/**
	 * Default constructor
	 *
	 * @param resourceFile
	 *            the file which contains the .xsd schema
	 * @param baseURI
	 * 	       the baseURI of this file
	 * @param systemId
	 *            the systemId of the .xsd schema
	 * @param publicId
	 * 	       the public id of the .xsd schema
	 *
	 */
	public SchemaValidationResource(File resourceFile, String baseURI, String systemId, String publicId) {
		this.systemId = systemId;
		this.publicId = publicId;
		this.resourceFile = resourceFile;

		if (baseURI == null) {
		    throw new  IllegalArgumentException("BaseURI for resource " + resourceFile + " should not be null");
		}
		this.baseURI = baseURI;
	}

	public String getBaseURI() {
		return baseURI;
	}

	/*
	 * Method returns the ByteStream for this resource
	 *
	 * @see org.w3c.dom.ls.LSInput#getByteStream()
	 */
	public InputStream getByteStream() {
		try {
			return new FileInputStream(resourceFile);
		} catch (FileNotFoundException e) {
			throw new IllegalStateException("File not found: " + resourceFile.getName()
					+ ", should not happen because existence is checked on initalization", e);
		}
	}

	/*
	 * Not implemented
	 *
	 * @see org.w3c.dom.ls.LSInput#getCertifiedText()
	 */
	public boolean getCertifiedText() { // NOPMD - implementing interface
		return false;
	}

	/*
	 * Not implemented
	 *
	 * @see org.w3c.dom.ls.LSInput#getCharacterStream()
	 */
	public Reader getCharacterStream() {
		return null;
	}

	/*
	 * Not implemented
	 *
	 * @see org.w3c.dom.ls.LSInput#getEncoding()
	 */
	public String getEncoding() {
		return null;
	}

	/*
	 * @see org.w3c.dom.ls.LSInput#getPublicId()
	 */
	public String getPublicId() {
		return this.publicId;
	}

	/*
	 * Not implemented
	 *
	 * @see org.w3c.dom.ls.LSInput#getStringData()
	 */
	public String getStringData() {
		return null;
	}

	/*
	 * @see org.w3c.dom.ls.LSInput#getSystemId()
	 */
	public String getSystemId() {
		return this.systemId;
	}

	/*
	 * @see org.w3c.dom.ls.LSInput#setBaseURI(java.lang.String)
	 */
	public void setBaseURI(String baseURI) {
		this.baseURI = baseURI;
	}

	/*
	 * Not implemented
	 *
	 * @see org.w3c.dom.ls.LSInput#setByteStream(java.io.InputStream)
	 */
	public void setByteStream(InputStream byteStream) {
		// Not implemented
	}

	/*
	 * Not implemented
	 *
	 * @see org.w3c.dom.ls.LSInput#setCertifiedText(boolean)
	 */
	public void setCertifiedText(boolean certifiedText) {
		// Not implemented
	}

	/*
	 * Not implemented
	 *
	 * @see org.w3c.dom.ls.LSInput#setCharacterStream(java.io.Reader)
	 */
	public void setCharacterStream(Reader characterStream) {
		// Not implemented
	}

	/*
	 * Not implemented
	 *
	 * @see org.w3c.dom.ls.LSInput#setEncoding(java.lang.String)
	 */
	public void setEncoding(String encoding) {
		// Not implemented
	}

	/*
	 * @see org.w3c.dom.ls.LSInput#setPublicId(java.lang.String)
	 */
	public void setPublicId(String publicId) {
	    this.publicId = publicId;
	}

	/*
	 * Not implemented
	 *
	 * @see org.w3c.dom.ls.LSInput#setStringData(java.lang.String)
	 */
	public void setStringData(String stringData) {
		// Not implemented
	}

	/*
	 * @see org.w3c.dom.ls.LSInput#setSystemId(java.lang.String)
	 */
	public void setSystemId(String systemId) {
		this.systemId = systemId;
	}

}