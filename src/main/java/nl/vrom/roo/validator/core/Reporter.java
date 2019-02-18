package nl.vrom.roo.validator.core;

import nl.vrom.roo.validator.core.reporting.AbstractReporting;

import java.util.List;

public interface Reporter {

	public void report(ReporterContext context);

	public List<AbstractReporting> getReportings();

}
