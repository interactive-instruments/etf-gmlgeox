package nl.vrom.roo.validator.core.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

import nl.vrom.roo.validator.core.Reporter;
import nl.vrom.roo.validator.core.ReporterContext;
import nl.vrom.roo.validator.core.ValidatorMessageBundle;
import nl.vrom.roo.validator.core.exception.ReportingException;
import nl.vrom.roo.validator.core.reporting.AbstractReporting;
import nl.vrom.roo.validator.core.util.EncodedFileReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for validating the contents of an inputstream using the defined reporting implementations
 * 
 * @author brinkmanro
 * @version 1.0.0 Date: 28 Jan 2008
 */
public class BasicReporterImpl implements Reporter {

	private final Logger logger = LoggerFactory.getLogger(BasicReporterImpl.class);

	/**
	 * List of reportings used (in order) by this reporter
	 */
	protected List<AbstractReporting> reportings;

	public BasicReporterImpl(List<AbstractReporting> reportings) {
		this.reportings = reportings;
	}

	public void report(ReporterContext context) {
		for (AbstractReporting reporting : reportings) {

			if (reporting.isSkipAllowed() && context.skipTask(reporting.getId())) {
				logger.info("Reporting: {} ({}) has been skipped", reporting.getName(), reporting.getId());
				continue;
			}

			// If no filter is set or filter is set and contains the taskname
			logger.info("ReportingName: {}", reporting.getName());
			Reader reader = null;

			try {
				reader = new EncodedFileReader(context.getInputFile());
				reporting.report(context, reader);
			} catch (ReportingException e) {
				logger.error("Fatal Reporter error: ", e);
				context.setErrorInformation(ValidatorMessageBundle.getMessage("reporter.core.reporting.exception"));
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (IOException e) { // NOPMD - Ignored. The reader is not needed any more.
					}
				}
			}
		}
	}

	/**
	 * Set the list of reportings used by this reporter
	 * 
	 * @param reportings
	 *            list of reportings to set
	 */
	public void setReportings(List<AbstractReporting> reportings) {
		this.reportings = reportings;
	}

	/**
	 * Returns the list of reporting used by this reporter
	 * 
	 * @return the reportings used by this reporter
	 */
	public List<AbstractReporting> getReportings() {
		return reportings;
	}

	/**
	 * Adds the reporting to this reporter
	 * 
	 * @param reporting
	 *            the reporting to add to this reporter
	 * @return true on success
	 */
	public boolean addReporting(AbstractReporting reporting) {
		return reportings.add(reporting);
	}

}
