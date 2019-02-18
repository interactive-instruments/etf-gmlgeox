package nl.vrom.roo.validator.core.reporting;

import nl.vrom.roo.validator.core.AbstractTask;
import nl.vrom.roo.validator.core.ReporterContext;
import nl.vrom.roo.validator.core.exception.ReportingException;

import java.io.Reader;

public abstract class AbstractReporting extends AbstractTask {

	/**
	 * Abstract method used to execute the Reporting of the subclass as a template
	 *
	 * @param reporterContext
	 *            context of the Reporter
	 * @param reader
	 *            the reader of the document to generate the report for.
	 * @throws ReportingException
	 */
	protected abstract void reportTemplate(ReporterContext reporterContext, Reader reader) throws ReportingException;

	/**
	 * Generates report information by using the reader and the ReporterContext to store the results for this Reporter
	 *
	 * @param reporterContext
	 *            context of the Reporter
	 * @param reader
	 *            the reader of the document to generate the report for.
	 * @throws ReportingException
	 */
	public final void report(ReporterContext reporterContext, Reader reader) throws ReportingException {
		reporterContext.setCurrentTask(this);
		reportTemplate(reporterContext, reader);
		reporterContext.resetTaskHandling();
	}

	/**
	 * Skipping is always allowed for reports
	 */
	@Override
	public boolean isSkipAllowed() { // NOPMD this method is not empty
		return true;
	}

}
