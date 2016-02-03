package nl.vrom.roo.validator.core;

import java.util.List;

import nl.vrom.roo.validator.core.reporting.AbstractReporting;

public interface Reporter {

	public void report(ReporterContext context);

	public List<AbstractReporting> getReportings();

}
