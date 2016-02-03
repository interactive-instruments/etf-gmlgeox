package nl.vrom.roo.validator.core.validation.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import nl.vrom.roo.validator.core.TaskVersion;
import nl.vrom.roo.validator.core.ValidatorContext;
import nl.vrom.roo.validator.core.ValidatorMessageBundle;
import nl.vrom.roo.validator.core.errorlocation.IdErrorLocation;
import nl.vrom.roo.validator.core.exception.ValidationException;
import nl.vrom.roo.validator.core.validation.AbstractValidation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class SchematronValidationImpl extends AbstractValidation {

	private static final Logger LOGGER = LoggerFactory.getLogger(SchematronValidationImpl.class);

	private TransformerFactory trfFactory;

	private Templates stXSLT;

	private SchematronValidationUriResolver uriResolver;

	private File defFile;

	private File ruleFile;

	private boolean schematronUsesReportTags;

	@Override
	protected void initialize() {
		
		// System.setProperty("javax.xml.transform.TransformerFactory","com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");

   		trfFactory = new SettingsDependentFactory<TransformerFactory>() {

			@Override
			protected TransformerFactory createFactory() {
				LOGGER.debug("Found alternative class for factory: {}", getProperty("javax.xml.transform.TransformerFactory"));
				return TransformerFactory.newInstance();
			}
   		}.createSystemSettingsDependentFactory();
		
		// System.clearProperty("javax.xml.transform.TransformerFactory");
		LOGGER.info("TransformerFactory class=" + trfFactory.getClass().getName());
 		
		trfFactory.setURIResolver(uriResolver);

		uriResolver = new SchematronValidationUriResolver();
		trfFactory.setURIResolver(uriResolver);

		String defFilename = (String) parameters.get("schematronDefinitionFile");

		if (defFilename == null) {
			throw new IllegalArgumentException(
					"No parameter schematronDefinitionFile specified for schematron validation");
		}

		defFile = new File(defFilename);
		if (!defFile.exists()) {
			throw new IllegalArgumentException("Schematron definition file does not exist at location: " + defFilename);
		}

		String ruleFilename = (String) parameters.get("schematronRuleFile");
		if (ruleFilename == null) {
			throw new IllegalArgumentException("No parameter schematronRuleFile specified for schematron validation");
		}

		ruleFile = new File(ruleFilename);
		if (!ruleFile.exists()) {
			throw new IllegalArgumentException("Schematron rule file does not exist at location: " + ruleFilename);
		}

		schematronUsesReportTags = Boolean.TRUE.equals(parameters.get("schematronUsesReportTags"));

		initializeSchematronDocument();
	}

	private void initializeSchematronDocument() {
		try {
			LOGGER.debug("Initialize schematron validation: {}", ruleFile.getName());

			File transformerFile = transformDocument("schematron_transform_", ruleFile, defFile);

			SchematronErrorListener checkFileTransformErrorListener = new SchematronErrorListener(transformerFile
					.getAbsolutePath());
			trfFactory.setErrorListener(checkFileTransformErrorListener);
			stXSLT = trfFactory.newTemplates(new StreamSource(transformerFile));

			if (checkFileTransformErrorListener.errorsDetected) {
				throw new IllegalArgumentException("Errors while parsing generated schematron transformer file "
						+ transformerFile.getName() + ". Schematron file " + ruleFile.getName() + ". Error messages: "
						+ checkFileTransformErrorListener.errors);
			}

			debugTransform(transformerFile);

			if(!transformerFile.delete()) {
				LOGGER.error("Could not delete temporary file {}", transformerFile);
			}
		} catch (TransformerException e) {
			throw new IllegalArgumentException("Could not create schematronDocument  for file: " + ruleFile.getName(),
					e);
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not create tmp file", e);
		}
	}

	private File transformDocument(String tmpFileName, File fileToTransform, File transformationFile)
			throws IOException, TransformerException {

		File tmpFile = File.createTempFile(tmpFileName, ".xslt");


		LOGGER.debug("transform file {} into {} using {}", new Object[] {fileToTransform.getAbsolutePath(), tmpFile.getAbsolutePath(), transformationFile.getAbsolutePath()});

		Transformer transformer = trfFactory.newTransformer(new StreamSource(transformationFile));

		SchematronErrorListener errorListener = new SchematronErrorListener(tmpFile.getName());
		transformer.setErrorListener(errorListener);
		
		// Make it possible to define custom XML or XSLT in the schematron
		transformer.setParameter("allow-foreign", "true");

		OutputStream osTmp = new FileOutputStream(tmpFile);
		try {
		    transformer.transform(new StreamSource(fileToTransform), new StreamResult(osTmp));
		} finally {
		    osTmp.close();
		}

		if (errorListener.errorsDetected) {
			throw new IllegalArgumentException("Errors while transforming schematron file " + fileToTransform.getName()
					+ ", result file=" + tmpFile.getAbsolutePath() + ". Error messages: " + errorListener.errors);
		}

		return tmpFile;
	}

	@Override
	public void validateTemplate(ValidatorContext validatorContext, Reader reader) throws ValidationException {
		try {
			// Document resultDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			File resultFile = File.createTempFile("schematron_report_", ".xml");
			LOGGER.debug("Result of validation stored in temporary file: {}", resultFile.getAbsolutePath());

			LOGGER.debug("Start validating using schematron file {}", ruleFile.getName());

			LOGGER.debug("XSLT transformation started");
			Transformer transformer = stXSLT.newTransformer();

			if (transformer == null) {
				throw new ValidationException("Fatal schematron validation error caused by initialization errors.");
			}
			OutputStream osResult = new FileOutputStream(resultFile);
			try {
//				transformer.setURIResolver(new SchematronValidationUriResolver(ruleFile.getParentFile()));
				
			    transformer.transform(new StreamSource(reader), new StreamResult(osResult));
			} finally {
			    osResult.close();
			}
			LOGGER.debug("XSLT transformation finished");

			debugOutput(resultFile, validatorContext.getInputFile());

			Document resultDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resultFile);
			
			if(!resultFile.delete()) {
				LOGGER.error("Could not delete resultFile {}", resultFile);
			}

			LOGGER.debug("Handling result started");
			if (resultDocument == null) {
				throw new IllegalStateException("Schematron validation failed, no document as result");
			}

			findFailures(resultDocument, validatorContext, "svrl:failed-assert");
			if (schematronUsesReportTags) {
				findFailures(resultDocument, validatorContext, "svrl:successful-report");
			}

			LOGGER.debug("Handling result stopped");
			if (validatorContext.isSuccessful(this)) {
				validatorContext.addNotice(ValidatorMessageBundle.getMessage(
						"validator.core.validation.schematron.valid", new Object[] {
								validatorContext.getInputFile().getName(), ruleFile.getName() }), null);
			}
		} catch (TransformerException e) {
			throw new ValidationException(e);
		} catch (ParserConfigurationException e) {
			throw new ValidationException(e);
		} catch (SAXException e) {
			throw new ValidationException(e);
		} catch (IOException e) {
			throw new ValidationException(e);
		}
	}

	private void debugTransform(File transformFile) {
		doOutput(getSystemprop("schematronDebug"), transformFile, null);
	}

	private void debugOutput(File resultFile, File inputFile) {
		doOutput(getSystemprop("schematronOutput"), resultFile, inputFile);
	}

	private void doOutput(String destination, File resultFile, File inputFile) {

		try {
			if (destination == null) {
				return;
			} else {
				destination += "_"+ ruleFile.getName();
				if(inputFile!=null) {
					destination += "__"+inputFile.getName();
				}
				
				FileInputStream fis = null;
				try {
					fis = new FileInputStream(resultFile);

					if (destination.equalsIgnoreCase("logger")) {
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						fis.getChannel().transferTo(0, resultFile.length(), Channels.newChannel(baos));

						LOGGER.debug("Contents of file: {}", resultFile);
						LOGGER.debug("{}", baos);
					} else {
						File destFile = new File(destination + "_" +resultFile.getName());
						FileOutputStream fos = new FileOutputStream(destFile);
						try {
							fis.getChannel()
								.transferTo(0, resultFile.length(), fos.getChannel());
						} finally {
							if(fos!=null) {
								fos.close();
							}
						}
					}
				} finally {
					if (fis != null) {
						fis.close();
					}
				}

			}
		} catch (FileNotFoundException e) {
			LOGGER.debug("Unexpected exception", e);
		} catch (IOException e) {
			LOGGER.debug("Unexpected exception", e);
		}
	}

	private String getSystemprop(String propname) {

		String result = System.getProperty(propname);
		if (result != null) {
			return result;
		}

		return System.getenv(propname);
	}

	private void findFailures(Document resultDocument, ValidatorContext validatorContext, String tagName) {

		NodeList failedAsserts = resultDocument.getElementsByTagName(tagName);
		for (int i = 0, n = failedAsserts.getLength(); i < n; ++i) {
			IdErrorLocation errorLocation = null;

			Element failedAssert = (Element) failedAsserts.item(i);

			// Set default message
			String message = ValidatorMessageBundle.getMessage("validator.core.validation.schematron.unknown-error");
			boolean isWarning = false;
			boolean isFatal = false;

			NodeList texts = failedAssert.getElementsByTagName("svrl:text");
			if (texts.getLength() > 0) {
				// Retrieve the text node.
				org.w3c.dom.Node node = texts.item(0).getFirstChild();
				if (node != null && node.getNodeType() == Node.TEXT_NODE) {
					message = node.getTextContent().replace("\n", " ");
				} else {
					message = texts.item(0).getTextContent().replace("\n", " ");
				}

				NodeList diagnostics = ((Element) texts.item(0)).getElementsByTagName("svrl:diagnostic-reference");
				for (int j = 0, m = diagnostics.getLength(); j < m; ++j) {
					Element diagnostic = (Element) diagnostics.item(j);

					String gmlId = getDiagnosticInfo(diagnostic, "gmlId");
					String identification = getDiagnosticInfo(diagnostic, "identificatie");
					String coords = getDiagnosticInfo(diagnostic, "coords");
					isWarning = hasDiagnosticInfo(diagnostic, "warning");
					isFatal = hasDiagnosticInfo(diagnostic, "fatal");

					if (gmlId != null) { // NOPMD
						errorLocation = new IdErrorLocation(gmlId);
					} else if (identification != null) { // NOPMD
						errorLocation = new IdErrorLocation(identification);
					} else if (coords != null) { // NOPMD
						errorLocation = new IdErrorLocation(coords);
					}
				}
			}

			if (isWarning) {
				validatorContext.addWarning(message, errorLocation);
			} else if(isFatal) {
				validatorContext.addFatal(message, errorLocation);
			}
			else {
				validatorContext.addError(message, errorLocation);
			}
		}

	}

	private boolean hasDiagnosticInfo(Element diagnostic, String diagId) {

		return diagnostic.getAttribute("diagnostic") != null && diagnostic.getAttribute("diagnostic").equals(diagId);
	}

	private String getDiagnosticInfo(Element diagnostic, String diagId) {

		return hasDiagnosticInfo(diagnostic, diagId) ? diagnostic.getTextContent() : null;
	}

	private static final class SchematronValidationUriResolver implements URIResolver {

//		private File resourceDir;
//		
//		public SchematronValidationUriResolver() {	// NOPMD
//		}
//		
//		public SchematronValidationUriResolver(File resourceDir) {
//			this.resourceDir = resourceDir;
//		}
		
		public Source resolve(String targetFilename, String baseURI) throws TransformerException {
			Source result = null;

			try {
				URI uri = new URI(baseURI);

				if (uri.getScheme().equals("file")) {
					File baseFile = new File(uri);
					if (baseFile.exists()) {
//						File targetDir = (resourceDir==null) ? baseFile.getParentFile() : resourceDir;
//						
//						File targetFile = new File(targetDir, targetFilename);
						File targetFile = new File(baseFile.getParent(), targetFilename);
						if (targetFile.exists()) {
							result = new StreamSource(targetFile);
						}
					}
				} else {
					throw new IllegalArgumentException("Resolving of remote files is not allowed");
				}
			} catch (URISyntaxException e) {
				throw new TransformerException(e);
			}

			if (result == null) {
				throw new IllegalStateException("Could not resolve file target: " + targetFilename + ", base: "
						+ baseURI);
			}
			return result;
		}
	}

	private static final class SchematronErrorListener implements ErrorListener {

		private final String filename;
		boolean errorsDetected;
		StringBuffer errors = new StringBuffer();

		public SchematronErrorListener(String filename) {
			this.filename = filename;
		}

		private void reportError(TransformerException exception) {

			errorsDetected = true;
			errors.append(getDetailedMessage(exception));
			errors.append("\n");
		}

		private String getDetailedMessage(TransformerException exception) {

			SourceLocator locator = exception.getLocator();
			String location = locator == null ? "" : (", row=" + locator.getLineNumber() + ",col=" + locator
					.getColumnNumber());
			return exception.getMessage() + location;
		}

		public void error(TransformerException exception) throws TransformerException {
			reportError(exception);
			LOGGER.error("Error while preparing schematron: {}. file= {}", getDetailedMessage(exception), filename);
		}

		public void fatalError(TransformerException exception) throws TransformerException {
			reportError(exception);
			LOGGER.error("Fatal error while preparing schematron: {}. file= {}", getDetailedMessage(exception), filename);
		}

		public void warning(TransformerException exception) throws TransformerException {
			LOGGER.warn("Warning error while preparing schematron: {}. file= {}", getDetailedMessage(exception), filename);
		}

	}

	@Override
	public TaskVersion getTaskVersion() {
		return new TaskVersion("1.4", this.ruleFile.getName());
	}

}
